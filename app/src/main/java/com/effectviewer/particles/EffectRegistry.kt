package com.effectviewer.particles

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Carica e interpreta effects-v2.json dagli assets dell'app. È l'UNICA fonte
 * di verità degli effetti: lo stesso file, con la stessa struttura, viene
 * interpretato dal sandbox HTML in fase di progettazione.
 *
 * Robustezza: se il file manca, è malformato, o una definizione è incompleta,
 * l'app NON deve crashare — usa un fallback neutro (pallino bianco morbido) e
 * scrive un warning in Logcat. Progetto di un solo sviluppatore alle prime
 * armi: un JSON scritto a mano che rompe l'app è uno scenario da prevenire,
 * non da lasciar succedere.
 */
object EffectRegistry {
    private const val TAG = "EffectRegistry"
    private const val ASSET_NAME = "effects-v2.json"

    private val defs = mutableMapOf<EffectType, EffectDef>()
    private var loaded = false

    /** Chiamato una volta (tipicamente da ParticleView.onAttachedToWindow). No-op se già caricato. */
    fun ensureLoaded(context: Context) {
        if (loaded) return
        loaded = true
        try {
            val json = context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
            val root = JSONObject(json)
            val effects = root.getJSONObject("effects")
            for (type in EffectType.values()) {
                if (type == EffectType.BLACKOUT) continue // fuori dal motore, resta draw diretto
                val key = type.name.lowercase()
                defs[type] = if (effects.has(key)) {
                    try {
                        parseEffectDef(effects.getJSONObject(key))
                    } catch (e: Exception) {
                        Log.w(TAG, "Definizione \"$key\" malformata, uso fallback", e)
                        fallback(key)
                    }
                } else {
                    Log.w(TAG, "Nessuna definizione per \"$key\" in $ASSET_NAME, uso fallback")
                    fallback(key)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Impossibile leggere $ASSET_NAME, tutti gli effetti usano il fallback", e)
            for (type in EffectType.values()) {
                if (type != EffectType.BLACKOUT) defs[type] = fallback(type.name.lowercase())
            }
        }
    }

    fun get(type: EffectType): EffectDef = defs[type] ?: fallback(type.name.lowercase())

    /** Ricarica da zero (utile se in futuro l'editor in-app modifica il file a runtime). */
    fun reload(context: Context) {
        loaded = false
        defs.clear()
        ensureLoaded(context)
    }

    // ── Parsing ──────────────────────────────────────────────────────────

    private fun parseEffectDef(o: JSONObject): EffectDef {
        val d = defaultEmission()
        return EffectDef(
            name = o.optString("name", "?"),
            emoji = o.optString("emoji", ""),
            sprite = parseSprite(o.getJSONObject("sprite")),
            emission = if (o.has("emission")) parseEmission(o.getJSONObject("emission")) else d,
            physics = if (o.has("physics")) parsePhysics(o.getJSONObject("physics")) else PhysicsDef(),
            overLife = if (o.has("overLife")) parseOverLife(o.getJSONObject("overLife")) else OverLifeDef(),
            layers = if (o.has("layers")) parseLayers(o.getJSONArray("layers")) else emptyList()
        )
    }

    private fun parseSprite(o: JSONObject): SpriteDef {
        val palette = if (o.has("palette")) {
            val arr = o.getJSONArray("palette")
            (0 until arr.length()).map { i ->
                val c = arr.getJSONArray(i)
                Triple(c.getInt(0), c.getInt(1), c.getInt(2))
            }
        } else listOf(Triple(255, 255, 255))

        val bands = if (o.has("bands")) {
            val arr = o.getJSONArray("bands")
            (0 until arr.length()).map { arr.getDouble(it).toFloat() }
        } else null

        val colorMode = if (o.has("colorMode")) {
            val cm = o.getJSONObject("colorMode")
            ColorModeDef(
                steps = cm.optInt("steps", 12),
                span = cm.optDouble("span", 300.0).toFloat()
            )
        } else null

        return SpriteDef(
            kind = o.optString("kind", "point"),
            shape = o.optString("shape", "softdot"),
            blur = o.optDouble("blur", 0.0).toFloat(),
            palette = palette,
            bands = bands,
            colorMode = colorMode,
            lineVariants = o.optInt("lineVariants", 4),
            lineFrames = o.optInt("lineFrames", 6),
            lineRoughness = o.optDouble("lineRoughness", 0.22).toFloat(),
            lineBranches = o.optBoolean("lineBranches", false),
            lineLength = parseRange(o, "lineLength", FloatRange(50f, 50f)),
            lineThickness = o.optDouble("lineThickness", 2.0).toFloat()
        )
    }

    private fun defaultEmission() = EmissionDef()

    private fun parseEmission(o: JSONObject): EmissionDef = EmissionDef(
        mode = if (o.optString("mode", "density") == "fixed") EmissionMode.FIXED else EmissionMode.DENSITY,
        spawnFraction = o.optDouble("spawnFraction", 0.08).toFloat(),
        count = o.optInt("count", 100),
        densityMul = o.optDouble("densityMul", 1.0).toFloat()
    )

    private fun parseRange(o: JSONObject, key: String, default: FloatRange): FloatRange {
        if (!o.has(key)) return default
        val arr = o.getJSONArray(key)
        return FloatRange(arr.getDouble(0).toFloat(), arr.getDouble(1).toFloat())
    }

    private fun parsePhysics(o: JSONObject): PhysicsDef = PhysicsDef(
        still = o.optBoolean("still", false),
        vx0 = parseRange(o, "vx0", FloatRange.ZERO),
        vy0 = parseRange(o, "vy0", FloatRange.ZERO),
        ax = o.optDouble("ax", 0.0).toFloat(),
        ay = o.optDouble("ay", 0.0).toFloat(),
        jitterX = o.optDouble("jitterX", 0.0).toFloat(),
        jitterY = o.optDouble("jitterY", 0.0).toFloat(),
        damp = o.optDouble("damp", 0.0).toFloat(),
        orbit = if (o.has("orbit")) {
            val ob = o.getJSONObject("orbit")
            OrbitDef(speed = parseRange(ob, "speed", FloatRange(-1f, 1f)))
        } else null,
        life = parseRange(o, "life", FloatRange(1.0f, 1.5f))
    )

    private fun parseOverLifeRange(o: JSONObject, key: String, default: OverLifeRange): OverLifeRange {
        if (!o.has(key)) return default
        val arr = o.getJSONArray(key)
        return OverLifeRange(arr.getDouble(0).toFloat(), arr.getDouble(1).toFloat())
    }

    private fun parseOverLife(o: JSONObject): OverLifeDef = OverLifeDef(
        size = parseOverLifeRange(o, "size", OverLifeRange(8f, 8f)),
        sizeCurve = if (o.optString("sizeCurve", "linear") == "easeOut") SizeCurve.EASE_OUT else SizeCurve.LINEAR,
        alpha = parseOverLifeRange(o, "alpha", OverLifeRange(1f, 0f)),
        alphaCurve = if (o.optString("alphaCurve", "linear") == "flash") AlphaCurve.FLASH else AlphaCurve.LINEAR,
        flashPeakAt = o.optDouble("flashPeakAt", 0.45).toFloat(),
        spin = if (o.has("spin")) parseRange(o, "spin", FloatRange.ZERO) else null
    )

    private fun parseLayers(arr: JSONArray): List<LayerDef> {
        val out = mutableListOf<LayerDef>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            var useMain = false
            var spriteDef: SpriteDef? = null
            if (o.has("sprite")) {
                val raw = o.get("sprite")
                if (raw is String && raw == "primary") useMain = true
                else if (raw is JSONObject) spriteDef = parseSprite(raw)
            }
            out.add(
                LayerDef(
                    sprite = spriteDef,
                    useMainSprite = useMain,
                    window = parseOverLifeRange(o, "window", OverLifeRange(0f, 1f)),
                    size = if (o.has("size")) parseOverLifeRange(o, "size", OverLifeRange(0f, 0f)) else null,
                    alpha = if (o.has("alpha")) parseOverLifeRange(o, "alpha", OverLifeRange(1f, 0f)) else null,
                    scale = o.optDouble("scale", 1.0).toFloat(),
                    alphaMul = o.optDouble("alphaMul", 1.0).toFloat(),
                    probability = o.optDouble("probability", 1.0).toFloat()
                )
            )
        }
        return out
    }

    /** Definizione neutra usata quando manca o è rotta quella richiesta: un pallino bianco morbido. */
    private fun fallback(name: String): EffectDef = EffectDef(
        name = name, emoji = "•",
        sprite = SpriteDef(shape = "softdot", blur = 4f, palette = listOf(Triple(255, 255, 255))),
        emission = EmissionDef(),
        physics = PhysicsDef(vy0 = FloatRange(-20f, -10f), ay = -10f, life = FloatRange(1f, 1.5f)),
        overLife = OverLifeDef(size = OverLifeRange(8f, 4f), alpha = OverLifeRange(0.8f, 0f))
    )
}
