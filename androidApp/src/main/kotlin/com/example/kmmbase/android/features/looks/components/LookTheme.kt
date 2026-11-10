package com.example.kmmbase.android.features.looks.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

internal val LocalLookDark = staticCompositionLocalOf { true }
internal val LocalLookGlass = staticCompositionLocalOf { true }
internal val LocalOperationBusy = staticCompositionLocalOf { false }
internal val LookAccent = Color(0xFFFFD23F)
internal val LookBackground: Color @Composable get() = if (LocalLookDark.current) Color(0xFF10130F) else Color(0xFFF5F3EE)
internal val LookPanel: Color @Composable get() = if (LocalLookDark.current) Color(0xFF1D231B) else Color.White
internal val LookStudio = Color(0xFFC5CAB8)

@Composable
internal fun LookTheme(content: @Composable () -> Unit) {
    val scheme = if (LocalLookDark.current) darkColorScheme(
        primary = LookAccent, onPrimary = Color(0xFF211C09), background = LookBackground,
        surface = LookPanel, onSurface = Color(0xFFF4F5EB), onBackground = Color(0xFFF4F5EB), onSurfaceVariant = Color(0xFFADB8A4),
    ) else lightColorScheme(primary = Color(0xFF715800), onPrimary = Color.White, background = LookBackground,
        surface = LookPanel, onSurface = Color(0xFF171B15), onBackground = Color(0xFF171B15), onSurfaceVariant = Color(0xFF60675B))
    MaterialTheme(colorScheme = scheme) { Surface(color = LookBackground, contentColor = scheme.onSurface, content = content) }
}

/** Optical glass treatment using Material surfaces; avoids blurring foreground text. */
@Composable
internal fun Modifier.lookGlass(selected: Boolean = false): Modifier {
    val dark = LocalLookDark.current
    val colors = if (!LocalLookGlass.current) listOf(LookPanel, LookPanel)
        else if (dark) listOf(Color(0xF04A5742), Color(0xE62E3928))
        else listOf(Color(0xF7FFFFFF), Color(0xE6E4EADD))
    return background(Brush.verticalGradient(colors), RoundedCornerShape(28.dp))
        .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else if (dark) Color(0xFF66735B) else Color(0xFFC6CEBC), RoundedCornerShape(28.dp))
}

@Composable
internal fun LookButton(title: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled && !LocalOperationBusy.current,
        colors = ButtonDefaults.buttonColors(containerColor = LookAccent, contentColor = Color(0xFF211C09)),
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(title) }
}
