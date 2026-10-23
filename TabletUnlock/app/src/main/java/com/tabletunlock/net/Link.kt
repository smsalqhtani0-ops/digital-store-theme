package com.tabletunlock.net

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.Closeable

/** قناة اتصال موحّدة فوق أي نقل (بلوتوث أو socket واي فاي). */
class Link(
    val reader: BufferedReader,
    val writer: BufferedWriter,
    private val socket: Closeable,
    val transport: String,
) : Closeable {
    override fun close() {
        runCatching { socket.close() }
    }
}
