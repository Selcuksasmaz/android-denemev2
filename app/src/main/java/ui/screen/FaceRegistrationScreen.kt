package ui.screen

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
import com.example.zedeneme.engine.FeatureExtractionEngine
import com.example.zedeneme.viewmodel.FaceRegistrationViewModel
import com.example.zedeneme.viewmodel.FaceRegistrationViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaceRegistrationScreen(
    onNavigateBack: () -> Unit = {}
) {
    // Dependencies injection with TensorFlow support
    val repository = FaceRecognitionApplication.instance.repository
    val tensorFlowEngine = FaceRecognitionApplication.instance.tensorFlowEngine
    val faceDetectionEngine = remember { FaceDetectionEngine() }
    val featureExtractionEngine = remember { FeatureExtractionEngine(tensorFlowEngine) }

    val viewModel: FaceRegistrationViewModel = viewModel(
        factory = FaceRegistrationViewModelFactory(
            repository = repository,
            faceDetectionEngine = faceDetectionEngine,
            featureExtractionEngine = featureExtractionEngine,
            tensorFlowEngine = tensorFlowEngine
        )
    )

    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header with TensorFlow status
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Yüz Kayıt",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            // TensorFlow status indicator
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
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Feature type and progress info
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
                    Text(
                        text = "Feature Type: ${state.currentFeatureType}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${state.capturedFaces.size}/5",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                LinearProgressIndicator(
                    progress = state.registrationProgress,
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary
                )

                if (state.featureExtractionProgress.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = state.featureExtractionProgress,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Name input
        OutlinedTextField(
            value = state.currentPersonName,
            onValueChange = viewModel::setPersonName,
            label = { Text("Kişi Adı") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isLoading,
            leadingIcon = {
                Icon(Icons.Default.Person, contentDescription = "Person")
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Angle progress indicators
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Açı Durumu",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    val angles = listOf("frontal", "left", "right", "up", "down")

                    angles.forEach { angle ->
                        val isCompleted = state.completedAngles.contains(angle)
                        val count = state.capturedFaces.count { it.angle.getAngleType() == angle }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = when (angle) {
                                    "frontal" -> Icons.Default.Face
                                    "left" -> Icons.Default.TurnLeft
                                    "right" -> Icons.Default.TurnRight
                                    "up" -> Icons.Default.KeyboardArrowUp
                                    "down" -> Icons.Default.KeyboardArrowDown
                                    else -> Icons.Default.Face
                                },
                                contentDescription = angle,
                                tint = if (isCompleted) Color.Green else Color.Gray,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = angle.take(3),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "$count",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (count > 0) Color.Green else Color.Gray
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Camera placeholder and controls
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
                        Text("İşleniyor...")
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
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
                            text = "Yüzünüzü farklı açılardan kaydedin",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            OutlinedButton(
                onClick = viewModel::clearCapturedFaces,
                enabled = !state.isLoading && state.capturedFaces.isNotEmpty()
            ) {
                Icon(Icons.Default.Clear, contentDescription = "Clear")
                Spacer(modifier = Modifier.width(4.dp))
                Text("Temizle")
            }

            Button(
                onClick = viewModel::saveFaceProfile,
                enabled = !state.isLoading && state.isRegistrationComplete && state.currentPersonName.isNotBlank()
            ) {
                Icon(Icons.Default.Save, contentDescription = "Save")
                Spacer(modifier = Modifier.width(4.dp))
                Text("Kaydet")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Messages and captured faces list
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

            // Success message
            state.successMessage?.let { success ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color.Green.copy(alpha = 0.1f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "Success",
                                tint = Color.Green
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = success,
                                color = Color.Green
                            )
                        }
                    }
                }
            }

            // Captured faces list
            if (state.capturedFaces.isNotEmpty()) {
                item {
                    Text(
                        text = "Yakalanan Yüzler (${state.capturedFaces.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(state.capturedFaces) { face ->
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = when (face.angle.getAngleType()) {
                                    "frontal" -> Icons.Default.Face
                                    "left" -> Icons.Default.TurnLeft
                                    "right" -> Icons.Default.TurnRight
                                    "up" -> Icons.Default.KeyboardArrowUp
                                    "down" -> Icons.Default.KeyboardArrowDown
                                    else -> Icons.Default.Face
                                },
                                contentDescription = face.angle.getAngleType(),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "Açı: ${face.angle}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Güven: ${(face.confidence * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                face.extractedFeatures?.let { features ->
                                    Text(
                                        text = "Features: ${features.size}D",
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
    }

    // Clear messages when they're shown
    LaunchedEffect(state.errorMessage, state.successMessage) {
        if (state.errorMessage != null || state.successMessage != null) {
            kotlinx.coroutines.delay(5000) // 5 seconds
            viewModel.clearMessages()
        }
    }
}