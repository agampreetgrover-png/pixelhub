package com.agam.pixelhub.health

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.health.connect.HealthConnectManager
import android.util.Log
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import java.time.Period
import androidx.health.connect.client.time.TimeRangeFilter
import com.agam.pixelhub.data.HealthNudge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

enum class HealthStatus {
    /** Health Connect is ready (always the case on Android 14+). */
    AVAILABLE,
    /** Health Connect exists but needs an update from the Play Store. */
    NEEDS_UPDATE,
    UNAVAILABLE,
}

/**
 * Read-only access to today's activity in Health Connect. Fitbit and Google Fit write
 * there, and aggregate reads de-duplicate overlapping sources.
 */
class HealthRepository(private val context: Context) {

    /** Data types PixelHub reads (steps & activity only). */
    val readPermissions: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
    )

    /** Lets nudges read steps while PixelHub isn't on screen. */
    val backgroundPermission: String = HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND

    /** Request these together with [permissionContract]. */
    val allPermissions: Set<String> = readPermissions + backgroundPermission

    private val client by lazy { HealthConnectClient.getOrCreate(context) }

    fun status(): HealthStatus = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> HealthStatus.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthStatus.NEEDS_UPDATE
        else -> HealthStatus.UNAVAILABLE
    }

    /** UI: `rememberLauncherForActivityResult(permissionContract())`, then `launch(allPermissions)`. */
    fun permissionContract(): ActivityResultContract<Set<String>, Set<String>> =
        PermissionController.createRequestPermissionResultContract()

    /**
     * Where to review or revoke PixelHub's health access. Health Connect's per-app permission
     * page (ACTION_MANAGE_HEALTH_PERMISSIONS) is guarded by a system-only permission on current
     * Android, so starting it throws SecurityException; then open Health Connect's own
     * "manage data" screen instead (App permissions → PixelHub is one tap from there).
     */
    fun managePermissionsIntent(): Intent {
        val perAppPage = Intent(HealthConnectManager.ACTION_MANAGE_HEALTH_PERMISSIONS)
            .putExtra(Intent.EXTRA_PACKAGE_NAME, context.packageName)
        return if (canStart(perAppPage)) perAppPage else HealthConnectClient.getHealthConnectManageDataIntent(context)
    }

    /** True if some exported activity handles [intent] and we hold whatever permission it requires. */
    private fun canStart(intent: Intent): Boolean {
        val info = context.packageManager.resolveActivity(intent, 0)?.activityInfo ?: return false
        if (!info.exported) return false
        val required = info.permission ?: return true
        return context.checkSelfPermission(required) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * What the user has allowed. Throws if Health Connect can't be asked right now (its process
     * was killed, a binder error...), so callers can tell "unknown" from "revoked" and keep
     * showing the last good state instead of flipping to "not connected".
     */
    suspend fun grantedPermissions(): Set<String> =
        if (status() != HealthStatus.AVAILABLE) emptySet()
        else client.permissionController.getGrantedPermissions()

    /** True once every data type in [readPermissions] is allowed. Throws like [grantedPermissions]. */
    suspend fun isConnected(): Boolean = grantedPermissions().containsAll(readPermissions)

    fun supportsBackgroundReads(): Boolean =
        status() == HealthStatus.AVAILABLE &&
            client.features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND) ==
            HealthConnectFeatures.FEATURE_STATUS_AVAILABLE

    suspend fun canReadInBackground(): Boolean =
        supportsBackgroundReads() && grantedPermissions().containsAll(readPermissions + backgroundPermission)

    private val latestState = kotlinx.coroutines.flow.MutableStateFlow<DailyActivity?>(null)

    /** The newest figures read by anyone (the Health screen, a nudge check). Widgets follow it. */
    val latest: kotlinx.coroutines.flow.StateFlow<DailyActivity?> = latestState

    /** Today's totals from midnight until now, or null if not connected. */
    suspend fun today(now: ZonedDateTime = ZonedDateTime.now()): DailyActivity? {
        if (!isConnected()) return null
        val start = now.toLocalDate().atStartOfDay(now.zone).toInstant()
        val result = client.aggregate(
            AggregateRequest(
                metrics = setOf(
                    StepsRecord.COUNT_TOTAL,
                    DistanceRecord.DISTANCE_TOTAL,
                    ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL,
                    ExerciseSessionRecord.EXERCISE_DURATION_TOTAL,
                ),
                timeRangeFilter = TimeRangeFilter.between(start, now.toInstant()),
            ),
        )
        return DailyActivity(
            steps = result[StepsRecord.COUNT_TOTAL] ?: 0L,
            distanceMeters = result[DistanceRecord.DISTANCE_TOTAL]?.inMeters ?: 0.0,
            activeKcal = result[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories ?: 0.0,
            exerciseMinutes = result[ExerciseSessionRecord.EXERCISE_DURATION_TOTAL]?.toMinutes() ?: 0L,
            asOf = now.toInstant(),
        ).also { latestState.value = it }
    }

    /**
     * Daily totals for the last [days] days including today, oldest first (for charts).
     * Days with no data are present with zeros. Empty list if not connected.
     */
    suspend fun history(days: Int = 7, now: ZonedDateTime = ZonedDateTime.now()): List<DayActivity> {
        if (!isConnected()) return emptyList()
        val firstDay = now.toLocalDate().minusDays((days - 1).toLong())
        val groups = client.aggregateGroupByPeriod(
            AggregateGroupByPeriodRequest(
                metrics = setOf(
                    StepsRecord.COUNT_TOTAL,
                    DistanceRecord.DISTANCE_TOTAL,
                    ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL,
                    ExerciseSessionRecord.EXERCISE_DURATION_TOTAL,
                ),
                timeRangeFilter = TimeRangeFilter.between(firstDay.atStartOfDay(), now.toLocalDateTime()),
                timeRangeSlicer = Period.ofDays(1),
            ),
        ).associateBy { it.startTime.toLocalDate() }
        return (0 until days).map { i ->
            val day = firstDay.plusDays(i.toLong())
            val r = groups[day]?.result
            DayActivity(
                date = day,
                steps = r?.get(StepsRecord.COUNT_TOTAL) ?: 0L,
                distanceMeters = r?.get(DistanceRecord.DISTANCE_TOTAL)?.inMeters ?: 0.0,
                activeKcal = r?.get(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)?.inKilocalories ?: 0.0,
                exerciseMinutes = r?.get(ExerciseSessionRecord.EXERCISE_DURATION_TOTAL)?.toMinutes() ?: 0L,
            )
        }
    }

    /**
     * Re-reads today's totals every [periodMillis] while collected. Emits null only once access
     * is confirmed gone; a failed read (including a failed permission check) keeps the last good
     * figures on screen and sets [readFailed].
     */
    fun todayFlow(periodMillis: Long = 60_000): Flow<DailyActivity?> = flow {
        var last: DailyActivity? = null
        while (true) {
            try {
                // WidgetSync watches [latest] (set by today()) and keeps the Health widget current.
                last = today()
                readFailedState.value = false
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.w("PixelHub", "Health Connect read failed", e)
                readFailedState.value = true
                // Past midnight, yesterday's figures must not pose as today's.
                if (last?.asOf?.atZone(ZoneId.systemDefault())?.toLocalDate() != LocalDate.now()) last = null
            }
            emit(last)
            delay(periodMillis)
        }
    }.flowOn(Dispatchers.IO)

    private val readFailedState = kotlinx.coroutines.flow.MutableStateFlow(false)

    /** True when the latest read of today's totals failed; the screens say so instead of "Reading…". */
    val readFailed: kotlinx.coroutines.flow.StateFlow<Boolean> = readFailedState

    /** Background check for a nudge (called from the alarm receiver). */
    suspend fun check(nudge: HealthNudge): NudgeCheck {
        // An alarm never fires early, so a clock before the nudge's time means it was handled
        // after midnight: today's totals would be the new day's, so skip rather than nag.
        if (LocalTime.now() < LocalTime.of(nudge.hour, nudge.minute)) return NudgeCheck.OnTrack
        val canRead = try {
            canReadInBackground()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            // Health Connect is briefly unreachable: skip this nudge quietly rather than
            // telling the user access is gone. It fires again at its next time.
            Log.w("PixelHub", "Health Connect permission check failed; skipping nudge", e)
            return NudgeCheck.OnTrack
        }
        if (!canRead) return NudgeCheck.NoAccess
        val activity = try {
            today()
        } catch (e: SecurityException) {
            null
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w("PixelHub", "Health Connect read failed; skipping nudge", e)
            return NudgeCheck.OnTrack
        } ?: return NudgeCheck.NoAccess
        return NudgeMath.evaluate(nudge, activity)
    }
}
