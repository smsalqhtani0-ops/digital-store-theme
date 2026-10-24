package com.tabletunlock.lock

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.tabletunlock.admin.LockAdminReceiver

/** إدارة مشرف الجهاز وقفل الشاشة بدون Shizuku. */
object AppLockManager {
    private fun component(context: Context) = ComponentName(context, LockAdminReceiver::class.java)

    private fun dpm(context: Context) =
        context.getSystemService(DevicePolicyManager::class.java)

    fun isAdminActive(context: Context): Boolean =
        dpm(context).isAdminActive(component(context))

    fun enableAdminIntent(context: Context, explanation: String): Intent =
        Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component(context))
            .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, explanation)

    /** يقفل الشاشة (يطفئها). عند الإضاءة التالية تظهر شاشة قفل التطبيق. */
    fun lockNow(context: Context): Boolean {
        if (!isAdminActive(context)) return false
        return try {
            dpm(context).lockNow()
            true
        } catch (e: Exception) {
            false
        }
    }
}
