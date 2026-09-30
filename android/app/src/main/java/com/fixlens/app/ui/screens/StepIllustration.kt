package com.fixlens.app.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.fixlens.app.ui.theme.FixLensColors
import com.fixlens.app.ui.theme.NotebookEyebrow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Watercolor-style step illustration: a sketched object on the workbench with
 * a hand-drawn motion arrow animated over it, chosen from the step's action
 * verb. Pure Canvas, no images, no network, deterministic geometry with a
 * moving terracotta head over a faint ink "ghost" of the full path.
 *
 * This is an instructional aid (how the motion should feel), never a claim
 * about what the camera sees, that remains Show Me's honest boundary.
 */
enum class StepMotion(val label: String) {
    MOVE("MOVE"),
    ROTATE("ROTATE"),
    PRESS("PRESS"),
    PULL("LIFT"),
    SLIDE("SLIDE"),
    FASTEN("FASTEN"),
    PLACE("PLACE"),
    APPLY("APPLY"),
    FIX("FIX"),
    ;

    companion object {
        /** Verb matching over the step's action sentence (order matters). */
        fun fromAction(action: String): StepMotion {
            val a = action.lowercase()
            fun has(vararg words: String): Boolean = words.any { a.contains(it) }
            return when {
                has("rotate", "turn", "twist", "unscrew") -> ROTATE
                has("slide") -> SLIDE
                has("press", "push") -> PRESS
                has("pull", "lift", "raise") -> PULL
                has("thread", "insert", "hook", "bolt", "fasten", "screw in") -> FASTEN
                has("place", "position", "stand", "set ") -> PLACE
                has("oil", "apply", "spray", "clean", "wipe", "lubricat") -> APPLY
                has("move", "carry", "reposition") -> MOVE
                else -> FIX
            }
        }
    }
}

@Composable
fun StepIllustration(action: String, modifier: Modifier = Modifier) {
    val motion = remember(action) { StepMotion.fromAction(action) }
    val transition = rememberInfiniteTransition(label = "stepMotion")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing)),
        label = "motionProgress",
    )
    Card(
        modifier = modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, FixLensColors.Rule),
        colors = CardDefaults.cardColors(containerColor = FixLensColors.Cream),
        shape = RoundedCornerShape(12.dp, 16.dp, 10.dp, 14.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NotebookEyebrow("HOW IT MOVES")
                Spacer(Modifier.weight(1f))
                Surface(
                    color = FixLensColors.Sage,
                    shape = RoundedCornerShape(6.dp, 9.dp, 7.dp, 8.dp),
                ) {
                    Text(
                        text = motion.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = FixLensColors.SageInk,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(118.dp),
            ) {
                drawScene(motion, t)
            }
        }
    }
}

private fun DrawScope.drawScene(motion: StepMotion, t: Float) {
    val w = size.width
    val h = size.height

    // Soft watercolor washes.
    drawCircle(FixLensColors.Sage.copy(alpha = 0.55f), radius = w * 0.16f, center = Offset(w * 0.24f, h * 0.62f))
    drawCircle(FixLensColors.Lavender.copy(alpha = 0.5f), radius = w * 0.13f, center = Offset(w * 0.78f, h * 0.34f))

    // Bench line.
    drawLine(
        FixLensColors.Rule,
        start = Offset(w * 0.08f, h * 0.84f),
        end = Offset(w * 0.92f, h * 0.84f),
        strokeWidth = 3.dp.toPx(),
        cap = StrokeCap.Round,
    )

    // The object: a sketchy panel with two fasteners.
    val objLeft = w * 0.32f
    val objTop = h * 0.52f
    val objW = w * 0.38f
    val objH = h * 0.3f
    drawRoundRect(
        FixLensColors.Ink.copy(alpha = 0.85f),
        topLeft = Offset(objLeft, objTop),
        size = Size(objW, objH),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx(), 10.dp.toPx()),
        style = Stroke(4.dp.toPx(), cap = StrokeCap.Round),
    )
    val fastA = Offset(objLeft + objW * 0.25f, objTop + objH * 0.5f)
    val fastB = Offset(objLeft + objW * 0.75f, objTop + objH * 0.5f)
    listOf(fastA, fastB).forEach { c ->
        drawCircle(FixLensColors.Ink.copy(alpha = 0.8f), radius = 7.dp.toPx(), center = c, style = Stroke(3.dp.toPx()))
        drawLine(FixLensColors.Ink.copy(alpha = 0.5f), Offset(c.x - 4.dp.toPx(), c.y), Offset(c.x + 4.dp.toPx(), c.y), 2.dp.toPx())
    }

    when (motion) {
        StepMotion.ROTATE -> {
            val c = fastB
            val r = w * 0.14f
            val startDeg = -70f
            val sweep = 300f
            ghostArc(c, r, startDeg, sweep)
            val ang = Math.toRadians((startDeg + sweep * t).toDouble())
            head(Offset(c.x + r * cos(ang).toFloat(), c.y + r * sin(ang).toFloat()), (ang + PI / 2).toFloat())
        }
        StepMotion.MOVE -> {
            val a = Offset(w * 0.12f, h * 0.4f)
            val ctrl = Offset(w * 0.5f, h * 0.2f)
            val b = Offset(w * 0.88f, h * 0.4f)
            ghostQuad(a, ctrl, b)
            head(quadPt(a, ctrl, b, t), quadAngle(a, ctrl, b, t))
        }
        StepMotion.PRESS -> {
            val a = Offset(w * 0.5f, h * 0.12f)
            val b = Offset(w * 0.5f, objTop - 6.dp.toPx())
            ghostLine(a, b)
            head(linePt(a, b, t), lineAngle(a, b))
            if (t > 0.82f) {
                val p = (t - 0.82f) / 0.18f
                drawCircle(
                    FixLensColors.Terracotta.copy(alpha = (1f - p) * 0.8f),
                    radius = 6.dp.toPx() + p * 16.dp.toPx(),
                    center = b,
                    style = Stroke(3.dp.toPx()),
                )
            }
        }
        StepMotion.PULL -> {
            val a = Offset(w * 0.5f, objTop - 4.dp.toPx())
            val b = Offset(w * 0.5f, h * 0.12f)
            ghostLine(a, b)
            head(linePt(a, b, t), lineAngle(a, b))
        }
        StepMotion.SLIDE -> {
            val a = Offset(w * 0.26f, h * 0.42f)
            val b = Offset(w * 0.74f, h * 0.42f)
            ghostLine(a, b)
            // Static double heads; the terracotta head ping-pongs.
            arrowHead(a, lineAngle(b, a))
            arrowHead(b, lineAngle(a, b))
            val tt = if (t < 0.5f) t * 2f else 2f - t * 2f
            dot(linePt(a, b, tt))
        }
        StepMotion.FASTEN -> {
            val a = Offset(w * 0.12f, h * 0.22f)
            val ctrl = Offset(w * 0.3f, h * 0.2f)
            val b = fastA
            ghostQuad(a, ctrl, b)
            head(quadPt(a, ctrl, b, t), quadAngle(a, ctrl, b, t))
            if (t > 0.9f) {
                drawCircle(FixLensColors.Terracotta.copy(alpha = 0.6f), radius = 10.dp.toPx(), center = b, style = Stroke(3.dp.toPx()))
            }
        }
        StepMotion.PLACE -> {
            // Dashed landing ellipse + descending arrow.
            val cx = w * 0.5f
            val cy = h * 0.78f
            val rx = w * 0.16f
            val ry = 6.dp.toPx()
            var i = 0
            while (i < 12) {
                val s = Math.toRadians((i * 30).toDouble())
                val e = Math.toRadians((i * 30 + 16).toDouble())
                drawLine(
                    FixLensColors.SageInk,
                    Offset(cx + rx * cos(s).toFloat(), cy + ry * sin(s).toFloat()),
                    Offset(cx + rx * cos(e).toFloat(), cy + ry * sin(e).toFloat()),
                    3.dp.toPx(),
                    StrokeCap.Round,
                )
                i++
            }
            val a = Offset(cx, h * 0.14f)
            val b = Offset(cx, cy - 8.dp.toPx())
            ghostLine(a, b)
            head(linePt(a, b, t), lineAngle(a, b))
        }
        StepMotion.APPLY -> {
            val a = Offset(w * 0.42f, h * 0.16f)
            val b = Offset(w * 0.42f, objTop - 4.dp.toPx())
            ghostLine(a, b)
            // Falling droplet instead of an arrow head.
            val p = linePt(a, b, t)
            drawCircle(FixLensColors.Terracotta.copy(alpha = 0.85f), radius = 5.dp.toPx(), center = p)
            drawCircle(FixLensColors.Terracotta.copy(alpha = 0.35f), radius = 9.dp.toPx(), center = Offset(b.x, b.y + 2.dp.toPx()), style = Stroke(3.dp.toPx()))
        }
        StepMotion.FIX -> {
            val a = Offset(w * 0.14f, h * 0.62f)
            val ctrl = Offset(w * 0.45f, h * 0.18f)
            val b = Offset(w * 0.8f, h * 0.5f)
            ghostQuad(a, ctrl, b)
            head(quadPt(a, ctrl, b, t), quadAngle(a, ctrl, b, t))
        }
    }
}

// ---------- ghost path + animated terracotta head ----------

private fun DrawScope.ghostLine(a: Offset, b: Offset) {
    drawLine(FixLensColors.Ink.copy(alpha = 0.22f), a, b, 4.dp.toPx(), StrokeCap.Round)
}

private fun DrawScope.ghostQuad(a: Offset, c: Offset, b: Offset) {
    val path = Path().apply {
        moveTo(a.x, a.y)
        quadraticBezierTo(c.x, c.y, b.x, b.y)
    }
    drawPath(path, FixLensColors.Ink.copy(alpha = 0.22f), style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
}

private fun DrawScope.ghostArc(c: Offset, r: Float, startDeg: Float, sweepDeg: Float) {
    drawArc(
        FixLensColors.Ink.copy(alpha = 0.22f),
        startAngle = startDeg,
        sweepAngle = sweepDeg,
        useCenter = false,
        topLeft = Offset(c.x - r, c.y - r),
        size = Size(r * 2, r * 2),
        style = Stroke(4.dp.toPx(), cap = StrokeCap.Round),
    )
}

private fun DrawScope.head(p: Offset, angleRad: Float) {
    // Trail segment + terracotta arrowhead pointing along the motion.
    drawCircle(FixLensColors.Terracotta, radius = 4.dp.toPx(), center = p)
    arrowHead(p, angleRad, color = FixLensColors.Terracotta, len = 13.dp.toPx(), width = 4.dp.toPx())
}

private fun DrawScope.dot(p: Offset) {
    drawCircle(FixLensColors.Terracotta, radius = 5.dp.toPx(), center = p)
}

private fun DrawScope.arrowHead(
    tip: Offset,
    angleRad: Float,
    color: androidx.compose.ui.graphics.Color = FixLensColors.Ink,
    len: Float = 12.dp.toPx(),
    width: Float = 3.5f.dp.toPx(),
) {
    val spread = 0.5f // radians, half-angle of the head
    val left = angleRad + PI.toFloat() - spread
    val right = angleRad + PI.toFloat() + spread
    drawLine(color, tip, Offset(tip.x + len * cos(left), tip.y + len * sin(left)), width, StrokeCap.Round)
    drawLine(color, tip, Offset(tip.x + len * cos(right), tip.y + len * sin(right)), width, StrokeCap.Round)
}

// ---------- parametric helpers ----------

private fun linePt(a: Offset, b: Offset, t: Float): Offset =
    Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)

private fun lineAngle(a: Offset, b: Offset): Float = Math.atan2((b.y - a.y).toDouble(), (b.x - a.x).toDouble()).toFloat()

private fun quadPt(a: Offset, c: Offset, b: Offset, t: Float): Offset {
    val u = 1f - t
    return Offset(
        u * u * a.x + 2 * u * t * c.x + t * t * b.x,
        u * u * a.y + 2 * u * t * c.y + t * t * b.y,
    )
}

private fun quadAngle(a: Offset, c: Offset, b: Offset, t: Float): Float {
    val d = 0.01f
    val p1 = quadPt(a, c, b, (t - d).coerceAtLeast(0f))
    val p2 = quadPt(a, c, b, (t + d).coerceIn(0f, 1f))
    return Math.atan2((p2.y - p1.y).toDouble(), (p2.x - p1.x).toDouble()).toFloat()
}
