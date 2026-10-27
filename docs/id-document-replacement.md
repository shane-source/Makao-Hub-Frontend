# ID front/back upload and replacement

## Decided behavior

- Renter, Landlord and Sublessor use the same front-ID screen followed by the same back-ID screen.
- IDs accept JPEG or PNG input up to 10 MiB. Android normalizes accepted input to JPEG before upload. PDF remains restricted to a Landlord ownership proof or a Sublessor rental agreement; it is not accepted as an ID.
- `Continue` is enabled only after the backend reports that the current side is `READY` and `securityChecked`.
- `Replace file` always asks the backend for a new immutable upload ticket with `replaceExisting=true`.
- `Retry current file` retries confirmation of the already-selected object. It never silently reuses an old file when the user chose a replacement.
- Verification remains editable only in `NOT_STARTED` or `REJECTED`. `PENDING` and `VERIFIED` evidence cannot be replaced.
- No schema or Flyway migration is required. Existing replacement endpoints and document history are reused.

## State and failure model

1. The ViewModel checks that the role verification is editable. This prevents a stale client screen from starting a replacement after submission or approval.
2. It requests a role-owned upload ticket with explicit replacement. The backend performs the role, ownership and verification-state checks and supersedes only the active document of the same type.
3. Until that ticket succeeds, the previous pending byte buffer and retry state remain intact. A temporary ticket failure therefore does not destroy the only retryable upload.
4. Once the new ticket is accepted, the old in-memory bytes are zeroed, the selected side's stale readiness is removed, and the bounded display name is replaced.
5. The bytes are written to the signed Supabase URL without the Makao JWT. Byte progress is display-only; reaching 100% is not treated as verification success.
6. The backend confirmation endpoint performs the existing type, size, magic-byte, malware, normalization and Kenyan-ID OCR checks. The client enables `Continue` only after refreshed server metadata is `READY` and `securityChecked`.
7. If confirmation fails, the ticket and bytes remain available for `Retry current file`; the user may instead choose `Replace file` and create a fresh immutable object.
8. On success, logout, workspace exit or ViewModel clearing, private byte arrays are overwritten before references and file names are discarded.

## Changed frontend files

### `app/src/main/java/com/makaohub/android/data/EvidenceUploadBody.kt`

- `EvidenceUploadBody` is the evidence-only OkHttp `RequestBody`. It reports actual bytes written in fixed 8 KiB chunks while preserving the supplied MIME type and length. Removing it would remove determinate upload progress and tempt the UI to display invented percentages. It does not decide success or approval.
- `writeTo` rejects an empty buffer, writes each chunk directly to Okio, and reports monotonically increasing percentages. The buffer remains owned by the ViewModel so it can be zeroed after success or exit.

### `app/src/main/java/com/makaohub/android/data/VerificationImages.kt`

- `evidenceDisplayName` reads optional Android document-provider metadata. It removes control characters and caps the name at 120 characters. The name is display-only: it is not logged, persisted, or used to build a storage path. Removing the bound would allow hostile providers to inject oversized or control-character UI text.
- Existing image decoding, size/dimension checks, JPEG normalization and metadata stripping remain the authority for client-side preparation. Backend validation remains mandatory.

### `app/src/main/java/com/makaohub/android/LandlordViewModel.kt`

- `evidenceProgress` and `pendingEvidenceType` expose only transfer state needed by the screen.
- `uploadEvidence`/`uploadCameraEvidence` prepare private bytes and a bounded session-only name, then call `beginEvidence`.
- `beginEvidence` implements the replacement sequence described above. It explicitly requests replacement, preserves an earlier retry when ticket creation fails, removes stale readiness only after a valid ticket arrives, and creates ID thumbnails nowhere. Removing that ordering recreates the reported trap or can falsely display rejected evidence as ready.
- `finishEvidenceUpload` writes through `EvidenceUploadBody`, then confirms and refreshes server metadata. It clears private state only after success so a failed confirmation can be retried.
- `confirmSavedEvidence` clears matching local retry state after successful server confirmation.
- `clearPrivateState` overwrites buffers and clears names, progress and previews to reduce in-memory exposure.

### `app/src/main/java/com/makaohub/android/SublessorViewModel.kt`

- The evidence state, selection, replacement, transfer, confirmation and clearing methods mirror the Landlord sequence, but call only Sublessor verification routes and retain the separate PDF rental-agreement policy.
- `confirmStoredEvidence` now clears a matching local pending buffer after server confirmation. Without it, a confirmed document could still appear stuck as pending.

### `app/src/main/java/com/makaohub/android/RenterViewModel.kt`

- The evidence state and methods mirror the same sequence for exactly `ID_FRONT`, `ID_BACK` and `SELFIE`.
- `clear` now also resets the transfer flag, progress, stage and display names while overwriting pending bytes. Removing this reset can leak stale state into a later account session.

### `app/src/main/java/com/makaohub/android/ui/IdDocumentStep.kt`

- `IdDocumentStep` is the shared Compose implementation of the provided front/back layouts. It shows server-backed readiness, real upload progress, a replacement action, a distinct retry action, accurate JPEG/PNG limits and the private-evidence warning.
- `IdCard` and `IdNote` provide the repeated white-card and neutral/warning surfaces using the existing Makao design tokens.
- `IdSourceDialog` offers only camera, gallery and cancel. Choosing either source starts replacement; it cannot be confused with retry.
- The screen never displays private ID pixels after selection. This reduces shoulder-surfing and screenshot exposure while retaining the requested capture flow.

### `app/src/main/java/com/makaohub/android/ui/LandlordVerificationScreen.kt`

- Step 2 is split into front and back pages using `IdDocumentStep`. Back navigation moves from back to front before leaving verification. Re-submission always starts at the front.
- Camera/gallery actions address only the current side. Existing live-face, PDF ownership proof, review and terminal outcomes remain unchanged.

### `app/src/main/java/com/makaohub/android/ui/SublessorScreens.kt`

- `SublessorVerification` now uses the same front/back pages and replacement controls. Existing selfie, PDF rental agreement, review and status pages remain role-specific.

### `app/src/main/java/com/makaohub/android/ui/RenterScreens.kt`

- The verification workspace now owns the full page while active, avoiding duplicated headers and bottom navigation around sensitive capture screens.
- `RenterVerification` uses front, back and selfie as three sequential steps. Existing pending, approved and rejected outcomes remain server-driven.

## Changed tests

- `EvidenceUploadBodyTest` proves exact byte transfer, MIME/length preservation, monotonic real progress and empty-buffer rejection.
- `LandlordVerificationFlowTest` proves a wrong ID can be replaced, a ticket failure preserves the earlier retry, an accepted replacement wipes the older buffer, and clearing wipes the replacement.
- Existing `SublessorContractTest` and `RenterContractTest` cover reselection and byte wiping for their role flows.
- Backend `LandlordEvidencePolicyTest`, `SublessorEvidencePolicyTest` and `RenterEvidencePolicyTest` now prove both ID sides can be explicitly superseded with new upload records. Their existing tests still prove wrong-role and foreign-document denial, edit-state locking, OCR failure behavior and fail-closed scanning.

## Dependencies and complexity

- No dependency was added. The implementation uses the project's existing Kotlin/Compose, OkHttp 4.12, Retrofit and Okio stack.
- Upload time is O(n) in document bytes with a fixed 8 KiB write chunk. Screen readiness checks scan at most the role's small fixed evidence set, so their practical cost is constant.
