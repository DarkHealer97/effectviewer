package com.effectviewer.particles

enum class EffectType(
    val displayName: String,
    /** Se true le particelle possono coprire l'immagine senza limite di opacità */
    val opaque: Boolean
) {
    FIRE("🔥 Fuoco", opaque = false),
    TOXIC_GAS("☠️ Gas Tossico", opaque = true),
    EMBERS("✨ Braci", opaque = false),
    SMOKE("💨 Fumo", opaque = true),
    MAGIC("🔮 Magia", opaque = false),
    ICE("❄️ Ghiaccio", opaque = false),

    /**
     * Oscuramento statico. Non genera particelle: viene disegnato direttamente
     * da ParticleView con un RadialGradient nero→trasparente molto netto.
     * ParticleSystem.update lo ignora completamente (particles resta vuota).
     */
    BLACKOUT("⬛ Oscuramento", opaque = true)
}
