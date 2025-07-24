package com.example.zedeneme.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.zedeneme.FaceRecognitionApplication
import com.example.zedeneme.engine.FaceDetectionEngine
import com.example.zedeneme.engine.FaceRecognitionEngine
import com.example.zedeneme.engine.FeatureExtractionEngine
import com.example.zedeneme.viewmodel.FaceRecognitionViewModel
import com.example.zedeneme.viewmodel.FaceRecognitionViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaceRecognitionScreen(
    onNavigateBack: () -> Unit = {}
) {
    // Dependencies injection with TensorFlow support
    val repository = FaceRecognitionApplication.instance.repository
    val tensorFlowEngine = FaceRecognitionApplication.instance.tensorFlowEngine
    val faceDetectionEngine = remember { FaceDetectionEngine() }
    val featureExtractionEngine = remember { FeatureExtractionEngine(tensorFlowEngine) }
    val faceRecognitionEngine = remember {
        FaceRecognitionEngine(repository, tensorFlowEngine)
    }

    val viewModel: FaceRecognitionViewModel = viewModel(
        factory = FaceRecognitionViewModelFactory(
            repository = repository,
            faceDetectionEngine = faceDetectionEngine,
            faceRecognitionEngine = faceRecognitionEngine,
            tensorFlowEngine = tensorFlowEngine
        )
    )

    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header with TensorFlow status and controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Yüz Tanıma",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // TensorFlow status
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (state.tensorFlowEnabled) Icons.Default.Psychology else Icons.Default.Error,
                        contentDescription = "TensorFlow Status",
                        tint = if (state.tensorFlowEnabled) Color.Green else Color.Red,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (state.tensorFlowEnabled) "TF Lite" else "Legacy",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.tensorFlowEnabled) Color.Green else Color.Red
                    )
                }

                // Real-time toggle
                IconButton(
                    onClick = viewModel::toggleRealTimeMode
                ) {
                    Icon(
                        imageVector = if (state.isRealTimeMode) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = "Toggle Real-time",
                        tint = if (state.isRealTimeMode) Color.Green else Color.Orange
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Recognition stats card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Toplam Tanıma",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "${state.totalRecognitions}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Column {
                        Text(
                            text = "Başarılı",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "${state.successfulRecognitions}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.Green
                        )
                    }

                    Column {
                        Text(
                            text = "Başarı Oranı",
                            style = MaterialTheme.typography.bodySmall
                        )
                        val successRate = if (state.totalRecognitions > 0) {
                            (state.successfulRecognitions.toFloat() / state.totalRecognitions * 100)
                        } else 0f
                        Text(
                            text = "${successRate.toInt()}%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                successRate >= 80 -> Color.Green
                                successRate >= 60 -> Color(0xFFFF9800) // Orange
                                else -> Color.Red
                            }
                        )
                    }
                }

                if (state.processingStatus.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = state.processingStatus,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Confidence threshold slider
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Güven Eşiği",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${(state.confidenceThreshold * 100).toInt()}%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Slider(
                    value = state.confidenceThreshold,
                    onValueChange = viewModel::setConfidenceThreshold,
                    valueRange = 0.5f..0.95f,
                    steps = 8
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "50%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "95%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Camera area
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (state.isLoading) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Tanıma işlemi...")
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Camera",
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Kamera görünümü burada olacak",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = if (state.isRealTimeMode) "Gerçek zamanlı tanıma aktif" else "Manuel tanıma modu",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Control buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            OutlinedButton(
                onClick = viewModel::clearResults,
                enabled = !state.isLoading
            ) {
                Icon(Icons.Default.Clear, contentDescription = "Clear")
                Spacer(modifier = Modifier.width(4.dp))
                Text("Temizle")
            }

            if (!state.isRealTimeMode) {
                Button(
                    onClick = {
                        // Manual recognition trigger - needs bitmap from camera
                        // This would be connected to camera capture
                    },
                    enabled = !state.isLoading
                ) {
                    Icon(Icons.Default.Search, contentDescription = "Recognize")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Tanı")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Results section
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Error message
            state.errorMessage?.let { error ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = "Error",
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = error,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            // Current detections
            if (state.detectedFaces.isNotEmpty()) {
                item {
                    Text(
                        text = "Algılanan Yüzler (${state.detectedFaces.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(state.detectedFaces) { face ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Face,
                                contentDescription = "Detected Face",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Yüz algılandı",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "Pozisyon: ${face.landmarks.boundingBox.centerX().toInt()}, ${face.landmarks.boundingBox.centerY().toInt()}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Recognition results
            if (state.recognitionResults.isNotEmpty()) {
                item {
                    Text(
                        text = "Tanıma Sonuçları (${state.recognitionResults.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(state.recognitionResults) { result ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.Green.copy(alpha = 0.1f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Recognized",
                                tint = Color.Green,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = result.personName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Green
                                )
                                Text(
                                    text = "Güven: ${(result.confidence * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "Açı: ${result.matchedAngle}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                val timeAgo = (System.currentTimeMillis() - result.timestamp) / 1000
                                Text(
                                    text = "${timeAgo}s önce",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Empty state message
            if (state.recognitionResults.isEmpty() && state.detectedFaces.isEmpty() && !state.isLoading) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "No Results",
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Henüz yüz algılanmadı",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Kameraya bakın ve tanıma işleminin başlamasını bekleyin",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Auto-clear error messages
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            kotlinx.coroutines.delay(5000)
            viewModel.clearError()
        }
    }
}