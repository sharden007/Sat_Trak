# Satellite Tracker - Real-Time 3D Visualization

A real-time satellite tracking application that displays the International Space Station (ISS), NOAA 19, and GPS BIIF-1 satellites orbiting a 3D Earth globe.

## Features

- **Real-time Satellite Tracking**: Fetches live satellite positions from the N2YO API every 10 seconds
- **Interactive 3D Globe**: Rotate and zoom the Earth using touch/mouse controls
- **Three Satellites**: Tracks ISS, NOAA 19, and GPS BIIF-1
- **MVVM Architecture**: Clean separation of concerns with ViewModel and Repository pattern
- **Jetpack Compose UI**: Modern Android UI toolkit
- **WebView with Three.js**: High-performance 3D rendering

## Technology Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Architecture**: MVVM (Model-View-ViewModel)
- **Networking**: Retrofit2 + Moshi
- **Async Processing**: Kotlin Coroutines
- **3D Rendering**: Three.js (via WebView)

## Setup Instructions

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
private val apiKey = "YOUR_API_KEY_HERE"  // Replace this with your actual API key
```

### 3. Build and Run

1. Open the project in Android Studio
2. Sync Gradle files (if not already synced)
3. Connect an Android device or start an emulator (API level 27 or higher)
4. Click "Run" or press Shift+F10

## Project Structure

```
app/src/main/java/com/example/sat_trak/
├── MainActivity.kt                          # Entry point
├── data/
│   ├── api/
│   │   └── N2YOApiService.kt               # Retrofit API interface
│   ├── models/
│   │   └── SatelliteModels.kt              # Data models
│   └── repository/
│       └── SatelliteRepository.kt          # Data layer
├── ui/
│   ├── components/
│   │   └── GlobeWebView.kt                 # 3D globe WebView component
│   ├── screens/
│   │   └── MainScreen.kt                   # Main screen composable
│   ├── theme/
│   │   ├── Color.kt                        # Theme colors
│   │   ├── Theme.kt                        # Material theme
│   │   └── Type.kt                         # Typography
│   └── viewmodel/
│       └── SatelliteViewModel.kt           # ViewModel layer
```

## How It Works

### Data Flow

1. **ViewModel** (`SatelliteViewModel`) starts a periodic update every 10 seconds
2. **Repository** (`SatelliteRepository`) fetches satellite positions from N2YO API
3. **Coordinate Conversion**: Lat/Lon/Alt is converted to Cartesian X/Y/Z coordinates
4. **UI Update**: WebView receives the satellite positions and renders them on the 3D globe

### Coordinate Conversion

The app converts geographic coordinates (latitude, longitude, altitude) to 3D Cartesian coordinates:

```
Earth Radius = 6371 km
Total Radius = Earth Radius + Altitude

X = Total Radius × cos(latitude) × cos(longitude)
Y = Total Radius × sin(latitude)
Z = -Total Radius × cos(latitude) × sin(longitude)
```

### API Configuration

- **Observer Location**: (0°, 0°) - Equator at Prime Meridian
- **Observer Altitude**: 0 meters
- **Update Interval**: 10 seconds
- **API Seconds Parameter**: 1 (current position only)

## Tracked Satellites

| Satellite | NORAD ID | Type |
|-----------|----------|------|
| ISS | 25544 | Space Station |
| NOAA 19 | 33591 | Weather Satellite |
| GPS BIIF-1 | 36585 | GPS Satellite |

## User Controls

### Mouse Controls
- **Click + Drag**: Rotate the globe
- **Scroll Wheel**: Zoom in/out

### Touch Controls
- **Single Finger Drag**: Rotate the globe
- **Pinch**: Zoom in/out (via scroll simulation)

## Requirements

- **Minimum SDK**: Android 8.1 (API 27)
- **Target SDK**: Android 15 (API 36)
- **Internet Connection**: Required for API calls
- **N2YO API Key**: Required (free tier available)

## Dependencies

```kotlin
// Compose
androidx.compose:compose-bom:2024.10.00
androidx.activity:activity-compose:1.9.3
androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6

// Networking
com.squareup.retrofit2:retrofit:2.9.0
com.squareup.retrofit2:converter-moshi:2.9.0

// JSON Parsing
com.squareup.moshi:moshi-kotlin:1.15.1

// Coroutines
org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0
```

## Troubleshooting

### API Errors
- **Check API Key**: Ensure you've replaced `YOUR_API_KEY_HERE` with your actual key
- **Rate Limiting**: Free tier has 1000 requests/hour limit
- **Network Connection**: Verify device has internet access

### Build Errors
- **Sync Gradle**: File → Sync Project with Gradle Files
- **Clean Build**: Build → Clean Project, then Build → Rebuild Project
- **Invalidate Caches**: File → Invalidate Caches / Restart

### WebView Issues
- **Enable JavaScript**: Already enabled in `GlobeWebView.kt`
- **Internet Permission**: Already added in `AndroidManifest.xml`

## Future Enhancements

- Add more satellites
- Display satellite names on the globe
- Add orbital path visualization
- Implement satellite search functionality
- Add location picker for observer position
- Display satellite details (speed, orbit time, etc.)
- Offline mode with cached data

## License

This is an educational project for classroom and museum use.

## Credits

- **N2YO API**: Satellite tracking data
- **Three.js**: 3D rendering library
- **Jetpack Compose**: Modern Android UI toolkit

---

**Note**: Replace `YOUR_API_KEY_HERE` in `SatelliteRepository.kt` before running the app!

