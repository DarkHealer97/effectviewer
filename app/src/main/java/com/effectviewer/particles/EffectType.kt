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
     * Resta FUORI dal motore Effects V2 (nessuna voce in effects-v2.json).
     */
    BLACKOUT("⬛ Oscuramento", opaque = true),

    /**
     * Pioggia vista dall'alto: ogni particella è un impatto (flash + anello
     * che si allarga e sfuma), fermo — nessuno spostamento. Definita in
     * effects-v2.json come le altre (mode "fixed": conteggio proprio,
     * lineare sull'intensità, slegato dall'area dell'emitter).
     */
    RAIN("🌧️ Pioggia", opaque = false),

    /**
     * Scariche elettriche statiche: piccoli fulmini ramificati che si
     * accendono percorrendo il proprio tracciato e poi svaniscono. Prima
     * particella della famiglia sprite "line" (sprite.kind = "line" in
     * effects-v2.json): orientata e allungata, non puntuale come le altre.
     */
    SPARK("⚡ Scintilla", opaque = false),

    /**
     * Radici che nascono dal terreno, si allungano, restano visibili a lungo,
     * poi si ritirano e spariscono. Seconda applicazione della famiglia "line",
     * con reveal a tre fasi (revealCurve="growHoldRetract"), alpha che segue
     * il reveal (alphaCurve="followReveal") e foglie decorative lungo il
     * percorso — vedi i campi leaf e branch di SpriteDef.
     */
    ROOTS("🌱 Radici", opaque = false)
}