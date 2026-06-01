package com.effectviewer.ui.viewer

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import com.effectviewer.particles.EffectType
import kotlin.math.*

class RadialMenuView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    sealed class MenuAction {
        data class AddEffect(val type: EffectType, val radius: Float, val intensity: Float) : MenuAction()
        object Remove : MenuAction()
        object Dismiss : MenuAction()
    }

    data class MenuItem(val label: String, val action: MenuAction)

    enum class AdjustMode { NONE, RADIUS, INTENSITY }

    var items: List<MenuItem> = emptyList()
    var selectedIndex: Int = 0
    var centerX: Float = 0f
    var centerY: Float = 0f
    var adjustMode: AdjustMode = AdjustMode.NONE
        private set

    var onAction: ((MenuAction) -> Unit)? = null
    /** Callback chiamata ogni volta che raggio o intensità cambiano, per aggiornare il CursorView */
    var onPreviewChanged: ((radiusNorm: Float, intensity: Float) -> Unit)? = null

    var currentRadius: Float = 0.08f
        private set
    var currentIntensity: Float = 0.5f
        private set

    private val radiusMin  = 0.02f
    private val radiusMax  = 0.35f
    private val radiusStep = 0.01f
    private val intensityStep = 0.05f

    // ── Paint ─────────────────────────────────────────────────────────────────

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val itemBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 30, 30, 30); style = Paint.Style.FILL
    }
    private val itemSelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 50, 120, 255); style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 2f
    }
    private val borderSelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(255, 100, 180, 255); style = Paint.Style.STROKE; strokeWidth = 3f
    }
    private val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textAlign = Paint.Align.CENTER; textSize = 30f
    }
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textAlign = Paint.Align.CENTER; textSize = 19f
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 200, 200, 200); textAlign = Paint.Align.CENTER; textSize = 18f
    }
    private val adjustHintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(230, 255, 220, 80); textAlign = Paint.Align.CENTER; textSize = 20f
        setShadowLayer(4f, 0f, 0f, Color.BLACK)
    }

    private val itemRadius   = 62f
    private val orbitRadius  = 170f

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = w / 2f
        centerY = h / 2f
    }

    override fun onDraw(canvas: Canvas) {
        val cx = centerX; val cy = centerY
        val count = items.size
        if (count == 0) return

        // Opacità del menu: dimmed durante regolazione raggio/intensità
        val menuAlpha = when (adjustMode) {
            AdjustMode.NONE      -> 255
            AdjustMode.RADIUS    -> 80
            AdjustMode.INTENSITY -> 80
        }

        // Sfondo
        bgPaint.color = Color.argb((menuAlpha * 0.63f).toInt(), 0, 0, 0)
        canvas.drawCircle(cx, cy, orbitRadius + itemRadius + 24f, bgPaint)

        // Voci
        items.forEachIndexed { i, item ->
            val angle = (2.0 * PI / count * i - PI / 2).toFloat()
            val ix = cx + cos(angle) * orbitRadius
            val iy = cy + sin(angle) * orbitRadius

            val isSelected = i == selectedIndex && adjustMode == AdjustMode.NONE

            itemBgPaint.alpha   = menuAlpha
            itemSelPaint.alpha  = menuAlpha
            borderPaint.alpha   = menuAlpha
            borderSelPaint.alpha = menuAlpha
            emojiPaint.alpha    = menuAlpha
            namePaint.alpha     = menuAlpha

            canvas.drawCircle(ix, iy, itemRadius, if (isSelected) itemSelPaint else itemBgPaint)
            canvas.drawCircle(ix, iy, itemRadius, if (isSelected) borderSelPaint else borderPaint)

            val space = item.label.indexOf(' ')
            if (space > 0) {
                canvas.drawText(item.label.substring(0, space), ix, iy,        emojiPaint)
                canvas.drawText(item.label.substring(space + 1), ix, iy + 24f,  namePaint)
            } else {
                canvas.drawText(item.label, ix, iy + 8f, emojiPaint)
            }
        }

        // Reset alpha paint
        emojiPaint.alpha = 255; namePaint.alpha = 255

        // ── Hint in base alla modalità ────────────────────────────────────────
        when (adjustMode) {
            AdjustMode.NONE -> {
                canvas.drawText("◀ ▶  scegli effetto", cx, cy - 20f, hintPaint)
                canvas.drawText("▼  regola raggio",    cx, cy + 4f,  hintPaint)
                canvas.drawText("▲  regola intensità", cx, cy + 28f, hintPaint)
                canvas.drawText("OK  conferma  •  BACK  chiudi", cx, cy + 52f, hintPaint)
            }
            AdjustMode.RADIUS -> {
                canvas.drawText("◀ ▶  cambia raggio", cx, cy - 10f, adjustHintPaint)
                canvas.drawText("▲▼  passa ad altro  •  OK  conferma", cx, cy + 18f, adjustHintPaint)
            }
            AdjustMode.INTENSITY -> {
                canvas.drawText("◀ ▶  cambia intensità", cx, cy - 10f, adjustHintPaint)
                canvas.drawText("▲▼  passa ad altro  •  OK  conferma", cx, cy + 18f, adjustHintPaint)
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val count = items.size
        if (count == 0) return super.onKeyDown(keyCode, event)

        when (adjustMode) {
            AdjustMode.RADIUS -> {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        currentRadius = (currentRadius + radiusStep).coerceAtMost(radiusMax)
                        notifyPreview(); invalidate(); return true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        currentRadius = (currentRadius - radiusStep).coerceAtLeast(radiusMin)
                        notifyPreview(); invalidate(); return true
                    }
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        adjustMode = AdjustMode.INTENSITY
                        notifyPreview(); invalidate(); return true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        adjustMode = AdjustMode.NONE
                        notifyPreview(); invalidate(); return true
                    }
                    KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_DPAD_CENTER -> { confirmEffect(); return true }
                    KeyEvent.KEYCODE_BACK -> {
                        adjustMode = AdjustMode.NONE; notifyPreview(); invalidate(); return true
                    }
                }
            }
            AdjustMode.INTENSITY -> {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        currentIntensity = (currentIntensity + intensityStep).coerceAtMost(1f)
                        notifyPreview(); invalidate(); return true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        currentIntensity = (currentIntensity - intensityStep).coerceAtLeast(0.1f)
                        notifyPreview(); invalidate(); return true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        adjustMode = AdjustMode.RADIUS
                        notifyPreview(); invalidate(); return true
                    }
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        adjustMode = AdjustMode.NONE
                        notifyPreview(); invalidate(); return true
                    }
                    KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_DPAD_CENTER -> { confirmEffect(); return true }
                    KeyEvent.KEYCODE_BACK -> {
                        adjustMode = AdjustMode.NONE; notifyPreview(); invalidate(); return true
                    }
                }
            }
            AdjustMode.NONE -> {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        selectedIndex = (selectedIndex + 1) % count; invalidate(); return true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        selectedIndex = (selectedIndex - 1 + count) % count; invalidate(); return true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        val action = items[selectedIndex].action
                        if (action is MenuAction.AddEffect) {
                            adjustMode = AdjustMode.RADIUS; notifyPreview(); invalidate()
                        }
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        val action = items[selectedIndex].action
                        if (action is MenuAction.AddEffect) {
                            adjustMode = AdjustMode.INTENSITY; notifyPreview(); invalidate()
                        }
                        return true
                    }
                    KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_DPAD_CENTER -> { confirmEffect(); return true }
                    KeyEvent.KEYCODE_BACK -> {
                        onAction?.invoke(MenuAction.Dismiss); return true
                    }
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun confirmEffect() {
        val action = items[selectedIndex].action
        if (action is MenuAction.AddEffect) {
            onAction?.invoke(MenuAction.AddEffect(action.type, currentRadius, currentIntensity))
        } else {
            onAction?.invoke(action)
        }
        adjustMode = AdjustMode.NONE
    }

    private fun notifyPreview() {
        onPreviewChanged?.invoke(currentRadius, currentIntensity)
    }

    companion object {
        fun itemsForEmpty(): List<MenuItem> =
            EffectType.values().map {
                MenuItem(it.displayName, MenuAction.AddEffect(it, 0.08f, 0.5f))
            }

        fun itemsForExisting(): List<MenuItem> =
            EffectType.values().map {
                MenuItem(it.displayName, MenuAction.AddEffect(it, 0.08f, 0.5f))
            } + listOf(MenuItem("🗑️ Rimuovi", MenuAction.Remove))
    }
}