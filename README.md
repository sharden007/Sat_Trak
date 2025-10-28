# Satellite Tracker - Native Android App 🛰️🌍📱

**A native Android application** featuring real-time satellite tracking with an interactive 3D Earth globe powered by Three.js and WebGL. Watch 5 satellites orbit Earth in real-time with beautiful orbital trail visualization.

> **Platform:** Android (Kotlin + Jetpack Compose)  
> **Minimum Android Version:** 8.1 Oreo (API 27)  
> **Target:** Android 14+ (API 36)

## 📸 Screenshots

<div align="center">
  <img src="screenshots/app-overview.png" alt="Android App Overview" width="800"/>
  <p><i>Native Android app - Real-time satellite tracking with 3D Earth globe</i></p>
</div>

<div align="center">
  <img src="screenshots/app-overview.png" alt="App Overview" width="400"/>
  <img src="screenshots/data-panel.png" alt="Telemetry Panel" width="400"/>
  <p><i>Left: Orbital trails visualization | Right: Auto-cycling telemetry display</i></p>
</div>

<div align="center">
  <img src="screenshots/satellite-details.png" alt="Satellite Details" width="400"/>
  <img src="screenshots/api-data.png" alt="API Data" width="400"/>
  <p><i>Left: Satellite detail sheet | Right: Live API data viewer</i></p>
</div>

## 📱 About This Android App

This is a **native Android application** built with modern Android development tools:
- Developed in **Kotlin** using **Jetpack Compose** for UI
- Runs on Android phones and tablets (API 27+)
- Requires **Android Studio** to build and run
- Uses WebView to embed Three.js for 3D rendering
- Designed for Android devices with internet connectivity

## ✨ Features

### Real-Time Tracking
- **5 Live Satellites**: ISS, NOAA 19, GPS BIIF-1, Starlink-1007, and Hubble Space Telescope
- **Live Position Updates**: Fetches real-time satellite positions from the N2YO API every 10 seconds
- **Auto-Cycling Telemetry**: Automatically displays telemetry data for each satellite (5 seconds each), with manual override on click

### Interactive 3D Visualization
- **Photorealistic Earth**: High-quality Blue Marble Earth texture for stunning realism
- **Orbital Trail Rendering**: Configurable orbital trails (130 steps) showing satellite paths with gradient fade effects
- **Interactive Globe**: Smooth rotation, zoom controls, and satellite click detection
- **Satellite Highlighting**: Green wireframe box highlights selected satellites
- **Toggle Controls**: Easy on/off toggle for orbital trails

### Advanced UI/UX
- **MVVM Architecture**: Clean separation with ViewModel and Repository pattern
- **Jetpack Compose**: Modern declarative UI with Material Design 3
- **Telemetry HUD**: Real-time speed, heading, and elevation data for each satellite
- **Detailed Satellite Info**: Bottom sheet with comprehensive orbital data and descriptions
- **Responsive Design**: Optimized layout with floating action buttons and smart positioning

## 🚀 Technology Stack

### Frontend
- **Kotlin** - Modern, concise Android development
- **Jetpack Compose** - Declarative UI framework with Material Design 3
- **WebView Integration** - Seamless Android-JavaScript bridge for 3D rendering

### 3D Rendering Engine
- **Three.js r128** - Industry-standard WebGL 3D library
- **Custom Shaders** - Phong material with specular highlights and emissive lighting
- **Raycasting** - Precise click detection on 3D satellite meshes
- **Dynamic Scene Updates** - Real-time satellite position updates without page reloads

### Backend & Data
- **Retrofit2** - Type-safe HTTP client for API communication
- **Moshi** - Fast JSON parsing with Kotlin reflection
- **Kotlin Coroutines** - Efficient asynchronous programming
- **N2YO API** - Professional satellite tracking data source

### Architecture
- **MVVM Pattern** - Model-View-ViewModel for clean architecture
- **Repository Pattern** - Centralized data management
- **LiveData/State** - Reactive state management with Compose
- **Coordinate Transformation** - Geographic to Cartesian conversion for 3D positioning

## 🎯 Key Capabilities

### Satellite Click & Selection
- Click any satellite on the globe to view detailed information
- Green highlight box tracks selected satellite
- Manual selection pauses auto-cycling telemetry
- Bottom sheet displays full orbital data and satellite description

### Orbital Trail System
- 130-step trail calculation using orbital mechanics
- Gradient opacity fading from satellite to trail end
- Real-time redraw on configuration changes
- Individual trail rendering per satellite with color coding

### Real-Time Data Processing
- Parallel API requests for all 5 satellites
- Lat/Lon/Alt to Cartesian (X/Y/Z) coordinate conversion
- Orbital speed and heading calculations
- 10-second refresh interval for live tracking

## 📦 Setup Instructions

### 1. Get N2YO API Key

1. Visit [N2YO.com](https://www.n2yo.com/api/)
2. Sign up for a free account
3. Generate an API key

### 2. Configure the API Key

Open the file:
```
app/src/main/java/com/example/sat_trak/data/repository/SatelliteRepository.kt
```

Replace the placeholder with your actual API key:
```kotlin
private val apiKey = "YOUR_API_KEY_HERE"  // Replace with your N2YO API key
```

### 3. Build and Run

1. Open the project in Android Studio
2. Sync Gradle files
3. Connect an Android device or start an emulator (API level 27+)
4. Click "Run" or press Shift+F10

## 🏗️ Project Structure

```
app/src/main/java/com/example/sat_trak/
├── MainActivity.kt                          # App entry point
├── data/
│   ├── api/
│   │   └── N2YOApiService.kt               # Retrofit API interface
│   ├── models/
│   │   ├── SatelliteModels.kt              # Satellite data models
│   │   └── ContinentModels.kt              # Geographic data models
│   └── repository/
│       ├── SatelliteRepository.kt          # Satellite data layer
│       └── ContinentDataLoader.kt          # Continent data loader
├── ui/
│   ├── components/
│   │   └── GlobeWebView.kt                 # 3D globe WebView with Three.js
│   ├── screens/
│   │   └── MainScreen.kt                   # Main UI composable
│   ├── theme/
│   │   ├── Color.kt                        # Material theme colors
│   │   ├── Theme.kt                        # Theme configuration
│   │   └── Type.kt                         # Typography definitions
│   └── viewmodel/
│       └── SatelliteViewModel.kt           # Business logic & state
```

## 🌐 Tracked Satellites

1. **ISS (25544)** - International Space Station
   - Type: Space Station
   - Color: Red
   - Orbit: Low Earth Orbit (~400 km)

2. **NOAA 19 (33591)** - Weather Satellite
   - Type: Environmental Monitoring
   - Color: Green
   - Orbit: Polar Sun-synchronous

3. **GPS BIIF-1 (36585)** - Navigation Satellite
   - Type: GPS Constellation
   - Color: Yellow
   - Orbit: Medium Earth Orbit (~20,200 km)

4. **STARLINK-1007 (43013)** - Communication Satellite
   - Type: Internet Constellation
   - Color: Yellow
   - Orbit: Low Earth Orbit (~550 km)

5. **HUBBLE (37820)** - Space Telescope
   - Type: Space Observatory
   - Color: Yellow
   - Orbit: Low Earth Orbit (~540 km)

## 📊 How It Works

### Data Flow
```
N2YO API → Repository → ViewModel → Compose UI → WebView → Three.js Rendering
    ↑                                                              ↓
    └──────────────── 10-second update cycle ─────────────────────┘
```

### Coordinate Transformation
Geographic coordinates are converted to 3D Cartesian for WebGL rendering:

```
Earth Radius = 6371 km
Total Radius = Earth Radius + Altitude

X = Total Radius × cos(latitude) × cos(longitude)
Y = Total Radius × sin(latitude)
Z = -Total Radius × cos(latitude) × sin(longitude)
```

### Orbital Trail Calculation
Trails are projected using simplified orbital mechanics:
- **Angular velocity** = Orbital Speed / Orbital Radius
- **30-second time steps** for smooth trail rendering
- **130 points** per trail with gradient opacity fading

## 🔧 Key Dependencies

```kotlin
// Compose & UI
implementation(platform("androidx.compose:compose-bom:2024.09.03"))
implementation("androidx.compose.material3:material3")

// Networking
implementation("com.squareup.retrofit2:retrofit:2.9.0")
implementation("com.squareup.retrofit2:converter-moshi:2.9.0")
implementation("com.squareup.moshi:moshi-kotlin:1.15.0")

// Async Processing
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

// 3D Rendering (via WebView)
Three.js r128 (loaded from CDN)
```

## 📱 System Requirements

- **minSdk**: 27 (Android 8.1 Oreo)
- **targetSdk**: 36
- **compileSdk**: 36
- **Internet Permission**: Required for API calls and texture loading

## 🎨 Features Highlights

### WebView-Native Integration
- **JavaScript Interface**: Bidirectional communication between Kotlin and JavaScript
- **Console Forwarding**: JavaScript console logs forwarded to Android Logcat
- **Thread-Safe Callbacks**: Satellite clicks posted to UI thread for safe state updates

### Performance Optimizations
- **Parallel API Requests**: All satellites fetched simultaneously with coroutines
- **Efficient Scene Updates**: Only trails are redrawn on configuration changes
- **Texture Caching**: Earth texture loaded once and cached by WebGL
- **Smooth Animations**: 60 FPS rendering with requestAnimationFrame

### User Experience
- **Auto-Cycling Display**: Hands-free monitoring of all satellites
- **Manual Override**: Click to lock on specific satellite
- **Rotation Speed Toggle**: Switch between simulated and real-time Earth rotation
- **Zoom Controls**: Easy zoom in/out with floating action buttons

## 📄 License

This project is open source and available for educational purposes.

---

**Note**: You must configure your N2YO API key in `SatelliteRepository.kt` before running the app. The free tier supports up to 1000 requests per hour, which is more than sufficient for this application's 10-second update interval.

## 🌍 Continent Dataset

A detailed continent dataset is included for enhanced geographic visualization:

- **File**: `app/src/main/assets/continents.json`
- **Data**: Area, population, boundaries, extremities, and geographic features
- **Usage**: Available for future map overlays and geographic features

For more details, see `ContinentModels.kt` and `ContinentDataLoader.kt`.
