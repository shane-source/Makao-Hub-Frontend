package com.makaohub.android.ui

import android.view.MotionEvent
import android.view.View
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.makaohub.android.data.validOwnerPin
import org.maplibre.android.geometry.LatLng

/** Google Maps renderer recovered from the APK. MapLibre remains the no-key fallback. */
@Composable
fun GooglePropertyMap(
    pin: LatLng?,
    enabled: Boolean,
    onPin: (LatLng) -> Unit,
    approximate: Boolean,
    onUnavailable: () -> Unit
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val currentEnabled by rememberUpdatedState(enabled)
    val currentSelect by rememberUpdatedState(onPin)
    val currentUnavailable by rememberUpdatedState(onUnavailable)
    val available = remember(context) {
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == 0
    }
    if (!available) {
        LaunchedEffect(Unit) { currentUnavailable() }
        return
    }

    var map by remember { mutableStateOf<GoogleMap?>(null) }
    val view = remember(context) {
        MapView(context).apply {
            id = View.NO_ID
            isSaveEnabled = false
            isSaveFromParentEnabled = false
            onCreate(null)
        }
    }
    DisposableEffect(view, owner) {
        var started = false
        var resumed = false
        fun start() { if (!started) { view.onStart(); started = true } }
        fun resume() { start(); if (!resumed) { view.onResume(); resumed = true } }
        fun pause() { if (resumed) { view.onPause(); resumed = false } }
        fun stop() { pause(); if (started) { view.onStop(); started = false } }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> start()
                Lifecycle.Event.ON_RESUME -> resume()
                Lifecycle.Event.ON_PAUSE -> pause()
                Lifecycle.Event.ON_STOP, Lifecycle.Event.ON_DESTROY -> stop()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) start()
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) resume()
        view.getMapAsync { ready ->
            ready.uiSettings.isMapToolbarEnabled = false
            ready.setLatLngBoundsForCameraTarget(LatLngBounds(
                com.google.android.gms.maps.model.LatLng(-5.0, 33.0),
                com.google.android.gms.maps.model.LatLng(5.5, 42.5)
            ))
            ready.setOnMapClickListener { point ->
                if (currentEnabled && validOwnerPin(point.latitude, point.longitude)) {
                    currentSelect(LatLng(point.latitude, point.longitude))
                }
            }
            map = ready
        }
        onDispose {
            owner.lifecycle.removeObserver(observer)
            view.setOnTouchListener(null)
            stop()
            view.onDestroy()
            map = null
        }
    }
    LaunchedEffect(map, pin?.latitude, pin?.longitude, approximate, enabled) {
        map?.let { ready ->
            ready.clear()
            ready.uiSettings.setAllGesturesEnabled(enabled)
            val target = pin?.takeIf { validOwnerPin(it.latitude, it.longitude) }
                ?: LatLng(-1.2921, 36.8219)
            val googlePin = com.google.android.gms.maps.model.LatLng(target.latitude, target.longitude)
            if (pin != null) {
                if (approximate) ready.addCircle(CircleOptions().center(googlePin).radius(1800.0)
                    .fillColor(0x33573B2D).strokeColor(0xFF573B2D.toInt()).strokeWidth(2f))
                else ready.addMarker(MarkerOptions().position(googlePin).title("Property location"))
            }
            ready.moveCamera(CameraUpdateFactory.newLatLngZoom(googlePin, if (pin == null || approximate) 11f else 17f))
        }
    }
    AndroidView(
        factory = { view },
        modifier = Modifier.fillMaxWidth().height(360.dp),
        update = {
            it.contentDescription = if (approximate) "Approximate listing area, not an exact property pin" else "Property location map"
            it.setOnTouchListener { target, event ->
                target.parent?.requestDisallowInterceptTouchEvent(enabled && event.actionMasked !in
                    listOf(MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL))
                false
            }
        }
    )
}
