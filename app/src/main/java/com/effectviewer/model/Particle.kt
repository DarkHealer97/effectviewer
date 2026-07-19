package com.effectviewer.model

data class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var life: Float, // 0.0 → 1.0 (1.0 = appena nato)
    var maxLife: Float,
    var size: Float,
    var alpha: Float,
    var color: Int,
    var rotation: Float = 0f,
    var rotationSpeed: Float = 0f,

    // ── Campi aggiunti per il motore Effects V2 ──────────────────────────
    // Tutti con default neutro: non cambiano il comportamento di nessun
    // codice esistente che costruisce Particle(...) senza specificarli.

    /** Raggio dell'orbita in px, usato solo se physics.orbit è presente nella EffectDef. */
    var orbitR: Float = 0f,
    /** Angolo corrente dell'orbita in radianti. */
    var orbitAngle: Float = 0f,
    /** Velocità angolare dell'orbita in rad/s. */
    var orbitSpeed: Float = 0f,

    /** Indice della variante colore da usare (fascia di vita o step HSV). Ricalcolato ogni frame. */
    var colorBand: Int = 0,

    /**
     * Flag stabili per i layer secondari (es. flash d'impatto, anello interno),
     * decisi UNA VOLTA alla nascita in base a `probability` e mai più cambiati.
     * Evita lo sfarfallio di una scelta ricalcolata ogni frame (bug noto,
     * capitato con hashCode() durante il primo prototipo della pioggia).
     */
    var layerFlags: BooleanArray = BooleanArray(0),

    // ── Campi per la famiglia di sprite "line" (es. scariche elettriche) ──
    // Neutri (0) e ignorati da ogni effetto puntuale: fuoco, pioggia, fumo
    // e tutto il resto non li leggono mai.

    /** Lunghezza della scarica in px, decisa alla nascita. Usata solo se sprite.kind = "line". */
    var lineLength: Float = 0f,
    /** Orientamento della scarica in gradi, deciso alla nascita. */
    var lineAngle: Float = 0f,
    /** Quale forma pre-cotta (tra le lineVariants) questa scarica usa per tutta la sua vita. */
    var shapeVariant: Int = 0
)
