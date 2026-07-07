// com/g37/arspray/ArSprayScreen.kt
package com.g37.arspray

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.g37.arspray.ar.configureArSession
import com.g37.arspray.ar.interpolateAndDraw
import com.g37.arspray.ui.ControlsPanel
import com.g37.arspray.ui.StatusOverlay
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Session
import io.github.sceneview.ar.ARScene
import io.github.sceneview.ar.arcore.createAnchorOrNull
import io.github.sceneview.ar.arcore.isValid
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.ar.rememberARCameraNode
import io.github.sceneview.node.ModelNode
import io.github.sceneview.node.SphereNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNodes
import io.github.sceneview.rememberOnGestureListener

/**
 * Main AR screen that assembles the full AR scene and all UI overlays.
 *
 * Responsibilities:
 * - Owns all AR/Compose state (engine, loaders, frame, mode flags, brush size).
 * - Connects [ARScene] to the gesture listener.
 * - Delegates UI to [StatusOverlay] and [ControlsPanel].
 * - Delegates session configuration to [configureArSession].
 * - Delegates stroke interpolation to [interpolateAndDraw].
 */
@Composable
fun ArSprayScreen() {
    val context = LocalContext.current

    // --- AR engine & scene resources ---
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)
    val cameraNode = rememberARCameraNode(engine)
    val childNodes = rememberNodes()

    // --- AR frame & model state ---
    var frame by remember { mutableStateOf<Frame?>(null) }
    var isModelLoading by remember { mutableStateOf(true) }
    var duckModel by remember { mutableStateOf<ModelNode?>(null) }

    // --- Drawing state ---
    var isSprayMode by remember { mutableStateOf(true) }
    val sprayColor = Color.Magenta
    var brushSize by remember { mutableFloatStateOf(0.02f) }

    // --- Gesture tracking state (for stroke interpolation) ---
    var lastTouchX by remember { mutableStateOf<Float?>(null) }
    var lastTouchY by remember { mutableStateOf<Float?>(null) }
    var lastTouchTime by remember { mutableLongStateOf(0L) }

    // Performs a hit-test at the given screen coordinate and spawns a spray sphere
    val drawPointAt = { x: Float, y: Float ->
        val currentFrame = frame
        if (currentFrame != null) {
            val hitResult = currentFrame
                .hitTest(x, y)
                .firstOrNull { it.isValid(depthPoint = true, point = true) }

            hitResult?.createAnchorOrNull()?.let { anchor ->
                val anchorNode = AnchorNode(engine = engine, anchor = anchor)
                val sphereNode = SphereNode(
                    engine = engine,
                    radius = brushSize,
                    materialInstance = materialLoader.createColorInstance(sprayColor)
                )
                anchorNode.addChildNode(sphereNode)
                childNodes += anchorNode
            }
        }
    }

    // Load the duck GLB model asynchronously
    LaunchedEffect(modelLoader) {
        isModelLoading = true
        try {
            val model = modelLoader.loadModel("models/duck.glb")
            duckModel = if (model != null) {
                ModelNode(modelInstance = modelLoader.createInstance(model)!!)
            } else {
                Toast.makeText(context, "Model not found in assets/models/duck.glb", Toast.LENGTH_LONG).show()
                null
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Error loading model: ${e.message}", Toast.LENGTH_LONG).show()
        } finally {
            isModelLoading = false
        }
    }

    // --- Layout ---
    Box(modifier = Modifier.fillMaxSize()) {

        ARScene(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            cameraNode = cameraNode,
            childNodes = childNodes,
            planeRenderer = true,
            sessionConfiguration = { session: Session, config: Config ->
                configureArSession(session, config)
            },
            onSessionUpdated = { _, updatedFrame ->
                frame = updatedFrame
            },
            onGestureListener = rememberOnGestureListener(
                onSingleTapConfirmed = { motionEvent, _ ->
                    if (!isSprayMode) {
                        val currentModel = duckModel
                        if (currentModel == null) {
                            Toast.makeText(context, "Model not ready", Toast.LENGTH_SHORT).show()
                        } else {
                            val currentFrame = frame
                            if (currentFrame != null) {
                                val hitResult = currentFrame
                                    .hitTest(motionEvent.x, motionEvent.y)
                                    .firstOrNull { it.isValid(depthPoint = true, point = true) }

                                hitResult?.createAnchorOrNull()?.let { anchor ->
                                    val modelInstance =
                                        modelLoader.createInstance(currentModel.modelInstance.asset)
                                    if (modelInstance != null) {
                                        val anchorNode = AnchorNode(engine = engine, anchor = anchor)
                                        val modelNode = ModelNode(modelInstance = modelInstance).apply {
                                            scale = io.github.sceneview.math.Scale(0.5f)
                                        }
                                        anchorNode.addChildNode(modelNode)
                                        childNodes += anchorNode
                                    }
                                }
                            }
                        }
                    }
                },
                onMove = { _, motionEvent, _ ->
                    if (isSprayMode) {
                        val now = System.currentTimeMillis()
                        val timeDelta = now - lastTouchTime
                        lastTouchTime = now

                        val endX = motionEvent.x
                        val endY = motionEvent.y

                        interpolateAndDraw(
                            startX = lastTouchX,
                            startY = lastTouchY,
                            endX = endX,
                            endY = endY,
                            timeDelta = timeDelta,
                            onDrawPoint = drawPointAt
                        )

                        lastTouchX = endX
                        lastTouchY = endY
                    }
                }
            )
        )

        StatusOverlay(
            isSprayMode = isSprayMode,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        if (isModelLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary
            )
        }

        ControlsPanel(
            isSprayMode = isSprayMode,
            brushSize = brushSize,
            onBrushSizeChange = { brushSize = it },
            onToggleMode = { isSprayMode = !isSprayMode },
            onClearAll = { childNodes.clear() },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
