package com.aline.viagens.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.aline.viagens.data.UserPrefs

/** As paletas que cada pessoa pode escolher. A cor base gera todo o resto. */
enum class Palette(val id: String, val label: String, val base: Color, val accent: Color) {
    OCEANO("oceano", "Azul oceano", Color(0xFF1E6091), Color(0xFF2A9D8F)),
    FLORESTA("floresta", "Verde floresta", Color(0xFF2D6A4F), Color(0xFFB08B3E)),
    TERRACOTA("terracota", "Terracota", Color(0xFFB5543C), Color(0xFF4F6D7A)),
    LAVANDA("lavanda", "Lavanda", Color(0xFF7B5EA7), Color(0xFFC0617F)),
    GRAFITE("grafite", "Grafite", Color(0xFF4A5560), Color(0xFFD08C3A)),
    CORAL("coral", "Coral", Color(0xFFD2544E), Color(0xFF3D7A8A));

    companion object {
        fun from(id: String) = entries.firstOrNull { it.id == id } ?: OCEANO
    }
}

private val White = Color.White
private val Ink = Color(0xFF15181C)
private val Night = Color(0xFF111417)

private fun lightScheme(p: Palette): ColorScheme {
    val b = p.base
    val bg = lerp(b, White, 0.95f)
    return lightColorScheme(
        primary = b,
        onPrimary = White,
        primaryContainer = lerp(b, White, 0.80f),
        onPrimaryContainer = lerp(b, Ink, 0.60f),
        secondary = p.accent,
        onSecondary = White,
        secondaryContainer = lerp(p.accent, White, 0.80f),
        onSecondaryContainer = lerp(p.accent, Ink, 0.60f),
        tertiary = p.accent,
        onTertiary = White,
        tertiaryContainer = lerp(p.accent, White, 0.80f),
        onTertiaryContainer = lerp(p.accent, Ink, 0.60f),
        background = bg,
        onBackground = Ink,
        surface = bg,
        onSurface = Ink,
        surfaceVariant = lerp(b, White, 0.86f),
        onSurfaceVariant = lerp(b, Ink, 0.75f),
        surfaceContainerLowest = White,
        surfaceContainerLow = lerp(b, White, 0.93f),
        surfaceContainer = lerp(b, White, 0.91f),
        surfaceContainerHigh = lerp(b, White, 0.88f),
        surfaceContainerHighest = lerp(b, White, 0.85f),
        outline = lerp(b, Ink, 0.30f).copy(alpha = 0.55f),
        outlineVariant = lerp(b, White, 0.75f),
    )
}

private fun darkScheme(p: Palette): ColorScheme {
    val b = p.base
    val bg = lerp(b, Night, 0.90f)
    return darkColorScheme(
        primary = lerp(b, White, 0.40f),
        onPrimary = lerp(b, Ink, 0.70f),
        primaryContainer = lerp(b, Night, 0.45f),
        onPrimaryContainer = lerp(b, White, 0.82f),
        secondary = lerp(p.accent, White, 0.40f),
        onSecondary = lerp(p.accent, Ink, 0.70f),
        secondaryContainer = lerp(p.accent, Night, 0.50f),
        onSecondaryContainer = lerp(p.accent, White, 0.82f),
        tertiary = lerp(p.accent, White, 0.40f),
        onTertiary = lerp(p.accent, Ink, 0.70f),
        tertiaryContainer = lerp(p.accent, Night, 0.50f),
        onTertiaryContainer = lerp(p.accent, White, 0.82f),
        background = bg,
        onBackground = Color(0xFFE6E8EB),
        surface = bg,
        onSurface = Color(0xFFE6E8EB),
        surfaceVariant = lerp(b, Night, 0.75f),
        onSurfaceVariant = lerp(b, White, 0.70f),
        surfaceContainerLowest = lerp(b, Night, 0.95f),
        surfaceContainerLow = lerp(b, Night, 0.86f),
        surfaceContainer = lerp(b, Night, 0.83f),
        surfaceContainerHigh = lerp(b, Night, 0.79f),
        surfaceContainerHighest = lerp(b, Night, 0.75f),
        outline = lerp(b, White, 0.45f).copy(alpha = 0.55f),
        outlineVariant = lerp(b, Night, 0.65f),
    )
}

@Composable
fun VamoTheme(prefs: UserPrefs, content: @Composable () -> Unit) {
    val palette = Palette.from(prefs.palette)
    val dark = when (prefs.mode) {
        "claro" -> false
        "escuro" -> true
        else -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (dark) darkScheme(palette) else lightScheme(palette),
        content = content,
    )
}
