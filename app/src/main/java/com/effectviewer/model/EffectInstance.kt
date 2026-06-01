package com.effectviewer.model

import java.util.UUID

/**
 * Tipi di effetto particellare disponibili.
 * Ogni tipo ha un nome leggibile, un'icona (unicode/emoji per semplicità)
 * e parametri di default.
 */
enum class EffectType(
    val displayName: String,
    val emoji: String,
    val description: String
) {
    FIRE("Fuoco", "🔥", "Fiamme che lambiscono verso l'alto"),
    TOXIC_GAS("Gas Tossico", "☠", "Nuvola di vapore verde corrosivo"),
    SMOKE("Fumo", "💨", "Fumo grigio che si dissolve"),
    SPARKS("Scintille", "✦", "Scintille elettriche che si irradiano"),
    MAGIC("Magia", "✦", "Particelle magiche incandescenti"),
    BLOOD("Sangue", "●", "Gocce di sangue che cadono"),
    ICE("Ghiaccio", "❄", "Cristalli di ghiaccio fluttuanti")
}

/**
 * Un'istanza di effetto posizionata su una coordinata normalizzata (0.0–1.0)
 * dell'immagine. Le coordinate sono normalizzate così restano valide
 * indipendentemente dalla dimensione di visualizzazione.
 */
data class EffectInstance(
    val id: String = UUID.randomUUID().toString(),
    val type: EffectType,
    // Posizione normalizzata rispetto all'immagine visualizzata (0.0–1.0)
    val normalizedX: Float,
    val normalizedY: Float,
    // Raggio dell'area dell'effetto in px (calcolato al momento del render)
    val radiusDp: Float = 80f
)
