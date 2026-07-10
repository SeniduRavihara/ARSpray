package com.g37.arspray.ai

import android.content.Context
import android.widget.Toast
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizer
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.recognition.Ink

class AutoDrawClassifier(
    private val context: Context,
    private val onStateChanged: (String?) -> Unit,
    private val onSuccess: (centerX: Float, centerY: Float) -> Unit,
    private val onClearDrawing: () -> Unit
) {
    fun recognize(ink: Ink) {
        if (ink.strokes.isEmpty()) {
            Toast.makeText(context, "Please draw something on the whiteboard first!", Toast.LENGTH_SHORT).show()
            return
        }

        val modelIdentifier = DigitalInkRecognitionModelIdentifier.fromLanguageTag("zxx-Zsym-x-autodraw")
        if (modelIdentifier == null) {
            Toast.makeText(context, "AutoDraw model identifier not found", Toast.LENGTH_SHORT).show()
            return
        }

        val model = DigitalInkRecognitionModel.builder(modelIdentifier).build()
        val modelManager = RemoteModelManager.getInstance()

        onStateChanged("Checking AI Sketch Model...")
        modelManager.isModelDownloaded(model)
            .addOnSuccessListener { downloaded ->
                if (downloaded) {
                    performRecognition(
                        recognizer = DigitalInkRecognition.getClient(
                            DigitalInkRecognizerOptions.builder(model).build()
                        ),
                        ink = ink
                    )
                } else {
                    onStateChanged("Downloading AI Model (2MB)...")
                    modelManager.download(model, DownloadConditions.Builder().build())
                        .addOnSuccessListener {
                            onStateChanged("AI Model downloaded! Analyzing...")
                            performRecognition(
                                recognizer = DigitalInkRecognition.getClient(
                                    DigitalInkRecognizerOptions.builder(model).build()
                                ),
                                ink = ink
                            )
                        }
                        .addOnFailureListener { e ->
                            onStateChanged(null)
                            Toast.makeText(context, "Failed to download model: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .addOnFailureListener { e ->
                onStateChanged(null)
                Toast.makeText(context, "Model check failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun performRecognition(recognizer: DigitalInkRecognizer, ink: Ink) {
        onStateChanged("AI is analyzing your sketch...")
        recognizer.recognize(ink)
            .addOnSuccessListener { result ->
                onStateChanged(null)
                val candidate = result.candidates.firstOrNull()?.text?.lowercase() ?: ""
                if (candidate == "duck") {
                    Toast.makeText(context, "AI recognized a Duck! Spawning 3D model...", Toast.LENGTH_LONG).show()
                    
                    onClearDrawing()

                    var minX = Float.MAX_VALUE
                    var maxX = -Float.MAX_VALUE
                    var minY = Float.MAX_VALUE
                    var maxY = -Float.MAX_VALUE
                    for (stroke in ink.strokes) {
                        for (pt in stroke.pointsInGlobalCoordinates) {
                            val xMeters = pt.x / 1000f
                            val yMeters = pt.y / 1000f
                            if (xMeters < minX) minX = xMeters
                            if (xMeters > maxX) maxX = xMeters
                            if (yMeters < minY) minY = yMeters
                            if (yMeters > maxY) maxY = yMeters
                        }
                    }
                    val centerX = if (minX != Float.MAX_VALUE) (minX + maxX) / 2f else 0f
                    val centerY = if (minY != Float.MAX_VALUE) (minY + maxY) / 2f else 0f
                    onSuccess(centerX, centerY)
                } else {
                    Toast.makeText(context, "AI recognized '$candidate', but only 'duck' is supported for 3D placement.", Toast.LENGTH_LONG).show()
                }
            }
            .addOnFailureListener { e ->
                onStateChanged(null)
                Toast.makeText(context, "Recognition failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
