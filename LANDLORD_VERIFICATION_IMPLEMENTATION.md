# Landlord verification — Android implementation notes

Updated 14 October 2026. This implements the supplied landlord-verification layouts as native Jetpack Compose screens and connects them to the existing Spring backend. Ownership proof is PDF-only by the owner's explicit instruction, even though one reference frame says “PDF, JPG”.

This is a secure evidence-submission flow, not automatic identity verification. A photo capture does not prove liveness or face match, a clean PDF does not prove ownership, and `READY` does not mean the landlord is approved. Only the server's role-verification status controls the pending/approved/rejected screens.

## User journey

1. An unverified signed-in landlord enters **Upload National ID**, labelled **Step 2 of 4** to match the owner's references. The explanation screen/step 1 has been removed.
2. They provide separate front and back ID images from the camera or system document picker.
3. They capture a current selfie with the front camera. The UI makes no automatic face-alignment/liveness claim.
4. They choose one ownership PDF. The Android client accepts only `application/pdf`, `%PDF-` header bytes, and at most 10 MiB before upload.
5. A summary enables submission only when the backend reports all four records as `READY` and `securityChecked`; ownership must also report `application/pdf`.
6. Submission moves the server state to `PENDING`. Pending, `VERIFIED`, and `REJECTED` screens come from server data. Returning to a terminal screen reloads it; **Close & Back to Main** opens the restricted pending dashboard.
7. A rejected landlord can return to the evidence steps and explicitly replace files. A verified landlord can continue to listing creation.

The supplied pending screen's “within 24 hours”, M-Pesa match, and face-match statements are absent because the backend does not establish them. The supplied approved screen's “3x inquiries” claim is absent because no source supports it. The app never invents a rejection reason; it displays only the backend's saved reason.

## Network sequence

```
Android -> Makao API: request owner-scoped upload ticket
Makao API -> Android: document ID + MIME + short-lived Supabase URL
Android -> Supabase: PUT prepared bytes (no Makao JWT)
Android -> Makao API: confirm document ID (Makao JWT)
Makao API -> private storage -> ClamAV/parser -> database READY
Android -> Makao API: reload metadata
Android -> Makao API: submit only after four current READY records
```

The client pre-checks files for usability and memory bounds. Server scanning/parsing remains authoritative because Android can be modified by an attacker.

## Files, classes, and methods

### `app/build.gradle.kts`

Adds CameraX `camera-camera2`, `camera-lifecycle`, and `camera-view` version `1.5.3`. CameraX supplies the native preview and in-memory capture. Without these dependencies the app cannot capture ID/selfie images. No cloud biometric SDK was added.

### `app/src/main/AndroidManifest.xml`

Adds `android.permission.CAMERA` and keeps the camera feature optional so gallery/PDF selection can still work on devices without a camera. Existing release cleartext denial remains unchanged. No location permission was added by this work.

### `ui/LandlordScreens.kt`

`LandlordWorkspace` renders `LandlordVerificationScreen(model, profile)` before the ordinary dashboard scaffold when `OwnerScreen.VERIFICATION` is active. It also renders `LandlordPendingDashboard` for the home route while the landlord role is `PENDING`. The existing secure-window flag remains active before these branches, preventing recent-app screenshots/screen capture where Android honors `FLAG_SECURE`. Removing the verification branch would make the flow unreachable; moving it outside the secure-window setup would expose sensitive previews. The ordinary verified dashboard is preserved.

### `data/MakaoApi.kt` — `MyProfile`

Adds optional `phoneNumber`, which already exists in the backend's `MyProfileResponse`. The summary displays this authenticated owner's phone number as **PHONE NUMBER**, without claiming phone verification. Older/missing-phone responses fall back to the account email. No contact number is fetched for another user. Removing the field forces the summary to use the email fallback.

### `data/LandlordRepository.kt`

Verification models:

- `OwnerVerification` mirrors the server's role state and review timestamps/reason.
- `OwnerVerificationDocument` mirrors metadata only and adds `contentType`, `sizeBytes`, and `securityChecked`. It contains no storage path, file bytes, or read URL.
- `VerificationUploadRequest` includes `replaceExisting`; replacement is explicit rather than silently overwriting evidence.
- `VerificationUploadTicket` contains the server document ID, fixed type/MIME, and short-lived signed upload URL.
- `OwnerEvidence` is the single type/MIME table: ID front/back/selfie upload as JPEG; ownership proof uploads as PDF.

Verification functions:

- `verificationReady` checks all four enum values. It is O(4n), with both sets bounded to four, so effectively constant time.
- `evidenceReady` requires matching type, server `READY`, `securityChecked`, and PDF MIME for ownership. Removing any condition would allow old/unconfirmed/wrong-format evidence to enable submission.
- `signedVerificationRequest` delegates to the shared storage URL guard. It requires HTTPS, a `.supabase.co` host, no embedded credentials, and the signed-upload route, and deliberately does not attach the Makao JWT.
- `verification` and `verificationDocuments` read only the authenticated landlord's server state through the shared JWT/refresh lock.
- `verificationTicket` sends only enum type, enum MIME, and explicit replacement; it sends no landlord ID or storage path.
- `transferVerification` performs the signed storage transfer with finite timeouts, no redirects, and no automatic retry after an uncertain write.
- `confirmVerification` calls the owner-scoped API and requires HTTP success.
- `submitVerification` asks the backend to enter review; it does not set approval locally.

The local URL check is defense in depth. Backend role, owner, path, and storage checks remain mandatory.

### `data/VerificationDocuments.kt`

- `VerificationPdfRejected` provides one fixed safe message.
- `VerificationDocuments.fromUri` accepts only a content-resolver MIME of `application/pdf` and opens the selected URI only for this operation.
- `readPdf` streams into memory in 8 KiB chunks, aborts above 10 MiB, checks a minimal `%PDF-` header, and returns unchanged bytes. It does not claim that the header makes the document safe; the backend performs malware and structural checks.

The selected URI is not persisted and no copy is written to app storage. Complexity is O(n) time and O(n) memory with a hard 10 MiB cap.

### `data/VerificationImages.kt`

- `validVerificationImageDimensions` uses `Long` multiplication to avoid integer overflow and caps decoded source dimensions at 25 megapixels.
- `fromUri` accepts JPEG/PNG/WebP from Android, reads no more than 10 MiB, inspects dimensions before full decode, subsamples long images, applies EXIF orientation, and passes pixels to `fromBitmap`.
- `fromBitmap` creates a JPEG byte array at quality 90. Because it does not copy EXIF, GPS and source metadata do not enter the uploaded image.
- `thumbnail` makes a maximum-256 KiB session preview and releases its bitmap.
- `VerificationImageRejected` exposes no filename, URI, decoder internals, or metadata.

Removing the bounds can cause memory exhaustion. Removing orientation makes ID text appear rotated. Removing re-encoding can leak GPS metadata. The backend independently revalidates and re-encodes because client work is not a trust boundary.

### `ui/EvidenceCamera.kt`

`EvidenceCamera` is a native CameraX composable:

1. It requests camera permission at the moment the user opens capture.
2. It binds only its own `Preview` and `ImageCapture` use cases to the current lifecycle; it does not unbind unrelated camera consumers.
3. The selfie uses the front lens when available; ID capture uses the back lens.
4. It displays a circular selfie guide or rectangular ID guide. These are framing aids, not biometric results.
5. `takePicture` captures to `ImageProxy` memory rather than a public/private file path.
6. The callback converts and rotates pixels, closes the proxy in all branches, checks that the composable is still alive, and returns the bitmap only to the ViewModel.
7. Disposal unbinds only owned use cases, stops the executor, and rejects late callbacks.

The host verification screen supplies the capture action through `captureRequest` and sets `showControls=false`, so there is only one bottom capture button, matching the reference. `lastCaptureRequest` initializes to the current request and only consumes subsequent changes: recreating a camera after navigation does not silently take another photo. `capturePhoto` checks the enabled state, permission, camera availability, and in-flight capture before calling CameraX. Denied permission cannot start capture. The ID source dialog offers the gallery; the selfie step uses live camera capture. These are photographic evidence, not biometric results.

### `LandlordViewModel.kt`

New/changed state:

- `evidenceStage` shows honest steps such as preparing, uploading, scanner confirmation, or checking the saved object; it never fabricates a percentage.
- `evidencePreviews` holds small byte arrays only for the current in-memory session.
- `evidenceNames` holds selected display names only in memory. The document-provider query runs off the main thread, failures fall back to generic labels, control characters are removed and labels are capped at 120 characters. Camera captures get descriptive names. Neither names nor document URIs are persisted or logged. `clearPrivateState` removes these names.
- `verificationTicket`, `verificationBytes`, `verificationTransferred`, and `verificationPendingId` separate upload capability, retained retry bytes, uncertain-transfer state, and confirmed server state.

Verification methods:

- `start` and `loadVerification` route any non-verified landlord to server-backed verification data. They do not infer approval from local files.
- `uploadEvidence` reads a chosen PDF/image on `Dispatchers.IO`, then delegates to `beginEvidence`.
- `uploadSelfie`/`uploadCameraEvidence` transfer bitmap ownership to the model, immediately release it if another operation is active, prepare it off the main thread, and recycle it in `finally`.
- `beginEvidence` prevents overlapping documents and frozen-state edits, requests explicit replacement when any active record exists, checks that server type/MIME equal the enum contract, creates only a bounded thumbnail, retains bytes for a user-requested retry, and begins transfer.
- `retryEvidence` renews an expired signed URL only when the first transfer was not completed. It verifies that the server returned the same document/type/MIME and never automatically replays an uncertain upload.
- `finishEvidenceUpload` uploads once, asks the backend to confirm/scan, then zeroes the retained full-size bytes and clears the ticket only after confirmation.
- `confirmSavedEvidence` resolves an uncertain stored transfer without uploading bytes again.
- `confirmEvidence` treats a `409` as a successful prior confirmation only after reloading an owned record with both `READY` and `securityChecked`.
- `submitVerification` refuses a request until all four server records satisfy `verificationReady`; successful upload alone is insufficient.
- `clearPrivateState` cancels work, zeroes retained full/preview byte arrays, clears all evidence/session models, and disconnects chat. `onCleared` invokes it.
- `refresh` reloads the pending owner's document metadata before updating the workspace; the dashboard therefore uses server readiness rather than client guesses.
- `newListing` redirects a `PENDING` owner to verification, including calls from other listing buttons. This implements the reference's locked Add Listing action. This is a UI gate; existing server authorization remains the trust boundary.
- Successful confirmation/submission no longer adds an extra success paragraph. Real failures appear in the native snackbar and can be retried through the bottom action or the incomplete file row.
- The common `run` wrapper still serializes UI operations, maps scanner outage to a fixed message, clears progress in `finally`, and expires the session only for authentication/workspace failures.

Full evidence bytes remain in memory only during the active upload/retry. Kotlin/Java garbage collection means zeroing is best-effort memory hygiene, not a formal secure-erasure guarantee.

### `ui/LandlordVerificationScreen.kt`

- `LandlordVerificationScreen` owns the remaining steps (2–4), summary, camera/gallery launchers, back behavior, fixed bottom actions, real loading state, and server-driven terminal outcomes. `rememberSaveable` preserves only step/correction navigation, never evidence bytes.
- Step 1 has been deleted. Back from step 2 returns to the workspace.
- Step 2 uses one dashed upload panel and a source dialog for front/back, camera/gallery and replacement; both ID sides remain mandatory.
- Step 3 shows the live camera or the current session preview; it does not download a previous private selfie just to render it.
- Step 4 allows only an ownership PDF, with three reference document-category cards and the dashed upload panel. The approved requirement is PDF-only, 10 MiB; the source's JPG/15 MB text is intentionally corrected. The reference's delete button is not faked: the current backend exposes replacement but no evidence-deletion operation, so tapping the file selects a replacement.
- The summary uses four reference-style cards: account contact, both ID filenames, live photo, ownership PDF. Edit/Retake return to the relevant step. Contact **View** opens the existing profile; no unsupported contact-update API is invented. Submission is enabled only when all four server evidence records pass `verificationReady`.
- The lifecycle observer refreshes only terminal states on resume. Editable steps do not refresh on picker return, because that would race the ViewModel's busy gate and drop the selected upload.
- `VerificationOutcome` shows pending, approved, or rejected from `OwnerVerification`, with the large circular status symbol, centered heading/body, bordered status card, and anchored reference action. It omits unsupported SLA and marketing claims. Rejection displays only the backend's saved reason.
- `hasEvidence` decides whether there is a real file row to render; `evidenceName` uses the current session's display name or an honest generic label, never the example filenames from the mockup.
- `EvidenceFile` shows metadata, current-session thumbnails and real progress stages. If a previous process left an `UPLOADING` record without a local ticket, tapping it calls `confirmSavedEvidence`; otherwise it offers replacement. Removing that branch would strand stored uploads after process restart.
- `ReviewItem` supplies the reference summary row and its actual Edit/Retake/View action.
- `PrivateEvidencePhoto` decodes only bounded local thumbnail bytes, crops within the requested shape and releases its bitmap when removed. It does not download private evidence for decoration.
- `VerificationTitle`, `VerificationCopy`, `VerificationCard`, `FaceTip`, `ProofFact`, `UploadArea`, and `VerificationAction` keep typography, cards, native icons, dashed border and bottom actions consistent with the 402dp-wide reference. The dashed border is a standard Compose stroke, not an image/icon approximation. Removing these helpers would duplicate presentation rules; none grants access.
- `LandlordPendingDashboard` supplies the requested pending home state and four native navigation tabs. It uses the actual profile name, current document metadata, disabled Add Listing, and only existing draft records. It does not invent the example draft photo or zero views when the API provides no thumbnail/view data.
- `PendingDocument` renders a document-status row. ID shows **Uploaded** only when both sides are current, security-confirmed records. Selfie and ownership stay **Under Review** until the server makes a role decision; no per-document biometric approval is inferred.

The palette follows the references: cream `#FAF7F4`, brown `#6A2E00`, dark ink `#21150E`, muted text `#706259`, and success green `#417653`. Native insets are used; the fake phone status/home bars from the images are not copied into the app.

### `data/AuthRepository.kt`

- `isEvidenceConfirmation` matches exactly one protected confirm route.
- `createMakaoApi` keeps ordinary requests at 15/30/40-second connect/read/call bounds and gives only the confirm route a 75/90-second read/call budget. This covers bounded storage download, scanner deadline, parsing, and image normalization without making OTP/payment calls wait or retry longer.
- Both clients retain no body logger, no redirects, no SSL redirects, and no automatic connection retries.

Removing the narrow matcher would either cause valid scans to time out on slow test hardware or unnecessarily lengthen security-sensitive auth/payment requests.

### Tests

- `VerificationPdfTest` covers unchanged bounded PDF bytes, fake/empty/oversized rejection, historical MIME/security blocking, and exact selection of the longer HTTP budget.
- `LandlordContractTest` covers the metadata/request shape, owner routes, wrong-role blocking, all-four readiness, PDF/security requirements, and signed URL/JWT separation.
- `LandlordVerificationFlowTest` covers unverified routing, server `PENDING`, missing-evidence blocking, private-state clearing, verified-dashboard routing, and the pending owner's inability to reach creation through another button.
- Existing splash, auth, map, and chat tests remain in the same full Android suite.

### `RentalChatSocketTest.kt` — test cleanup

The full regression run exposed an existing mock-server close-handshake omission. Its test listener now acknowledges `onClosing` using `webSocket.close(code, reason)`, allowing MockWebServer to release the upgraded socket before shutdown. Without the acknowledgement, cleanup can time out despite the message assertions succeeding. No production messaging behavior changed.

## Verification performed

- Android `testDebugUnitTest` passed.
- Debug APK `assembleDebug` passed.
- The full Android suite has 38 passing tests after this revision. The final `testDebugUnitTest assembleDebug` run succeeded.
- No real ID/selfie/PDF, Supabase object, email, or payment was used by these tests.
- No Android phone or emulator was connected, so camera permission, CameraX lifecycle, PDF picker behavior, layout fidelity, accessibility, and process-death handling still require device testing.

## Security and product risks still open

- `FLAG_SECURE` reduces casual screen capture; it does not stop a compromised device, another camera, accessibility abuse, or memory inspection.
- Client-side preparation and validation are usability controls, not authorization/security approval.
- No liveness/face-match/ID-authenticity provider exists yet. Keep the current truthful copy until a provider is selected and reviewed.
- The app intentionally does not persist evidence tickets/URIs/bytes. An OS process death during upload requires the user to reopen verification and check/reselect the file.
- A real signed-upload/confirmation run needs the private Supabase bucket and local clamd configured first.
- Exact visual acceptance is blocked until screenshots are captured from a connected phone and compared with all eight current references. Draft images/view counts and a document-delete operation are absent from the current API; the UI does not fabricate them.
- Document consent, privacy notice, KMS/encryption, retention, reviewer access/audit, and legal sufficiency need provider review and a Kenyan privacy lawyer.

Suggested next task: connect the development phone, install the rebuilt debug app, and use disposable non-sensitive test files to compare the eight reference states and verify camera, PDF picker, upload, fail-closed scan, replacement, pending, approved, and rejected flows. ClamAV installation/PONG was confirmed separately; a real backend confirmation is still required. Do not use a real national ID during this first integration run.
