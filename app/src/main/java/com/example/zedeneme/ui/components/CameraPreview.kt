package com.example.zedeneme.ui.components

import android.graphics.Bitmap
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleOwner
import androidx.compose.ui.res.stringResource
import com.example.zedeneme.R
import com.example.zedeneme.camera.CameraManager
import kotlinx.coroutines.launch

@Composable
fun CameraPreview(
    cameraManager: CameraManager,
    isActive: Boolean,
    onFrame: (Bitmap) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var cameraSetupComplete by remember { mutableStateOf(false) }
    
    // Setup camera when isActive becomes true
    LaunchedEffect(isActive, previewView) {
        if (isActive && previewView != null && !cameraSetupComplete) {
            coroutineScope.launch {
                try {
                    cameraManager.setupCamera(
                        lifecycleOwner = lifecycleOwner,
                        previewView = previewView!!,
                        onImageAnalyzed = onFrame
                    )
                    cameraSetupComplete = true
                } catch (e: Exception) {
                    // Handle camera setup error - logged in CameraManager
                }
            }
        } else if (!isActive && cameraSetupComplete) {
            cameraManager.stopCamera()
            cameraSetupComplete = false
        }
    }
    
    // Cleanup on dispose
    DisposableEffect(cameraManager) {
        onDispose {
            cameraManager.stopCamera()
        }
    }
    
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (isActive) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        previewView = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            
            if (!cameraSetupComplete) {
                Card(
                    modifier = Modifier
                        .padding(16.dp)
                        .wrapContentSize(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.camera_starting),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}