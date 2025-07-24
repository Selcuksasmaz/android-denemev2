package com.example.zedeneme.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.zedeneme.engine.EngineStatus

/**
 * Debug panel for monitoring TensorFlow Lite integration status
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TensorFlowDebugPanel(
    engineStatus: EngineStatus?,
    onToggleTensorFlowLite: (Boolean) -> Unit,
    onRunBenchmark: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Analytics,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "TensorFlow Lite Status",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            HorizontalDivider()
            
            // Engine Status
            if (engineStatus != null) {
                EngineStatusSection(engineStatus)
            } else {
                Text(
                    text = "Engine status not available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            
            HorizontalDivider()
            
            // Controls
            ControlsSection(
                engineStatus = engineStatus,
                onToggleTensorFlowLite = onToggleTensorFlowLite,
                onRunBenchmark = onRunBenchmark
            )
        }
    }
}

@Composable
private fun EngineStatusSection(engineStatus: EngineStatus) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatusRow(
            label = "Active Engine",
            value = engineStatus.activEngine,
            isGood = engineStatus.isTensorFlowLiteAvailable
        )
        
        StatusRow(
            label = "TensorFlow Lite",
            value = if (engineStatus.isTensorFlowLiteAvailable) "Available" else "Not Available",
            isGood = engineStatus.isTensorFlowLiteAvailable
        )
        
        StatusRow(
            label = "Feature Size",
            value = "${engineStatus.featureSize}D",
            isGood = engineStatus.featureSize == 512
        )
        
        StatusRow(
            label = "Recognition Threshold",
            value = String.format("%.2f", engineStatus.recognitionThreshold),
            isGood = true
        )
        
        StatusRow(
            label = "Expected Accuracy",
            value = engineStatus.expectedAccuracy,
            isGood = engineStatus.expectedAccuracy.contains("90")
        )
    }
}

@Composable
private fun StatusRow(
    label: String,
    value: String,
    isGood: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (isGood) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            }
        )
    }
}

@Composable
private fun ControlsSection(
    engineStatus: EngineStatus?,
    onToggleTensorFlowLite: (Boolean) -> Unit,
    onRunBenchmark: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // TensorFlow Lite Toggle
        var isTensorFlowEnabled by remember { 
            mutableStateOf(engineStatus?.isTensorFlowLiteAvailable == true) 
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Use TensorFlow Lite",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            
            Switch(
                checked = isTensorFlowEnabled,
                onCheckedChange = { enabled ->
                    isTensorFlowEnabled = enabled
                    onToggleTensorFlowLite(enabled)
                },
                enabled = engineStatus?.isTensorFlowLiteAvailable == true
            )
        }
        
        // Benchmark Button
        Button(
            onClick = onRunBenchmark,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary
            )
        ) {
            Icon(
                imageVector = Icons.Default.Speed,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Run Performance Benchmark")
        }
    }
}

/**
 * Compact status indicator for integration in existing screens
 */
@Composable
fun TensorFlowStatusIndicator(
    engineStatus: EngineStatus?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (engineStatus?.isTensorFlowLiteAvailable == true) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Analytics,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (engineStatus?.isTensorFlowLiteAvailable == true) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onErrorContainer
                }
            )
            
            Text(
                text = engineStatus?.activEngine?.split(" ")?.first() ?: "Unknown",
                style = MaterialTheme.typography.labelSmall,
                color = if (engineStatus?.isTensorFlowLiteAvailable == true) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onErrorContainer
                }
            )
            
            if (engineStatus?.expectedAccuracy?.contains("90") == true) {
                Text(
                    text = "⚡",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}