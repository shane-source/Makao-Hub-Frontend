package com.makaohub.android.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** CameraX capture stays in memory; it is evidence, not a liveness or face-match verdict. */
@Composable
fun EvidenceCamera(
    selfie: Boolean,
    enabled: Boolean,
    onPhoto: (Bitmap) -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier,
    captureRequest: Int = 0,
    showControls: Boolean = true,
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var permitted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var capture by remember { mutableStateOf<ImageCapture?>(null) }
    var capturing by remember { mutableStateOf(false) }
    val photo by rememberUpdatedState(onPhoto)
    val error by rememberUpdatedState(onError)
    val permittedAction by rememberUpdatedState(enabled)
    val alive = remember { AtomicBoolean(true) }
    val worker = remember { Executors.newSingleThreadExecutor() }
    val main = remember(context) { ContextCompat.getMainExecutor(context) }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permitted = it
        if (!it) error("Camera permission was denied. You can select a photo instead.")
    }
    val view = remember(context) {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
            isSaveEnabled = false; isSaveFromParentEnabled = false
        }
    }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME)
                permitted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(Unit) {
        onDispose { alive.set(false); worker.shutdownNow() }
    }
    DisposableEffect(owner, permitted, selfie) {
        var bound = true
        var provider: ProcessCameraProvider? = null
        val preview = Preview.Builder().build()
        @Suppress("DEPRECATION")
        val image = ImageCapture.Builder().setTargetResolution(Size(1600, 1200))
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).setJpegQuality(90).build()
        if (permitted) {
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                if (bound && alive.get()) {
                    try {
                        val available = future.get()
                        val selector = if (selfie) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                        if (!available.hasCamera(selector)) error("The requested camera is unavailable. Select a photo instead.")
                        else {
                            provider = available
                            preview.setSurfaceProvider(view.surfaceProvider)
                            available.bindToLifecycle(owner, selector, preview, image)
                            capture = image
                        }
                    } catch (_: Exception) { error("Camera could not start. Select a photo or try again.") }
                }
            }, main)
        }
        onDispose { bound = false; provider?.unbind(preview, image); capture = null }
    }
    fun capturePhoto() {
        if (!enabled || capturing) return
        if (!permitted) {
            cameraPermission.launch(Manifest.permission.CAMERA)
        } else {
            val active = capture ?: return
            capturing = true
            active.takePicture(worker, object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    var bitmap: Bitmap? = null
                    try {
                        if (!alive.get()) return
                        if (image.width.toLong() * image.height > 8_000_000) throw IllegalArgumentException()
                        val decoded = image.toBitmap()
                        val rotation = image.imageInfo.rotationDegrees
                        bitmap = if (rotation == 0) decoded else {
                            try { Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height,
                                Matrix().apply { postRotate(rotation.toFloat()) }, true) }
                            finally { decoded.recycle() }
                        }
                        val ready = requireNotNull(bitmap)
                        bitmap = null
                        main.execute {
                            if (alive.get()) {
                                capturing = false
                                if (permittedAction && permitted) photo(ready) else ready.recycle()
                            } else ready.recycle()
                        }
                    } catch (_: Exception) {
                        bitmap?.recycle()
                        main.execute { if (alive.get()) { capturing = false; error("Photo could not be captured. Try again or select an image.") } }
                    } finally { image.close() }
                }
                override fun onError(exception: ImageCaptureException) {
                    main.execute { if (alive.get()) { capturing = false; error("Camera capture failed. No document was submitted.") } }
                }
            })
        }
    }
    // The verification screen owns its bottom action. CameraX still owns capture and cleanup.
    var lastCaptureRequest by remember { mutableIntStateOf(captureRequest) }
    LaunchedEffect(captureRequest) {
        if (captureRequest != lastCaptureRequest) {
            lastCaptureRequest = captureRequest
            capturePhoto()
        }
    }
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        AndroidView(factory = { view }, modifier = if (selfie) Modifier.size(260.dp).clip(CircleShape).border(3.dp, Color(0xFF6A2E00), CircleShape)
            else Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(20.dp)))
    }
    if (showControls) {
        Gap(12)
        if (!permitted) {
            OutlinedButton({ cameraPermission.launch(Manifest.permission.CAMERA) }, enabled = enabled,
                modifier = Modifier.fillMaxWidth()) { Text("Allow camera") }
        } else {
            PrimaryButton(if (capturing) "Capturing…" else if (selfie) "Capture Live Photo" else "Capture ID Photo",
                enabled && capture != null && !capturing, ::capturePhoto)
        }
    }
}
