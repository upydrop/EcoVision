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
import androidx.compose.material.icons.rounded.LocationOn
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

@Composable
fun MainMenuScreen(
    palette: EcoPalette,
    animations: Boolean,
    onCamera: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
    onMap: () -> Unit
) {
    var visible by remember { mutableStateOf(!animations) }
    LaunchedEffect(animations) { visible = true }
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(if (animations) 900 else 0), label = "menuAlpha")
    val scale by animateFloatAsState(if (visible) 1f else 0.8f, tween(if (animations) 900 else 0, easing = FastOutSlowInEasing), label = "menuScale")

    Box(Modifier.fillMaxSize().background(palette.background).drawBehind {
        drawCircle(palette.blobA, size.width * 0.65f, Offset(size.width * 0.95f, size.height * 0.05f))
        drawCircle(palette.blobA, size.width * 0.5f, Offset(size.width * 0.05f, size.height * 0.55f))
        drawCircle(palette.blobB, size.width * 0.4f, Offset(size.width * 0.85f, size.height * 0.8f))
    }) {
        Box(Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start = 22.dp, top = 14.dp)) {
            DockIcon(Icons.Rounded.Info, palette, onAbout, "О приложении")
        }
        Column(
            Modifier.align(Alignment.Center).offset(y = (-70).dp)
                .graphicsLayer { this.alpha = alpha; scaleX = scale; scaleY = scale },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PuffyText("Eco", 76.sp, palette)
            PuffyText("Vision", 76.sp, palette)
            Spacer(Modifier.height(20.dp))
            Text("SORT SMARTER", color = palette.text.copy(alpha = 0.75f), fontSize = 13.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
        }
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 20.dp)
                .fillMaxWidth().shadow(12.dp, RoundedCornerShape(40.dp))
                .graphicsLayer { this.alpha = alpha },
            shape = RoundedCornerShape(40.dp), color = palette.panel
        ) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                DockIcon(Icons.Rounded.LocationOn, palette, onMap, "Карта сортировки")
                Box(Modifier.size(78.dp).shadow(8.dp, CircleShape)
                    .background(palette.accent, CircleShape).clickable(onClick = onCamera),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.PhotoCamera, contentDescription = "Камера", tint = Color.White, modifier = Modifier.size(38.dp))
                }
                DockIcon(Icons.Rounded.Settings, palette, onSettings, "Настройки")
            }
        }
    }
}

@Composable
private fun DockIcon(icon: ImageVector, palette: EcoPalette, onClick: () -> Unit, description: String) {
    Box(Modifier.size(52.dp).background(palette.background, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = description, tint = palette.text, modifier = Modifier.size(26.dp))
    }
}

@Composable
fun PuffyText(text: String, fontSize: TextUnit, palette: EcoPalette, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val strokePx = with(density) { fontSize.toPx() * 0.16f }
    val style = TextStyle(fontSize = fontSize, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
    Box(modifier) {
        for (i in 9 downTo 1) PuffyLayer(text, style, palette.text.copy(alpha = 0.82f), strokePx, i.dp)
        PuffyLayer(text, style, palette.accent, strokePx, 0.dp)
        PuffyLayer(text, style, if (palette == DarkEco) Color(0xFFA4F0BB) else Color(0xFF8EDDA6), strokePx * 0.15f, (-3).dp)
    }
}

@Composable
private fun PuffyLayer(text: String, style: TextStyle, color: Color, strokePx: Float, dy: androidx.compose.ui.unit.Dp) {
    Box(Modifier.offset(y = dy)) {
        Text(text, style = style, color = color)
        Text(text, style = style.copy(drawStyle = Stroke(width = strokePx, join = StrokeJoin.Round)), color = color)
    }
}