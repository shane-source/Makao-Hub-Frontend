# MapLibre / OpenFreeMap property picker

## Scope, decisions and design

The owner requested replacing Google Maps with a no-billing-account option for Android testing. This changes only map presentation and its dependency/configuration. No backend code, database schema/data, authentication, prices, payment rules, Google OAuth or splash artwork is changed. No separate website is created.

MapLibre Native is pinned to `org.maplibre.gl:android-sdk-opengl:13.6.1`; its published POM uses Kotlin 2.2.10, matching this app. OpenGL is selected explicitly so the app does not acquire a Vulkan-only hardware requirement or choose an unavailable renderer on a Vulkan-capable phone. The style is the fixed HTTPS OpenFreeMap Liberty endpoint. OpenFreeMap advertises a public service without registration, keys or billing; it is provided as-is, not an availability guarantee. Address search stays on Android Geocoder, whose availability depends on the device. No new geocoding provider or offline download feature is introduced.

Flow: authenticated LANDLORD -> own draft -> public area + private map -> optional address search/pan/zoom/tap -> local selected marker -> explicit Save -> existing authenticated PUT -> backend ownership/range checks and storage -> listing preview. A camera view alone never becomes a property pin. Public renter and paid-location entitlement endpoints are untouched. Failure to load the map does not alter a stored location.

## Every changed file and method

### /home/hp/Desktop/makao-hub-frontend/app/build.gradle.kts

Removes Maps Compose 6.12.2, the MAKAO_MAPS_API_KEY lookup, MAPS_CONFIGURED Boolean and MAPS_API_KEY manifest placeholder. Adds MapLibre Native's OpenGL 13.6.1 artifact using existing Maven Central. Without the dependency native map classes cannot compile. No Compose wrapper library, WebView or JavaScript bridge is added. OAuth/Retrofit/Compose versions stay unchanged.

### /home/hp/Desktop/makao-hub-frontend/app/src/main/AndroidManifest.xml

Removes com.google.android.geo.API_KEY metadata and explicitly removes ACCESS_COARSE_LOCATION/ACCESS_FINE_LOCATION permissions contributed by the map SDK through manifest merging. No map credential is embedded. Existing Internet permission, HTTPS/release cleartext policy, no-backup and optional camera rules remain. Removing Internet permission prevents tiles/API calls; removing the manifest-removal rules reintroduces unnecessary GPS permissions.

### /home/hp/Desktop/makao-hub-frontend/app/src/main/java/com/makaohub/android/ui/PropertyMapPicker.kt

- MAP_STYLE is a fixed provider HTTPS URL, not client-supplied data. PIN_SOURCE identifies a local GeoJSON source, not a provider query.
- `privatePinFeatures`: returns an empty collection for absent/unsupported pins, or a point using GeoJSON's longitude-then-latitude order. Reuses `validOwnerPin` for finite geographic bounds. The feature contains no owner/listing ID or other metadata. Removing empty-state handling could falsely select the default map view; reversing axes moves properties to the wrong place. This is O(1) work/memory for one pin.
- `PropertyMapPicker`: retains the public interface but uses MapLibre LatLng. Keeps address, errors, retry counter and selection in memory. The existing bounded Geocoder request runs off the main thread, snapshots the query before launching, catches failure safely and preserves cancellation. Pan/tap remains available when search is absent. It shows required OpenMapTiles/OpenStreetMap credits below the map and keeps SDK attribution enabled. The explicit retry creates a fresh native map without changing the selected pin or calling Makao. Without the attribution display the provider/data credits would be missing; without retry a load failure has no in-screen recovery. No coordinates/raw exceptions appear in messages.
- `NativePropertyMap`: initializes MapLibre and hosts its MapView through AndroidView. Texture mode supports the scrolling Compose surface. SDK Java logging is disabled to avoid URL/camera/private-marker logging. View hierarchy saving is disabled and onCreate receives null; no camera/marker is deliberately persisted to a Bundle. Local updated-state callbacks avoid stale permission/selection handlers during recomposition. The map starts at the stored pin or a Nairobi view, but never calls onPin for that default. Its supported rectangle matches existing backend validation, not a claim that all points in the rectangle are geographically inside Kenya.
- Lifecycle effect: registers lifecycle/memory listeners, balances start/resume/pause/stop with guards, and forwards low-memory events. Map/style callbacks check the `alive` flag. On disposal it first blocks late callbacks, removes listeners, stops/destroys native rendering and clears references. Without this cleanup an exited screen could leak native resources or late private state updates. Native lifecycle still requires device validation.
- Map click callback: requires current enabled state, a loaded local source and a valid point before copying the coordinates into parent state. It does not save, create an entitlement or authenticate the user.
- Style callback: adds a local GeoJsonSource and CircleLayer for the brown/white selection marker. GeoJSON stays in the renderer, not a URL. The LaunchedEffect refreshes the marker/camera when the selected pin changes; this lets address search move the map. The SDK attribution control stays enabled. Map failure presents a fixed message rather than logging/displaying its raw error.
- AndroidView update: follows current enabled state and stops the surrounding scroll view intercepting active map gestures. Touch-up/cancel releases that request. A content description identifies the picker. No GPS tracking, automatic backend writes or app-owned HTTP interceptor is introduced.
- Credit action: the SDK's default attribution menu includes a feedback link that appends camera coordinates to an external URL. A custom AttributionDialogManager overrides only its view-click callback and opens a Compose credit dialog instead. Fixed HTTPS provider/licence links are offered; none includes a camera point, account/token or private marker. Closing/stopping the page dismisses credits. Removing this override reintroduces coordinate-bearing feedback links. Required credit text and the attribution control remain visible.

### /home/hp/Desktop/makao-hub-frontend/app/src/main/java/com/makaohub/android/ui/LandlordScreens.kt

`PrivateLocation` now constructs MapLibre LatLng from the existing stored BigDecimal coordinates. Save no longer depends on a Google-key flag, but still requires a selected pin and no active owner request. The public area, saveMapLocation call, current role/session checks and scoped FLAG_SECURE remain. Removing the map callback would prevent selection; replacing saveMapLocation with client-only success would disconnect actual backend persistence.

### /home/hp/Desktop/makao-hub-frontend/app/src/test/java/com/makaohub/android/PropertyMapPinTest.kt

Three tests cover no default marker, correct coordinate order/no extra feature data, and rejection of unsupported/non-finite positions. MapLibre rejects NaN/infinity at LatLng construction; application bounds still reject globally valid points outside the supported rectangle. These tests need no native map/network. Existing authenticated location/role-denial contract tests still cover the save path. Removing these checks would lose coverage of the provider-switch coordinate boundary.

### Documentation

README.md and IMPLEMENTATION_NOTES.md point to this current implementation. LANDLORD_MAPS_VERIFICATION.md is retained as history but its Google-key instructions are labelled superseded. This file explains the changed methods, security flow and verification limits. design-qa.md records that no live native screenshot is available; build success does not prove visual fidelity.

## Security-critical sequence

1. Existing profile/session establishes LANDLORD; map selection itself grants no role or entitlement.
2. Map SDK loads a fixed public HTTPS style/tiles using its own client, not Makao's authenticated Retrofit client. No Makao Authorization header, ID evidence or payment data is added. Tile requests reveal viewed geographic areas and the user's network address to the provider/CDN; do not describe them as anonymous.
3. Private marker data is added locally, without a remote GeoJSON URL, saved-state Bundle, analytics or app log. Map styles and ordinary tile caches can still reveal areas viewed; this is not a guarantee that the provider cannot infer location from tile requests.
4. Explicit Save uses the unchanged ViewModel conversion to BigDecimal and authenticated owner/listing PUT body. The backend remains responsible for role and ownership checks. Selection is never proof of entitlement or ownership.
5. On leaving, native callbacks/resources are released; the existing owner workspace clears private session state on logout. Screenshots remain blocked on the private location page.

## Verification and remaining risks

Final verification: all 33 Android tests passed (including the three new marker checks), and the final OpenGL debug APK built successfully. The merged manifest was inspected: no Google map API-key metadata, coarse/fine GPS permissions or Vulkan hardware requirement remains. Active source contains no Google map imports, maps-compose dependency or map-key flag. The temporary build-session reset was recovered; the final successful command was `./gradlew --offline -Pkotlin.compiler.execution.strategy=in-process testDebugUnitTest assembleDebug --console=plain`. APK: `/home/hp/Desktop/makao-hub-frontend/app/build/outputs/apk/debug/app-debug.apk`.

No database migrations or customer data edits are required. Host tests/build are run with synthetic local mock servers only. No actual email, property edit, payment or private-location provider request is used by tests. Public dependency/style verification requests contain no customer data.

Real map rendering, style/marker visibility, attribution controls, scrolling gestures, address search and background/resume handling need a connected phone/emulator. Service/network outages must remain honest failures. MapLibre SDK tile caching and third-party/CDN data processing need privacy review before production; no production security/custody completion is claimed. The app does not enable bulk/offline tile downloads. Production retention/consent and international data transfer obligations need qualified Kenyan privacy review; provider terms/attribution should be reviewed by the owner. No new Safaricom/payment decision is introduced.

RECOMMENDED next test: sign in as a landlord, open an owned draft, select a test point, explicitly save and reopen it. Check that the initial camera is not silently saved, approximate renter results remain unchanged, another owner cannot access the location, and Back/background/resume/retry are safe. Continue with real-device checks before accepting visual fidelity.

## Official references

- https://maplibre.org/maplibre-native/android/examples/getting-started/
- https://repo.maven.apache.org/maven2/org/maplibre/gl/android-sdk-opengl/13.6.1/android-sdk-opengl-13.6.1.pom
- https://maplibre.org/maplibre-native/android/examples/data/rendering-engine/
- https://github.com/maplibre/maplibre-native/tree/android-v13.6.1/platform/android/MapLibreAndroid/src/main/java/org/maplibre/android
- https://openfreemap.org/quick_start/
- https://openfreemap.org/tos/
- https://openfreemap.org/privacy/
