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
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNodes
import io.github.sceneview.rememberOnGestureListener
import java.net.URI

enum class ArAppMode {
    LOBBY,
    SOLO,
    HOST_LOBBY,
    HOST_ACTIVE,
    JOIN_LOBBY,
    JOIN_SCANNING,
    JOIN_ACTIVE
}

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

    // --- Drawing state ---
    var isSprayMode by remember { mutableStateOf(true) }
    val sprayColor = Color.Magenta
    var brushSize by remember { mutableFloatStateOf(0.02f) }

    // --- Whiteboard state ---
    var isWhiteboardMode by remember { mutableStateOf(false) }
    var whiteboardNode by remember { mutableStateOf<CubeNode?>(null) }
    var whiteboardAnchorNode by remember { mutableStateOf<AnchorNode?>(null) }
    var whiteboardWidth by remember { mutableFloatStateOf(1.2f) }
    var whiteboardHeight by remember { mutableFloatStateOf(0.9f) }

    // --- Gesture tracking state (for stroke interpolation) ---
    var lastTouchX by remember { mutableStateOf<Float?>(null) }
    var lastTouchY by remember { mutableStateOf<Float?>(null) }
    var lastTouchTime by remember { mutableLongStateOf(0L) }

    // Automatically update whiteboard dimensions when parameters change
    LaunchedEffect(whiteboardWidth, whiteboardHeight, whiteboardNode) {
        whiteboardNode?.scale = io.github.sceneview.math.Scale(whiteboardWidth, whiteboardHeight, 0.02f)
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

    // Performs a hit-test at the given screen coordinate and spawns a spray sphere
    val drawPointAt = { x: Float, y: Float ->
        val currentFrame = frame
        val board = whiteboardNode
        if (currentFrame != null) {
            val hitResult = currentFrame
                .hitTest(x, y)
                .firstOrNull { hit ->
                    val trackable = hit.trackable
                    (trackable is Plane && trackable.trackingState == TrackingState.TRACKING) ||
                    hit.isValid(depthPoint = true, point = true, instantPlacementPoint = true)
                }

            hitResult?.createAnchorOrNull()?.let { anchor ->
                if (isWhiteboardMode && board != null) {
                    val tempAnchorNode = AnchorNode(engine = engine, anchor = anchor)
                    val worldPos = tempAnchorNode.worldPosition
                    val localPos = (board.worldToLocal * dev.romainguy.kotlin.math.Float4(worldPos, 1f)).xyz
                    val halfWidth = whiteboardWidth / 2f
                    val halfHeight = whiteboardHeight / 2f

                    if (localPos.x >= -halfWidth && localPos.x <= halfWidth &&
                        localPos.y >= -halfHeight && localPos.y <= halfHeight) {

                        val sphereNode = SphereNode(
                            engine = engine,
                            radius = brushSize,
                            materialInstance = materialLoader.createColorInstance(sprayColor)
                        ).apply {
                            position = io.github.sceneview.math.Position(localPos.x, localPos.y, 0f)
                        }

                        board.addChildNode(sphereNode)

                        if (appMode != ArAppMode.SOLO) {
                            val syncNode = ArSyncNode(
                                type = "sphere",
                                posX = localPos.x,
                                posY = localPos.y,
                                posZ = 0f,
                                scale = brushSize,
                                colorHex = "#FF00FF"
                            )
                            syncClient?.sendNode(syncNode)
                        }
                    }
                } else {
                    val sphereNode = SphereNode(
                        engine = engine,
                        radius = brushSize,
                        materialInstance = materialLoader.createColorInstance(sprayColor)
                    )

                    if (appMode == ArAppMode.SOLO) {
                        val anchorNode = AnchorNode(engine = engine, anchor = anchor)
                        anchorNode.addChildNode(sphereNode)
                        childNodes += anchorNode
                    } else {
                        val baseNode = sharedBaseNode
                        if (baseNode != null) {
                            // Position relative to parent base anchor is calculated automatically by setting worldPosition
                            val tempAnchorNode = AnchorNode(engine = engine, anchor = anchor)
                            sphereNode.worldPosition = tempAnchorNode.worldPosition
                            baseNode.addChildNode(sphereNode)

                            // Send coordinate relative to shared anchor to the server
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
                                CubeNode(
                                    engine = engine,
                                    materialInstance = materialLoader.createColorInstance(Color(0xFFF5F5F5))
                                ).apply {
                                    scale = io.github.sceneview.math.Scale(whiteboardWidth, whiteboardHeight, 0.02f)
                                    lookAt(cameraNode.worldPosition)
                                    val rot = rotation
                                    rotation = io.github.sceneview.math.Rotation(0f, rot.y, 0f)
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
                                    val nodeRadius = syncNode.scale
                                    val targetParent = board ?: resolvedNode
                                    if (syncNode.type == "sphere") {
                                        val sphereNode = SphereNode(
                                            engine = engine,
                                            radius = nodeRadius,
                                            materialInstance = materialLoader.createColorInstance(sprayColor)
                                        ).apply {
                                            position = Position(syncNode.posX, syncNode.posY, syncNode.posZ)
                                        }
                                        targetParent.addChildNode(sphereNode)
                                    } else if (syncNode.type == "duck") {
                                        val currentModel = duckModel
                                        if (currentModel != null) {
                                            val modelInstance = modelLoader.createInstance(currentModel.modelInstance.asset)
                                            if (modelInstance != null) {
                                                val modelNode = ModelNode(modelInstance = modelInstance).apply {
                                                    scale = io.github.sceneview.math.Scale(0.5f)
                                                    position = Position(syncNode.posX, syncNode.posY, syncNode.posZ)
                                                }
                                                targetParent.addChildNode(modelNode)
                                            }
                                        }
                                    }
                                },
                                onRoomCleared = {
                                    if (board != null) {
                                        board.clearChildNodes()
                                    } else {
                                        resolvedNode.clearChildNodes()
                                    }
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
                        if (currentFrame != null) {
                            val hitResult = currentFrame
                                .hitTest(motionEvent.x, motionEvent.y)
                                .firstOrNull { hit ->
                                    val trackable = hit.trackable
                                    (trackable is Plane && trackable.trackingState == TrackingState.TRACKING) ||
                                    hit.isValid(depthPoint = true, point = true, instantPlacementPoint = true)
                                }

                            hitResult?.createAnchorOrNull()?.let { anchor ->
                                val board = CubeNode(
                                    engine = engine,
                                    materialInstance = materialLoader.createColorInstance(Color(0xFFF5F5F5))
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
                                                        val nodeRadius = syncNode.scale
                                                        val targetParent = board
                                                        if (syncNode.type == "sphere") {
                                                            val sphereNode = SphereNode(
                                                                engine = engine,
                                                                radius = nodeRadius,
                                                                materialInstance = materialLoader.createColorInstance(sprayColor)
                                                            ).apply {
                                                                position = Position(syncNode.posX, syncNode.posY, syncNode.posZ)
                                                            }
                                                            targetParent.addChildNode(sphereNode)
                                                        } else if (syncNode.type == "duck") {
                                                            val currentModel = duckModel
                                                            if (currentModel != null) {
                                                                val modelInstance = modelLoader.createInstance(currentModel.modelInstance.asset)
                                                                if (modelInstance != null) {
                                                                    val modelNode = ModelNode(modelInstance = modelInstance).apply {
                                                                        scale = io.github.sceneview.math.Scale(0.5f)
                                                                        position = Position(syncNode.posX, syncNode.posY, syncNode.posZ)
                                                                    }
                                                                    targetParent.addChildNode(modelNode)
                                                                }
                                                            }
                                                        }
                                                    },
                                                    onRoomCleared = {
                                                        board.clearChildNodes()
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
                        // Place a duck on the whiteboard
                        val currentModel = duckModel
                        val board = whiteboardNode
                        if (currentModel == null || board == null) {
                            Toast.makeText(context, "Model not ready", Toast.LENGTH_SHORT).show()
                        } else {
                            val currentFrame = frame
                            if (currentFrame != null) {
                                val hitResult = currentFrame
                                    .hitTest(motionEvent.x, motionEvent.y)
                                    .firstOrNull { hit ->
                                        val trackable = hit.trackable
                                        (trackable is Plane && trackable.trackingState == TrackingState.TRACKING) ||
                                        hit.isValid(depthPoint = true, point = true, instantPlacementPoint = true)
                                    }

                                hitResult?.createAnchorOrNull()?.let { anchor ->
                                    val tempAnchorNode = AnchorNode(engine = engine, anchor = anchor)
                                    val worldPos = tempAnchorNode.worldPosition
                                    val localPos = (board.worldToLocal * dev.romainguy.kotlin.math.Float4(worldPos, 1f)).xyz
                                    val halfWidth = whiteboardWidth / 2f
                                    val halfHeight = whiteboardHeight / 2f

                                    if (localPos.x >= -halfWidth && localPos.x <= halfWidth &&
                                        localPos.y >= -halfHeight && localPos.y <= halfHeight) {

                                        val modelInstance = modelLoader.createInstance(currentModel.modelInstance.asset)
                                        if (modelInstance != null) {
                                            val modelNode = ModelNode(modelInstance = modelInstance).apply {
                                                scale = io.github.sceneview.math.Scale(0.5f)
                                                position = Position(localPos.x, localPos.y, 0f)
                                            }
                                            board.addChildNode(modelNode)

                                            if (appMode != ArAppMode.SOLO) {
                                                val syncNode = ArSyncNode(
                                                    type = "duck",
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
                                                val nodeRadius = syncNode.scale
                                                if (syncNode.type == "sphere") {
                                                    val sphereNode = SphereNode(
                                                        engine = engine,
                                                        radius = nodeRadius,
                                                        materialInstance = materialLoader.createColorInstance(sprayColor)
                                                    ).apply {
                                                        position = Position(syncNode.posX, syncNode.posY, syncNode.posZ)
                                                    }
                                                    sharedBaseNode?.addChildNode(sphereNode)
                                                } else if (syncNode.type == "duck") {
                                                    val currentModel = duckModel
                                                    if (currentModel != null) {
                                                        val modelInstance = modelLoader.createInstance(currentModel.modelInstance.asset)
                                                        if (modelInstance != null) {
                                                            val modelNode = ModelNode(modelInstance = modelInstance).apply {
                                                                scale = io.github.sceneview.math.Scale(0.5f)
                                                                position = Position(syncNode.posX, syncNode.posY, syncNode.posZ)
                                                            }
                                                            sharedBaseNode?.addChildNode(modelNode)
                                                        }
                                                    }
                                                }
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
                        // Regular duck placement mode
                        if (!isSprayMode) {
                            val currentModel = duckModel
                            if (currentModel == null) {
                                Toast.makeText(context, "Model not ready", Toast.LENGTH_SHORT).show()
                            } else {
                                val currentFrame = frame
                                if (currentFrame != null) {
                                    val hitResult = currentFrame
                                        .hitTest(motionEvent.x, motionEvent.y)
                                        .firstOrNull { hit ->
                                            val trackable = hit.trackable
                                            (trackable is Plane && trackable.trackingState == TrackingState.TRACKING) ||
                                            hit.isValid(depthPoint = true, point = true, instantPlacementPoint = true)
                                        }

                                    hitResult?.createAnchorOrNull()?.let { anchor ->
                                        val modelInstance = modelLoader.createInstance(currentModel.modelInstance.asset)
                                        if (modelInstance != null) {
                                            val modelNode = ModelNode(modelInstance = modelInstance).apply {
                                                scale = io.github.sceneview.math.Scale(0.5f)
                                            }

                                            if (appMode == ArAppMode.SOLO) {
                                                val anchorNode = AnchorNode(engine = engine, anchor = anchor)
                                                anchorNode.addChildNode(modelNode)
                                                childNodes += anchorNode
                                            } else {
                                                val baseNode = sharedBaseNode
                                                if (baseNode != null) {
                                                    val tempAnchorNode = AnchorNode(engine = engine, anchor = anchor)
                                                    modelNode.worldPosition = tempAnchorNode.worldPosition
                                                    baseNode.addChildNode(modelNode)

                                                    // Send placements over socket
                                                    val relativePos = modelNode.position
                                                    val syncNode = ArSyncNode(
                                                        type = "duck",
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
                modifier = Modifier
            )
        }

        if (isModelLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary
            )
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
                    }
                },
                whiteboardWidth = whiteboardWidth,
                onWhiteboardWidthChange = { whiteboardWidth = it },
                whiteboardHeight = whiteboardHeight,
                onWhiteboardHeightChange = { whiteboardHeight = it }
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
