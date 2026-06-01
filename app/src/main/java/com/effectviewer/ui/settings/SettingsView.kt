package com.effectviewer.ui.settings

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import com.effectviewer.data.AppSettings

/**
 * Lista di impostazioni navigabile con D-pad. Ogni voce è numerica
 * (incremento con SX/DX) oppure a scelta (ciclo tra opzioni con SX/DX).
 * Scrive su AppSettings a ogni modifica.
 */
class SettingsView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var ctx: Context = context
    private var selected = 0

    // Definizione dichiarativa delle voci: etichetta, lettura valore,
    // azione di modifica (dir = -1 / +1), e descrizione del valore corrente.
    private data class Item(
        val label: String,
        val valueText: () -> String,
        val adjust: (dir: Int) -> Unit,
        val note: String? = null
    )

    private var items: List<Item> = emptyList()

    fun load(context: Context) {
        ctx = context.applicationContext
        items = buildItems()
        invalidate()
    }

    private fun buildItems(): List<Item> = listOf(
        Item(
            label = "Risoluzione caricamento",
            valueText = { AppSettings.loadResolution(ctx).label },
            adjust = { dir ->
                val all = AppSettings.LoadResolution.values()
                val cur = AppSettings.loadResolution(ctx)
                val next = all[(all.indexOf(cur) + dir + all.size) % all.size]
                AppSettings.setLoadResolution(ctx, next)
            },
            note = "Effetto alla prossima apertura immagine. 4K usa molta RAM."
        ),
        Item(
            label = "Zoom massimo",
            valueText = { "${"%.1f".format(AppSettings.zoomMax(ctx))}×" },
            adjust = { dir -> AppSettings.setZoomMax(ctx, AppSettings.zoomMax(ctx) + dir * 0.5f) }
        ),
        Item(
            label = "Incremento zoom",
            valueText = { "${"%.2f".format(AppSettings.zoomStep(ctx))}×" },
            adjust = { dir -> AppSettings.setZoomStep(ctx, AppSettings.zoomStep(ctx) + dir * 0.05f) }
        ),
        Item(
            label = "Velocità pan",
            valueText = { "${AppSettings.panStep(ctx).toInt()} px" },
            adjust = { dir -> AppSettings.setPanStep(ctx, AppSettings.panStep(ctx) + dir * 5f) }
        ),
        Item(
            label = "Ripetizione pan",
            valueText = { "${AppSettings.panRepeatMs(ctx)} ms" },
            adjust = { dir -> AppSettings.setPanRepeatMs(ctx, AppSettings.panRepeatMs(ctx) + dir * 10L) }
        ),
        Item(
            label = "Particelle massime",
            valueText = { "${AppSettings.maxParticles(ctx)}" },
            adjust = { dir -> AppSettings.setMaxParticles(ctx, AppSettings.maxParticles(ctx) + dir * 500) },
            note = "Più alto = più densità ma più carico. 3000 consigliato."
        ),
        Item(
            label = "Effetti all'apertura",
            valueText = { AppSettings.effectsOnOpen(ctx).label },
            adjust = { dir ->
                val all = AppSettings.EffectsOnOpen.values()
                val cur = AppSettings.effectsOnOpen(ctx)
                val next = all[(all.indexOf(cur) + dir + all.size) % all.size]
                AppSettings.setEffectsOnOpen(ctx, next)
            }
        ),
    )

    // ── Paint ─────────────────────────────────────────────────────────────────
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 46f; typeface = Typeface.DEFAULT_BOLD
    }
    private val rowSelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 40, 70, 140); style = Paint.Style.FILL
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(235, 220, 226, 240); textSize = 34f
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 34f; textAlign = Paint.Align.RIGHT
        typeface = Typeface.MONOSPACE
    }
    private val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 150, 160, 180); textSize = 24f
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 150, 160, 180); textSize = 26f; textAlign = Paint.Align.CENTER
    }
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 120, 180, 255); textSize = 34f; textAlign = Paint.Align.RIGHT
    }

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        setBackgroundColor(Color.rgb(17, 17, 17))
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val marginX = 80f
        var y = 110f

        canvas.drawText("Impostazioni", marginX, y, titlePaint)
        y += 70f

        val rowH = if (items.any { it.note != null }) 110f else 90f
        items.forEachIndexed { i, item ->
            val top = y
            if (i == selected) {
                canvas.drawRoundRect(marginX - 24f, top - 44f, w - marginX + 24f, top + rowH - 64f, 14f, 14f, rowSelPaint)
            }
            canvas.drawText(item.label, marginX, top, labelPaint)
            // Valore con frecce ◀ ▶ quando selezionato
            val valStr = item.valueText()
            if (i == selected) {
                canvas.drawText("◀ $valStr ▶", w - marginX, top, arrowPaint)
            } else {
                canvas.drawText(valStr, w - marginX, top, valuePaint)
            }
            item.note?.let { canvas.drawText(it, marginX, top + 34f, notePaint) }
            y += rowH
        }

        canvas.drawText("▲▼ scegli    ◀▶ modifica    BACK esci",
            w / 2f, height - 50f, hintPaint)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (items.isEmpty()) return super.onKeyDown(keyCode, event)
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP   -> { selected = (selected - 1 + items.size) % items.size; invalidate(); return true }
            KeyEvent.KEYCODE_DPAD_DOWN -> { selected = (selected + 1) % items.size; invalidate(); return true }
            KeyEvent.KEYCODE_DPAD_LEFT -> { items[selected].adjust(-1); invalidate(); return true }
            KeyEvent.KEYCODE_DPAD_RIGHT-> { items[selected].adjust(+1); invalidate(); return true }
        }
        return super.onKeyDown(keyCode, event)
    }
}
