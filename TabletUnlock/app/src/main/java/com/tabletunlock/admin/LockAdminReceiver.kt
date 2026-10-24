package com.tabletunlock.admin

import android.app.admin.DeviceAdminReceiver

/** مشرف جهاز بسيط، يمنح صلاحية قفل الشاشة (force-lock) بدون Shizuku. */
class LockAdminReceiver : DeviceAdminReceiver()
