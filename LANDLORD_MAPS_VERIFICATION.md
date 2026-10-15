# Landlord Google Maps and verification screens — 14 October 2026

**Historical Google map implementation:** the current picker has been replaced with MapLibre Native + OpenFreeMap. Do not follow the Google map key/billing steps below for the current build. See `/home/hp/Desktop/makao-hub-frontend/MAPS_IMPLEMENTATION.md`. The verification work and protected backend location contract described here remain unchanged.

## Outcome and scope

The owner requested a Google Map instead of latitude/longitude form inputs, and the missing landlord ID/selfie/ownership verification screens. This change reuses the existing Kotlin/Compose, Retrofit, JWT and Supabase signed-upload flows (Ponytail: no new auth/navigation/provider framework).

Unverified landlords open verification after successful account OTP/profile loading. Four evidence cards cover ID_FRONT, ID_BACK, SELFIE and OWNERSHIP_PROOF. Stored-file confirmation means READY for review, not VERIFIED. Submission receives PENDING from the existing backend; only a server review decision produces VERIFIED. Home, profile and publish-check screens also link to verification. Verified owners keep the dashboard route. Private drafts remain possible; publication and payouts remain backend-gated.

Location entry now offers Google Maps pan/zoom/tap plus optional Android Geocoder address search. The coordinates still exist behind the interface and are sent in the existing authenticated PUT body. They are not printed in the UI or put into app-owned request URLs. Public neighborhood/city remains a separate field. A default Nairobi camera view is only a starting view, never silently saved as the property pin.

No email-encryption changes, account data changes, database migrations or payment-rule changes were made. Email encryption still awaits the separate key-storage decision. Google Maps is not configured on this computer yet: source compiles without a key and displays an honest setup message instead of creating a fictitious map or default pin.

## Create and configure the Maps key

1. Open [Google Cloud Console](https://console.cloud.google.com/) and create/select your own project, e.g. Makao Hub Testing.
2. Follow Google's billing prerequisites. Do not treat this as unlimited free use; review your account's terms/usage controls before enabling billing. No billing account/project/key was created by Codex.
3. In APIs & Services -> Library, enable Maps SDK for Android.
4. In APIs & Services -> Credentials, Create credentials -> API key. Restrict it immediately:
   - Application restriction: Android apps.
   - Package: com.makaohub.android.
   - Debug SHA-1 on this computer: FB:38:B1:83:EE:F7:8E:95:1E:A5:47:D2:DC:0B:E4:DA:33:0A:B6:60.
   - API restriction: Maps SDK for Android only.
5. Put MAKAO_MAPS_API_KEY=your-actual-key in /home/hp/.gradle/gradle.properties (private user-level settings, not this project's tracked gradle.properties). Alternatively expose MAKAO_MAPS_API_KEY as a private environment variable to the Gradle process. Never paste it into chat or source. Android keys are embedded in the APK by design, so application/API restrictions—not hiding alone—are essential.
6. Sync/rebuild/run in Android Studio. No key goes in backend.env and no Gmail/OTP key should be reused.

The fingerprint was obtained from the debug signingReport, not guessed. Other development machines, release signing and Play App Signing need their own correct certificate fingerprints/restrictions. To check it again:

```bash
cd /home/hp/Desktop/makao-hub-frontend
./gradlew :app:signingReport
```

Official sources: [Maps setup](https://developers.google.com/maps/documentation/android-sdk/start), [key security guidance](https://developers.google.com/maps/api-security-best-practices), [Maps Compose](https://developers.google.com/maps/documentation/android-sdk/maps-compose).

Maps Compose is pinned to 6.12.2, compatible with the existing Kotlin 2.2/Compose setup. The newer documentation example uses a version requiring a newer Kotlin toolchain; the app's toolchain was not upgraded just for this feature. The [published dependency](https://repo.maven.apache.org/maven2/com/google/maps/android/maps-compose/6.12.2/maps-compose-6.12.2.pom) includes the Maps SDK through maps-ktx. No separate Places SDK, Google Geocoding HTTP key, location permission or GPS tracking was added. Android Geocoder availability varies by device; pan/tap works when address search is unavailable. Search results are suggestions, not ownership proof or guaranteed entrance coordinates.

## Backend additions

### /home/hp/Desktop/makao-hub-backend/src/main/java/com/makaohub/backend/verification/api/LandlordVerificationDocumentResponse.java

Record fields are documentId, documentType and status only. This is safe readiness metadata for the authenticated owner; it contains no document image/text, filename, objectPath or signed read URL. Removing it forces either no resumable progress or accidental entity exposure.

### /home/hp/Desktop/makao-hub-backend/src/main/java/com/makaohub/backend/verification/repository/LandlordVerificationDocumentRepository.java

findAllByLandlordIdAndDocumentTypeInAndStatusIn filters by authenticated landlord, the four required types and UPLOADING/READY status. JPA binds values, rather than concatenating SQL. This fixed evidence set does not need unbounded feed pagination. Without the landlord predicate another account's metadata could leak.

### /home/hp/Desktop/makao-hub-backend/src/main/java/com/makaohub/backend/verification/service/LandlordVerificationService.java

getMyDocuments is read-only, reuses requireActiveLandlord (existing ACTIVE account and database LANDLORD role checks), queries only the caller's evidence and maps it to the restricted DTO. Existing upload, confirm and submit logic is unchanged. Removing the service guard would weaken internal-call authorization even though the HTTP namespace is also role-protected.

### /home/hp/Desktop/makao-hub-backend/src/main/java/com/makaohub/backend/verification/api/LandlordVerificationController.java

GET /api/v1/landlord/verification/documents calls getMyDocuments using the validated UUID in the authenticated JWT subject. No landlordId is accepted from request data. Existing namespace security requires LANDLORD. Removing this route means Android cannot recover accurate saved progress after reopening.

## Android changes

### /home/hp/Desktop/makao-hub-frontend/app/build.gradle.kts and app/src/main/AndroidManifest.xml

The build reads a private MAKAO_MAPS_API_KEY Gradle property or environment variable, validates its character set, exposes only MAPS_CONFIGURED as a BuildConfig Boolean and injects the key into com.google.android.geo.API_KEY manifest metadata. Maps Compose 6.12.2 is added. The camera is optional; the selfie capture delegates to a camera app without requesting broad camera/storage permissions. The existing network policy, no-backup setting and API base URL remain. Removing metadata/dependency prevents Maps authentication/compilation. Test-only kotlinx-coroutines-test 1.10.2 makes ViewModel flow checks runnable without a phone.

### /home/hp/Desktop/makao-hub-frontend/app/src/main/java/com/makaohub/android/data/MakaoApi.kt

Adds GET verification/status, GET own-document metadata, POST document upload-url, POST document/{id}/confirm (204), and POST verification/submit contracts. Paths/JSON fields match the Spring controller. Without them the UI would be decorative, not connected.

### /home/hp/Desktop/makao-hub-frontend/app/src/main/java/com/makaohub/android/data/LandlordRepository.kt

OwnerVerification, OwnerVerificationDocument and VerificationUploadTicket are ordinary classes without generated toString methods, so private review details/upload URLs are not casually logged. OwnerEvidence limits UI uploads to four supported types.

verification/verificationDocuments/verificationTicket/confirmVerification/submitVerification all reuse auth.authorized("LANDLORD"), including the existing serialized refresh handling. confirmVerification checks the actual HTTP success before reporting completion. transferVerification uses the separate storage client and no Makao JWT. signedVerificationRequest and signedMediaRequest share signedStorageRequest, which requires HTTPS, the Supabase host suffix, no embedded credentials and the signed-upload path. Redirects and hidden transfer retries remain disabled.

validOwnerPin rejects NaN/infinity and enforces the existing backend's geographic rectangle; it does not claim that a bounding rectangle proves a point is geographically inside Kenya. verificationReady requires every evidence type to be READY, not just four arbitrary records. Removing those checks permits bogus client completion states, while the backend remains authoritative.

### /home/hp/Desktop/makao-hub-frontend/app/src/main/java/com/makaohub/android/data/VerificationImages.kt

validVerificationImageDimensions safely multiplies as Long and rejects zero/negative/over-25-megapixel inputs. fromUri reads only supported image types, bounds encoded bytes to 10 MB, validates image dimensions before decoding, downsamples to keep the long edge at most about 2400 pixels, corrects JPEG EXIF orientation, re-encodes and releases native bitmaps. fromBitmap JPEG-encodes the selfie/image with output bounds. The new JPEG does not copy GPS/EXIF metadata. VerificationImageRejected has a fixed non-sensitive message. Everything is prepared in memory/IO work, not saved to an app file or log.

Removing bounds risks memory exhaustion/decompression abuse; removing re-encoding can copy unnecessary metadata into sensitive uploads. RECOMMENDED device check: verify the prepared ID text remains legible. These frontend checks are defense-in-depth, not a substitute for independent server magic-byte checks, malware scanning or application-layer document encryption.

### /home/hp/Desktop/makao-hub-frontend/app/src/main/java/com/makaohub/android/LandlordViewModel.kt

- OwnerScreen adds VERIFICATION.
- start fetches actual verification; unverified owners load evidence and enter verification, while VERIFIED owners refresh the existing dashboard.
- verificationStatus uses the latest server response, with the authenticated profile only as fallback. refresh now refreshes verification too; refreshWorkspace holds the existing dashboard/list/application calls. go loads verification when selected.
- loadVerification reloads status/metadata; it does not invent progress.
- uploadEvidence reads/prepares the selected URI off the main thread; uploadSelfie prepares the camera bitmap and releases it.
- beginEvidence checks state, permits one pending upload, obtains an owned signed ticket and verifies matching type/JPEG content type before retaining bounded bytes in memory.
- finishEvidenceUpload uploads once, confirms with the backend, then clears pending bytes/ticket and reports readiness—not approval.
- retryEvidence is explicitly user-triggered; it renews an expired transfer URL if needed and requires unchanged document ID/type/format. It does not silently duplicate a network write.
- confirmEvidence accepts an uncertain prior 409 only if a fresh own-document list independently shows that exact document READY. Other conflicts propagate. confirmSavedEvidence lets a reopened screen check a stored upload without pretending bytes were transferred.
- submitVerification requires all four READY records then adopts the backend response, normally PENDING.
- saveMapLocation validates a finite supported point, converts coordinates to BigDecimal strings and calls existing saveLocation, preserving the owner/listing route/body.
- run keeps the existing loading/cancellation/session checks and now reports safe image/document-transfer errors. showNotice supports camera-app absence.
- clearPrivateState clears verification status, records, tickets and pending bytes alongside existing private owner state on logout/disposal; no new persistent cache was introduced.

Removing any upload/confirmation distinction risks claiming a missing file is ready; removing state clearing or server role gates risks stale private data crossing sessions.

### /home/hp/Desktop/makao-hub-frontend/app/src/main/java/com/makaohub/android/ui/LandlordScreens.kt

LandlordWorkspace invokes start, routes VERIFICATION and hides ordinary bottom tabs during evidence collection. A scoped FLAG_SECURE is applied on verification/location pages to prevent screenshots/non-secure displays, then removed on leaving them. Existing lifecycle cleanup remains.

OwnerHome adds the verification action/banner. OwnerProfile and PublicationChecks link to the same screen. ListingPreview/PublicationChecks/OwnerProfile use refreshed server verification status. PrivateLocation replaces latitude/longitude text boxes with PropertyMapPicker and an explicit Save private pin action, while retaining the public area field. Removing these links again hides the required flow; removing the refresh can show stale badges. No client approval/unlock entitlement is introduced.

### /home/hp/Desktop/makao-hub-frontend/app/src/main/java/com/makaohub/android/ui/LandlordVerificationScreen.kt

The screen chooses four evidence types via OpenDocument and supports a delegated selfie camera intent. It displays only actual metadata/status and offers manual confirmation retry, server review submission and status refresh. Controls distinguish NOT_STARTED, PENDING, VERIFIED and REJECTED; confirmed files are not offered a fake replacement endpoint. Missing camera app is handled safely. Labels explicitly distinguish a selfie photo from a trusted live face check. No sensitive image preview, saved-state URI, national-ID parser or automatic approval is added. Removing it hides the flow again.

### /home/hp/Desktop/makao-hub-frontend/app/src/main/java/com/makaohub/android/ui/PropertyMapPicker.kt

PropertyMapPicker explains missing configuration without rendering a broken map. With a key it keeps address text/pin/camera in memory, searches through native Geocoder on IO (bounded query, one result, safe failure/cancellation handling), then lets the owner pan/zoom/tap a valid point. Only an explicit save sends it to Makao. Toolbar links to external Maps apps are disabled. Google and the device geocoder receive map/search requests, but no Makao JWT or identity/payment data is sent to them. Removing onMapClick prevents exact selection; removing explicit selection could save the initial camera as a false location.

## Security-critical flow, in order

1. Successful Makao OTP establishes the session; /profile/me establishes active LANDLORD role.
2. Each verification API call uses the existing JWT/refresh path; the backend separately checks role, account status and authenticated ownership.
3. The user chooses an evidence type/file; bounded native image preparation validates shape/type and strips metadata without storing another copy on disk.
4. The backend creates a signed upload capability for that owner/type. The app sends bytes only to the checked HTTPS Supabase destination; Makao bearer tokens are never attached.
5. Upload HTTP success is not verification readiness. POST /confirm separately inspects the stored object using the existing size/content-type checks.
6. UI only shows READY when the owned metadata endpoint confirms it. A retry cannot substitute a different document or bypass a conflict.
7. The server validates all four required evidence types before moving the role to PENDING; Android never writes VERIFIED.
8. Publishing and payouts remain backend-controlled even if UI code is tampered with.
9. Map selection remains private owner state; only the selected point is posted in an authenticated owner/listing request. Public renter endpoints retain their existing approximate-area/paid-entitlement protections.

## Tests and remaining verification

New backend service/API tests cover metadata-only responses, authenticated subject rather than a supplied owner ID, anonymous/wrong-role denial and inactive account denial. The real local PostgreSQL integration test inserts separate synthetic owners and proves queries cannot return the other owner's documents. No live Supabase/email/payment call is involved.

Android tests cover exact Retrofit routes/body fields, JWT separation, all-four-type readiness, malicious signed URLs, finite geographic bounds, safe image dimension arithmetic, first-entry routing, pending rather than local approval, refusal of incomplete submission and logout cleanup. Existing auth/splash/listing/payment/chat tests remain.

Completed backend verification: all 124 tests passed, including startup/JPA repository parsing, all 53 unchanged migrations on a clean local database and the owner-isolation integration check. The temporary database server was stopped. All 30 Android tests passed on the final rebuild, and the debug APK was assembled at /home/hp/Desktop/makao-hub-frontend/app/build/outputs/apk/debug/app-debug.apk. git diff --check passed for the backend. No device is attached, so real map rendering, camera capture, native image legibility and live signed-upload delivery are not proven by these host tests.

To verify with a phone: restart the backend with existing private settings and mock payment profile, rebuild Android with the restricted Maps key, sign in as an unverified LANDLORD, upload four readable test images, confirm READY, submit and see PENDING. Verify that renters/sublessors cannot access this workspace and publication remains blocked. After a legitimate backend review, refresh and see VERIFIED. Create a private listing draft, select a map pin, save and reopen it; another owner must not read it and renter discovery must still show only an approximate area.

## Known limits / provider and legal review

The backend's DTO mentions PDFs, but its upload service currently supports images only; the UI truthfully offers JPEG/PNG/WebP photos. Existing confirmed evidence cannot be replaced through these endpoints; rejected owners are directed to support rather than a fake correction flow. An older in-progress upload with a non-JPEG format is not silently overwritten. The API/key, actual bucket policies, independent malware/magic-byte checks, envelope encryption/KMS, live face-match provider, review permissions/audit and transport configuration still need production review. Staff approval itself was not implemented or bypassed here.

Google billing/key restrictions/terms and map/geocoding data processing need provider review. Identity-document retention/consent and relevant Kenyan privacy obligations need qualified legal review before production. No new pricing, custody, escrow or payment provider decision was made. RECOMMENDED next task: configure the restricted Android key, then run one controlled landlord verification/map flow on the phone with test documents.
