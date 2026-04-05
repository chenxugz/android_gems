package com.gems.android.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import android.content.Intent
import android.net.Uri
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelDownloadScreen(
    onBack: () -> Unit,
    viewModel: ModelDownloadViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Model Manager") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Download models to run on device. Files are saved to app storage.",
                style = MaterialTheme.typography.bodyMedium,
            )

            val context = LocalContext.current
            uiState.models.forEach { model ->
                ModelCard(
                    model = model,
                    onDownload = { viewModel.downloadModel(model.id) },
                    onOpenLicensePage = {
                        if (model.licensePage.isNotEmpty()) {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(model.licensePage)))
                        }
                    },
                )
            }

            if (uiState.models.all { it.downloaded }) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "All models downloaded. You're ready to go!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun ModelCard(
    model: ModelDownloadInfo,
    onDownload: () -> Unit,
    onOpenLicensePage: () -> Unit = {},
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (model.downloaded)
                MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = model.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = model.description,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = model.sizeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (model.downloaded) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = "Downloaded",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (model.downloading) {
                Spacer(modifier = Modifier.height(8.dp))
                if (model.progress > 0f) {
                    LinearProgressIndicator(
                        progress = { model.progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "${(model.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        text = "Starting download...",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }

            if (model.error != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = model.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (!model.downloaded && !model.downloading) {
                Spacer(modifier = Modifier.height(8.dp))
                if (model.requiresAuth && model.licensePage.isNotEmpty()) {
                    OutlinedButton(
                        onClick = onOpenLicensePage,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("1. Accept License (opens browser)")
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = onDownload,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null)
                        Text("  2. Download (after accepting)")
                    }
                } else {
                    Button(
                        onClick = onDownload,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null)
                        Text("  Download")
                    }
                }
            }
        }
    }
}
