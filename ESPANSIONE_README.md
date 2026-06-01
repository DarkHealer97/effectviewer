# EffectViewer — Espansione Emitter & Menù (30-05-26)

Questo archivio contiene il progetto EffectViewer con le espansioni del sistema
emitter già integrate. È basato su `EffectViewer_stable_30-05-26`.

## Cosa è cambiato

### File NUOVI (2)
- `model/EmitterShape.kt` — enum delle 4 forme (CIRCLE, ELLIPSE, RECTANGLE, TRIANGLE)
- `ui/viewer/EditorView.kt` — editor esteso (pannello a lista navigabile con D-pad)

### File MODIFICATI (8)
- `particles/EffectType.kt` — aggiunto BLACKOUT (7° tipo)
- `model/EffectEmitter.kt` — nuovi campi shape, radiusX, radiusY, rotation
  (mantiene un getter `radius` = radiusX per compatibilità)
- `data/EffectsMemory.kt` — serializza i nuovi campi; legge i JSON vecchi
  (campo "radius" → radiusX/radiusY) in piena retrocompatibilità
- `particles/ParticleSystem.kt` — spawn per forma con rotazione, formula densità
  radiusX×radiusY, BLACKOUT saltato; when reso esaustivo
- `ui/viewer/ParticleView.kt` — screenRX/screenRY nel ticker, drawBlackout
- `ui/viewer/CursorView.kt` — anteprima forme ruotate (API single-radius mantenuta)
- `ui/viewer/ViewerActivity.kt` — stati EDITOR e MOVE, long press Enter, wiring editor
- `res/layout/activity_viewer.xml` — aggiunto overlay EditorView

### File INVARIATI rilevanti
- `ui/viewer/RadialMenuView.kt` — non modificato. BLACKOUT compare automaticamente
  nel menu rapido perché itera EffectType.values(). Il carosello a spirale con
  fade è un refinement separato, non incluso in questa iterazione.

## Come usare

1. Fai un backup del progetto attuale (o lavora su un branch Git).
2. Sovrascrivi i file con quelli di questo archivio (stessa struttura di cartelle).
3. In Android Studio: Build → Clean Project, poi Rebuild Project.
   (EffectEmitter è cambiata: serve rigenerare tutto ciò che dipende da lei.)
4. Avvia e prova:
   - Menu rapido (click breve Enter in CURSOR): verifica che BLACKOUT appaia
   - Editor esteso (long press Enter su punto vuoto): nuovo emitter con forma/rotazione
   - Editor su esistente (long press Enter su un emitter): pre-popolato, con Sposta/Rimuovi

## Note

- `local.properties` NON è incluso (contiene il path SDK locale della macchina
  originale). Android Studio lo rigenera al primo apri-progetto, oppure crealo con:
  `sdk.dir=/percorso/al/tuo/Android/sdk`
- `gradle/wrapper/gradle-wrapper.jar` e gli script `gradlew`/`gradlew.bat` non erano
  presenti nell'archivio originale. Se ti servono per build da riga di comando,
  rigenerali con `gradle wrapper` o apri semplicemente il progetto in Android Studio.
- Il file `model/EffectInstance.kt` contiene un enum EffectType legacy NON usato
  (codice morto). Lasciato intatto per non allargare lo scope.

## Comportamenti preservati dal progetto stabile
- ZOOM_MAX = 10.0, Glide override(1920, 1080)
- Navigazione immagini SX/DX a zoom naturale
- Tutta la logica zoom/pan con Matrix, salvataggio in onStop
