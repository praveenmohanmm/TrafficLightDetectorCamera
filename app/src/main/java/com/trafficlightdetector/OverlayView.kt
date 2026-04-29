package com.poodlesoft.trafficlightdetector

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import com.google.mediapipe.tasks.components.containers.Detection

/**
 * Transparent overlay drawn on top of the camera preview.
 *
 * Draws a professional corner-bracket style bounding box for each detection
 * with a clean label chip above the top-left corner.
 *
 * All shown detections are traffic lights (filtered upstream), so a single
 * cyan colour scheme is used throughout.
 */
class OverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var allDetections:  List<Detection> = emptyList()
    private var poleDetections: List<Detection> = emptyList()
    private var srcWidth  = 1
    private var srcHeight = 1

    // ── Paints ────────────────────────────────────────────────────────────────

    /** Corner bracket strokes */
    private val bracketPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style       = Paint.Style.STROKE
        strokeWidth = 5f
        strokeCap   = Paint.Cap.ROUND
        color       = Color.parseColor("#00B4D8")   // cyan accent
    }

    /** Semi-transparent fill behind the label text */
    private val labelBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#D90D1117")
    }

    /** Label text */
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color    = Color.parseColor("#00B4D8")
        textSize = 36f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val labelRect = RectF()

    // ── Data update ───────────────────────────────────────────────────────────

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

    // ── Drawing ───────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val scaleX = width.toFloat()  / srcWidth
        val scaleY = height.toFloat() / srcHeight

        for (det in allDetections) {
            val box = det.boundingBox()
            val l = box.left   * scaleX
            val t = box.top    * scaleY
            val r = box.right  * scaleX
            val b = box.bottom * scaleY

            drawCornerBrackets(canvas, l, t, r, b)

            val topCat = det.categories().maxByOrNull { it.score() } ?: continue
            val score  = (topCat.score() * 100).toInt()
            val label  = "${topCat.categoryName()?.uppercase() ?: "TRAFFIC LIGHT"}  $score%"

            drawLabel(canvas, label, l, t)
        }
    }

    /**
     * Draws four L-shaped corner brackets instead of a full rectangle.
     * Corner arm length = 22% of the shorter dimension of the box.
     */
    private fun drawCornerBrackets(canvas: Canvas, l: Float, t: Float, r: Float, b: Float) {
        val cx = (r - l) * 0.22f
        val cy = (b - t) * 0.22f

        // Top-left
        canvas.drawLine(l, t, l + cx, t, bracketPaint)
        canvas.drawLine(l, t, l, t + cy, bracketPaint)
        // Top-right
        canvas.drawLine(r - cx, t, r, t, bracketPaint)
        canvas.drawLine(r, t, r, t + cy, bracketPaint)
        // Bottom-left
        canvas.drawLine(l, b - cy, l, b, bracketPaint)
        canvas.drawLine(l, b, l + cx, b, bracketPaint)
        // Bottom-right
        canvas.drawLine(r - cx, b, r, b, bracketPaint)
        canvas.drawLine(r, b - cy, r, b, bracketPaint)
    }

    /**
     * Draws a rounded label chip anchored to the top-left of the box.
     */
    private fun drawLabel(canvas: Canvas, text: String, boxLeft: Float, boxTop: Float) {
        val fm      = labelPaint.fontMetrics
        val textW   = labelPaint.measureText(text)
        val textH   = fm.descent - fm.ascent
        val padH    = 10f
        val padV    = 6f
        val radius  = 8f

        val chipLeft   = boxLeft
        val chipTop    = boxTop - textH - padV * 2 - 4f
        val chipRight  = boxLeft + textW + padH * 2
        val chipBottom = boxTop - 4f

        // Keep chip on screen
        val clampedTop    = chipTop.coerceAtLeast(0f)
        val clampedBottom = chipBottom.coerceAtLeast(clampedTop + textH + padV * 2)

        labelRect.set(chipLeft, clampedTop, chipRight, clampedBottom)
        canvas.drawRoundRect(labelRect, radius, radius, labelBgPaint)
        canvas.drawText(
            text,
            chipLeft + padH,
            clampedBottom - padV - fm.descent,
            labelPaint
        )
    }
}
