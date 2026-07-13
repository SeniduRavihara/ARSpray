package com.g37.arspray.model

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset

data class DrawingStroke(
    val points: List<Offset>,
    val color: Color,
    val width: Float,
    val isEraser: Boolean = false
)
