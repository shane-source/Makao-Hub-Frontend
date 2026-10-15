package com.makaohub.android

import com.makaohub.android.ui.privatePinFeatures
import org.junit.Assert.*
import org.junit.Test
import org.maplibre.android.geometry.LatLng
import org.maplibre.geojson.Point

/** Local marker data only; no native map, tiles, email or backend writes. */
class PropertyMapPinTest {
    @Test fun initialViewDoesNotCreateAPropertyPin() {
        assertTrue(privatePinFeatures(null).features()!!.isEmpty())
    }

    @Test fun supportedPinPreservesLatitudeLongitudeOrderWithoutExtraData() {
        val feature = privatePinFeatures(LatLng(-1.2921, 36.8219)).features()!!.single()
        val point = feature.geometry() as Point
        assertEquals(-1.2921, point.latitude(), 0.0)
        assertEquals(36.8219, point.longitude(), 0.0)
        assertTrue(feature.properties()!!.entrySet().isEmpty())
    }

    @Test fun invalidOrUnsupportedPointsDoNotBecomeMarkers() {
        for (point in listOf(LatLng(51.5, -0.12), LatLng(-5.1, 36.8), LatLng(-1.3, 42.6))) {
            assertTrue(privatePinFeatures(point).features()!!.isEmpty())
        }
        // MapLibre itself rejects non-finite values before a LatLng can reach the picker.
        assertThrows(IllegalArgumentException::class.java) { LatLng(Double.NaN, 36.8) }
        assertThrows(IllegalArgumentException::class.java) { LatLng(-1.3, Double.POSITIVE_INFINITY) }
    }
}
