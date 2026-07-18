package com.personal.hydra.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.personal.hydra.R
import com.personal.hydra.ui.components.SectionCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutRoute(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
            SectionCard(title = stringResource(R.string.about_offline)) {
                Disclaimer(stringResource(R.string.about_pure_water))
                Disclaimer(stringResource(R.string.about_activity))
                Disclaimer(stringResource(R.string.about_hourly_cap))
                Disclaimer(stringResource(R.string.about_season_limit))
                Disclaimer(stringResource(R.string.about_balance))
            }
        }
    }
}

@Composable
private fun Disclaimer(text: String) {
    Text("•  $text", style = MaterialTheme.typography.bodyMedium)
}
