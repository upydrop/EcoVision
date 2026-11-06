package com.example.ecovision

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class EcoPalette(
    val background: Color,
    val panel: Color,
    val soft: Color,
    val accent: Color,
    val text: Color,
    val muted: Color,
    val blobA: Color,
    val blobB: Color
)

val LightEco = EcoPalette(
    background = Color(0xFFE4F1DE), panel = Color(0xFFF6FBF3),
    soft = Color(0xFFD2E8CA), accent = Color(0xFF3FA66B),
    text = Color(0xFF1F6B42), muted = Color(0xFF64816C),
    blobA = Color(0xFFD2E8CA), blobB = Color(0xFFCDE5C4)
)
val DarkEco = EcoPalette(
    background = Color(0xFF101A15), panel = Color(0xFF1D3025),
    soft = Color(0xFF294332), accent = Color(0xFF64CE88),
    text = Color(0xFFD5F2DE), muted = Color(0xFFA5BDAA),
    blobA = Color(0xFF1B3023), blobB = Color(0xFF213A2A)
)

/** Theme: system, light, dark. Settings persist across launches. */
class EcoPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("ecovision_preferences", Context.MODE_PRIVATE)
    var theme: String
        get() = prefs.getString("theme", "system") ?: "system"
        set(value) = prefs.edit().putString("theme", value).apply()
    var developerUnlocked: Boolean
        get() = prefs.getBoolean("developer_unlocked", false)
        set(value) = prefs.edit().putBoolean("developer_unlocked", value).apply()
    var showPercent: Boolean
        get() = prefs.getBoolean("show_percent", false)
        set(value) = prefs.edit().putBoolean("show_percent", value).apply()
    var showRaw: Boolean
        get() = prefs.getBoolean("show_raw", false)
        set(value) = prefs.edit().putBoolean("show_raw", value).apply()
    var customThresholds: Boolean
        get() = prefs.getBoolean("custom_thresholds", false)
        set(value) = prefs.edit().putBoolean("custom_thresholds", value).apply()
    var highThreshold: Float
        get() = prefs.getFloat("high_threshold", 0.80f)
        set(value) = prefs.edit().putFloat("high_threshold", value).apply()
    var lowThreshold: Float
        get() = prefs.getFloat("low_threshold", 0.50f)
        set(value) = prefs.edit().putFloat("low_threshold", value).apply()
    var stabilize: Boolean
        get() = prefs.getBoolean("stabilize", true)
        set(value) = prefs.edit().putBoolean("stabilize", value).apply()
    var animations: Boolean
        get() = prefs.getBoolean("animations", true)
        set(value) = prefs.edit().putBoolean("animations", value).apply()
}

@Composable
fun EcoVisionTheme(dark: Boolean, content: @Composable () -> Unit) {
    val p = if (dark) DarkEco else LightEco
    val colors = if (dark) darkColorScheme(primary = p.accent, background = p.background, surface = p.panel, onSurface = p.text)
    else lightColorScheme(primary = p.accent, background = p.background, surface = p.panel, onSurface = p.text)
    MaterialTheme(colorScheme = colors, content = content)
}

@Composable
fun isDarkEco(mode: String): Boolean = when (mode) {
    "dark" -> true
    "light" -> false
    else -> isSystemInDarkTheme()
}
