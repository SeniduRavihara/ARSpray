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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
// Material icon imports removed for standard lightweight text symbols
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.g37.arspray.ArObjectType

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
    isWhiteboardTransparent: Boolean,
    onWhiteboardTransparentToggle: (Boolean) -> Unit,
    whiteboardYaw: Float,
    onWhiteboardYawChange: (Float) -> Unit,
    whiteboardPitch: Float,
    onWhiteboardPitchChange: (Float) -> Unit,
    whiteboardRoll: Float,
    onWhiteboardRollChange: (Float) -> Unit,
    whiteboardDistance: Float,
    onWhiteboardDistanceChange: (Float) -> Unit,
    onAiRecognize: () -> Unit,
    selectedObjectType: ArObjectType,
    onObjectTypeChange: (ArObjectType) -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by rememberSaveable { mutableStateOf(true) }

    Column(
        modifier = modifier
            .padding(bottom = 24.dp)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isExpanded) {
            // Collapse toggle pill
            Button(
                onClick = { isExpanded = false },
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Black.copy(alpha = 0.5f),
                    contentColor = Color.White
                ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Text("Hide Controls ▼", style = MaterialTheme.typography.bodySmall)
            }
            
            Spacer(modifier = Modifier.height(12.dp))

            if (isSprayMode && !isWhiteboardMode) {
                BrushSizeCard(
                    brushSize = brushSize,
                    onBrushSizeChange = onBrushSizeChange
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (!isSprayMode && !isWhiteboardMode) {
                ObjectSelectorCard(
                    selectedType = selectedObjectType,
                    onTypeSelect = onObjectTypeChange
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (isWhiteboardMode) {
                WhiteboardSizeCard(
                    boardWidth = whiteboardWidth,
                    boardHeight = whiteboardHeight,
                    onWidthChange = onWhiteboardWidthChange,
                    onHeightChange = onWhiteboardHeightChange,
                    isTransparent = isWhiteboardTransparent,
                    onTransparentToggle = onWhiteboardTransparentToggle,
                    yaw = whiteboardYaw,
                    onYawChange = onWhiteboardYawChange,
                    pitch = whiteboardPitch,
                    onPitchChange = onWhiteboardPitchChange,
                    roll = whiteboardRoll,
                    onRollChange = onWhiteboardRollChange,
                    distance = whiteboardDistance,
                    onDistanceChange = onWhiteboardDistanceChange,
                    onAiRecognize = onAiRecognize
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onToggleMode,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSprayMode)
                                MaterialTheme.colorScheme.secondary
                            else
                                MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            text = if (isSprayMode) "Switch to Object" else "Switch to Spray",
                            maxLines = 1,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Button(
                        onClick = onToggleWhiteboardMode,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isWhiteboardMode)
                                Color(0xFF4CAF50) // Green when active
                            else
                                MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(
                            text = if (isWhiteboardMode) "Whiteboard: ON" else "Whiteboard: OFF",
                            maxLines = 1,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                Button(
                    onClick = onClearAll,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(
                        text = "Clear All",
                        maxLines = 1,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            // Collapsed trigger pill
            Button(
                onClick = { isExpanded = true },
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Black.copy(alpha = 0.7f),
                    contentColor = Color.White
                ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text("Show Controls ⚙ ▲", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun WhiteboardSizeCard(
    boardWidth: Float,
    boardHeight: Float,
    onWidthChange: (Float) -> Unit,
    onHeightChange: (Float) -> Unit,
    isTransparent: Boolean,
    onTransparentToggle: (Boolean) -> Unit,
    yaw: Float,
    onYawChange: (Float) -> Unit,
    pitch: Float,
    onPitchChange: (Float) -> Unit,
    roll: Float,
    onRollChange: (Float) -> Unit,
    distance: Float,
    onDistanceChange: (Float) -> Unit,
    onAiRecognize: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Whiteboard Configuration",
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
                text = "Transparent Background",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium
            )
            Switch(
                checked = isTransparent,
                onCheckedChange = onTransparentToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                )
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SliderRow(label = "Width", value = boardWidth, valueRange = 0.5f..3.0f, suffix = "m", onValueChange = onWidthChange)
            SliderRow(label = "Height", value = boardHeight, valueRange = 0.5f..3.0f, suffix = "m", onValueChange = onHeightChange)
            SliderRow(label = "Pitch", value = pitch, valueRange = -90f..90f, suffix = "°", onValueChange = onPitchChange)
            SliderRow(label = "Yaw", value = yaw, valueRange = -180f..180f, suffix = "°", onValueChange = onYawChange)
            SliderRow(label = "Roll", value = roll, valueRange = -180f..180f, suffix = "°", onValueChange = onRollChange)
            SliderRow(label = "Distance", value = distance, valueRange = -1.5f..1.5f, suffix = "m", onValueChange = onDistanceChange)
        }
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = onAiRecognize,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.Black
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("AI AutoDraw (Sketch to 3D)")
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    suffix: String,
    onValueChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label: %.1f$suffix".format(value),
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(100.dp)
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary
            )
        )
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

@Composable
private fun ObjectSelectorCard(
    selectedType: ArObjectType,
    onTypeSelect: (ArObjectType) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Select Object to Place",
            color = Color.White,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.align(Alignment.Start)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ArObjectType.values().forEach { type ->
                val isSelected = type == selectedType
                Button(
                    onClick = { onTypeSelect(type) },
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSelected)
                            MaterialTheme.colorScheme.primary
                        else
                            Color.White.copy(alpha = 0.15f),
                        contentColor = if (isSelected) Color.Black else Color.White
                    ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = type.displayName,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
