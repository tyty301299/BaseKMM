package com.example.kmmbase.android.features.looks.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp

internal enum class LookSymbol { OUTFIT, WARDROBE, LEARN, HISTORY, BACK, FLIP }

/** Decorative vector icons; parent buttons provide their accessible labels. */
@Composable
internal fun LookIcon(symbol: LookSymbol, color: Color = Color.White) {
    Canvas(Modifier.size(24.dp)) {
        scale(size.width / 24f, size.height / 24f, Offset.Zero) {
            val stroke = Stroke(width = 1.7f, cap = StrokeCap.Round)
            fun line(vararg points: Pair<Float, Float>) {
                val path = Path().apply { points.forEachIndexed { i, p -> if (i == 0) moveTo(p.first,p.second) else lineTo(p.first,p.second) } }
                drawPath(path, color, style = stroke)
            }
            when (symbol) {
                LookSymbol.OUTFIT -> line(12f to 2f,15f to 9f,22f to 12f,15f to 15f,12f to 22f,9f to 15f,2f to 12f,9f to 9f,12f to 2f)
                LookSymbol.WARDROBE -> line(8f to 4f,2f to 8f,5f to 13f,8f to 11f,8f to 21f,16f to 21f,16f to 11f,19f to 13f,22f to 8f,16f to 4f,14f to 7f,10f to 7f,8f to 4f)
                LookSymbol.LEARN -> {
                    line(3f to 4f,10f to 4f,12f to 6f,14f to 4f,21f to 4f,21f to 20f,14f to 20f,12f to 22f,10f to 20f,3f to 20f,3f to 4f)
                    line(12f to 6f,12f to 22f)
                }
                LookSymbol.HISTORY -> {
                    drawCircle(color, 9f, Offset(12f,12f), style = stroke)
                    line(12f to 6f,12f to 12f,16f to 14f)
                }
                LookSymbol.BACK -> line(15f to 5f,8f to 12f,15f to 19f)
                LookSymbol.FLIP -> {
                    drawArc(color, 40f, 280f, false, Offset(3f,3f), Size(18f,18f), style = stroke)
                    line(20f to 2f,20f to 8f,14f to 8f)
                }
            }
        }
    }
}
