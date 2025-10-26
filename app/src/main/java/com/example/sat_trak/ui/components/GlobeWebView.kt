package com.example.sat_trak.ui.components

import android.annotation.SuppressLint
import android.webkit.WebView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.example.sat_trak.data.models.SatelliteData

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GlobeWebView(satellites: List<SatelliteData>, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowContentAccess = true
                settings.allowFileAccess = true
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
                    "{\"name\":\"${it.name}\",\"x\":${it.x},\"y\":${it.y},\"z\":${it.z}}"
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
            background-color: #000; 
            touch-action: none;
        }
        canvas { 
            display: block; 
            width: 100%;
            height: 100%;
        }
    </style>
</head>
<body>
    <script src="https://cdnjs.cloudflare.com/ajax/libs/three.js/r128/three.min.js"></script>
    <script>
        let scene, camera, renderer, earth, earthGroup;
        let satelliteObjects = [];
        let isDragging = false;
        let previousMousePosition = { x: 0, y: 0 };
        let rotation = { x: 0.4, y: 0 };

        function init() {
            scene = new THREE.Scene();
            camera = new THREE.PerspectiveCamera(75, window.innerWidth / window.innerHeight, 0.1, 50000);
            camera.position.z = 15000;

            renderer = new THREE.WebGLRenderer({ antialias: true });
            renderer.setSize(window.innerWidth, window.innerHeight);
            document.body.appendChild(renderer.domElement);

            // Create Earth group for rotation
            earthGroup = new THREE.Group();
            scene.add(earthGroup);

            // Create Earth
            const geometry = new THREE.SphereGeometry(6371, 64, 64);
            const material = new THREE.MeshPhongMaterial({
                color: 0x2233ff,
                emissive: 0x112244,
                shininess: 25,
                specular: 0x333333
            });
            earth = new THREE.Mesh(geometry, material);
            earthGroup.add(earth);

            // Add lighting
            const ambientLight = new THREE.AmbientLight(0x404040, 2);
            scene.add(ambientLight);

            const directionalLight = new THREE.DirectionalLight(0xffffff, 1);
            directionalLight.position.set(10000, 5000, 5000);
            scene.add(directionalLight);

            // Mouse controls
            renderer.domElement.addEventListener('mousedown', onMouseDown);
            renderer.domElement.addEventListener('mousemove', onMouseMove);
            renderer.domElement.addEventListener('mouseup', onMouseUp);
            renderer.domElement.addEventListener('wheel', onWheel);

            // Touch controls
            renderer.domElement.addEventListener('touchstart', onTouchStart);
            renderer.domElement.addEventListener('touchmove', onTouchMove);
            renderer.domElement.addEventListener('touchend', onTouchEnd);

            window.addEventListener('resize', onWindowResize);

            animate();
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
            camera.position.z = Math.max(8000, Math.min(30000, camera.position.z));
        }

        function onWindowResize() {
            camera.aspect = window.innerWidth / window.innerHeight;
            camera.updateProjectionMatrix();
            renderer.setSize(window.innerWidth, window.innerHeight);
        }

        function updateSatellites(satellites) {
            // Remove old satellites
            satelliteObjects.forEach(obj => earthGroup.remove(obj));
            satelliteObjects = [];

            // Add new satellites
            satellites.forEach(sat => {
                const geometry = new THREE.SphereGeometry(150, 16, 16);
                const material = new THREE.MeshBasicMaterial({ color: 0xff0000 });
                const sphere = new THREE.Mesh(geometry, material);
                sphere.position.set(sat.x, sat.y, sat.z);
                earthGroup.add(sphere);
                satelliteObjects.push(sphere);
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

