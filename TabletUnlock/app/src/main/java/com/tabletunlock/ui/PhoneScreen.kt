package com.tabletunlock.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.tabletunlock.PhoneViewModel
import com.tabletunlock.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneScreen(onChangeMode: () -> Unit, vm: PhoneViewModel = viewModel()) {
    val paired by vm.paired.collectAsStateWithLifecycle()
    val listening by vm.listening.collectAsStateWithLifecycle()
    val log by vm.log.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var canOverlay by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { canOverlay = Settings.canDrawOverlays(context) }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let(vm::onScanned)
    }
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { vm.startListening() }
    val btLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            btLauncher.launch(android.Manifest.permission.BLUETOOTH_CONNECT)
        }
    }

    fun enableListening() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            vm.startListening()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.phone_title)) },
                actions = { TextButton(onClick = onChangeMode) { Text(stringResource(R.string.change_mode)) } },
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
            Column(Modifier.widthIn(max = 640.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SectionCard(stringResource(R.string.pair_title)) {
                    if (paired) {
                        Text("مقترن مع التابلت ✓", color = MaterialTheme.colorScheme.primary)
                    } else {
                        Text(stringResource(R.string.pair_none))
                    }
                    Text(stringResource(R.string.bt_hint), style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            scanLauncher.launch(
                                ScanOptions().setBeepEnabled(false).setOrientationLocked(false).setPrompt(""),
                            )
                        }) { Text(stringResource(R.string.pair_scan)) }
                        if (paired) {
                            OutlinedButton(onClick = vm::unpair) { Text(stringResource(R.string.unpair)) }
                        }
                    }
                }

                SectionCard(stringResource(R.string.bg_title)) {
                    if (!paired) {
                        Text(stringResource(R.string.phone_need_pair))
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.bg_switch),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Switch(
                                checked = listening,
                                onCheckedChange = { if (it) enableListening() else vm.stopListening() },
                            )
                        }
                        Text(
                            stringResource(if (listening) R.string.bg_on else R.string.bg_off),
                            color = if (listening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(stringResource(R.string.bg_battery), style = MaterialTheme.typography.bodySmall)
                        if (canOverlay) {
                            Text(
                                stringResource(R.string.overlay_ok),
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        } else {
                            Text(stringResource(R.string.overlay_needed), style = MaterialTheme.typography.bodySmall)
                            OutlinedButton(onClick = {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:" + context.packageName),
                                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }) { Text(stringResource(R.string.overlay_grant)) }
                        }
                    }
                    log.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}
