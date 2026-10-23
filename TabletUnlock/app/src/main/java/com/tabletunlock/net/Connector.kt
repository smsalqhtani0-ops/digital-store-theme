package com.tabletunlock.net

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

/** يفتح قناة للطرف الآخر: البلوتوث أولاً، فإن تعذّر فالواي فاي (NSD). */
class Connector(context: Context) {
    private val appContext = context.applicationContext
    private val nsd = NsdHelper(appContext)

    suspend fun open(
        peerBtName: String?,
        peerServiceName: String?,
        onLog: (String) -> Unit,
    ): Link? = withContext(Dispatchers.IO) {
        if (peerBtName != null && Bt.isReady(appContext)) {
            onLog("محاولة الاتصال عبر البلوتوث…")
            val link = Bt.connect(appContext, peerBtName)
            if (link != null) {
                onLog("متصل عبر البلوتوث ✓")
                return@withContext link
            }
            onLog("تعذّر البلوتوث، تجربة الواي فاي…")
        }
        if (peerServiceName != null) {
            onLog("البحث عن الجوال عبر الواي فاي…")
            val target = nsd.resolveOnce(peerServiceName)
            if (target != null) {
                try {
                    val socket = Socket()
                    socket.connect(InetSocketAddress(target.first, target.second), 8000)
                    onLog("متصل عبر الواي فاي ✓ (${target.first})")
                    return@withContext Link(
                        reader = Wire.reader(socket.getInputStream()),
                        writer = Wire.writer(socket.getOutputStream()),
                        socket = socket,
                        transport = "الواي فاي",
                    )
                } catch (e: Exception) {
                    onLog("فشل اتصال الواي فاي: ${e.message}")
                }
            } else {
                onLog("لم يُعثر على الجوال عبر الواي فاي.")
            }
        }
        null
    }
}
