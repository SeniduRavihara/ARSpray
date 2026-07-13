package com.g37.arspray.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.g37.arspray.model.ArAppMode
import com.g37.arspray.model.DrawingStroke
import com.g37.arspray.ar.ArSyncClient
import com.g37.arspray.ar.ArSyncNode

@Composable
fun WhiteboardDrawingOverlay(
    isEditCanvasOpen: Boolean,
    isWhiteboardTransparent: Boolean,
    whiteboardWidth: Float,
    whiteboardHeight: Float,
    sprayColor: Color,
    appMode: ArAppMode,
    syncClient: ArSyncClient?,
    whiteboardPaths: List<DrawingStroke>,
    onPathsChanged: (List<DrawingStroke>) -> Unit,
    onClearAll: () -> Unit,
    onClose: () -> Unit
) {
    if (!isEditCanvasOpen) return

    var currentBrushColor by remember { mutableStateOf(Color.Black) }
    var currentBrushWidth by remember { mutableFloatStateOf(10f) }
    var isEraserActive by remember { mutableStateOf(false) }

    // Maintain a local state for the active drawing session to support cancel/discard
    var localPaths by remember(isEditCanvasOpen) { mutableStateOf(whiteboardPaths) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header Bar Controls
            // Header Bar Controls (Top Bar: Title + Cancel + Save & Exit)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E1C2C))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Drawing Board", color = Color.White, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.weight(1f))

                // Cancel Button
                Button(
                    onClick = {
                        if (appMode != ArAppMode.SOLO) {
                            syncClient?.sendClear()
                            // Re-sync the original paths to all synchronized clients
                            whiteboardPaths.forEach { stroke ->
                                if (stroke.points.isNotEmpty()) {
                                    val first = stroke.points.first()
                                    val halfW = whiteboardWidth / 2f
                                    val halfH = whiteboardHeight / 2f
                                    val lx = (first.x / 1024f) * whiteboardWidth - halfW
                                    val ly = halfH - (first.y / 1024f) * whiteboardHeight
                                    val hex = String.format("#%06X", 0xFFFFFF and stroke.color.toArgb())
                                    syncClient?.sendNode(
                                        ArSyncNode(
                                            type = "line",
                                            posX = lx,
                                            posY = ly,
                                            posZ = if (stroke.isEraser) -1f else 1f,
                                            scale = stroke.width,
                                            colorHex = hex
                                        )
                                    )
                                    for (i in 1 until stroke.points.size) {
                                        val pt = stroke.points[i]
                                        val nlx = (pt.x / 1024f) * whiteboardWidth - halfW
                                        val nly = halfH - (pt.y / 1024f) * whiteboardHeight
                                        syncClient?.sendNode(
                                            ArSyncNode(
                                                type = "line",
                                                posX = nlx,
                                                posY = nly,
                                                posZ = if (stroke.isEraser) -2f else 0f,
                                                scale = stroke.width,
                                                colorHex = hex
                                            )
                                        )
                                    }
                                }
                            }
                        }
                        onClose()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text("Cancel", fontSize = 10.sp, color = Color.White)
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Save Button
                Button(
                    onClick = {
                        if (localPaths.isEmpty() && whiteboardPaths.isNotEmpty()) {
                            onClearAll()
                        } else {
                            onPathsChanged(localPaths)
                        }
                        onClose()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = sprayColor),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text("Save & Exit", fontSize = 10.sp, color = Color.White)
                }
            }

            // Toolbar Controls (Color Pickers + Eraser + Clear)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF252335))
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Color pickers
                listOf(Color.Black, Color.Red, Color.Blue, Color.Green, Color.Magenta).forEach { color ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(24.dp)
                            .background(color, CircleShape)
                            .background(
                                color = if (currentBrushColor == color && !isEraserActive) Color.White.copy(alpha = 0.4f) else Color.Transparent,
                                shape = CircleShape
                            )
                            .clickable {
                                currentBrushColor = color
                                isEraserActive = false
                            }
                    )
                }
                
                Spacer(modifier = Modifier.weight(1f))

                // Eraser Toggle
                Button(
                    onClick = { isEraserActive = !isEraserActive },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isEraserActive) Color.Red else Color.Gray
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text(if (isEraserActive) "Eraser: ON" else "Eraser", fontSize = 10.sp, color = Color.White)
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Clear Button
                Button(
                    onClick = {
                        localPaths = emptyList()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text("Clear", fontSize = 10.sp, color = Color.White)
                }
            }

            // Brush Thickness Slider
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF2E2C3C))
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Size: ${currentBrushWidth.toInt()}", color = Color.White, fontSize = 12.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Slider(
                    value = currentBrushWidth,
                    onValueChange = { currentBrushWidth = it },
                    valueRange = 2f..50f,
                    modifier = Modifier.weight(1f)
                )
            }

            // The actual drawing canvas (Real-time zero-slop gesture detector)
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            // Wait for the first down event (equivalent to touch start)
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val w = size.width.toFloat()
                            val h = size.height.toFloat()
                            val px = if (w > 0f) (down.position.x / w) * 1024f else down.position.x
                            val py = if (h > 0f) (down.position.y / h) * 1024f else down.position.y
                            val startPt = Offset(px, py)

                            val stroke = DrawingStroke(
                                points = listOf(startPt),
                                color = currentBrushColor,
                                width = currentBrushWidth,
                                isEraser = isEraserActive
                            )
                            localPaths = localPaths + stroke

                            if (appMode != ArAppMode.SOLO) {
                                val halfW = whiteboardWidth / 2f
                                val halfH = whiteboardHeight / 2f
                                val lx = (px / 1024f) * whiteboardWidth - halfW
                                val ly = halfH - (py / 1024f) * whiteboardHeight
                                val hex = String.format("#%06X", 0xFFFFFF and currentBrushColor.toArgb())
                                syncClient?.sendNode(
                                    ArSyncNode(
                                        type = "line",
                                        posX = lx,
                                        posY = ly,
                                        posZ = if (isEraserActive) -1f else 1f,
                                        scale = currentBrushWidth,
                                        colorHex = hex
                                    )
                                )
                            }

                            // Wait for subsequent touch move events
                            while (true) {
                                val event = awaitPointerEvent()
                                val anyDown = event.changes.any { it.pressed }
                                if (!anyDown) {
                                    break // Gesture finished
                                }

                                val change = event.changes.firstOrNull()
                                if (change != null) {
                                    change.consume()
                                    val touchOffset = change.position
                                    val npx = if (w > 0f) (touchOffset.x / w) * 1024f else touchOffset.x
                                    val npy = if (h > 0f) (touchOffset.y / h) * 1024f else touchOffset.y
                                    val dragPt = Offset(npx, npy)

                                    val last = localPaths.lastOrNull()
                                    if (last != null) {
                                        val updated = last.copy(points = last.points + dragPt)
                                        localPaths = localPaths.dropLast(1) + updated
                                    }

                                    if (appMode != ArAppMode.SOLO) {
                                        val halfW = whiteboardWidth / 2f
                                        val halfH = whiteboardHeight / 2f
                                        val lx = (npx / 1024f) * whiteboardWidth - halfW
                                        val ly = halfH - (npy / 1024f) * whiteboardHeight
                                        val hex = String.format("#%06X", 0xFFFFFF and currentBrushColor.toArgb())
                                        syncClient?.sendNode(
                                            ArSyncNode(
                                                type = "line",
                                                posX = lx,
                                                posY = ly,
                                                posZ = if (isEraserActive) -2f else 0f,
                                                scale = currentBrushWidth,
                                                colorHex = hex
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
            ) {
                // Render paths on Compose canvas
                localPaths.forEach { stroke ->
                    val path = Path()
                    if (stroke.points.isNotEmpty()) {
                        val first = stroke.points.first()
                        path.moveTo(
                            (first.x / 1024f) * size.width,
                            (first.y / 1024f) * size.height
                        )
                        for (i in 1 until stroke.points.size) {
                            val pt = stroke.points[i]
                            path.lineTo(
                                (pt.x / 1024f) * size.width,
                                (pt.y / 1024f) * size.height
                            )
                        }
                        drawPath(
                            path = path,
                            color = if (stroke.isEraser) Color.LightGray else stroke.color,
                            style = Stroke(
                                width = (stroke.width / 1024f) * size.width,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                }
            }
        }
    }
}
