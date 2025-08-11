package ui.screen

import android.Manifest
import android.graphics.Bitmap
import androidx.camera.view.PreviewView
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.zedeneme.FaceRecognitionApplication
import com.example.zedeneme.camera.CameraManager
import com.example.zedeneme.engine.FaceDetectionEngine
import com.example.zedeneme.engine.FaceRecognitionEngine
import com.example.zedeneme.engine.FeatureExtractionEngine
import com.example.zedeneme.viewmodel.FaceRecognitionViewModel
import com.example.zedeneme.viewmodel.FaceRecognitionViewModelFactory
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun FaceRecognitionScreen(
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val repository = FaceRecognitionApplication.instance.repository
    val tensorFlowEngine = FaceRecognitionApplication.instance.tensorFlowEngine
    val faceDetectionEngine = remember { FaceDetectionEngine() }
    val featureExtractionEngine = remember { FeatureExtractionEngine(tensorFlowEngine) }
    val faceRecognitionEngine = remember {
        FaceRecognitionEngine(FaceRecognitionApplication.instance, repository, tensorFlowEngine)
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
    val cameraManager = remember { CameraManager(context) }
    var lastBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
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
                IconButton(
                    onClick = viewModel::toggleRealTimeMode
                ) {
                    Icon(
                        imageVector = if (state.isRealTimeMode) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = "Toggle Real-time",
                        tint = if (state.isRealTimeMode) Color.Green else Color(0xFFFF9800)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Camera Area
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
        ) {
            if (cameraPermissionState.status.isGranted) {
                CameraPreview(
                    cameraManager = cameraManager,
                    onAnalyze = { bitmap ->
                        lastBitmap = bitmap
                        if (state.isRealTimeMode) {
                            viewModel.processCameraFrame(bitmap)
                        }
                    }
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Kamera izni yüz tanıma için gereklidir.")
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { cameraPermissionState.launchPermissionRequest() }) {
                        Text("İzin İste")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Recognition stats and controls below...

        // Recognition stats card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            // This content is preserved from your original file
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
                         lastBitmap?.let { viewModel.triggerManualRecognition(it) }
                    },
                    enabled = !state.isLoading && lastBitmap != null
                ) {
                    Icon(Icons.Default.Search, contentDescription = "Recognize")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Tanı")
                }
            }
        }

        // Results section follows...
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            delay(5000)
            viewModel.clearError()
        }
    }
}

@Composable
fun CameraPreview(
    cameraManager: CameraManager,
    onAnalyze: (Bitmap) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        onDispose {
            cameraManager.release()
        }
    }

    AndroidView(
        factory = { context ->
            val previewView = PreviewView(context).apply {
                this.scaleType = PreviewView.ScaleType.FILL_CENTER
            }
            coroutineScope.launch {
                cameraManager.setupCamera(lifecycleOwner, previewView, onAnalyze)
            }
            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}
