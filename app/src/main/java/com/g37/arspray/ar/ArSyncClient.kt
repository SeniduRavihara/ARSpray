// com/g37/arspray/ar/ArSyncClient.kt
package com.g37.arspray.ar

import android.os.Handler
import android.os.Looper
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.json.JSONObject
import java.net.URI

/**
 * Data class representing a drawing point or placed model.
 * All coordinates are represented LOCAL to the shared Cloud Anchor.
 */
data class ArSyncNode(
    val type: String, // "sphere" or "duck"
    val posX: Float,
    val posY: Float,
    val posZ: Float,
    val scale: Float = 1f,
    val colorHex: String = "#FF00FF"
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("type", type)
            put("posX", posX.toDouble())
            put("posY", posY.toDouble())
            put("posZ", posZ.toDouble())
            put("scale", scale.toDouble())
            put("colorHex", colorHex)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): ArSyncNode {
            return ArSyncNode(
                type = json.getString("type"),
                posX = json.optDouble("posX", 0.0).toFloat(),
                posY = json.optDouble("posY", 0.0).toFloat(),
                posZ = json.optDouble("posZ", 0.0).toFloat(),
                scale = json.optDouble("scale", 1.0).toFloat(),
                colorHex = json.optString("colorHex", "#FF00FF")
            )
        }
    }
}

/**
 * Client class that handles connections to the Node.js broadcast server.
 * Uses org.java-websocket for high-performance low-latency sockets.
 */
class ArSyncClient(
    serverUri: URI,
    private val roomId: String,
    private val onNodeReceived: (ArSyncNode) -> Unit,
    private val onRoomCleared: () -> Unit,
    private val onConnectionStateChanged: (Boolean) -> Unit
) {
    // Deliver callbacks on Android Main/UI thread for Jetpack Compose reactive state updates
    private val mainHandler = Handler(Looper.getMainLooper())

    private val client = object : WebSocketClient(serverUri) {
        override fun onOpen(handshakedata: ServerHandshake?) {
            mainHandler.post { onConnectionStateChanged(true) }
            joinRoom()
        }

        override fun onMessage(message: String?) {
            if (message == null) return
            try {
                val data = JSONObject(message)
                when (data.optString("action")) {
                    "history" -> {
                        val nodesArray = data.optJSONArray("nodes")
                        if (nodesArray != null) {
                            for (i in 0 until nodesArray.length()) {
                                val nodeObj = nodesArray.getJSONObject(i)
                                val syncNode = ArSyncNode.fromJson(nodeObj)
                                mainHandler.post { onNodeReceived(syncNode) }
                            }
                        }
                    }
                    "add_node" -> {
                        val nodeObj = data.optJSONObject("node")
                        if (nodeObj != null) {
                            val syncNode = ArSyncNode.fromJson(nodeObj)
                            mainHandler.post { onNodeReceived(syncNode) }
                        }
                    }
                    "clear" -> {
                        mainHandler.post { onRoomCleared() }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        override fun onClose(code: Int, reason: String?, remote: Boolean) {
            mainHandler.post { onConnectionStateChanged(false) }
        }

        override fun onError(ex: Exception?) {
            ex?.printStackTrace()
            mainHandler.post { onConnectionStateChanged(false) }
        }
    }

    fun connect() {
        try {
            client.connect()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun disconnect() {
        try {
            client.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun joinRoom() {
        val joinMessage = JSONObject().apply {
            put("action", "join")
            put("roomId", roomId)
        }
        sendText(joinMessage.toString())
    }

    fun sendNode(node: ArSyncNode) {
        if (!client.isOpen) return
        val nodeMessage = JSONObject().apply {
            put("action", "add_node")
            put("roomId", roomId)
            put("node", node.toJson())
        }
        sendText(nodeMessage.toString())
    }

    fun sendClear() {
        if (!client.isOpen) return
        val clearMessage = JSONObject().apply {
            put("action", "clear")
            put("roomId", roomId)
        }
        sendText(clearMessage.toString())
    }

    private fun sendText(text: String) {
        try {
            client.send(text)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
