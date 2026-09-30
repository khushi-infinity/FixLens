package com.fixlens.app.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.random.Random

/** Quiet, deterministic paper flecks, cached at the current size rather than animated noise. */
fun Modifier.paperSurface() = drawWithCache {
    val random = Random(41)
    val flecks = List(((size.width * size.height) / 900f).toInt().coerceAtMost(3200)) {
        Offset(random.nextFloat() * size.width, random.nextFloat() * size.height)
    }
    onDrawBehind {
        drawRect(FixLensColors.Paper)
        flecks.forEach { drawCircle(FixLensColors.Ink.copy(alpha = 0.035f), 0.55.dp.toPx(), it) }
    }
}

@Composable
fun NotebookEyebrow(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style = MaterialTheme.typography.labelSmall, color = FixLensColors.MutedInk)
}

/** Organic contour and pencilled arrow. A framing aid only; never a detected component. */
@Composable
fun SketchTarget(modifier: Modifier = Modifier, annotated: Boolean = false) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val x = w * 0.5f
        val y = h * 0.44f
        val rx = w * 0.28f
        val ry = minOf(h * 0.16f, w * 0.31f)
        val contour = Path().apply {
            moveTo(x - rx, y + ry * 0.1f)
            cubicTo(x - rx * 1.12f, y - ry, x + rx * 0.72f, y - ry * 1.2f, x + rx, y - ry * 0.14f)
            cubicTo(x + rx * 1.15f, y + ry, x - rx * 0.8f, y + ry * 1.18f, x - rx, y + ry * 0.1f)
        }
        drawPath(contour, FixLensColors.CameraInk.copy(alpha = 0.7f), style = Stroke(5.dp.toPx(), cap = StrokeCap.Round))
        drawPath(contour, FixLensColors.Cream, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        if (annotated) {
            val arrow = Path().apply {
                moveTo(w * 0.87f, y - ry * 1.65f)
                quadraticBezierTo(w * 0.62f, y - ry * 1.65f, x + rx * 0.35f, y - ry * 0.55f)
                moveTo(x + rx * 0.34f, y - ry * 0.55f)
                lineTo(x + rx * 0.39f, y - ry * 0.92f)
                moveTo(x + rx * 0.34f, y - ry * 0.55f)
                lineTo(x + rx * 0.7f, y - ry * 0.65f)
            }
            drawPath(arrow, FixLensColors.CameraInk, style = Stroke(5.dp.toPx(), cap = StrokeCap.Round))
            drawPath(arrow, FixLensColors.ClayWash, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

/** Opaque paper bands keep controls legible over every camera scene. */
@Composable
fun CameraPaperBands(modifier: Modifier = Modifier, bottom: Boolean = true) {
    Column(modifier.fillMaxSize()) {
        Surface(color = FixLensColors.Paper) {
            Spacer(Modifier.fillMaxWidth().statusBarsPadding().height(64.dp))
        }
        Spacer(Modifier.weight(1f))
        if (bottom) Surface(color = FixLensColors.Paper) {
            Spacer(Modifier.fillMaxWidth().navigationBarsPadding().height(126.dp))
        }
    }
}

@Composable
fun CameraFieldNote(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = FixLensColors.Cream,
        shape = RoundedCornerShape(6.dp, 10.dp, 8.dp, 5.dp),
        border = BorderStroke(1.dp, FixLensColors.Rule),
    ) {
        Text(text, Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodySmall, color = FixLensColors.Ink)
    }
}
