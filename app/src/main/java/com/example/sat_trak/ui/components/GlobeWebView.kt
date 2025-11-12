package com.example.sat_trak.ui.components

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.example.sat_trak.data.models.SatelliteData
import com.example.sat_trak.data.repository.ContinentDataLoader
import com.example.sat_trak.utils.SatelliteColorUtils
import java.lang.StringBuilder

private const val TAG_STORE_KEY = 0x53415453 // 'SATS'
private class LatestSatStore { @Volatile var list: List<SatelliteData> = emptyList() }

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GlobeWebView(
    satellites: List<SatelliteData>,
    modifier: Modifier = Modifier,
    onSatelliteClick: (SatelliteData) -> Unit = {},
    onZoomControlsReady: ((zoomIn: () -> Unit, zoomOut: () -> Unit) -> Unit)? = null,
    // visual flags
    showTrails: Boolean = true,
    trailSteps: Int = 130,
    // optional selected satellite id (null = none)
    selectedSatelliteId: Int? = null,
    // Bird's Eye View mode - disables rotation and focuses on satellite
    birdsEyeMode: Boolean = false,
    focusLatitude: Double? = null,
    focusLongitude: Double? = null
) {
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            // Load bundled continents.json from assets so the WebView can draw landmasses even when
            // no external getContinentData() is provided.
            // Parse assets into a compact JSON containing only bounding boxes to avoid
            // injected-comments or unexpected schema from the raw file.
            val continentsForJs = try {
                // Try to load polygon coordinates from continents_polygons.json first
                val polygonJson = try {
                    context.assets.open("continents_polygons.json").bufferedReader().use { it.readText() }
                } catch (_: Throwable) {
                    null
                }

                if (polygonJson != null) {
                    // We have polygon data - use it directly
                    polygonJson
                } else {
                    // Fallback: load from continents.json and extract bounding boxes
                    val list = ContinentDataLoader.loadFromAssets(context)
                    val sb = StringBuilder()
                    sb.append("[")
                    list.forEachIndexed { idx, cont ->
                        val bb = cont.boundingBox
                        sb.append("{\"id\":\"").append(cont.id).append("\",\"boundingBox\":{\"minLat\":${bb.minLat},\"minLon\":${bb.minLon},\"maxLat\":${bb.maxLat},\"maxLon\":${bb.maxLon}}}")
                        if (idx < list.size - 1) sb.append(',')
                    }
                    sb.append("]")
                    sb.toString()
                }
            } catch (_: Throwable) {
                "[]"
            }

            WebView(context).apply {
                val store = LatestSatStore()
                this.setTag(TAG_STORE_KEY, store)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowContentAccess = true
                settings.allowFileAccess = true

                // Enable remote debugging to inspect the WebView's JS console (helpful during development)
                WebView.setWebContentsDebuggingEnabled(true)

                addJavascriptInterface(object {
                    @Suppress("unused")
                    @JavascriptInterface
                    fun onSatelliteClicked(satelliteId: Int) {
                        Log.d("GlobeWebView", "Satellite clicked: ID=$satelliteId")
                        val current = (getTag(TAG_STORE_KEY) as? LatestSatStore)?.list ?: emptyList()
                        // Post to main thread to update UI using the latest satellites list
                        post {
                            current.find { it.id == satelliteId }?.let { satellite ->
                                Log.d("GlobeWebView", "Found satellite: ${satellite.name}, triggering callback")
                                onSatelliteClick(satellite)
                            } ?: Log.w("GlobeWebView", "Satellite ID=$satelliteId not found in latest list (size=${current.size})")
                        }
                    }

                    @Suppress("unused")
                    @JavascriptInterface
                    fun onConsole(message: String) {
                        Log.d("GlobeWebViewJS", message)
                    }
                }, "Android")

                // Inject the continents JSON into the HTML template by replacing a marker. This avoids
                // trying to evaluate JS before the page has the continent data available.
                val htmlWithContinents = getHtmlContent().replace("/*__CONTINENTS_JSON__*/", continentsForJs)

                loadDataWithBaseURL(
                    null,
                    htmlWithContinents,
                    "text/html",
                    "UTF-8",
                    null
                )
            }
        },
        update = { webView ->
            // Keep the store up-to-date with the latest satellites list
            try { (webView.getTag(TAG_STORE_KEY) as? LatestSatStore)?.list = satellites } catch (_: Throwable) {}

            if (satellites.isNotEmpty()) {
                // Also log to the WebView console (forwarded to Android) the count being pushed
                try {
                    val count = satellites.size
                    webView.evaluateJavascript("console.log('Kotlin->JS satellite push count: $count');", null)
                } catch (_: Throwable) { }

                // Build a safe JSON representation of the satellites list by escaping string fields
                // Include color information for each satellite
                val satellitesJson = satellites.joinToString(",") { sat ->
                    val nameEsc = sat.name.replace("\"", "\\\"")
                    val typeEsc = sat.type.replace("\"", "\\\"")
                    val colorHex = SatelliteColorUtils.getIntColorForSatellite(sat.id)
                    "{\"id\":${sat.id},\"name\":\"$nameEsc\",\"x\":${sat.x},\"y\":${sat.y},\"z\":${sat.z},\"lat\":${sat.latitude},\"lon\":${sat.longitude},\"alt\":${sat.altitude},\"type\":\"$typeEsc\",\"color\":$colorHex}"
                }

                // Queue updates if the page hasn't defined updateSatellites yet. This prevents the
                // JS from ignoring the first push if the page is still loading.
                val js = "window.__pendingSatellites = [${satellitesJson}]; if(typeof updateSatellites === 'function'){ try{ updateSatellites(window.__pendingSatellites); delete window.__pendingSatellites; }catch(e){ console.log('sat update error:'+e); } }"
                webView.evaluateJavascript(js, null)
            }

            // Inject config (removed useHighResTiles)
            try {
                val jsConfig = "window.__satTrakConfig = { showTrails: ${showTrails}, trailSteps: ${trailSteps} };"
                webView.evaluateJavascript(jsConfig, null)
                webView.evaluateJavascript("if(typeof applyConfig === 'function'){ applyConfig(); }", null)
            } catch (_: Throwable) {
                // swallow
            }

            // highlight selected satellite (or clear if null)
            try {
                if (selectedSatelliteId != null) {
                    Log.d("GlobeWebView", "Highlighting satellite ID: $selectedSatelliteId")
                    webView.evaluateJavascript("if(typeof highlightSatellite === 'function'){ highlightSatellite(${selectedSatelliteId}); }", null)
                } else {
                    webView.evaluateJavascript("if(typeof clearHighlight === 'function'){ clearHighlight(); }", null)
                }
            } catch (_: Throwable) {
                // ignore
            }

            // Set up zoom controls
            onZoomControlsReady?.invoke(
                { webView.evaluateJavascript("zoomIn();", null) },
                { webView.evaluateJavascript("zoomOut();", null) }
            )

            // Bird's Eye View mode - disables rotation and focuses on satellite
            try {
                if (birdsEyeMode && selectedSatelliteId != null) {
                    val targetSatellite = satellites.find { it.id == selectedSatelliteId }
                    targetSatellite?.let { sat ->
                        val lat = sat.latitude
                        val lon = sat.longitude
                        val jsFocus = "setCameraFocus(${lat}, ${lon});"
                        webView.evaluateJavascript(jsFocus, null)
                    }
                } else if (!birdsEyeMode) {
                    // Exit Bird's Eye View mode and restore normal rotation
                    webView.evaluateJavascript("if(typeof exitBirdsEyeView === 'function'){ exitBirdsEyeView(); }", null)
                }
            } catch (_: Throwable) {
                // ignore
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
        body { margin: 0; overflow: hidden; background: #000; font-family: Arial, sans-serif; }
        canvas { display: block; width: 100%; height: 100%; }
        #info { position: absolute; top: 10px; right: 10px; color: #fff; background: rgba(0,0,0,0.7); padding:10px; border-radius:5px; font-size:12px; max-width:250px; pointer-events:none; display:none }
    </style>
</head>
<body>
<div id="info"></div>
<script src="https://cdnjs.cloudflare.com/ajax/libs/three.js/r128/three.min.js"></script>
<script>
    // forward console messages to Android for debugging (Android.onConsole will receive strings)
    (function(){
        try {
            var origLog = console.log || function(){};
            console.log = function(){
                try{ Android.onConsole(Array.prototype.slice.call(arguments).join(' ')); }catch(e){}
                origLog.apply(console, arguments);
            };
            var origError = console.error || function(){};
            console.error = function(){
                try{ Android.onConsole(Array.prototype.slice.call(arguments).join(' ')); }catch(e){}
                origError.apply(console, arguments);
            };
        } catch(e) {}
    })();

    // SAFETY: provide a fallback getContinentData() if one isn't available globally.
    // If your app intends to show real continent polygons, replace this stub with the real data provider.
    if (typeof getContinentData !== 'function') {
        function getContinentData() {
            // return an empty list to avoid runtime errors when drawing continents
            return [];
        }
    }

    function getConfig(){ return (window.__satTrakConfig) ? window.__satTrakConfig : { showTrails:true, trailSteps:130, useHighResTiles:false }; }

    // Convert lat/lon (degrees) + radius (km) to THREE.Vector3 in the same coord system used for earth mesh
    function latLonToVector3(lat, lon, radius){
        // Match Kotlin latLonAltToCartesian: x = r*cos(lat)*cos(lon); y = r*sin(lat); z = -r*cos(lat)*sin(lon)
        const latRad = lat * Math.PI / 180.0;
        const lonRad = lon * Math.PI / 180.0;
        const x = radius * Math.cos(latRad) * Math.cos(lonRad);
        const y = radius * Math.sin(latRad);
        const z = -radius * Math.cos(latRad) * Math.sin(lonRad);
        return new THREE.Vector3(x, y, z);
    }

    // Apply configuration changes - NOTE: We're using a photorealistic texture now, not canvas-drawn continents
    function applyConfig(){
        // Redraw trails when config changes
        console.log('applyConfig called - redrawing trails based on config');
        redrawTrails();
    }

    // Redraw all trails based on current config
    function redrawTrails(){
        // Clear existing trails
        if(trailsGroup){ 
            while(trailsGroup.children.length) trailsGroup.remove(trailsGroup.children[0]); 
        }
        
        // Redraw trails if enabled and we have satellites
        const cfg = getConfig();
        if(cfg.showTrails && currentSatellites && currentSatellites.length > 0){
            currentSatellites.forEach(sat => {
                drawProjectedTrail(sat, cfg.trailSteps || 130);
            });
            console.log('Trails redrawn for ' + currentSatellites.length + ' satellites');
        } else {
            console.log('Trails cleared (showTrails=' + cfg.showTrails + ')');
        }
    }

    // Simple zoom handlers used by the Android UI
    function zoomIn(){ try{ camera.position.z = Math.max(1000, camera.position.z - 2000); } catch(e){ console.error(e); } }
    function zoomOut(){ try{ camera.position.z = Math.min(100000, camera.position.z + 2000); } catch(e){ console.error(e); } }

    let scene, camera, renderer, earth, earthGroup, trailsGroup;
    let satelliteObjects = []; let satelliteLabels = []; let currentSatellites = [];
    let satelliteHitObjects = [];
    let selectedHighlight = null;
    let highlightedId = null;
    
    // Rotation speed control
    let rotationSpeed = 0.0009; // Simulated speed (default)
    let birdsEyeViewMode = false; // Track if we're in Bird's Eye View mode
    let targetCameraRotation = null; // Target rotation for Bird's Eye View
    let wasInBirdsEyeMode = false; // Track previous state to detect transitions
    let savedCameraZ = null; // Save camera Z position before Bird's Eye View

    // Set camera focus for Bird's Eye View mode
    function setCameraFocus(lat, lon) {
        try {
            birdsEyeViewMode = true;
            wasInBirdsEyeMode = true;
            rotationSpeed = 0; // Stop rotation
            
            // Save current camera Z position before zooming in
            if (savedCameraZ === null) {
                savedCameraZ = camera.position.z;
                console.log('Saved camera Z position:', savedCameraZ);
            }
            
            // Zoom in for Bird's Eye View - move camera much closer (15000 gives nice detail view)
            camera.position.z = 15000;
            
            // Calculate the rotation needed to center the view on the satellite
            // Convert lat/lon to rotation angles
            const lonRad = lon * Math.PI / 180.0;
            const latRad = lat * Math.PI / 180.0;
            
            // Set the earth rotation to center on this location
            earthGroup.rotation.x = 0.4 - latRad * 0.5; // Adjust X for latitude
            earthGroup.rotation.y = -lonRad; // Adjust Y for longitude
            
            targetCameraRotation = { x: earthGroup.rotation.x, y: earthGroup.rotation.y };
            
            console.log('Bird\'s Eye View mode activated: focusing on lat=' + lat + ', lon=' + lon + ', zoomed to ' + camera.position.z);
        } catch(e) {
            console.error('Error in setCameraFocus:', e);
        }
    }
    
    // Exit Bird's Eye View mode
    function exitBirdsEyeView() {
        try {
            // Only execute if we were actually in Bird's Eye View mode
            if (!wasInBirdsEyeMode) {
                console.log('exitBirdsEyeView called but was not in Bird\'s Eye mode - skipping');
                return;
            }
            
            console.log('exitBirdsEyeView called - restoring normal view');
            birdsEyeViewMode = false;
            wasInBirdsEyeMode = false;
            rotationSpeed = 0.0009; // Resume rotation at simulated speed
            targetCameraRotation = null;
            
            // Restore the saved camera Z position (preserves user's zoom level from before Bird's Eye View)
            if (savedCameraZ !== null) {
                camera.position.z = savedCameraZ;
                savedCameraZ = null;
                console.log('Restored camera Z position to:', camera.position.z);
            }
            
            camera.lookAt(0, 0, 0);
            
            // Reset earth rotation to default orientation
            earthGroup.rotation.x = 0.4;
            earthGroup.rotation.y = 0;
            earthGroup.rotation.z = 0;
            
            // Force a render update
            if(typeof renderer !== 'undefined' && renderer) {
                renderer.render(scene, camera);
            }
            
            console.log('Bird\'s Eye View mode deactivated - view restored (zoom preserved)');
        } catch(e) {
            console.error('Error in exitBirdsEyeView:', e);
        }
    }

    function init(){
        scene = new THREE.Scene();
        camera = new THREE.PerspectiveCamera(60, window.innerWidth/window.innerHeight, 0.1, 50000);
        camera.position.set(0, 0, 30000); // Ensure camera starts at correct position
        camera.lookAt(0, 0, 0); // Ensure camera looks at center
        renderer = new THREE.WebGLRenderer({antialias:true, alpha:true}); 
        renderer.setSize(window.innerWidth, window.innerHeight); 
        document.body.appendChild(renderer.domElement);
        earthGroup = new THREE.Group();
        earthGroup.rotation.x = 0.4; // Set initial tilt
        earthGroup.rotation.y = 0;
        earthGroup.rotation.z = 0;
        scene.add(earthGroup);
        trailsGroup = new THREE.Group(); 
        earthGroup.add(trailsGroup);
        
        const geometry = new THREE.SphereGeometry(6371, 256, 256);
        
        // Use a high-quality Earth texture instead of canvas-drawn continents
        const loader = new THREE.TextureLoader();
        const earthTextureUrl = 'https://unpkg.com/three-globe@2.31.1/example/img/earth-blue-marble.jpg';
        const earthTexture = loader.load(
            earthTextureUrl,
            function(texture) {
                console.log('Earth texture loaded successfully');
            },
            undefined,
            function(error) {
                console.error('Failed to load Earth texture:', error);
                // Fallback to another texture source
                const fallbackUrl = 'https://threejs.org/examples/textures/planets/earth_atmos_2048.jpg';
                const fallbackTexture = loader.load(fallbackUrl);
                if(earth && earth.material) {
                    earth.material.map = fallbackTexture;
                    earth.material.needsUpdate = true;
                }
            }
        );
        
        const material = new THREE.MeshPhongMaterial({ 
            map: earthTexture, 
            specular: 0x333333, 
            shininess: 25,
            emissive: 0x112244,
            emissiveIntensity: 0.15
        });
        
        earth = new THREE.Mesh(geometry, material); 
        earthGroup.add(earth);
        
        // lights
        scene.add(new THREE.AmbientLight(0x404040, 2.5)); 
        const dl = new THREE.DirectionalLight(0xffffff, 1.8); 
        dl.position.set(10000, 5000, 5000); 
        scene.add(dl);
        
        // events
        renderer.domElement.addEventListener('click', onClick);
        window.addEventListener('resize', onWindowResize);
        
        animate();
    }

    function highlightSatellite(id){
        try{
            // Clear any existing highlight first
            clearHighlight();
            
            // set desired highlighted id; create highlight mesh if absent; position will be updated in animate()
            highlightedId = id;
            if(!id) return;
            
            // Create a MUCH larger, more visible green wireframe box with pulsing animation
            const boxSize = 800; // Increased from 500 for MAXIMUM visibility
            const boxGeom = new THREE.BoxGeometry(boxSize, boxSize, boxSize);
            
            // Create both a wireframe box AND corner markers for extra visibility
            const boxMat = new THREE.LineBasicMaterial({ 
                color: 0x00ff00, 
                linewidth: 5,
                transparent: true,
                opacity: 0.9
            });
            
            // Create edges geometry for better line rendering
            const edges = new THREE.EdgesGeometry(boxGeom);
            const boxMesh = new THREE.LineSegments(edges, boxMat);
            boxMesh.userData = { isHighlight: true, createdTime: Date.now() };
            
            // Add corner spheres for extra visibility
            const cornerSize = 80;
            const cornerGeom = new THREE.SphereGeometry(cornerSize, 8, 8);
            const cornerMat = new THREE.MeshBasicMaterial({ color: 0x00ff00, transparent: true, opacity: 0.8 });
            const corners = [
                [-1, -1, -1], [1, -1, -1], [-1, 1, -1], [1, 1, -1],
                [-1, -1, 1], [1, -1, 1], [-1, 1, 1], [1, 1, 1]
            ];
            corners.forEach(([x, y, z]) => {
                const corner = new THREE.Mesh(cornerGeom, cornerMat.clone());
                corner.position.set(x * boxSize/2, y * boxSize/2, z * boxSize/2);
                corner.userData = { isHighlightCorner: true };
                boxMesh.add(corner);
            });
            
            earthGroup.add(boxMesh);
            selectedHighlight = boxMesh;
            
            console.log('Enhanced highlight box created for satellite ID:', id, 'with size:', boxSize);
        } catch(e){ 
            console.error('Error in highlightSatellite:', e); 
        }
    }
    
    function clearHighlight(){ 
        try{ 
            if(selectedHighlight){ 
                earthGroup.remove(selectedHighlight); 
                selectedHighlight.geometry?.dispose(); 
                selectedHighlight.material?.dispose(); 
                selectedHighlight = null; 
                highlightedId = null;
                console.log('Highlight cleared');
            } 
        }catch(e){
            console.error('Error in clearHighlight:', e);
        } 
    }

    function updateSatellites(satellites){
        try{ console.log('updateSatellites called, count=' + (satellites? satellites.length : 0)); }catch(e){}
        currentSatellites = satellites;
        satelliteObjects.forEach(o=>earthGroup.remove(o));
        satelliteLabels.forEach(l=>earthGroup.remove(l));
        satelliteHitObjects.forEach(o=>earthGroup.remove(o));
        satelliteObjects = []; satelliteLabels = []; satelliteHitObjects = [];
        if(trailsGroup){ while(trailsGroup.children.length) trailsGroup.remove(trailsGroup.children[0]); }
        const cfg = getConfig(); 
        
        satellites.forEach(sat=>{
            // Visible satellite sphere (moderate size for aesthetics)
            const visGeom = new THREE.SphereGeometry(200, 32, 32);
            const satColor = sat.color !== undefined ? sat.color : 0xffff00;
            const visMat = new THREE.MeshBasicMaterial({ color: satColor });

            // Large invisible hit target for easy tapping
            const hitGeom = new THREE.SphereGeometry(800, 16, 16);
            const hitMat = new THREE.MeshBasicMaterial({ color: 0x000000, opacity: 0.0, transparent: true, depthWrite: false });
            
            let posVec;
            try{
                if(sat.x != null && sat.y != null && sat.z != null && (sat.x !== 0 || sat.y !== 0 || sat.z !== 0)){
                    posVec = new THREE.Vector3(sat.x, sat.y, sat.z);
                } else {
                    posVec = latLonToVector3(sat.lat, sat.lon, 6371 + (sat.alt || 400));
                }
            }catch(e){
                posVec = latLonToVector3(sat.lat, sat.lon, 6371 + (sat.alt || 400));
            }

            const visSphere = new THREE.Mesh(visGeom, visMat);
            visSphere.position.copy(posVec);
            visSphere.userData = { id: sat.id, type: 'visible', name: sat.name };

            const hitSphere = new THREE.Mesh(hitGeom, hitMat);
            hitSphere.position.copy(posVec);
            hitSphere.userData = { id: sat.id, type: 'hit', name: sat.name };

            earthGroup.add(visSphere);
            earthGroup.add(hitSphere);

            satelliteObjects.push(visSphere);
            satelliteHitObjects.push(hitSphere);

            console.log('Added satellite:', sat.name, 'ID:', sat.id, 'pos:', posVec.x.toFixed(2), posVec.y.toFixed(2), posVec.z.toFixed(2));

            if(cfg.showTrails) drawProjectedTrail(sat, (cfg.trailSteps||40));
        });
        
        console.log('Total visible objects:', satelliteObjects.length, 'Total hit objects:', satelliteHitObjects.length);
    }

    function drawProjectedTrail(sat, steps){
        const orbitalRadius = 6371 + (sat.alt||400);
        let orbitalSpeed = 3.87;
        if(sat.id===25544) orbitalSpeed = 7.66;
        else if(sat.id===33591) orbitalSpeed = 7.40;
        const angularSpeedRadPerSec = orbitalSpeed / orbitalRadius;
        const stepSec = 30.0;
        const deltaAngleRad = angularSpeedRadPerSec * stepSec;
        const deltaLonDeg = (deltaAngleRad * 180) / Math.PI;
        let lon = sat.lon;
        const lat = sat.lat;
        const pts = [];
        for(let i=0;i<steps;i++){
            pts.push(latLonToVector3(lat, lon, orbitalRadius));
            lon += deltaLonDeg;
        }
        // Use the satellite's color for trails, or fallback to cyan
        const trailColor = sat.color !== undefined ? sat.color : 0x00ffff;
        for(let i=0;i<pts.length-1;i++){
            const segGeom = new THREE.BufferGeometry().setFromPoints([pts[i], pts[i+1]]);
            const t = i / Math.max(1, pts.length-1);
            const opacity = 0.9 * (1 - t);
            const mat = new THREE.LineBasicMaterial({ color: trailColor, transparent: true, opacity: opacity });
            const line = new THREE.Line(segGeom, mat);
            if(trailsGroup) trailsGroup.add(line);
        }
    }

    function onClick(e){
        console.log('=== CLICK EVENT DETECTED ===');
        const rect = renderer.domElement.getBoundingClientRect();
        const x = ((e.clientX - rect.left) / rect.width) * 2 - 1;
        const y = -((e.clientY - rect.top) / rect.height) * 2 + 1;
        const mouse = new THREE.Vector2(x, y);
        const ray = new THREE.Raycaster();
        ray.setFromCamera(mouse, camera);

        // First try precise hit objects (large invisible)
        let intersects = ray.intersectObjects(satelliteHitObjects, true);
        console.log('Hit targets intersections:', intersects.length);
        
        // Fallback: try visible spheres
        if(intersects.length === 0){
            const fallback = ray.intersectObjects(satelliteObjects, true);
            console.log('Visible intersections:', fallback.length);
            intersects = fallback;
        }
        
        // Fallback 2: deep-check all earthGroup children
        if(intersects.length === 0){
            const deep = ray.intersectObjects(earthGroup.children, true);
            console.log('Deep intersections:', deep.length);
            // Filter to those with an id
            intersects = deep.filter(x => x.object && x.object.userData && x.object.userData.id != null);
            console.log('Deep intersections with id:', intersects.length);
        }

        if(intersects.length > 0){
            const clicked = intersects[0].object;
            const id = clicked.userData?.id;
            console.log('✓ Clicked satellite id:', id, 'type:', clicked.userData?.type, 'name:', clicked.userData?.name);
            if(id != null){
                try{ Android.onSatelliteClicked(id); }catch(err){ console.error('Android callback failed', err); }
            }
        } else {
            const infoDiv = document.getElementById('info');
            if(infoDiv){ infoDiv.style.display = 'block'; infoDiv.innerText = 'Click registered, no satellite hit'; setTimeout(()=> infoDiv.style.display='none', 1200); }
            console.log('✗ No satellite clicked');
        }
        console.log('=== END CLICK EVENT ===');
    }

    function onWindowResize(){
        camera.aspect = window.innerWidth / window.innerHeight;
        camera.updateProjectionMatrix();
        renderer.setSize(window.innerWidth, window.innerHeight);
    }

    function animate(){
        requestAnimationFrame(animate);
        // Only apply default rotation when NOT in Bird's Eye View mode
        if (!birdsEyeViewMode) {
            earthGroup.rotation.x = 0.4;
        }
        earthGroup.rotation.y += rotationSpeed;
        if(selectedHighlight && highlightedId != null){
            const obj = satelliteHitObjects.find(o => o.userData && o.userData.id === highlightedId) ||
                        satelliteObjects.find(o => o.userData && o.userData.id === highlightedId);
            if(obj){ selectedHighlight.position.copy(obj.position); }
        }
        renderer.render(scene, camera);
    }

    init();
    try{ console.log('Globe initialized'); }catch(e){}
    
    // If Kotlin injected satellite data before the page finished loading, process it now.
    try{
        if(window && window.__pendingSatellites){
            if(typeof updateSatellites === 'function'){
                try{ updateSatellites(window.__pendingSatellites); delete window.__pendingSatellites; }
                catch(e){ console.error('processing pending satellites:'+e); }
            }
        }
    }catch(e){ /* ignore */ }

    // Apply any config that Kotlin set before page load
    try{ if(typeof applyConfig === 'function'){ applyConfig(); } }catch(e){}

    // Debug fallback: if no satellites appear after 2s, create a visible test satellite at lat=0,lon=0
    try{
        setTimeout(function(){
            try{
                if(typeof updateSatellites === 'function' && (currentSatellites == null || currentSatellites.length===0)){
                    console.log('No satellites received — drawing debug satellite');
                    const debug = { id: 99999, name: 'DEBUG', lat:0, lon:0, alt:400, x: null, y: null, z: null };
                    // compute vector and add sphere directly
                    const vec = latLonToVector3(debug.lat, debug.lon, 6371 + debug.alt);
                    const geometry = new THREE.SphereGeometry(240, 16, 16);
                    const material = new THREE.MeshBasicMaterial({ color: 0x00ff88 });
                    const sphere = new THREE.Mesh(geometry, material);
                    sphere.position.copy(vec);
                    sphere.userData = { id: debug.id };
                    earthGroup.add(sphere);
                    satelliteObjects.push(sphere);
                }
            }catch(e){ console.error('debug fallback error:'+e); }
        }, 2000);
    }catch(e){ /* ignore */ }
</script>

    <script>
    // continents JSON will be injected into this placeholder by the Kotlin factory using
    // a simple string replace; we keep it as a JS value so getContinentData() can return it.
    window.__CONTINENTS_DATA = /*__CONTINENTS_JSON__*/;
    function getContinentData(){ return (window.__CONTINENTS_DATA) ? window.__CONTINENTS_DATA : []; }

    // After continents are injected, force a redraw and process any pending satellites.
    try{
        console.log('continents injected, count=' + ((window.__CONTINENTS_DATA && window.__CONTINENTS_DATA.length) || 0));
    }catch(e){}
    try{ if(typeof applyConfig === 'function'){ applyConfig(); console.log('applyConfig called after continents injection'); } }catch(e) { console.error(e); }
    try{
        if(window && window.__pendingSatellites){
            if(typeof updateSatellites === 'function'){
                try{ updateSatellites(window.__pendingSatellites); console.log('processed pending satellites, count=' + window.__pendingSatellites.length); delete window.__pendingSatellites; }
                catch(e){ console.error('processing pending satellites:'+e); }
            }
        }
    }catch(e) { console.error(e) }
    </script>

</body>
</html>
    """.trimIndent()
}
