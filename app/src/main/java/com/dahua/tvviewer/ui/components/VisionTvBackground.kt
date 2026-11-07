package com.dahua.tvviewer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path

@Composable
fun VisionTvBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF07101B))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // 1. Deep ambient dark-blue base glow from top-left
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF0A223E).copy(alpha = 0.85f),
                        Color(0xFF08182B).copy(alpha = 0.50f),
                        Color.Transparent
                    ),
                    center = Offset(width * 0.18f, height * 0.12f),
                    radius = width * 0.70f
                )
            )

            // 2. Subtle electric blue sweep in the top-right
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF0066FF).copy(alpha = 0.22f),
                        Color(0xFF00A3FF).copy(alpha = 0.10f),
                        Color.Transparent
                    ),
                    center = Offset(width * 0.85f, height * 0.05f),
                    radius = width * 0.55f
                )
            )

            // 3. Diagonal wave / curved layer 1 (matching the header in the brand manual)
            val path1 = Path().apply {
                moveTo(0f, 0f)
                lineTo(width * 0.44f, 0f)
                cubicTo(
                    width * 0.36f, height * 0.38f,
                    width * 0.16f, height * 0.58f,
                    0f, height * 0.80f
                )
                close()
            }
            drawPath(
                path = path1,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF003D7A).copy(alpha = 0.30f),
                        Color(0xFF001F40).copy(alpha = 0.12f)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(width * 0.35f, height * 0.55f)
                )
            )

            // 4. Diagonal accent wave 2 (electric blue blade accent #00A3FF)
            val path2 = Path().apply {
                moveTo(width * 0.18f, 0f)
                cubicTo(
                    width * 0.26f, height * 0.30f,
                    width * 0.13f, height * 0.62f,
                    0f, height * 0.90f
                )
                lineTo(0f, height * 0.70f)
                cubicTo(
                    width * 0.10f, height * 0.48f,
                    width * 0.20f, height * 0.22f,
                    width * 0.12f, 0f
                )
                close()
            }
            drawPath(
                path = path2,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF00A3FF).copy(alpha = 0.35f),
                        Color(0xFF0066FF).copy(alpha = 0.12f)
                    ),
                    start = Offset(width * 0.15f, 0f),
                    end = Offset(0f, height * 0.80f)
                )
            )
        }

        content()
    }
}
