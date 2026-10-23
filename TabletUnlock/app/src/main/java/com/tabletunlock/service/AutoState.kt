package com.tabletunlock.service

import kotlinx.coroutines.flow.MutableStateFlow

/** حالة التشغيل التلقائي في التابلت، مشتركة بين الخدمة والواجهة. */
object AutoState {
    val running = MutableStateFlow(false)
    val lastStatus = MutableStateFlow("")
}
