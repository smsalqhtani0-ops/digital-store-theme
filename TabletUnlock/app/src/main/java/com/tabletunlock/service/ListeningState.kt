package com.tabletunlock.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** حالة استماع الجوال، مشتركة بين الخدمة والواجهة. */
object ListeningState {
    val running = MutableStateFlow(false)
    val log = MutableStateFlow<List<String>>(emptyList())

    fun appendLog(line: String) = log.update { (it + line).takeLast(30) }
    fun resetLog() = log.update { emptyList() }
}
