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
    onSatelliteClick: (SatelliteData) -> Unit = {}
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
    </style>
</head>
<body>
    <div id="info"></div>
    <div id="instructions">🖱️ Drag to rotate • Scroll to zoom • Click satellites for info</div>
    
    <script src="https://cdnjs.cloudflare.com/ajax/libs/three.js/r128/three.min.js"></script>
    <script>
        let scene, camera, renderer, earth, earthGroup;
        let satelliteObjects = [];
        let satelliteLabels = [];
        let isDragging = false;
        let previousMousePosition = { x: 0, y: 0 };
        let rotation = { x: 0.4, y: 0 };
        let raycaster = new THREE.Raycaster();
        let mouse = new THREE.Vector2();
        let currentSatellites = [];

        function init() {
            scene = new THREE.Scene();
            
            // Adjusted camera for better view - pulled back more
            camera = new THREE.PerspectiveCamera(60, window.innerWidth / window.innerHeight, 0.1, 50000);
            camera.position.z = 18000;  // Increased from 15000 to show full globe

            renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true });
            renderer.setSize(window.innerWidth, window.innerHeight);
            renderer.setClearColor(0x000000, 1);
            document.body.appendChild(renderer.domElement);

            // Create Earth group for rotation
            earthGroup = new THREE.Group();
            scene.add(earthGroup);

            // Create Earth with improved appearance
            const geometry = new THREE.SphereGeometry(6371, 64, 64);
            const material = new THREE.MeshPhongMaterial({
                color: 0x2244ff,
                emissive: 0x112244,
                shininess: 30,
                specular: 0x555555
            });
            earth = new THREE.Mesh(geometry, material);
            earthGroup.add(earth);

            // Add equator line for reference
            const equatorGeometry = new THREE.TorusGeometry(6371, 10, 16, 100);
            const equatorMaterial = new THREE.MeshBasicMaterial({ 
                color: 0x44ff44, 
                transparent: true, 
                opacity: 0.3 
            });
            const equator = new THREE.Mesh(equatorGeometry, equatorMaterial);
            equator.rotation.x = Math.PI / 2;
            earthGroup.add(equator);

            // Add lighting
            const ambientLight = new THREE.AmbientLight(0x404040, 2.5);
            scene.add(ambientLight);

            const directionalLight = new THREE.DirectionalLight(0xffffff, 1.5);
            directionalLight.position.set(10000, 5000, 5000);
            scene.add(directionalLight);

            const backLight = new THREE.DirectionalLight(0x6666ff, 0.5);
            backLight.position.set(-10000, -5000, -5000);
            scene.add(backLight);

            // Add stars background
            addStars();

            // Mouse/Touch controls
            renderer.domElement.addEventListener('mousedown', onMouseDown);
            renderer.domElement.addEventListener('mousemove', onMouseMove);
            renderer.domElement.addEventListener('mouseup', onMouseUp);
            renderer.domElement.addEventListener('wheel', onWheel);
            renderer.domElement.addEventListener('click', onClick);

            renderer.domElement.addEventListener('touchstart', onTouchStart);
            renderer.domElement.addEventListener('touchmove', onTouchMove);
            renderer.domElement.addEventListener('touchend', onTouchEnd);

            window.addEventListener('resize', onWindowResize);

            animate();
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

        function updateSatellites(satellites) {
            currentSatellites = satellites;
            
            // Remove old satellites and labels
            satelliteObjects.forEach(obj => earthGroup.remove(obj));
            satelliteLabels.forEach(label => earthGroup.remove(label));
            satelliteObjects = [];
            satelliteLabels = [];

            // Add new satellites with labels
            satellites.forEach(sat => {
                // Create satellite sphere
                const geometry = new THREE.SphereGeometry(120, 16, 16);
                const material = new THREE.MeshBasicMaterial({ 
                    color: sat.id === 25544 ? 0xff0000 : (sat.id === 33591 ? 0x00ff00 : 0xffff00)
                });
                const sphere = new THREE.Mesh(geometry, material);
                sphere.position.set(sat.x, sat.y, sat.z);
                sphere.userData = { id: sat.id };
                earthGroup.add(sphere);
                satelliteObjects.push(sphere);

                // Create text label using sprite
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
            earthGroup.rotation.y += 0.0005;
            rotation.y += 0.0005;
            
            renderer.render(scene, camera);
        }

        init();
    </script>
</body>
</html>
    """.trimIndent()
}
