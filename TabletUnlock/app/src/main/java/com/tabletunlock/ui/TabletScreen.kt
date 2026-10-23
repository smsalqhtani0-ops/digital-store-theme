package com.tabletunlock.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tabletunlock.R
import com.tabletunlock.TabletViewModel
import com.tabletunlock.data.UnlockMethod
import com.tabletunlock.shizuku.ShizukuState

private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabletScreen(onChangeMode: () -> Unit, vm: TabletViewModel = viewModel()) {
    val shizuku by vm.shizukuState.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshShizuku() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.mode_tablet)) },
                actions = {
                    TextButton(onClick = onChangeMode, enabled = !ui.busy) {
                        Text(stringResource(R.string.change_mode))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val sectionModifier = Modifier.widthIn(max = 640.dp)
            Column(sectionModifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ShizukuSection(shizuku, onGrant = vm::requestShizukuPermission, onRefresh = vm::refreshShizuku)
                PinSection(
                    hasPin = ui.hasPin,
                    enabled = !ui.busy,
                    onSave = vm::savePin,
                    onDelete = vm::clearPin,
                )
                MethodSection(ui.method, enabled = !ui.busy, onSelect = vm::setMethod)
                TestSection(
                    canRun = shizuku == ShizukuState.READY && ui.hasPin && !ui.busy,
                    busy = ui.busy,
                    countdown = ui.countdown,
                    log = ui.log,
                    onRun = vm::runTest,
                )
            }
        }
    }
}

@Composable
private fun ShizukuSection(state: ShizukuState, onGrant: () -> Unit, onRefresh: () -> Unit) {
    val context = LocalContext.current
    SectionCard(stringResource(R.string.shizuku_title)) {
        val text = when (state) {
            ShizukuState.NOT_RUNNING -> R.string.shizuku_not_running
            ShizukuState.UNSUPPORTED -> R.string.shizuku_unsupported
            ShizukuState.NO_PERMISSION -> R.string.shizuku_no_permission
            ShizukuState.DENIED_FOREVER -> R.string.shizuku_denied_forever
            ShizukuState.CONNECTING -> R.string.shizuku_connecting
            ShizukuState.READY -> R.string.shizuku_ready
        }
        Text(
            stringResource(text),
            color = if (state == ShizukuState.READY) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when (state) {
                ShizukuState.NO_PERMISSION ->
                    Button(onClick = onGrant) { Text(stringResource(R.string.shizuku_grant)) }
                ShizukuState.NOT_RUNNING, ShizukuState.UNSUPPORTED, ShizukuState.DENIED_FOREVER ->
                    Button(onClick = { openShizuku(context) }) { Text(stringResource(R.string.shizuku_open_app)) }
                else -> Unit
            }
            OutlinedButton(onClick = onRefresh) { Text(stringResource(R.string.refresh)) }
        }
    }
}

private fun openShizuku(context: Context) {
    context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)?.let(context::startActivity)
}

@Composable
private fun PinSection(
    hasPin: Boolean,
    enabled: Boolean,
    onSave: (CharArray) -> Boolean,
    onDelete: () -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    SectionCard(stringResource(R.string.pin_title)) {
        if (hasPin && !editing) {
            Text(stringResource(R.string.pin_saved))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { editing = true }, enabled = enabled) {
                    Text(stringResource(R.string.change))
                }
                OutlinedButton(onClick = onDelete, enabled = enabled) {
                    Text(stringResource(R.string.delete))
                }
            }
        } else {
            OutlinedTextField(
                value = input,
                onValueChange = { value ->
                    input = value.filter(Char::isDigit).take(16)
                    error = false
                },
                label = { Text(stringResource(R.string.pin_hint)) },
                singleLine = true,
                isError = error,
                supportingText = if (error) {
                    { Text(stringResource(R.string.pin_invalid)) }
                } else {
                    null
                },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val pin = input.toCharArray()
                        val saved = onSave(pin)
                        pin.fill('\u0000')
                        if (saved) {
                            input = ""
                            editing = false
                        } else {
                            error = true
                        }
                    },
                    enabled = enabled && input.isNotEmpty(),
                ) { Text(stringResource(R.string.save)) }
                if (hasPin) {
                    OutlinedButton(onClick = {
                        input = ""
                        error = false
                        editing = false
                    }) { Text(stringResource(R.string.cancel)) }
                }
            }
        }
    }
}

@Composable
private fun MethodSection(selected: UnlockMethod, enabled: Boolean, onSelect: (UnlockMethod) -> Unit) {
    SectionCard(stringResource(R.string.method_title)) {
        MethodOption(R.string.method_swipe, selected == UnlockMethod.SWIPE, enabled) {
            onSelect(UnlockMethod.SWIPE)
        }
        MethodOption(R.string.method_dismiss, selected == UnlockMethod.DISMISS_KEYGUARD, enabled) {
            onSelect(UnlockMethod.DISMISS_KEYGUARD)
        }
    }
}

@Composable
private fun MethodOption(labelRes: Int, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Text(stringResource(labelRes), modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun TestSection(
    canRun: Boolean,
    busy: Boolean,
    countdown: Int,
    log: List<String>,
    onRun: () -> Unit,
) {
    SectionCard(stringResource(R.string.test_title)) {
        Text(stringResource(R.string.test_desc), style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onRun, enabled = canRun, modifier = Modifier.fillMaxWidth()) {
            Text(
                when {
                    countdown > 0 -> "$countdown…"
                    busy -> stringResource(R.string.test_running)
                    else -> stringResource(R.string.test_button)
                },
            )
        }
        log.forEach { line ->
            Text(line, style = MaterialTheme.typography.bodySmall)
        }
    }
}
