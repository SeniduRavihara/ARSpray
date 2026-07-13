// com/g37/arspray/ArSprayScreen.kt
package com.g37.arspray

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.material3.Slider
import com.google.android.filament.Texture
import java.nio.ByteBuffer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.g37.arspray.ar.ArSyncClient
import com.g37.arspray.ar.ArSyncNode
import com.g37.arspray.ar.configureArSession
import com.g37.arspray.ar.generateQrCode
import com.g37.arspray.ar.interpolateAndDraw
import com.g37.arspray.ui.CameraScanner
import com.g37.arspray.ui.ControlsPanel
import com.g37.arspray.ui.HostLobbyScreen
import com.g37.arspray.ui.JoinLobbyScreen
import com.g37.arspray.ui.LobbyScreen
import com.g37.arspray.ui.StatusOverlay
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import io.github.sceneview.ar.ARScene
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.arcore.createAnchorOrNull
import io.github.sceneview.ar.arcore.isValid
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.ar.node.CloudAnchorNode
import io.github.sceneview.ar.rememberARCameraNode
import com.google.ar.core.Anchor
import io.github.sceneview.ar.scene.PlaneRenderer
import io.github.sceneview.math.Position
import io.github.sceneview.node.ModelNode
import io.github.sceneview.node.SphereNode
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.LightNode
import com.google.android.filament.LightManager
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMainLightNode
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNodes
import io.github.sceneview.rememberOnGestureListener
import java.net.URI
import com.google.mlkit.vision.digitalink.recognition.Ink
import com.g37.arspray.ai.AutoDrawClassifier
import com.g37.arspray.model.DrawingStroke
import com.g37.arspray.model.ArAppMode
import com.g37.arspray.ui.WhiteboardDrawingOverlay
import com.g37.arspray.ar.spawnSyncNode

@Composable
fun ArSprayScreen() {
    var appMode by remember { mutableStateOf(ArAppMode.LOBBY) }
    var serverIp by remember { mutableStateOf("192.168.1.100") }
    var activeRoomId by remember { mutableStateOf<String?>(null) }

    when (appMode) {
        ArAppMode.LOBBY -> LobbyScreen(
            onSoloMode = { appMode = ArAppMode.SOLO },
            onHostMode = { appMode = ArAppMode.HOST_LOBBY },
            onJoinMode = { appMode = ArAppMode.JOIN_LOBBY }
        )
        ArAppMode.HOST_LOBBY -> HostLobbyScreen(
            serverIp = serverIp,
            onServerIpChange = { serverIp = it },
            onStartHosting = { appMode = ArAppMode.HOST_ACTIVE },
            onBack = { appMode = ArAppMode.LOBBY }
        )
        ArAppMode.JOIN_LOBBY -> JoinLobbyScreen(
            onScanQr = { appMode = ArAppMode.JOIN_SCANNING },
            onBack = { appMode = ArAppMode.LOBBY }
        )
        ArAppMode.JOIN_SCANNING -> CameraScanner(
            onBarcodeScanned = { barcodeText ->
                try {
                    val parts = barcodeText.split("|")
                    if (parts.size == 2) {
                        serverIp = parts[0]
                        activeRoomId = parts[1]
                        appMode = ArAppMode.JOIN_ACTIVE
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        )
        ArAppMode.SOLO, ArAppMode.HOST_ACTIVE, ArAppMode.JOIN_ACTIVE -> {
            ArActiveScreen(
                appMode = appMode,
                serverIp = serverIp,
                roomId = activeRoomId,
                onExit = {
                    appMode = ArAppMode.LOBBY
                    activeRoomId = null
                }
            )
        }
    }
}

@Composable
fun ArActiveScreen(
    appMode: ArAppMode,
    serverIp: String,
    roomId: String?,
    onExit: () -> Unit
) {
    val context = LocalContext.current

    // --- AR engine & scene resources ---
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)
    val cameraNode = rememberARCameraNode(engine)
    val childNodes = rememberNodes()

    // --- AR frame & model state ---
    var frame by remember { mutableStateOf<Frame?>(null) }
    var arSession by remember { mutableStateOf<Session?>(null) }
    var isModelLoading by remember { mutableStateOf(true) }
    var duckModel by remember { mutableStateOf<ModelNode?>(null) }
    var avocadoModel by remember { mutableStateOf<ModelNode?>(null) }
    var foxModel by remember { mutableStateOf<ModelNode?>(null) }
    var lanternModel by remember { mutableStateOf<ModelNode?>(null) }

    // --- Drawing state ---
    var isSprayMode by remember { mutableStateOf(true) }
    val sprayColor = Color.Magenta
    var brushSize by remember { mutableFloatStateOf(0.02f) }
    // Cache a single material instance for ALL spray spheres — creating one per sphere
    // was exhausting GPU memory and crashing the app within seconds of drawing.
    val sprayMaterialInstance = remember(materialLoader) {
        materialLoader.createColorInstance(Color.Magenta)
    }
    val displayMetrics = context.resources.displayMetrics

    // --- Whiteboard state ---
    var isWhiteboardMode by remember { mutableStateOf(false) }
    var whiteboardNode by remember { mutableStateOf<CubeNode?>(null) }
    var whiteboardAnchorNode by remember { mutableStateOf<AnchorNode?>(null) }
    var whiteboardWidth by remember { mutableFloatStateOf(1.2f) }
    var whiteboardHeight by remember { mutableFloatStateOf(0.9f) }
    var isWhiteboardTransparent by remember { mutableStateOf(false) }
    var whiteboardYaw by remember { mutableFloatStateOf(0f) }
    var whiteboardPitch by remember { mutableFloatStateOf(0f) }
    var whiteboardRoll by remember { mutableFloatStateOf(0f) }
    var whiteboardDistance by remember { mutableFloatStateOf(0f) }

    // --- Whiteboard drawing canvas and texture state ---
    var isEditCanvasOpen by remember { mutableStateOf(false) }
    var whiteboardBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var whiteboardTexture by remember { mutableStateOf<com.google.android.filament.Texture?>(null) }
    var whiteboardPaths by remember { mutableStateOf(listOf<DrawingStroke>()) }

    // Helper to upload Android Bitmap to Filament Texture
    val updateTextureFromBitmap = remember(engine) {
        { bmp: android.graphics.Bitmap, tex: com.google.android.filament.Texture ->
            val buffer = java.nio.ByteBuffer.allocateDirect(bmp.byteCount)
            bmp.copyPixelsToBuffer(buffer)
            buffer.flip()
            tex.setImage(
                engine,
                0,
                com.google.android.filament.Texture.PixelBufferDescriptor(
                    buffer,
                    com.google.android.filament.Texture.Format.RGBA,
                    com.google.android.filament.Texture.Type.UBYTE
                )
            )
            buffer.clear()
        }
    }

    // Helper to get or initialize whiteboard bitmap, texture, and texture-based material instance
    val getOrCreateWhiteboardMaterial = remember(materialLoader, engine, isWhiteboardTransparent) {
        {
            var bmp = whiteboardBitmap
            var tex = whiteboardTexture
            if (bmp == null || tex == null) {
                bmp = android.graphics.Bitmap.createBitmap(1024, 1024, android.graphics.Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bmp)
                if (isWhiteboardTransparent) {
                    canvas.drawColor(android.graphics.Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
                } else {
                    canvas.drawColor(android.graphics.Color.WHITE)
                }
                whiteboardBitmap = bmp

                tex = com.google.android.filament.Texture.Builder()
                    .width(1024)
                    .height(1024)
                    .levels(1)
                    .sampler(com.google.android.filament.Texture.Sampler.SAMPLER_2D)
                    .format(com.google.android.filament.Texture.InternalFormat.SRGB8_A8)
                    .build(engine)

                whiteboardTexture = tex
                updateTextureFromBitmap(bmp, tex)
            }
            materialLoader.createTextureInstance(tex, isOpaque = !isWhiteboardTransparent)
        }
    }

    // --- Gesture tracking state (for stroke interpolation) ---
    var lastTouchX by remember { mutableStateOf<Float?>(null) }
    var lastTouchY by remember { mutableStateOf<Float?>(null) }
    var lastTouchTime by remember { mutableLongStateOf(0L) }
    // Last drawn whiteboard-local position (for connecting line segments)
    var lastWbLocalX by remember { mutableStateOf<Float?>(null) }
    var lastWbLocalY by remember { mutableStateOf<Float?>(null) }

    // --- ML Kit Ink and Recognizer state ---
    var inkBuilder by remember { mutableStateOf(Ink.builder()) }
    var currentStrokeBuilder by remember { mutableStateOf<Ink.Stroke.Builder?>(null) }

    // --- Selected Object Mode Type ---
    var selectedObjectType by remember { mutableStateOf(ArObjectType.DUCK) }

    // Automatically update whiteboard dimensions, transparency, rotation, distance, and redraw paths
    LaunchedEffect(
        whiteboardWidth,
        whiteboardHeight,
        isWhiteboardTransparent,
        whiteboardYaw,
        whiteboardPitch,
        whiteboardRoll,
        whiteboardDistance,
        whiteboardNode,
        whiteboardPaths
    ) {
        val board = whiteboardNode ?: return@LaunchedEffect
        board.scale = io.github.sceneview.math.Scale(whiteboardWidth, whiteboardHeight, 0.02f)
        board.materialInstance = getOrCreateWhiteboardMaterial()
        
        val bmp = whiteboardBitmap
        val tex = whiteboardTexture
        if (bmp != null && tex != null) {
            val canvas = android.graphics.Canvas(bmp)
            if (isWhiteboardTransparent) {
                canvas.drawColor(android.graphics.Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
            } else {
                canvas.drawColor(android.graphics.Color.WHITE)
            }
            
            whiteboardPaths.forEach { stroke ->
                val paint = android.graphics.Paint().apply {
                    if (stroke.isEraser) {
                        if (isWhiteboardTransparent) {
                            color = android.graphics.Color.TRANSPARENT
                            xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR)
                        } else {
                            color = android.graphics.Color.WHITE
                        }
                    } else {
                        color = stroke.color.toArgb()
                    }
                    style = android.graphics.Paint.Style.STROKE
                    strokeWidth = stroke.width
                    strokeCap = android.graphics.Paint.Cap.ROUND
                    strokeJoin = android.graphics.Paint.Join.ROUND
                }
                val path = android.graphics.Path()
                if (stroke.points.isNotEmpty()) {
                    path.moveTo(stroke.points.first().x, stroke.points.first().y)
                    for (i in 1 until stroke.points.size) {
                        path.lineTo(stroke.points[i].x, stroke.points[i].y)
                    }
                    canvas.drawPath(path, paint)
                }
            }
            updateTextureFromBitmap(bmp, tex)
        }
        
        board.rotation = io.github.sceneview.math.Rotation(whiteboardPitch, whiteboardYaw, whiteboardRoll)
        board.position = Position(0f, 0f, whiteboardDistance)
    }

    // --- Shared Anchor & Sync State ---
    var sharedBaseNode by remember { mutableStateOf<CloudAnchorNode?>(null) }
    var activeRoomId by remember { mutableStateOf(roomId) }
    var isResolvingStarted by remember { mutableStateOf(false) }
    var connectionState by remember { mutableStateOf(false) }
    var syncStatusText by remember { mutableStateOf<String?>(null) }
    var showQrDialog by remember { mutableStateOf(false) }
    var syncClient by remember { mutableStateOf<ArSyncClient?>(null) }

    // Safely disconnect WebSocket when composable is destroyed
    DisposableEffect(syncClient) {
        onDispose {
            syncClient?.disconnect()
        }
    }

    // Free-spray drawing: ARCore hit-test with mid-air fallback.
    // Whiteboard drawing is handled inline in the onMove handler below (using Session batching).
    val drawPointAt = { x: Float, y: Float ->
        val currentFrame = frame
        val session = arSession
        if (currentFrame != null) {
            var hitResult = currentFrame
                .hitTest(x, y)
                .firstOrNull { hit ->
                    val trackable = hit.trackable
                    (trackable is Plane && trackable.trackingState == TrackingState.TRACKING) ||
                    hit.isValid(depthPoint = true, point = true, instantPlacementPoint = true)
                }
            if (hitResult == null) {
                hitResult = currentFrame.hitTest(x, y).firstOrNull()
            }

            var anchor = hitResult?.createAnchorOrNull()
            if (anchor == null && session != null) {
                val cameraPose = currentFrame.camera.displayOrientedPose
                val fallbackPose = cameraPose.compose(com.google.ar.core.Pose.makeTranslation(0f, 0f, -1.0f))
                anchor = session.createAnchor(fallbackPose)
            }

            anchor?.let { anchor ->
                val sphereNode = SphereNode(
                    engine = engine,
                    radius = brushSize,
                    materialInstance = sprayMaterialInstance
                )

                if (appMode == ArAppMode.SOLO) {
                    val anchorNode = AnchorNode(engine = engine, anchor = anchor)
                    anchorNode.addChildNode(sphereNode)
                    childNodes += anchorNode
                } else {
                    val baseNode = sharedBaseNode
                    if (baseNode != null) {
                        val tempAnchorNode = AnchorNode(engine = engine, anchor = anchor)
                        sphereNode.worldPosition = tempAnchorNode.worldPosition
                        baseNode.addChildNode(sphereNode)

                        val relativePos = sphereNode.position
                        val syncNode = ArSyncNode(
                            type = "sphere",
                            posX = relativePos.x,
                            posY = relativePos.y,
                            posZ = relativePos.z,
                            scale = brushSize,
                            colorHex = "#FF00FF"
                        )
                        syncClient?.sendNode(syncNode)
                    }
                }
            }
        }
    }

    // Add headlight to camera so that objects are always illuminated from the camera view direction
    LaunchedEffect(cameraNode) {
        try {
            val headlight = LightNode(
                engine = engine,
                type = LightManager.Type.DIRECTIONAL
            ) {
                intensity(150_000.0f)
            }
            cameraNode.addChildNode(headlight)
        } catch (e: Exception) {
            Toast.makeText(context, "Error setting up headlight: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Load the GLB models asynchronously
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
            val aModel = modelLoader.loadModel("models/avocado.glb")
            avocadoModel = if (aModel != null) {
                ModelNode(modelInstance = modelLoader.createInstance(aModel)!!)
            } else {
                Toast.makeText(context, "Model not found in assets/models/avocado.glb", Toast.LENGTH_LONG).show()
                null
            }
            val fModel = modelLoader.loadModel("models/fox.glb")
            foxModel = if (fModel != null) {
                ModelNode(modelInstance = modelLoader.createInstance(fModel)!!)
            } else {
                Toast.makeText(context, "Model not found in assets/models/fox.glb", Toast.LENGTH_LONG).show()
                null
            }
            val lModel = modelLoader.loadModel("models/lantern.glb")
            lanternModel = if (lModel != null) {
                ModelNode(modelInstance = modelLoader.createInstance(lModel)!!)
            } else {
                Toast.makeText(context, "Model not found in assets/models/lantern.glb", Toast.LENGTH_LONG).show()
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
            mainLightNode = rememberMainLightNode(engine) {
                intensity = 100_000.0f
            },
            onViewCreated = {
                planeRenderer.planeRendererMode = PlaneRenderer.PlaneRendererMode.RENDER_ALL
            },
            sessionConfiguration = { session: Session, config: Config ->
                configureArSession(session, config)
            },
            onSessionUpdated = { session, updatedFrame ->
                frame = updatedFrame
                arSession = session

                // Auto-resolve cloud anchor once guest session starts
                if (appMode == ArAppMode.JOIN_ACTIVE && sharedBaseNode == null && !isResolvingStarted && activeRoomId != null) {
                    isResolvingStarted = true
                    syncStatusText = "Resolving Shared Anchor..."
                    
                    CloudAnchorNode.resolve(engine, session, activeRoomId!!) { state, resolvedNode ->
                        if (state == Anchor.CloudAnchorState.SUCCESS && resolvedNode != null) {
                            sharedBaseNode = resolvedNode
                            childNodes += resolvedNode
                            syncStatusText = null
                            
                            val board = if (isWhiteboardMode) {
                                val matInstance = getOrCreateWhiteboardMaterial()
                                CubeNode(
                                    engine = engine,
                                    materialInstance = matInstance
                                ).apply {
                                    scale = io.github.sceneview.math.Scale(whiteboardWidth, whiteboardHeight, 0.02f)
                                    lookAt(cameraNode.worldPosition)
                                    val rot = rotation
                                    whiteboardYaw = rot.y
                                    whiteboardPitch = rot.x
                                    whiteboardRoll = rot.z
                                    position = Position(0f, 0f, whiteboardDistance)
                                }
                            } else null
                            
                            if (board != null) {
                                whiteboardNode = board
                                resolvedNode.addChildNode(board)
                            }
                            
                            // Initialize guest socket client
                            val uri = URI("ws://$serverIp:8080")
                            val client = ArSyncClient(
                                serverUri = uri,
                                roomId = activeRoomId!!,
                                onNodeReceived = { syncNode ->
                                    spawnSyncNode(
                                        engine = engine,
                                        materialLoader = materialLoader,
                                        modelLoader = modelLoader,
                                        syncNode = syncNode,
                                        parent = board ?: resolvedNode,
                                        sprayMaterialInstance = sprayMaterialInstance,
                                        sprayColor = sprayColor,
                                        duckModel = duckModel,
                                        avocadoModel = avocadoModel,
                                        foxModel = foxModel,
                                        lanternModel = lanternModel,
                                        whiteboardWidth = whiteboardWidth,
                                        whiteboardHeight = whiteboardHeight,
                                        whiteboardPaths = whiteboardPaths,
                                        onPathsChanged = { whiteboardPaths = it }
                                    )
                                },
                                onRoomCleared = {
                                    if (board != null) {
                                        board.clearChildNodes()
                                    } else {
                                        resolvedNode.clearChildNodes()
                                    }
                                    inkBuilder = Ink.builder()
                                    currentStrokeBuilder = null
                                },
                                onConnectionStateChanged = { connected ->
                                    connectionState = connected
                                }
                            )
                            syncClient = client
                            client.connect()
                        } else if (state.isError) {
                            syncStatusText = "Resolving failed: $state"
                            isResolvingStarted = false
                        }
                    }
                }
            },
            onGestureListener = rememberOnGestureListener(
                onSingleTapConfirmed = { motionEvent, tappedNode ->
                    if (isWhiteboardMode && whiteboardNode == null) {
                        // Place Whiteboard
                        val currentFrame = frame
                        val session = arSession
                        if (currentFrame != null) {
                            var hitResult = currentFrame
                                .hitTest(motionEvent.x, motionEvent.y)
                                .firstOrNull { hit ->
                                    val trackable = hit.trackable
                                    (trackable is Plane && trackable.trackingState == TrackingState.TRACKING) ||
                                    hit.isValid(depthPoint = true, point = true, instantPlacementPoint = true)
                                }
                            if (hitResult == null) {
                                hitResult = currentFrame.hitTest(motionEvent.x, motionEvent.y).firstOrNull()
                            }

                            var anchor = hitResult?.createAnchorOrNull()
                            if (anchor == null && session != null) {
                                val cameraPose = currentFrame.camera.displayOrientedPose
                                val fallbackPose = cameraPose.compose(com.google.ar.core.Pose.makeTranslation(0f, 0f, -1.0f))
                                anchor = session.createAnchor(fallbackPose)
                            }

                            anchor?.let { anchor ->
                                val matInstance = getOrCreateWhiteboardMaterial()
                                val board = CubeNode(
                                    engine = engine,
                                    materialInstance = matInstance
                                ).apply {
                                    scale = io.github.sceneview.math.Scale(whiteboardWidth, whiteboardHeight, 0.02f)
                                    lookAt(cameraNode.worldPosition)
                                    val rot = rotation
                                    rotation = io.github.sceneview.math.Rotation(0f, rot.y, 0f)
                                }
                                whiteboardNode = board

                                if (appMode == ArAppMode.SOLO) {
                                    val anchorNode = AnchorNode(engine = engine, anchor = anchor)
                                    whiteboardAnchorNode = anchorNode
                                    anchorNode.addChildNode(board)
                                    childNodes += anchorNode
                                } else if (appMode == ArAppMode.HOST_ACTIVE) {
                                    val cloudAnchor = CloudAnchorNode(engine = engine, anchor = anchor)
                                    whiteboardAnchorNode = cloudAnchor
                                    cloudAnchor.addChildNode(board)
                                    childNodes += cloudAnchor
                                    
                                    val session = arSession
                                    if (session != null) {
                                        syncStatusText = "Hosting Shared Anchor with Whiteboard..."
                                        cloudAnchor.host(session) { anchorId, state ->
                                            if (state == Anchor.CloudAnchorState.SUCCESS && anchorId != null) {
                                                activeRoomId = anchorId
                                                sharedBaseNode = cloudAnchor
                                                syncStatusText = null

                                                // Initialize host socket client
                                                val uri = URI("ws://$serverIp:8080")
                                                val client = ArSyncClient(
                                                    serverUri = uri,
                                                    roomId = anchorId,
                                                    onNodeReceived = { syncNode ->
                                                        spawnSyncNode(
                                                            engine = engine,
                                                            materialLoader = materialLoader,
                                                            modelLoader = modelLoader,
                                                            syncNode = syncNode,
                                                            parent = board,
                                                            sprayMaterialInstance = sprayMaterialInstance,
                                                            sprayColor = sprayColor,
                                                            duckModel = duckModel,
                                                            avocadoModel = avocadoModel,
                                                            foxModel = foxModel,
                                                            lanternModel = lanternModel,
                                                            whiteboardWidth = whiteboardWidth,
                                                            whiteboardHeight = whiteboardHeight,
                                                            whiteboardPaths = whiteboardPaths,
                                                            onPathsChanged = { whiteboardPaths = it }
                                                        )
                                                    },
                                                    onRoomCleared = {
                                                        board.clearChildNodes()
                                                        inkBuilder = Ink.builder()
                                                        currentStrokeBuilder = null
                                                    },
                                                    onConnectionStateChanged = { connected ->
                                                        connectionState = connected
                                                    }
                                                )
                                                syncClient = client
                                                client.connect()
                                            } else {
                                                syncStatusText = "Hosting failed: $state"
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else if (isWhiteboardMode && whiteboardNode != null && !isSprayMode) {
                        // Place a object on the whiteboard
                        val board = whiteboardNode
                        if (board == null) {
                            Toast.makeText(context, "Model not ready", Toast.LENGTH_SHORT).show()
                        } else {
                            val currentFrame = frame
                            if (currentFrame != null) {
                                // Ray-cast onto the whiteboard plane (no ARCore hit-test needed)
                                val displayMetrics = context.resources.displayMetrics
                                val worldHit = com.g37.arspray.ar.RayPlaneIntersector.intersect(
                                    frame = currentFrame,
                                    screenX = motionEvent.x,
                                    screenY = motionEvent.y,
                                    viewWidth = displayMetrics.widthPixels,
                                    viewHeight = displayMetrics.heightPixels,
                                    boardWorldPosition = board.worldPosition,
                                    boardWorldTransform = board.worldTransform
                                )

                                if (worldHit != null) {
                                    val localPos = (board.worldToLocal * dev.romainguy.kotlin.math.Float4(worldHit, 1f)).xyz
                                    val halfWidth = whiteboardWidth / 2f
                                    val halfHeight = whiteboardHeight / 2f

                                    if (localPos.x >= -halfWidth && localPos.x <= halfWidth &&
                                        localPos.y >= -halfHeight && localPos.y <= halfHeight
                                    ) {
                                        val spawnedNode = when (selectedObjectType) {
                                            ArObjectType.DUCK -> {
                                                val currentModel = duckModel
                                                val modelInstance = currentModel?.let { modelLoader.createInstance(it.modelInstance.asset) }
                                                if (modelInstance != null) {
                                                    ModelNode(modelInstance = modelInstance).apply {
                                                        scale = io.github.sceneview.math.Scale(0.5f)
                                                        position = Position(localPos.x, localPos.y, 0f)
                                                    }
                                                } else null
                                            }
                                            ArObjectType.AVOCADO -> {
                                                val currentModel = avocadoModel
                                                val modelInstance = currentModel?.let { modelLoader.createInstance(it.modelInstance.asset) }
                                                if (modelInstance != null) {
                                                    ModelNode(modelInstance = modelInstance).apply {
                                                        scale = io.github.sceneview.math.Scale(3.0f)
                                                        position = Position(localPos.x, localPos.y, 0f)
                                                    }
                                                } else null
                                            }
                                            ArObjectType.FOX -> {
                                                val currentModel = foxModel
                                                val modelInstance = currentModel?.let { modelLoader.createInstance(it.modelInstance.asset) }
                                                if (modelInstance != null) {
                                                    ModelNode(modelInstance = modelInstance).apply {
                                                        scale = io.github.sceneview.math.Scale(0.02f)
                                                        position = Position(localPos.x, localPos.y, 0f)
                                                    }
                                                } else null
                                            }
                                            ArObjectType.LANTERN -> {
                                                val currentModel = lanternModel
                                                val modelInstance = currentModel?.let { modelLoader.createInstance(it.modelInstance.asset) }
                                                if (modelInstance != null) {
                                                    ModelNode(modelInstance = modelInstance).apply {
                                                        scale = io.github.sceneview.math.Scale(0.5f)
                                                        position = Position(localPos.x, localPos.y, 0f)
                                                    }
                                                } else null
                                            }
                                            ArObjectType.CUBE -> {
                                                CubeNode(
                                                    engine = engine,
                                                    size = io.github.sceneview.math.Size(0.1f),
                                                    materialInstance = materialLoader.createColorInstance(Color.Red)
                                                ).apply {
                                                    position = Position(localPos.x, localPos.y, 0f)
                                                }
                                            }
                                            ArObjectType.SPHERE -> {
                                                SphereNode(
                                                    engine = engine,
                                                    radius = 0.05f,
                                                    materialInstance = materialLoader.createColorInstance(Color.Blue)
                                                ).apply {
                                                    position = Position(localPos.x, localPos.y, 0f)
                                                }
                                            }
                                        }

                                        if (spawnedNode != null) {
                                            board.addChildNode(spawnedNode)

                                            if (appMode != ArAppMode.SOLO) {
                                                val syncNode = ArSyncNode(
                                                    type = when (selectedObjectType) {
                                                        ArObjectType.DUCK -> "duck"
                                                        ArObjectType.AVOCADO -> "avocado"
                                                        ArObjectType.FOX -> "fox"
                                                        ArObjectType.LANTERN -> "lantern"
                                                        ArObjectType.CUBE -> "cube"
                                                        ArObjectType.SPHERE -> "sphere_object"
                                                    },
                                                    posX = localPos.x,
                                                    posY = localPos.y,
                                                    posZ = 0f,
                                                    scale = 0.5f,
                                                    colorHex = ""
                                                )
                                                syncClient?.sendNode(syncNode)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else if (appMode == ArAppMode.HOST_ACTIVE && sharedBaseNode == null) {
                        val currentFrame = frame
                        val session = arSession
                        if (currentFrame != null && session != null) {
                            val hitResult = currentFrame
                                .hitTest(motionEvent.x, motionEvent.y)
                                .firstOrNull { hit ->
                                    val trackable = hit.trackable
                                    (trackable is Plane && trackable.trackingState == TrackingState.TRACKING) ||
                                    hit.isValid(depthPoint = true, point = true, instantPlacementPoint = true)
                                }

                            hitResult?.createAnchorOrNull()?.let { anchor ->
                                val cloudAnchor = CloudAnchorNode(engine, anchor)
                                childNodes += cloudAnchor
                                syncStatusText = "Hosting Shared Anchor..."

                                cloudAnchor.host(session) { anchorId, state ->
                                    if (state == Anchor.CloudAnchorState.SUCCESS && anchorId != null) {
                                        activeRoomId = anchorId
                                        sharedBaseNode = cloudAnchor
                                        syncStatusText = null

                                        // Initialize host socket client
                                        val uri = URI("ws://$serverIp:8080")
                                        val client = ArSyncClient(
                                            serverUri = uri,
                                            roomId = anchorId,
                                            onNodeReceived = { syncNode ->
                                                spawnSyncNode(
                                                    engine = engine,
                                                    materialLoader = materialLoader,
                                                    modelLoader = modelLoader,
                                                    syncNode = syncNode,
                                                    parent = sharedBaseNode ?: cloudAnchor,
                                                    sprayMaterialInstance = sprayMaterialInstance,
                                                    sprayColor = sprayColor,
                                                    duckModel = duckModel,
                                                    avocadoModel = avocadoModel,
                                                    foxModel = foxModel,
                                                    lanternModel = lanternModel,
                                                    whiteboardWidth = whiteboardWidth,
                                                    whiteboardHeight = whiteboardHeight,
                                                    whiteboardPaths = whiteboardPaths,
                                                    onPathsChanged = { whiteboardPaths = it }
                                                )
                                            },
                                            onRoomCleared = {
                                                sharedBaseNode?.clearChildNodes()
                                            },
                                            onConnectionStateChanged = { connected ->
                                                connectionState = connected
                                            }
                                        )
                                        syncClient = client
                                        client.connect()
                                    } else {
                                        syncStatusText = "Hosting failed: $state"
                                    }
                                }
                            }
                        }
                    } else {
                        // Regular object placement mode
                        if (!isSprayMode) {
                            val currentFrame = frame
                            if (currentFrame != null) {
                                // Strictly require tracked planes in Object Mode
                                val hitResult = currentFrame
                                    .hitTest(motionEvent.x, motionEvent.y)
                                    .firstOrNull { hit ->
                                        val trackable = hit.trackable
                                        trackable is Plane && trackable.trackingState == TrackingState.TRACKING
                                    }

                                hitResult?.createAnchorOrNull()?.let { anchor ->
                                    val targetNode = when (selectedObjectType) {
                                        ArObjectType.DUCK -> {
                                            val currentModel = duckModel
                                            if (currentModel != null) {
                                                val modelInstance = modelLoader.createInstance(currentModel.modelInstance.asset)
                                                if (modelInstance != null) {
                                                    ModelNode(modelInstance = modelInstance).apply {
                                                        scale = io.github.sceneview.math.Scale(0.5f)
                                                    }
                                                } else null
                                            } else null
                                        }
                                        ArObjectType.AVOCADO -> {
                                            val currentModel = avocadoModel
                                            if (currentModel != null) {
                                                val modelInstance = modelLoader.createInstance(currentModel.modelInstance.asset)
                                                if (modelInstance != null) {
                                                    ModelNode(modelInstance = modelInstance).apply {
                                                        scale = io.github.sceneview.math.Scale(3.0f)
                                                    }
                                                } else null
                                            } else null
                                        }
                                        ArObjectType.FOX -> {
                                            val currentModel = foxModel
                                            if (currentModel != null) {
                                                val modelInstance = modelLoader.createInstance(currentModel.modelInstance.asset)
                                                if (modelInstance != null) {
                                                    ModelNode(modelInstance = modelInstance).apply {
                                                        scale = io.github.sceneview.math.Scale(0.02f)
                                                    }
                                                } else null
                                            } else null
                                        }
                                        ArObjectType.LANTERN -> {
                                            val currentModel = lanternModel
                                            if (currentModel != null) {
                                                val modelInstance = modelLoader.createInstance(currentModel.modelInstance.asset)
                                                if (modelInstance != null) {
                                                    ModelNode(modelInstance = modelInstance).apply {
                                                        scale = io.github.sceneview.math.Scale(0.5f)
                                                    }
                                                } else null
                                            } else null
                                        }
                                        ArObjectType.CUBE -> {
                                            CubeNode(
                                                engine = engine,
                                                size = io.github.sceneview.math.Size(0.1f),
                                                materialInstance = materialLoader.createColorInstance(Color.Red)
                                            )
                                        }
                                        ArObjectType.SPHERE -> {
                                            SphereNode(
                                                engine = engine,
                                                radius = 0.05f,
                                                materialInstance = materialLoader.createColorInstance(Color.Blue)
                                            )
                                        }
                                    }

                                    if (targetNode != null) {
                                        if (appMode == ArAppMode.SOLO) {
                                            val anchorNode = AnchorNode(engine = engine, anchor = anchor)
                                            anchorNode.addChildNode(targetNode)
                                            childNodes += anchorNode
                                        } else {
                                            val baseNode = sharedBaseNode
                                            if (baseNode != null) {
                                                val tempAnchorNode = AnchorNode(engine = engine, anchor = anchor)
                                                targetNode.worldPosition = tempAnchorNode.worldPosition
                                                baseNode.addChildNode(targetNode)

                                                // Send placements over socket
                                                val relativePos = targetNode.position
                                                val syncNode = ArSyncNode(
                                                    type = when (selectedObjectType) {
                                                        ArObjectType.DUCK -> "duck"
                                                        ArObjectType.AVOCADO -> "avocado"
                                                        ArObjectType.FOX -> "fox"
                                                        ArObjectType.LANTERN -> "lantern"
                                                        ArObjectType.CUBE -> "cube"
                                                        ArObjectType.SPHERE -> "sphere_object"
                                                    },
                                                    posX = relativePos.x,
                                                    posY = relativePos.y,
                                                    posZ = relativePos.z,
                                                    scale = 0.5f,
                                                    colorHex = ""
                                                )
                                                syncClient?.sendNode(syncNode)
                                            }
                                        }
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

                        if (isWhiteboardMode) {
                            // In whiteboard mode, drawing is handled by the 2D canvas editor overlay
                        } else {
                            // ── Free-spray mode (no whiteboard) ──
                            interpolateAndDraw(
                                startX = lastTouchX,
                                startY = lastTouchY,
                                endX = endX,
                                endY = endY,
                                timeDelta = timeDelta,
                                onDrawPoint = drawPointAt
                            )
                        }

                        lastTouchX = endX
                        lastTouchY = endY
                    }
                }
            )
        )

        // State indicator & top overlay UI
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Socket / Session Info Row
            if (appMode != ArAppMode.SOLO) {
                Row(
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                color = if (connectionState) Color.Green else Color.Red,
                                shape = CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (connectionState) "Connected to Host Server" else "Offline from Server",
                        color = Color.White,
                        fontSize = 12.sp
                    )

                    if (appMode == ArAppMode.HOST_ACTIVE && activeRoomId != null) {
                        Spacer(modifier = Modifier.width(16.dp))
                        Button(
                            onClick = { showQrDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = sprayColor),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("Show QR", fontSize = 10.sp, color = Color.White)
                        }
                    }
                }
            }

            // Sync Process overlay message
            syncStatusText?.let {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = it, fontSize = 13.sp)
                    }
                }
            }

            StatusOverlay(
                isSprayMode = isSprayMode,
                selectedObjectType = selectedObjectType,
                isWhiteboardMode = isWhiteboardMode,
                modifier = Modifier
            )
        }

        if (isModelLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary
            )
        }

        val onAiRecognize = {
            currentStrokeBuilder?.let {
                inkBuilder.addStroke(it.build())
                currentStrokeBuilder = null
            }

            val ink = inkBuilder.build()
            val classifier = AutoDrawClassifier(
                context = context,
                onStateChanged = { syncStatusText = it },
                onClearDrawing = {
                    if (appMode == ArAppMode.SOLO) {
                        whiteboardNode?.clearChildNodes()
                    } else {
                        syncClient?.sendClear()
                    }
                    inkBuilder = Ink.builder()
                    whiteboardPaths = emptyList()
                },
                onSuccess = { centerX, centerY ->
                    val currentModel = duckModel
                    val board = whiteboardNode
                    if (currentModel != null && board != null) {
                        val modelInstance = modelLoader.createInstance(currentModel.modelInstance.asset)
                        if (modelInstance != null) {
                            val modelNode = ModelNode(modelInstance = modelInstance).apply {
                                scale = io.github.sceneview.math.Scale(0.5f)
                                position = Position(centerX, centerY, 0f)
                            }
                            board.addChildNode(modelNode)

                            if (appMode != ArAppMode.SOLO) {
                                val syncNode = ArSyncNode(
                                    type = "duck",
                                    posX = centerX,
                                    posY = centerY,
                                    posZ = 0f,
                                    scale = 0.5f,
                                    colorHex = ""
                                )
                                syncClient?.sendNode(syncNode)
                            }
                        }
                    }
                }
            )
            classifier.recognize(ink)
            Unit
        }

        // Bottom Controls
        Column(
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            ControlsPanel(
                isSprayMode = isSprayMode,
                brushSize = brushSize,
                onBrushSizeChange = { brushSize = it },
                onToggleMode = { isSprayMode = !isSprayMode },
                onClearAll = {
                    if (appMode == ArAppMode.SOLO) {
                        if (isWhiteboardMode) {
                            whiteboardNode?.clearChildNodes()
                            inkBuilder = Ink.builder()
                            currentStrokeBuilder = null
                            whiteboardPaths = emptyList()
                        } else {
                            childNodes.clear()
                        }
                    } else {
                        syncClient?.sendClear()
                    }
                },
                isWhiteboardMode = isWhiteboardMode,
                onToggleWhiteboardMode = {
                    isWhiteboardMode = !isWhiteboardMode
                    if (!isWhiteboardMode) {
                        // Remove whiteboard from scene if disabled
                        whiteboardAnchorNode?.let { childNodes -= it }
                        whiteboardNode = null
                        whiteboardAnchorNode = null
                        inkBuilder = Ink.builder()
                        currentStrokeBuilder = null
                    }
                },
                whiteboardWidth = whiteboardWidth,
                onWhiteboardWidthChange = { whiteboardWidth = it },
                whiteboardHeight = whiteboardHeight,
                onWhiteboardHeightChange = { whiteboardHeight = it },
                isWhiteboardTransparent = isWhiteboardTransparent,
                onWhiteboardTransparentToggle = { isWhiteboardTransparent = it },
                whiteboardYaw = whiteboardYaw,
                onWhiteboardYawChange = { whiteboardYaw = it },
                whiteboardPitch = whiteboardPitch,
                onWhiteboardPitchChange = { whiteboardPitch = it },
                whiteboardRoll = whiteboardRoll,
                onWhiteboardRollChange = { whiteboardRoll = it },
                whiteboardDistance = whiteboardDistance,
                onWhiteboardDistanceChange = { whiteboardDistance = it },
                onAiRecognize = onAiRecognize,
                selectedObjectType = selectedObjectType,
                onObjectTypeChange = { selectedObjectType = it }
            )
            
            Button(
                onClick = onExit,
                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 16.dp)
            ) {
                Text("Exit Session")
            }
        }

        // Host QR Code Dialog overlay
        if (showQrDialog && activeRoomId != null) {
            val qrBitmap = remember(serverIp, activeRoomId) {
                generateQrCode("$serverIp|$activeRoomId")
            }
            if (qrBitmap != null) {
                Dialog(onDismissRequest = { showQrDialog = false }) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1C2C)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Scan QR Code to Join", color = Color.White, style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(16.dp))
                            Image(
                                bitmap = qrBitmap.asImageBitmap(),
                                contentDescription = "Room QR Code",
                                modifier = Modifier.size(220.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Room ID: $activeRoomId", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp)
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { showQrDialog = false },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                            ) {
                                Text("Close")
                            }
                        }
                    }
                }
            }
        }

        // Floating "Edit Board" button when in Whiteboard Mode and a board is placed
        if (isWhiteboardMode && whiteboardNode != null && !isEditCanvasOpen) {
            Button(
                onClick = { isEditCanvasOpen = true },
                colors = ButtonDefaults.buttonColors(containerColor = sprayColor),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 24.dp)
            ) {
                Text("Edit Board", color = Color.White)
            }
        }

        // Full-screen Whiteboard Drawing Canvas Editor Overlay
        WhiteboardDrawingOverlay(
            isEditCanvasOpen = isEditCanvasOpen,
            isWhiteboardTransparent = isWhiteboardTransparent,
            whiteboardWidth = whiteboardWidth,
            whiteboardHeight = whiteboardHeight,
            sprayColor = sprayColor,
            appMode = appMode,
            syncClient = syncClient,
            whiteboardPaths = whiteboardPaths,
            onPathsChanged = { whiteboardPaths = it },
            onClearAll = {
                whiteboardPaths = emptyList()
                val bmp = whiteboardBitmap
                if (bmp != null) {
                    val canvas = android.graphics.Canvas(bmp)
                    if (isWhiteboardTransparent) {
                        canvas.drawColor(android.graphics.Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
                    } else {
                        canvas.drawColor(android.graphics.Color.WHITE)
                    }
                }
                val tex = whiteboardTexture
                if (bmp != null && tex != null) {
                    updateTextureFromBitmap(bmp, tex)
                }
                if (appMode != ArAppMode.SOLO) {
                    syncClient?.sendClear()
                }
            },
            onClose = { isEditCanvasOpen = false }
        )

        // Version Indicator Overlay
        Text(
            text = "v1.2.0",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 11.sp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 16.dp)
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
