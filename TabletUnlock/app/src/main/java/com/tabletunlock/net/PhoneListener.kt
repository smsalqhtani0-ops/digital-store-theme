package com.tabletunlock.net

import android.content.Context
import com.tabletunlock.data.AppMode
import com.tabletunlock.data.PairingStore
import kotlinx.coroutines.CoroutineScope

/** استماع الجوال: قناة موحّدة (واي فاي + بلوتوث) بمعالج الجوال. */
class PhoneListener(context: Context) {
    private val appContext = context.applicationContext
    private val store = PairingStore(appContext, AppMode.PHONE)
    private val listener = TransportListener(appContext, store.ownServiceName(), PhoneRequestHandler(appContext))

    fun start(scope: CoroutineScope, onLog: (String) -> Unit) = listener.start(scope, onLog)
    fun stop() = listener.stop()
}
