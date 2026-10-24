# Mocka 🛰️ — Advanced Android Mock Location & GPS Telemetry Simulator

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android_8.0+_--_14+-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android" />
  <img src="https://img.shields.io/badge/Language-Kotlin_1.9-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin" />
  <img src="https://img.shields.io/badge/Build-Gradle_8.11_--_AGP_8.5-02303A?style=for-the-badge&logo=gradle&logoColor=white" alt="Gradle" />
  <img src="https://img.shields.io/badge/Version-v1.0.0-0EA5E9?style=for-the-badge" alt="Version" />
  <img src="https://img.shields.io/badge/Author-@kaiserabbas-10B981?style=for-the-badge&logo=github&logoColor=white" alt="Author" />
</p>

---

## 📖 Overview

**Mocka** is an enterprise-grade, high-fidelity Android Mock Location Provider and GPS Telemetry Simulator built for mobile engineers, QA specialists, and geolocation researchers. It enables real-time testing of geofences, proximity triggers, navigation routing, fitness tracking, and location-dependent apps without physical relocation.

Unlike basic GPS spoofer apps that inject static, rigid coordinates, Mocka implements **dynamic vector-based velocity progression**, **interactive D-Pad steering**, and **natural satellite drift (jitter)** to authentically replicate authentic device movement across Android 8.0 (API 26) through Android 14+ (API 34).

---

## ✨ Key Features

### 🛰️ Multi-Provider Mock Location Engine
- Concurrently registers and feeds high-accuracy mock telemetry into both `LocationManager.GPS_PROVIDER` and `LocationManager.NETWORK_PROVIDER`.
- Injects complete modern accuracy metadata on API 26+:
  - `bearingAccuracyDegrees`
  - `speedAccuracyMetersPerSecond`
  - `verticalAccuracyMeters`
  - Nanosecond-precision `elapsedRealtimeNanos` synchronization

### 🏃 Realistic Movement & Velocity Simulation
- **Dynamic Coordinate Progression**: Automatically calculates and advances latitude and longitude coordinates per second based on velocity ($v$) and heading angle ($\theta$):
  $$\Delta \text{lat} = \frac{v \cdot \Delta t \cdot \cos(\theta)}{111,139 \text{ m}}$$
  $$\Delta \text{lon} = \frac{v \cdot \Delta t \cdot \sin(\theta)}{111,139 \text{ m} \cdot \cos(\text{lat})}$$
- **One-Tap Speed Presets**:
  - `Static` (0 km/h — Stationary position)
  - `Walk` (5 km/h — Pedestrian pace)
  - `Cycle` (18 km/h — Urban commute)
  - `Drive` (60 km/h — City vehicular traffic)
  - `Highway` (110 km/h — Expressway velocity)

### 🌊 Authentic GPS Jitter & Satellite Drift
- Real-world satellite constellations naturally experience $\pm 1 \text{m}$ to $2.5 \text{m}$ micro-fluctuations due to atmospheric ionization.
- Mocka includes an optional **Satellite Drift Toggle** that introduces subtle, realistic Gaussian variance to avoid detection by anti-spoof heuristic algorithms.

### 🎮 Real-Time D-Pad Steer & Step Controls
- Steer mock coordinates dynamically in real time without stopping or editing inputs:
  - **▲ North (0°)**
  - **▶ East (90°)**
  - **▼ South (180°)**
  - **◀ West (270°)**
  - **● Pause / Stop Movement**
- Configurable step distance chips: **10m**, **50m**, **100m**, and **500m**.
- Quick compass rotation: $\pm 15^\circ$ heading trim buttons.

### 🌍 Global Landmark Presets & ⭐ Custom Bookmarks
- **Instant World Landmarks**:
  - 🇦🇪 Dubai: Burj Khalifa (`25.1972`, `55.2744`)
  - 🇺🇸 New York: Times Square (`40.7580`, `-73.9855`)
  - 🇬🇧 London: Big Ben (`51.5007`, `-0.1246`)
  - 🇯🇵 Tokyo: Shibuya Crossing (`35.6595`, `139.7005`)
  - 🇫🇷 Paris: Eiffel Tower (`48.8584`, `2.2945`)
  - 🇦🇺 Sydney: Sydney Opera House (`-33.8568`, `151.2153`)
  - 🇺🇸 San Francisco: Golden Gate Bridge (`37.8199`, `-122.4783`)
  - 🇸🇬 Singapore: Marina Bay Sands (`1.2847`, `103.8610`)
  - 🇮🇹 Rome: Colosseum (`41.8902`, `12.4922`)
  - 🇪🇬 Cairo: Pyramids of Giza (`29.9792`, `31.1342`)
- **Saved Favorites**: Save any custom location with a friendly label (persisted locally) for rapid regression testing.
- **Smart Clipboard Import**: One-tap parsing and loading of comma-separated or whitespace-separated coordinates from the clipboard.

### 📊 Live Telemetry HUD & Interactive Notification
- **Heads-Up Dashboard**: Real-time display of broadcasted coordinates, active speed, cardinal heading, cumulative distance traveled, and drift state.
- **Interactive Notification**: Ongoing status notification with a direct **Stop Spoofing** action button to terminate mocking directly from the notification tray.

---

## 📱 Quick Setup (Android Configuration)

To enable Mocka to broadcast mock locations system-wide on your Android device or emulator:

1. **Enable Developer Options**:
   - Open Android **Settings** > **About Phone**.
   - Tap **Build Number** 7 times until you see *"You are now a developer!"*.
2. **Select Mock Location App**:
   - Return to **Settings** > **System** > **Developer Options** (or search *"Mock location"*).
   - Find **Select mock location app**.
   - Choose **Mocka**.
3. **Launch Mocka**:
   - Open Mocka, pick a preset or enter target coordinates, and tap **Start Spoofing**!

---

## 📦 APK Installation

A pre-built, ready-to-install debug/release APK is packaged directly in the repository:

- **File Path**: [`release/mocka-v1.0.0.apk`](release/mocka-v1.0.0.apk)
- **Install via ADB**:
  ```bash
  adb install release/mocka-v1.0.0.apk
  ```
- Or transfer the APK directly to any Android device running Android 8.0 or newer.

---

## 🛠️ Tech Stack & Architecture

| Component | Specification |
|---|---|
| **Language** | Kotlin 1.9.24 |
| **Android SDK** | Min SDK 26 (Android 8.0) • Compile SDK 34 • Target SDK 34 (Android 14) |
| **Build System** | Gradle 8.11.1 • Android Gradle Plugin (AGP) 8.5.2 |
| **UI Architecture** | Material AppCompat Dark Theme • Responsive Card Layout • Custom Vector Drawables |
| **Background Processing**| Foreground Service (`ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION`) |
| **IPC & Telemetry** | Local broadcast intent communication • BroadcastReceiver telemetry sync |
| **Persistence** | SharedPreferences JSON bookmark store |

---

## 🏗️ Building from Source

To compile the application locally:

```bash
# Clone the repository
git clone https://github.com/Kaiserabbas/mocka.git
cd mocka

# Build debug APK
./gradlew assembleDebug

# Output APK location:
# app/build/outputs/apk/debug/app-debug.apk
```

---

## 👨‍💻 Author

**Kaiser Abbas**
- GitHub: [@kaiserabbas](https://github.com/Kaiserabbas)

---

## 📄 License

This project is licensed under the [MIT License](LICENSE).
