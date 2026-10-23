package com.tabletunlock.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import com.tabletunlock.BuildConfig
import com.tabletunlock.IShellService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

enum class ShizukuState { NOT_RUNNING, UNSUPPORTED, NO_PERMISSION, DENIED_FOREVER, CONNECTING, READY }

data class ShellResult(val exitCode: Int, val error: String) {
    val ok: Boolean get() = exitCode == 0
}

object ShizukuManager {
    private const val PERMISSION_REQUEST_CODE = 1001
    private const val MIN_VERSION = 11

    private val _state = MutableStateFlow(ShizukuState.NOT_RUNNING)
    val state: StateFlow<ShizukuState> = _state.asStateFlow()

    @Volatile
    private var shell: IShellService? = null
    private var bindRequested = false
    private lateinit var userServiceArgs: Shizuku.UserServiceArgs

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            if (binder != null && binder.pingBinder()) {
                shell = IShellService.Stub.asInterface(binder)
            }
            refresh()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            shell = null
            bindRequested = false
            refresh()
        }
    }

    fun init(context: Context) {
        userServiceArgs = Shizuku.UserServiceArgs(
            ComponentName(context.packageName, ShellService::class.java.name),
        )
            .daemon(false)
            .processNameSuffix("shell")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)

        Shizuku.addBinderReceivedListenerSticky { refresh() }
        Shizuku.addBinderDeadListener {
            shell = null
            bindRequested = false
            refresh()
        }
        Shizuku.addRequestPermissionResultListener { requestCode, _ ->
            if (requestCode == PERMISSION_REQUEST_CODE) refresh()
        }
    }

    fun refresh() {
        _state.value = computeState()
    }

    private fun computeState(): ShizukuState {
        if (!Shizuku.pingBinder()) return ShizukuState.NOT_RUNNING
        if (Shizuku.isPreV11() || Shizuku.getVersion() < MIN_VERSION) return ShizukuState.UNSUPPORTED
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            return if (Shizuku.shouldShowRequestPermissionRationale()) {
                ShizukuState.DENIED_FOREVER
            } else {
                ShizukuState.NO_PERMISSION
            }
        }
        if (shell?.asBinder()?.pingBinder() == true) return ShizukuState.READY
        bindShellService()
        return ShizukuState.CONNECTING
    }

    fun requestPermission() {
        if (Shizuku.pingBinder()) Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
    }

    private fun bindShellService() {
        if (bindRequested) return
        bindRequested = true
        runCatching { Shizuku.bindUserService(userServiceArgs, connection) }
            .onFailure { bindRequested = false }
    }

    suspend fun exec(vararg args: String): ShellResult = withContext(Dispatchers.IO) {
        val service = shell ?: return@withContext ShellResult(-1, "خدمة Shizuku غير متصلة")
        try {
            val code = service.exec(arrayOf(*args))
            ShellResult(code, if (code == 0) "" else service.lastError().orEmpty())
        } catch (e: Exception) {
            ShellResult(-1, e.javaClass.simpleName)
        }
    }
}
