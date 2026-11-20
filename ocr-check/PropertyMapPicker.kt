package com.makaohub.android.ui

import android.content.ComponentCallbacks
import android.content.res.Configuration
import android.location.Geocoder
import android.view.MotionEvent
import android.view.View
import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.makaohub.android.BuildConfig
import com.makaohub.android.data.validOwnerPin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre
import org.maplibre.android.RenderingEngine
import org.maplibre.android.WellKnownTileServer
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.log.Logger
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.AttributionDialogManager
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import java.util.Locale

private const val MAP_STYLE = "https://tiles.openfreemap.org/styles/liberty"
private const val PIN_SOURCE = "private-property-pin"

/** Local GeoJSON only: never send the marker in a provider URL or save its state. */
internal fun privatePinFeatures(pin: LatLng?): FeatureCollection = FeatureCollection.fromFeatures(
    if (pin != null && validOwnerPin(pin.latitude, pin.longitude))
        arrayOf(Feature.fromGeometry(Point.fromLngLat(pin.longitude, pin.latitude))) else emptyArray()
)

/** In-memory selection; only the parent's explicit Save action calls the owner API. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PropertyMapPicker(pin: LatLng?, enabled: Boolean, showDeveloperDetails: Boolean = true, onPin: (LatLng) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    var mapProblem by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var county by remember { mutableStateOf<String?>(null) }
    var countyOpen by remember { mutableStateOf(false) }
    var searchNonce by remember { mutableIntStateOf(0) }
    ExposedDropdownMenuBox(expanded = countyOpen, onExpandedChange = { if (enabled) countyOpen = it }) {
        OutlinedTextField(value = county ?: "", onValueChange = {}, readOnly = true,
            label = { Text("Select county") }, placeholder = { Text("Choose from Kenya's 47 counties") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(countyOpen) }, enabled = enabled,
            modifier = Modifier.menuAnchor().fillMaxWidth())
        ExposedDropdownMenu(expanded = countyOpen, onDismissRequest = { countyOpen = false }) {
            KenyanCounties.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = {
                county = option; countyOpen = false; problem = null
            }) }
        }
    }
    Gap(8)
    OutlinedTextField(query, { query = it.take(150) }, label = { Text("Find address") },
        enabled = enabled, singleLine = true, modifier = Modifier.fillMaxWidth())
    LaunchedEffect(query, county, searchNonce, enabled) {
        val selectedCounty = county
        if (!enabled || selectedCounty == null || query.trim().length < 3) return@LaunchedEffect
        kotlinx.coroutines.delay(if (searchNonce == 0) 900L else 250L)
        if (!Geocoder.isPresent()) {
            problem = "Address lookup is unavailable on this device. Select the pin manually."
            return@LaunchedEffect
        }
        searching = true; problem = null
        try {
            @Suppress("DEPRECATION")
            val point = withContext(Dispatchers.IO) {
                Geocoder(context, Locale.getDefault()).getFromLocationName(
                    "${query.trim()}, $selectedCounty County, Kenya", 1, -5.0, 33.0, 5.5, 42.5)
                    ?.firstOrNull()?.let { LatLng(it.latitude, it.longitude) }
            }
            if (point != null && validOwnerPin(point.latitude, point.longitude)) onPin(point)
            else problem = "Address not found. Check the county or select a point on the map."
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { problem = "Address lookup failed. Check the map and try again." }
        finally { searching = false }
    }
    OutlinedButton(onClick = {
        if (county == null) problem = "Select a county first."
        else if (query.trim().length < 3) problem = "Enter at least three address characters."
        else searchNonce++
    }, enabled = enabled && !searching) { Text(if (searching) "Searching…" else "Find address") }
    problem?.let { Note(it); Gap(10) }
    BodyCopy("Pan and zoom, then tap the exact property point. Address results are approximate—check the pin before saving.", true)
    Gap(12)
    key(retry) {
        ProviderPropertyMap(pin, enabled && !searching, onPin, false) { mapProblem = it }
    }
    // Credits remain visible without opening the SDK attribution control.
    BodyCopy("© OpenMapTiles · © OpenStreetMap contributors · Maps by OpenFreeMap", true)
    mapProblem?.let {
        Note(it)
        OutlinedButton({ mapProblem = null; retry++ }, enabled = enabled && !searching) { Text("Retry map") }
    }
    Gap(10)
    BodyCopy(if (pin == null) "No property pin selected yet."
        else "Property pin selected · Exact location stays private.", true)
    Gap(12)
    if (showDeveloperDetails) Note("No map API key or billing setup is needed. OpenFreeMap receives requests for the map areas you view; address search uses your device's geocoding service. No Makao JWTs, ID documents or payment details are sent to these services.")
}

/** Native MapView hosted in Compose, with balanced lifecycle and no saved private camera. */
@Composable
private fun NativePropertyMap(pin: LatLng?, enabled: Boolean, onPin: (LatLng) -> Unit,
    onProblem: (String?) -> Unit, approximate: Boolean = false) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val uriHandler = LocalUriHandler.current
    val currentPin by rememberUpdatedState(pin)
    val canSelect by rememberUpdatedState(enabled)
    val select by rememberUpdatedState(onPin)
    val report by rememberUpdatedState(onProblem)
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var source by remember { mutableStateOf<GeoJsonSource?>(null) }
    var showCredits by remember { mutableStateOf(false) }
    val view = remember(context) {
        MapLibre.getInstance(context, null, WellKnownTileServer.MapLibre, RenderingEngine.Type.OPENGL)
        Logger.setVerbosity(Logger.NONE) // Avoid SDK URL/camera/marker logging.
        MapView(context, MapLibreMapOptions.createFromAttributes(context).textureMode(true)).apply {
            id = View.NO_ID
            isSaveEnabled = false
            isSaveFromParentEnabled = false
            onCreate(null)
        }
    }
    DisposableEffect(view, owner) {
        var alive = true
        var started = false
        var resumed = false
        fun start() { if (!started) { view.onStart(); started = true } }
        fun resume() { start(); if (!resumed) { view.onResume(); resumed = true } }
        fun pause() { if (resumed) { view.onPause(); resumed = false } }
        fun stop() { showCredits = false; pause(); if (started) { view.onStop(); started = false } }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> start()
                Lifecycle.Event.ON_RESUME -> resume()
                Lifecycle.Event.ON_PAUSE -> pause()
                Lifecycle.Event.ON_STOP, Lifecycle.Event.ON_DESTROY -> stop()
                else -> Unit
            }
        }
        val memory = object : ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) = Unit
            override fun onLowMemory() { if (alive) view.onLowMemory() }
        }
        owner.lifecycle.addObserver(observer)
        context.registerComponentCallbacks(memory)
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) start()
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) resume()
        view.addOnDidFailLoadingMapListener {
            if (alive) report("Map could not load. Check your internet connection and retry. Your saved location is unchanged.")
        }
        view.getMapAsync { nativeMap ->
            if (!alive) return@getMapAsync
            map = nativeMap
            nativeMap.setLatLngBoundsForCameraTarget(LatLngBounds.Builder()
                .include(LatLng(-5.0, 33.0)).include(LatLng(5.5, 42.5)).build())
            // Initial camera only, never a selected/default property pin.
            nativeMap.cameraPosition = CameraPosition.Builder()
                .target(currentPin ?: LatLng(-1.2921, 36.8219)).zoom(if (currentPin == null || approximate) 11.0 else 17.0).build()
            nativeMap.uiSettings.setAllGesturesEnabled(canSelect)
            nativeMap.uiSettings.isAttributionEnabled = true
            // The default feedback action includes the camera coordinates in an external URL.
            // Keep the credit control, but expose only fixed provider/licence links.
            nativeMap.uiSettings.setAttributionDialogManager(object : AttributionDialogManager(context, nativeMap) {
                override fun onClick(view: View) { if (alive) showCredits = true }
            })
            nativeMap.addOnMapClickListener { point ->
                if (alive && canSelect && source != null && validOwnerPin(point.latitude, point.longitude)) {
                    select(LatLng(point.latitude, point.longitude))
                    true
                } else false
            }
            nativeMap.setStyle(MAP_STYLE) { style ->
                if (alive) {
                    val marker = GeoJsonSource(PIN_SOURCE, privatePinFeatures(currentPin))
                    style.addSource(marker)
                    style.addLayer(CircleLayer("private-pin-marker", PIN_SOURCE).withProperties(
                        circleColor("#573B2D"), circleRadius(if (approximate) 36f else 8f),
                        circleOpacity(if (approximate) 0.2f else 1f), circleStrokeColor(if (approximate) "#573B2D" else "#FFFFFF"), circleStrokeWidth(3f)))
                    source = marker
                    report(null)
                }
            }
        }
        onDispose {
            alive = false
            owner.lifecycle.removeObserver(observer)
            context.unregisterComponentCallbacks(memory)
            view.setOnTouchListener(null)
            stop()
            view.onDestroy()
            source = null
            map = null
        }
    }
    LaunchedEffect(source, pin?.latitude, pin?.longitude) {
        source?.setGeoJson(privatePinFeatures(pin))
        if (pin != null && validOwnerPin(pin.latitude, pin.longitude)) {
            map?.cameraPosition = CameraPosition.Builder().target(pin).zoom(if (approximate) 11.0 else 17.0).build()
        }
    }
    AndroidView(factory = { view }, modifier = Modifier.fillMaxWidth().height(360.dp), update = { nativeView ->
        map?.uiSettings?.setAllGesturesEnabled(enabled)
        nativeView.contentDescription = if (approximate) "Approximate listing area, not an exact property pin" else "Property location map"
        // Preserve map gestures inside the scrolling form; no GPS tracking.
        nativeView.setOnTouchListener { target, event ->
            target.parent?.requestDisallowInterceptTouchEvent(enabled && event.actionMasked !in
                listOf(MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL))
            false
        }
    })
    if (showCredits) {
        AlertDialog(onDismissRequest = { showCredits = false }, title = { Text("Map credits") },
            text = {
                Column {
                    Text("© OpenMapTiles · © OpenStreetMap contributors · Maps by OpenFreeMap")
                    listOf("OpenStreetMap licence" to "https://www.openstreetmap.org/copyright",
                        "OpenMapTiles" to "https://openmaptiles.org/",
                        "OpenFreeMap" to "https://openfreemap.org/").forEach { (label, url) ->
                        TextButton({
                            try { uriHandler.openUri(url) }
                            catch (_: Exception) { report("No browser is available to open the map credit link.") }
                        }) { Text(label) }
                    }
                }
            }, confirmButton = { TextButton({ showCredits = false }) { Text("Close") } })
    }
}

@Composable
private fun ProviderPropertyMap(pin: LatLng?, enabled: Boolean, onPin: (LatLng) -> Unit,
    approximate: Boolean, onProblem: (String?) -> Unit) {
    var googleUnavailable by remember { mutableStateOf(BuildConfig.GOOGLE_MAPS_API_KEY.isBlank()) }
    if (!googleUnavailable) {
        GooglePropertyMap(pin, enabled, onPin, approximate) { googleUnavailable = true }
    } else {
        NativePropertyMap(pin, enabled, onPin, onProblem, approximate)
    }
}

/** Only pass approximate public coordinates unless the backend returned a paid exact location. */
@Composable internal fun RenterAreaMap(point: LatLng, exact: Boolean = false) {
    var problem by remember { mutableStateOf<String?>(null) }
    key(point.latitude, point.longitude, exact) { ProviderPropertyMap(point, false, {}, !exact) { problem = it } }
    problem?.let { Text("Map unavailable. Please try again later.") }
}
