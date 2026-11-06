package com.example.ecovision

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun EcoPageHeader(title: String, p: EcoPalette, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад", tint = p.text) }
        Spacer(Modifier.width(8.dp))
        Text(title, fontSize = 25.sp, fontWeight = FontWeight.Bold, color = p.text)
    }
}

@Composable
private fun EcoSwitchRow(title: String, description: String, checked: Boolean, p: EcoPalette, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = p.text, fontWeight = FontWeight.SemiBold)
            Text(description, color = p.muted, fontSize = 12.sp)
        }
        Switch(checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun EcoSection(title: String, p: EcoPalette, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = p.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Column(Modifier.fillMaxWidth().background(p.panel, RoundedCornerShape(24.dp)).padding(18.dp), content = content)
    }
}

@Composable
fun SettingsScreen(
    p: EcoPalette,
    prefs: EcoPreferences,
    theme: String,
    onThemeChange: (String) -> Unit,
    onBack: () -> Unit,
    onDeveloper: () -> Unit
) {
    var stabilize by remember { mutableStateOf(prefs.stabilize) }
    var animations by remember { mutableStateOf(prefs.animations) }
    var unlocked by remember { mutableStateOf(prefs.developerUnlocked) }
    Column(Modifier.fillMaxSize().background(p.background).statusBarsPadding()
        .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)) {
        EcoPageHeader("Настройки", p, onBack)
        EcoSection("Внешний вид", p) {
            Text("Тема оформления", color = p.text, fontWeight = FontWeight.SemiBold)
            listOf("system" to "Как в системе", "light" to "Светлая", "dark" to "Тёмная").forEach { (value, title) ->
                Row(Modifier.fillMaxWidth().clickable { onThemeChange(value) }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = theme == value, onClick = { onThemeChange(value) })
                    Text(title, color = p.text)
                }
            }
            HorizontalDivider(color = p.soft)
            EcoSwitchRow("Плавные анимации", "Анимации интерфейса и результатов", animations, p) {
                animations = it; prefs.animations = it
            }
        }
        EcoSection("Камера и сканирование", p) {
            EcoSwitchRow("Стабильный результат", "Уменьшает скачки между категориями", stabilize, p) {
                stabilize = it; prefs.stabilize = it
            }
            Text("Предсказание происходит прямо на устройстве. Снимки не сохраняются.", color = p.muted, fontSize = 12.sp)
        }
        if (unlocked) {
            EcoSection("Дополнительно", p) {
                Row(Modifier.fillMaxWidth().clickable(onClick = onDeveloper).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Для разработчиков", color = p.text, fontWeight = FontWeight.SemiBold)
                        Text("Проценты и отладка предсказаний", color = p.muted, fontSize = 12.sp)
                    }
                    Icon(Icons.Rounded.ChevronRight, null, tint = p.text)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
fun DeveloperScreen(p: EcoPalette, prefs: EcoPreferences, onBack: () -> Unit) {
    var showPercent by remember { mutableStateOf(prefs.showPercent) }
    var showRaw by remember { mutableStateOf(prefs.showRaw) }
    var custom by remember { mutableStateOf(prefs.customThresholds) }
    var high by remember { mutableFloatStateOf(prefs.highThreshold) }
    var low by remember { mutableFloatStateOf(prefs.lowThreshold) }
    Column(Modifier.fillMaxSize().background(p.background).statusBarsPadding()
        .verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        EcoPageHeader("Режим разработчика", p, onBack)
        EcoSection("Отображение", p) {
            EcoSwitchRow("Показывать проценты", "Точная оценка уверенности модели", showPercent, p) {
                showPercent = it; prefs.showPercent = it
            }
            HorizontalDivider(color = p.soft)
            EcoSwitchRow("Raw predictions", "Исходное значение лучшего предсказания", showRaw, p) {
                showRaw = it; prefs.showRaw = it
            }
        }
        EcoSection("Цветовые пороги", p) {
            EcoSwitchRow("Свои пороги", "Изменить границы статусов", custom, p) {
                custom = it; prefs.customThresholds = it
            }
            if (custom) {
                Text("Зелёный: от ${(high * 100).toInt()}%", color = p.text)
                Slider(value = high, onValueChange = { high = it.coerceAtLeast(low + 0.05f); prefs.highThreshold = high }, valueRange = 0.55f..1.0f)
                Text("Жёлтый: от ${(low * 100).toInt()}%", color = p.text)
                Slider(value = low, onValueChange = { low = it.coerceAtMost(high - 0.05f); prefs.lowThreshold = low }, valueRange = 0.05f..0.90f)
            } else Text("Стандарт: зелёный от 80%, жёлтый от 50%.", color = p.muted, fontSize = 13.sp)
        }
        Text("Число уверенности — выход модели, а не гарантия правильной классификации.", color = p.muted, fontSize = 12.sp)
    }
}

@Composable
fun AboutScreen(p: EcoPalette, prefs: EcoPreferences, onBack: () -> Unit, onUnlock: () -> Unit) {
    var taps by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().background(p.background).statusBarsPadding().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)) {
        EcoPageHeader("О приложении", p, onBack)
        Column(Modifier.fillMaxWidth().background(p.panel, RoundedCornerShape(28.dp)).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("EcoVision", color = p.accent, fontSize = 35.sp, fontWeight = FontWeight.Black)
            Text("SORT SMARTER", color = p.muted, letterSpacing = 3.sp)
            Text("Распознавание материалов с помощью нейросети прямо на устройстве.", color = p.text)
            Text("Камера анализирует кадры без сохранения фотографий.", color = p.muted, fontSize = 13.sp)
            Text("Создатели 'МусорДроп':\nВедущие инженеры: Валерий Семянников, Александр Бабарик\nКоллаборатор: ЧатГПТ", color = p.muted, fontSize = 13.sp)
            Text("Версия 1.2.1", color = p.text, modifier = Modifier.clickable {
                if (!prefs.developerUnlocked) {
                    taps++
                    if (taps >= 7) { prefs.developerUnlocked = true; onUnlock() }
                }
            }.padding(12.dp))
            if (prefs.developerUnlocked) Text("Режим разработчика открыт в настройках", color = p.accent, fontSize = 12.sp)
            else if (taps >= 3) Text("До режима разработчика: ${7 - taps}", color = p.muted, fontSize = 12.sp)
        }
    }
}
