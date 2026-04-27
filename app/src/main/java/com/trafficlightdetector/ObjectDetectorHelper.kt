package com.trafficlightdetector

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.components.containers.Detection
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetectorResult

/**
 * Wraps the MediaPipe Tasks object detector.
 *
 * Model : EfficientDet-Lite0 (COCO int8, ~4.4 MB) from MediaPipe model zoo.
 * Traffic-pole proxy classes filtered from COCO:
 *   - traffic light  → signal head on a pole
 *   - stop sign      → sign on a roadside post
 *   - parking meter  → meter on a short post
 */
class ObjectDetectorHelper(
    private val context: Context,
    private val listener: DetectorListener
) {
    private var detector: ObjectDetector? = null

    private val trafficPoleClasses = setOf(
        "traffic light",
        "stop sign",
        "parking meter"
    )

    init { setup() }

    private fun setup() {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath(MODEL_FILENAME)
                .build()

            val options = ObjectDetector.ObjectDetectorOptions.builder()
                .setBaseOptions(baseOptions)
                .setScoreThreshold(CONFIDENCE_THRESHOLD)
                .setMaxResults(MAX_RESULTS)
                .build()

            detector = ObjectDetector.createFromOptions(context, options)
        } catch (e: Exception) {
            listener.onError("Failed to load model '$MODEL_FILENAME': ${e.message}")
            Log.e(TAG, "Model load error", e)
        }
    }

    /**
     * Run detection on one camera frame.
     * @param bitmap   ARGB_8888 bitmap from CameraX ImageAnalysis
     * @param rotation degrees to rotate the bitmap to upright orientation
     */
    fun detect(bitmap: Bitmap, rotation: Int) {
        if (detector == null) { setup(); return }

        // Rotate to upright before inference
        val upright = if (rotation != 0) {
            val m = Matrix().apply { postRotate(rotation.toFloat()) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
        } else bitmap

        val mpImage = BitmapImageBuilder(upright).build()
        val result: ObjectDetectorResult = detector!!.detect(mpImage)

        val all   = result.detections()
        val poles = all.filter { det ->
            det.categories().any { cat ->
                trafficPoleClasses.contains(
                    cat.categoryName().orElse("").lowercase().trim()
                )
            }
        }

        listener.onResults(
            allDetections  = all,
            poleDetections = poles,
            imageWidth     = upright.width,
            imageHeight    = upright.height
        )
    }

    interface DetectorListener {
        fun onError(error: String)
        fun onResults(
            allDetections:  List<Detection>,
            poleDetections: List<Detection>,
            imageWidth:     Int,
            imageHeight:    Int
        )
    }

    companion object {
        private const val TAG                 = "ObjectDetectorHelper"
        const val         MODEL_FILENAME      = "efficientdet_lite0.tflite"
        private const val CONFIDENCE_THRESHOLD = 0.45f
        private const val MAX_RESULTS         = 10
    }
}
