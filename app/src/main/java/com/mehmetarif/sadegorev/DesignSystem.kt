package com.mehmetarif.sadegorev

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/* ───────────────────────── Renkler ───────────────────────── */

/** Koyu zeminler için vurgu renkleri (widget'lar da bunları kullanır). */
val AccentColors = listOf(Color(0xFF8B97FF), Color(0xFF5AAEFF), Color(0xFF3FD18F), Color(0xFFB792FF), Color(0xFFFF7FB6))
/** Açık zemin için vurgu renkleri. */
val LightAccentColors = listOf(Color(0xFF4F5BD5), Color(0xFF1C6FE8), Color(0xFF0F8F57), Color(0xFF7646D8), Color(0xFFD13A7B))
val AccentNames = listOf("İndigo", "Mavi", "Yeşil", "Mor", "Pembe")

/** Öncelik renkleri: 0 düşük, 1 orta, 2 yüksek. */
fun priorityColor(priority: Int): Color = when (priority) { 2 -> Color(0xFFEF4B4B); 1 -> Color(0xFFF2A324); else -> Color(0xFF3E9BFF) }
fun priorityLabel(priority: Int): String = when (priority) { 2 -> "Yüksek"; 1 -> "Orta"; else -> "Düşük" }

/** Ders renk işaretleri. */
val CourseColors = listOf(Color(0xFF5B8DEF), Color(0xFF2FBF8F), Color(0xFFF08A5D), Color(0xFF9B7BF0), Color(0xFFE9B949))

/* ───────────────────────── Tema ───────────────────────── */

private val Light = lightColorScheme(
    background = Color(0xFFF4F5F9), onBackground = Color(0xFF14161B),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF14161B),
    surfaceVariant = Color(0xFFEDEFF4), onSurfaceVariant = Color(0xFF666C7A),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF9FAFC),
    surfaceContainer = Color(0xFFF1F2F7), surfaceContainerHigh = Color(0xFFFFFFFF), surfaceContainerHighest = Color(0xFFE9EBF1),
    outline = Color(0xFFDCDFE7), outlineVariant = Color(0xFFE8EAF0),
    error = Color(0xFFD93A3F), onError = Color.White,
)
private fun darkScheme(navy: Boolean) = if (navy) darkColorScheme(
    background = Color(0xFF0A1120), onBackground = Color(0xFFEFF3FA),
    surface = Color(0xFF131C2E), onSurface = Color(0xFFEFF3FA),
    surfaceVariant = Color(0xFF1C2840), onSurfaceVariant = Color(0xFF93A2BC),
    surfaceContainerLowest = Color(0xFF0A1120), surfaceContainerLow = Color(0xFF101828),
    surfaceContainer = Color(0xFF151F33), surfaceContainerHigh = Color(0xFF1A253B), surfaceContainerHighest = Color(0xFF223049),
    outline = Color(0xFF263450), outlineVariant = Color(0xFF1E2A42),
    error = Color(0xFFFF6B6B),
) else darkColorScheme(
    background = Color(0xFF0D0E12), onBackground = Color(0xFFF1F2F6),
    surface = Color(0xFF17191F), onSurface = Color(0xFFF1F2F6),
    surfaceVariant = Color(0xFF22252D), onSurfaceVariant = Color(0xFF9097A6),
    surfaceContainerLowest = Color(0xFF0D0E12), surfaceContainerLow = Color(0xFF131519),
    surfaceContainer = Color(0xFF1A1C22), surfaceContainerHigh = Color(0xFF20232A), surfaceContainerHighest = Color(0xFF282B33),
    outline = Color(0xFF2B2E37), outlineVariant = Color(0xFF23262E),
    error = Color(0xFFFF6B6B),
)

private val AppTypography = Typography().let { t ->
    t.copy(
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineLarge = t.headlineLarge.copy(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp),
        headlineMedium = t.headlineMedium.copy(fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
        headlineSmall = t.headlineSmall.copy(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
        titleLarge = t.titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        titleSmall = t.titleSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        bodyLarge = t.bodyLarge.copy(fontSize = 16.sp, letterSpacing = 0.sp),
        bodyMedium = t.bodyMedium.copy(fontSize = 14.sp, letterSpacing = 0.sp),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        labelMedium = t.labelMedium.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.sp),
    )
}

@Composable internal fun SadeTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { AppPreferences.prefs(context) }
    var mode by remember { mutableIntStateOf(prefs.getInt(AppPreferences.APP_THEME, 0)) }
    var accentIndex by remember { mutableIntStateOf(prefs.getInt(AppPreferences.APP_ACCENT, 0)) }
    DisposableEffect(prefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            if (key == AppPreferences.APP_THEME) mode = p.getInt(key, 0)
            if (key == AppPreferences.APP_ACCENT) accentIndex = p.getInt(key, 0)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    // mode: 0 koyu, 1 açık, 2 gece mavisi, 3 sistem
    val dark = if (mode == 3) isSystemInDarkTheme() else mode != 1
    val i = accentIndex.coerceIn(0, 4)
    val accent = if (dark) AccentColors[i] else LightAccentColors[i]
    val base = if (dark) darkScheme(mode == 2) else Light
    val palette = base.copy(
        primary = accent, onPrimary = if (dark) Color(0xFF0B0D14) else Color.White,
        primaryContainer = accent.copy(alpha = if (dark) 0.18f else 0.12f), onPrimaryContainer = accent,
        secondaryContainer = accent.copy(alpha = if (dark) 0.18f else 0.12f), onSecondaryContainer = accent,
        tertiary = accent,
    )
    SideEffect {
        (context as? ComponentActivity)?.enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark },
            navigationBarStyle = androidx.activity.SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark })
    }
    val animated = palette.copy(
        background = animateColorAsState(palette.background, tween(300), label = "bg").value,
        surface = animateColorAsState(palette.surface, tween(300), label = "surface").value,
        primary = animateColorAsState(palette.primary, tween(300), label = "accent").value,
    )
    MaterialTheme(
        colorScheme = animated, typography = AppTypography,
        shapes = Shapes(extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(28.dp)),
        content = content,
    )
}

/* ───────────────────────── Bileşenler ───────────────────────── */

val CardShape = RoundedCornerShape(20.dp)

/** Hafif kenarlıklı, gölgesiz kart. */
@Composable fun AppCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, color: Color = MaterialTheme.colorScheme.surface, shape: androidx.compose.ui.graphics.Shape = CardShape, content: @Composable ColumnScope.() -> Unit) {
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    if (onClick != null) Surface(onClick = onClick, modifier = modifier, shape = shape, color = color, border = border) { Column(content = content) }
    else Surface(modifier = modifier, shape = shape, color = color, border = border) { Column(content = content) }
}

/** Ekran başlığı: küçük üst satır + büyük başlık + sağda eylemler. */
@Composable fun PageHeader(title: String, modifier: Modifier = Modifier, eyebrow: String? = null, subtitle: String? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(top = 20.dp, bottom = 12.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            if (eyebrow != null) Text(eyebrow, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/** Liste bölüm başlığı, isteğe bağlı sayaç ile. */
@Composable fun SectionHeader(title: String, modifier: Modifier = Modifier, count: Int? = null, color: Color = MaterialTheme.colorScheme.onSurfaceVariant, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = color)
        if (count != null) {
            Spacer(Modifier.width(8.dp))
            Box(Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 8.dp, vertical = 1.dp)) {
                Text(count.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.weight(1f))
        trailing()
    }
}

/** Eski ekranlarla uyumlu bölüm etiketi. */
@Composable fun SectionLabel(text: String) = SectionHeader(text)

/** Renkli zemin üzerinde ikon. */
@Composable fun IconTile(icon: ImageVector, tint: Color, modifier: Modifier = Modifier, size: Dp = 38.dp, iconSize: Dp = 20.dp) {
    Box(modifier.size(size).clip(RoundedCornerShape(size * 0.32f)).background(tint.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(iconSize), tint = tint)
    }
}

/** Boş liste görünümü. */
@Composable fun EmptyState(icon: ImageVector, title: String, message: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(84.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(38.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(18.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(6.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (action != null) { Spacer(Modifier.height(16.dp)); action() }
    }
}

/** Ayar grubunu tek kartta toplar. */
@Composable fun SettingsGroup(modifier: Modifier = Modifier, title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth()) {
        if (title != null) Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp))
        AppCard(Modifier.fillMaxWidth(), content = content)
    }
}

@Composable fun GroupDivider(inset: Dp = 66.dp) = HorizontalDivider(Modifier.padding(start = inset), thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)

/** Ayar satırı: ikon kutusu, başlık, alt başlık, sağda değer/ok. */
@Composable fun SettingsRow(icon: ImageVector?, title: String, modifier: Modifier = Modifier, subtitle: String? = null, tint: Color = MaterialTheme.colorScheme.primary, value: String? = null, titleColor: Color = MaterialTheme.colorScheme.onSurface, showChevron: Boolean = true, onClick: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 14.dp, vertical = 12.dp).heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { IconTile(icon, tint); Spacer(Modifier.width(14.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = titleColor)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (value != null) Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp).widthIn(max = 150.dp))
        if (trailing != null) { Spacer(Modifier.width(8.dp)); trailing() }
        else if (showChevron && onClick != null) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
    }
}

/** Seçim satırı: dokununca menüden değer seçilir. */
@Composable fun ChoiceRow(icon: ImageVector?, title: String, choices: List<String>, selected: Int, tint: Color = MaterialTheme.colorScheme.primary, onSelected: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        SettingsRow(icon, title, tint = tint, value = choices.getOrElse(selected) { "" }, onClick = { expanded = true })
        DropdownMenu(expanded, { expanded = false }, Modifier.align(Alignment.CenterEnd), shape = RoundedCornerShape(16.dp)) {
            choices.forEachIndexed { i, label ->
                DropdownMenuItem(text = { Text(label, fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Normal) },
                    trailingIcon = { if (i == selected) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary) },
                    onClick = { onSelected(i); expanded = false })
            }
        }
    }
}

/** Kaydırmalı, hap şeklinde segment seçici. */
@Composable fun SegmentedOptions(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, colors: List<Color>? = null) {
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        labels.forEachIndexed { i, label ->
            val active = i == selected
            val bg by animateColorAsState(if (active) MaterialTheme.colorScheme.surface else Color.Transparent, tween(180), label = "segment")
            val elevation by animateDpAsState(if (active) 1.dp else 0.dp, label = "segment-elev")
            Surface(onClick = { onSelect(i) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), color = bg, shadowElevation = elevation) {
                Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    colors?.getOrNull(i)?.let { Box(Modifier.size(8.dp).clip(CircleShape).background(it)); Spacer(Modifier.width(6.dp)) }
                    Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Yuvarlak, kenarlıksız metin alanı renkleri. */
@Composable fun softFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surface, unfocusedContainerColor = MaterialTheme.colorScheme.surface,
    disabledContainerColor = MaterialTheme.colorScheme.surface,
    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, disabledIndicatorColor = Color.Transparent,
)

/** Başlık gibi büyük, çerçevesiz metin alanı. */
@Composable fun BareTextField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.bodyLarge, singleLine: Boolean = false, minLines: Int = 1) {
    TextField(value, onValueChange, modifier.fillMaxWidth(), textStyle = style, singleLine = singleLine, minLines = minLines,
        placeholder = { Text(placeholder, style = style, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)) },
        colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent))
}

/** Görünüm ayarları: tema kutuları + vurgu rengi. */
@Composable fun AppearanceSettings() {
    val context = LocalContext.current
    val prefs = remember { AppPreferences.prefs(context) }
    val scope = rememberCoroutineScope()
    var theme by remember { mutableIntStateOf(prefs.getInt(AppPreferences.APP_THEME, 0)) }
    var accent by remember { mutableIntStateOf(prefs.getInt(AppPreferences.APP_ACCENT, 0)) }
    // Tema değerleri: 3 otomatik, 1 açık, 0 koyu, 2 gece mavisi
    val themes = listOf(Triple(3, "Otomatik", listOf(Color(0xFFF4F5F9), Color(0xFF17191F))), Triple(1, "Açık", listOf(Color(0xFFF4F5F9), Color(0xFFFFFFFF))),
        Triple(0, "Koyu", listOf(Color(0xFF0D0E12), Color(0xFF22252D))), Triple(2, "Gece", listOf(Color(0xFF0A1120), Color(0xFF1C2840))))
    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Tema", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            themes.forEach { (value, label, swatch) ->
                val active = theme == value
                Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).clickable { theme = value; prefs.edit().putInt(AppPreferences.APP_THEME, value).apply() },
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth().height(58.dp).clip(RoundedCornerShape(14.dp))
                        .border(if (active) 2.dp else 1.dp, if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))) {
                        Row(Modifier.fillMaxSize()) { swatch.forEach { Box(Modifier.weight(1f).fillMaxHeight().background(it)) } }
                        Box(Modifier.align(Alignment.BottomStart).padding(8.dp).size(width = 22.dp, height = 6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Text("Vurgu rengi", style = MaterialTheme.typography.titleSmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val dark = MaterialTheme.colorScheme.background.let { (it.red + it.green + it.blue) / 3 < 0.5f }
            (if (dark) AccentColors else LightAccentColors).forEachIndexed { i, color ->
                Box(Modifier.size(40.dp).clip(CircleShape).background(color).clickable {
                    accent = i; prefs.edit().putInt(AppPreferences.APP_ACCENT, i).apply()
                    scope.launch { com.mehmetarif.sadegorev.widget.refreshWidgets(context); com.mehmetarif.sadegorev.widget.refreshScheduleWidgets(context) }
                }, contentAlignment = Alignment.Center) {
                    if (accent == i) Icon(Icons.Rounded.Check, AccentNames[i], Modifier.size(20.dp), tint = if (dark) Color(0xFF0B0D14) else Color.White)
                }
            }
        }
    }
}
