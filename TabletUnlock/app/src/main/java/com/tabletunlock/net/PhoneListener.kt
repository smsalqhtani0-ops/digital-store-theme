package com.tabletunlock.net

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.ServerSocket

/**
 * الجوال في وضع الاستماع: يسجّل خدمة NSD ويستقبل اتصالات التابلت عبر الواي فاي،
 * ويستمع أيضاً عبر البلوتوث (RFCOMM). كل اتصال يُعالَج بـ PhoneRequestHandler.
 */
class PhoneListener(context: Context) {
    private val appContext = context.applicationContext
    private val store = com.tabletunlock.data.PairingStore(appContext, com.tabletunlock.data.AppMode.PHONE)
    private val nsd = NsdHelper(appContext)
    private val handler = PhoneRequestHandler(appContext)

    private var wifiServer: ServerSocket? = null
    private var btServer: android.bluetooth.BluetoothServerSocket? = null
    private var job: Job? = null

    fun start(scope: CoroutineScope, onLog: (String) -> Unit) {
        if (job?.isActive == true) return
        job = scope.launch {
            launch(Dispatchers.IO) { wifiLoop(onLog) }
            launch(Dispatchers.IO) { btLoop(onLog) }
        }
    }

    private suspend fun wifiLoop(onLog: (String) -> Unit) {
        try {
            val server = ServerSocket(0)
            wifiServer = server
            val name = store.ownServiceName()
            nsd.register(name, server.localPort)
            onLog("الواي فاي: يستمع باسم \"$name\" ✓")
            val scope = CoroutineScope(Dispatchers.IO)
            while (scope.isActive) {
                val socket = try {
                    server.accept()
                } catch (e: Exception) {
                    break
                }
                scope.launch {
                    val link = Link(
                        Wire.reader(socket.getInputStream()),
                        Wire.writer(socket.getOutputStream()),
                        socket,
                        "الواي فاي",
                    )
                    handler.handle(link) { onLog(it) }
                }
            }
        } catch (e: Exception) {
            onLog("تعذّر استماع الواي فاي: ${e.message}")
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    private suspend fun btLoop(onLog: (String) -> Unit) {
        if (!Bt.isReady(appContext)) {
            onLog("البلوتوث غير مهيأ (مطفأ أو بلا إذن) — الاستماع عبر الواي فاي فقط.")
            return
        }
        val server = Bt.serverSocket(appContext) ?: return
        btServer = server
        onLog("البلوتوث: يستمع ✓")
        val scope = CoroutineScope(Dispatchers.IO)
        while (scope.isActive) {
            val socket = try {
                server.accept()
            } catch (e: Exception) {
                break
            }
            scope.launch {
                val link = Link(
                    Wire.reader(socket.inputStream),
                    Wire.writer(socket.outputStream),
                    socket,
                    "البلوتوث",
                )
                handler.handle(link) { onLog(it) }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        nsd.unregister()
        runCatching { wifiServer?.close() }
        runCatching { btServer?.close() }
        wifiServer = null
        btServer = null
    }
}
