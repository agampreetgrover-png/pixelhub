package com.agam.pixelhub.health

import android.content.Context
import android.content.Intent
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

    /** Health Connect's page for PixelHub's permissions (to review or revoke). */
    fun managePermissionsIntent(): Intent =
        Intent(HealthConnectManager.ACTION_MANAGE_HEALTH_PERMISSIONS)
            .putExtra(Intent.EXTRA_PACKAGE_NAME, context.packageName)

    suspend fun grantedPermissions(): Set<String> =
        if (status() != HealthStatus.AVAILABLE) emptySet()
        else runCatching { client.permissionController.getGrantedPermissions() }.getOrDefault(emptySet())

    /** True once every data type in [readPermissions] is allowed. */
    suspend fun isConnected(): Boolean = grantedPermissions().containsAll(readPermissions)

    fun supportsBackgroundReads(): Boolean =
        status() == HealthStatus.AVAILABLE &&
            client.features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND) ==
            HealthConnectFeatures.FEATURE_STATUS_AVAILABLE

    suspend fun canReadInBackground(): Boolean =
        supportsBackgroundReads() && grantedPermissions().containsAll(readPermissions + backgroundPermission)

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
        )
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

    /** Re-reads today's totals every [periodMillis] while collected. Emits null when not connected. */
    fun todayFlow(periodMillis: Long = 60_000): Flow<DailyActivity?> = flow {
        // A failed read keeps the last good figures on screen instead of dropping them to zero.
        var last: DailyActivity? = null
        while (true) {
            try {
                last = today()
                readFailedState.value = false
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.w("PixelHub", "Health Connect read failed", e)
                readFailedState.value = true
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
        if (!canReadInBackground()) return NudgeCheck.NoAccess
        val activity = try {
            today()
        } catch (e: SecurityException) {
            null
        } ?: return NudgeCheck.NoAccess
        return NudgeMath.evaluate(nudge, activity)
    }
}
