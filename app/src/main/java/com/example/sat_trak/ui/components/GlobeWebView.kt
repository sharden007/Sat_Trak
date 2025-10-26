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
            const material = new THREE.MeshPhongMaterial({
                color: 0x1a4d8f,  // Deep ocean blue
                specular: 0x3366aa,
                shininess: 30,
                emissive: 0x0a2a5a,
                emissiveIntensity: 0.3
            });
            
            earth = new THREE.Mesh(geometry, material);
            earthGroup.add(earth);

            // Add realistic brown landmasses
            addLandmasses();
            
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
                button.innerHTML = '🌍 Rotation: Visual Speed';
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
            // Create filled landmass using proper triangulation
            const vertices = [];
            const indices = [];
            
            // Calculate center point
            const centerLat = coordinates.reduce((sum, c) => sum + c[0], 0) / coordinates.length;
            const centerLon = coordinates.reduce((sum, c) => sum + c[1], 0) / coordinates.length;
            const centerPos = latLonToVector3(centerLat, centerLon, 6371 + 10);
            
            // Add all edge vertices
            const edgeVertices = [];
            for (let i = 0; i < coordinates.length; i++) {
                const pos = latLonToVector3(coordinates[i][0], coordinates[i][1], 6371 + 10);
                vertices.push(pos.x, pos.y, pos.z);
                edgeVertices.push(pos);
            }
            
            // Add center vertex
            const centerIndex = vertices.length / 3;
            vertices.push(centerPos.x, centerPos.y, centerPos.z);
            
            // Create triangle fan from center to all edges
            for (let i = 0; i < coordinates.length; i++) {
                const next = (i + 1) % coordinates.length;
                indices.push(centerIndex, i, next);
            }
            
            // Create the filled geometry
            const geometry = new THREE.BufferGeometry();
            geometry.setAttribute('position', new THREE.Float32BufferAttribute(vertices, 3));
            geometry.setIndex(indices);
            geometry.computeVertexNormals();
            
            // Brown material for landmass fill
            const material = new THREE.MeshPhongMaterial({
                color: 0xa89968,  // Sandy brown
                emissive: 0x7a6545,
                emissiveIntensity: 0.25,
                side: THREE.DoubleSide,
                flatShading: false,
                shininess: 10
            });
            
            const mesh = new THREE.Mesh(geometry, material);
            earthGroup.add(mesh);
            
            // Add thicker outline for definition
            const outlinePoints = coordinates.map(coord => 
                latLonToVector3(coord[0], coord[1], 6371 + 12)
            );
            outlinePoints.push(outlinePoints[0].clone());
            
            const curve = new THREE.CatmullRomCurve3(outlinePoints);
            const tubeGeometry = new THREE.TubeGeometry(curve, outlinePoints.length * 4, 25, 8, false);
            const tubeMaterial = new THREE.MeshPhongMaterial({
                color: 0x7a5d3a,  // Darker brown outline
                emissive: 0x5a4321,
                emissiveIntensity: 0.3
            });
            const tube = new THREE.Mesh(tubeGeometry, tubeMaterial);
            earthGroup.add(tube);
        }

        function getContinentData() {
            // Accurate continent outlines
            return [
                {
                    name: 'Africa',
                    coordinates: [
                        [37, -6], [36, 0], [37, 10], [32, 22], [31, 32], [29, 35],
                        [24, 37], [15, 39], [12, 43], [4, 51], [-1, 42], [-11, 43],
                        [-17, 49], [-26, 49], [-34, 28], [-35, 18], [-22, 15], [-12, 13],
                        [0, 10], [10, 15], [11, 20], [15, 10], [23, 20], [30, 32],
                        [35, 10], [37, -6]
                    ]
                },
                {
                    name: 'Europe',
                    coordinates: [
                        [71, 26], [70, 31], [68, 40], [66, 48], [60, 60], [56, 63],
                        [54, 66], [50, 60], [48, 52], [43, 44], [40, 36], [36, 25],
                        [37, 12], [40, 0], [43, -9], [48, -2], [52, 8], [58, 14],
                        [65, 22], [71, 26]
                    ]
                },
                {
                    name: 'Asia',
                    coordinates: [
                        [77, 105], [75, 115], [72, 130], [68, 145], [64, 160], [58, 170],
                        [50, 171], [42, 166], [35, 150], [28, 135], [20, 120], [10, 105],
                        [5, 95], [0, 80], [8, 70], [20, 68], [30, 75], [36, 95],
                        [43, 110], [50, 125], [58, 115], [65, 95], [68, 70], [65, 55],
                        [58, 50], [55, 55], [60, 75], [70, 90], [76, 100], [77, 105]
                    ]
                },
                {
                    name: 'North America',
                    coordinates: [
                        [83, -95], [80, -85], [75, -75], [70, -68], [60, -65], [50, -55],
                        [48, -125], [55, -135], [65, -145], [72, -165], [70, 178], [60, 165],
                        [55, -168], [50, -155], [45, -145], [35, -125], [28, -115], [15, -112],
                        [15, -90], [25, -82], [35, -80], [45, -75], [55, -75], [65, -85],
                        [75, -90], [83, -95]
                    ]
                },
                {
                    name: 'South America',
                    coordinates: [
                        [12, -72], [8, -65], [0, -60], [-8, -55], [-18, -57], [-28, -65],
                        [-38, -72], [-48, -75], [-55, -70], [-54, -67], [-48, -68], [-38, -63],
                        [-28, -58], [-18, -54], [-8, -58], [2, -66], [8, -70], [12, -72]
                    ]
                },
                {
                    name: 'Australia',
                    coordinates: [
                        [-10, 142], [-12, 150], [-18, 154], [-25, 153], [-33, 151],
                        [-38, 145], [-38, 138], [-35, 130], [-28, 125], [-22, 122],
                        [-15, 123], [-11, 132], [-10, 142]
                    ]
                },
                {
                    name: 'Antarctica',
                    coordinates: [
                        [-65, -60], [-68, -40], [-72, -20], [-76, 0], [-80, 20], [-84, 40],
                        [-87, 70], [-85, 100], [-80, 130], [-75, 160], [-70, -170],
                        [-66, -120], [-64, -80], [-65, -60]
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
