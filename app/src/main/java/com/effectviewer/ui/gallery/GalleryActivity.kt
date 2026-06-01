package com.effectviewer.ui.gallery

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.view.KeyEvent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.effectviewer.R
import com.effectviewer.data.AppSettings
import com.effectviewer.data.EffectsMemory
import com.effectviewer.ui.settings.SettingsActivity
import com.effectviewer.ui.viewer.ViewerActivity
import java.io.File

class GalleryActivity : AppCompatActivity() {

    private lateinit var recycler:   RecyclerView
    private lateinit var emptyLabel: TextView
    private lateinit var pathLabel:  TextView
    private lateinit var settingsButton: ImageView
    private lateinit var adapter:    FolderAdapter

    private val folderStack = ArrayDeque<File>()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.any { it }) openRoot()
        else showEmpty("Permesso negato.\nConcedi accesso nelle impostazioni.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gallery)

        recycler   = findViewById(R.id.recycler)
        emptyLabel = findViewById(R.id.emptyLabel)
        pathLabel  = findViewById(R.id.pathLabel)
        settingsButton = findViewById(R.id.settingsButton)

        settingsButton.setOnClickListener { openSettings() }
        settingsButton.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN &&
                (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)) {
                openSettings(); true
            } else false
        }

        adapter = FolderAdapter(
            onFolderClick = { folder -> navigateTo(folder) },
            onImageClick  = { file   -> handleImageClick(file) }
        )
        recycler.layoutManager = GridLayoutManager(this, 4)
        recycler.adapter = adapter

        checkPermissionsAndLoad()
    }

    // ── Click immagine: controlla storico effetti ─────────────────────────────

    private fun handleImageClick(file: File) {
        if (!EffectsMemory.hasHistory(this, file.absolutePath)) {
            openViewer(file, loadEffects = false)
            return
        }
        // C'è uno storico: il comportamento dipende dall'impostazione globale
        when (AppSettings.effectsOnOpen(this)) {
            AppSettings.EffectsOnOpen.ASK   -> showEffectsDialog(file)
            AppSettings.EffectsOnOpen.LOAD  -> openViewer(file, loadEffects = true)
            AppSettings.EffectsOnOpen.CLEAN -> {
                EffectsMemory.delete(this, file.absolutePath)
                openViewer(file, loadEffects = false)
            }
        }
    }

    private fun showEffectsDialog(file: File) {
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Effetti salvati")
            .setMessage("Questa immagine ha degli effetti impostati in una sessione precedente. Vuoi aprirla con gli effetti attivi o iniziare da zero?")
            .setPositiveButton("Con gli effetti") { _, _ ->
                openViewer(file, loadEffects = true)
            }
            .setNegativeButton("Inizia da zero") { _, _ ->
                EffectsMemory.delete(this, file.absolutePath)
                openViewer(file, loadEffects = false)
            }
            .setCancelable(true)
            .create()
        dialog.show()
    }

    private fun openViewer(file: File, loadEffects: Boolean) {
        // Raccoglie tutte le immagini nella stessa cartella per la navigazione SX/DX
        val siblings = try {
            file.parentFile?.listFiles()
                ?.filter { it.isFile && it.isImage() }
                ?.sortedBy { it.name.lowercase() }
                ?.map { it.absolutePath }
                ?: emptyList()
        } catch (e: Exception) { emptyList() }

        startActivity(Intent(this, ViewerActivity::class.java).apply {
            putExtra(ViewerActivity.EXTRA_URI,          Uri.fromFile(file).toString())
            putExtra(ViewerActivity.EXTRA_IMAGE_PATH,   file.absolutePath)
            putExtra(ViewerActivity.EXTRA_LOAD_EFFECTS, loadEffects)
            putStringArrayListExtra(ViewerActivity.EXTRA_IMAGE_LIST, ArrayList(siblings))
        })
    }

    // ── Permessi ──────────────────────────────────────────────────────────────

    private fun checkPermissionsAndLoad() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        else
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

        val allGranted = permissions.all {
            checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) openRoot() else permissionLauncher.launch(permissions)
    }

    // ── Volumi ────────────────────────────────────────────────────────────────

    private fun openRoot() {
        val volumes = getAvailableVolumes()
        if (volumes.size == 1) {
            navigateTo(volumes[0].second)
        } else {
            folderStack.clear()
            showItems(null, volumes.map { (label, file) -> FolderItem.Volume(file, label) })
            pathLabel.text = "📦 Seleziona volume"
            pathLabel.visibility = View.VISIBLE
        }
    }

    private fun getAvailableVolumes(): List<Pair<String, File>> {
        val result = mutableListOf<Pair<String, File>>()
        val sm = getSystemService(Context.STORAGE_SERVICE) as StorageManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val volumes: List<StorageVolume> = sm.storageVolumes
            for (vol in volumes) {
                val dir: File? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    vol.directory
                } else {
                    try {
                        val m = vol.javaClass.getMethod("getPathFile")
                        m.invoke(vol) as? File
                    } catch (e: Exception) { null }
                }
                if (dir != null && dir.canRead()) {
                    val label = when {
                        vol.isPrimary    -> "💾 Memoria interna"
                        vol.isRemovable  -> "🔌 ${vol.getDescription(this)}"
                        else             -> "📦 ${vol.getDescription(this)}"
                    }
                    result.add(label to dir)
                }
            }
        }

        if (result.isEmpty()) {
            val internal = Environment.getExternalStorageDirectory()
            if (internal.canRead()) result.add("💾 Memoria interna" to internal)
            listOf("/storage/sdcard1","/storage/extsdcard","/storage/usbdisk",
                "/storage/usb","/storage/usb0","/storage/usb1","/mnt/usb","/mnt/sdcard1")
                .forEach { path ->
                    val f = File(path)
                    if (f.exists() && f.canRead() && f.isDirectory)
                        result.add("🔌 $path" to f)
                }
            try {
                File("/storage").listFiles()?.forEach { vol ->
                    if (vol.isDirectory && vol.name != "emulated" && vol.name != "self" && vol.canRead())
                        if (result.none { it.second.absolutePath == vol.absolutePath })
                            result.add("🔌 ${vol.name}" to vol)
                }
            } catch (_: Exception) {}
        }
        return result
    }

    // ── Navigazione ───────────────────────────────────────────────────────────

    private fun navigateTo(folder: File) {
        folderStack.addLast(folder)
        loadFolder(folder)
    }

    private fun navigateUp() {
        when {
            folderStack.size > 1  -> { folderStack.removeLast(); loadFolder(folderStack.last()) }
            folderStack.size == 1 -> { folderStack.removeLast(); openRoot() }
            else -> finish()
        }
    }

    private fun loadFolder(folder: File) {
        val items = mutableListOf<FolderItem>()
        try {
            val files = folder.listFiles() ?: emptyArray()
            files.filter { it.isDirectory && !it.isHidden && it.canRead() }
                .sortedBy { it.name.lowercase() }
                .forEach { items.add(FolderItem.Folder(it)) }
            files.filter { it.isFile && it.isImage() }
                .sortedBy { it.name.lowercase() }
                .forEach { items.add(FolderItem.Image(it)) }
        } catch (e: Exception) { showEmpty("Errore lettura:\n${e.message}"); return }
        showItems(folder, items)
    }

    private fun showItems(folder: File?, items: List<FolderItem>) {
        if (items.isEmpty()) { showEmpty("Cartella vuota"); return }
        emptyLabel.visibility = View.GONE
        recycler.visibility   = View.VISIBLE
        adapter.setItems(items)
        pathLabel.text = folder?.let { "📁 ${buildDisplayPath(it)}" } ?: "📦 Volumi"
        pathLabel.visibility = View.VISIBLE
        recycler.scrollToPosition(0)
        recycler.post { recycler.getChildAt(0)?.requestFocus() }
    }

    private fun buildDisplayPath(folder: File): String {
        val volumes = try { getAvailableVolumes() } catch (e: Exception) { emptyList() }
        for ((_, root) in volumes) {
            try {
                val rel = folder.relativeTo(root).path
                return if (rel.isEmpty()) "/" else "/$rel"
            } catch (_: Exception) {}
        }
        return folder.absolutePath
    }

    private fun showEmpty(msg: String) {
        recycler.visibility   = View.GONE
        emptyLabel.visibility = View.VISIBLE
        emptyLabel.text       = msg
    }

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) { navigateUp(); return true }
        return super.onKeyDown(keyCode, event)
    }
}

private val IMAGE_EXTENSIONS = setOf("jpg","jpeg","png","gif","bmp","webp","heic","heif")
fun File.isImage() = extension.lowercase() in IMAGE_EXTENSIONS