package com.trafficlightdetector

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import com.google.mediapipe.tasks.components.containers.Detection

/**
 * Transparent overlay drawn on top of the camera preview.
 * - Traffic-pole detections → red box, yellow label
 * - All other detections    → green box, white label
 */
class OverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var allDetections:  List<Detection> = emptyList()
    private var poleDetections: List<Detection> = emptyList()
    private var srcWidth  = 1
    private var srcHeight = 1

    private val normalBoxPaint = Paint().apply {
        style       = Paint.Style.STROKE
        strokeWidth = 6f
        color       = Color.GREEN
    }
    private val poleBoxPaint = Paint().apply {
        style       = Paint.Style.STROKE
        strokeWidth = 10f
        color       = Color.RED
    }
    private val labelBackPaint = Paint().apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#B3000000")
    }
    private val normalLabelPaint = Paint().apply {
        color    = Color.WHITE
        textSize = 40f
        typeface = Typeface.DEFAULT_BOLD
        isAntiAlias = true
    }
    private val poleLabelPaint = Paint().apply {
        color    = Color.YELLOW
        textSize = 44f
        typeface = Typeface.DEFAULT_BOLD
        isAntiAlias = true
    }
    private val textBounds = Rect()

    fun update(
        all:         List<Detection>,
        poles:       List<Detection>,
        imageWidth:  Int,
        imageHeight: Int
    ) {
        allDetections  = all
        poleDetections = poles
        srcWidth       = imageWidth
        srcHeight      = imageHeight
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val scaleX = width.toFloat()  / srcWidth
        val scaleY = height.toFloat() / srcHeight

        for (det in allDetections) {
            val isPole    = poleDetections.contains(det)
            val boxPaint  = if (isPole) poleBoxPaint  else normalBoxPaint
            val lblPaint  = if (isPole) poleLabelPaint else normalLabelPaint

            val box = det.boundingBox()
            val l = box.left   * scaleX
            val t = box.top    * scaleY
            val r = box.right  * scaleX
            val b = box.bottom * scaleY

            canvas.drawRect(l, t, r, b, boxPaint)

            val topCat = det.categories().maxByOrNull { it.score() } ?: continue
            val label  = "${topCat.categoryName() ?: "?"}  ${"%.0f".format(topCat.score() * 100)}%"

            lblPaint.getTextBounds(label, 0, label.length, textBounds)
            val lblW = textBounds.width().toFloat()
            val lblH = textBounds.height().toFloat()

            canvas.drawRect(l, t - lblH - 12f, l + lblW + 16f, t, labelBackPaint)
            canvas.drawText(label, l + 8f, t - 6f, lblPaint)
        }
    }
}
