package com.effectviewer.ui.viewer

import android.graphics.Matrix
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import android.graphics.drawable.Drawable
import com.effectviewer.R
import com.effectviewer.data.AppSettings
import com.effectviewer.data.EffectsMemory
import com.effectviewer.model.EffectEmitter
import com.effectviewer.model.EmitterShape
import com.effectviewer.particles.EffectType
import java.io.File
import kotlin.math.hypot

class ViewerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URI          = "extra_uri"
        const val EXTRA_IMAGE_PATH   = "extra_image_path"
        const val EXTRA_LOAD_EFFECTS = "extra_load_effects"
        const val EXTRA_IMAGE_LIST   = "extra_image_list"   // ArrayList<String> percorsi immagini cartella
        const val HIT_RADIUS         = 80f
        const val ZOOM_STEP          = 0.2f
        const val ZOOM_MIN           = 0.3f   // permette zoom out sotto dimensione naturale
        const val ZOOM_MAX           = 10.0f
        const val PAN_STEP           = 40f
        const val LONG_PRESS_MS      = 400L
        const val PAN_REPEAT_MS      = 80L
    }

    private lateinit var imageView:    ImageView
    private lateinit var particleView: ParticleView
    private lateinit var cursorView:   CursorView
    private lateinit var radialMenu:   RadialMenuView
    private lateinit var editorView:   EditorView
    private lateinit var loadingBar:   ProgressBar

    private val emitters = mutableListOf<EffectEmitter>()
    private var hitEmitter: EffectEmitter? = null
    private var cursorClickX = 0f
    private var cursorClickY = 0f

    // Emitter attualmente in editing nell'editor esteso (null = nuovo)
    private var editingEmitter: EffectEmitter? = null
    // Backup posizione per annullamento MOVE
    private var moveCxBackup = 0f
    private var moveCyBackup = 0f

    // Percorso immagine corrente
    private var imagePath: String? = null

    // Lista immagini nella stessa cartella per navigazione SX/DX
    private var imageList: List<String> = emptyList()
    private var imageIndex: Int = 0

    // Zoom & Pan
    private var zoomLevel     = 1.0f
    private var panX          = 0f
    private var panY          = 0f
    private var baseImageRect = RectF()
    private var baseMatrix    = Matrix()   // matrice fitCenter calcolata da computeBaseRect

    // Valori letti da AppSettings in onCreate (i const val sono i default storici)
    private var cfgZoomMax    = ZOOM_MAX
    private var cfgZoomStep   = ZOOM_STEP
    private var cfgPanStep    = PAN_STEP
    private var cfgPanRepeat  = PAN_REPEAT_MS
    private var cfgResW       = 1920
    private var cfgResH       = 1080

    private val handler = Handler(Looper.getMainLooper())
    private var activePanRunnable: Runnable? = null
    private var pressedKeyCode: Int = -1
    private var longPressTriggered: Boolean = false

    // Long press su ENTER in modalità CURSOR (apre l'editor esteso)
    private var cursorEnterPressed = false
    private var cursorEnterLong    = false
    // L'OK che apre il cursore (VIEW→CURSOR) non deve aprire anche il menu:
    // consumiamo il suo ACTION_UP con questo flag.
    private var consumeNextCursorEnterUp = false
    // L'OK del long press che apre l'EDITOR non deve poi selezionare una voce
    // del menu appena aperto: ignoriamo ogni evento OK (ripetizioni incluse)
    // finché il tasto non viene rilasciato almeno una volta.
    private var swallowEditorOpenEnter = false

    private enum class Mode { VIEW, CURSOR, MENU, EDITOR, MOVE }
    private var mode = Mode.VIEW

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_viewer)

        // Carica le impostazioni globali (i const val restano i default)
        cfgZoomMax   = AppSettings.zoomMax(this)
        cfgZoomStep  = AppSettings.zoomStep(this)
        cfgPanStep   = AppSettings.panStep(this)
        cfgPanRepeat = AppSettings.panRepeatMs(this)
        val res = AppSettings.loadResolution(this)
        cfgResW = res.width
        cfgResH = res.height

        imageView    = findViewById(R.id.imageView)
        particleView = findViewById(R.id.particleView)
        cursorView   = findViewById(R.id.cursorView)
        radialMenu   = findViewById(R.id.radialMenu)
        editorView   = findViewById(R.id.editorView)
        loadingBar   = findViewById(R.id.loadingBar)

        imagePath = intent.getStringExtra(EXTRA_IMAGE_PATH)
        imageList = intent.getStringArrayListExtra(EXTRA_IMAGE_LIST) ?: emptyList()
        imageIndex = imageList.indexOf(imagePath).coerceAtLeast(0)

        setupCursor()
        setupMenu()
        setupEditor()

        val uriStr = intent.getStringExtra(EXTRA_URI)
        if (!uriStr.isNullOrEmpty()) loadImage(Uri.parse(uriStr))

        window.decorView.isFocusable = true
        window.decorView.isFocusableInTouchMode = true
        window.decorView.requestFocus()
    }

    override fun onStop() {
        super.onStop()
        stopPan()
        saveCurrentEffects()
    }

    // ── Salvataggio effetti immagine corrente ─────────────────────────────────

    private fun saveCurrentEffects() {
        imagePath?.let { EffectsMemory.save(this, it, emitters.toList()) }
    }

    // ── Navigazione immagini ──────────────────────────────────────────────────

    private fun showPreviousImage() {
        if (imageList.isEmpty()) return
        saveCurrentEffects()
        imageIndex = (imageIndex - 1 + imageList.size) % imageList.size
        switchToImage(imageIndex)
    }

    private fun showNextImage() {
        if (imageList.isEmpty()) return
        saveCurrentEffects()
        imageIndex = (imageIndex + 1) % imageList.size
        switchToImage(imageIndex)
    }

    private fun switchToImage(index: Int) {
        val path = imageList[index]
        imagePath = path
        val file = File(path)

        // Resetta zoom, pan ed emitter
        zoomLevel = 1.0f
        panX = 0f
        panY = 0f
        baseImageRect = RectF()
        baseMatrix    = Matrix()
        synchronized(emitters) { emitters.clear() }
        particleView.emitters = emptyList()

        // Carica nuova immagine
        loadImage(Uri.fromFile(file))

        // Carica effetti salvati se esistono (senza dialog, come da requisito)
        // Il caricamento avviene dentro onResourceReady dopo computeBaseRect
    }

    // ── Caricamento immagine ──────────────────────────────────────────────────

    private fun loadImage(uri: Uri) {
        loadingBar.visibility = View.VISIBLE
        Glide.with(this)
            .load(uri)
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .override(cfgResW, cfgResH)
            .fitCenter()
            .into(object : CustomTarget<Drawable>() {
                override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                    loadingBar.visibility = View.GONE
                    imageView.setImageDrawable(resource)

                    imageView.viewTreeObserver.addOnGlobalLayoutListener(object :
                        android.view.ViewTreeObserver.OnGlobalLayoutListener {
                        override fun onGlobalLayout() {
                            imageView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                            if (imageView.measuredWidth <= 0 || imageView.measuredHeight <= 0) return
                            computeBaseRect()
                            applyZoomPan()
                            val shouldLoad = intent.getBooleanExtra(EXTRA_LOAD_EFFECTS, false)
                            val isFirstLoad = (imagePath == intent.getStringExtra(EXTRA_IMAGE_PATH))
                            if (isFirstLoad && shouldLoad) {
                                loadSavedEffects()
                            } else if (!isFirstLoad) {
                                loadSavedEffects()
                            }
                        }
                    })
                }
                override fun onLoadFailed(errorDrawable: Drawable?) {
                    loadingBar.visibility = View.GONE
                    imageView.setImageResource(android.R.drawable.ic_menu_gallery)
                }
                override fun onLoadCleared(placeholder: Drawable?) {
                    imageView.setImageDrawable(null)
                }
            })
    }

    private fun loadSavedEffects() {
        val path = imagePath ?: return
        val saved = EffectsMemory.load(this, path)
        if (saved.isEmpty()) return
        synchronized(emitters) { emitters.addAll(saved) }
        particleView.emitters = emitters.toList()
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        when (mode) {
            Mode.VIEW -> {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    if (handleViewKeyDown(event.keyCode, event)) return true
                } else if (event.action == KeyEvent.ACTION_UP) {
                    if (handleViewKeyUp(event.keyCode)) return true
                }
            }
            Mode.CURSOR -> {
                // Intercetta solo ENTER per distinguere click breve / long press.
                // Le frecce e BACK restano gestite da CursorView.onKeyDown.
                if (event.keyCode == KeyEvent.KEYCODE_ENTER ||
                    event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
                    // Scarta il rilascio dell'OK che ha aperto il cursore.
                    if (event.action == KeyEvent.ACTION_UP && consumeNextCursorEnterUp) {
                        consumeNextCursorEnterUp = false
                        return true
                    }
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        if (handleCursorEnterDown(event.keyCode, event)) return true
                    } else if (event.action == KeyEvent.ACTION_UP) {
                        if (handleCursorEnterUp()) return true
                    }
                }
            }
            Mode.MOVE -> {
                // MOVE riusa CursorView per il movimento ma con conferma/annulla custom.
                if (event.action == KeyEvent.ACTION_DOWN) {
                    if (handleMoveKey(event.keyCode)) return true
                }
            }
            Mode.EDITOR -> {
                // L'OK del long press che ha aperto l'editor genera ripetizioni
                // (ACTION_DOWN con repeatCount>0) e un ACTION_UP finale: vanno
                // ignorati, altrimenti selezionano la voce su cui si è aperti.
                if (swallowEditorOpenEnter &&
                    (event.keyCode == KeyEvent.KEYCODE_ENTER ||
                     event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER)) {
                    if (event.action == KeyEvent.ACTION_UP) {
                        swallowEditorOpenEnter = false   // pressione conclusa: riabilita l'OK
                    }
                    return true
                }
                // Resto degli eventi: gestiti da EditorView
            }
            else -> { /* MENU: gestito dalla rispettiva View */ }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun handleViewKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.repeatCount > 0) return true

        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                pressedKeyCode = keyCode
                longPressTriggered = false
                handler.postDelayed({
                    if (pressedKeyCode == keyCode) {
                        longPressTriggered = true
                        startPan(0f, cfgPanStep)
                    }
                }, LONG_PRESS_MS)
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                pressedKeyCode = keyCode
                longPressTriggered = false
                handler.postDelayed({
                    if (pressedKeyCode == keyCode) {
                        longPressTriggered = true
                        startPan(0f, -cfgPanStep)
                    }
                }, LONG_PRESS_MS)
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                pressedKeyCode = keyCode
                longPressTriggered = false
                handler.postDelayed({
                    if (pressedKeyCode == keyCode) {
                        longPressTriggered = true
                        startPan(cfgPanStep, 0f)
                    }
                }, LONG_PRESS_MS)
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                pressedKeyCode = keyCode
                longPressTriggered = false
                handler.postDelayed({
                    if (pressedKeyCode == keyCode) {
                        longPressTriggered = true
                        startPan(-cfgPanStep, 0f)
                    }
                }, LONG_PRESS_MS)
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER -> {
                // L'ACTION_UP di questo stesso OK va scartato in CURSOR,
                // così l'apertura del cursore non apre subito anche il menu.
                consumeNextCursorEnterUp = true
                toMode(Mode.CURSOR); true
            }
            KeyEvent.KEYCODE_BACK  -> { finish(); true }
            else -> false
        }
    }

    private fun handleViewKeyUp(keyCode: Int): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                val wasLong = longPressTriggered
                pressedKeyCode = -1; longPressTriggered = false; stopPan()
                if (!wasLong) zoomIn()
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                val wasLong = longPressTriggered
                pressedKeyCode = -1; longPressTriggered = false; stopPan()
                if (!wasLong) zoomOut()
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                val wasLong = longPressTriggered
                pressedKeyCode = -1; longPressTriggered = false; stopPan()
                if (!wasLong && isAtNaturalZoom()) showPreviousImage()
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                val wasLong = longPressTriggered
                pressedKeyCode = -1; longPressTriggered = false; stopPan()
                if (!wasLong && isAtNaturalZoom()) showNextImage()
                true
            }
            else -> false
        }
    }

    // ── ENTER in modalità CURSOR: click breve → menu, long press → editor ─────

    private fun handleCursorEnterDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.repeatCount > 0) return true
        cursorEnterPressed = true
        cursorEnterLong = false
        handler.postDelayed({
            if (cursorEnterPressed) {
                cursorEnterLong = true
                swallowEditorOpenEnter = true   // ignora il resto di questa pressione OK
                openEditor()
            }
        }, LONG_PRESS_MS)
        return true
    }

    private fun handleCursorEnterUp(): Boolean {
        val wasLong = cursorEnterLong
        cursorEnterPressed = false
        cursorEnterLong = false
        if (!wasLong) {
            // Click breve: apre il menu radiale (comportamento attuale)
            cursorClickX = cursorView.cx
            cursorClickY = cursorView.cy
            hitEmitter = findHit(cursorClickX, cursorClickY)
            toMode(Mode.MENU, cursorClickX, cursorClickY)
        }
        return true
    }

    // Zoom "naturale" = nessun pan attivo e zoom al livello 1x
    private fun isAtNaturalZoom() = zoomLevel == 1.0f && panX == 0f && panY == 0f

    // ── Zoom & Pan ────────────────────────────────────────────────────────────

    private fun computeBaseRect() {
        val d  = imageView.drawable ?: return
        val vw = imageView.measuredWidth.toFloat()
        val vh = imageView.measuredHeight.toFloat()
        val iw = d.intrinsicWidth.toFloat()
        val ih = d.intrinsicHeight.toFloat()
        if (vw <= 0f || vh <= 0f || iw <= 0f || ih <= 0f) return

        val scale = minOf(vw / iw, vh / ih)
        val sw = iw * scale
        val sh = ih * scale
        val ox = (vw - sw) / 2f
        val oy = (vh - sh) / 2f
        baseImageRect = RectF(ox, oy, ox + sw, oy + sh)

        baseMatrix = Matrix()
        baseMatrix.postScale(scale, scale)
        baseMatrix.postTranslate(ox, oy)
        imageView.scaleType   = ImageView.ScaleType.MATRIX
        imageView.imageMatrix = Matrix(baseMatrix)
    }

    private fun applyZoomPan() {
        if (baseImageRect.isEmpty) return

        val matrix = Matrix(baseMatrix)
        val bcx = baseImageRect.centerX()
        val bcy = baseImageRect.centerY()
        matrix.postScale(zoomLevel, zoomLevel, bcx, bcy)
        matrix.postTranslate(panX, panY)

        imageView.scaleType   = ImageView.ScaleType.MATRIX
        imageView.imageMatrix = matrix

        val rect = getCurrentImageRect()
        particleView.imageRect = rect
        cursorView.clampRect   = rect
        particleView.invalidate()
        cursorView.invalidate()
    }

    private fun clampPan() {
        val vw = imageView.measuredWidth.toFloat()
        val vh = imageView.measuredHeight.toFloat()
        val zw = baseImageRect.width()  * zoomLevel
        val zh = baseImageRect.height() * zoomLevel

        if (zw <= vw && zh <= vh) return
        val maxPanX = maxOf(0f, (zw - vw) / 2f)
        val maxPanY = maxOf(0f, (zh - vh) / 2f)
        panX = panX.coerceIn(-maxPanX, maxPanX)
        panY = panY.coerceIn(-maxPanY, maxPanY)
    }

    private fun zoomIn() {
        zoomLevel = (zoomLevel + cfgZoomStep).coerceAtMost(cfgZoomMax)
        clampPan()
        applyZoomPan()
    }

    private fun zoomOut() {
        zoomLevel = (zoomLevel - cfgZoomStep).coerceAtLeast(ZOOM_MIN)
        if (zoomLevel <= 1.0f) { panX = 0f; panY = 0f }
        clampPan()
        applyZoomPan()
    }

    private fun pan(dx: Float, dy: Float) {
        panX += dx; panY += dy
        clampPan()
        applyZoomPan()
    }

    private fun startPan(dx: Float, dy: Float) {
        stopPan()
        val r = object : Runnable {
            override fun run() { pan(dx, dy); handler.postDelayed(this, cfgPanRepeat) }
        }
        activePanRunnable = r
        handler.postDelayed(r, LONG_PRESS_MS)
    }

    private fun stopPan() {
        activePanRunnable?.let { handler.removeCallbacks(it) }
        activePanRunnable = null
    }

    private fun getCurrentImageRect(): RectF {
        if (baseImageRect.isEmpty) return RectF()
        // baseImageRect è GIÀ in coordinate schermo (rect fitCenter). Lo zoom/pan
        // vanno applicati direttamente su di esso: scala attorno al suo centro,
        // poi trasla. NON va usato baseMatrix qui — quella mappa intrinseco→schermo
        // e applicata a un rect già in schermo lo trasformerebbe due volte,
        // producendo un rettangolo più piccolo e disallineato.
        val matrix = Matrix()
        matrix.postScale(zoomLevel, zoomLevel, baseImageRect.centerX(), baseImageRect.centerY())
        matrix.postTranslate(panX, panY)
        val dst = RectF(baseImageRect)
        matrix.mapRect(dst)
        return dst
    }

    // ── Cursor & Menu ─────────────────────────────────────────────────────────

    private fun setupCursor() {
        // In CURSOR l'OK è gestito interamente da dispatchKeyEvent (click breve →
        // menu semplice, long press → editor esteso). onConfirm resta neutro per
        // evitare la doppia apertura del menu.
        cursorView.onConfirm = { _, _ -> /* gestito da dispatchKeyEvent */ }
        cursorView.onCancel = {
            if (mode == Mode.CURSOR) toMode(Mode.VIEW)
        }
    }

    private fun setupMenu() {
        radialMenu.onPreviewChanged = { radiusNorm, intensity ->
            val rect = getCurrentImageRect()
            cursorView.previewShape     = EmitterShape.CIRCLE
            cursorView.previewRadiusPx  = radiusNorm * rect.width()
            cursorView.previewIntensity = intensity
            cursorView.showPreview      = radialMenu.adjustMode != RadialMenuView.AdjustMode.NONE
            cursorView.invalidate()
        }
        radialMenu.onAction = { action ->
            when (action) {
                is RadialMenuView.MenuAction.AddEffect -> {
                    addEmitter(cursorClickX, cursorClickY, action.type, action.radius, action.intensity)
                    cursorView.showPreview = false; toMode(Mode.VIEW)
                }
                RadialMenuView.MenuAction.Remove -> {
                    hitEmitter?.let { removeEmitter(it) }
                    cursorView.showPreview = false; toMode(Mode.VIEW)
                }
                RadialMenuView.MenuAction.Dismiss -> {
                    cursorView.showPreview = false; toMode(Mode.VIEW)
                }
            }
        }
    }

    private fun setupEditor() {
        editorView.onChanged = { updateEditorPreview() }
        editorView.onAction = { action ->
            when (action) {
                EditorView.EditorAction.Confirm -> confirmEditor()
                EditorView.EditorAction.Move    -> enterMoveMode()
                EditorView.EditorAction.Remove  -> removeEditingEmitter()
                EditorView.EditorAction.Cancel  -> {
                    // Scarta le modifiche, torna a CURSOR
                    editingEmitter = null
                    cursorView.showPreview = false
                    toMode(Mode.CURSOR)
                }
            }
        }
    }

    // ── Editor esteso ─────────────────────────────────────────────────────────

    private fun openEditor() {
        val hit = findHit(cursorView.cx, cursorView.cy)
        editingEmitter = hit

        if (hit != null) {
            editorView.editorType      = hit.type
            editorView.editorShape     = hit.shape
            editorView.editorRadiusX   = hit.radiusX
            editorView.editorRadiusY   = hit.radiusY
            editorView.editorRotation  = hit.rotation
            editorView.editorIntensity = hit.intensity
            editorView.editingExisting = true
        } else {
            editorView.editorType      = EffectType.FIRE
            editorView.editorShape     = EmitterShape.CIRCLE
            editorView.editorRadiusX   = 0.08f
            editorView.editorRadiusY   = 0.08f
            editorView.editorRotation  = 0f
            editorView.editorIntensity = 0.5f
            editorView.editingExisting = false
        }
        editorView.resetSelection()
        toMode(Mode.EDITOR)
        updateEditorPreview()
    }

    private fun updateEditorPreview() {
        val rect = getCurrentImageRect()
        cursorView.previewShape     = editorView.editorShape
        cursorView.previewRadiusXPx = editorView.editorRadiusX * rect.width()
        cursorView.previewRadiusYPx = editorView.editorRadiusY * rect.height()
        cursorView.previewRotation  = editorView.editorRotation
        cursorView.previewIntensity = editorView.editorIntensity
        cursorView.showPreview      = true
        cursorView.invalidate()
    }

    private fun confirmEditor() {
        val rect = getCurrentImageRect()
        if (rect.isEmpty) return
        val nx = ((cursorView.cx - rect.left) / rect.width()).coerceIn(0f, 1f)
        val ny = ((cursorView.cy - rect.top)  / rect.height()).coerceIn(0f, 1f)

        val edited = editingEmitter
        val newEmitter = EffectEmitter(
            id        = edited?.id ?: System.nanoTime(),
            cx        = nx, cy = ny,
            type      = editorView.editorType,
            shape     = editorView.editorShape,
            radiusX   = editorView.editorRadiusX,
            radiusY   = editorView.editorRadiusY,
            rotation  = editorView.editorRotation,
            intensity = editorView.editorIntensity
        )

        synchronized(emitters) {
            val idx = emitters.indexOfFirst { it.id == newEmitter.id }
            if (idx >= 0) emitters[idx] = newEmitter else emitters.add(newEmitter)
        }
        particleView.emitters = emitters.toList()

        editingEmitter = null
        cursorView.showPreview = false
        toMode(Mode.CURSOR)
    }

    private fun removeEditingEmitter() {
        editingEmitter?.let { em ->
            synchronized(emitters) { emitters.removeAll { it.id == em.id } }
            particleView.emitters = emitters.toList()
        }
        editingEmitter = null
        cursorView.showPreview = false
        toMode(Mode.CURSOR)
    }

    // ── Modalità MOVE ──────────────────────────────────────────────────────────

    private fun enterMoveMode() {
        val em = editingEmitter
        if (em != null) {
            // Emitter esistente: porta il cursore sulla sua posizione attuale
            moveCxBackup = em.cx
            moveCyBackup = em.cy
            val rect = getCurrentImageRect()
            cursorView.cx = rect.left + em.cx * rect.width()
            cursorView.cy = rect.top  + em.cy * rect.height()
        }
        // Effetto nuovo (em == null): il cursore resta dov'è; MOVE sposta solo
        // l'area tratteggiata di anteprima. Nessun backup necessario.
        toMode(Mode.MOVE)
    }

    private fun handleMoveKey(keyCode: Int): Boolean {
        val em = editingEmitter   // null = effetto nuovo non ancora piazzato
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT  -> { cursorView.cx -= cursorView.step; clampCursor(); em?.let { updateMovedEmitter(it) }; return true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { cursorView.cx += cursorView.step; clampCursor(); em?.let { updateMovedEmitter(it) }; return true }
            KeyEvent.KEYCODE_DPAD_UP    -> { cursorView.cy -= cursorView.step; clampCursor(); em?.let { updateMovedEmitter(it) }; return true }
            KeyEvent.KEYCODE_DPAD_DOWN  -> { cursorView.cy += cursorView.step; clampCursor(); em?.let { updateMovedEmitter(it) }; return true }
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_DPAD_CENTER -> {
                if (em != null) {
                    // Emitter esistente: posizione confermata → torna a CURSOR
                    editingEmitter = null
                    cursorView.showPreview = false
                    toMode(Mode.CURSOR)
                } else {
                    // Effetto nuovo: l'anteprima è ora nella nuova posizione,
                    // torna all'editor per confermare/regolare prima di piazzarlo.
                    // Scarta il resto di questa pressione OK così non seleziona
                    // subito una voce dell'editor.
                    swallowEditorOpenEnter = true
                    toMode(Mode.EDITOR)
                    updateEditorPreview()
                }
                return true
            }
            KeyEvent.KEYCODE_BACK -> {
                if (em != null) {
                    // Ripristina posizione originale → torna a EDITOR
                    synchronized(emitters) {
                        val idx = emitters.indexOfFirst { it.id == em.id }
                        if (idx >= 0) {
                            emitters[idx] = emitters[idx].copy(cx = moveCxBackup, cy = moveCyBackup)
                            editingEmitter = emitters[idx]
                        }
                    }
                    particleView.emitters = emitters.toList()
                }
                // Per l'effetto nuovo non c'è nulla da ripristinare: torna all'editor
                toMode(Mode.EDITOR)
                updateEditorPreview()
                return true
            }
        }
        return false
    }

    private fun clampCursor() {
        val rect = cursorView.clampRect
        if (!rect.isEmpty) {
            cursorView.cx = cursorView.cx.coerceIn(rect.left, rect.right)
            cursorView.cy = cursorView.cy.coerceIn(rect.top,  rect.bottom)
        }
        cursorView.invalidate()
    }

    private fun updateMovedEmitter(em: EffectEmitter) {
        val rect = getCurrentImageRect()
        if (rect.isEmpty) return
        val nx = ((cursorView.cx - rect.left) / rect.width()).coerceIn(0f, 1f)
        val ny = ((cursorView.cy - rect.top)  / rect.height()).coerceIn(0f, 1f)
        synchronized(emitters) {
            val idx = emitters.indexOfFirst { it.id == em.id }
            if (idx >= 0) {
                emitters[idx] = emitters[idx].copy(cx = nx, cy = ny)
                editingEmitter = emitters[idx]
            }
        }
        particleView.emitters = emitters.toList()
    }

    // ── Mode switching ─────────────────────────────────────────────────────────

    private fun toMode(next: Mode, menuX: Float = 0f, menuY: Float = 0f) {
        mode = next
        cursorView.visibility = if (next == Mode.CURSOR || next == Mode.MENU ||
                                    next == Mode.EDITOR || next == Mode.MOVE) View.VISIBLE else View.GONE
        radialMenu.visibility = if (next == Mode.MENU)   View.VISIBLE else View.GONE
        editorView.visibility = if (next == Mode.EDITOR) View.VISIBLE else View.GONE

        when (next) {
            Mode.VIEW -> { hitEmitter = null; window.decorView.requestFocus() }
            Mode.CURSOR -> {
                val rect = getCurrentImageRect()
                cursorView.clampRect = rect
                if (cursorView.cx !in rect.left..rect.right ||
                    cursorView.cy !in rect.top..rect.bottom) {
                    cursorView.cx = rect.centerX(); cursorView.cy = rect.centerY()
                }
                cursorView.showPreview = false; cursorView.requestFocus()
            }
            Mode.MENU -> {
                radialMenu.selectedIndex = 0
                radialMenu.items = if (hitEmitter != null)
                    RadialMenuView.itemsForExisting()
                else
                    RadialMenuView.itemsForEmpty()
                radialMenu.invalidate(); radialMenu.requestFocus()
                val rect = getCurrentImageRect()
                cursorView.previewShape     = EmitterShape.CIRCLE
                cursorView.previewRadiusPx  = radialMenu.currentRadius * rect.width()
                cursorView.previewIntensity = radialMenu.currentIntensity
                cursorView.showPreview = true
            }
            Mode.EDITOR -> {
                editorView.requestFocus()
                // CursorView resta visibile sotto l'editor per la preview
            }
            Mode.MOVE -> {
                cursorView.clampRect = getCurrentImageRect()
                cursorView.requestFocus()
            }
        }
    }

    // ── Emitter helpers ───────────────────────────────────────────────────────

    private fun addEmitter(sx: Float, sy: Float, type: EffectType, radius: Float, intensity: Float) {
        val rect = getCurrentImageRect()
        if (rect.isEmpty) return
        val nx = ((sx - rect.left) / rect.width()).coerceIn(0f, 1f)
        val ny = ((sy - rect.top)  / rect.height()).coerceIn(0f, 1f)
        val e  = EffectEmitter(
            cx = nx, cy = ny, type = type,
            radiusX = radius, radiusY = radius, intensity = intensity
        )
        synchronized(emitters) { emitters.add(e) }
        particleView.emitters = emitters.toList()
    }

    private fun removeEmitter(e: EffectEmitter) {
        synchronized(emitters) { emitters.remove(e) }
        particleView.emitters = emitters.toList()
    }

    private fun findHit(sx: Float, sy: Float): EffectEmitter? {
        val rect = getCurrentImageRect()
        return emitters.firstOrNull { e ->
            val ex = rect.left + e.cx * rect.width()
            val ey = rect.top  + e.cy * rect.height()
            hypot(sx - ex, sy - ey) <= HIT_RADIUS
        }
    }
}
