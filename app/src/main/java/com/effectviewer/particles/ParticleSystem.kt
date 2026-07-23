package com.effectviewer.particles

import android.content.Context
import android.graphics.Color
import com.effectviewer.model.EffectEmitter
import com.effectviewer.model.EmitterShape
import com.effectviewer.model.Particle
import kotlin.math.*
import kotlin.random.Random

/**
 * Motore particellare "Effects V2": spawn/update GENERICI guidati dalle
 * definizioni di EffectRegistry (a loro volta lette da effects-v2.json).
 *
 * Non c'è più un ramo di codice per tipo di effetto: fisica, colore, forma
 * e layer sono tutti parametri. Aggiungere un comportamento nuovo in futuro
 * (es. un campo "wind" in PhysicsDef) significa toccare SOLO questo file,
 * in un punto solo, e diventa disponibile a ogni effetto esistente e futuro
 * senza modificarli.
 *
 * BLACKOUT resta fuori dal motore: nessuna particella, nessuna definizione
 * JSON — il suo rendering è un disegno diretto in ParticleView, invariato.
 */
object ParticleSystem {

    /**
     * Densità costante: particelle per unità di area normalizzata.
     * target = DENSITY * (radiusX / REF_RADIUS) * (radiusY / REF_RADIUS) * intensity
     * Usata quando l'effetto ha emission.mode = DENSITY (il caso comune).
     */
    private const val REF_RADIUS = 0.08f
    private const val DENSITY = 600

    // Tetto di particelle per emitter. 3000 è il limite oltre il quale il
    // rendering su main thread (Handler + Canvas) fa calare gli fps sul
    // proiettore.
    private const val MAX_PARTICLES = 3000

    /** Da chiamare una volta prima del primo update() (es. da ParticleView.onAttachedToWindow). */
    fun ensureRegistryLoaded(context: Context) = EffectRegistry.ensureLoaded(context)

    /**
     * @param screenCx centro X dell'emitter in px schermo
     * @param screenCy centro Y dell'emitter in px schermo
     * @param screenRX semiasse X in px schermo (radiusX * larghezza rect)
     * @param screenRY semiasse Y in px schermo (radiusY * altezza rect)
     */
    fun update(
        emitter: EffectEmitter,
        screenCx: Float,
        screenCy: Float,
        screenRX: Float,
        screenRY: Float,
        dt: Float,
        maxParticles: Int = MAX_PARTICLES
    ) {
        // BLACKOUT non ha particelle: il rendering è gestito da ParticleView.
        if (emitter.type == EffectType.BLACKOUT) {
            if (emitter.particles.isNotEmpty()) emitter.particles.clear()
            return
        }

        val def = EffectRegistry.get(emitter.type)
        val particles = emitter.particles

        val target = targetFor(def, emitter, maxParticles)

        // Spawn proporzionale al target (vedi spawnFraction nella EmissionDef):
        // un valore fisso farebbe apparire radi gli emitter grandi, perché le
        // particelle morirebbero più in fretta di quanto ne nascano.
        val spawnPerFrame = maxOf(2, (target * def.emission.spawnFraction).toInt())
        if (particles.size < target) {
            val toSpawn = minOf(spawnPerFrame, target - particles.size)
            repeat(toSpawn) {
                particles.add(spawn(def, emitter, screenCx, screenCy, screenRX, screenRY))
            }
        }

        // Rimuovi le eccedenti se il target è sceso (es. dopo cambio intensità)
        while (particles.size > target) {
            particles.removeAt(particles.lastIndex)
        }

        val iter = particles.iterator()
        while (iter.hasNext()) {
            val p = iter.next()
            if (!step(def, p, dt, screenCx, screenCy)) iter.remove()
        }
    }

    private fun targetFor(def: EffectDef, emitter: EffectEmitter, maxParticles: Int): Int {
        return if (def.emission.mode == EmissionMode.FIXED) {
            // Conteggio proprio, lineare sull'intensità (es. la pioggia: niente
            // a che vedere con l'area dell'emitter, solo "quanti impatti insieme").
            (def.emission.count * emitter.intensity).toInt().coerceIn(2, maxParticles)
        } else {
            // densityMul (default 1.0): moltiplicatore per-effetto sulla formula
            // condivisa DENSITY*area*intensity — l'analogo di "count" per il modo
            // FIXED, ma relativo anziché assoluto (scala la stessa formula che
            // useresti comunque, non la sostituisce).
            val areaRatio = (emitter.radiusX / REF_RADIUS) * (emitter.radiusY / REF_RADIUS)
            (DENSITY * def.emission.densityMul * areaRatio * emitter.intensity).toInt().coerceIn(5, maxParticles)
        }
    }

    /** Avanza una particella di dt secondi. Ritorna false se è morta (va rimossa dal chiamante). */
    private fun step(def: EffectDef, p: Particle, dt: Float, cx: Float, cy: Float): Boolean {
        p.life -= dt / p.maxLife
        if (p.life <= 0f) return false

        val ph = def.physics
        when {
            ph.still -> { /* ferma: nessuno spostamento (es. gli impatti della pioggia) */ }

            ph.orbit != null -> {
                // Orbita attorno al centro CORRENTE dell'emitter (screenCx/Cy passati
                // dal chiamante ogni frame): se l'emitter si sposta, l'orbita lo segue.
                p.orbitAngle += p.orbitSpeed * dt
                p.x = cx + cos(p.orbitAngle) * p.orbitR
                p.y = cy + sin(p.orbitAngle) * p.orbitR
            }

            else -> {
                if (ph.jitterX != 0f) p.vx += (Random.nextFloat() * 2f - 1f) * ph.jitterX
                if (ph.jitterY != 0f) p.vy += (Random.nextFloat() * 2f - 1f) * ph.jitterY
                p.vx += ph.ax * dt
                p.vy += ph.ay * dt
                if (ph.damp != 0f) p.vx *= (1f - ph.damp * dt).coerceIn(0f, 1f)
                p.x += p.vx * dt
                p.y += p.vy * dt
            }
        }

        // ── Aspetto nel tempo (overLife), generico per qualunque effetto ──
        val t = (1f - p.life).coerceIn(0f, 1f)
        val ol = def.overLife
        val sizeT = if (ol.sizeCurve == SizeCurve.EASE_OUT) 1f - (1f - t) * (1f - t) else t
        p.size = lerp(ol.size.start, ol.size.end, sizeT)
        p.alpha = when (ol.alphaCurve) {
            AlphaCurve.LINEAR -> lerp(ol.alpha.start, ol.alpha.end, t)
            AlphaCurve.FLASH -> {
                // Si accende da 0 fino a flashPeakAt, poi si spegne nel resto
                // della vita — indipendente dal reveal della linea.
                val peak = ol.flashPeakAt.coerceIn(0.01f, 0.99f)
                if (t < peak) lerp(0f, ol.alpha.start, t / peak)
                else lerp(ol.alpha.start, ol.alpha.end, (t - peak) / (1f - peak))
            }
            AlphaCurve.FOLLOW_REVEAL -> {
                // L'opacità rispecchia quanto percorso è visibile — per la
                // famiglia "line" quando il reveal stesso torna a 0 (es. le
                // Radici che si ritirano): non ha senso sfumare per conto
                // proprio, deve "spegnersi" insieme al percorso che si accorcia.
                val rf = if (def.sprite.kind == "line") revealFraction(def.sprite, t) else 1f
                lerp(ol.alpha.end, ol.alpha.start, rf)
            }
        }
        p.colorBand = SpriteLibrary.colorIndex(def.sprite, p.life)
        if (ol.spin != null) p.rotation += p.rotationSpeed * dt

        return true
    }

    /**
     * Genera una particella in una posizione interna alla forma dell'emitter
     * (invariato rispetto al motore precedente: la geometria delle forme non
     * dipende dal tipo di effetto). Fisica, vita e aspetto iniziale vengono
     * invece dalla EffectDef.
     */
    private fun spawn(
        def: EffectDef,
        emitter: EffectEmitter,
        cx: Float, cy: Float,
        screenRX: Float, screenRY: Float
    ): Particle {
        // ── Posizione locale nella forma (non ruotata) ──────────────────────
        val (lx, ly) = when (emitter.shape) {
            EmitterShape.CIRCLE -> {
                val angle = Random.nextFloat() * 2f * PI.toFloat()
                val dist = sqrt(Random.nextFloat()) * screenRX
                Pair(cos(angle) * dist, sin(angle) * dist)
            }
            EmitterShape.ELLIPSE -> {
                val angle = Random.nextFloat() * 2f * PI.toFloat()
                val t = sqrt(Random.nextFloat())
                Pair(cos(angle) * screenRX * t, sin(angle) * screenRY * t)
            }
            EmitterShape.RECTANGLE -> {
                val rx = (Random.nextFloat() * 2f - 1f) * screenRX
                val ry = (Random.nextFloat() * 2f - 1f) * screenRY
                Pair(rx, ry)
            }
            EmitterShape.TRIANGLE -> {
                var r1 = Random.nextFloat()
                var r2 = Random.nextFloat()
                if (r1 + r2 > 1f) { r1 = 1f - r1; r2 = 1f - r2 }
                val v0x = -screenRX; val v0y = screenRY
                val v1x = screenRX; val v1y = screenRY
                val v2x = 0f; val v2y = -screenRY
                Pair(
                    v0x + r1 * (v1x - v0x) + r2 * (v2x - v0x),
                    v0y + r1 * (v1y - v0y) + r2 * (v2y - v0y)
                )
            }
        }

        // ── Rotazione emitter + traslazione al centro schermo ───────────────
        val px: Float
        val py: Float
        if (emitter.rotation != 0f && emitter.shape != EmitterShape.CIRCLE) {
            val rad = Math.toRadians(emitter.rotation.toDouble())
            val c = cos(rad).toFloat()
            val s = sin(rad).toFloat()
            px = cx + lx * c - ly * s
            py = cy + lx * s + ly * c
        } else {
            px = cx + lx
            py = cy + ly
        }

        // ── Fisica iniziale guidata dalla definizione ────────────────────────
        val ph = def.physics
        val spawnAngle = atan2(ly, lx)
        val dist = hypot(lx, ly)

        var vx = 0f; var vy = 0f
        var orbitR = 0f; var orbitAngle = 0f; var orbitSpeed = 0f
        when {
            ph.orbit != null -> {
                orbitR = dist
                orbitAngle = spawnAngle
                orbitSpeed = randRange(ph.orbit.speed)
            }
            ph.still -> { /* vx=vy=0, già impostati sopra */ }
            else -> {
                vx = randRange(ph.vx0)
                vy = randRange(ph.vy0)
            }
        }

        val ol = def.overLife
        val spinSpeed = ol.spin?.let { randRange(it) } ?: 0f

        val layerFlags = BooleanArray(def.layers.size) { i ->
            Random.nextFloat() < def.layers[i].probability
        }

        // ── Campi per la famiglia "line" (es. scariche elettriche) ──────────
        // Decisi una volta alla nascita, come il flag dell'anello doppio della
        // pioggia: la forma e l'orientamento di una scarica non cambiano mai
        // durante la sua vita, solo il fotogramma di reveal mostrato (calcolato
        // in ParticleView da p.life, non serve memorizzarlo).
        val isLine = def.sprite.kind == "line"
        val lineLen = if (isLine) randRange(def.sprite.lineLength) else 0f
        val lineAng = if (isLine) Random.nextFloat() * 360f else 0f
        val shapeVar = if (isLine) Random.nextInt(def.sprite.lineVariants.coerceAtLeast(1)) else 0

        return Particle(
            px, py, vx, vy,
            life = 1f,
            maxLife = randRange(ph.life),
            size = ol.size.start,
            alpha = ol.alpha.start,
            color = Color.WHITE, // non letto dal draw V2 (si usa colorBand + SpriteLibrary)
            rotation = 0f,
            rotationSpeed = spinSpeed,
            orbitR = orbitR, orbitAngle = orbitAngle, orbitSpeed = orbitSpeed,
            colorBand = SpriteLibrary.colorIndex(def.sprite, 1f),
            layerFlags = layerFlags,
            lineLength = lineLen, lineAngle = lineAng, shapeVariant = shapeVar
        )
    }

    private fun randRange(r: FloatRange): Float = r.min + Random.nextFloat() * (r.max - r.min)
    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t.coerceIn(0f, 1f)

    /**
     * Frazione di percorso "rivelato" (0..1) in funzione di t (0=nascita, 1=morte).
     * Condivisa concettualmente con ParticleView.blitLine (stessa formula, duplicata
     * come lerp() — nessuna infrastruttura di condivisione necessaria per math pura).
     *  - "grow" (default): sale fino a growEnd, poi resta a 1 per il resto della vita.
     *  - "growHoldRetract": sale fino a growEnd, resta a 1 fino a retractStart, poi
     *    ridiscende a 0 — i fotogrammi pre-cotti sono un flipbook monotono, quindi
     *    la "ritirata" è gratis: si sceglie solo un indice di fotogramma più basso.
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
}
