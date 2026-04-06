package com.gems.android.ui.screen

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComparisonScreen(
    prompt: String,
    steps: Int = 2,
    iterations: Int = 2,
    useE4B: Boolean = false,
    onBack: () -> Unit,
    viewModel: ComparisonViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(prompt, steps, iterations, useE4B) {
        viewModel.startComparison(prompt, steps, iterations, useE4B)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Comparison") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
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
        ) {
            // Original prompt
            PromptSection(label = "Original Prompt", text = uiState.originalPrompt)

            Spacer(modifier = Modifier.height(12.dp))

            // Direct generation result
            PipelineCard(
                modifier = Modifier.fillMaxWidth(),
                title = "Direct Generation",
                image = uiState.directImage,
                timeMs = uiState.directTimeMs,
                loading = uiState.directLoading,
                error = uiState.directError,
            )

            // GEMS agent status
            if (uiState.gemsStatus.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = uiState.gemsStatus,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            // Round-by-round images
            if (uiState.gemsRoundImages.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "GEMS Rounds",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    uiState.gemsRoundImages.forEach { roundImg ->
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                        ) {
                            Column(
                                modifier = Modifier.padding(4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = "Round ${roundImg.round}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                                Image(
                                    bitmap = roundImg.image.asImageBitmap(),
                                    contentDescription = "Round ${roundImg.round}",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f),
                                    contentScale = ContentScale.Fit,
                                )
                                if (roundImg.score != null) {
                                    Text(
                                        text = "Score: ${(roundImg.score * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // GEMS metadata
            GemsMetadataSection(
                refinedPrompt = uiState.gemsRefinedPrompt,
                verifierScore = uiState.gemsVerifierScore,
                totalIterations = uiState.gemsTotalIterations,
                usedSkill = uiState.gemsUsedSkill,
            )
        }
    }
}

@Composable
private fun PromptSection(label: String, text: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun PipelineCard(
    modifier: Modifier = Modifier,
    title: String,
    image: Bitmap?,
    timeMs: Long?,
    loading: Boolean,
    error: String?,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Image area — fixed 1:1 aspect ratio to keep both sides equal
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    loading -> {
                        CircularProgressIndicator(modifier = Modifier.size(48.dp))
                    }
                    error != null -> {
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    image != null -> {
                        Image(
                            bitmap = image.asImageBitmap(),
                            contentDescription = "$title generated image",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }
            }

            // Generation time
            if (timeMs != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatDuration(timeMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun GemsMetadataSection(
    refinedPrompt: String?,
    verifierScore: Float?,
    totalIterations: Int?,
    usedSkill: String? = null,
) {
    // Only show when at least one piece of metadata is available
    if (refinedPrompt == null && verifierScore == null && totalIterations == null && usedSkill == null) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "GEMS Agent Details",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (usedSkill != null) {
                MetadataRow(label = "Skill Used", value = usedSkill)
                Spacer(modifier = Modifier.height(4.dp))
            }

            if (refinedPrompt != null) {
                MetadataRow(label = "Refined Prompt", value = refinedPrompt)
                Spacer(modifier = Modifier.height(4.dp))
            }

            if (verifierScore != null) {
                MetadataRow(
                    label = "Verifier Score",
                    value = "%.0f%%".format(verifierScore * 100),
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            if (totalIterations != null) {
                MetadataRow(label = "Iterations", value = totalIterations.toString())
            }
        }
    }
}

@Composable
private fun MetadataRow(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

/** Format milliseconds into a human-readable duration string. */
private fun formatDuration(ms: Long): String {
    val seconds = ms / 1000.0
    return if (seconds < 60) {
        "%.1fs".format(seconds)
    } else {
        val min = ms / 60000
        val sec = (ms % 60000) / 1000.0
        "%dm %.1fs".format(min, sec)
    }
}
