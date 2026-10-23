package com.tabletunlock.net

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * اكتشاف الخدمة على الشبكة المحلية عبر mDNS/NSD.
 * الجوال يسجّل خدمة باسم فريد؛ التابلت يكتشفها ويحلّ عنوانها.
 */
class NsdHelper(context: Context) {
    private val nsd = context.applicationContext.getSystemService(NsdManager::class.java)
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    fun register(serviceName: String, port: Int) {
        unregister()
        val info = NsdServiceInfo().apply {
            this.serviceName = serviceName
            this.serviceType = SERVICE_TYPE
            this.port = port
        }
        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {}
            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {}
            override fun onServiceUnregistered(info: NsdServiceInfo) {}
            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {}
        }
        nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, registrationListener)
    }

    fun unregister() {
        registrationListener?.let { runCatching { nsd.unregisterService(it) } }
        registrationListener = null
    }

    /** يكتشف الخدمة ذات الاسم المطلوب ويعيد (host, port)، أو null عند انتهاء المهلة. */
    suspend fun resolveOnce(targetName: String, timeoutMs: Long = 8000): Pair<String, Int>? =
        withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val listener = object : NsdManager.DiscoveryListener {
                    override fun onDiscoveryStarted(serviceType: String) {}
                    override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                        if (cont.isActive) cont.resume(null)
                    }
                    override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
                    override fun onDiscoveryStopped(serviceType: String) {}
                    override fun onServiceLost(info: NsdServiceInfo) {}

                    override fun onServiceFound(info: NsdServiceInfo) {
                        if (info.serviceName != targetName) return
                        nsd.resolveService(info, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {}
                            override fun onServiceResolved(resolved: NsdServiceInfo) {
                                val host = resolved.host?.hostAddress
                                if (host != null && cont.isActive) {
                                    cont.resume(host to resolved.port)
                                }
                            }
                        })
                    }
                }
                discoveryListener = listener
                nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
                cont.invokeOnCancellation { stopDiscovery() }
            }
        }.also { stopDiscovery() }

    fun stopDiscovery() {
        discoveryListener?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        discoveryListener = null
    }

    companion object {
        const val SERVICE_TYPE = "_tabletunlock._tcp."
    }
}
