package com.example.sat_trak.ui.components

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.example.sat_trak.data.models.SatelliteData

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GlobeWebView(
    satellites: List<SatelliteData>,
    modifier: Modifier = Modifier,
    onSatelliteClick: (SatelliteData) -> Unit = {},
    onZoomControlsReady: ((zoomIn: () -> Unit, zoomOut: () -> Unit) -> Unit)? = null
) {
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowContentAccess = true
                settings.allowFileAccess = true

                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun onSatelliteClicked(satelliteId: Int) {
                        satellites.find { it.id == satelliteId }?.let { satellite ->
                            onSatelliteClick(satellite)
                        }
                    }
                }, "Android")

                loadDataWithBaseURL(
                    null,
                    getHtmlContent(),
                    "text/html",
                    "UTF-8",
                    null
                )
            }
        },
        update = { webView ->
            if (satellites.isNotEmpty()) {
                val satellitesJson = satellites.joinToString(",") {
                    """{"id":${it.id},"name":"${it.name}","x":${it.x},"y":${it.y},"z":${it.z},"lat":${it.latitude},"lon":${it.longitude},"alt":${it.altitude},"type":"${it.type}"}"""
                }
                webView.evaluateJavascript(
                    "updateSatellites([$satellitesJson]);",
                    null
                )
            }

            // Set up zoom controls
            onZoomControlsReady?.invoke(
                { webView.evaluateJavascript("zoomIn();", null) },
                { webView.evaluateJavascript("zoomOut();", null) }
            )
        }
    )

    LaunchedEffect(Unit) {
        // Initial setup
    }
}

private fun getHtmlContent(): String {
    return """
<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, user-scalable=no">
    <style>
        body { 
            margin: 0; 
            overflow: hidden; 
            background: linear-gradient(to bottom, #000000 0%, #0a0a2e 50%, #000000 100%);
            touch-action: none;
            font-family: Arial, sans-serif;
        }
        canvas { 
            display: block; 
            width: 100%;
            height: 100%;
        }
        #info {
            position: absolute;
            top: 10px;
            right: 10px;
            color: #ffffff;
            background: rgba(0, 0, 0, 0.7);
            padding: 10px;
            border-radius: 5px;
            font-size: 12px;
            max-width: 250px;
            pointer-events: none;
            display: none;
        }
        #instructions {
            position: absolute;
            bottom: 10px;
            left: 10px;
            color: #ffffff;
            background: rgba(0, 0, 0, 0.7);
            padding: 8px 12px;
            border-radius: 5px;
            font-size: 11px;
        }
        #rotationToggle {
            position: fixed;
            bottom: 20px;
            left: 50%;
            transform: translateX(-50%);
            background: rgba(0, 0, 0, 0.95);
            color: #ffffff;
            border: 3px solid #4CAF50;
            padding: 15px 25px;
            border-radius: 10px;
            font-size: 16px;
            cursor: pointer;
            transition: all 0.3s;
            font-weight: bold;
            z-index: 10000;
            box-shadow: 0 4px 15px rgba(76, 175, 80, 0.6);
            white-space: nowrap;
        }
        #rotationToggle:hover {
            background: rgba(76, 175, 80, 0.6);
            border-color: #66BB6A;
            transform: translateX(-50%) translateY(-3px);
            box-shadow: 0 6px 20px rgba(76, 175, 80, 0.8);
        }
        #rotationToggle:active {
            transform: translateX(-50%) scale(0.95);
        }
    </style>
</head>
<body>
    <div id="info"></div>
    <button id="rotationToggle">🌍 Rotation: Simulated Speed</button>
    <div id="instructions">🖱️ Drag to rotate • Scroll to zoom • Click satellites for info</div>
    
    <script src="https://cdnjs.cloudflare.com/ajax/libs/three.js/r128/three.min.js"></script>
    <script>
        let scene, camera, renderer, earth, earthGroup;
        let satelliteObjects = [];
        let satelliteLabels = [];
        let continentLabels = [];
        let isDragging = false;
        let previousMousePosition = { x: 0, y: 0 };
        let rotation = { x: 0.4, y: 0 };
        let raycaster = new THREE.Raycaster();
        let mouse = new THREE.Vector2();
        let currentSatellites = [];
        let useRealisticRotation = false; // Toggle for rotation mode
        let visualRotationSpeed = 0.0005; // Visual rotation speed

        function init() {
            scene = new THREE.Scene();
            
            camera = new THREE.PerspectiveCamera(60, window.innerWidth / window.innerHeight, 0.1, 50000);
            camera.position.z = 18000;

            renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true });
            renderer.setSize(window.innerWidth, window.innerHeight);
            renderer.setClearColor(0x000000, 1);
            document.body.appendChild(renderer.domElement);

            earthGroup = new THREE.Group();
            scene.add(earthGroup);

            // Create Earth sphere with deep blue oceans
            const geometry = new THREE.SphereGeometry(6371, 256, 256);
            
            // Create a canvas texture for the Earth with landmasses
            const canvas = document.createElement('canvas');
            canvas.width = 2048;
            canvas.height = 1024;
            const ctx = canvas.getContext('2d');
            
            // Fill with ocean blue
            ctx.fillStyle = '#1a4d8f';
            ctx.fillRect(0, 0, canvas.width, canvas.height);
            
            // Draw landmasses in tan/beige
            ctx.fillStyle = '#d4a673';
            ctx.strokeStyle = '#8b6914';
            ctx.lineWidth = 2;
            
            const continents = getContinentData();
            continents.forEach(continent => {
                ctx.beginPath();
                continent.coordinates.forEach((coord, i) => {
                    // Convert lat/lon to canvas coordinates
                    const x = ((coord[1] + 180) / 360) * canvas.width;
                    const y = ((90 - coord[0]) / 180) * canvas.height;
                    if (i === 0) {
                        ctx.moveTo(x, y);
                    } else {
                        ctx.lineTo(x, y);
                    }
                });
                ctx.closePath();
                ctx.fill();
                ctx.stroke();
            });
            
            const texture = new THREE.CanvasTexture(canvas);
            texture.needsUpdate = true;
            
            const material = new THREE.MeshPhongMaterial({
                map: texture,
                specular: 0x3366aa,
                shininess: 30,
                emissive: 0x0a2a5a,
                emissiveIntensity: 0.2
            });
            
            earth = new THREE.Mesh(geometry, material);
            earthGroup.add(earth);
            
            // Add continent labels
            addContinentLabels();
            
            // Add ocean labels
            addOceanLabels();

            // Lighting for depth and realism
            const ambientLight = new THREE.AmbientLight(0x404040, 2.5);
            scene.add(ambientLight);

            const directionalLight = new THREE.DirectionalLight(0xffffff, 1.8);
            directionalLight.position.set(10000, 5000, 5000);
            scene.add(directionalLight);

            const backLight = new THREE.DirectionalLight(0x6688ff, 0.6);
            backLight.position.set(-10000, -5000, -5000);
            scene.add(backLight);

            addStars();

            // Event listeners
            renderer.domElement.addEventListener('mousedown', onMouseDown);
            renderer.domElement.addEventListener('mousemove', onMouseMove);
            renderer.domElement.addEventListener('mouseup', onMouseUp);
            renderer.domElement.addEventListener('wheel', onWheel);
            renderer.domElement.addEventListener('click', onClick);
            renderer.domElement.addEventListener('touchstart', onTouchStart);
            renderer.domElement.addEventListener('touchmove', onTouchMove);
            renderer.domElement.addEventListener('touchend', onTouchEnd);
            window.addEventListener('resize', onWindowResize);

            // Rotation mode toggle
            document.getElementById('rotationToggle').addEventListener('click', () => {
                useRealisticRotation = !useRealisticRotation;
                updateRotationMode();
            });

            animate();
        }

        function updateRotationMode() {
            const button = document.getElementById('rotationToggle');
            if (useRealisticRotation) {
                button.innerHTML = '🌍 Rotation: Realistic';
                button.style.background = 'rgba(76, 175, 80, 0.8)';
            } else {
                button.innerHTML = '🌍 Rotation: Simulated';
                button.style.background = 'rgba(0, 0, 0, 0.8)';
            }
        }

        function addLandmasses() {
            const continents = getContinentData();
            
            continents.forEach(continent => {
                // Create 3D filled mesh on sphere surface
                createLandmassOnSphere(continent.coordinates);
            });
        }

        function createLandmassOnSphere(coordinates) {
            // Simple approach: Create individual triangles using earcut algorithm approximation
            // For now, use a simpler filled polygon approach
            
            const points = coordinates.map(coord => {
                return latLonToVector3(coord[0], coord[1], 6371 + 50);
            });
            
            // Use ShapeGeometry approach - create shape on plane then map to sphere
            const shape = new THREE.Shape();
            
            // Convert 3D points to 2D for shape (using lat/lon directly)
            coordinates.forEach((coord, i) => {
                const lat = coord[0];
                const lon = coord[1];
                if (i === 0) {
                    shape.moveTo(lon, lat);
                } else {
                    shape.lineTo(lon, lat);
                }
            });
            shape.closePath();
            
            const shapeGeometry = new THREE.ShapeGeometry(shape);
            const positions = shapeGeometry.attributes.position;
            
            // Map the 2D shape vertices to 3D sphere surface
            for (let i = 0; i < positions.count; i++) {
                const lon = positions.getX(i);
                const lat = positions.getY(i);
                const pos = latLonToVector3(lat, lon, 6371 + 50);
                positions.setXYZ(i, pos.x, pos.y, pos.z);
            }
            
            positions.needsUpdate = true;
            shapeGeometry.computeVertexNormals();
            
            // Bright, highly visible material
            const material = new THREE.MeshBasicMaterial({
                color: 0xe6d7b8,  // Light sandy beige
                side: THREE.DoubleSide,
                transparent: false
            });
            
            const mesh = new THREE.Mesh(shapeGeometry, material);
            earthGroup.add(mesh);
            
            // Add outline with higher altitude
            const outlinePoints = coordinates.map(coord => 
                latLonToVector3(coord[0], coord[1], 6371 + 55)
            );
            outlinePoints.push(outlinePoints[0].clone());
            
            const lineGeometry = new THREE.BufferGeometry().setFromPoints(outlinePoints);
            const lineMaterial = new THREE.LineBasicMaterial({ 
                color: 0x8b7355,  // Brown outline
                linewidth: 3
            });
            const line = new THREE.Line(lineGeometry, lineMaterial);
            earthGroup.add(line);
        }

        function getContinentData() {
            // More detailed and accurate continent outlines
            return [
                {
                    name: 'North America',
                    coordinates: [
                        [71.5, -156], [70, -141], [69, -135], [68, -133], [66, -139], [64, -141],
                        [60, -139], [58, -137], [56, -133], [54, -130], [52, -128], [49, -123],
                        [48.5, -125], [49, -127], [50, -128], [51, -128], [52, -131], [54, -133],
                        [58, -135], [60, -138], [61, -142], [62, -145], [64, -147], [65, -151],
                        [66, -156], [67, -161], [68, -165], [69, -168], [70, -172], [71, -177],
                        [70.5, 178], [69, 175], [66, 172], [63, 169], [60, 167], [58, 170],
                        [56, 172], [54, 169], [52, 166], [51, 162], [52, 158], [54, 154],
                        [57, 161], [60, 165], [62, 168], [63, 172], [64, 175], [65, 177],
                        [60, 170], [58, 165], [55, 160], [53, 156], [51, 152], [52, 148],
                        [54, 145], [56, 142], [58, 140], [60, 138], [62, 136], [64, 134],
                        [66, 132], [68, 135], [69, 140], [70, 145], [71, 150], [71.5, 154],
                        [71, 158], [70, 162], [69, 165], [68, 167], [67, 163], [66, 159],
                        [65, 155], [64, 151], [62, 147], [60, 144], [58, 141], [57, 138],
                        [60, -65], [58, -64], [56, -63], [54, -62], [52, -61], [50, -60],
                        [48, -58], [47.5, -52], [49, -50], [51, -52], [53, -55], [55, -58],
                        [57, -61], [59, -63], [61, -64], [63, -65], [65, -67], [67, -70],
                        [69, -73], [70, -77], [71, -82], [72, -87], [73, -92], [74, -97],
                        [75, -102], [76, -107], [77, -112], [78, -118], [79, -124], [79.5, -130],
                        [79, -136], [78, -142], [77, -148], [75, -153], [73, -158], [71.5, -156]
                    ]
                },
                {
                    name: 'South America',
                    coordinates: [
                        [12, -72], [11, -71], [10, -70], [8, -69], [6, -68], [4, -67],
                        [2, -66], [0, -65], [-2, -64], [-4, -63], [-6, -62], [-8, -61],
                        [-10, -60], [-12, -59], [-14, -58], [-16, -57.5], [-18, -57],
                        [-20, -58], [-22, -59], [-24, -60], [-26, -62], [-28, -64],
                        [-30, -66], [-32, -68], [-34, -69.5], [-36, -71], [-38, -72],
                        [-40, -73], [-42, -73.5], [-44, -74], [-46, -74.5], [-48, -75],
                        [-50, -75], [-52, -74.5], [-54, -74], [-55, -73], [-55.5, -72],
                        [-55, -71], [-54, -70], [-53, -69], [-52, -68.5], [-50, -68],
                        [-48, -67.5], [-46, -67], [-44, -66.5], [-42, -66], [-40, -65.5],
                        [-38, -65], [-36, -64.5], [-34, -64], [-32, -63.5], [-30, -63],
                        [-28, -62], [-26, -61], [-24, -60], [-22, -59], [-20, -58],
                        [-18, -57], [-16, -56.5], [-14, -56], [-12, -55.5], [-10, -55],
                        [-8, -55], [-6, -55.5], [-4, -56], [-2, -57], [0, -58],
                        [2, -60], [4, -62], [6, -64], [7, -66], [8, -68], [9, -70],
                        [10, -71], [11, -71.5], [12, -72]
                    ]
                },
                {
                    name: 'Africa',
                    coordinates: [
                        [37, 10], [36, 12], [34, 15], [32, 18], [30, 20], [28, 22],
                        [26, 25], [24, 28], [22, 30], [20, 32], [18, 34], [16, 36],
                        [14, 37], [12, 38], [10, 40], [8, 42], [6, 44], [4, 46],
                        [2, 48], [0, 50], [-2, 51], [-4, 51], [-6, 51], [-8, 51],
                        [-10, 50], [-12, 49], [-14, 48], [-16, 48], [-18, 48.5], [-20, 49],
                        [-22, 49.5], [-24, 50], [-26, 50], [-28, 49], [-30, 48], [-32, 46],
                        [-34, 44], [-35, 40], [-35, 36], [-34.5, 32], [-34, 28], [-33, 24],
                        [-32, 20], [-30, 18], [-28, 16], [-26, 15], [-24, 14], [-22, 14],
                        [-20, 14], [-18, 13.5], [-16, 13], [-14, 13], [-12, 13], [-10, 13],
                        [-8, 13.5], [-6, 14], [-4, 14.5], [-2, 15], [0, 15], [2, 15],
                        [4, 15.5], [6, 16], [8, 17], [10, 18], [12, 19], [14, 20],
                        [16, 22], [18, 24], [20, 26], [22, 28], [24, 30], [26, 32],
                        [28, 34], [30, 35], [32, 36], [33, 34], [34, 32], [35, 28],
                        [36, 24], [37, 20], [37.5, 16], [37, 12], [37, 10]
                    ]
                },
                {
                    name: 'Europe',
                    coordinates: [
                        [71, 25], [70, 28], [69, 30], [68, 33], [67, 36], [66, 40],
                        [65, 44], [64, 48], [63, 52], [62, 56], [60, 60], [58, 62],
                        [56, 64], [54, 65], [52, 66], [50, 65], [48, 64], [47, 62],
                        [46, 60], [45, 58], [44, 56], [43, 54], [42, 52], [41, 50],
                        [40, 48], [39, 46], [38, 44], [37, 42], [36.5, 40], [36, 38],
                        [36, 36], [36.5, 34], [37, 32], [37.5, 30], [38, 28], [38.5, 26],
                        [39, 24], [39.5, 22], [40, 20], [40, 18], [40, 16], [40, 14],
                        [40.5, 12], [41, 10], [41.5, 8], [42, 6], [42.5, 4], [43, 2],
                        [43.5, 0], [44, -2], [44.5, -4], [45, -6], [45.5, -8], [46, -9],
                        [47, -8], [48, -6], [49, -4], [50, -2], [51, 0], [52, 2],
                        [53, 4], [54, 6], [55, 8], [56, 10], [57, 12], [58, 14],
                        [59, 16], [60, 18], [62, 20], [64, 22], [66, 23], [68, 24],
                        [70, 24.5], [71, 25]
                    ]
                },
                {
                    name: 'Asia',
                    coordinates: [
                        [77, 70], [76, 75], [75, 80], [74, 85], [73, 90], [72, 95],
                        [71, 100], [70, 105], [69, 110], [68, 115], [67, 120], [66, 125],
                        [65, 130], [64, 135], [63, 140], [62, 145], [61, 150], [60, 155],
                        [58, 160], [56, 165], [54, 169], [52, 171], [50, 172], [48, 170],
                        [46, 168], [44, 166], [42, 164], [40, 162], [38, 160], [36, 158],
                        [34, 156], [32, 154], [30, 152], [28, 150], [26, 148], [24, 146],
                        [22, 144], [20, 142], [18, 140], [16, 138], [14, 136], [12, 134],
                        [10, 132], [8, 130], [6, 128], [4, 126], [2, 124], [0, 122],
                        [-2, 120], [-4, 118], [-6, 116], [-8, 114], [-10, 112], [-11, 110],
                        [-10, 108], [-8, 106], [-6, 104], [-4, 102], [-2, 100], [0, 98],
                        [2, 96], [4, 94], [6, 92], [8, 90], [10, 88], [12, 86],
                        [14, 84], [16, 82], [18, 80], [20, 78], [22, 76], [24, 74],
                        [26, 73], [28, 72], [30, 71], [32, 70.5], [34, 70], [36, 70],
                        [38, 71], [40, 72], [42, 74], [44, 76], [46, 78], [48, 80],
                        [50, 82], [52, 84], [54, 86], [56, 88], [58, 90], [60, 92],
                        [62, 94], [64, 96], [66, 98], [68, 100], [70, 102], [72, 105],
                        [74, 108], [75, 112], [76, 116], [77, 120], [77.5, 125], [77, 130],
                        [76, 135], [75, 138], [74, 140], [72, 138], [70, 135], [68, 132],
                        [66, 128], [65, 124], [64, 120], [63, 116], [62, 112], [61, 108],
                        [60, 104], [59, 100], [58, 96], [57, 92], [56, 88], [55, 84],
                        [55, 80], [56, 76], [58, 72], [60, 68], [62, 65], [64, 62],
                        [66, 60], [68, 58], [70, 57], [72, 58], [74, 60], [75, 64],
                        [76, 68], [77, 70]
                    ]
                },
                {
                    name: 'Australia',
                    coordinates: [
                        [-10, 142], [-11, 143], [-12, 144], [-13, 145], [-14, 146], [-16, 148],
                        [-18, 150], [-20, 152], [-22, 153], [-24, 153.5], [-26, 153.5], [-28, 153],
                        [-30, 152.5], [-32, 152], [-34, 151], [-36, 150], [-38, 148], [-39, 146],
                        [-38.5, 144], [-38, 142], [-37.5, 140], [-37, 138], [-36, 136], [-35, 134],
                        [-34, 132], [-33, 130], [-32, 128], [-31, 126], [-30, 125], [-28, 124],
                        [-26, 123.5], [-24, 123], [-22, 122.5], [-20, 122], [-18, 122], [-16, 122.5],
                        [-14, 123], [-12, 124], [-11, 126], [-10.5, 128], [-10, 130], [-10, 132],
                        [-10, 134], [-10, 136], [-10, 138], [-10, 140], [-10, 142]
                    ]
                },
                {
                    name: 'Antarctica',
                    coordinates: [
                        [-60, -60], [-62, -50], [-64, -40], [-66, -30], [-68, -20], [-70, -10],
                        [-72, 0], [-74, 10], [-76, 20], [-78, 30], [-80, 40], [-82, 50],
                        [-84, 60], [-85, 70], [-86, 80], [-87, 90], [-87, 100], [-86, 110],
                        [-85, 120], [-83, 130], [-81, 140], [-79, 150], [-77, 160], [-75, 170],
                        [-72, 180], [-70, -170], [-68, -160], [-66, -150], [-64, -140], [-62, -130],
                        [-61, -120], [-60, -110], [-60, -100], [-60, -90], [-60, -80], [-60, -70],
                        [-60, -60]
                    ]
                }
            ];
        }

        function latLonToVector3(lat, lon, radius) {
            const phi = (90 - lat) * (Math.PI / 180);
            const theta = (lon + 180) * (Math.PI / 180);
            
            const x = -(radius * Math.sin(phi) * Math.cos(theta));
            const y = radius * Math.cos(phi);
            const z = radius * Math.sin(phi) * Math.sin(theta);
            
            return new THREE.Vector3(x, y, z);
        }

        function addContinentLabels() {
            const continents = [
                { name: 'AFRICA', lat: 0, lon: 20, size: 2000 },
                { name: 'EUROPE', lat: 54, lon: 20, size: 1600 },
                { name: 'ASIA', lat: 50, lon: 100, size: 2200 },
                { name: 'NORTH\\nAMERICA', lat: 54, lon: -100, size: 1800 },
                { name: 'SOUTH\\nAMERICA', lat: -15, lon: -60, size: 1800 },
                { name: 'AUSTRALIA', lat: -25, lon: 135, size: 1800 },
                { name: 'ANTARCTICA', lat: -75, lon: 0, size: 2000 }
            ];

            continents.forEach(continent => {
                const canvas = document.createElement('canvas');
                const context = canvas.getContext('2d');
                canvas.width = 1024;
                canvas.height = 256;
                
                context.clearRect(0, 0, canvas.width, canvas.height);
                
                // Draw text with strong outline
                context.font = 'Bold 80px Arial';
                context.textAlign = 'center';
                context.textBaseline = 'middle';
                
                const lines = continent.name.split('\\n');
                
                // Black outline for visibility
                context.strokeStyle = 'rgba(0, 0, 0, 0.95)';
                context.lineWidth = 10;
                lines.forEach((line, i) => {
                    const y = 128 + (i - (lines.length - 1) / 2) * 90;
                    context.strokeText(line, 512, y);
                });
                
                // White text fill
                context.fillStyle = 'rgba(255, 255, 255, 1.0)';
                lines.forEach((line, i) => {
                    const y = 128 + (i - (lines.length - 1) / 2) * 90;
                    context.fillText(line, 512, y);
                });

                const texture = new THREE.Texture(canvas);
                texture.needsUpdate = true;

                const spriteMaterial = new THREE.SpriteMaterial({ 
                    map: texture,
                    transparent: true,
                    depthTest: false
                });
                const sprite = new THREE.Sprite(spriteMaterial);
                
                const pos = latLonToVector3(continent.lat, continent.lon, 6371 + 300);
                sprite.position.copy(pos);
                sprite.scale.set(continent.size, continent.size * 0.25, 1);
                
                earthGroup.add(sprite);
                continentLabels.push(sprite);
            });
        }

        function addOceanLabels() {
            const oceans = [
                { name: 'PACIFIC\\nOCEAN', lat: 0, lon: -160, size: 2400 },
                { name: 'ATLANTIC\\nOCEAN', lat: 15, lon: -35, size: 2200 },
                { name: 'INDIAN\\nOCEAN', lat: -20, lon: 75, size: 2200 },
                { name: 'ARCTIC\\nOCEAN', lat: 80, lon: 0, size: 2000 },
                { name: 'SOUTHERN\\nOCEAN', lat: -65, lon: 90, size: 2000 }
            ];

            oceans.forEach(ocean => {
                const canvas = document.createElement('canvas');
                const context = canvas.getContext('2d');
                canvas.width = 1024;
                canvas.height = 256;
                
                context.clearRect(0, 0, canvas.width, canvas.height);
                
                // Draw ocean names in blue/cyan
                context.font = 'Bold 70px Arial';
                context.textAlign = 'center';
                context.textBaseline = 'middle';
                
                const lines = ocean.name.split('\\n');
                
                // Dark outline
                context.strokeStyle = 'rgba(0, 20, 40, 0.9)';
                context.lineWidth = 8;
                lines.forEach((line, i) => {
                    const y = 128 + (i - (lines.length - 1) / 2) * 85;
                    context.strokeText(line, 512, y);
                });
                
                // Cyan/light blue fill for ocean names
                context.fillStyle = 'rgba(100, 200, 255, 0.9)';
                lines.forEach((line, i) => {
                    const y = 128 + (i - (lines.length - 1) / 2) * 85;
                    context.fillText(line, 512, y);
                });

                const texture = new THREE.Texture(canvas);
                texture.needsUpdate = true;

                const spriteMaterial = new THREE.SpriteMaterial({ 
                    map: texture,
                    transparent: true,
                    depthTest: false,
                    opacity: 0.85
                });
                const sprite = new THREE.Sprite(spriteMaterial);
                
                const pos = latLonToVector3(ocean.lat, ocean.lon, 6371 + 250);
                sprite.position.copy(pos);
                sprite.scale.set(ocean.size, ocean.size * 0.25, 1);
                
                earthGroup.add(sprite);
                continentLabels.push(sprite);
            });
        }

        function addStars() {
            const starGeometry = new THREE.BufferGeometry();
            const starMaterial = new THREE.PointsMaterial({ 
                color: 0xffffff, 
                size: 2,
                transparent: true,
                opacity: 0.8
            });

            const starVertices = [];
            for (let i = 0; i < 1000; i++) {
                const x = (Math.random() - 0.5) * 40000;
                const y = (Math.random() - 0.5) * 40000;
                const z = (Math.random() - 0.5) * 40000;
                starVertices.push(x, y, z);
            }

            starGeometry.setAttribute('position', new THREE.Float32BufferAttribute(starVertices, 3));
            const stars = new THREE.Points(starGeometry, starMaterial);
            scene.add(stars);
        }

        function onMouseDown(e) {
            isDragging = true;
            previousMousePosition = { x: e.clientX, y: e.clientY };
        }

        function onMouseMove(e) {
            if (isDragging) {
                const deltaX = e.clientX - previousMousePosition.x;
                const deltaY = e.clientY - previousMousePosition.y;
                
                rotation.y += deltaX * 0.005;
                rotation.x += deltaY * 0.005;
                
                rotation.x = Math.max(-Math.PI / 2, Math.min(Math.PI / 2, rotation.x));
                
                previousMousePosition = { x: e.clientX, y: e.clientY };
            }
        }

        function onMouseUp() {
            isDragging = false;
        }

        function onClick(e) {
            mouse.x = (e.clientX / window.innerWidth) * 2 - 1;
            mouse.y = -(e.clientY / window.innerHeight) * 2 + 1;

            raycaster.setFromCamera(mouse, camera);
            const intersects = raycaster.intersectObjects(satelliteObjects);

            if (intersects.length > 0) {
                const clickedSat = intersects[0].object;
                const satData = currentSatellites.find(s => s.id === clickedSat.userData.id);
                if (satData && typeof Android !== 'undefined') {
                    Android.onSatelliteClicked(satData.id);
                    showSatelliteInfo(satData);
                }
            }
        }

        function showSatelliteInfo(sat) {
            const infoDiv = document.getElementById('info');
            infoDiv.innerHTML = `
                <strong>🛰️ ${'$'}{sat.name}</strong><br>
                <em>${'$'}{sat.type}</em><br>
                <br>
                📍 Lat: ${'$'}{sat.lat.toFixed(2)}°<br>
                📍 Lon: ${'$'}{sat.lon.toFixed(2)}°<br>
                📏 Alt: ${'$'}{sat.alt.toFixed(0)} km<br>
                🆔 NORAD: ${'$'}{sat.id}
            `;
            infoDiv.style.display = 'block';
            
            setTimeout(() => {
                infoDiv.style.display = 'none';
            }, 5000);
        }

        function onTouchStart(e) {
            if (e.touches.length === 1) {
                isDragging = true;
                previousMousePosition = { x: e.touches[0].clientX, y: e.touches[0].clientY };
            }
        }

        function onTouchMove(e) {
            e.preventDefault();
            if (isDragging && e.touches.length === 1) {
                const deltaX = e.touches[0].clientX - previousMousePosition.x;
                const deltaY = e.touches[0].clientY - previousMousePosition.y;
                
                rotation.y += deltaX * 0.005;
                rotation.x += deltaY * 0.005;
                
                rotation.x = Math.max(-Math.PI / 2, Math.min(Math.PI / 2, rotation.x));
                
                previousMousePosition = { x: e.touches[0].clientX, y: e.touches[0].clientY };
            }
        }

        function onTouchEnd() {
            isDragging = false;
        }

        function onWheel(e) {
            e.preventDefault();
            camera.position.z += e.deltaY * 5;
            camera.position.z = Math.max(9000, Math.min(30000, camera.position.z));
        }

        function onWindowResize() {
            camera.aspect = window.innerWidth / window.innerHeight;
            camera.updateProjectionMatrix();
            renderer.setSize(window.innerWidth, window.innerHeight);
        }

        function zoomIn() {
            camera.position.z = Math.max(9000, camera.position.z - 1000);
        }

        function zoomOut() {
            camera.position.z = Math.min(30000, camera.position.z + 1000);
        }

        function updateSatellites(satellites) {
            currentSatellites = satellites;
            
            satelliteObjects.forEach(obj => earthGroup.remove(obj));
            satelliteLabels.forEach(label => earthGroup.remove(label));
            satelliteObjects = [];
            satelliteLabels = [];

            satellites.forEach(sat => {
                const geometry = new THREE.SphereGeometry(120, 16, 16);
                const material = new THREE.MeshBasicMaterial({ 
                    color: sat.id === 25544 ? 0xff0000 : (sat.id === 33591 ? 0x00ff00 : 0xffff00)
                });
                const sphere = new THREE.Mesh(geometry, material);
                sphere.position.set(sat.x, sat.y, sat.z);
                sphere.userData = { id: sat.id };
                earthGroup.add(sphere);
                satelliteObjects.push(sphere);

                const canvas = document.createElement('canvas');
                const context = canvas.getContext('2d');
                canvas.width = 256;
                canvas.height = 64;
                context.fillStyle = 'rgba(0, 0, 0, 0.7)';
                context.fillRect(0, 0, canvas.width, canvas.height);
                context.font = 'Bold 20px Arial';
                context.fillStyle = 'white';
                context.textAlign = 'center';
                context.fillText(sat.name, 128, 40);

                const texture = new THREE.Texture(canvas);
                texture.needsUpdate = true;

                const spriteMaterial = new THREE.SpriteMaterial({ 
                    map: texture,
                    transparent: true
                });
                const sprite = new THREE.Sprite(spriteMaterial);
                sprite.position.set(sat.x, sat.y + 300, sat.z);
                sprite.scale.set(800, 200, 1);
                earthGroup.add(sprite);
                satelliteLabels.push(sprite);
            });
        }

        function animate() {
            requestAnimationFrame(animate);
            
            earthGroup.rotation.x = rotation.x;
            
            if (useRealisticRotation) {
                // Realistic rotation: Earth rotates 360° in 24 hours
                const now = Date.now();
                const earthRotationRate = (2 * Math.PI) / 86400000; // radians per millisecond
                const millisecondsInDay = 86400000;
                const currentTimeInDay = now % millisecondsInDay;
                const earthAutoRotation = (currentTimeInDay * earthRotationRate);
                earthGroup.rotation.y = rotation.y + earthAutoRotation;
            } else {
                // Visual rotation: Fast visual speed for better viewing
                earthGroup.rotation.y += visualRotationSpeed;
                rotation.y += visualRotationSpeed;
            }
            
            renderer.render(scene, camera);
        }

        init();
    </script>
</body>
</html>
    """.trimIndent()
}
