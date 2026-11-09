package com.example.ecovision

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private fun sortingTip(label: String): String {
    val value = label.lowercase(Locale.ROOT)
    return when {
        "plastic" in value || "пласт" in value -> "Обычно пластиковую упаковку нужно опустошить. Проверь правила сортировки в своём городе."
        "paper" in value || "бумаг" in value || "cardboard" in value || "картон" in value -> "Чистую и сухую бумагу обычно собирают отдельно. Уточни местные правила."
        "glass" in value || "стекл" in value -> "Стеклянную тару часто принимают отдельно. Уточни, какие виды стекла разрешены."
        "metal" in value || "металл" in value || "alumin" in value -> "Металлическую упаковку обычно нужно опустошить. Уточни местные правила."
        "organic" in value || "food" in value || "органик" in value -> "Пищевые отходы сортируются по местным правилам сбора биоотходов."
        else -> "Способ утилизации зависит от материала и местных правил сортировки."
    }
}

private enum class Screen { Menu, Camera, Settings, About, Developer, Map }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = EcoPreferences(this)
        setContent {
            var screen by remember { mutableStateOf(Screen.Menu) }
            var themeMode by remember { mutableStateOf(prefs.theme) }
            var settingsVersion by remember { mutableIntStateOf(0) }
            val dark = isDarkEco(themeMode)
            val palette = if (dark) DarkEco else LightEco
            EcoVisionTheme(dark) {
                val back = { screen = if (screen == Screen.Developer) Screen.Settings else Screen.Menu }
                BackHandler(screen != Screen.Menu) { back() }
                // Refresh user-configurable flags when returning from settings.
                @Suppress("UNUSED_VARIABLE") val v = settingsVersion
                when (screen) {
                    Screen.Menu -> MainMenuScreen(palette, prefs.animations,
                        onCamera = { screen = Screen.Camera },
                        onSettings = { screen = Screen.Settings },
                        onAbout = { screen = Screen.About },
                        onMap = { screen = Screen.Map })
                    Screen.Map -> RecyclingMapScreen(palette, onBack = { screen = Screen.Menu })
                    Screen.Camera -> WasteCameraScreen(palette, prefs, onBack = { screen = Screen.Menu }, onSettings = { screen = Screen.Settings })
                    Screen.Settings -> SettingsScreen(palette, prefs, themeMode,
                        onThemeChange = { themeMode = it; prefs.theme = it },
                        onBack = { settingsVersion++; screen = Screen.Menu },
                        onDeveloper = { screen = Screen.Developer })
                    Screen.About -> AboutScreen(palette, prefs, onBack = { screen = Screen.Menu },
                        onUnlock = { settingsVersion++ })
                    Screen.Developer -> DeveloperScreen(palette, prefs, onBack = { screen = Screen.Settings })
                }
            }
        }
    }
}

@Composable
fun WasteCameraScreen(p: EcoPalette, prefs: EcoPreferences, onBack: () -> Unit, onSettings: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPermission = it }
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val classifier = remember { WasteClassifier(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val cameraActive = remember { AtomicBoolean(true) }
    // 0 = live, 1 = capture next frame, 2 = frozen frame
    val captureState = remember { AtomicInteger(0) }
    var frozenBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var freezePending by remember { mutableStateOf(false) }
    var prediction by remember { mutableStateOf<Prediction?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    // Require a few consecutive equivalent predictions to reduce flicker.
    var candidate by remember { mutableStateOf<String?>(null) }
    var candidateCount by remember { mutableIntStateOf(0) }
    var accepted by remember { mutableStateOf<Prediction?>(null) }
    val low = if (prefs.customThresholds) prefs.lowThreshold else 0.5f
    val high = if (prefs.customThresholds) prefs.highThreshold else 0.8f

    DisposableEffect(Unit) {
        onDispose {
            cameraActive.set(false)
            captureState.set(2)
            // Stop accepting frames before disposing the interpreter.
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                runCatching { future.get().unbindAll() }
            }, ContextCompat.getMainExecutor(context))
            // The close operation must run *after* any queued inference on the SAME executor.
            executor.execute { runCatching { classifier.close() } }
            executor.shutdown()
        }
    }
    LaunchedEffect(hasPermission) {
        if (hasPermission && cameraActive.get()) startCamera(previewView, lifecycleOwner, executor, classifier,
            cameraActive, captureState,
            onPrediction = { if (cameraActive.get() && captureState.get() == 0) { prediction = it; errorMessage = null } },
            onCaptured = { frame, result ->
                if (cameraActive.get() && captureState.get() == 2) {
                    frozenBitmap = frame
                    freezePending = false
                    prediction = result
                    accepted = result
                    candidate = result.label
                    candidateCount = 3
                    errorMessage = null
                }
            },
            onError = { if (cameraActive.get()) { errorMessage = it; freezePending = false } })
    }
    LaunchedEffect(prediction, prefs.stabilize) {
        if (captureState.get() != 0) return@LaunchedEffect
        val value = prediction ?: return@LaunchedEffect
        if (!prefs.stabilize) {
            accepted = value
        } else {
            val status = if (value.confidence < low) "thinking" else value.label
            if (candidate == status) candidateCount++ else { candidate = status; candidateCount = 1 }
            if (candidateCount >= 3) accepted = value
            else if (accepted?.label == value.label && value.confidence >= low) accepted = value
        }
    }

    val displayed = if (prefs.stabilize) accepted else prediction
    val confidence = displayed?.confidence ?: 0f
    val thinking = displayed == null || confidence < low
    val statusColor = when {
        thinking -> p.muted
        confidence >= high -> if (p == DarkEco) Color(0xFF64CE88) else Color(0xFF23864B)
        else -> if (p == DarkEco) Color(0xFFF1CC65) else Color(0xFFBB870D)
    }
    val animatedColor by animateColorAsState(statusColor, tween(if (prefs.animations) 300 else 0), label = "resultColor")
    val progress by animateFloatAsState(confidence, tween(if (prefs.animations) 300 else 0), label = "confidence")

    Column(Modifier.fillMaxSize().background(p.background).statusBarsPadding().navigationBarsPadding()
        .padding(horizontal = 16.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = onBack, modifier = Modifier.background(p.panel, RoundedCornerShape(50))) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад", tint = p.text)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("EcoVision", color = p.text, fontSize = 25.sp, fontWeight = FontWeight.Black)
                Text("SMART SCANNER", color = p.accent, fontSize = 10.sp, letterSpacing = 2.sp)
            }
            IconButton(onClick = onSettings, modifier = Modifier.background(p.panel, RoundedCornerShape(50))) {
                Icon(Icons.Rounded.Settings, "Настройки", tint = p.text)
            }
        }
        Spacer(Modifier.height(16.dp))
        if (!hasPermission) {
            Spacer(Modifier.weight(1f))
            Text("Для распознавания нужен доступ к камере", color = p.text)
            Spacer(Modifier.height(12.dp))
            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) { Text("Разрешить камеру") }
            Spacer(Modifier.weight(1f))
        } else {
            Box(modifier = Modifier.fillMaxWidth().weight(1f)
                .clip(RoundedCornerShape(26.dp))) {
                AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
                frozenBitmap?.let { frame ->
                    Image(
                        bitmap = frame.asImageBitmap(),
                        contentDescription = "Зафиксированный кадр",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                enabled = !freezePending,
                onClick = {
                    if (captureState.get() == 2) {
                        captureState.set(0)
                        frozenBitmap = null
                        prediction = null
                        accepted = null
                        candidate = null
                        candidateCount = 0
                        errorMessage = null
                    } else if (captureState.compareAndSet(0, 1)) {
                        freezePending = true
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = p.accent),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    if (frozenBitmap != null) Icons.Rounded.Replay else Icons.Rounded.CameraAlt,
                    contentDescription = null
                )
                Spacer(Modifier.width(8.dp))
                Text(when {
                    frozenBitmap != null -> "Продолжить сканирование"
                    freezePending -> "Фиксируем кадр…"
                    else -> "Зафиксировать кадр"
                })
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CenterFocusStrong, null, tint = p.text, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (frozenBitmap != null) "Анализ зафиксированного кадра" else "Распознавание в реальном времени", color = p.text, fontSize = 12.sp)
            }
            Spacer(Modifier.height(12.dp))
            Column(Modifier.fillMaxWidth().background(p.panel, RoundedCornerShape(24.dp)).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("ОБНАРУЖЕНО", color = p.muted, fontSize = 11.sp, letterSpacing = 2.sp)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (thinking) "Модель ещё думает…" else displayed!!.label,
                        color = animatedColor, fontWeight = FontWeight.Bold,
                        fontSize = if (thinking) 20.sp else 26.sp, modifier = Modifier.weight(1f))
                    if (!thinking) Icon(Icons.Rounded.CheckCircle, null, tint = animatedColor)
                }
                Text(when {
                    displayed == null -> "Наведи камеру на предмет"
                    thinking -> "Попробуй другой ракурс или улучши освещение"
                    confidence >= high -> "Высокая уверенность"
                    else -> "Предварительный результат"
                }, color = p.muted, fontSize = 12.sp)
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(8.dp)
                    .clip(RoundedCornerShape(50)), color = animatedColor, trackColor = p.soft)
                if (!thinking && displayed != null)
                    Text(sortingTip(displayed.label), color = p.muted, fontSize = 12.sp)
                if (prefs.developerUnlocked && prefs.showPercent && displayed != null)
                    Text("Уверенность модели: ${String.format(Locale.US, "%.1f", confidence * 100)}%", color = p.muted, fontSize = 13.sp)
                if (prefs.developerUnlocked && prefs.showRaw && displayed != null)
                    Text("Raw score: ${String.format(Locale.US, "%.6f", confidence)}", color = p.muted, fontSize = 12.sp)
                if (errorMessage != null) Text(errorMessage!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
            Spacer(Modifier.height(10.dp))
            Text("Анализ на устройстве · Снимок только в памяти", color = p.muted, fontSize = 11.sp)
        }
    }
}

private fun startCamera(
    previewView: PreviewView,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    executor: ExecutorService,
    classifier: WasteClassifier,
    cameraActive: AtomicBoolean,
    captureState: AtomicInteger,
    onCaptured: (Bitmap, Prediction) -> Unit,
    onPrediction: (Prediction) -> Unit,
    onError: (String) -> Unit
) {
    val context = previewView.context
    val providerFuture = ProcessCameraProvider.getInstance(context)

    providerFuture.addListener({
        try {
            val provider = providerFuture.get()
            if (!cameraActive.get()) return@addListener

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
                if (!cameraActive.get() || captureState.get() == 2) {
                    imageProxy.close()
                    return@setAnalyzer
                }
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

                    val isCapturing = captureState.get() == 1
                    // Own a separate bitmap: the working frame is recycled in finally.
                    val frozenFrame = if (isCapturing) rotatedBitmap.copy(Bitmap.Config.ARGB_8888, false) else null
                    val result = try {
                        classifier.classify(rotatedBitmap)
                    } catch (e: Exception) {
                        frozenFrame?.recycle()
                        throw e
                    }

                    if (isCapturing && captureState.compareAndSet(1, 2)) {
                        previewView.post {
                            if (cameraActive.get() && captureState.get() == 2) onCaptured(frozenFrame!!, result)
                            else frozenFrame?.recycle()
                        }
                    } else {
                        frozenFrame?.recycle()
                        previewView.post {
                            if (cameraActive.get() && captureState.get() == 0) onPrediction(result)
                        }
                    }
                } catch (e: Exception) {
                    captureState.compareAndSet(1, 0)
                    previewView.post {
                        if (cameraActive.get()) onError(
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

            if (!cameraActive.get()) return@addListener
            provider.unbindAll()

            if (!cameraActive.get()) return@addListener
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis
            )
        } catch (e: Exception) {
            if (cameraActive.get()) onError(
                e.message ?: "Не удалось запустить камеру"
            )
        }
    }, ContextCompat.getMainExecutor(context))
}
