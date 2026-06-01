package com.effectviewer.model

/**
 * Forma dell'area di spawn (e del disegno) di un emitter.
 *
 * La forma controlla:
 *  - dove nascono le particelle (area di spawn)
 *  - il disegno dell'anteprima in CursorView
 *  - il disegno dell'oscuramento per il tipo BLACKOUT
 *
 * NON controlla la fisica delle particelle: le velocità e le accelerazioni
 * restano in coordinate schermo assolute (es. FIRE sale sempre verso l'alto)
 * indipendentemente da forma e rotazione dell'emitter.
 */
enum class EmitterShape(val displayName: String) {
    CIRCLE("⬤ Cerchio"),
    ELLIPSE("⬭ Ellisse"),
    RECTANGLE("▬ Rettangolo"),
    TRIANGLE("▲ Triangolo")
}
