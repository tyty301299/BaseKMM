package com.example.kmmbase.android.features.studio.presentation.components

import com.example.kmmbase.android.common.LocalAppLanguage
import com.example.kmmbase.android.R
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp

@Composable
internal fun StudioInstructionIllustration() {
    val language = LocalAppLanguage.current
    Canvas(Modifier.fillMaxWidth().height(220.dp).background(Color(0xFF3B4A3B), RoundedCornerShape(28.dp))
        .semantics { contentDescription = language.text(R.string.ui_119) }) {
        val factor = minOf(size.width / 320f, size.height / 220f)
        translate((size.width - 320*factor)/2, (size.height - 220*factor)/2) {
            scale(factor, factor, Offset.Zero) {
                drawRoundRect(Color(0xFFAB997D), Offset(14f,139f), Size(292f,66f), CornerRadius(14f))
                drawOval(Color.Black.copy(alpha=.18f), Offset(90f,168f), Size(142f,21f))
                drawRoundRect(Color(0xFF384A38), Offset(107f,162f), Size(106f,15f), CornerRadius(7f))
                drawRoundRect(Color(0xFF7A8F75), Offset(153f,70f), Size(14f,100f), CornerRadius(6f))
                drawRoundRect(Color(0xFFD6DECC), Offset(119f,41f), Size(82f,50f), CornerRadius(9f))
                drawRoundRect(Color(0xFF405740), Offset(125f,47f), Size(70f,38f), CornerRadius(6f))
                val brackets = Path().apply {
                    moveTo(32f,54f);lineTo(32f,34f);lineTo(52f,34f)
                    moveTo(288f,54f);lineTo(288f,34f);lineTo(268f,34f)
                    moveTo(32f,171f);lineTo(32f,191f);lineTo(52f,191f)
                    moveTo(288f,171f);lineTo(288f,191f);lineTo(268f,191f)
                }
                drawPath(brackets,Color.White.copy(alpha=.8f),style=Stroke(2f))
            }
        }
    }
}
