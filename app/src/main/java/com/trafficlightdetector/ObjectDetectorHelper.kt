package com.trafficlightdetector

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.Rot90Op
import org.tensorflow.lite.task.core.BaseOptions
import org.tensorflow.lite.task.vision.detector.Detection
import org.tensorflow.lite.task.vision.detector.ObjectDetector

/**
 * Wraps TFLite Task Vision object detector.
 *
 * Model: EfficientDet-Lite0 trained on COCO 2017.
 * "Traffic pole" is inferred by detecting objects that are mounted on poles:
 *   - traffic light  (COCO label index 9)
 *   - stop sign      (COCO label index 11)
 *   - parking meter  (COCO label index 12)
 *
 * For even better accuracy a custom model trained specifically on traffic poles
 * can be dropped in as assets/efficientdet_lite0.tflite (same filename).
 */
class ObjectDetectorHelper(
    private val context: Context,
    private val listener: DetectorListener
) {
    private var objectDetector: ObjectDetector? = null

    // COCO classes that indicate a traffic pole is present
    private val trafficPoleClasses = setOf(
        "traffic light",
        "stop sign",
        "parking meter"
    )

    init {
        setup()
    }

    private fun setup() {
        val baseOptions = BaseOptions.builder()
            .setNumThreads(4)
            // Uncomment to attempt GPU acceleration (falls back to CPU automatically):
            // .useGpu()
            .build()

        val options = ObjectDetector.ObjectDetectorOptions.builder()
            .setBaseOptions(baseOptions)
            .setScoreThreshold(CONFIDENCE_THRESHOLD)
            .setMaxResults(MAX_RESULTS)
            .build()

        try {
            objectDetector = ObjectDetector.createFromFileAndOptions(
                context,
                MODEL_FILENAME,
                options
            )
        } catch (e: Exception) {
            listener.onError(
                "Failed to load model '$MODEL_FILENAME'. " +
                "Run scripts/download_model.sh first. Error: ${e.message}"
            )
            Log.e(TAG, "Model load error", e)
        }
    }

    /**
     * Run detection on a single camera frame.
     * @param bitmap   ARGB_8888 bitmap from CameraX ImageAnalysis
     * @param rotation degrees the image must be rotated to match display orientation
     */
    fun detect(bitmap: Bitmap, rotation: Int) {
        if (objectDetector == null) {
            setup()
            return
        }

        // Rotate image to upright orientation before inference
        val imageProcessor = ImageProcessor.Builder()
            .add(Rot90Op(-rotation / 90))
            .build()

        val tensorImage = imageProcessor.process(TensorImage.fromBitmap(bitmap))
        val allDetections: MutableList<Detection> = objectDetector?.detect(tensorImage)
            ?: mutableListOf()

        val poleDetections = allDetections.filter { detection ->
            detection.categories.any { cat ->
                trafficPoleClasses.contains(cat.label.lowercase().trim())
            }
        }

        listener.onResults(
            allDetections = allDetections,
            poleDetections = poleDetections,
            imageWidth = tensorImage.width,
            imageHeight = tensorImage.height
        )
    }

    interface DetectorListener {
        fun onError(error: String)
        fun onResults(
            allDetections: MutableList<Detection>,
            poleDetections: List<Detection>,
            imageWidth: Int,
            imageHeight: Int
        )
    }

    companion object {
        private const val TAG = "ObjectDetectorHelper"
        const val MODEL_FILENAME = "efficientdet_lite0.tflite"
        private const val CONFIDENCE_THRESHOLD = 0.45f
        private const val MAX_RESULTS = 10
    }
}
