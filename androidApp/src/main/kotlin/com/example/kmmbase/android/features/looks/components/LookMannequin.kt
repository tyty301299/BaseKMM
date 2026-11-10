package com.example.kmmbase.android.features.looks.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/** Vector fixture for native UI review. It is never labelled as a camera or AI photo. */
@Composable
internal fun LookMannequin(preset: String, modifier: Modifier = Modifier, before: Boolean = false, flipped: Boolean = false) {
    Canvas(modifier.semantics { contentDescription = "Hình nhân trang phục mẫu, không phải ảnh AI" }) {
        val factor = minOf(size.width / 345f, size.height / 440f)
        translate((size.width - 345 * factor) / 2, (size.height - 440 * factor) / 2) {
            scale(factor, factor, Offset.Zero) {
                scale(if (flipped) -1f else 1f, 1f, Offset(172.5f, 220f)) {
                    fun polygon(color: Color, vararg points: Pair<Int, Int>) {
                        val path = Path().apply {
                            points.forEachIndexed { i, p -> if (i == 0) moveTo(p.first.toFloat(), p.second.toFloat()) else lineTo(p.first.toFloat(), p.second.toFloat()) }
                            close()
                        }
                        drawPath(path, color)
                    }
                    val skin = Color(0xFFDCAD87)
                    val shirt = if (before) Color(0xFFD9D3C3) else if (preset == "blouse") Color(0xFFE3A783) else Color(0xFF8CA88E)
                    drawOval(Color.Black.copy(alpha = .15f), Offset(112f, 411f), Size(122f, 16f))
                    polygon(Color(0xFF343D33),136 to 242,208 to 242,214 to 304,195 to 408,171 to 408,178 to 298,168 to 298,162 to 408,137 to 408,127 to 304)
                    drawRect(skin, Offset(160f,89f), Size(26f,38f))
                    drawOval(skin, Offset(145f,36f), Size(56f,70f))
                    polygon(Color(0xFF3D352D),146 to 70,143 to 45,153 to 31,181 to 28,198 to 40,202 to 73,195 to 48,168 to 53,149 to 52)
                    polygon(skin,128 to 118,108 to 139,87 to 253,101 to 260,134 to 160,204 to 118,226 to 139,251 to 252,237 to 258,205 to 161)
                    polygon(shirt,129 to 116,157 to 108,173 to 120,188 to 108,212 to 116,229 to 139,244 to 243,225 to 247,212 to 165,212 to 262,130 to 262,127 to 165,113 to 246,93 to 242,108 to 138)
                    polygon(Color.White.copy(alpha = .2f),157 to 108,173 to 127,156 to 146,143 to 118)
                    polygon(Color.White.copy(alpha = .2f),188 to 108,173 to 127,190 to 146,202 to 117)
                    drawLine(Color.Black.copy(alpha = .15f),Offset(173f,129f),Offset(173f,262f))
                    listOf(164f,203f,241f).forEach { drawCircle(Color.Black.copy(alpha = .4f),1.5f,Offset(177f,it)) }
                    polygon(Color.White,135 to 403,161 to 403,161 to 416,121 to 416,126 to 409)
                    polygon(Color.White,172 to 403,196 to 403,213 to 415,172 to 416)
                }
            }
        }
    }
}
