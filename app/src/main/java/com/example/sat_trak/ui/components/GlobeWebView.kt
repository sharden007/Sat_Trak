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
            // Highly detailed and accurate continent outlines based on real geographic data
            return [
                {
                    name: 'North America',
                    coordinates: [
                        // Starting from Alaska's western tip
                        [51, -178], [52, -177], [53, -176], [54, -175], [55, -174], [56, -173],
                        [57, -172], [58, -171], [59, -170], [60, -169], [61, -168], [62, -167],
                        [63, -166], [64, -165], [65, -164], [66, -163], [67, -162], [68, -161],
                        [69, -160], [70, -159], [71, -158], [70, -157], [69, -156], [68, -155],
                        [67, -154], [66, -153], [65, -152], [64, -151], [63, -150], [62, -149],
                        
                        // Alaska North Slope
                        [70, -156], [71, -154], [70, -152], [69, -150], [68, -148], [67, -146],
                        [66, -144], [65, -142], [64, -141], [65, -140], [66, -139], [67, -138],
                        [68, -137], [69, -136], [70, -135], [69, -134], [68, -133], [67, -132],
                        
                        // Alaska/Yukon border area
                        [66, -141], [65, -140], [64, -139], [63, -138], [62, -137], [61, -136],
                        [60, -135], [59, -134], [60, -133], [61, -132], [62, -131], [63, -130],
                        [64, -129], [63, -128], [62, -127], [61, -126], [60, -125],
                        
                        // British Columbia coast (detailed)
                        [59, -135], [58, -134], [57, -133], [56, -132], [55, -131], [54, -130],
                        [53, -129], [52, -128], [51, -127], [50, -126], [49, -125], [48, -124],
                        [49, -123], [50, -124], [51, -125], [52, -126], [53, -127], [54, -128],
                        [55, -129], [56, -130], [57, -131], [58, -132], [59, -133],
                        
                        // Queen Charlotte Islands and Vancouver Island region
                        [54, -133], [53, -132], [52, -131], [51, -130], [50, -129], [51, -128],
                        [52, -129], [53, -130], [54, -131],
                        
                        // Northwest Territories - Arctic Archipelago
                        [70, -120], [71, -115], [72, -110], [73, -105], [74, -100], [75, -95],
                        [76, -90], [77, -85], [78, -80], [79, -75], [80, -70], [79, -65],
                        [78, -70], [77, -75], [76, -80], [75, -85], [74, -90], [73, -95],
                        [72, -100], [71, -105], [70, -110],
                        
                        // Hudson Bay (detailed outline)
                        [64, -95], [63, -94], [62, -93], [61, -92], [60, -91], [59, -90],
                        [58, -89], [57, -88], [56, -87], [55, -86], [56, -85], [57, -84],
                        [58, -83], [59, -82], [60, -81], [61, -80], [62, -79], [63, -78],
                        [64, -77], [63, -76], [62, -77], [61, -78], [60, -79], [59, -80],
                        [58, -81], [57, -82], [56, -83], [57, -84], [58, -85], [59, -86],
                        [60, -87], [61, -88], [62, -89], [63, -90], [64, -91], [64, -92],
                        [63, -93], [64, -94],
                        
                        // Northern Canada mainland
                        [69, -130], [68, -125], [67, -120], [66, -115], [65, -110], [64, -105],
                        [63, -100], [62, -95], [61, -90], [62, -85], [63, -80], [64, -75],
                        
                        // Labrador and Newfoundland region
                        [60, -64], [59, -63], [58, -62], [57, -61], [56, -60], [55, -59],
                        [54, -58], [53, -57], [52, -56], [51, -55], [50, -56], [49, -57],
                        [48, -58], [47, -59], [48, -60], [49, -61], [50, -62], [51, -63],
                        [52, -64], [53, -65], [54, -66], [55, -67], [56, -68], [57, -69],
                        [58, -70], [59, -71], [60, -72],
                        
                        // Quebec and Maritime Provinces
                        [52, -66], [51, -65], [50, -64], [49, -63], [48, -62], [47, -61],
                        [46, -60], [45, -61], [46, -62], [47, -63], [48, -64], [49, -65],
                        
                        // Great Lakes region (detailed)
                        // Lake Superior
                        [49, -89], [48, -88], [47, -87], [46, -86], [47, -85], [48, -84],
                        [49, -85], [49, -86], [49, -87], [49, -88],
                        
                        // Lake Michigan
                        [46, -87], [45, -86], [44, -85], [43, -84], [42, -85], [43, -86],
                        [44, -87], [45, -88], [46, -88],
                        
                        // Lake Huron
                        [46, -84], [45, -83], [44, -82], [43, -81], [44, -80], [45, -81],
                        [46, -82], [46, -83],
                        
                        // Lake Erie
                        [42, -83], [42, -82], [42, -81], [42, -80], [42, -79], [41, -80],
                        [41, -81], [41, -82], [42, -83],
                        
                        // Lake Ontario
                        [44, -79], [44, -78], [44, -77], [44, -76], [43, -76], [43, -77],
                        [43, -78], [43, -79], [44, -79],
                        
                        // Eastern seaboard (highly detailed)
                        [47, -70], [46, -69], [45, -68], [44, -67], [43, -66], [44, -65],
                        [45, -66], [46, -67], [47, -68], [48, -69],
                        
                        // Nova Scotia
                        [45, -64], [44, -63], [43, -62], [44, -61], [45, -62], [46, -63],
                        [45, -64],
                        
                        // New England coast
                        [44, -70], [43, -69], [42, -68], [41, -69], [42, -70], [43, -71],
                        
                        // US East Coast detailed
                        [45, -67], [44, -68], [43, -69], [42, -70], [41, -71], [40, -72],
                        [39, -73], [38, -74], [37, -75], [36, -76], [35, -77], [34, -78],
                        [33, -79], [32, -80], [31, -81], [30, -81],
                        
                        // Florida peninsula (both coasts)
                        [29, -81], [28, -81], [27, -80], [26, -80], [25, -80], [24, -81],
                        [25, -82], [26, -82], [27, -83], [28, -83], [29, -84], [30, -84],
                        
                        // Gulf Coast detailed
                        [30, -85], [30, -86], [30, -87], [30, -88], [30, -89], [29, -90],
                        [29, -91], [29, -92], [29, -93], [28, -94], [28, -95], [27, -96],
                        [26, -97], [25, -98], [24, -99], [23, -100], [22, -101],
                        
                        // Texas coast
                        [28, -96], [27, -97], [26, -98], [25, -99], [26, -100], [27, -101],
                        [28, -102], [29, -103], [30, -104], [31, -105], [32, -106],
                        
                        // Mexico west coast (detailed)
                        [32, -117], [31, -116], [30, -115], [29, -114], [28, -113], [27, -112],
                        [26, -111], [25, -110], [24, -109], [23, -108], [22, -107], [21, -106],
                        [20, -105], [19, -104], [18, -103], [17, -102], [16, -101], [15, -100],
                        
                        // Baja California
                        [32, -117], [31, -116], [30, -115], [29, -114], [28, -113], [27, -112],
                        [26, -111], [25, -110], [24, -109], [23, -108], [24, -107], [25, -108],
                        [26, -109], [27, -110], [28, -111], [29, -112], [30, -113], [31, -114],
                        [32, -115], [32, -116],
                        
                        // Central America
                        [17, -93], [16, -92], [15, -91], [14, -90], [13, -89], [12, -88],
                        [11, -87], [10, -86], [9, -85], [8, -84], [9, -83], [10, -82],
                        [9, -81], [8, -80], [9, -79],
                        
                        // California coast (detailed)
                        [42, -124], [41, -124], [40, -124], [39, -124], [38, -123], [37, -122],
                        [36, -121], [35, -121], [34, -120], [33, -118], [32, -117],
                        
                        // Pacific Northwest
                        [49, -125], [48, -124], [47, -124], [46, -124], [45, -124], [44, -124],
                        [43, -124], [42, -124],
                        
                        // Back to Alaska to close
                        [60, -165], [59, -166], [58, -167], [57, -168], [56, -169], [55, -170],
                        [54, -171], [53, -172], [52, -173], [51, -174], [52, -175], [53, -176],
                        [54, -177], [53, -178], [52, -179], [51, -180], [52, 180], [53, 179],
                        [54, 178], [53, 177], [52, 176], [51, 175], [52, 174], [53, 173],
                        [52, 172], [51, 171], [50, 170], [51, 169], [52, 168], [51, 167],
                        [50, 166], [51, 165], [52, 164], [51, 163], [50, 162], [51, 161],
                        [52, 160], [51, 159], [50, 158], [51, 157], [52, 156], [51, 155],
                        [50, 154], [51, 153], [52, 152], [51, 151], [50, 150], [51, 149],
                        [52, 148], [51, 147], [50, 146], [51, 145], [52, 144], [51, 143],
                        [50, 142], [51, 141], [52, 140], [51, 139], [50, 138], [51, 137],
                        [52, 136], [51, 135], [50, 134], [51, 133], [52, 132], [51, 131],
                        [50, 130], [51, 129], [52, 128], [51, 127], [50, 126], [51, 125],
                        [51, -178]
                    ]
                },
                {
                    name: 'South America',
                    coordinates: [
                        // Colombia/Venezuela
                        [12, -72], [11, -71], [10, -72], [9, -73], [8, -74], [7, -75], [6, -77],
                        [5, -78], [4, -79], [3, -79], [2, -80], [1, -80], [0, -79], [-1, -78],
                        [-2, -77], [-3, -76], [-4, -76], [-5, -77], [-6, -78], [-7, -79],
                        
                        // Peru
                        [-8, -79], [-9, -79], [-10, -78], [-11, -77], [-12, -77], [-13, -76],
                        [-14, -76], [-15, -75], [-16, -74], [-17, -73], [-18, -71],
                        
                        // Chile
                        [-19, -70], [-20, -70], [-21, -70], [-22, -70], [-23, -71], [-24, -71],
                        [-25, -71], [-26, -71], [-27, -71], [-28, -71], [-29, -71], [-30, -71],
                        [-31, -71], [-32, -72], [-33, -72], [-34, -72], [-35, -72], [-36, -73],
                        [-37, -73], [-38, -73], [-39, -74], [-40, -74], [-41, -74], [-42, -74],
                        [-43, -74], [-44, -74], [-45, -74], [-46, -75], [-47, -75], [-48, -75],
                        [-49, -75], [-50, -75], [-51, -75], [-52, -74], [-53, -73], [-54, -72],
                        [-55, -70], [-55, -68],
                        
                        // Argentina east coast
                        [-54, -67], [-53, -66], [-52, -65], [-51, -64], [-50, -64], [-49, -63],
                        [-48, -62], [-47, -61], [-46, -61], [-45, -60], [-44, -60], [-43, -60],
                        [-42, -61], [-41, -62], [-40, -62], [-39, -62], [-38, -61], [-37, -60],
                        [-36, -58], [-35, -57], [-34, -56], [-33, -55], [-32, -55], [-31, -55],
                        
                        // Uruguay/Brazil south
                        [-30, -54], [-29, -52], [-28, -51], [-27, -50], [-26, -49], [-25, -48],
                        [-24, -47], [-23, -46], [-22, -45], [-21, -44], [-20, -43], [-19, -42],
                        [-18, -41], [-17, -40], [-16, -40], [-15, -39],
                        
                        // Brazil east coast (bulge)
                        [-14, -39], [-13, -39], [-12, -38], [-11, -38], [-10, -37], [-9, -36],
                        [-8, -35], [-7, -35], [-6, -35], [-5, -35], [-4, -36], [-3, -37],
                        [-2, -38], [-1, -40], [0, -42], [1, -44], [2, -46],
                        
                        // North Brazil coast
                        [3, -48], [4, -50], [5, -51], [4, -53], [3, -55], [2, -57], [1, -59],
                        [0, -60], [1, -62], [2, -63], [3, -64], [4, -65], [5, -66], [6, -67],
                        [7, -68], [8, -69], [9, -70], [10, -71], [11, -71], [12, -72]
                    ]
                },
                {
                    name: 'Africa',
                    coordinates: [
                        // Morocco
                        [35, -6], [34, -7], [33, -8], [32, -9], [31, -10], [30, -11], [29, -11],
                        [28, -11], [27, -12], [26, -13], [25, -14], [24, -15], [23, -16], [22, -16],
                        
                        // West Africa coast
                        [21, -17], [20, -17], [19, -17], [18, -16], [17, -16], [16, -16], [15, -16],
                        [14, -17], [13, -17], [12, -16], [11, -15], [10, -15], [9, -14], [8, -13],
                        [7, -12], [6, -11], [5, -10], [4, -9], [5, -8], [6, -7], [5, -6],
                        [4, -5], [3, -4], [2, -3], [1, -2], [0, -1], [-1, 0], [0, 1],
                        [1, 2], [0, 3], [-1, 4], [-2, 5], [-3, 6], [-4, 7], [-5, 8],
                        
                        // Central Africa west
                        [-6, 9], [-7, 10], [-8, 11], [-9, 12], [-10, 13], [-11, 13], [-12, 13],
                        [-13, 13], [-14, 12], [-15, 12], [-16, 12], [-17, 13], [-18, 14],
                        
                        // Southwest Africa
                        [-19, 14], [-20, 14], [-21, 14], [-22, 14], [-23, 14], [-24, 14], [-25, 14],
                        [-26, 14], [-27, 15], [-28, 16], [-29, 16], [-30, 16], [-31, 17], [-32, 18],
                        [-33, 18], [-34, 18], [-34, 19], [-34, 20], [-33, 22], [-32, 24],
                        
                        // South Africa
                        [-31, 26], [-30, 28], [-29, 29], [-28, 30], [-27, 31], [-26, 32], [-25, 33],
                        [-24, 33], [-23, 33], [-22, 33], [-23, 32], [-24, 31], [-25, 30], [-26, 29],
                        [-27, 28], [-28, 28], [-29, 27], [-30, 27], [-31, 28], [-32, 29],
                        [-33, 29], [-34, 28], [-34, 26], [-34, 24], [-33, 22],
                        
                        // East Africa
                        [-32, 32], [-31, 33], [-30, 34], [-29, 35], [-28, 36], [-27, 37], [-26, 38],
                        [-25, 39], [-24, 39], [-23, 39], [-22, 40], [-21, 40], [-20, 41], [-19, 41],
                        [-18, 41], [-17, 41], [-16, 41], [-15, 41], [-14, 41], [-13, 41], [-12, 41],
                        [-11, 42], [-10, 43], [-9, 43], [-8, 44], [-7, 45], [-6, 46], [-5, 47],
                        [-4, 48], [-3, 49], [-2, 50], [-1, 51], [0, 51], [1, 51], [2, 51],
                        
                        // Horn of Africa
                        [3, 50], [4, 49], [5, 48], [6, 47], [7, 46], [8, 45], [9, 44], [10, 44],
                        [11, 44], [12, 43], [11, 42], [10, 41], [9, 40], [10, 41], [11, 42],
                        [12, 42], [11, 41], [10, 40], [9, 39],
                        
                        // East Africa north
                        [8, 38], [9, 37], [10, 37], [11, 37], [12, 38], [13, 38], [14, 39],
                        [15, 39], [16, 39], [17, 38], [18, 38], [19, 37], [20, 37], [21, 37],
                        [22, 36], [23, 36], [24, 36], [25, 36], [26, 36], [27, 36], [28, 35],
                        
                        // Red Sea
                        [29, 35], [30, 34], [31, 33], [32, 32], [31, 31], [30, 30], [29, 30],
                        [28, 31], [27, 32], [26, 32], [25, 32], [24, 32], [23, 31], [22, 31],
                        
                        // North Africa - Egypt/Libya
                        [31, 32], [32, 29], [31, 26], [31, 24], [31, 22], [31, 20], [31, 18],
                        [31, 16], [31, 14], [31, 12], [31, 10], [31, 8], [31, 6], [31, 4],
                        [31, 2], [31, 0], [31, -2], [31, -4], [31, -6], [31, -8], [30, -10],
                        
                        // Libya/Tunisia/Algeria
                        [33, -8], [34, -7], [35, -6], [36, -5], [37, -4], [37, -2], [37, 0],
                        [37, 2], [37, 4], [37, 6], [37, 8], [36, 10], [35, 11], [34, 10],
                        [33, 9], [32, 8], [31, 9], [30, 10],
                        
                        // Tunisia/Med coast
                        [37, 10], [36, 11], [35, 10], [34, 9], [33, 9], [32, 10], [33, 11],
                        [34, 11], [35, 10], [36, 9], [37, 9], [36, 8], [35, 7], [34, 6],
                        [33, 5], [33, 3], [34, 1], [34, -1], [35, -3], [35, -6]
                    ]
                },
                {
                    name: 'Europe',
                    coordinates: [
                        // Norway
                        [71, 25], [70, 28], [69, 29], [68, 30], [67, 31], [66, 32], [65, 33],
                        [64, 33], [63, 32], [62, 31], [61, 30], [60, 29], [59, 28], [60, 27],
                        [61, 26], [62, 25], [63, 24], [64, 24], [65, 24], [66, 25], [67, 26],
                        [68, 27], [69, 28], [70, 29], [71, 30],
                        
                        // Scandinavia east
                        [70, 30], [69, 31], [68, 31], [67, 30], [66, 29], [65, 28], [64, 27],
                        [63, 26], [62, 25], [61, 24], [60, 24], [59, 24], [60, 25], [61, 26],
                        
                        // Baltic
                        [60, 26], [59, 27], [58, 27], [57, 27], [56, 27], [55, 26], [54, 26],
                        [55, 25], [56, 24], [57, 23], [58, 23], [59, 24], [60, 25],
                        
                        // Western Europe coast
                        [58, 8], [57, 9], [56, 9], [55, 8], [54, 8], [53, 7], [52, 6],
                        [51, 5], [50, 4], [49, 3], [48, 2], [47, 1], [48, 0], [49, -1],
                        [50, -2], [51, -3], [50, -4], [49, -5], [48, -5], [47, -4],
                        
                        // France/Iberia
                        [46, -2], [45, -1], [44, -1], [43, -2], [42, -3], [41, -4], [40, -5],
                        [41, -6], [42, -7], [43, -8], [43, -9], [42, -9], [41, -8], [40, -8],
                        [39, -9], [38, -9], [37, -9], [36, -8], [37, -7], [38, -6], [39, -5],
                        
                        // Spain south
                        [38, -4], [37, -3], [36, -3], [36, -4], [36, -5], [36, -6], [37, -7],
                        
                        // Spain east/Mediterranean
                        [38, -1], [39, 0], [40, 1], [41, 2], [42, 3], [41, 4], [40, 4],
                        [39, 4], [38, 3], [37, 3], [38, 4],
                        
                        // Italy
                        [39, 8], [38, 9], [38, 10], [39, 11], [40, 12], [41, 13], [42, 14],
                        [43, 14], [44, 14], [45, 13], [45, 12], [45, 11], [44, 10], [43, 9],
                        [42, 8], [41, 8], [40, 8], [39, 8],
                        
                        // Adriatic/Balkans
                        [45, 14], [44, 15], [43, 16], [42, 17], [41, 18], [40, 19], [39, 20],
                        [40, 21], [41, 22], [42, 23], [43, 24], [42, 25], [41, 26], [40, 27],
                        [41, 28], [42, 28],
                        
                        // Greece/Turkey border
                        [41, 23], [40, 24], [39, 25], [38, 26], [37, 27], [38, 28], [39, 28],
                        [40, 28], [41, 27], [42, 26],
                        
                        // Black Sea north
                        [46, 30], [47, 31], [48, 32], [47, 33], [46, 34], [45, 35], [46, 36],
                        [47, 37], [48, 38], [47, 39], [46, 40], [45, 40], [44, 40], [45, 39],
                        
                        // Eastern Europe
                        [50, 30], [51, 31], [52, 32], [53, 33], [54, 34], [55, 35], [56, 36],
                        [57, 37], [58, 38], [59, 39], [60, 40], [59, 41], [58, 42], [57, 43],
                        
                        // Russia west
                        [60, 30], [61, 31], [62, 32], [63, 33], [64, 34], [65, 35], [66, 36],
                        [67, 37], [68, 38], [69, 39], [70, 40], [69, 41], [68, 42], [67, 43],
                        [68, 44], [69, 45], [70, 46], [69, 47], [68, 48], [67, 49], [68, 50],
                        [69, 51], [70, 50], [71, 49], [70, 48], [69, 47],
                        
                        // Return to start
                        [71, 25]
                    ]
                },
                {
                    name: 'Asia',
                    coordinates: [
                        // Russia far east/Kamchatka
                        [60, 160], [59, 161], [58, 162], [57, 163], [56, 164], [55, 165], [54, 166],
                        [53, 167], [52, 168], [51, 169], [50, 170], [51, 171], [52, 172], [53, 171],
                        [54, 170], [55, 169], [56, 168], [57, 167], [58, 166], [59, 165], [60, 164],
                        
                        // Siberia east coast
                        [65, 170], [66, 171], [67, 172], [68, 173], [69, 174], [70, 175], [71, 176],
                        [70, 177], [69, 178], [68, 179], [67, 180], [68, -179], [69, -178], [70, -177],
                        [69, -176], [68, -175], [67, -174], [66, -173], [65, -172], [64, -171],
                        
                        // Arctic Russia
                        [70, 170], [71, 165], [72, 160], [73, 155], [74, 150], [75, 145], [76, 140],
                        [77, 135], [78, 130], [77, 125], [76, 120], [75, 115], [74, 110], [73, 105],
                        [72, 100], [71, 95], [70, 90], [69, 85], [68, 80], [67, 75], [66, 70],
                        
                        // Central Asia
                        [50, 60], [49, 61], [48, 62], [47, 63], [46, 64], [45, 65], [44, 66],
                        [43, 67], [42, 68], [41, 69], [40, 70], [41, 71], [42, 72], [43, 73],
                        [44, 74], [45, 75], [46, 76], [47, 77], [48, 78], [49, 79], [50, 80],
                        
                        // Middle East west
                        [40, 35], [39, 36], [38, 37], [37, 38], [36, 39], [35, 40], [34, 41],
                        [33, 42], [32, 43], [31, 44], [30, 45], [31, 46], [32, 47], [33, 48],
                        [34, 48], [35, 48], [36, 48], [37, 47], [38, 46], [39, 45], [40, 44],
                        
                        // Arabian Peninsula
                        [30, 35], [29, 36], [28, 37], [27, 38], [26, 39], [25, 40], [24, 41],
                        [23, 42], [22, 43], [21, 44], [20, 45], [19, 46], [18, 47], [17, 48],
                        [16, 49], [15, 50], [14, 51], [13, 52], [14, 53], [15, 54], [16, 55],
                        [17, 56], [18, 57], [19, 57], [20, 57], [21, 57], [22, 56], [23, 55],
                        [24, 54], [25, 53], [26, 52], [27, 51], [28, 50], [29, 49], [30, 48],
                        
                        // India
                        [35, 75], [34, 76], [33, 77], [32, 78], [31, 79], [30, 80], [29, 81],
                        [28, 82], [27, 83], [26, 84], [25, 85], [24, 86], [23, 87], [22, 88],
                        [21, 89], [20, 90], [21, 91], [22, 92], [23, 93], [24, 93], [25, 93],
                        [26, 92], [27, 91], [28, 90], [29, 89], [28, 88], [27, 87], [26, 86],
                        
                        // India east coast
                        [22, 88], [21, 87], [20, 86], [19, 85], [18, 84], [17, 83], [16, 82],
                        [15, 81], [14, 80], [13, 80], [12, 80], [11, 80], [10, 80], [9, 79],
                        [8, 78], [9, 77], [10, 76], [11, 76], [12, 77], [13, 78], [14, 79],
                        
                        // Southeast Asia
                        [20, 100], [19, 101], [18, 102], [17, 103], [16, 104], [15, 105], [14, 106],
                        [13, 107], [12, 108], [11, 109], [10, 110], [9, 111], [8, 112], [7, 113],
                        [6, 114], [5, 115], [4, 116], [3, 117], [2, 118], [1, 119], [0, 120],
                        [1, 121], [2, 122], [3, 123], [4, 124], [5, 125], [6, 125], [7, 125],
                        
                        // China east coast
                        [40, 120], [39, 121], [38, 122], [37, 122], [36, 122], [35, 121], [34, 120],
                        [33, 120], [32, 120], [31, 121], [30, 122], [29, 122], [28, 121], [27, 120],
                        [26, 120], [25, 120], [24, 120], [23, 120], [22, 121], [21, 122],
                        
                        // Korea/Japan area
                        [42, 130], [41, 131], [40, 131], [39, 130], [38, 129], [37, 128], [36, 127],
                        [35, 127], [34, 128], [35, 129], [36, 130], [37, 131], [38, 132], [39, 133],
                        
                        // Japan
                        [45, 140], [44, 141], [43, 142], [42, 143], [41, 144], [40, 144], [39, 143],
                        [38, 142], [37, 141], [36, 140], [35, 139], [34, 138], [35, 137], [36, 136],
                        [37, 136], [38, 137], [39, 138], [40, 139], [41, 140], [42, 141], [43, 142],
                        
                        // Return north
                        [50, 145], [51, 146], [52, 147], [53, 148], [54, 149], [55, 150], [56, 151],
                        [57, 152], [58, 153], [59, 154], [60, 155], [60, 160]
                    ]
                },
                {
                    name: 'Australia',
                    coordinates: [
                        // North coast
                        [-10, 142], [-11, 143], [-12, 143], [-13, 144], [-12, 145], [-11, 145],
                        [-10, 144], [-11, 144], [-12, 144], [-13, 145], [-14, 145], [-13, 146],
                        [-12, 146], [-11, 147], [-12, 148], [-13, 149], [-14, 150], [-15, 150],
                        [-16, 149], [-17, 148], [-18, 148], [-17, 149], [-16, 150], [-15, 151],
                        [-16, 152], [-17, 152], [-18, 152], [-19, 152], [-20, 152], [-21, 153],
                        
                        // East coast
                        [-22, 153], [-23, 153], [-24, 153], [-25, 153], [-26, 153], [-27, 153],
                        [-28, 153], [-29, 153], [-30, 153], [-31, 152], [-32, 152], [-33, 152],
                        [-34, 151], [-35, 150], [-36, 150], [-37, 149], [-38, 148], [-38, 147],
                        [-38, 146], [-38, 145],
                        
                        // South coast
                        [-38, 144], [-38, 143], [-38, 142], [-38, 141], [-38, 140], [-38, 139],
                        [-37, 138], [-36, 138], [-35, 137], [-35, 136], [-35, 135], [-35, 134],
                        [-34, 133], [-33, 133], [-32, 133], [-33, 132], [-34, 132], [-35, 132],
                        [-34, 131], [-33, 130], [-32, 130], [-31, 129], [-32, 128], [-33, 127],
                        [-34, 127], [-35, 127], [-34, 126], [-33, 125], [-32, 125], [-31, 124],
                        [-30, 124], [-31, 123], [-32, 123], [-33, 123], [-32, 122], [-31, 122],
                        
                        // West coast
                        [-30, 122], [-29, 122], [-28, 122], [-27, 122], [-26, 122], [-25, 122],
                        [-24, 122], [-23, 122], [-22, 122], [-21, 122], [-20, 122], [-21, 121],
                        [-22, 121], [-23, 121], [-22, 120], [-21, 120], [-20, 121], [-19, 121],
                        [-18, 122], [-17, 122], [-16, 122], [-15, 122], [-14, 123], [-13, 123],
                        [-12, 124], [-13, 125], [-14, 126], [-13, 127], [-12, 128], [-13, 129],
                        [-14, 130], [-13, 131], [-12, 132], [-13, 133], [-14, 134], [-13, 135],
                        [-12, 136], [-13, 137], [-14, 138], [-13, 139], [-12, 140], [-11, 141],
                        [-10, 142]
                    ]
                },
                {
                    name: 'Antarctica',
                    coordinates: [
                        [-60, -60], [-61, -50], [-62, -40], [-63, -30], [-64, -20], [-65, -10],
                        [-66, 0], [-67, 10], [-68, 20], [-69, 30], [-70, 40], [-71, 50],
                        [-72, 60], [-73, 70], [-74, 80], [-75, 90], [-76, 100], [-77, 110],
                        [-78, 120], [-77, 130], [-76, 140], [-75, 150], [-74, 160], [-73, 170],
                        [-72, 180], [-71, -170], [-70, -160], [-69, -150], [-68, -140], [-67, -130],
                        [-66, -120], [-65, -110], [-64, -100], [-63, -90], [-62, -80], [-61, -70],
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
                { name: 'AFRICA', lat: 1.5, lon: 18.5, size: 2000 },
                { name: 'EUROPE', lat: 53.0, lon: 28.0, size: 1600 },
                { name: 'ASIA', lat: 45.0, lon: 95.0, size: 2200 },
                { name: 'NORTH\\nAMERICA', lat: 48.0, lon: -100.0, size: 1800 },
                { name: 'SOUTH\\nAMERICA', lat: -12.0, lon: -60.0, size: 1800 },
                { name: 'AUSTRALIA', lat: -25.5, lon: 134.0, size: 1800 },
                { name: 'ANTARCTICA', lat: -80.0, lon: 0, size: 2000 }
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
