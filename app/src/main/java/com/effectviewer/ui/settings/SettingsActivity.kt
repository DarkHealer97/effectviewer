package com.effectviewer.ui.settings

import android.os.Bundle
import android.view.KeyEvent
import androidx.appcompat.app.AppCompatActivity
import com.effectviewer.R
import com.effectviewer.data.AppSettings

/**
 * Impostazioni globali dell'app. Activity dedicata, navigabile con D-pad:
 *  SU/GIÙ  → scorre le voci
 *  SX/DX   → modifica il valore della voce selezionata
 *  BACK    → salva (già salvato a ogni modifica) e torna alla galleria
 *
 * I valori vengono scritti su AppSettings immediatamente a ogni modifica,
 * quindi non serve un salvataggio esplicito all'uscita.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var view: SettingsView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        view = findViewById(R.id.settingsView)
        view.load(this)
        view.requestFocus()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_BACK -> { finish(); return true }
            }
        }
        return super.dispatchKeyEvent(event)
    }
}
