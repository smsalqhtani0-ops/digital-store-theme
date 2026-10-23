package com.tabletunlock.net

import android.annotation.SuppressLint
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.ServerSocket

/** يستقبل اتصالات عبر الواي فاي (NSD + socket) والبلوتوث (RFCOMM) ويمرّرها للمعالج. */
class TransportListener(
    context: Context,
    private val serviceName: String,
    private val handler: RequestHandler,
) {
    private val appContext = context.applicationContext
    private val nsd = NsdHelper(appContext)

    private var wifiServer: ServerSocket? = null
    private var btServer: android.bluetooth.BluetoothServerSocket? = null
    private var job: Job? = null

    fun start(scope: CoroutineScope, onLog: (String) -> Unit) {
        if (job?.isActive == true) return
        job = scope.launch {
            launch(Dispatchers.IO) { wifiLoop(this, onLog) }
            launch(Dispatchers.IO) { btLoop(this, onLog) }
        }
    }

    private suspend fun wifiLoop(scope: CoroutineScope, onLog: (String) -> Unit) {
        try {
            val server = ServerSocket(0)
            wifiServer = server
            nsd.register(serviceName, server.localPort)
            onLog("الواي فاي: يستمع باسم \"$serviceName\" ✓")
            while (scope.isActive) {
                val socket = try {
                    server.accept()
                } catch (e: Exception) {
                    break
                }
                scope.launch {
                    handler.handle(
                        Link(Wire.reader(socket.getInputStream()), Wire.writer(socket.getOutputStream()), socket, "الواي فاي"),
                    ) { onLog(it) }
                }
            }
        } catch (e: Exception) {
            onLog("تعذّر استماع الواي فاي: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun btLoop(scope: CoroutineScope, onLog: (String) -> Unit) {
        if (!Bt.isReady(appContext)) {
            onLog("البلوتوث غير مهيأ — الاستماع عبر الواي فاي فقط.")
            return
        }
        val server = Bt.serverSocket(appContext) ?: return
        btServer = server
        onLog("البلوتوث: يستمع ✓")
        while (scope.isActive) {
            val socket = try {
                server.accept()
            } catch (e: Exception) {
                break
            }
            scope.launch {
                handler.handle(
                    Link(Wire.reader(socket.inputStream), Wire.writer(socket.outputStream), socket, "البلوتوث"),
                ) { onLog(it) }
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
