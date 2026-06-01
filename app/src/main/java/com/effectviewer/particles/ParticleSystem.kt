package com.effectviewer.particles

import android.graphics.Color
import com.effectviewer.model.EffectEmitter
import com.effectviewer.model.EmitterShape
import com.effectviewer.model.Particle
import kotlin.math.*
import kotlin.random.Random

object ParticleSystem {

    /**
     * Densità costante: particelle per unità di area normalizzata.
     * target = DENSITY * (radiusX / REF_RADIUS) * (radiusY / REF_RADIUS) * intensity
     *
     * Per CIRCLE (radiusX == radiusY) è identica alla vecchia formula quadratica.
     * Per forme allungate scala con l'area effettiva del bounding box.
     */
    private const val REF_RADIUS  = 0.08f          // raggio di riferimento
    private const val DENSITY     = 600             // particelle al raggio di riferimento, intensità 100%
    // Tetto di particelle per emitter. 3000 è il limite oltre il quale il
    // rendering su main thread (Handler + Canvas) fa calare gli fps sul
    // proiettore. Le aree molto grandi a intensità alta vanno in cap e quindi
    // risultano un po' più rade: è il compromesso scelto per non perdere fluidità.
    private const val MAX_PARTICLES = 3000

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

        val particles = emitter.particles

        // Area relativa al raggio di riferimento (prodotto dei due semiassi normalizzati)
        val areaRatio = (emitter.radiusX / REF_RADIUS) * (emitter.radiusY / REF_RADIUS)
        val target    = (DENSITY * areaRatio * emitter.intensity).toInt().coerceIn(5, maxParticles)

        // Spawn proporzionale al target: per sostenere `target` particelle vive
        // con una certa vita media, serve un numero di nascite/frame proporzionale
        // al target stesso. Con un valore FISSO gli emettitori grandi non
        // raggiungono mai la densità (le particelle muoiono più in fretta di
        // quanto nascano) e appaiono radi. La frazione per tipo tiene conto della
        // vita media: tipi con vita più breve devono spawnare più in fretta.
        val spawnFraction = when (emitter.type) {
            EffectType.FIRE      -> 0.10f   // vita breve → riempimento rapido
            EffectType.TOXIC_GAS -> 0.05f
            EffectType.EMBERS    -> 0.08f
            EffectType.SMOKE     -> 0.04f   // vita lunga → bastano poche nascite
            EffectType.MAGIC     -> 0.12f
            EffectType.ICE       -> 0.07f
            EffectType.BLACKOUT  -> 0f      // mai raggiunto (return sopra)
        }
        // Almeno 2/frame per non avere riempimenti lentissimi su aree piccole.
        val spawnPerFrame = maxOf(2, (target * spawnFraction).toInt())

        if (particles.size < target) {
            val toSpawn = minOf(spawnPerFrame, target - particles.size)
            repeat(toSpawn) {
                particles.add(spawn(emitter, screenCx, screenCy, screenRX, screenRY))
            }
        }

        // Rimuovi le eccedenti se il target è sceso (es. dopo cambio intensità)
        while (particles.size > target) {
            particles.removeAt(particles.lastIndex)
        }

        val iter = particles.iterator()
        while (iter.hasNext()) {
            val p = iter.next()
            p.life -= dt / p.maxLife
            if (p.life <= 0f) { iter.remove(); continue }

            p.x += p.vx * dt
            p.y += p.vy * dt
            p.rotation += p.rotationSpeed * dt

            when (emitter.type) {
                EffectType.FIRE -> {
                    p.vy -= 60f * dt
                    p.vx += Random.nextFloat() * 10f - 5f
                    p.alpha = p.life * 0.7f
                    p.size  = lerp(4f, 18f, p.life)
                    p.color = fireColor(p.life)
                }
                EffectType.TOXIC_GAS -> {
                    p.vy -= 20f * dt
                    p.vx += (Random.nextFloat() - 0.5f) * 8f * dt
                    p.alpha = p.life * 0.55f
                    p.size  = lerp(8f, 40f, 1f - p.life)
                    p.color = Color.argb(255, 80, 200, 60)
                }
                EffectType.EMBERS -> {
                    p.vy -= 40f * dt
                    p.vx += (Random.nextFloat() - 0.5f) * 20f * dt
                    p.vy += 10f * dt
                    p.alpha = p.life
                    p.size  = lerp(2f, 5f, p.life)
                    p.color = Color.argb(255, 255, (180 + (75 * p.life).toInt()).coerceAtMost(255), 0)
                }
                EffectType.SMOKE -> {
                    p.vy -= 15f * dt
                    p.vx += (Random.nextFloat() - 0.5f) * 6f * dt
                    p.alpha = p.life * 0.4f
                    p.size  = lerp(10f, 55f, 1f - p.life)
                    p.color = Color.argb(255, 160, 160, 160)
                }
                EffectType.MAGIC -> {
                    val angle = p.rotation
                    p.vx    = cos(angle) * 50f
                    p.vy    = sin(angle) * 50f
                    p.alpha = p.life
                    p.size  = lerp(3f, 10f, p.life)
                    p.color = magicColor(p.life)
                }
                EffectType.ICE -> {
                    p.vy -= 25f * dt
                    p.vx += (Random.nextFloat() - 0.5f) * 15f * dt
                    p.alpha = p.life * 0.8f
                    p.size  = lerp(2f, 12f, p.life)
                    p.color = Color.argb(255, 180, 230, 255)
                }
                EffectType.BLACKOUT -> { /* nessuna particella */ }
            }
        }
    }

    /**
     * Genera una particella in una posizione interna alla forma dell'emitter.
     * La posizione locale (origine = centro emitter, forma non ruotata) viene
     * calcolata per ogni shape, poi ruotata di emitter.rotation e traslata al
     * centro schermo. La FISICA della particella resta invariata e in coordinate
     * assolute (la rotazione non tocca le velocità).
     */
    private fun spawn(
        emitter: EffectEmitter,
        cx: Float, cy: Float,
        screenRX: Float, screenRY: Float
    ): Particle {
        // ── Posizione locale nella forma (non ruotata) ──────────────────────
        val (lx, ly) = when (emitter.shape) {
            EmitterShape.CIRCLE -> {
                val angle = Random.nextFloat() * 2f * PI.toFloat()
                val dist  = sqrt(Random.nextFloat()) * screenRX
                Pair(cos(angle) * dist, sin(angle) * dist)
            }
            EmitterShape.ELLIPSE -> {
                val angle = Random.nextFloat() * 2f * PI.toFloat()
                val t     = sqrt(Random.nextFloat())
                Pair(cos(angle) * screenRX * t, sin(angle) * screenRY * t)
            }
            EmitterShape.RECTANGLE -> {
                val rx = (Random.nextFloat() * 2f - 1f) * screenRX
                val ry = (Random.nextFloat() * 2f - 1f) * screenRY
                Pair(rx, ry)
            }
            EmitterShape.TRIANGLE -> {
                // Coordinate baricentriche uniformi su un triangolo isoscele.
                // screenRX = mezza base (orizzontale), screenRY = mezza altezza
                // (verticale): variando i due raggi il triangolo cambia forma
                // (equilatero, alto e stretto, basso e largo...).
                var r1 = Random.nextFloat()
                var r2 = Random.nextFloat()
                if (r1 + r2 > 1f) { r1 = 1f - r1; r2 = 1f - r2 }
                // Vertici: base in basso (±screenRX, +screenRY), apice in alto (0, -screenRY)
                val v0x = -screenRX; val v0y =  screenRY
                val v1x =  screenRX; val v1y =  screenRY
                val v2x =  0f;       val v2y = -screenRY
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

        // ── Spawn della particella (fisica invariata, identica a prima) ──────
        // Per la velocità iniziale di MAGIC serve l'angolo di orbita: lo deriviamo
        // dalla posizione locale per coerenza con il comportamento originale.
        val spawnAngle = atan2(ly, lx)

        return when (emitter.type) {
            EffectType.FIRE -> Particle(px, py,
                vx = (Random.nextFloat() - 0.5f) * 30f,
                vy = -(20f + Random.nextFloat() * 40f),
                life = 1f, maxLife = 0.8f + Random.nextFloat() * 0.6f,
                size = 10f, alpha = 0.8f, color = Color.RED)
            EffectType.TOXIC_GAS -> Particle(px, py,
                vx = (Random.nextFloat() - 0.5f) * 20f,
                vy = -(5f + Random.nextFloat() * 20f),
                life = 1f, maxLife = 1.5f + Random.nextFloat(),
                size = 15f, alpha = 0.5f, color = Color.GREEN)
            EffectType.EMBERS -> Particle(px, py,
                vx = (Random.nextFloat() - 0.5f) * 60f,
                vy = -(50f + Random.nextFloat() * 80f),
                life = 1f, maxLife = 1.0f + Random.nextFloat() * 0.5f,
                size = 4f, alpha = 1f, color = Color.YELLOW)
            EffectType.SMOKE -> Particle(px, py,
                vx = (Random.nextFloat() - 0.5f) * 15f,
                vy = -(8f + Random.nextFloat() * 15f),
                life = 1f, maxLife = 2f + Random.nextFloat(),
                size = 20f, alpha = 0.3f, color = Color.GRAY)
            EffectType.MAGIC -> Particle(px, py,
                vx = 0f, vy = 0f,
                life = 1f, maxLife = 0.6f + Random.nextFloat() * 0.4f,
                size = 6f, alpha = 1f, color = Color.MAGENTA,
                rotation = spawnAngle, rotationSpeed = (Random.nextFloat() - 0.5f) * 4f)
            EffectType.ICE -> Particle(px, py,
                vx = (Random.nextFloat() - 0.5f) * 40f,
                vy = -(15f + Random.nextFloat() * 35f),
                life = 1f, maxLife = 1.0f + Random.nextFloat() * 0.8f,
                size = 7f, alpha = 0.9f, color = Color.CYAN,
                rotation = Random.nextFloat() * 360f,
                rotationSpeed = (Random.nextFloat() - 0.5f) * 180f)
            EffectType.BLACKOUT -> Particle(px, py,
                vx = 0f, vy = 0f, life = 0f, maxLife = 1f,
                size = 0f, alpha = 0f, color = Color.TRANSPARENT)  // mai usata
        }
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t.coerceIn(0f, 1f)

    private fun fireColor(life: Float) = when {
        life > 0.6f -> Color.argb(255, 255, 255, 100)
        life > 0.3f -> Color.argb(255, 255, 140, 0)
        else        -> Color.argb(255, 200, 30, 0)
    }

    private fun magicColor(life: Float): Int {
        val hsv = floatArrayOf((life * 300f) % 360f, 1f, 1f)
        return Color.HSVToColor(hsv)
    }
}
