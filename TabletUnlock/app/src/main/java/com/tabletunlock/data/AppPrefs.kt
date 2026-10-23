package com.tabletunlock.data

import android.content.Context

enum class AppMode { TABLET, PHONE }

enum class UnlockMethod { SWIPE, DISMISS_KEYGUARD }

class AppPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    var mode: AppMode?
        get() = prefs.getString(KEY_MODE, null)?.let { name ->
            AppMode.entries.firstOrNull { it.name == name }
        }
        set(value) {
            prefs.edit().apply {
                if (value == null) remove(KEY_MODE) else putString(KEY_MODE, value.name)
            }.apply()
        }

    var unlockMethod: UnlockMethod
        get() = prefs.getString(KEY_METHOD, null)?.let { name ->
            UnlockMethod.entries.firstOrNull { it.name == name }
        } ?: UnlockMethod.SWIPE
        set(value) {
            prefs.edit().putString(KEY_METHOD, value.name).apply()
        }

    private companion object {
        const val KEY_MODE = "mode"
        const val KEY_METHOD = "unlock_method"
    }
}
