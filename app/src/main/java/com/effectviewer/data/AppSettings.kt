package com.effectviewer.data

import android.content.Context

/**
 * Impostazioni globali dell'app, persistite su SharedPreferences.
 *
 * Unica fonte di verità per i parametri configurabili. I default coincidono
 * con i valori storici del codice, così un'installazione che non tocca nulla
 * si comporta esattamente come prima.
 *
 * Estensibile: per aggiungere un'impostazione basta una nuova proprietà qui
 * e il punto che la consuma. Niente refactor a cascata.
 */
object AppSettings {

    private const val PREFS = "effectviewer_settings"

    // Chiavi
    private const val K_RES        = "load_resolution"     // enum LoadResolution.name
    private const val K_ZOOM_MAX   = "zoom_max"            // Float
    private const val K_ZOOM_STEP  = "zoom_step"           // Float
    private const val K_PAN_STEP   = "pan_step"            // Float
    private const val K_PAN_REPEAT = "pan_repeat_ms"       // Long
    private const val K_MAX_PART   = "max_particles"       // Int
    private const val K_EFFECTS    = "effects_on_open"     // enum EffectsOnOpen.name

    // ── Preset risoluzione di caricamento (Glide override) ─────────────────────
    enum class LoadResolution(val label: String, val width: Int, val height: Int) {
        R480("480p (854×480)",   854,  480),
        R720("720p (1280×720)",  1280, 720),
        R1080("1080p (1920×1080)", 1920, 1080),
        R1440("1440p (2560×1440)", 2560, 1440),
        R2160("4K (3840×2160)",  3840, 2160);
    }

    // ── Comportamento all'apertura di un'immagine con effetti salvati ──────────
    enum class EffectsOnOpen(val label: String) {
        ASK("Chiedi sempre"),
        LOAD("Carica sempre"),
        CLEAN("Parti pulito");
    }

    // ── Limiti (clamp) per i valori numerici ───────────────────────────────────
    const val ZOOM_MAX_MIN = 2.0f;    const val ZOOM_MAX_MAX = 20.0f
    const val ZOOM_STEP_MIN = 0.05f;  const val ZOOM_STEP_MAX = 1.0f
    const val PAN_STEP_MIN = 10f;     const val PAN_STEP_MAX = 200f
    const val PAN_REPEAT_MIN = 20L;   const val PAN_REPEAT_MAX = 300L
    const val MAX_PART_MIN = 500;     const val MAX_PART_MAX = 8000

    // ── Default (= valori storici del codice) ──────────────────────────────────
    private val DEF_RES        = LoadResolution.R1080
    private const val DEF_ZOOM_MAX   = 10.0f
    private const val DEF_ZOOM_STEP  = 0.2f
    private const val DEF_PAN_STEP   = 40f
    private const val DEF_PAN_REPEAT = 80L
    private const val DEF_MAX_PART   = 3000
    private val DEF_EFFECTS    = EffectsOnOpen.ASK

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ── Risoluzione ─────────────────────────────────────────────────────────────
    fun loadResolution(context: Context): LoadResolution {
        val name = prefs(context).getString(K_RES, DEF_RES.name) ?: DEF_RES.name
        return try { LoadResolution.valueOf(name) } catch (e: Exception) { DEF_RES }
    }
    fun setLoadResolution(context: Context, value: LoadResolution) {
        prefs(context).edit().putString(K_RES, value.name).apply()
    }

    // ── Zoom max ─────────────────────────────────────────────────────────────────
    fun zoomMax(context: Context): Float =
        prefs(context).getFloat(K_ZOOM_MAX, DEF_ZOOM_MAX).coerceIn(ZOOM_MAX_MIN, ZOOM_MAX_MAX)
    fun setZoomMax(context: Context, v: Float) {
        prefs(context).edit().putFloat(K_ZOOM_MAX, v.coerceIn(ZOOM_MAX_MIN, ZOOM_MAX_MAX)).apply()
    }

    // ── Zoom step ────────────────────────────────────────────────────────────────
    fun zoomStep(context: Context): Float =
        prefs(context).getFloat(K_ZOOM_STEP, DEF_ZOOM_STEP).coerceIn(ZOOM_STEP_MIN, ZOOM_STEP_MAX)
    fun setZoomStep(context: Context, v: Float) {
        prefs(context).edit().putFloat(K_ZOOM_STEP, v.coerceIn(ZOOM_STEP_MIN, ZOOM_STEP_MAX)).apply()
    }

    // ── Pan step ─────────────────────────────────────────────────────────────────
    fun panStep(context: Context): Float =
        prefs(context).getFloat(K_PAN_STEP, DEF_PAN_STEP).coerceIn(PAN_STEP_MIN, PAN_STEP_MAX)
    fun setPanStep(context: Context, v: Float) {
        prefs(context).edit().putFloat(K_PAN_STEP, v.coerceIn(PAN_STEP_MIN, PAN_STEP_MAX)).apply()
    }

    // ── Pan repeat ms ──────────────────────────────────────────────────────────
    fun panRepeatMs(context: Context): Long =
        prefs(context).getLong(K_PAN_REPEAT, DEF_PAN_REPEAT).coerceIn(PAN_REPEAT_MIN, PAN_REPEAT_MAX)
    fun setPanRepeatMs(context: Context, v: Long) {
        prefs(context).edit().putLong(K_PAN_REPEAT, v.coerceIn(PAN_REPEAT_MIN, PAN_REPEAT_MAX)).apply()
    }

    // ── Max particles ──────────────────────────────────────────────────────────
    fun maxParticles(context: Context): Int =
        prefs(context).getInt(K_MAX_PART, DEF_MAX_PART).coerceIn(MAX_PART_MIN, MAX_PART_MAX)
    fun setMaxParticles(context: Context, v: Int) {
        prefs(context).edit().putInt(K_MAX_PART, v.coerceIn(MAX_PART_MIN, MAX_PART_MAX)).apply()
    }

    // ── Comportamento effetti all'apertura ─────────────────────────────────────
    fun effectsOnOpen(context: Context): EffectsOnOpen {
        val name = prefs(context).getString(K_EFFECTS, DEF_EFFECTS.name) ?: DEF_EFFECTS.name
        return try { EffectsOnOpen.valueOf(name) } catch (e: Exception) { DEF_EFFECTS }
    }
    fun setEffectsOnOpen(context: Context, value: EffectsOnOpen) {
        prefs(context).edit().putString(K_EFFECTS, value.name).apply()
    }
}
