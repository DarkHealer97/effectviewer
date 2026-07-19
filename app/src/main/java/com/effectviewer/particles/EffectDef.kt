package com.effectviewer.particles

/**
 * Definizioni "a dati" del motore Effects V2. Ogni istanza di queste classi
 * rispecchia esattamente lo schema di effects-v2.json (stesso file letto dal
 * sandbox HTML e da questo motore Kotlin — è l'unica fonte di verità).
 *
 * Principio di espandibilità: ogni campo ha un default neutro (vedi i
 * companion "default*"). Una definizione che non specifica un campo si
 * comporta come se non esistesse: aggiungere un parametro nuovo in futuro
 * non rompe mai le definizioni esistenti, in Kotlin come nel JSON.
 */

/** Intervallo [min,max] per valori randomizzati alla nascita. */
data class FloatRange(val min: Float, val max: Float) {
    companion object { val ZERO = FloatRange(0f, 0f) }
}

/** Curva start→end nel corso della vita della particella (t: 0=nascita, 1=morte). */
data class OverLifeRange(val start: Float, val end: Float)

enum class SizeCurve { LINEAR, EASE_OUT }

data class ColorModeDef(
    val steps: Int = 12,
    val span: Float = 300f
)

data class SpriteDef(
    /**
     * Famiglia dello sprite. "point" (default): forma puntuale che si espande
     * da un centro — softdot/glowdot/ring/flake, tutti gli effetti attuali.
     * "line": forma a due estremi con orientamento e lunghezza proprie (es.
     * scariche elettriche) — pre-cotta come flipbook di reveal, vedi i campi
     * line* sotto. I campi line* sono ignorati per kind="point".
     */
    val kind: String = "point",
    val shape: String,                 // point: softdot|glowdot|ring|flake — line: solo etichetta (es. "bolt")
    val blur: Float = 0f,
    val palette: List<Triple<Int, Int, Int>> = listOf(Triple(255, 255, 255)),
    val bands: List<Float>? = null,    // soglie di vita decrescenti per scegliere il colore
    val colorMode: ColorModeDef? = null,

    // ── Solo per kind = "line" ──────────────────────────────────────────
    val lineVariants: Int = 4,         // quante forme distinte pre-cuocere (scelta fissa alla nascita)
    val lineFrames: Int = 6,           // fotogrammi del flipbook di reveal (0%→100% del percorso)
    val lineRoughness: Float = 0.22f,  // ampiezza dello zigzag (midpoint displacement)
    val lineBranches: Boolean = false, // 1-2 diramazioni procedurali sul percorso principale
    val lineLength: FloatRange = FloatRange(50f, 50f), // lunghezza (px) da cui ogni particella pesca alla nascita
    val lineThickness: Float = 2f      // spessore del tratto nitido (px)
) {
    /** Chiave stabile per la cache degli sprite: due SpriteDef "uguali" producono la stessa chiave. */
    fun cacheKey(): String = buildString {
        append(kind).append('|').append(shape).append('|').append(blur).append('|')
        append(palette.joinToString(",") { "${it.first}.${it.second}.${it.third}" }).append('|')
        append(bands?.joinToString(",") ?: "-").append('|')
        append(colorMode?.let { "${it.steps}.${it.span}" } ?: "-")
        if (kind == "line") {
            append('|').append(lineVariants).append('|').append(lineFrames).append('|')
            append(lineRoughness).append('|').append(lineBranches).append('|')
            append(lineLength.min).append('.').append(lineLength.max).append('|').append(lineThickness)
        }
    }
}

data class OrbitDef(val speed: FloatRange)

data class PhysicsDef(
    val still: Boolean = false,
    val vx0: FloatRange = FloatRange.ZERO,
    val vy0: FloatRange = FloatRange.ZERO,
    val ax: Float = 0f,
    val ay: Float = 0f,
    val jitterX: Float = 0f,
    val jitterY: Float = 0f,
    val damp: Float = 0f,
    val orbit: OrbitDef? = null,
    val life: FloatRange = FloatRange(1.0f, 1.5f)
)

enum class AlphaCurve { LINEAR, FLASH }

data class OverLifeDef(
    val size: OverLifeRange = OverLifeRange(8f, 8f),
    val sizeCurve: SizeCurve = SizeCurve.LINEAR,
    val alpha: OverLifeRange = OverLifeRange(1f, 0f),
    /**
     * LINEAR (default): alpha = lerp(alpha.start, alpha.end, t) — comportamento
     * di sempre. FLASH: sale da 0 al valore alpha.start durante il primo
     * flashPeakAt della vita (si "accende"), poi scende ad alpha.end nel resto
     * (si spegne). Generico: qualunque effetto puntuale può usarlo, non solo
     * gli sprite a linea.
     */
    val alphaCurve: AlphaCurve = AlphaCurve.LINEAR,
    val flashPeakAt: Float = 0.45f,    // frazione di vita in cui il flash (o il reveal della linea) è al culmine
    val spin: FloatRange? = null       // gradi/secondo, se presente lo sprite ruota
)

enum class EmissionMode { DENSITY, FIXED }

data class EmissionDef(
    val mode: EmissionMode = EmissionMode.DENSITY,
    val spawnFraction: Float = 0.08f,
    val count: Int = 100,               // usato solo con mode = FIXED
    val densityMul: Float = 1.0f        // usato solo con mode = DENSITY: moltiplica la formula ad area
)

/**
 * Layer secondario disegnato sopra lo sprite principale (es. il flash
 * d'impatto della pioggia, o un anello interno). `useMainSprite = true`
 * corrisponde al sentinel JSON "sprite": "primary".
 */
data class LayerDef(
    val sprite: SpriteDef?,
    val useMainSprite: Boolean = false,
    val window: OverLifeRange = OverLifeRange(0f, 1f),
    val size: OverLifeRange? = null,    // se null, deriva da scale * dimensione principale
    val alpha: OverLifeRange? = null,   // se null, deriva da alphaMul * alpha principale
    val scale: Float = 1f,
    val alphaMul: Float = 1f,
    val probability: Float = 1f         // frazione di particelle che possiede questo layer
)

data class EffectDef(
    val name: String,
    val emoji: String,
    val sprite: SpriteDef,
    val emission: EmissionDef = EmissionDef(),
    val physics: PhysicsDef = PhysicsDef(),
    val overLife: OverLifeDef = OverLifeDef(),
    val layers: List<LayerDef> = emptyList()
)
