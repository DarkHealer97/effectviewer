package com.effectviewer.ui.viewer

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import com.effectviewer.model.EmitterShape

class CursorView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var cx: Float = 0f
    var cy: Float = 0f
    var step: Float = 40f
    var clampRect: RectF = RectF()

    // Raggio X in pixel schermo — impostato dall'esterno durante regolazione.
    // (Manteniamo il nome previewRadiusPx per compatibilità col menu radiale:
    //  scrive qui e questo aggiorna anche previewRadiusXPx.)
    var previewRadiusPx: Float = 0f
        set(value) {
            field = value
            previewRadiusXPx = value
            if (previewShape == EmitterShape.CIRCLE) previewRadiusYPx = value
            invalidate()
        }

    // Semiassi indipendenti per le forme dell'editor esteso
    var previewRadiusXPx: Float = 0f
        set(value) { field = value; invalidate() }
    var previewRadiusYPx: Float = 0f
        set(value) { field = value; invalidate() }

    var previewRotation: Float = 0f
        set(value) { field = value; invalidate() }

    var previewShape: EmitterShape = EmitterShape.CIRCLE
        set(value) { field = value; invalidate() }

    // Intensità 0..1 — mostrata come area interna
    var previewIntensity: Float = 0.5f
        set(value) { field = value; invalidate() }

    var showPreview: Boolean = false
        set(value) { field = value; invalidate() }

    var onConfirm: ((x: Float, y: Float) -> Unit)? = null
    var onCancel:  (() -> Unit)? = null

    // ── Paint ─────────────────────────────────────────────────────────────────

    private val crossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; strokeWidth = 3f; style = Paint.Style.STROKE
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(120, 0, 0, 0); strokeWidth = 5f; style = Paint.Style.STROKE
    }
    private val circleFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(60, 255, 255, 255); style = Paint.Style.FILL
    }
    private val previewFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(40, 255, 200, 50); style = Paint.Style.FILL
    }
    private val previewBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 255, 200, 50)
        style = Paint.Style.STROKE
        strokeWidth = 2f
        pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
    }
    private val intensityPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(80, 255, 160, 0); style = Paint.Style.FILL
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textAlign = Paint.Align.CENTER; textSize = 22f
        setShadowLayer(4f, 0f, 0f, Color.BLACK)
    }

    private val armLen  = 30f
    private val gapSize = 10f
    private val ringR   = 18f

    override fun onDraw(canvas: Canvas) {
        val x = cx; val y = cy

        // Anteprima forma (ruotata)
        if (showPreview && previewRadiusXPx > 0f) {
            canvas.save()
            canvas.translate(x, y)
            if (previewRotation != 0f && previewShape != EmitterShape.CIRCLE) {
                canvas.rotate(previewRotation)
            }

            val rx = previewRadiusXPx
            // Solo il CERCHIO lega ry a rx; ellisse, rettangolo e triangolo
            // usano previewRadiusYPx indipendente (coerente con lo spawn).
            val ry = if (previewShape == EmitterShape.CIRCLE)
                previewRadiusXPx else previewRadiusYPx

            // Area interna proporzionale all'intensità
            val k = 0.2f + previewIntensity * 0.8f
            intensityPaint.alpha = (previewIntensity * 100).toInt().coerceIn(20, 100)
            drawShape(canvas, previewShape, rx * k, ry * k, previewFill)
            drawShape(canvas, previewShape, rx * k, ry * k, intensityPaint)

            // Contorno tratteggiato (area di spawn)
            drawShape(canvas, previewShape, rx, ry, previewBorder)

            canvas.restore()
        }

        // Mirino (non ruota)
        drawCrosshair(canvas, x + 2f, y + 2f, shadowPaint)
        canvas.drawCircle(x, y, ringR, circleFill)
        drawCrosshair(canvas, x, y, crossPaint)

        // Label
        if (showPreview) {
            canvas.drawText(buildLabel(), x, y - armLen - 16f, labelPaint)
        }
    }

    private fun drawShape(canvas: Canvas, shape: EmitterShape, rx: Float, ry: Float, paint: Paint) {
        when (shape) {
            EmitterShape.CIRCLE,
            EmitterShape.ELLIPSE   -> canvas.drawOval(-rx, -ry, rx, ry, paint)
            EmitterShape.RECTANGLE -> canvas.drawRect(-rx, -ry, rx, ry, paint)
            EmitterShape.TRIANGLE  -> canvas.drawPath(trianglePath(rx, ry), paint)
        }
    }

    // Triangolo isoscele: base in basso (±rx, +ry), apice in alto (0, -ry).
    // Stessa geometria dello spawn in ParticleSystem (radiusX = mezza base,
    // radiusY = mezza altezza).
    private fun trianglePath(rx: Float, ry: Float): Path {
        return Path().apply {
            moveTo(0f, -ry)
            lineTo(rx, ry)
            lineTo(-rx, ry)
            close()
        }
    }

    private fun buildLabel(): String {
        val w = clampRect.width().coerceAtLeast(1f)
        val h = clampRect.height().coerceAtLeast(1f)
        val xPct = (previewRadiusXPx / w * 100).toInt()
        val yPct = (previewRadiusYPx / h * 100).toInt()
        val iPct = (previewIntensity * 100).toInt()
        return when (previewShape) {
            EmitterShape.CIRCLE    -> "⬤ $xPct%  ✦ $iPct%"
            EmitterShape.ELLIPSE   -> "⬭ ${xPct}×${yPct}%  ✦ $iPct%  ↻${previewRotation.toInt()}°"
            EmitterShape.RECTANGLE -> "▬ ${xPct}×${yPct}%  ✦ $iPct%  ↻${previewRotation.toInt()}°"
            EmitterShape.TRIANGLE  -> "▲ ${xPct}×${yPct}%  ✦ $iPct%  ↻${previewRotation.toInt()}°"
        }
    }

    private fun drawCrosshair(canvas: Canvas, x: Float, y: Float, paint: Paint) {
        canvas.drawCircle(x, y, ringR, paint.apply { style = Paint.Style.STROKE })
        canvas.drawLine(x - armLen, y, x - gapSize, y, paint)
        canvas.drawLine(x + gapSize, y, x + armLen, y, paint)
        canvas.drawLine(x, y - armLen, x, y - gapSize, paint)
        canvas.drawLine(x, y + gapSize, x, y + armLen, paint)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        var moved = true
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT  -> cx -= step
            KeyEvent.KEYCODE_DPAD_RIGHT -> cx += step
            KeyEvent.KEYCODE_DPAD_UP    -> cy -= step
            KeyEvent.KEYCODE_DPAD_DOWN  -> cy += step
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_DPAD_CENTER -> { onConfirm?.invoke(cx, cy); return true }
            KeyEvent.KEYCODE_BACK        -> { onCancel?.invoke(); return true }
            else -> moved = false
        }
        if (moved) { clamp(); invalidate() }
        return moved || super.onKeyDown(keyCode, event)
    }

    private fun clamp() {
        if (!clampRect.isEmpty) {
            cx = cx.coerceIn(clampRect.left, clampRect.right)
            cy = cy.coerceIn(clampRect.top,  clampRect.bottom)
        }
    }

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }
}
