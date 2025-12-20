package com.kiranaflow.app.ui.screens.scanner

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size as ComposeSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

@Composable
fun ScannerScreen(
    isContinuous: Boolean,
    onBarcodeScanned: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier.fillMaxSize(),
    backgroundColor: Color = Color.Black,
    showCloseButton: Boolean = true,
    showViewfinder: Boolean = true,
    viewfinderWidth: Dp = 280.dp,
    viewfinderHeight: Dp = 200.dp
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCamPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            Log.d("ScannerScreen", "CAMERA permission result=$granted")
            hasCamPermission = granted
        }
    )
    var isCameraStarting by remember { mutableStateOf(true) }
    var lastScannedText by remember { mutableStateOf<String?>(null) }
    var lastError by remember { mutableStateOf<String?>(null) }

    // PreviewView must be created/owned by AndroidView to avoid "already has a parent" issues
    // and to ensure proper attach/detach across navigation.
    var previewView by remember { mutableStateOf<PreviewView?>(null) }

    // Dedicated analyzer executor (avoid creating a new executor per recomposition).
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }

    LaunchedEffect(key1 = true) {
        Log.d("ScannerScreen", "ScannerScreen entered continuous=$isContinuous hasPermission=$hasCamPermission")
        if (!hasCamPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    // Bind camera asynchronously (DO NOT block UI thread with cameraProviderFuture.get()).
    DisposableEffect(hasCamPermission, lifecycleOwner, previewView) {
        var cameraProvider: androidx.camera.lifecycle.ProcessCameraProvider? = null
        var imageAnalysis: ImageAnalysis? = null
        var scanner: com.google.mlkit.vision.barcode.BarcodeScanner? = null

        if (hasCamPermission && previewView != null) {
            isCameraStarting = true
            lastError = null

            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener(
                {
                    try {
                        cameraProvider = cameraProviderFuture.get()

                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView!!.surfaceProvider)
                        }
                        val selector =
                            CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()

                        val resolutionSelector = ResolutionSelector.Builder()
                            .setResolutionStrategy(
                                ResolutionStrategy(
                                    Size(1280, 720),
                                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                                )
                            )
                            .build()

                        imageAnalysis = ImageAnalysis.Builder()
                            .setResolutionSelector(resolutionSelector)
                            .setBackpressureStrategy(STRATEGY_KEEP_ONLY_LATEST)
                            .build()

                        val options = BarcodeScannerOptions.Builder()
                            .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                            .build()
                        scanner = BarcodeScanning.getClient(options)

                        var lastScannedTimeMs = 0L
                        imageAnalysis?.setAnalyzer(analyzerExecutor) { imageProxy ->
                            val now = System.currentTimeMillis()
                            if (isContinuous && now - lastScannedTimeMs < 1500) {
                                imageProxy.close()
                                return@setAnalyzer
                            }

                            val mediaImage = imageProxy.image
                            if (mediaImage == null) {
                                imageProxy.close()
                                return@setAnalyzer
                            }

                            val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                            scanner?.process(inputImage)
                                ?.addOnSuccessListener { barcodes ->
                                    if (barcodes.isNotEmpty()) {
                                        val barcodeValue = (barcodes[0].rawValue ?: "").trim()
                                        if (barcodeValue.isNotBlank()) {
                                            Log.d(
                                                "ScannerScreen",
                                                "Scanned barcode='$barcodeValue' continuous=$isContinuous"
                                            )
                                            lastScannedText = barcodeValue
                                            lastScannedTimeMs = now
                                            onBarcodeScanned(barcodeValue)
                                            if (!isContinuous) onClose()
                                        }
                                    }
                                }
                                ?.addOnFailureListener { e ->
                                    Log.e("ScannerScreen", "Scan failed", e)
                                    lastError = e.message ?: "Scan failed"
                                }
                                ?.addOnCompleteListener { imageProxy.close() }
                        }

                        cameraProvider?.unbindAll()
                        cameraProvider?.bindToLifecycle(lifecycleOwner, selector, preview, imageAnalysis)

                        isCameraStarting = false
                        Log.d("ScannerScreen", "Camera bound successfully")
                    } catch (e: Exception) {
                        Log.e("ScannerScreen", "Camera bind failed", e)
                        lastError = e.message ?: "Camera bind failed"
                        isCameraStarting = false
                    }
                },
                ContextCompat.getMainExecutor(context)
            )
        } else if (hasCamPermission && previewView == null) {
            Log.d("ScannerScreen", "Waiting for PreviewView before binding camera")
        }

        onDispose {
            try {
                imageAnalysis?.clearAnalyzer()
            } catch (_: Exception) {
            }
            try {
                scanner?.close()
            } catch (_: Exception) {
            }
            try {
                cameraProvider?.unbindAll()
            } catch (_: Exception) {
            }
        }
    }

    Box(modifier = modifier.background(backgroundColor)) {
        if (hasCamPermission) {
            AndroidView(
                factory = { ctx ->
                    Log.d("ScannerScreen", "AndroidView.factory called; creating PreviewView")
                    PreviewView(ctx).apply {
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        setBackgroundColor(android.graphics.Color.BLACK)
                        previewView = this
                    }
                },
                update = { pv ->
                    if (previewView !== pv) {
                        previewView = pv
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        ScannerOverlay(
            isStarting = isCameraStarting,
            onClose = onClose,
            hasPermission = hasCamPermission,
            onRequestPermission = { launcher.launch(Manifest.permission.CAMERA) },
            errorText = lastError,
            showCloseButton = showCloseButton,
            showViewfinder = showViewfinder,
            viewfinderWidth = viewfinderWidth,
            viewfinderHeight = viewfinderHeight
        )

        // lightweight feedback overlay (useful esp. for Billing continuous scan)
        if (!lastScannedText.isNullOrBlank()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Black.copy(alpha = 0.6f)
                ) {
                    Text(
                        text = "Scanned: $lastScannedText",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ScannerOverlay(
    isStarting: Boolean,
    onClose: () -> Unit,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    errorText: String?,
    showCloseButton: Boolean,
    showViewfinder: Boolean,
    viewfinderWidth: Dp,
    viewfinderHeight: Dp
) {
    val viewfinderSize = with(LocalDensity.current) { ComposeSize(viewfinderWidth.toPx(), viewfinderHeight.toPx()) }

    if (showViewfinder) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val overlayPath = Path().apply { addRect(Rect(Offset.Zero, size)) }

            val vfRect = Rect(
                topLeft = Offset(center.x - viewfinderSize.width / 2f, center.y - viewfinderSize.height / 2f),
                bottomRight = Offset(center.x + viewfinderSize.width / 2f, center.y + viewfinderSize.height / 2f)
            )
            val viewfinderRect = RoundRect(
                rect = vfRect,
                cornerRadius = CornerRadius(24.dp.toPx(), 24.dp.toPx())
            )
            val viewfinderPath = Path().apply { addRoundRect(viewfinderRect) }

            val finalPath = Path.combine(PathOperation.Difference, overlayPath, viewfinderPath)
            drawPath(finalPath, color = Color.Black.copy(alpha = 0.6f))
        }

        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .size(width = viewfinderWidth, height = viewfinderHeight)
                    .border(2.dp, Color.White, RoundedCornerShape(24.dp))
            )
        }
    }

    if (!hasPermission) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Camera permission is required", color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = onRequestPermission) {
                    Text("Grant Permission")
                }
            }
        }
    }

    if (hasPermission && isStarting) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Color.White)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Starting Camera...", color = Color.White)
            }
        }
    }

    if (!errorText.isNullOrBlank()) {
        Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.TopCenter) {
            Surface(shape = RoundedCornerShape(12.dp), color = Color.Red.copy(alpha = 0.75f)) {
                Text(
                    text = errorText,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
    
    if (showCloseButton) {
        IconButton(
            onClick = onClose,
            modifier = Modifier.statusBarsPadding().padding(16.dp),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = Color.Black.copy(alpha = 0.4f),
                contentColor = Color.White
            )
        ) {
            Icon(Icons.Default.Close, contentDescription = "Close Scanner")
        }
    }
    
    Box(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(16.dp), contentAlignment = Alignment.TopEnd) {
        Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.4f)) {
            Text("SCANNER ACTIVE", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
        }
    }
} 
