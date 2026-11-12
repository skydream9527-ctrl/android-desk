package com.skydream.androiddesk.app.ui

import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shape
import com.skydream.androiddesk.core.icon.IconShape
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

private val squircle: Shape = GenericShape { size, _ ->
    // Superellipse |x|^n + |y|^n = 1: flatter sides and tighter corners than a rounded rect.
    val n = 4.2
    val rx = size.width / 2f
    val ry = size.height / 2f
    val steps = 96
    for (i in 0 until steps) {
        val t = 2 * PI * i / steps
        val c = cos(t)
        val s = sin(t)
        val x = rx + rx * (sign(c) * abs(c).pow(2 / n)).toFloat()
        val y = ry + ry * (sign(s) * abs(s).pow(2 / n)).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

private val hexagon: Shape = GenericShape { size, _ ->
    // Pointy-top hexagon with softened corners.
    val cx = size.width / 2f
    val cy = size.height / 2f
    val r = minOf(size.width, size.height) / 2f
    val corners = (0 until 6).map { i ->
        val a = PI / 3 * i - PI / 2
        Offset(cx + r * cos(a).toFloat(), cy + r * sin(a).toFloat())
    }
    val soften = 0.22f
    for (i in 0 until 6) {
        val prev = corners[(i + 5) % 6]
        val corner = corners[i]
        val next = corners[(i + 1) % 6]
        val enter = Offset(corner.x + (prev.x - corner.x) * soften, corner.y + (prev.y - corner.y) * soften)
        val leave = Offset(corner.x + (next.x - corner.x) * soften, corner.y + (next.y - corner.y) * soften)
        if (i == 0) moveTo(enter.x, enter.y) else lineTo(enter.x, enter.y)
        quadraticTo(corner.x, corner.y, leave.x, leave.y)
    }
    close()
}

private val flower: Shape = GenericShape { size, _ ->
    // Eight shallow petals.
    val cx = size.width / 2f
    val cy = size.height / 2f
    val r = minOf(size.width, size.height) / 2f
    val steps = 144
    for (i in 0 until steps) {
        val t = 2 * PI * i / steps
        val radius = r * (0.93 + 0.07 * cos(8 * t)).toFloat()
        val x = cx + radius * cos(t).toFloat()
        val y = cy + radius * sin(t).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

private val rounded: Shape = RoundedCornerShape(percent = 24)

private val teardrop: Shape = AbsoluteRoundedCornerShape(
    topLeft = CornerSize(50),
    topRight = CornerSize(50),
    bottomRight = CornerSize(12),
    bottomLeft = CornerSize(50),
)

fun IconShape.toShape(): Shape = when (this) {
    IconShape.Circle -> CircleShape
    IconShape.Rounded -> rounded
    IconShape.Squircle -> squircle
    IconShape.Teardrop -> teardrop
    IconShape.Hexagon -> hexagon
    IconShape.Flower -> flower
}
