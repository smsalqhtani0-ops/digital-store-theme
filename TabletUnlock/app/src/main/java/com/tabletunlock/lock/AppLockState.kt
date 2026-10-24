package com.tabletunlock.lock

import kotlinx.coroutines.flow.MutableSharedFlow

/** إشارة لإنهاء شاشة قفل التطبيق عند الموافقة على الفتح (من أي اتجاه). */
object AppLockState {
    val unlockSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    fun signalUnlock() {
        unlockSignal.tryEmit(Unit)
    }
}
