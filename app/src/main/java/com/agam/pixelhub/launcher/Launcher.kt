package com.agam.pixelhub.launcher

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import com.agam.pixelhub.R
import com.agam.pixelhub.data.AppShortcut
import com.agam.pixelhub.data.ShortcutKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppEntry(val label: String, val packageName: String)

/** Launchable apps on the phone (visible via the MAIN/LAUNCHER <queries> entry). */
class InstalledApps(private val context: Context) {
    private val pm = context.packageManager
    private val icons = LruCache<String, ImageBitmap>(256)

    @Volatile private var cached: List<AppEntry>? = null

    suspend fun list(refresh: Boolean = false): List<AppEntry> {
        if (!refresh) cached?.let { return it }
        return withContext(Dispatchers.IO) {
            val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(launcher, PackageManager.ResolveInfoFlags.of(0))
                .map { AppEntry(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
                .filter { it.packageName != context.packageName }
                .distinctBy { it.packageName }
                .sortedBy { it.label.lowercase() }
                .also { cached = it }
        }
    }

    fun cachedIcon(packageName: String): ImageBitmap? = icons.get(packageName)

    /** Loads (and caches) an app icon. Call off the main thread. */
    fun icon(packageName: String): ImageBitmap? = icons.get(packageName) ?: try {
        pm.getApplicationIcon(packageName).toBitmap(ICON_PX, ICON_PX).asImageBitmap()
            .also { icons.put(packageName, it) }
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    private companion object {
        const val ICON_PX = 144
    }
}

object ShortcutLauncher {
    fun intentFor(context: Context, s: AppShortcut): Intent? = when (s.kind) {
        ShortcutKind.APP -> s.packageName?.let { context.packageManager.getLaunchIntentForPackage(it) }
        ShortcutKind.LINK -> s.uri?.let { uri ->
            Intent(Intent.ACTION_VIEW, uri.trim().toUri()).apply { s.packageName?.let { setPackage(it) } }
        }
    }?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun launch(context: Context, s: AppShortcut): Boolean {
        val intent = intentFor(context, s) ?: return false
        if (start(context, intent)) return true
        // A link pinned to an app that is gone: let Android pick another handler.
        return s.kind == ShortcutKind.LINK && intent.`package` != null && start(context, intent.setPackage(null))
    }

    fun openUri(context: Context, uri: String): Boolean =
        start(context, Intent(Intent.ACTION_VIEW, uri.trim().toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    /** Asks the launcher to pin this shortcut to the home screen. */
    fun pinToHomeScreen(context: Context, s: AppShortcut): Boolean {
        val sm = context.getSystemService(ShortcutManager::class.java)
        if (!sm.isRequestPinShortcutSupported) return false
        val intent = intentFor(context, s) ?: return false
        val bitmap = s.packageName?.let { pkg ->
            runCatching { context.packageManager.getApplicationIcon(pkg).toBitmap(192, 192) }.getOrNull()
        }
        val icon = bitmap?.let { Icon.createWithBitmap(it) } ?: Icon.createWithResource(context, R.mipmap.ic_launcher)
        val info = ShortcutInfo.Builder(context, "shortcut_${s.id}")
            .setShortLabel(s.label.take(24))
            .setIcon(icon)
            .setIntent(intent)
            .build()
        return sm.requestPinShortcut(info, null)
    }

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    } catch (e: RuntimeException) {
        // e.g. FileUriExposedException for a file:// link: report it instead of crashing.
        false
    }
}
