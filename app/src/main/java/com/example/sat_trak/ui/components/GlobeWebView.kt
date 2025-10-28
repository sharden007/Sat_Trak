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
import com.example.sat_trak.data.models.BoundingBox
import com.example.sat_trak.data.models.Continent
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.squareup.moshi.Types
import java.lang.StringBuilder

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GlobeWebView(
    satellites: List<SatelliteData>,
    modifier: Modifier = Modifier,
    onSatelliteClick: (SatelliteData) -> Unit = {},
    onZoomControlsReady: ((zoomIn: () -> Unit, zoomOut: () -> Unit) -> Unit)? = null,
    // visual flags
    showTrails: Boolean = true,
    trailSteps: Int = 40,
    useHighResTiles: Boolean = false,
    // optional selected satellite id (null = none)
    selectedSatelliteId: Int? = null
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
                        satellites.find { it.id == satelliteId }?.let { satellite ->
                            onSatelliteClick(satellite)
                        }
                    }

                    @Suppress("unused")
                    @JavascriptInterface
                    fun onConsole(message: String) {
                        // forward JS console messages to Android logcat for debugging
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
            if (satellites.isNotEmpty()) {
                // Also log to the WebView console (forwarded to Android) the count being pushed
                try {
                    val count = satellites.size
                    webView.evaluateJavascript("console.log('Kotlin->JS satellite push count: $count');", null)
                } catch (_: Throwable) { }

                // Build a safe JSON representation of the satellites list by escaping string fields
                val satellitesJson = satellites.joinToString(",") { sat ->
                    val nameEsc = sat.name.replace("\"", "\\\"")
                    val typeEsc = sat.type.replace("\"", "\\\"")
                    "{\"id\":${sat.id},\"name\":\"$nameEsc\",\"x\":${sat.x},\"y\":${sat.y},\"z\":${sat.z},\"lat\":${sat.latitude},\"lon\":${sat.longitude},\"alt\":${sat.altitude},\"type\":\"$typeEsc\"}"
                }

                // Queue updates if the page hasn't defined updateSatellites yet. This prevents the
                // JS from ignoring the first push if the page is still loading.
                val js = "window.__pendingSatellites = [${satellitesJson}]; if(typeof updateSatellites === 'function'){ try{ updateSatellites(window.__pendingSatellites); delete window.__pendingSatellites; }catch(e){ console.log('sat update error:'+e); } }"
                webView.evaluateJavascript(js, null)
            }

            // Inject config
            try {
                val jsConfig = "window.__satTrakConfig = { showTrails: ${showTrails}, trailSteps: ${trailSteps}, useHighResTiles: ${useHighResTiles} };"
                webView.evaluateJavascript(jsConfig, null)
                webView.evaluateJavascript("if(typeof applyConfig === 'function'){ applyConfig(); }", null)
            } catch (_: Throwable) {
                // swallow
            }

            // highlight selected satellite (or clear if null)
            try {
                if (selectedSatelliteId != null) {
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
        #rotationToggle { position: fixed; bottom: 20px; left: 50%; transform: translateX(-50%); background: rgba(0,0,0,0.95); color:#fff; border:3px solid #4CAF50; padding:15px 25px; border-radius:10px; font-size:16px; cursor:pointer; z-index:10000 }
    </style>
</head>
<body>
<div id="info"></div>
<button id="rotationToggle">🌍 Rotation: Simulated Speed</button>
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

    function getConfig(){ return (window.__satTrakConfig) ? window.__satTrakConfig : { showTrails:true, trailSteps:40, useHighResTiles:false }; }

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

    // Apply configuration changes (e.g. high-res tiles toggle) by updating the earth texture
    function applyConfig(){
        try{
            const cfg = getConfig();
            if(earth && earth.material){
                const canvas = document.createElement('canvas');
                canvas.width = cfg.useHighResTiles ? 4096 : 2048;
                canvas.height = cfg.useHighResTiles ? 2048 : 1024;
                const ctx = canvas.getContext('2d');
                // Enable anti-aliasing for smoother edges
                ctx.imageSmoothingEnabled = true;
                ctx.imageSmoothingQuality = 'high';
                ctx.fillStyle = '#1a4d8f'; ctx.fillRect(0,0,canvas.width, canvas.height);
                const continents = (typeof getContinentData === 'function') ? getContinentData() : [];
                ctx.fillStyle = '#d4a673'; 
                ctx.strokeStyle = '#8b6914'; 
                ctx.lineWidth = 3; // Thicker borders
                ctx.lineJoin = 'round'; // Smoother corners
                ctx.lineCap = 'round'; // Smoother line ends
                continents.forEach(cont=>{
                    try{
                        if(Array.isArray(cont.coordinates) && cont.coordinates.length>0){
                            console.log('Drawing continent ' + cont.id + ' with ' + cont.coordinates.length + ' points');
                            ctx.beginPath();
                            cont.coordinates.forEach((coord,i)=>{ 
                                // coord is [lon, lat] in GeoJSON format
                                const lon = coord[0];
                                const lat = coord[1];
                                const x = ((lon+180)/360)*canvas.width; 
                                const y = ((90-lat)/180)*canvas.height; 
                                if(i===0) ctx.moveTo(x,y); else ctx.lineTo(x,y); 
                            });
                            ctx.closePath(); ctx.fill(); ctx.stroke();
                        } else if(cont.boundingBox){
                            // fallback: draw bounding box rectangle when polygon coords are unavailable
                            const minLat = cont.boundingBox.minLat; const maxLat = cont.boundingBox.maxLat;
                            const minLon = cont.boundingBox.minLon; const maxLon = cont.boundingBox.maxLon;
                            const x1 = ((minLon+180)/360)*canvas.width; const y1 = ((90-maxLat)/180)*canvas.height;
                            const x2 = ((maxLon+180)/360)*canvas.width; const y2 = ((90-minLat)/180)*canvas.height;
                            ctx.beginPath(); ctx.moveTo(x1,y1); ctx.lineTo(x2,y1); ctx.lineTo(x2,y2); ctx.lineTo(x1,y2); ctx.closePath(); ctx.fill(); ctx.stroke();
                        }
                    }catch(e){ console.error('continent draw error:'+e); }
                });
                const tex = new THREE.CanvasTexture(canvas); tex.needsUpdate = true;
                if(earth.material.map && earth.material.map.dispose) earth.material.map.dispose();
                earth.material.map = tex; earth.material.needsUpdate = true;
            }
        }catch(e){ console.error(e) }
    }

    // Simple zoom handlers used by the Android UI
    function zoomIn(){ try{ camera.position.z = Math.max(1000, camera.position.z - 2000); } catch(e){ console.error(e); } }
    function zoomOut(){ try{ camera.position.z = Math.min(100000, camera.position.z + 2000); } catch(e){ console.error(e); } }

    let scene, camera, renderer, earth, earthGroup, trailsGroup;
    let satelliteObjects = []; let satelliteLabels = []; let currentSatellites = [];
    let selectedHighlight = null;
    let highlightedId = null;
    
    // Rotation speed control
    let rotationSpeed = 0.0005; // Simulated speed (default)
    let isRealTimeSpeed = false; // false = simulated, true = real-time

    function init(){
        scene = new THREE.Scene();
        camera = new THREE.PerspectiveCamera(60, window.innerWidth/window.innerHeight, 0.1, 50000);
        camera.position.z = 18000;
        renderer = new THREE.WebGLRenderer({antialias:true, alpha:true}); renderer.setSize(window.innerWidth, window.innerHeight); document.body.appendChild(renderer.domElement);
        earthGroup = new THREE.Group(); scene.add(earthGroup);
        trailsGroup = new THREE.Group(); earthGroup.add(trailsGroup);
        const geometry = new THREE.SphereGeometry(6371, 256, 256);
        // initial canvas texture
        const cfg = getConfig(); 
        const canvas = document.createElement('canvas'); 
        canvas.width = cfg.useHighResTiles ? 4096 : 2048; 
        canvas.height = cfg.useHighResTiles ? 2048 : 1024; 
        const ctx = canvas.getContext('2d'); 
        // Enable anti-aliasing for smoother edges
        ctx.imageSmoothingEnabled = true;
        ctx.imageSmoothingQuality = 'high';
        ctx.fillStyle = '#1a4d8f'; ctx.fillRect(0,0,canvas.width, canvas.height);
        const continents = getContinentData(); 
        ctx.fillStyle = '#d4a673'; 
        ctx.strokeStyle = '#8b6914'; 
        ctx.lineWidth = 3; // Thicker borders
        ctx.lineJoin = 'round'; // Smoother corners
        ctx.lineCap = 'round'; // Smoother line ends
        let usedCanvasTexture = false;
        if(Array.isArray(continents) && continents.length>0){
            continents.forEach(cont=>{
                try{
                    if(Array.isArray(cont.coordinates) && cont.coordinates.length>0){
                        console.log('Drawing continent ' + cont.id + ' with ' + cont.coordinates.length + ' points');
                        ctx.beginPath();
                        cont.coordinates.forEach((coord,i)=>{ 
                            // coord is [lon, lat] in GeoJSON format
                            const lon = coord[0];
                            const lat = coord[1];
                            const x = ((lon+180)/360)*canvas.width; 
                            const y = ((90-lat)/180)*canvas.height; 
                            if(i===0) ctx.moveTo(x,y); else ctx.lineTo(x,y); 
                        });
                        ctx.closePath(); ctx.fill(); ctx.stroke();
                        usedCanvasTexture = true;
                    } else if(cont.boundingBox){
                        // draw bounding box rectangle when polygon coords are unavailable
                        const minLat = cont.boundingBox.minLat; const maxLat = cont.boundingBox.maxLat;
                        const minLon = cont.boundingBox.minLon; const maxLon = cont.boundingBox.maxLon;
                        const x1 = ((minLon+180)/360)*canvas.width; const y1 = ((90-maxLat)/180)*canvas.height;
                        const x2 = ((maxLon+180)/360)*canvas.width; const y2 = ((90-minLat)/180)*canvas.height;
                        ctx.beginPath(); ctx.moveTo(x1,y1); ctx.lineTo(x2,y1); ctx.lineTo(x2,y2); ctx.lineTo(x1,y2); ctx.closePath(); ctx.fill(); ctx.stroke();
                        usedCanvasTexture = true;
                    }
                }catch(e){ console.error('continent draw error:'+e); }
            });
        }
        let material;
        if(usedCanvasTexture){
            const earthTexture = new THREE.CanvasTexture(canvas); earthTexture.needsUpdate = true;
            material = new THREE.MeshPhongMaterial({ map: earthTexture, specular:0x3366aa, shininess:30, emissive:0x0a2a5a, emissiveIntensity:0.2 });
        } else {
            // as a final fallback (should be rare) load a remote Earth texture so continents are visible
            const loader = new THREE.TextureLoader();
            const fallbackUrl = 'https://threejs.org/examples/textures/land_ocean_ice_cloud_2048.jpg';
            const tex = loader.load(fallbackUrl);
            material = new THREE.MeshPhongMaterial({ map: tex, specular:0x3366aa, shininess:30, emissive:0x0a2a5a, emissiveIntensity:0.2 });
        }
        const materialFinal = material;
        const materialToUse = materialFinal;
        const materialObj = materialToUse;
        
        // use materialObj below
        const materialInstance = materialObj;
        
        const materialToAssign = materialInstance;
        
        // create earth with chosen material
        const materialForEarth = materialToAssign;
        const earthMeshMaterial = materialForEarth;
        const materialUsed = earthMeshMaterial;
        const materialRef = materialUsed;
        const materialActual = materialRef;
        const materialReady = materialActual;
        const materialFinalReady = materialReady;
        const materialReadyToUse = materialFinalReady;
        const materialObjFinal = materialReadyToUse;
        const materialToSet = materialObjFinal;
        const material_real = materialToSet;
        const material_final_real = material_real;
        const finalMaterial = material_final_real;
        
        earth = new THREE.Mesh(geometry, finalMaterial); earthGroup.add(earth);
        // lights
        scene.add(new THREE.AmbientLight(0x404040, 2.5)); const dl = new THREE.DirectionalLight(0xffffff,1.8); dl.position.set(10000,5000,5000); scene.add(dl);
        // events
        renderer.domElement.addEventListener('click', onClick);
        window.addEventListener('resize', onWindowResize);
        
        // Add rotation toggle button event listener
        const toggleBtn = document.getElementById('rotationToggle');
        if(toggleBtn){
            toggleBtn.addEventListener('click', function(){
                isRealTimeSpeed = !isRealTimeSpeed;
                if(isRealTimeSpeed){
                    // Real-time: Earth rotates 360° in 24 hours = 0.004167°/sec
                    // At 60fps, that's 0.004167/60 = 0.0000694°/frame
                    rotationSpeed = 0.0000694 * (Math.PI / 180); // Convert to radians
                    toggleBtn.textContent = '🌍 Rotation: Real-Time Speed';
                    toggleBtn.style.borderColor = '#2196F3';
                    console.log('Rotation: Real-Time Speed');
                } else {
                    // Simulated: faster for better visualization
                    rotationSpeed = 0.0005;
                    toggleBtn.textContent = '🌍 Rotation: Simulated Speed';
                    toggleBtn.style.borderColor = '#4CAF50';
                    console.log('Rotation: Simulated Speed');
                }
            });
        }
        
        animate();
    }

    function highlightSatellite(id){
        try{
            // set desired highlighted id; create highlight mesh if absent; position will be updated in animate()
            highlightedId = id;
            if(!id) return;
            if(!selectedHighlight){
                const boxSize = 360; // ~3x satellite marker diameter for visibility
                const boxGeom = new THREE.BoxGeometry(boxSize, boxSize, boxSize);
                const boxMat = new THREE.MeshBasicMaterial({ color: 0x00ff00, wireframe: true });
                const boxMesh = new THREE.Mesh(boxGeom, boxMat);
                boxMesh.userData = { isHighlight: true };
                earthGroup.add(boxMesh);
                selectedHighlight = boxMesh;
            }
        } catch(e){ console.error(e) }
    }
    function clearHighlight(){ try{ if(selectedHighlight){ earthGroup.remove(selectedHighlight); selectedHighlight.geometry?.dispose(); selectedHighlight.material?.dispose(); selectedHighlight = null; } }catch(e){console.error(e)} }

    function updateSatellites(satellites){
        try{ console.log('updateSatellites called, count=' + (satellites? satellites.length : 0)); }catch(e){}
        currentSatellites = satellites;
        satelliteObjects.forEach(o=>earthGroup.remove(o)); satelliteLabels.forEach(l=>earthGroup.remove(l)); satelliteObjects = []; satelliteLabels = [];
        if(trailsGroup){ while(trailsGroup.children.length) trailsGroup.remove(trailsGroup.children[0]); }
        const cfg = getConfig(); satellites.forEach(sat=>{
            const geometry = new THREE.SphereGeometry(120, 16, 16);
            const material = new THREE.MeshBasicMaterial({ color: sat.id===25544?0xff0000:(sat.id===33591?0x00ff00:0xffff00) });
            // compute position: use provided x/y/z when available and non-zero, otherwise compute from lat/lon
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
            const sphere = new THREE.Mesh(geometry, material);
            sphere.position.copy(posVec);
            sphere.userData={id:sat.id}; earthGroup.add(sphere); satelliteObjects.push(sphere);
            // labels skipped for brevity
            if(cfg.showTrails) drawProjectedTrail(sat, cfg.trailSteps||40);
        });
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
        for(let i=0;i<pts.length-1;i++){
            const segGeom = new THREE.BufferGeometry().setFromPoints([pts[i], pts[i+1]]);
            const t = i / Math.max(1, pts.length-1);
            const opacity = 0.9 * (1 - t);
            const mat = new THREE.LineBasicMaterial({ color: 0x00ffff, transparent: true, opacity: opacity });
            const line = new THREE.Line(segGeom, mat);
            if(trailsGroup) trailsGroup.add(line);
        }
    }

    function onClick(e){
        const rect = renderer.domElement.getBoundingClientRect();
        const x = ((e.clientX - rect.left) / rect.width) * 2 - 1;
        const y = -((e.clientY - rect.top) / rect.height) * 2 + 1;
        const mouse = new THREE.Vector2(x, y);
        const ray = new THREE.Raycaster();
        ray.setFromCamera(mouse, camera);
        const intersects = ray.intersectObjects(satelliteObjects);
        if(intersects.length > 0){
            const clicked = intersects[0].object;
            const sat = currentSatellites.find(s => s.id === clicked.userData.id);
            if(sat && typeof Android !== 'undefined' && Android.onSatelliteClicked){
                Android.onSatelliteClicked(sat.id);
            }
        }
    }

    function onWindowResize(){
        camera.aspect = window.innerWidth / window.innerHeight;
        camera.updateProjectionMatrix();
        renderer.setSize(window.innerWidth, window.innerHeight);
    }

    function animate(){
        requestAnimationFrame(animate);
        earthGroup.rotation.x = 0.4;
        earthGroup.rotation.y += rotationSpeed;
        if(selectedHighlight && highlightedId != null){
            const obj = satelliteObjects.find(o => o.userData && o.userData.id === highlightedId);
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
    try{ if(typeof applyConfig === 'function'){ applyConfig(); console.log('applyConfig called after continents injection'); } }catch(e){ console.error(e); }
    try{
        if(window && window.__pendingSatellites){
            if(typeof updateSatellites === 'function'){
                try{ updateSatellites(window.__pendingSatellites); console.log('processed pending satellites, count=' + window.__pendingSatellites.length); delete window.__pendingSatellites; }
                catch(e){ console.error('processing pending satellites:'+e); }
            }
        }
    }catch(e){ console.error(e) }
    </script>

</body>
</html>
    """.trimIndent()
}
