# Pitch Black Launcher 🚀

An ultra-lightweight, battery-efficient, high-performance Android Launcher built with **Jetpack Compose**.

Designed for minimalists who want maximum speed, zero battery drain, and clean aesthetics with pitch-black UI and customizable app layout.

---

## ✨ Features

- **Pitch Black Aesthetic**: True `#000000` dark mode optimized for OLED / AMOLED displays to save battery.
- **Custom Icon Support**: Dedicated high-clarity icon overrides for system & popular apps.
- **Dynamic 4-Column Grid**: Smooth, responsive app drawer layout with rounded app cards.
- **Drag-Free Reordering**: Tap-to-swap "Edit Layout" mode with persistent custom ordering.
- **Minimalist Header**: Live time (12h format), full date, and a dynamic color-coded battery indicator (Red / Orange / Green).
- **Fast Instant Search**: Filter installed apps instantly with zero lag.
- **App Details Dialog**: Long-press any app card to view package name, class name, app size, version, and quick access to System Settings.
- **Real-Time App List**: Automatically updates app list on package install or uninstall.
- **Work Profile Support**: Visual indicators for enterprise / work profile apps.

---

## ⚡ Performance & Optimizations

- **Pre-Rasterized GPU Bitmaps**: App icons are decoded once on background threads (`Dispatchers.IO`) and loaded directly into GPU VRAM (`Bitmap.Config.HARDWARE`). Zero main-thread decoding during scroll.
- **Compose Stability (`@Immutable`)**: All data models use `@Immutable` annotations, allowing Jetpack Compose to skip unnecessary recompositions.
- **Zero-Allocation Hot Paths**: Pre-computed search strings and grid keys prevent allocation garbage collection pauses during scroll or keystrokes.
- **Event-Driven & Polling-Free**: Time updates sleep to exact second boundaries; battery state uses system broadcast receivers without polling.
- **R8 Full Mode & Resource Shrinking**: Release builds use aggressive R8 dead code elimination, inlining, class merging, and unused resource stripping.
- **Embedded ART Startup Profile**: Includes `.dm` startup profiles for faster cold launch times.

---

## 📦 Download / Installation

Check the [Releases](https://github.com/arjncx-lang/pitch-black-launcher/releases) tab to download:
- `app-release.apk` — Ultra-optimized R8 release build (~2.4 MB)
- `app-debug.apk` — Uncompressed debug build (~13 MB)

---

## 🛠️ Build & Development

### Prerequisites
- Android Studio Ladybug / ME / Jellyfish or higher
- JDK 17 / Kotlin 2.2.10
- Android SDK 37 (Min SDK: 24, Target SDK: 37)

### Command Line Build
```bash
# Debug build
./gradlew assembleDebug

# Release build (signed with debug keystore for local testing)
./gradlew assembleRelease
```

---

## 📄 License

Distributed under the MIT License.
