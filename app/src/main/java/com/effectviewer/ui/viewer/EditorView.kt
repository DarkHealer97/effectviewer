package com.effectviewer.ui.viewer

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import com.effectviewer.model.EmitterShape
import com.effectviewer.particles.EffectType

/**
 * Editor esteso degli emitter. View overlay Canvas-based, navigabile con D-pad.
 *
 * SU/GIÙ  → scorre i parametri
 * SX/DX   → modifica il valore del parametro selezionato (cicla per enum)
 * OK      → conferma emitter, oppure attiva Sposta/Rimuovi se selezionati
 * BACK    → annulla (gestito da ViewerActivity: torna a CURSOR scartando)
 *
 * La View non possiede lo stato "verità" dell'emitter: lo riceve da
 * ViewerActivity tramite le proprietà editor*, e notifica i cambi con
 * onChanged / onAction. Questo tiene la logica di business nell'Activity
 * e la View puramente di presentazione + input.
 */
class EditorView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    sealed class EditorAction {
        object Confirm : EditorAction()
        object Move    : EditorAction()
        object Remove  : EditorAction()
        object Cancel  : EditorAction()
    }

    // ── Stato modificabile (sorgente: ViewerActivity) ─────────────────────────
    var editorType:      EffectType   = EffectType.FIRE
    var editorShape:     EmitterShape = EmitterShape.CIRCLE
    var editorRadiusX:   Float        = 0.08f
    var editorRadiusY:   Float        = 0.08f
    var editorRotation:  Float        = 0f
    var editorIntensity: Float        = 0.5f

    /** true se stiamo modificando un emitter già esistente (abilita "Sposta") */
    var editingExisting: Boolean = false

    /** Notifica ViewerActivity che un valore è cambiato (per aggiornare la preview) */
    var onChanged: (() -> Unit)? = null
    /** Notifica un'azione (conferma / sposta / rimuovi / annulla) */
    var onAction: ((EditorAction) -> Unit)? = null

    private var paramIndex = 0

    // Range / step coerenti con il menu radiale
    private val radiusMin = 0.02f
    private val radiusMax = 1.0f
    private val radiusStep = 0.01f
    private val intensityMin = 0.1f
    private val intensityMax = 1.0f
    private val intensityStep = 0.05f
    private val rotationStep = 15f

    // ── Lista parametri dinamica (dipende da shape ed editingExisting) ────────
    private enum class Param { TYPE, SHAPE, RADIUS_X, RADIUS_Y, ROTATION, INTENSITY, MOVE, REMOVE }

    private fun params(): List<Param> {
        val list = mutableListOf(Param.TYPE, Param.SHAPE, Param.RADIUS_X)
        // Raggio Y per ELLISSE, RETTANGOLO e TRIANGOLO (CIRCLE resta a un raggio).
        if (editorShape == EmitterShape.ELLIPSE ||
            editorShape == EmitterShape.RECTANGLE ||
            editorShape == EmitterShape.TRIANGLE) {
            list.add(Param.RADIUS_Y)
        }
        // Rotazione irrilevante per CIRCLE
        if (editorShape != EmitterShape.CIRCLE) {
            list.add(Param.ROTATION)
        }
        list.add(Param.INTENSITY)
        // MUOVI sempre disponibile: su un effetto nuovo sposta l'area tratteggiata
        // di anteprima prima di piazzarlo; su uno esistente sposta l'emitter.
        list.add(Param.MOVE)
        list.add(Param.REMOVE)
        return list
    }

    // ── Paint ─────────────────────────────────────────────────────────────────
    private val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(235, 16, 18, 26); style = Paint.Style.FILL
    }
    private val panelBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(255, 80, 130, 220); style = Paint.Style.STROKE; strokeWidth = 3f
    }
    private val rowSelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 40, 70, 140); style = Paint.Style.FILL
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 34f; textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 200, 210, 230); textSize = 28f; textAlign = Paint.Align.LEFT
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 28f; textAlign = Paint.Align.RIGHT
        typeface = Typeface.MONOSPACE
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 170, 180, 200); textSize = 20f; textAlign = Paint.Align.CENTER
    }

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    /** Resetta la selezione all'apertura dell'editor */
    fun resetSelection() { paramIndex = 0; invalidate() }

    override fun onDraw(canvas: Canvas) {
        val list = params()
        val w = width.toFloat()
        val h = height.toFloat()

        val panelW = minOf(w * 0.86f, 560f)
        val rowH   = 56f
        val headerH = 70f
        val footerH = 50f
        val panelH = headerH + list.size * rowH + footerH
        val left = (w - panelW) / 2f
        val top  = (h - panelH) / 2f

        canvas.drawRoundRect(left, top, left + panelW, top + panelH, 18f, 18f, panelPaint)
        canvas.drawRoundRect(left, top, left + panelW, top + panelH, 18f, 18f, panelBorder)

        // Titolo
        canvas.drawText(
            if (editingExisting) "Modifica effetto" else "Nuovo effetto",
            left + panelW / 2f, top + 44f, titlePaint
        )

        // Righe parametri
        var y = top + headerH
        list.forEachIndexed { i, param ->
            if (i == paramIndex) {
                canvas.drawRoundRect(left + 8f, y + 6f, left + panelW - 8f, y + rowH - 6f, 10f, 10f, rowSelPaint)
            }
            val textY = y + rowH / 2f + 10f
            canvas.drawText(labelFor(param), left + 28f, textY, labelPaint)
            canvas.drawText(valueFor(param), left + panelW - 28f, textY, valuePaint)
            y += rowH
        }

        // Hint
        canvas.drawText("▲▼ scegli   ◀▶ modifica   OK conferma   BACK annulla",
            left + panelW / 2f, top + panelH - 18f, hintPaint)
    }

    private fun labelFor(p: Param) = when (p) {
        Param.TYPE      -> "Tipo"
        Param.SHAPE     -> "Forma"
        Param.RADIUS_X  -> if (editorShape == EmitterShape.CIRCLE) "Raggio" else "Raggio X"
        Param.RADIUS_Y  -> "Raggio Y"
        Param.ROTATION  -> "Rotazione"
        Param.INTENSITY -> "Intensità"
        Param.MOVE      -> "↔ Sposta"
        Param.REMOVE    -> "🗑️ Rimuovi"
    }

    private fun valueFor(p: Param) = when (p) {
        Param.TYPE      -> editorType.displayName
        Param.SHAPE     -> editorShape.displayName
        Param.RADIUS_X  -> "${(editorRadiusX * 100).toInt()}%"
        Param.RADIUS_Y  -> "${(editorRadiusY * 100).toInt()}%"
        Param.ROTATION  -> "${editorRotation.toInt()}°"
        Param.INTENSITY -> "${(editorIntensity * 100).toInt()}%"
        Param.MOVE      -> "OK ▶"
        Param.REMOVE    -> "OK ▶"
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val list = params()
        if (list.isEmpty()) return super.onKeyDown(keyCode, event)
        val current = list[paramIndex.coerceIn(0, list.size - 1)]

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                paramIndex = (paramIndex - 1 + list.size) % list.size
                invalidate(); return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                paramIndex = (paramIndex + 1) % list.size
                invalidate(); return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT  -> { adjust(current, -1); return true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { adjust(current, +1); return true }
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_DPAD_CENTER -> {
                when (current) {
                    Param.MOVE   -> onAction?.invoke(EditorAction.Move)
                    Param.REMOVE -> onAction?.invoke(EditorAction.Remove)
                    else         -> onAction?.invoke(EditorAction.Confirm)
                }
                return true
            }
            KeyEvent.KEYCODE_BACK -> { onAction?.invoke(EditorAction.Cancel); return true }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun adjust(p: Param, dir: Int) {
        when (p) {
            Param.TYPE -> {
                val types = EffectType.values()
                editorType = types[(types.indexOf(editorType) + dir + types.size) % types.size]
            }
            Param.SHAPE -> {
                val shapes = EmitterShape.values()
                editorShape = shapes[(shapes.indexOf(editorShape) + dir + shapes.size) % shapes.size]
                // Normalizza i campi in base alla nuova forma
                when (editorShape) {
                    EmitterShape.CIRCLE -> { editorRadiusY = editorRadiusX; editorRotation = 0f }
                    else -> { /* ELLISSE/RETTANGOLO/TRIANGOLO: radiusY indipendente */ }
                }
                // L'indice del parametro potrebbe non essere più valido (lista cambiata)
                paramIndex = paramIndex.coerceIn(0, params().size - 1)
            }
            Param.RADIUS_X -> {
                editorRadiusX = (editorRadiusX + dir * radiusStep).coerceIn(radiusMin, radiusMax)
                if (editorShape == EmitterShape.CIRCLE) {
                    editorRadiusY = editorRadiusX
                }
            }
            Param.RADIUS_Y -> {
                editorRadiusY = (editorRadiusY + dir * radiusStep).coerceIn(radiusMin, radiusMax)
            }
            Param.ROTATION -> {
                editorRotation = ((editorRotation + dir * rotationStep) % 360f + 360f) % 360f
            }
            Param.INTENSITY -> {
                editorIntensity = (editorIntensity + dir * intensityStep).coerceIn(intensityMin, intensityMax)
            }
            Param.MOVE, Param.REMOVE -> { /* nessun valore da modificare */ }
        }
        onChanged?.invoke()
        invalidate()
    }
}
