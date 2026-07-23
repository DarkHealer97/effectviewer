package com.effectviewer.particles

import android.graphics.*
import kotlin.math.min
import kotlin.math.hypot
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI
import kotlin.random.Random

/**
 * Cache di sprite pre-renderizzati. Ogni sprite (con il suo eventuale blur)
 * viene disegnato UNA VOLTA in un Bitmap piccolo; a runtime ogni particella è
 * un semplice drawBitmap scalato — una copia di memoria, non un calcolo
 * geometrico. È la stessa tecnica validata sulla pioggia: elimina il costo
 * del BlurMaskFilter per-particella-per-frame che affossava gli fps.
 *
 * Port diretto della sprite library del sandbox HTML: stesse 4 forme
 * (softdot, glowdot, ring, flake), stessa dimensione di riferimento.
 */
object SpriteLibrary {
    const val SPRITE_SIZE = 64
    const val SPRITE_R = 20f   // raggio di riferimento della forma nello sprite

    private val cache = mutableMapOf<String, Array<Bitmap>>()

    /** Restituisce le varianti colore (una per bitmap) per uno SpriteDef, creandole se necessario. */
    fun get(def: SpriteDef): Array<Bitmap> {
        val key = def.cacheKey()
        cache[key]?.let { return it }
        val colors = colorVariants(def)
        val arr = Array(colors.size) { i -> render(def, colors[i]) }
        cache[key] = arr
        return arr
    }

    /** Indice della variante colore da usare per una data fase di vita (life: 1=appena nata, 0=morta). */
    fun colorIndex(def: SpriteDef, life: Float): Int {
        def.colorMode?.let { cm ->
            val n = cm.steps.coerceAtLeast(1)
            val t = (1f - life).coerceIn(0f, 1f)
            return (t * n).toInt().coerceIn(0, n - 1)
        }
        def.bands?.let { bands ->
            for (i in bands.indices) if (life > bands[i]) return i
            return bands.size
        }
        return 0
    }

    /** Libera tutti i bitmap cacheati (da chiamare quando la view si stacca dalla finestra). */
    fun clear() {
        for (arr in cache.values) for (b in arr) b.recycle()
        cache.clear()
        for (set in lineCache.values) for (variant in set.variants) for (b in variant) b.recycle()
        lineCache.clear()
    }

    // ── Famiglia "line" (es. scariche elettriche, radici) ───────────────
    // Percorso generato una volta per midpoint displacement, poi pre-cotto
    // in un flipbook (lineFrames fotogrammi, 0%→100% del percorso disegnato).
    // A runtime si sceglie solo QUALE variante e QUALE fotogramma mostrare:
    // zero calcoli geometrici per frame, solo drawBitmap orientato e allungato.

    /** Un set completo per una SpriteDef "line": [variante][fotogramma] + le dimensioni per il blit. */
    class LineSpriteSet(val variants: Array<Array<Bitmap>>, val w: Int, val h: Int, val refLen: Float)

    private val lineCache = mutableMapOf<String, LineSpriteSet>()

    /** Config diramazioni raggruppata: ogni default riproduce le costanti hardcoded originali. */
    private class BranchConfig(
        val count: FloatRange, val start: FloatRange, val lengthFrac: FloatRange,
        val angle: FloatRange, val roughnessMul: Float
    )

    fun getLineSet(def: SpriteDef): LineSpriteSet {
        val key = def.cacheKey()
        lineCache[key]?.let { return it }
        val refLen = (def.lineLength.min + def.lineLength.max) / 2f
        val rgb = def.palette.firstOrNull() ?: Triple(230, 245, 255)
        val glowBlur = if (def.blur > 0f) def.blur else 3f
        val branchCfg = BranchConfig(
            def.branchCount, def.branchStart, def.branchLengthFrac, def.branchAngle, def.branchRoughnessMul
        )
        var w = 1; var h = 1
        val variants = Array(def.lineVariants.coerceAtLeast(1)) {
            val baked = bakeShapeFrames(
                refLen, def.lineRoughness, def.lineBranches,
                def.lineFrames.coerceAtLeast(2), def.lineThickness, rgb, glowBlur,
                def.leafChance, def.leafColor, def.leafSize, branchCfg
            )
            w = baked.second; h = baked.third
            baked.first
        }
        val set = LineSpriteSet(variants, w, h, refLen)
        lineCache[key] = set
        return set
    }

    // ── Generazione procedurale del percorso (midpoint displacement) ────

    private fun generatePath(len: Float, roughness: Float, passes: Int): List<Pair<Float, Float>> {
        var pts: List<Pair<Float, Float>> = listOf(0f to 0f, len to 0f)
        var disp = len * roughness
        repeat(passes) {
            val next = mutableListOf(pts[0])
            for (i in 0 until pts.size - 1) {
                val (ax, ay) = pts[i]
                val (bx, by) = pts[i + 1]
                val mx = (ax + bx) / 2f
                val my = (ay + by) / 2f + (Random.nextFloat() * 2f - 1f) * disp
                next.add(mx to my)
                next.add(bx to by)
            }
            pts = next
            disp *= 0.5f
        }
        return pts
    }

    private fun polylineLen(pts: List<Pair<Float, Float>>): Pair<List<Float>, Float> {
        val segs = mutableListOf<Float>()
        var total = 0f
        for (i in 0 until pts.size - 1) {
            val d = hypot(pts[i + 1].first - pts[i].first, pts[i + 1].second - pts[i].second)
            segs.add(d)
            total += d
        }
        return segs to total
    }

    /** Disegna la polilinea troncata alla frazione `frac` (0..1) della sua lunghezza totale. */
    private fun drawPartial(
        canvas: Canvas, pts: List<Pair<Float, Float>>, segs: List<Float>, total: Float,
        frac: Float, paint: Paint
    ) {
        if (frac <= 0f) return
        var remain = total * min(1f, frac)
        val path = Path()
        path.moveTo(pts[0].first, pts[0].second)
        for (i in segs.indices) {
            if (remain <= 0f) break
            if (segs[i] <= remain) {
                path.lineTo(pts[i + 1].first, pts[i + 1].second)
                remain -= segs[i]
            } else {
                val t = remain / segs[i]
                path.lineTo(
                    pts[i].first + (pts[i + 1].first - pts[i].first) * t,
                    pts[i].second + (pts[i + 1].second - pts[i].second) * t
                )
                remain = 0f
            }
        }
        canvas.drawPath(path, paint)
    }

    /** Punto (e direzione locale) sul percorso a una data frazione della sua lunghezza — riusato da diramazioni e foglie. */
    private class PathPoint(val x: Float, val y: Float, val dirX: Float, val dirY: Float)

    private fun pointAtFraction(pts: List<Pair<Float, Float>>, segs: List<Float>, total: Float, frac: Float): PathPoint {
        var remain = total * frac
        var x = pts[0].first; var y = pts[0].second
        var dirX = 1f; var dirY = 0f
        for (idx in segs.indices) {
            if (segs[idx] >= remain) {
                val t = remain / segs[idx]
                x = pts[idx].first + (pts[idx + 1].first - pts[idx].first) * t
                y = pts[idx].second + (pts[idx + 1].second - pts[idx].second) * t
                val dx = pts[idx + 1].first - pts[idx].first
                val dy = pts[idx + 1].second - pts[idx].second
                val dl = hypot(dx, dy).let { if (it == 0f) 1f else it }
                dirX = dx / dl; dirY = dy / dl
                break
            }
            remain -= segs[idx]
        }
        return PathPoint(x, y, dirX, dirY)
    }

    /** Una diramazione: il proprio percorso + a quale frazione del percorso principale si innesca. */
    private class BranchPath(
        val pts: List<Pair<Float, Float>>, val segs: List<Float>, val total: Float, val startFrac: Float
    )

    /** Una foglia: posizione, angolo di rotazione dello sprite, e a quale frazione del reveal compare. */
    private class Leaf(val x: Float, val y: Float, val startFrac: Float, val angleDeg: Float)

    private class LineShape(
        val main: List<Pair<Float, Float>>, val mainSegs: List<Float>, val mainTotal: Float,
        val branches: List<BranchPath>, val leaves: List<Leaf>
    )

    private fun randInRange(range: FloatRange): Float = range.min + Random.nextFloat() * (range.max - range.min)

    /**
     * Costruisce una forma completa: percorso principale (midpoint displacement)
     * + eventuali diramazioni + eventuali foglie. Ogni diramazione parte da un
     * punto del percorso principale con la propria startFrac: compare solo
     * quando il reveal del ramo principale l'ha superata. Ogni candidato foglia
     * ha probabilità leafChance di comparire, decisa una volta in fase di baking.
     */
    private fun buildShape(
        len: Float, roughness: Float, withBranches: Boolean,
        leafChance: Float, leafSize: Float, branchCfg: BranchConfig
    ): LineShape {
        val main = generatePath(len, roughness, 4)
        val (mainSegs, mainTotal) = polylineLen(main)
        val branches = mutableListOf<BranchPath>()
        if (withBranches) {
            val n = randInRange(branchCfg.count).let { Math.round(it) }
            repeat(n) {
                val startFrac = randInRange(branchCfg.start)
                val pt = pointAtFraction(main, mainSegs, mainTotal, startFrac)
                val branchLen = len * randInRange(branchCfg.lengthFrac)
                val angleDeg = (if (Random.nextFloat() < 0.5f) -1f else 1f) * randInRange(branchCfg.angle)
                val angle = angleDeg * (PI.toFloat() / 180f)
                val localPath = generatePath(branchLen, roughness * branchCfg.roughnessMul, 3)
                val c = cos(angle)
                val s = sin(angle)
                val rotated = localPath.map { (x, y) -> (pt.x + x * c - y * s) to (pt.y + x * s + y * c) }
                val (bsegs, btotal) = polylineLen(rotated)
                branches.add(BranchPath(rotated, bsegs, btotal, startFrac))
            }
        }
        val leaves = mutableListOf<Leaf>()
        if (leafChance > 0f) {
            val candidateFracs = listOf(
                0.3f + Random.nextFloat() * 0.15f,
                0.5f + Random.nextFloat() * 0.15f,
                0.72f + Random.nextFloat() * 0.15f
            )
            for (frac in candidateFracs) {
                if (Random.nextFloat() >= leafChance) continue
                val pt = pointAtFraction(main, mainSegs, mainTotal, min(0.97f, frac))
                val side = if (Random.nextFloat() < 0.5f) -1f else 1f
                // Perpendicolare alla direzione locale del percorso, scostamento
                // proporzionale a leafSize: una foglia più grande deve sporgere
                // di più per non sovrapporsi al tratto.
                val off = leafSize * (1.0f + Random.nextFloat() * 0.75f)
                val px = pt.x - pt.dirY * side * off
                val py = pt.y + pt.dirX * side * off
                leaves.add(Leaf(px, py, frac, Random.nextFloat() * 360f))
            }
        }
        return LineShape(main, mainSegs, mainTotal, branches, leaves)
    }

    private const val LINE_PAD = 26f

    /**
     * Pre-cuoce UNA forma in `frameCount` fotogrammi (0%→100% del percorso
     * disegnato). L'altezza del canvas è dinamica: budget per il displacement
     * massimo del midpoint (scala con len*roughness), lo spessore del tratto,
     * le foglie, e la portata massima di una diramazione (lunghezza × seno
     * dell'angolo, caso peggiore) — un valore fisso andava bene solo per le
     * Scintille; con parametri diversi (percorsi più lunghi, diramazioni più
     * larghe, es. le Radici) rischierebbe di tagliare rami/foglie ai bordi.
     */
    private fun bakeShapeFrames(
        len: Float, roughness: Float, withBranches: Boolean,
        frameCount: Int, thickness: Float, rgb: Triple<Int, Int, Int>, glowBlur: Float,
        leafChance: Float, leafColor: Triple<Int, Int, Int>, leafSize: Float, branchCfg: BranchConfig
    ): Triple<Array<Bitmap>, Int, Int> {
        val shape = buildShape(len, roughness, withBranches, leafChance, leafSize, branchCfg)

        val maxDisp = len * roughness * 2f
        val leafMargin = if (leafChance > 0f) leafSize * 3f else 0f
        val branchReach = if (withBranches) {
            val maxLenFrac = branchCfg.lengthFrac.max
            val maxAngleDeg = branchCfg.angle.max.coerceAtMost(90f)
            len * maxLenFrac * sin(maxAngleDeg * (PI.toFloat() / 180f))
        } else 0f
        val h = (LINE_PAD * 2f + max(40f, maxDisp * 2f + thickness * 3f + leafMargin + branchReach * 2f)).toInt()
        val w = (len + LINE_PAD * 2).toInt().coerceAtLeast(1)
        val oy = h / 2f
        val (r, g, b) = rgb
        val (lr, lg, lb) = leafColor

        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = Color.argb(140, r, g, b)
            strokeWidth = thickness * 2.2f
            maskFilter = BlurMaskFilter(glowBlur, BlurMaskFilter.Blur.NORMAL)
        }
        val sharpPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = Color.argb(240, min(255, r + 20), min(255, g + 20), min(255, b + 10))
            strokeWidth = thickness * 0.8f
        }
        val leafPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.argb(230, lr, lg, lb)
            maskFilter = BlurMaskFilter(max(0.5f, glowBlur * 0.4f), BlurMaskFilter.Blur.NORMAL)
        }

        val frames = Array(frameCount) { f ->
            val frac = f.toFloat() / (frameCount - 1).coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.translate(LINE_PAD, oy)

            drawPartial(canvas, shape.main, shape.mainSegs, shape.mainTotal, frac, glowPaint)
            for (br in shape.branches) {
                val lf = ((frac - br.startFrac) / (1f - br.startFrac).coerceAtLeast(0.001f)).coerceIn(0f, 1f)
                if (lf > 0f) drawPartial(canvas, br.pts, br.segs, br.total, lf, glowPaint)
            }

            drawPartial(canvas, shape.main, shape.mainSegs, shape.mainTotal, frac, sharpPaint)
            for (br in shape.branches) {
                val lf = ((frac - br.startFrac) / (1f - br.startFrac).coerceAtLeast(0.001f)).coerceIn(0f, 1f)
                if (lf > 0f) drawPartial(canvas, br.pts, br.segs, br.total, lf, sharpPaint)
            }

            // Foglie: compaiono quando il reveal ha raggiunto il loro punto
            // d'innesto, spariscono da sole durante la ritirata (a un indice
            // di fotogramma più basso, semplicemente non sono ancora disegnate).
            for (leaf in shape.leaves) {
                if (frac < leaf.startFrac) continue
                canvas.save()
                canvas.translate(leaf.x, leaf.y)
                canvas.rotate(leaf.angleDeg)
                canvas.scale(1f, 0.6f)   // ellisse = cerchio schiacciato su un asse
                canvas.drawCircle(0f, 0f, leafSize, leafPaint)
                canvas.restore()
            }
            bmp
        }
        return Triple(frames, w, h)
    }

    // ── Costruzione colori ───────────────────────────────────────────────

    private fun colorVariants(def: SpriteDef): List<Triple<Int, Int, Int>> {
        def.colorMode?.let { cm ->
            val n = cm.steps.coerceAtLeast(1)
            return (0 until n).map { i -> hsvToRgb((i * cm.span / n) % 360f) }
        }
        return def.palette.ifEmpty { listOf(Triple(255, 255, 255)) }
    }

    private fun hsvToRgb(hue: Float): Triple<Int, Int, Int> {
        val hsv = floatArrayOf(hue, 1f, 1f)
        val c = Color.HSVToColor(hsv)
        return Triple(Color.red(c), Color.green(c), Color.blue(c))
    }

    // ── Rendering (pagato UNA volta per variante) ───────────────────────

    private fun render(def: SpriteDef, rgb: Triple<Int, Int, Int>): Bitmap {
        val bmp = Bitmap.createBitmap(SPRITE_SIZE, SPRITE_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val c = SPRITE_SIZE / 2f
        val (r, g, b) = rgb

        when (def.shape) {
            "softdot" -> {
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    color = Color.rgb(r, g, b)
                    if (def.blur > 0f) maskFilter = BlurMaskFilter(def.blur, BlurMaskFilter.Blur.NORMAL)
                }
                canvas.drawCircle(c, c, SPRITE_R, p)
            }
            "glowdot" -> {
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = RadialGradient(
                        c, c, SPRITE_R + 8f,
                        intArrayOf(
                            Color.argb(255, r, g, b),
                            Color.argb(128, r, g, b),
                            Color.argb(0, r, g, b)
                        ),
                        floatArrayOf(0f, 0.4f, 1f),
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawCircle(c, c, SPRITE_R + 8f, p)
            }
            "ring" -> {
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                }
                // Alone morbido (blur vero, pagato solo qui)
                p.strokeWidth = 5f
                p.maskFilter = BlurMaskFilter(max(def.blur, 4f), BlurMaskFilter.Blur.NORMAL)
                p.color = Color.argb(110, r, g, b)
                canvas.drawCircle(c, c, SPRITE_R, p)
                // Linea nitida sopra
                p.maskFilter = null
                p.strokeWidth = 2f
                p.color = Color.argb(255, min(255, r + 40), min(255, g + 25), min(255, b + 15))
                canvas.drawCircle(c, c, SPRITE_R, p)
            }
            "flake" -> {
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    strokeWidth = 3f
                    color = Color.rgb(r, g, b)
                    if (def.blur > 0f) maskFilter = BlurMaskFilter(def.blur, BlurMaskFilter.Blur.NORMAL)
                }
                for (i in 0 until 6) {
                    val a = i * Math.PI / 3.0
                    canvas.drawLine(
                        c, c,
                        c + (Math.cos(a) * SPRITE_R).toFloat(),
                        c + (Math.sin(a) * SPRITE_R).toFloat(),
                        p
                    )
                }
            }
            else -> {
                // Forma sconosciuta: pallino pieno di sicurezza, non un crash.
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(r, g, b) }
                canvas.drawCircle(c, c, SPRITE_R, p)
            }
        }
        return bmp
    }

    private fun max(a: Float, b: Float) = if (a > b) a else b
}
