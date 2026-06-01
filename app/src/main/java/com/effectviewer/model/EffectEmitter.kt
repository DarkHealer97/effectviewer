package com.effectviewer.model

import com.effectviewer.particles.EffectType

data class EffectEmitter(
    val id: Long = System.nanoTime(),
    val cx: Float,
    val cy: Float,
    val type: EffectType,

    // ── Forma dell'emitter ──────────────────────────────────────────────────
    val shape: EmitterShape = EmitterShape.CIRCLE,
    // Semiasse X normalizzato sulla larghezza immagine (ex "radius")
    val radiusX: Float = 0.08f,
    // Semiasse Y normalizzato sull'altezza immagine
    val radiusY: Float = 0.08f,
    // Gradi 0–360. Ruota SOLO l'area di spawn e il rendering, mai la fisica.
    val rotation: Float = 0f,

    val intensity: Float = 0.5f,

    val particles: MutableList<Particle> = mutableListOf()
) {
    /**
     * Raggio "legacy" per i punti del codice che ragionano ancora su un raggio
     * singolo (es. clamp/preview rapide). Coincide con radiusX, che per CIRCLE
     * è l'unico semiasse significativo.
     */
    val radius: Float get() = radiusX
}
