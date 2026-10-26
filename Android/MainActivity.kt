package com.example.ecovision

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                WasteCameraScreen()
            }
        }
    }
}

@Composable
fun WasteCameraScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            hasPermission = granted
        }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    val classifier = remember {
        WasteClassifier(context)
    }

    val cameraExecutor = remember {
        Executors.newSingleThreadExecutor()
    }

    var prediction by remember {
        mutableStateOf<Prediction?>(null)
    }

    var errorMessage by remember {
        mutableStateOf<String?>(null)
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
            classifier.close()
        }
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            startCamera(
                previewView = previewView,
                lifecycleOwner = lifecycleOwner,
                executor = cameraExecutor,
                classifier = classifier,
                onPrediction = { prediction = it },
                onError = { errorMessage = it }
            )
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF101712)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "EcoVision",
                color = Color.White,
                fontSize = 28.sp
            )

            Text(
                text = "LIVE WASTE CLASSIFIER",
                color = Color(0xFF86EFAC),
                fontSize = 12.sp
            )

            Spacer(Modifier.height(16.dp))

            if (!hasPermission) {
                Spacer(Modifier.weight(1f))

                Text(
                    "Для классификации мусора нужен доступ к камере.",
                    color = Color.White
                )

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = {
                        permissionLauncher.launch(
                            Manifest.permission.CAMERA
                        )
                    }
                ) {
                    Text("Разрешить камеру")
                }

                Spacer(Modifier.weight(1f))
            } else {
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )

                Spacer(Modifier.height(16.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        if (prediction != null) {
                            Text(
                                text = prediction!!.label.uppercase(),
                                color = Color(0xFF166534),
                                fontSize = 28.sp
                            )

                            Text(
                                text = "Оценка модели: ${
                                    "%.1f".format(
                                        java.util.Locale.US,
                                        prediction!!.confidence * 100
                                    )
                                }%",
                                color = Color.DarkGray,
                                fontSize = 16.sp
                            )

                            LinearProgressIndicator(
                                progress = {
                                    prediction!!.confidence
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp),
                                color = Color(0xFF16A34A)
                            )
                        } else {
                            Text(
                                "Наведи камеру на предмет…",
                                color = Color.DarkGray,
                                fontSize = 18.sp
                            )
                        }

                        errorMessage?.let {
                            Text(
                                text = it,
                                color = Color.Red,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    "Анализ выполняется на устройстве. Фото не сохраняются.",
                    color = Color.LightGray,
                    fontSize = 11.sp
                )
            }
        }
    }
}

private fun startCamera(
    previewView: PreviewView,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    executor: ExecutorService,
    classifier: WasteClassifier,
    onPrediction: (Prediction) -> Unit,
    onError: (String) -> Unit
) {
    val context = previewView.context
    val providerFuture = ProcessCameraProvider.getInstance(context)

    providerFuture.addListener({
        try {
            val provider = providerFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(
                    ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                )
                .build()

            analysis.setAnalyzer(executor) { imageProxy ->
                var bitmap: Bitmap? = null
                var rotatedBitmap: Bitmap? = null

                try {
                    bitmap = imageProxy.toBitmap()

                    val rotation =
                        imageProxy.imageInfo.rotationDegrees

                    rotatedBitmap = if (rotation != 0) {
                        val matrix = Matrix().apply {
                            postRotate(rotation.toFloat())
                        }

                        Bitmap.createBitmap(
                            bitmap,
                            0,
                            0,
                            bitmap.width,
                            bitmap.height,
                            matrix,
                            true
                        )
                    } else {
                        bitmap
                    }

                    val result = classifier.classify(rotatedBitmap)

                    previewView.post {
                        onPrediction(result)
                    }
                } catch (e: Exception) {
                    previewView.post {
                        onError(
                            e.message ?: "Не удалось обработать кадр"
                        )
                    }
                } finally {
                    if (rotatedBitmap != null &&
                        rotatedBitmap !== bitmap
                    ) {
                        rotatedBitmap.recycle()
                    }

                    bitmap?.recycle()
                    imageProxy.close()
                }
            }

            provider.unbindAll()

            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis
            )
        } catch (e: Exception) {
            onError(
                e.message ?: "Не удалось запустить камеру"
            )
        }
    }, ContextCompat.getMainExecutor(context))
}
