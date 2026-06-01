package com.effectviewer.data

import android.content.Context
import com.effectviewer.model.EffectEmitter
import com.effectviewer.model.EmitterShape
import com.effectviewer.particles.EffectType
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Salva e carica gli emitter associati a ciascun percorso immagine.
 * Storage: file JSON privato dell'app, nessun permesso extra richiesto.
 *
 * Formato JSON con piena retrocompatibilità: i file salvati da versioni
 * precedenti contenevano solo "radius" (raggio singolo). Vengono letti
 * correttamente mappando "radius" su radiusX e radiusY.
 */
object EffectsMemory {

    private const val FILE_NAME = "effects_memory.json"

    // ── Salva ─────────────────────────────────────────────────────────────────

    fun save(context: Context, imagePath: String, emitters: List<EffectEmitter>) {
        val root = loadAll(context)
        if (emitters.isEmpty()) {
            root.remove(imagePath)
        } else {
            val arr = JSONArray()
            emitters.forEach { e ->
                arr.put(JSONObject().apply {
                    put("type",      e.type.name)
                    put("cx",        e.cx.toDouble())
                    put("cy",        e.cy.toDouble())
                    put("shape",     e.shape.name)
                    put("radiusX",   e.radiusX.toDouble())
                    put("radiusY",   e.radiusY.toDouble())
                    put("rotation",  e.rotation.toDouble())
                    put("intensity", e.intensity.toDouble())
                })
            }
            root.put(imagePath, arr)
        }
        writeAll(context, root)
    }

    // ── Carica ────────────────────────────────────────────────────────────────

    fun load(context: Context, imagePath: String): List<EffectEmitter> {
        val root = loadAll(context)
        val arr  = root.optJSONArray(imagePath) ?: return emptyList()
        val result = mutableListOf<EffectEmitter>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val type = try {
                EffectType.valueOf(obj.getString("type"))
            } catch (e: Exception) { continue }

            // Retrocompatibilità: i JSON vecchi hanno "radius", non radiusX/radiusY
            val legacyRadius = obj.optDouble("radius", 0.08).toFloat()
            val radiusX  = obj.optDouble("radiusX", legacyRadius.toDouble()).toFloat()
            val radiusY  = obj.optDouble("radiusY", legacyRadius.toDouble()).toFloat()
            val rotation = obj.optDouble("rotation", 0.0).toFloat()
            val shape    = try {
                EmitterShape.valueOf(obj.optString("shape", "CIRCLE"))
            } catch (e: Exception) { EmitterShape.CIRCLE }

            result.add(EffectEmitter(
                type      = type,
                cx        = obj.getDouble("cx").toFloat(),
                cy        = obj.getDouble("cy").toFloat(),
                shape     = shape,
                radiusX   = radiusX,
                radiusY   = radiusY,
                rotation  = rotation,
                intensity = obj.optDouble("intensity", 0.5).toFloat()
            ))
        }
        return result
    }

    // ── Controlla se esiste uno storico ───────────────────────────────────────

    fun hasHistory(context: Context, imagePath: String): Boolean {
        return loadAll(context).has(imagePath)
    }

    // ── Elimina lo storico di una immagine ────────────────────────────────────

    fun delete(context: Context, imagePath: String) {
        val root = loadAll(context)
        root.remove(imagePath)
        writeAll(context, root)
    }

    // ── I/O ───────────────────────────────────────────────────────────────────

    private fun storageFile(context: Context) =
        File(context.filesDir, FILE_NAME)

    private fun loadAll(context: Context): JSONObject {
        val f = storageFile(context)
        if (!f.exists()) return JSONObject()
        return try {
            JSONObject(f.readText())
        } catch (e: Exception) {
            JSONObject()
        }
    }

    private fun writeAll(context: Context, root: JSONObject) {
        try {
            storageFile(context).writeText(root.toString(2))
        } catch (e: Exception) {
            // Scrittura fallita — ignora silenziosamente
        }
    }
}
