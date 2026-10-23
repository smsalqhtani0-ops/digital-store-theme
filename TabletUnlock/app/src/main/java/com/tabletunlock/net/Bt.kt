package com.tabletunlock.net

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.util.UUID

/** مساعد البلوتوث الكلاسيكي (RFCOMM) باستخدام UUID ثابت للخدمة. */
object Bt {
    val SERVICE_UUID: UUID = UUID.fromString("7b2a5c10-6d4e-4f21-9a3b-a1b2c3d4e5f6")
    const val SDP_NAME = "TabletUnlock"

    fun adapter(context: Context): BluetoothAdapter? =
        context.getSystemService(BluetoothManager::class.java)?.adapter

    fun hasPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun isReady(context: Context): Boolean {
        if (!hasPermission(context)) return false
        val a = adapter(context) ?: return false
        return a.isEnabled
    }

    @SuppressLint("MissingPermission")
    fun ownName(context: Context): String? {
        if (!hasPermission(context)) return null
        return adapter(context)?.name
    }

    @SuppressLint("MissingPermission")
    fun bondedByName(context: Context, name: String): android.bluetooth.BluetoothDevice? {
        if (!hasPermission(context)) return null
        return adapter(context)?.bondedDevices?.firstOrNull { it.name == name }
    }

    @SuppressLint("MissingPermission")
    fun connect(context: Context, peerName: String): Link? {
        val adapter = adapter(context) ?: return null
        if (!isReady(context)) return null
        val device = bondedByName(context, peerName) ?: return null
        return try {
            val socket = device.createRfcommSocketToServiceRecord(SERVICE_UUID)
            adapter.cancelDiscovery()
            socket.connect()
            Link(
                reader = Wire.reader(socket.inputStream),
                writer = Wire.writer(socket.outputStream),
                socket = socket,
                transport = "البلوتوث",
            )
        } catch (e: Exception) {
            null
        }
    }

    @SuppressLint("MissingPermission")
    fun serverSocket(context: Context): BluetoothServerSocket? {
        if (!isReady(context)) return null
        return try {
            adapter(context)?.listenUsingRfcommWithServiceRecord(SDP_NAME, SERVICE_UUID)
        } catch (e: Exception) {
            null
        }
    }
}
