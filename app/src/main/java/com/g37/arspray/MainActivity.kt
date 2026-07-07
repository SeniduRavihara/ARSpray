package com.g37.arspray

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.g37.arspray.ui.theme.ARSprayTheme
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Session
import io.github.sceneview.ar.ARScene
import io.github.sceneview.ar.arcore.createAnchorOrNull
import io.github.sceneview.ar.arcore.isValid
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.ar.rememberARCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNodes
import io.github.sceneview.rememberOnGestureListener
import io.github.sceneview.node.ModelNode
import io.github.sceneview.node.SphereNode

class MainActivity : ComponentActivity() {

    private var hasCameraPermission by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        hasCameraPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }

        setContent {
            ARSprayTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (hasCameraPermission) {
                        ArSprayScreen()
                    } else {
                        PermissionScreen {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionScreen(onRequestPermission: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Camera permission is required for AR features",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Button(onClick = onRequestPermission) {
                Text("Grant Permission")
            }
        }
    }
}

@Composable
fun ArSprayScreen() {
    val context = LocalContext.current
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)
    val cameraNode = rememberARCameraNode(engine)
    val childNodes = rememberNodes()
    var frame by remember { mutableStateOf<Frame?>(null) }
    var isModelLoading by remember { mutableStateOf(true) }

    var duckModel by remember { mutableStateOf<ModelNode?>(null) }
    
    // AR Mode: true = Spray (Draw), false = Place (Duck)
    var isSprayMode by remember { mutableStateOf(true) }
    val sprayColor = Color.Magenta

    LaunchedEffect(modelLoader) {
        isModelLoading = true
        try {
            val model = modelLoader.loadModel("models/duck.glb")
            if (model != null) {
                duckModel = ModelNode(modelInstance = modelLoader.createInstance(model)!!)
            } else {
                Toast.makeText(context, "Model not found in assets/models/duck.glb", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Error loading model: ${e.message}", Toast.LENGTH_LONG).show()
        } finally {
            isModelLoading = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ARScene(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            cameraNode = cameraNode,
            childNodes = childNodes,
            planeRenderer = true,
            sessionConfiguration = { session: Session, config: Config ->
                config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                config.lightEstimationMode = Config.LightEstimationMode.AMBIENT_INTENSITY
                config.depthMode = if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC))
                    Config.DepthMode.AUTOMATIC else Config.DepthMode.DISABLED
                config.focusMode = Config.FocusMode.AUTO
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
                                val hitResults = currentFrame.hitTest(motionEvent.x, motionEvent.y)
                                val hitResult = hitResults.firstOrNull { it.isValid(depthPoint = true, point = true) }
                                
                                if (hitResult != null) {
                                    val anchor = hitResult.createAnchorOrNull()
                                    if (anchor != null) {
                                        val anchorNode = AnchorNode(engine = engine, anchor = anchor)
                                        val modelInstance = modelLoader.createInstance(currentModel.modelInstance.asset)
                                        if (modelInstance != null) {
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
                    }
                },
                onMove = { _, motionEvent, _ ->
                    if (isSprayMode) {
                        val currentFrame = frame
                        if (currentFrame != null) {
                            val hitResults = currentFrame.hitTest(motionEvent.x, motionEvent.y)
                            val hitResult = hitResults.firstOrNull { it.isValid(depthPoint = true, point = true) }
                            
                            if (hitResult != null) {
                                val anchor = hitResult.createAnchorOrNull()
                                if (anchor != null) {
                                    val anchorNode = AnchorNode(engine = engine, anchor = anchor)
                                    val sphereNode = SphereNode(
                                        engine = engine,
                                        radius = 0.02f,
                                        materialInstance = materialLoader.createColorInstance(sprayColor)
                                    )
                                    anchorNode.addChildNode(sphereNode)
                                    childNodes += anchorNode
                                }
                            }
                        }
                    }
                }
            )
        )

        // UI Overlay
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 48.dp)
                .padding(horizontal = 24.dp)
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (isSprayMode) "AR Spray Mode (Drawing)" else "AR Place Mode (Duck)",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isSprayMode) 
                    "Move your finger on a surface to DRAW!" 
                    else "Tap on a surface to PLACE A DUCK!",
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodySmall
            )
        }

        if (isModelLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Action Buttons
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = { isSprayMode = !isSprayMode },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSprayMode) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                )
            ) {
                Text(if (isSprayMode) "Switch to Duck" else "Switch to Spray")
            }
            
            Button(
                onClick = { childNodes.clear() },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Clear All")
            }
        }
    }
}
