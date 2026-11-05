package com.mehmetarif.sadegorev.widget

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.unit.ColorProvider

/** Both home screen widgets use the same colors and rounded surfaces. */
internal data class WidgetAppearance(val background:Color,val surface:Color,val accent:Color,val muted:Color) {
    fun panel()=GlanceModifier.background(ColorProvider(background)).cornerRadius(22.dp)
    fun card()=GlanceModifier.background(ColorProvider(surface)).cornerRadius(14.dp)
    fun chip(active:Boolean)=GlanceModifier.background(ColorProvider(if(active)androidx.compose.ui.graphics.lerp(background,accent,0.22f) else surface)).cornerRadius(12.dp)
}

internal fun widgetAppearance(theme:Int,accent:Int=0)=when(theme.coerceIn(0,2)) {
    1 -> WidgetAppearance(Color(0xFF0F0E0C),Color(0xFF24221F),com.mehmetarif.sadegorev.AccentColors[accent.coerceIn(0,4)],Color(0xFFBEB9B1))
    2 -> WidgetAppearance(Color(0xFF111E32),Color(0xFF20324D),com.mehmetarif.sadegorev.AccentColors[accent.coerceIn(0,4)],Color(0xFFC2D1E5))
    else -> WidgetAppearance(Color(0xFF1C1A17),Color(0xFF2B2824),com.mehmetarif.sadegorev.AccentColors[accent.coerceIn(0,4)],Color(0xFFC4BEB5))
}
