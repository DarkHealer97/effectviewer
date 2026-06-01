# EffectViewer

An Android image viewer with animated particle effects, designed for a modded-Android projector and controlled entirely by remote (D-pad: arrows \+ OK \+ Back).

Open an image fullscreen, navigate with zoom and pan, and overlay animated visual effects (fire, smoke, magic, ice, and more) onto specific areas of the image. Effects are purely visual — they never alter the original file — but their configuration is saved and reloaded automatically between sessions.

## Features

- **Built-in file explorer** — browse internal storage, SD cards, and USB drives straight from the filesystem, without relying on MediaStore (handy on projectors that don't expose external volumes).  
- **Remote-controlled zoom and pan** — smooth D-pad control, distinguishing a short press (zoom) from a long press (continuous pan).  
- **Particle effects** — multiple effect types with dedicated physics: 🔥 Fire, ☠️ Toxic Gas, ✨ Embers, 💨 Smoke, 🔮 Magic, ❄️ Ice, ⬛ Blackout.  
- **Emitter shapes** — circle, ellipse, rectangle, and triangle, with configurable rotation.  
- **Extended editor** — a D-pad-navigable panel to adjust each effect's type, shape, radius, rotation, and intensity, with real-time preview.  
- **Automatic persistence** — the effect configuration for each image is saved locally and restored the next time it's opened.

## Tech stack

- **Language:** Kotlin  
- **Min SDK:** 21 (Android 5.0) — **Target SDK:** 34 (Android 14\)  
- **Build:** Gradle 8.1.1, Android Gradle Plugin 8.1.0, Kotlin 1.9.0, Java 17  
- **Main libraries:** AndroidX AppCompat, RecyclerView, Glide (image loading with downsampling)  
- **Particle rendering:** Android Canvas on a standard `View`  
- **Persistence:** app-private JSON file (no extra permissions required)

## Build

The project opens and builds in Android Studio:

1. Clone the repo: `git clone https://github.com/DarkHealer97/effectviewer.git`  
2. Open the folder in Android Studio.  
3. `Build → Clean Project`, then `Rebuild Project`.  
4. Run on an emulator or device.

For command-line builds you'll need the Gradle wrapper (`./gradlew assembleDebug`). If it's missing, regenerate it with `gradle wrapper` or open the project once in Android Studio.

## Project status

Under active development. Core functionality (gallery, viewer, zoom/pan, particle effects, persistence) is working. Recent additions: new effect types, emitter shapes, and the extended editor. In progress: a carousel menu and high-resolution reload while zooming.

## License

Released under the [MIT License](http://LICENSE). You're free to use, modify, and distribute this software, provided the copyright notice is retained.

## Buy me a coffee ☕

EffectViewer is a personal project I share freely. If you found it useful — or it just made you smile — you're welcome to buy me a coffee. Entirely optional, and much appreciated:

[**paypal.me/payAlpoca**](https://www.paypal.me/payAlpoca)  
