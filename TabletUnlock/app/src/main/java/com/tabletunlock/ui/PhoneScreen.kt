package com.tabletunlock.ui

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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.tabletunlock.PhoneViewModel
import com.tabletunlock.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneScreen(onChangeMode: () -> Unit, vm: PhoneViewModel = viewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let(vm::onScanned)
    }
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { vm.toggleListening() }

    fun onToggleListening() {
        if (!ui.listening && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            vm.toggleListening()
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
                    if (ui.paired) {
                        Text("مقترن مع التابلت ✓", color = MaterialTheme.colorScheme.primary)
                    } else {
                        Text(stringResource(R.string.pair_none))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            scanLauncher.launch(
                                ScanOptions()
                                    .setBeepEnabled(false)
                                    .setOrientationLocked(false)
                                    .setPrompt(""),
                            )
                        }) { Text(stringResource(R.string.pair_scan)) }
                        if (ui.paired) {
                            OutlinedButton(onClick = vm::unpair) { Text(stringResource(R.string.unpair)) }
                        }
                    }
                }

                SectionCard(stringResource(R.string.listen_title)) {
                    if (!ui.paired) {
                        Text(stringResource(R.string.phone_need_pair))
                    } else {
                        Text(stringResource(R.string.listen_desc), style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = { onToggleListening() }, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                stringResource(
                                    if (ui.listening) R.string.listen_stop else R.string.listen_start,
                                ),
                            )
                        }
                    }
                    ui.log.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}
