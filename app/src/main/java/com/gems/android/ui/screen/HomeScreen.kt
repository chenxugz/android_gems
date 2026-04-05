package com.gems.android.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Slider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToComparison: (prompt: String, steps: Int, iterations: Int, useE4B: Boolean) -> Unit,
    onNavigateToDemo: (useE4B: Boolean) -> Unit = {},
    onNavigateToImageGenDemo: (steps: Int) -> Unit = {},
    onNavigateToModelDownload: () -> Unit = {},
) {
    var prompt by rememberSaveable { mutableStateOf("A book floating in the sky, creative and cool concept, make it look artistic and dreamy.") }
    var steps by rememberSaveable { mutableIntStateOf(1) }
    var iterations by rememberSaveable { mutableIntStateOf(2) }
    var useE4B by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("GEMS Android") },
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
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Enter a prompt to compare direct generation vs GEMS agent loop",
                style = MaterialTheme.typography.bodyLarge,
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                label = { Text("Image generation prompt") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 5,
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Step selector
            Text(
                text = "Image gen steps:",
                style = MaterialTheme.typography.labelMedium,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(1, 2, 4).forEach { n ->
                    FilterChip(
                        selected = steps == n,
                        onClick = { steps = n },
                        label = {
                            Text(when (n) {
                                1 -> "1 (fast)"
                                2 -> "2 (balanced)"
                                else -> "$n (quality)"
                            })
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // LLM model selector
            Text(
                text = "LLM model:",
                style = MaterialTheme.typography.labelMedium,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = !useE4B,
                    onClick = { useE4B = false },
                    label = { Text("E2B (fast)") },
                )
                FilterChip(
                    selected = useE4B,
                    onClick = { useE4B = true },
                    label = { Text("E4B (smart)") },
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // GEMS iteration slider
            Text(
                text = "GEMS agent iterations: $iterations",
                style = MaterialTheme.typography.labelMedium,
            )
            Slider(
                value = iterations.toFloat(),
                onValueChange = { iterations = it.toInt() },
                valueRange = 1f..5f,
                steps = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { onNavigateToComparison(prompt, steps, iterations, useE4B) },
                enabled = prompt.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Run Android GEMS")
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = { onNavigateToDemo(useE4B) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Gemma 4 Demo")
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = { onNavigateToImageGenDemo(steps) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Direct Image Gen Demo")
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onNavigateToModelDownload,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Download Models")
            }
        }
    }
}
