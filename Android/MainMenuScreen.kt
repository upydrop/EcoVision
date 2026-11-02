package com.example.ecovision

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val BgColor = Color(0xFFE4F1DE)
private val DockColor = Color(0xFFF6FBF3)
private val GreenMain = Color(0xFF3FA66B)
private val GreenDark = Color(0xFF1F6B42)

@Composable
fun MainMenuScreen(
    onCamera: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(900),
        label = "alpha"
    )
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.8f,
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
            .drawBehind {
                // мягкие "пятна" вместо градиента
                drawCircle(
                    Color(0xFFD2E8CA),
                    radius = size.width * 0.65f,
                    center = Offset(size.width * 0.95f, size.height * 0.05f)
                )
                drawCircle(
                    Color(0xFFDDEFD6),
                    radius = size.width * 0.5f,
                    center = Offset(size.width * 0.05f, size.height * 0.55f)
                )
                drawCircle(
                    Color(0xFFCDE5C4),
                    radius = size.width * 0.4f,
                    center = Offset(size.width * 0.85f, size.height * 0.8f)
                )
            }
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-70).dp)
                .graphicsLayer {
                    this.alpha = alpha
                    scaleX = scale
                    scaleY = scale
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PuffyText(text = "Eco", fontSize = 76.sp)
            PuffyText(text = "Vision", fontSize = 76.sp)
            Spacer(Modifier.height(20.dp))
            Text(
                text = "SORT SMARTER",
                color = GreenDark.copy(alpha = 0.7f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp
            )
        }

        BottomDock(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 20.dp)
                .graphicsLayer { this.alpha = alpha },
            onAbout = onAbout,
            onCamera = onCamera,
            onSettings = onSettings
        )
    }
}

@Composable
private fun BottomDock(
    modifier: Modifier = Modifier,
    onAbout: () -> Unit,
    onCamera: () -> Unit,
    onSettings: () -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(12.dp, RoundedCornerShape(40.dp)),
        shape = RoundedCornerShape(40.dp),
        color = DockColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            DockIcon(Icons.Rounded.Info, onAbout)
            DockCameraButton(onCamera)
            DockIcon(Icons.Rounded.Settings, onSettings)
        }
    }
}

@Composable
private fun DockIcon(icon: ImageVector, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .background(Color(0xFFE4F1DE), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = GreenDark, modifier = Modifier.size(26.dp))
    }
}

@Composable
private fun DockCameraButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(78.dp)
            .shadow(8.dp, CircleShape)
            .background(GreenMain, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Rounded.PhotoCamera,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(38.dp)
        )
    }
}

/** Пышный закруглённый 3D-текст: слои "выдавливания" + контур + светлая грань */
@Composable
fun PuffyText(text: String, fontSize: TextUnit, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val strokePx = with(density) { fontSize.toPx() * 0.16f }
    val style = TextStyle(
        fontSize = fontSize,
        fontWeight = FontWeight.Black,
        letterSpacing = 1.sp
    )

    Box(modifier = modifier) {
        // 1. "глубина" – тёмные слои вниз
        for (i in 9 downTo 1) {
            PuffyLayer(text, style, GreenDark, strokePx, dy = i.dp)
        }
        // 2. толстое тело с закруглёнными краями
        PuffyLayer(text, style, GreenMain, strokePx, dy = 0.dp)
        // 3. светлая грань сверху
        PuffyLayer(text, style, Color(0xFF8EDDA6), strokePx * 0.15f, dy = (-3).dp)
    }
}

@Composable
private fun PuffyLayer(
    text: String,
    style: TextStyle,
    color: Color,
    strokePx: Float,
    dy: androidx.compose.ui.unit.Dp
) {
    Box(modifier = Modifier.offset(y = dy)) {
        Text(text = text, style = style, color = color)
        Text(
            text = text,
            style = style.copy(
                drawStyle = Stroke(width = strokePx, join = StrokeJoin.Round)
            ),
            color = color
        )
    }
}