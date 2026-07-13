package com.g37.arspray.ar

import androidx.compose.ui.graphics.Color
import com.google.android.filament.Engine
import com.google.android.filament.MaterialInstance
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.node.ModelNode
import io.github.sceneview.node.Node
import io.github.sceneview.node.SphereNode
import io.github.sceneview.node.CubeNode
import io.github.sceneview.math.Position
import com.g37.arspray.model.DrawingStroke

fun spawnSyncNode(
    engine: Engine,
    materialLoader: MaterialLoader,
    modelLoader: ModelLoader,
    syncNode: ArSyncNode,
    parent: Node,
    sprayMaterialInstance: MaterialInstance,
    sprayColor: Color,
    eyeModel: ModelNode?,
    heartModel: ModelNode?,
    skeletonModel: ModelNode?,
    skeletonHeadModel: ModelNode?,
    urinarySystemModel: ModelNode?,
    whiteboardWidth: Float,
    whiteboardHeight: Float,
    whiteboardPaths: List<DrawingStroke>,
    onPathsChanged: (List<DrawingStroke>) -> Unit
) {
    val nodeRadius = syncNode.scale
    if (syncNode.type == "sphere") {
        val sphereNode = SphereNode(
            engine = engine,
            radius = nodeRadius,
            materialInstance = materialLoader.createColorInstance(sprayColor)
        ).apply {
            position = Position(syncNode.posX, syncNode.posY, syncNode.posZ)
        }
        parent.addChildNode(sphereNode)
    } else if (syncNode.type == "eye") {
        if (eyeModel != null) {
            val modelInstance = modelLoader.createInstance(eyeModel.modelInstance.asset)
            if (modelInstance != null) {
                val modelNode = ModelNode(modelInstance = modelInstance).apply {
                    scale = io.github.sceneview.math.Scale(syncNode.scale)
                    position = Position(syncNode.posX, syncNode.posY, syncNode.posZ)
                }
                parent.addChildNode(modelNode)
            }
        }
    } else if (syncNode.type == "heart") {
        if (heartModel != null) {
            val modelInstance = modelLoader.createInstance(heartModel.modelInstance.asset)
            if (modelInstance != null) {
                val modelNode = ModelNode(modelInstance = modelInstance).apply {
                    scale = io.github.sceneview.math.Scale(syncNode.scale)
                    position = Position(syncNode.posX, syncNode.posY, syncNode.posZ)
                }
                parent.addChildNode(modelNode)
            }
        }
    } else if (syncNode.type == "skeleton") {
        if (skeletonModel != null) {
            val modelInstance = modelLoader.createInstance(skeletonModel.modelInstance.asset)
            if (modelInstance != null) {
                val modelNode = ModelNode(modelInstance = modelInstance).apply {
                    scale = io.github.sceneview.math.Scale(syncNode.scale)
                    position = Position(syncNode.posX, syncNode.posY, syncNode.posZ)
                }
                parent.addChildNode(modelNode)
            }
        }
    } else if (syncNode.type == "skeleton_head") {
        if (skeletonHeadModel != null) {
            val modelInstance = modelLoader.createInstance(skeletonHeadModel.modelInstance.asset)
            if (modelInstance != null) {
                val modelNode = ModelNode(modelInstance = modelInstance).apply {
                    scale = io.github.sceneview.math.Scale(syncNode.scale)
                    position = Position(syncNode.posX, syncNode.posY, syncNode.posZ)
                }
                parent.addChildNode(modelNode)
            }
        }
    } else if (syncNode.type == "urinary_system") {
        if (urinarySystemModel != null) {
            val modelInstance = modelLoader.createInstance(urinarySystemModel.modelInstance.asset)
            if (modelInstance != null) {
                val modelNode = ModelNode(modelInstance = modelInstance).apply {
                    scale = io.github.sceneview.math.Scale(syncNode.scale)
                    position = Position(syncNode.posX, syncNode.posY, syncNode.posZ)
                }
                parent.addChildNode(modelNode)
            }
        }
    } else if (syncNode.type == "cube") {
        val cubeNode = CubeNode(
            engine = engine,
            size = io.github.sceneview.math.Size(0.1f),
            materialInstance = materialLoader.createColorInstance(Color.Red)
        ).apply {
            position = Position(syncNode.posX, syncNode.posY, syncNode.posZ)
        }
        parent.addChildNode(cubeNode)
    } else if (syncNode.type == "sphere_object") {
        val sphereNode = SphereNode(
            engine = engine,
            radius = 0.05f,
            materialInstance = materialLoader.createColorInstance(Color.Blue)
        ).apply {
            position = Position(syncNode.posX, syncNode.posY, syncNode.posZ)
        }
        parent.addChildNode(sphereNode)
    } else if (syncNode.type == "line") {
        val halfW = whiteboardWidth / 2f
        val halfH = whiteboardHeight / 2f
        val px = ((syncNode.posX + halfW) / whiteboardWidth) * 1024f
        val py = ((halfH - syncNode.posY) / whiteboardHeight) * 1024f
        val pt = androidx.compose.ui.geometry.Offset(px, py)
        val isEraser = syncNode.posZ == -1f || syncNode.posZ == -2f
        val isStart = syncNode.posZ == 1f || syncNode.posZ == -1f
        val color = try {
            Color(android.graphics.Color.parseColor(syncNode.colorHex))
        } catch (e: Exception) {
            Color.Magenta
        }
        if (isStart) {
            onPathsChanged(whiteboardPaths + DrawingStroke(
                points = listOf(pt),
                color = color,
                width = syncNode.scale,
                isEraser = isEraser
            ))
        } else {
            val last = whiteboardPaths.lastOrNull()
            if (last != null) {
                val updatedLast = last.copy(points = last.points + pt)
                onPathsChanged(whiteboardPaths.dropLast(1) + updatedLast)
            } else {
                onPathsChanged(whiteboardPaths + DrawingStroke(
                    points = listOf(pt),
                    color = color,
                    width = syncNode.scale,
                    isEraser = isEraser
                ))
            }
        }
    }
}
