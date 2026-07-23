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
import com.effectviewer.model.Particle
import com.effectviewer.particles.EffectDef
import com.effectviewer.particles.EffectRegistry
import com.effectviewer.particles.EffectType
import com.effectviewer.particles.ParticleSystem
import com.effectviewer.particles.SpriteDef
import com.effectviewer.particles.SpriteLibrary
import kotlin.math.max

class ParticleView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var emitters: List<EffectEmitter> = emptyList()
    var imageRect: RectF = RectF()

    // ── Rendering V2: ogni particella è un drawBitmap scalato (sprite
    // pre-renderizzato da SpriteLibrary), non un calcolo geometrico per frame.
    private val spriteDst = RectF()
    private val spritePaint = Paint(Paint.FILTER_BITMAP_FLAG)

    // Paint per l'oscuramento BLACKOUT (shader impostato di volta in volta) — invariato.
    private val blackoutPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // Paint per BLACKOUT su forme poligonali (rettangolo/triangolo) — invariato.
    private val blackoutSolidPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.BLACK
    }

    private val handler = Handler(Looper.getMainLooper())
    private var lastTime = System.nanoTime()
    private var running = false

    // Cap particelle letto da AppSettings (cache per non leggere le prefs a ogni frame)
    private var maxParticles = 3000

    private val ticker = object : Runnable {
        override fun run() {
            if (!running) return
            val now = System.nanoTime()
            val dt = ((now - lastTime) / 1_000_000_000f).coerceAtMost(0.05f)
            lastTime = now

            val rect = imageRect
            if (!rect.isEmpty && emitters.isNotEmpty()) {
                for (emitter in emitters) {
                    val screenCx = rect.left + emitter.cx * rect.width()
                    val screenCy = rect.top + emitter.cy * rect.height()
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
        ParticleSystem.ensureRegistryLoaded(context)   // legge effects-v2.json una volta
        running = true
        lastTime = System.nanoTime()
        handler.post(ticker)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        running = false
        handler.removeCallbacks(ticker)
        SpriteLibrary.clear()   // libera i bitmap pre-renderizzati
    }

    override fun onDraw(canvas: Canvas) {
        val rect = imageRect
        if (rect.isEmpty || emitters.isEmpty()) return

        // Clip al rect dell'immagine: niente disegno fuori dai bordi
        canvas.save()
        canvas.clipRect(rect)

        for (emitter in emitters) {
            // BLACKOUT: oscuramento statico, nessuna particella, fuori dal motore V2
            if (emitter.type == EffectType.BLACKOUT) {
                drawBlackout(canvas, emitter, rect)
                continue
            }
            drawEffect(canvas, emitter)
        }
        canvas.restore()
    }

    /**
     * Disegna un emitter generico. Due famiglie di sprite:
     *  - "point" (softdot/glowdot/ring/flake): sprite principale + eventuali
     *    layer secondari, invariato rispetto a prima.
     *  - "line" (es. scariche elettriche): sprite orientato e allungato lungo
     *    lineAngle/lineLength, scelto tra le forme pre-cotte da SpriteLibrary.
     * Nessun ramo per TIPO di effetto in nessuno dei due casi: lo stesso
     * codice serve fuoco, pioggia, scintille, e qualunque effetto futuro
     * della stessa famiglia.
     */
    private fun drawEffect(canvas: Canvas, emitter: EffectEmitter) {
        val def = EffectRegistry.get(emitter.type)
        val list = emitter.particles

        if (def.sprite.kind == "line") {
            val lineSet = SpriteLibrary.getLineSet(def.sprite)
            for (i in list.indices) {
                val p = list[i]
                blitLine(canvas, lineSet, p, def.sprite)
                drawLayers(canvas, def, p, null)
            }
            return
        }

        val mainSprites = SpriteLibrary.get(def.sprite)
        val hasSpin = def.overLife.spin != null
        for (i in list.indices) {
            val p = list[i]
            blit(canvas, mainSprites, p.colorBand, p.x, p.y, p.size, p.alpha, if (hasSpin) p.rotation else 0f)
            drawLayers(canvas, def, p, mainSprites)
        }
    }

    /** Layer secondari (es. flash d'impatto, anello interno) — comuni a entrambe le famiglie di sprite. */
    private fun drawLayers(canvas: Canvas, def: EffectDef, p: Particle, mainSprites: Array<Bitmap>?) {
        val layers = def.layers
        if (layers.isEmpty()) return
        val t = (1f - p.life).coerceIn(0f, 1f)
        for (li in layers.indices) {
            if (li >= p.layerFlags.size || !p.layerFlags[li]) continue
            val L = layers[li]
            if (t < L.window.start || t > L.window.end) continue
            val span = (L.window.end - L.window.start).let { if (it < 1e-6f) 1e-6f else it }
            val lt = ((t - L.window.start) / span).coerceIn(0f, 1f)

            val lSprites = if (L.useMainSprite && mainSprites != null) mainSprites
                else SpriteLibrary.get(L.sprite ?: def.sprite)
            val lSize = L.size?.let { lerp(it.start, it.end, lt) } ?: (p.size * L.scale)
            val lAlpha = L.alpha?.let { lerp(it.start, it.end, lt) } ?: (p.alpha * L.alphaMul)

            blit(canvas, lSprites, p.colorBand, p.x, p.y, lSize, lAlpha, 0f)
        }
    }

    /**
     * Copia lo sprite a linea (già pre-cotto in orizzontale) orientato lungo
     * p.lineAngle e allungato a p.lineLength: uno stretch non uniforme
     * sull'asse X del bitmap canonico, nessun calcolo geometrico per frame.
     * Il fotogramma segue revealFraction(sprite, t) — la stessa formula usata
     * da ParticleSystem per l'alpha "followReveal": crescita/attesa/ritiro
     * restano coerenti per costruzione.
     */
    private fun blitLine(canvas: Canvas, set: SpriteLibrary.LineSpriteSet, p: Particle, sprite: SpriteDef) {
        if (p.alpha <= 0f || p.lineLength <= 0.5f || set.variants.isEmpty()) return
        val frames = set.variants[p.shapeVariant.coerceIn(0, set.variants.size - 1)]
        val t = (1f - p.life).coerceIn(0f, 1f)
        val revealT = revealFraction(sprite, t)
        val frameIdx = (revealT * (frames.size - 1)).toInt().coerceIn(0, frames.size - 1)
        val frame = frames[frameIdx]

        val scaleX = p.lineLength / set.refLen
        val halfW = (set.w / 2f) * scaleX
        val halfH = set.h / 2f

        spritePaint.alpha = (p.alpha.coerceIn(0f, 1f) * 255f).toInt()
        canvas.save()
        canvas.translate(p.x, p.y)
        canvas.rotate(p.lineAngle)
        spriteDst.set(-halfW, -halfH, halfW, halfH)
        canvas.drawBitmap(frame, null, spriteDst, spritePaint)
        canvas.restore()
    }

    /** Copia lo sprite (bitmap pre-renderizzato) scalato al raggio corrente — nessun calcolo geometrico. */
    private fun blit(
        canvas: Canvas,
        sprites: Array<Bitmap>,
        band: Int,
        x: Float, y: Float,
        sizePx: Float, alpha: Float,
        rotationDeg: Float
    ) {
        if (alpha <= 0f || sizePx <= 0.3f || sprites.isEmpty()) return
        val sprite = sprites[band.coerceIn(0, sprites.size - 1)]
        val half = (SpriteLibrary.SPRITE_SIZE / 2f) * (sizePx / SpriteLibrary.SPRITE_R)
        spritePaint.alpha = (alpha.coerceIn(0f, 1f) * 255f).toInt()
        if (rotationDeg != 0f) {
            canvas.save()
            canvas.translate(x, y)
            canvas.rotate(rotationDeg)
            spriteDst.set(-half, -half, half, half)
            canvas.drawBitmap(sprite, null, spriteDst, spritePaint)
            canvas.restore()
        } else {
            spriteDst.set(x - half, y - half, x + half, y + half)
            canvas.drawBitmap(sprite, null, spriteDst, spritePaint)
        }
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t.coerceIn(0f, 1f)

    /**
     * Frazione di percorso "rivelato" (0..1) in funzione di t. Stessa formula
     * di ParticleSystem.revealFraction — duplicata come lerp(), nessuna
     * infrastruttura di condivisione necessaria per una funzione matematica pura.
     */
    private fun revealFraction(sprite: SpriteDef, t: Float): Float {
        val growEnd = sprite.growEnd.coerceIn(0.001f, 1f)
        if (sprite.revealCurve == "growHoldRetract") {
            val retractStart = sprite.retractStart.coerceIn(growEnd, 0.999f)
            return when {
                t < growEnd -> t / growEnd
                t < retractStart -> 1f
                else -> (1f - (t - retractStart) / (1f - retractStart)).coerceAtLeast(0f)
            }
        }
        return (t / growEnd).coerceAtMost(1f)
    }

    /**
     * Disegna l'oscuramento per un emitter BLACKOUT. Invariato rispetto al
     * comportamento esistente, con UNA correzione: per CIRCLE i raggi vengono
     * misurati entrambi sulla stessa base (larghezza), come fa lo spawn delle
     * particelle negli altri effetti — altrimenti su immagini non quadrate un
     * cerchio risultava un'ellisse (bug isolato al blackout, verificato: gli
     * altri effetti con forma CIRCLE sono già tondi perché il loro spawn usa
     * un raggio unico e ignora radiusY).
     */
    private fun drawBlackout(canvas: Canvas, emitter: EffectEmitter, rect: RectF) {
        val cx = rect.left + emitter.cx * rect.width()
        val cy = rect.top + emitter.cy * rect.height()

        val rx: Float
        val ry: Float
        if (emitter.shape == EmitterShape.CIRCLE) {
            val r = emitter.radiusX * rect.width()
            rx = r; ry = r
        } else {
            rx = emitter.radiusX * rect.width()
            ry = emitter.radiusY * rect.height()
        }
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
                val blur = (minOf(rx, ry) * 0.18f).coerceAtLeast(2f)
                blackoutSolidPaint.maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
                canvas.drawRect(-rx + blur, -ry + blur, rx - blur, ry - blur, blackoutSolidPaint)
                blackoutSolidPaint.maskFilter = null
            }
            EmitterShape.TRIANGLE -> {
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
