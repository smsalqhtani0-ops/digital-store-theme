package com.tabletunlock.net

import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets

/** رسائل JSON مفصولة بأسطر فوق socket. كل رسالة سطر واحد UTF-8 ينتهي بـ \n. */
object Wire {
    fun writer(out: OutputStream): BufferedWriter =
        out.bufferedWriter(StandardCharsets.UTF_8)

    fun reader(input: InputStream): BufferedReader =
        input.bufferedReader(StandardCharsets.UTF_8)

    fun send(writer: BufferedWriter, message: JSONObject) {
        writer.write(message.toString())
        writer.write("\n")
        writer.flush()
    }

    fun receive(reader: BufferedReader): JSONObject? =
        reader.readLine()?.let { JSONObject(it) }
}
