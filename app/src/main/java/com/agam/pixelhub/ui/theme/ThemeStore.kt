package com.agam.pixelhub.ui.theme

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The chosen theme. Kept in SharedPreferences rather than DataStore so it can be read
 * synchronously when an activity starts: the first frame is already in the right colours,
 * with no flash of the default theme.
 */
object ThemeStore {
    private const val PREFS = "theme"
    private const val KEY = "app_theme"

    @Volatile private var state: MutableStateFlow<AppTheme>? = null

    fun theme(context: Context): StateFlow<AppTheme> = flow(context).asStateFlow()

    fun set(context: Context, theme: AppTheme) {
        prefs(context).edit().putString(KEY, theme.name).apply()
        flow(context).value = theme
    }

    private fun flow(context: Context): MutableStateFlow<AppTheme> =
        state ?: synchronized(this) {
            state ?: MutableStateFlow(read(context)).also { state = it }
        }

    private fun read(context: Context): AppTheme {
        val name = prefs(context).getString(KEY, null)
        return AppTheme.entries.firstOrNull { it.name == name } ?: AppTheme.SYSTEM
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
