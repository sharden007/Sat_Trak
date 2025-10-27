# Satellite Tracker - Real-Time 3D Visualization

A real-time satellite tracking application that displays the International Space Station (ISS), NOAA 19, and GPS BIIF-1 satellites orbiting a 3D Earth globe.

## Features

- **Real-time Satellite Tracking**: Fetches live satellite positions from the N2YO API every 10 seconds
- **Interactive 3D Globe**: Rotate and zoom the Earth using touch/mouse controls
- **Three Satellites**: Tracks ISS, NOAA 19, and GPS BIIF-1
- **Custom Splash Screen**: 3-second animated splash screen with app logo
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
- **Splash Screen**: AndroidX Core SplashScreen API

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

## Splash Screen Configuration

The app features a custom splash screen that displays for 3 seconds on app launch.

### Customizing the Splash Screen

**To adjust the logo size:**
1. Open `app/src/main/res/drawable/sat_trak_logo_scaled.xml`
2. Modify the `android:width` and `android:height` values (currently 120dp):
   - Smaller logo: 100dp x 100dp
   - Larger logo: 150dp x 150dp

**To change the display duration:**
1. Open `app/src/main/res/values/themes.xml`
2. Find the `Theme.Sat_Trak.SplashScreen` style
3. Modify `windowSplashScreenAnimationDuration` (currently 3000ms = 3 seconds)

**To replace the logo:**
1. Add your logo image to `app/src/main/res/drawable/`
2. Update `sat_trak_logo_scaled.xml` to reference your new logo

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

## Dependencies

Key dependencies used in this project:

- `androidx.core:core-splashscreen:1.0.1` - Splash screen API
- `com.squareup.retrofit2:retrofit` - HTTP client
- `com.squareup.retrofit2:converter-moshi` - JSON converter
- `com.squareup.moshi:moshi-kotlin` - JSON parsing
- `org.jetbrains.kotlinx:kotlinx-coroutines-android` - Coroutines
- Jetpack Compose BOM - UI framework

## Minimum Requirements

- **minSdk**: 27 (Android 8.1 Oreo)
- **targetSdk**: 36
- **compileSdk**: 36

## License

This project is open source and available for educational purposes.

---

**Note**: Replace `YOUR_API_KEY_HERE` in `SatelliteRepository.kt` before running the app!

## Continent Dataset (New)

A detailed, self-contained continent dataset is included for offline use and demos.

- File: `app/src/main/assets/continents.json`
- Models: `com.example.sat_trak.data.models.*` (see `ContinentModels.kt`)
- Loader/Validator: `com.example.sat_trak.data.repository.ContinentDataLoader`

Schema overview (per continent object):
- id: 2–3 letter code (e.g., "AF", "EU")
- name: Continent name
- areaKm2: Land area in square kilometers (Long)
- population: Mid-year population estimate (Long)
- populationYear: Year of the population estimate (Int)
- countriesCount: Number of sovereign states (Int)
- landlockedCountriesCount: Count of landlocked states (Int)
- coastLengthKm: Approximate coastline length (Int)
- densityPerKm2: Population density (Double)
- centroid: { lat, lon }
- boundingBox: { minLat, minLon, maxLat, maxLon }
- highestPoint: { name, elevationM, location{ lat, lon } }
- lowestPoint: { name, elevationM, location{ lat, lon } }
- extremities: northmost/southmost/eastmost/westmost, each a { name, location{ lat, lon } }
- subregions: [{ name, countriesCount }]
- majorOceans: [String]
- majorLanguages: [String]
- notes: Free-form notes

Quick usage (Android/Kotlin):
```kotlin
// Load from assets
val continents = ContinentDataLoader.loadFromAssets(context)

// Validate
val issuesById = ContinentDataLoader.validateAll(continents)
val nonDensityIssues = issuesById.mapValues { (_, v) -> v.filterNot { it.startsWith("densityPerKm2") } }
if (nonDensityIssues.isNotEmpty()) {
    // handle/report problems
}

// Example: find Europe
val europe = continents.firstOrNull { it.id == "EU" }
```

Notes
- All values are approximate and suitable for visualization and general reference (not authoritative).
- Validation tolerates density approximation differences due to rounding and heterogeneous sources.
- You can extend or replace `continents.json` with additional fields; update `ContinentModels.kt` accordingly.

### Run tests (Windows cmd)

```bat
cd C:\Users\Shawn\AndroidStudioProjects\Sat_Trak
gradlew.bat --no-daemon test --console=plain --rerun-tasks
```

Instrumented tests (require an emulator/device) will attempt to load the assets file and validate it.
