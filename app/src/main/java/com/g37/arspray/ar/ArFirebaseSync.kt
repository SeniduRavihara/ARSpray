package com.g37.arspray.ar

import android.os.Handler
import android.os.Looper
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.g37.arspray.model.DrawingStroke
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class ArFirebaseSync(private val roomId: String) {
    private val db = FirebaseFirestore.getInstance()
    private val roomRef = db.collection("rooms").document(roomId)
    private var listenerRegistration: ListenerRegistration? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    // Tracks previously processed nodes to avoid duplicate rendering/spawning
    private var processedNodesCount = 0

    // Callback handlers
    private var onNodeReceived: ((ArSyncNode) -> Unit)? = null
    private var onPathsChanged: ((List<DrawingStroke>) -> Unit)? = null
    private var onRoomCleared: (() -> Unit)? = null

    /**
     * Creates a new room document in Firestore if it doesn't already exist.
     */
    fun initializeRoom(onComplete: (Boolean) -> Unit) {
        val roomData = mapOf(
            "createdAt" to Timestamp.now(),
            "nodes" to emptyList<Any>(),
            "whiteboardPaths" to emptyList<Any>(),
            "anchorId" to null
        )
        roomRef.set(roomData)
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { onComplete(false) }
    }

    /**
     * Subscribes to the room document in Firestore and processes updates.
     */
    fun startListener(
        onNodeReceived: (ArSyncNode) -> Unit,
        onPathsChanged: (List<DrawingStroke>) -> Unit,
        onRoomCleared: () -> Unit,
        onConnectionStateChanged: (Boolean) -> Unit,
        onAnchorIdReceived: (String) -> Unit
    ) {
        this.onNodeReceived = onNodeReceived
        this.onPathsChanged = onPathsChanged
        this.onRoomCleared = onRoomCleared

        onConnectionStateChanged(true)

        listenerRegistration = roomRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                error.printStackTrace()
                mainHandler.post { onConnectionStateChanged(false) }
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                mainHandler.post { onConnectionStateChanged(true) }

                // 1. Process new AR Nodes
                val nodesRaw = snapshot.get("nodes") as? List<Map<String, Any>> ?: emptyList()
                if (nodesRaw.isEmpty() && processedNodesCount > 0) {
                    processedNodesCount = 0
                    mainHandler.post { onRoomCleared() }
                } else if (nodesRaw.size > processedNodesCount) {
                    val newNodesRaw = nodesRaw.drop(processedNodesCount)
                    newNodesRaw.forEach { nodeMap ->
                        try {
                            val syncNode = mapToArSyncNode(nodeMap)
                            mainHandler.post { onNodeReceived(syncNode) }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    processedNodesCount = nodesRaw.size
                }

                // 2. Process whiteboard paths
                val pathsRaw = snapshot.get("whiteboardPaths") as? List<Map<String, Any>> ?: emptyList()
                val parsedPaths = pathsRaw.mapNotNull { pathMap ->
                    try {
                        mapToDrawingStroke(pathMap)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        null
                    }
                }
                mainHandler.post { onPathsChanged(parsedPaths) }

                // 3. Process cloud anchor ID for guest auto-resolve
                val anchorId = snapshot.getString("anchorId")
                if (anchorId != null) {
                    mainHandler.post { onAnchorIdReceived(anchorId) }
                }
            }
        }
    }

    /**
     * Unsubscribes from the real-time updates.
     */
    fun stopListener() {
        listenerRegistration?.remove()
        listenerRegistration = null
    }

    /**
     * Appends a new AR placement node to the room's node list.
     */
    fun sendNode(node: ArSyncNode) {
        roomRef.update("nodes", FieldValue.arrayUnion(arSyncNodeToMap(node)))
            .addOnFailureListener { it.printStackTrace() }
    }

    /**
     * Overwrites the room's whiteboardPaths list in Firestore.
     */
    fun sendWhiteboardPaths(paths: List<DrawingStroke>) {
        val serializedPaths = paths.map { drawingStrokeToMap(it) }
        roomRef.update("whiteboardPaths", serializedPaths)
            .addOnFailureListener { it.printStackTrace() }
    }

    /**
     * Clears all placed nodes and whiteboard paths in the room.
     */
    fun sendClear() {
        roomRef.update(
            mapOf(
                "nodes" to emptyList<Any>(),
                "whiteboardPaths" to emptyList<Any>()
            )
        ).addOnFailureListener { it.printStackTrace() }
    }

    /**
     * Updates the hosted Cloud Anchor ID for the room.
     */
    fun updateAnchorId(anchorId: String) {
        roomRef.update("anchorId", anchorId)
            .addOnFailureListener { it.printStackTrace() }
    }

    // --- Mappings Helpers ---

    private fun arSyncNodeToMap(node: ArSyncNode): Map<String, Any> {
        return mapOf(
            "type" to node.type,
            "posX" to node.posX.toDouble(),
            "posY" to node.posY.toDouble(),
            "posZ" to node.posZ.toDouble(),
            "scale" to node.scale.toDouble(),
            "colorHex" to node.colorHex
        )
    }

    private fun mapToArSyncNode(map: Map<String, Any>): ArSyncNode {
        return ArSyncNode(
            type = map["type"] as? String ?: "sphere",
            posX = (map["posX"] as? Number)?.toFloat() ?: 0f,
            posY = (map["posY"] as? Number)?.toFloat() ?: 0f,
            posZ = (map["posZ"] as? Number)?.toFloat() ?: 0f,
            scale = (map["scale"] as? Number)?.toFloat() ?: 1f,
            colorHex = map["colorHex"] as? String ?: "#FF00FF"
        )
    }

    private fun drawingStrokeToMap(stroke: DrawingStroke): Map<String, Any> {
        val serializedPoints = stroke.points.map { mapOf("x" to it.x.toDouble(), "y" to it.y.toDouble()) }
        return mapOf(
            "points" to serializedPoints,
            "color" to stroke.color.toArgb(),
            "width" to stroke.width.toDouble(),
            "isEraser" to stroke.isEraser
        )
    }

    private fun mapToDrawingStroke(map: Map<String, Any>): DrawingStroke {
        val colorInt = (map["color"] as? Number)?.toInt() ?: Color.Black.toArgb()
        val widthFloat = (map["width"] as? Number)?.toFloat() ?: 10f
        val isEraser = map["isEraser"] as? Boolean ?: false
        val pointsRaw = map["points"] as? List<Map<String, Any>> ?: emptyList()
        val points = pointsRaw.map {
            val x = (it["x"] as? Number)?.toFloat() ?: 0f
            val y = (it["y"] as? Number)?.toFloat() ?: 0f
            Offset(x, y)
        }
        return DrawingStroke(points, Color(colorInt), widthFloat, isEraser)
    }
}
