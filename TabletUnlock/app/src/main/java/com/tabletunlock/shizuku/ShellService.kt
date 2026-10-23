package com.tabletunlock.shizuku

import com.tabletunlock.IShellService
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/**
 * خدمة Shizuku UserService: تعمل في عملية منفصلة بصلاحيات shell.
 * تقبل فقط أوامر من قائمة محددة، وتنفّذها مباشرة بدون sh -c لمنع حقن الأوامر.
 */
class ShellService : IShellService.Stub() {

    @Volatile
    private var lastErrorText: String = ""

    override fun destroy() {
        exitProcess(0)
    }

    override fun exec(args: Array<String>?): Int {
        if (args.isNullOrEmpty() || args[0] !in ALLOWED_BINARIES) {
            lastErrorText = "command not allowed"
            return -1
        }
        return try {
            val process = ProcessBuilder(args.toList())
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroy()
                lastErrorText = "timeout"
                return -1
            }
            val code = process.exitValue()
            lastErrorText = if (code == 0) "" else output.trim().take(500)
            code
        } catch (e: Exception) {
            lastErrorText = "${e.javaClass.simpleName}: ${e.message}"
            -1
        }
    }

    override fun lastError(): String = lastErrorText

    companion object {
        const val INPUT = "/system/bin/input"
        const val WM = "/system/bin/wm"
        private val ALLOWED_BINARIES = setOf(INPUT, WM)
        private const val TIMEOUT_SECONDS = 15L
    }
}
