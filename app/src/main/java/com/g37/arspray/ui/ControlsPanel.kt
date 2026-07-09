// com/g37/arspray/ui/ControlsPanel.kt
package com.g37.arspray.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Bottom controls panel containing:
 * - Brush size slider (only visible in spray mode)
 * - Mode toggle button
 * - Clear all button
 */
@Composable
fun ControlsPanel(
    isSprayMode: Boolean,
    brushSize: Float,
    onBrushSizeChange: (Float) -> Unit,
    onToggleMode: () -> Unit,
    onClearAll: () -> Unit,
    isWhiteboardMode: Boolean,
    onToggleWhiteboardMode: () -> Unit,
    whiteboardWidth: Float,
    onWhiteboardWidthChange: (Float) -> Unit,
    whiteboardHeight: Float,
    onWhiteboardHeightChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(bottom = 48.dp)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isSprayMode && !isWhiteboardMode) {
            BrushSizeCard(
                brushSize = brushSize,
                onBrushSizeChange = onBrushSizeChange
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (isWhiteboardMode) {
            WhiteboardSizeCard(
                boardWidth = whiteboardWidth,
                boardHeight = whiteboardHeight,
                onWidthChange = onWhiteboardWidthChange,
                onHeightChange = onWhiteboardHeightChange
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = onToggleMode,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSprayMode)
                        MaterialTheme.colorScheme.secondary
                    else
                        MaterialTheme.colorScheme.primary
                )
            ) {
                Text(if (isSprayMode) "Switch to Duck" else "Switch to Spray")
            }

            Button(
                onClick = onToggleWhiteboardMode,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isWhiteboardMode)
                        Color(0xFF4CAF50) // Green when active
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Text(if (isWhiteboardMode) "Whiteboard: ON" else "Whiteboard: OFF")
            }

            Button(
                onClick = onClearAll,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Clear All")
            }
        }
    }
}

@Composable
private fun WhiteboardSizeCard(
    boardWidth: Float,
    boardHeight: Float,
    onWidthChange: (Float) -> Unit,
    onHeightChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Whiteboard Dimensions",
            color = Color.White,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.align(Alignment.Start)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Width: %.1f m".format(boardWidth),
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.width(90.dp)
            )
            Slider(
                value = boardWidth,
                onValueChange = onWidthChange,
                valueRange = 0.5f..3.0f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary
                )
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Height: %.1f m".format(boardHeight),
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.width(90.dp)
            )
            Slider(
                value = boardHeight,
                onValueChange = onHeightChange,
                valueRange = 0.5f..2.5f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

/**
 * Semi-transparent card with a brush size label and a slider.
 */
@Composable
private fun BrushSizeCard(
    brushSize: Float,
    onBrushSizeChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Brush Size",
                color = Color.White,
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = "%.1f cm".format(brushSize * 100),
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Slider(
            value = brushSize,
            onValueChange = onBrushSizeChange,
            valueRange = 0.005f..0.08f,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
            )
        )
    }
}
