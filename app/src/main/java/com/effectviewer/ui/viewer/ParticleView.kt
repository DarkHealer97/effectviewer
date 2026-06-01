package com.effectviewer.ui.viewer

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import com.effectviewer.data.AppSettings
import com.effectviewer.model.EffectEmitter
import com.effectviewer.model.EmitterShape
import com.effectviewer.particles.EffectType
import com.effectviewer.particles.ParticleSystem
import kotlin.math.max

class ParticleView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var emitters: List<EffectEmitter> = emptyList()
    var imageRect: RectF = RectF()

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val blurPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        maskFilter = BlurMaskFilter(8f, BlurMaskFilter.Blur.NORMAL)
    }
    // Paint per l'oscuramento BLACKOUT (shader impostato di volta in volta)
    private val blackoutPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    // Paint per BLACKOUT su forme poligonali (rettangolo/triangolo): nero pieno
    // con bordo sfumato tramite BlurMaskFilter. Il raggio del blur viene impostato
    // di volta in volta in proporzione alla dimensione della forma.
    private val blackoutSolidPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.BLACK
    }

    private val handler = Handler(Looper.getMainLooper())
    private var lastTime = System.nanoTime()
    private var running  = false
    // Cap particelle letto da AppSettings (cache per non leggere le prefs a ogni frame)
    private var maxParticles = 3000

    private val ticker = object : Runnable {
        override fun run() {
            if (!running) return

            val now = System.nanoTime()
            val dt  = ((now - lastTime) / 1_000_000_000f).coerceAtMost(0.05f)
            lastTime = now

            val rect = imageRect
            if (!rect.isEmpty && emitters.isNotEmpty()) {
                for (emitter in emitters) {
                    val screenCx = rect.left + emitter.cx * rect.width()
                    val screenCy = rect.top  + emitter.cy * rect.height()
                    val screenRX = emitter.radiusX * rect.width()
                    val screenRY = emitter.radiusY * rect.height()
                    ParticleSystem.update(emitter, screenCx, screenCy, screenRX, screenRY, dt, maxParticles)
                }
                invalidate()
            }

            handler.postDelayed(this, 16)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        maxParticles = AppSettings.maxParticles(context)
        running  = true
        lastTime = System.nanoTime()
        handler.post(ticker)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        running = false
        handler.removeCallbacks(ticker)
    }

    override fun onDraw(canvas: Canvas) {
        val rect = imageRect
        if (rect.isEmpty || emitters.isEmpty()) return

        // Clip al rect dell'immagine: niente disegno fuori dai bordi
        canvas.save()
        canvas.clipRect(rect)

        for (emitter in emitters) {
            // BLACKOUT: oscuramento statico, nessuna particella
            if (emitter.type == EffectType.BLACKOUT) {
                drawBlackout(canvas, emitter, rect)
                continue
            }

            for (p in emitter.particles.toList()) {
                val alpha = (p.alpha * 255).toInt().coerceIn(0, 255)
                if (alpha == 0) continue

                val color     = (p.color and 0x00FFFFFF) or (alpha shl 24)
                val usedPaint = if (emitter.type.opaque) paint else blurPaint
                usedPaint.color = color

                canvas.save()
                canvas.translate(p.x, p.y)
                canvas.rotate(p.rotation)
                canvas.drawCircle(0f, 0f, p.size, usedPaint)
                canvas.restore()
            }
        }

        canvas.restore()
    }

    /**
     * Disegna l'oscuramento per un emitter BLACKOUT.
     * Il RadialGradient è costruito sempre circolare (nero piatto fino all'82%,
     * transizione netta nell'ultimo 18%), poi canvas.scale lo schiaccia
     * nell'ellisse corretta. Per CIRCLE lo scale è 1:1.
     */
    private fun drawBlackout(canvas: Canvas, emitter: EffectEmitter, rect: RectF) {
        val cx = rect.left + emitter.cx * rect.width()
        val cy = rect.top  + emitter.cy * rect.height()
        val rx = emitter.radiusX * rect.width()
        val ry = emitter.radiusY * rect.height()
        val maxR = max(rx, ry)
        if (maxR <= 0f) return

        canvas.save()
        canvas.translate(cx, cy)
        if (emitter.rotation != 0f && emitter.shape != EmitterShape.CIRCLE) {
            canvas.rotate(emitter.rotation)
        }

        when (emitter.shape) {
            EmitterShape.CIRCLE,
            EmitterShape.ELLIPSE -> {
                // Per cerchio/ellisse il gradiente radiale è geometricamente
                // corretto: nero piatto fino all'82%, poi transizione netta.
                val gradient = RadialGradient(
                    0f, 0f, maxR,
                    intArrayOf(Color.BLACK, Color.BLACK, Color.TRANSPARENT),
                    floatArrayOf(0f, 0.82f, 1f),
                    Shader.TileMode.CLAMP
                )
                blackoutPaint.shader = gradient
                canvas.scale(rx / maxR, ry / maxR)
                canvas.drawCircle(0f, 0f, maxR, blackoutPaint)
                blackoutPaint.shader = null
            }
            EmitterShape.RECTANGLE -> {
                // Nero pieno con bordo sfumato. Un gradiente radiale su un
                // rettangolo darebbe una sfumatura asimmetrica: usiamo invece
                // un riempimento solido con BlurMaskFilter per un bordo morbido
                // e uniforme su tutti i lati.
                val blur = (minOf(rx, ry) * 0.18f).coerceAtLeast(2f)
                blackoutSolidPaint.maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
                // Inset così il blur "sborda" fino al bordo nominale invece di oltre
                canvas.drawRect(-rx + blur, -ry + blur, rx - blur, ry - blur, blackoutSolidPaint)
                blackoutSolidPaint.maskFilter = null
            }
            EmitterShape.TRIANGLE -> {
                // Stessa tecnica del rettangolo: nero pieno + bordo sfumato.
                // Triangolo isoscele come lo spawn (base in basso, apice in alto).
                val blur = (minOf(rx, ry) * 0.18f).coerceAtLeast(2f)
                blackoutSolidPaint.maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
                val ix = rx - blur
                val iy = ry - blur
                val path = Path().apply {
                    moveTo(0f, -iy)
                    lineTo(ix, iy)
                    lineTo(-ix, iy)
                    close()
                }
                canvas.drawPath(path, blackoutSolidPaint)
                blackoutSolidPaint.maskFilter = null
            }
        }

        canvas.restore()
    }

    init {
        setBackgroundColor(Color.TRANSPARENT)
    }
}
