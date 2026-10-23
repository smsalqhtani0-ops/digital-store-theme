package com.tabletunlock.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tabletunlock.R
import com.tabletunlock.data.AppMode

@Composable
fun ModeSelectScreen(onSelect: (AppMode) -> Unit) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Text(stringResource(R.string.mode_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.mode_subtitle), style = MaterialTheme.typography.bodyMedium)
            ModeCard(R.string.mode_tablet, R.string.mode_tablet_desc) { onSelect(AppMode.TABLET) }
            ModeCard(R.string.mode_phone, R.string.mode_phone_desc) { onSelect(AppMode.PHONE) }
        }
    }
}

@Composable
private fun ModeCard(titleRes: Int, descRes: Int, onClick: () -> Unit) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 480.dp),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(titleRes), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(descRes), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
