package com.tabletunlock.data

import android.content.Context

enum class AppMode { TABLET, PHONE }

enum class UnlockMethod { SWIPE, DISMISS_KEYGUARD }

/** طريقة قفل التابلت: عبر Shizuku (قفل النظام) أو قفل التطبيق (بدون Shizuku). */
enum class TabletLockMode { SHIZUKU, APP_LOCK }

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


    var autoUnlockEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO, value).apply()

    var listeningEnabled: Boolean
        get() = prefs.getBoolean(KEY_LISTEN, false)
        set(value) = prefs.edit().putBoolean(KEY_LISTEN, value).apply()

    var receiveFromPhoneEnabled: Boolean
        get() = prefs.getBoolean(KEY_RECEIVE, false)
        set(value) = prefs.edit().putBoolean(KEY_RECEIVE, value).apply()

    var lockMode: TabletLockMode
        get() = prefs.getString(KEY_LOCK_MODE, null)?.let { name ->
            TabletLockMode.entries.firstOrNull { it.name == name }
        } ?: TabletLockMode.SHIZUKU
        set(value) = prefs.edit().putString(KEY_LOCK_MODE, value.name).apply()

    private companion object {
        const val KEY_MODE = "mode"
        const val KEY_METHOD = "unlock_method"
        const val KEY_AUTO = "auto_unlock"
        const val KEY_LISTEN = "listening"
        const val KEY_RECEIVE = "receive_from_phone"
        const val KEY_LOCK_MODE = "lock_mode"
    }
}
