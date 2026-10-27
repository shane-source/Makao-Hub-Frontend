# Sublessor workspace — implementation and reading guide

## Screen-set reconciliation — 22 October 2026

The twelve supplied Sublessor frames were checked against the current native workspace and its server contracts. The implemented journey covers the authenticated home, shared-listing details, media upload/confirmation/deletion, private address search and map pin, moderation preview, preference questionnaire/summary, opted-in renter discovery, public renter profile, application decisions, protected chat entry, test payment history, and listing publication states.

Two visual examples are intentionally not copied as product behavior. The example compatibility percentages remain absent because matching weights and rounding are an open decision; the app shows deterministic shared/different answers and says why no percentage is available. Discovery cannot create an unsolicited chat because the chat-unlock model is also open; protected conversation continues through the existing accepted-interest path. These limits prevent the sample board from silently becoming a payment or matching rule.

The backend supports the screen actions through role- and ownership-checked endpoints. V54/V55 store household/preferences and application rejection reasons without rewriting existing rows. V58 adds staff moderation state, submit/revise contracts, and a database publication guard. Exact coordinates remain in the private listing-location contract and never enter roommate discovery. Payment screens read the existing test ledger; they do not create a new payout destination or custody flow.

Current automated evidence: the final Android source compiled, **73 unit/contract tests passed**, and the debug APK assembled. The focused Sublessor backend suite passed **22 tests** covering authorization, application ownership/rejection, verified-role evidence, document privacy, and confirmed-photo publication. A clean PostgreSQL migration run remains environment-dependent (`MAKAO_ROLE_TEST_DB_URL`) and was not rerun against any live database. Native screenshot comparison remains blocked because this environment has no configured emulator and the owner chose Android Studio/device verification rather than an automated installation.

Date: 15 October 2026. This guide explains the changes for the supplied Sublessor board and the approved missing backend support. It is not a claim that the entire marketplace is production-ready.

## Pending dashboard shared with Landlord — 15 October 2026

Request: after a pending Sublessor closes the under-review confirmation and returns to Main, show the same pending dashboard as the Landlord. No new backend contract, migration, verification decision or account-data change is involved.

Changed files/methods and connections:

- `/home/hp/Desktop/makao-hub-frontend/app/src/main/java/com/makaohub/android/ui/LandlordVerificationScreen.kt`: `LandlordPendingDashboard` now supplies its existing role-specific data/actions to `PendingVerificationDashboard`. The shared composable is the existing cream/amber pending layout, not a redesigned screen. Its inputs are name, current ID readiness, proof label, bounded draft titles, busy state and five navigation callbacks. Removing this shared component breaks both pending views; copying it instead would let their layout drift. Existing `PendingDocument`, style tokens and locked Add Listing action are unchanged.
- `/home/hp/Desktop/makao-hub-frontend/app/src/main/java/com/makaohub/android/ui/SublessorScreens.kt`: `SublessorWorkspace` now selects the shared dashboard **only** for HOME plus server-provided PENDING. It passes Sublessor ID-front/back readiness, `Rental Agreement (PDF)`, its own DRAFT titles and its own Dashboard/Listings/Inbox/Profile/Verification destinations. It returns before the ordinary five-tab workspace scaffold, so two navigation bars cannot appear. Removing this branch restores the ordinary dashboard that prompted the request. The existing Close & Back to Main action continues to call `go(HOME)`.
- `/home/hp/Desktop/makao-hub-frontend/app/src/main/java/com/makaohub/android/SublessorViewModel.kt`: `go(HOME)` refreshes verification/documents first; when still PENDING it loads only owned listings, not applications/interests that the pending dashboard does not display. For other states it retains the existing workspace reload. Removing this distinction adds unrelated calls that can fail or delay the under-review screen. A freshly verified response leaves the pending view; no status is synthesized locally.
- `/home/hp/Desktop/makao-hub-frontend/app/src/test/java/com/makaohub/android/SublessorContractTest.kt`: `closingPendingVerificationLoadsOnlyTheOwnedDraftsAndKeepsCreationLocked` proves that Close-style HOME navigation retains PENDING, reads Sublessor-owned endpoints/draft titles without applications/interests requests, and that the ViewModel still prevents new listing creation. Removing the test loses the regression check; it is not a native screenshot test.

Security sequence: the UI checks the authenticated Sublessor's actual verification response; the document row requires both server-checked ID sides; draft titles come only from the owned listing endpoint; each navigation callback stays in the Sublessor workspace; Add Listing remains disabled and `newListing` independently rejects non-VERIFIED state. Backend role/ownership/publication guards are untouched. Uploaded ID is not an assertion of face matching or verified tenancy. No fake SLA, property photo or view count was added.

Reference: the existing `LandlordPendingDashboard` layout and previously supplied pending-dashboard frame. The original PNG is no longer present locally. The user chose to run from Android Studio and explicitly declined device installation/inspection, so no device action was taken. Rendered comparison remains blocked; shared source code ensures both roles use the same layout, but does not establish device text wrapping/accessibility acceptance.

What to test next: run from Android Studio, open Sublessor PENDING verification, tap Close & Back to Main, check name/badge/document rows/locked listing/drafts/four tabs, then check the Landlord counterpart for regressions. Tap the pending notice to return to verification. Check long names/large text and backend failure. No new provider/Safaricom/legal decision is introduced; existing document privacy/retention obligations remain. **RECOMMENDED next task:** perform this one-screen device comparison before more UI changes.

Final automated check for this dashboard change: **49 Android tests passed**, no failures/errors/skips, and the debug APK assembled. A first test session ended without returning completion; the subsequent complete run succeeded. This does not resolve the recorded visual/device acceptance gap.

## Fix: reselecting a failed verification upload — 15 October 2026

Cause: `SublessorVerification` disabled camera/gallery/PDF choices whenever `pendingEvidence` was present, and `SublessorViewModel.beginEvidence` also refused a new selection while a ticket was retained. Failed transfers/checks deliberately retain a pending file for retry, so this combination trapped the user on the same file.

Changed files and methods:

- `/home/hp/Desktop/makao-hub-frontend/app/src/main/java/com/makaohub/android/ui/SublessorScreens.kt`, `SublessorVerification`: camera, gallery and PDF actions now require only that no request is running (`!busy`). A pending failed file no longer disables them. The existing Retry action remains. Cancelling the picker leaves the previous file intact; no extra UI or dependency was added. Removing this change restores the disabled-button bug.
- `/home/hp/Desktop/makao-hub-frontend/app/src/main/java/com/makaohub/android/SublessorViewModel.kt`, `beginEvidence`: the module-internal prepared-file boundary now accepts a new validated selection during editable NOT_STARTED/REJECTED states. It asks the existing server for an explicit replacement ticket even if the client's metadata is stale. It no longer treats a retained ticket as a reason to block all new selections. Both gallery and camera, including selfie/agreement paths, use this method; it is not a new HTTP endpoint.
- `/home/hp/Desktop/makao-hub-frontend/app/src/test/java/com/makaohub/android/SublessorContractTest.kt`, `failedEvidenceCanBeReplacedWithoutLosingTheRetryOnTicketFailure`: regression test exercises the common prepared-file replacement path using synthetic PDF bytes (no Android camera or actual document), failed ticket requests and rejected unsafe storage URLs. It proves a later selection replaces the pending ID, stale readiness is removed, failed ticket issuance retains the previous file, and superseded/rejected/cleared arrays are zeroed. `pendingVerificationCannotStartAReplacement` also proves a pending review makes no upload request and wipes incoming ID bytes. These tests do not simulate native camera/gallery interaction.

Security-critical order in `beginEvidence`:

1. Check editable local status; wipe rejected incoming bytes. Server independently enforces ownership/role/editability, so a stale client approval cannot permit replacing pending/approved evidence.
2. Request `replaceExisting=true` through the existing authenticated Sublessor endpoint. This explicit user selection creates a fresh immutable upload path, not an overwrite of checked bytes. Ordinary Retry still renews the same ticket without replacement.
3. If ticket issuance throws, wipe only the new bytes and keep the old pending selection for recovery. A network timeout can be ambiguous about server completion; reselect/refresh may then be required.
4. Verify returned document type and MIME before adopting the ticket; unexpected formats wipe the new bytes and fail.
5. Zero superseded local bytes/thumbnail, remove that type's old READY metadata, then install the new ticket/bytes/pending ID and reset its transferred flag. This prevents a replaced file from still appearing checked. Server history is retained, not deleted by this client change.
6. Use the unchanged transfer and confirmation path. No new file becomes READY merely because the picker/camera returned it. ClamAV and backend checks remain mandatory; scanner failures cannot be fixed by this UI change.

No database schema, backend, payment or provider configuration changed. Verify on phone: force an upload/check failure, try Retry, then tap Take ID Photo and Choose from gallery; cancel once and select a replacement once. Both ID sides must remain selectable when idle; request-in-progress buttons remain disabled. Repeat for selfie/PDF, and confirm pending/approved verification cannot be edited. Remaining risk: native interaction still needs device testing. No new lawyer/Safaricom/provider decision is introduced; existing document retention/privacy obligations remain. **RECOMMENDED next task:** install the rebuilt APK and reproduce this exact failure-and-reselection sequence with disposable files.

Final regression run for this fix: **48 Android tests passed**, zero failures/errors/skips; `assembleDebug` succeeded. Existing Landlord icon deprecation and optional analytics-directory warnings do not block compilation. Camera/gallery interaction was not run on a physical device.

## Scope, decisions and intentional limits

Implemented: separate Sublessor workspace; spare-room/servant-quarter drafts; media; private map pin; household/deposit details; listing state actions; application review with rejection reasons; existing interests and protected chat; real payment history; role-specific verification; preferences and opted-in renter discovery.

Approved decision: store preferences but defer matching percentages. Discovery is an unranked list, not a compatibility recommendation. No scoring weights, commission, posting charge, boost price or payment rule was invented.

The reference is `/tmp/codex-clipboard-977ff12e-f032-4078-a453-f9e814740eb9.png`. Cream/brown surfaces, rounded cards and the reference's information hierarchy are implemented with native Compose. Device screenshots are unavailable, so exact visual fidelity is **not verified**. See `design-qa.md`.

Listing moderation is now server-backed through the V58 submission, review-status, revision and publication contracts. Direct Sublessor-started conversations from discovery still have no decided contract, so the UI continues through the existing accepted-interest protected inbox. It does not fabricate a profile-report endpoint, sample renters, scores, photos, booking balances or payout destination controls. Existing financial screens are history, not a new payment implementation.

## End-to-end flow

1. Existing JWT login/profile identifies the active `SUBLESSOR` role. `MainActivity` opens `SublessorWorkspace`, not Landlord screens.
2. The workspace loads the caller's verification, listings, applications and interests. No owner ID is accepted from a form.
3. Verification collects ID front, ID back, a camera photo and a PDF rental agreement. A photo capture is **not** a liveness/face-match result.
4. Each file gets an immutable upload ticket, uploads directly to allowed private storage without the app JWT, then is confirmed through the authenticated backend. Confirmation validates actual bytes; upload success alone is insufficient.
5. Once the role is server-verified, the app enables a new shared listing. Details and owned household data save before advancing to media. A partial household failure retains the created listing ID, so retry updates that draft rather than creating another listing.
6. A private MapLibre pin and vague public area are saved separately. Exact coordinates never enter discovery profiles.
7. Publish checks the real role verification, ownership, saved location and at least one confirmed **image**. A video alone cannot satisfy the photo requirement.
8. Preferences store seven explicit answers and a KES budget range. Opt-in is false by default. Discovery requires a verified, opted-in Sublessor and returns only active, verified, opted-in Renters.
9. Applications retain the existing state machine. Rejection now requires a reason and returns it to the applicant. Existing accepted-interest threads feed protected WebSocket chat; the client displays the server-masked echo, not an optimistic raw message.

## Android file/class/method guide

Root for the following files: `/home/hp/Desktop/makao-hub-frontend/app/src/main/java/com/makaohub/android/`.

### `MainActivity.kt`

The authenticated role routing now selects `SublessorWorkspace` for SUBLESSOR. Removing that branch sends this role to the old fallback rather than the new workspace. It does not grant backend permission; JWT authorization still decides access.

### `data/MakaoApi.kt`

Added Retrofit declarations for Sublessor verification/documents, shared listings/media/location/household, preferences/discovery, applications, interests and roommate threads/messages. Each declaration maps HTTP method/path/body to the backend contract. Removing one breaks its corresponding repository operation; changing a DTO without its server record can silently lose fields.

### `data/SublessorRepository.kt`

Models: `SharedListing`/`SharedListingRequest` describe a room, not a whole unit; `SharedHousehold` holds nullable deposit and household information; `SharedPreferences` holds answers and opt-in; `SharedPublicProfile` contains only discovery-safe fields. `SharedInterest`/`SharedThread` describe the existing interest-chat relationship. `SharedThreadPage` uses Spring's `content/number/last`; `SharedMessagePage` uses `items/nextCursor/hasMore`. They intentionally differ because the existing endpoints differ. `SharedDiscoveryPage` carries bounded pagination metadata. `SharedRejection` carries a required decision reason.

`SharedEvidence` enumerates the four permitted evidence types. `sharedEvidenceReady` and `sharedVerificationReady` require the server's READY state/current-check marker and the agreement's PDF MIME type; they do not trust a local selected-file flag. Removing these checks would enable premature navigation, although server submission still rejects incomplete evidence.

Repository operations and why each exists:

- `verification`, `documents`, `ticket`, `confirmEvidence`, `submitVerification`: metadata/status, private transfer authorization, byte validation and final role submission. Omitting confirmation leaves a document incomplete.
- `listings`, `save`, `transition`: list owned rooms, create/update a draft, and execute only allowlisted state actions. Removing the allowlist would permit arbitrary route suffixes from callers.
- `location`, `saveLocation`: fetch/update the owner's private pin through existing owned routes.
- `household`, `saveHousehold`: fetch/update the new owned supplemental record without altering the public location/listing response.
- `preferences`, `savePreferences`, `discovery`, `renterProfile`: own settings and server-rechecked public discovery. No client-side filtering substitutes for backend opt-in checks.
- `media`, `mediaTicket`, `confirmMedia`, `deleteMedia`: real stored rows, upload authorization, validation and confirmed deletion. No fake preview is persisted as successful media.
- `applications`, `decide`: own application queue; reject sends an explicit reason. `interests`, `interestAction`: existing interest lifecycle, with action allowlisting.
- `threads`, `messages`, `payments`: use the existing server paging contracts. `connect`: chooses the roommate socket channel, not the Landlord rental channel.
- `transfer` and `transferEvidence`: separate OkHttp transfer client, finite timeouts, no redirects, no transparent retries, allowlisted signed destination and no app Authorization header. This avoids leaking JWTs to storage/redirect targets or silently replaying uploads. `requireSuccess` turns failed empty responses into an error rather than reporting success.

All authenticated operations use `AuthRepository.authorized("SUBLESSOR")`, preserving its shared refresh lock. This client check improves routing only; server authorization is mandatory.

### `SublessorViewModel.kt`

`SharedScreen` names the native routes. `SharedForm.error` validates room type, required fields, integer KES amounts, dates and bounded household counts; `request` builds the existing listing body; `household` builds the supplemental body; `withHousehold` restores an owned saved record. `PreferenceQuestion` maps the seven categorical API answers to readable options. `RoommateForm.error` requires all answers and a positive ordered budget; `request` builds the body while preserving optional existing profile fields.

`SublessorViewModel` owns in-memory workspace state and operations:

- `start`, `reloadVerification`, `reloadWorkspace`, `refreshVerification`: load real state; no client-created verification approval. Removing reloads leaves stale UI.
- `go`: loads the selected destination's data and closes a departed chat. `newListing` gates the UI on server verification. `edit` loads the selected room plus household; `resetMedia` prevents another listing's media/location leaking into the editor.
- `saveDraft`: validates and saves base details, then household, retaining the first successful ID on partial failure. Navigation occurs only after both complete.
- `loadMedia`, `uploadMedia`, `retryMedia`, `confirmStoredMedia`, `finishMedia`, `deleteMedia`: prepare a known-length content stream, request a ticket, transfer once, confirm actual bytes, retry the retained operation or delete a selected owned row. A completed transfer is remembered so a confirmation retry need not upload again. Missing/changed streams fail rather than sending an unbounded file.
- `openLocation`, `savePin`: load the owned pin, validate Kenya bounds and save a decimal request. Floating-point map coordinates are geographic coordinates, never money.
- `transition`: run the selected listing action then refresh it. `openApplication` selects a queue row; `decide` sends a decision/reason and refreshes the queue; `interestAction` refreshes after the existing interest action.
- `nextThreads`, `openThread`, `olderMessages`: bounded server paging and selected-thread history. `nextPayments` advances actual payment records. No totals are invented from incomplete pages.
- `connect`, `reconnect`, `disconnect`: channel lifecycle with a generation guard that ignores callbacks from an old socket. `updateChat` bounds draft length; `sendMessage` assigns/reuses a UUID and waits for server echo before showing the masked message. Removing that wait exposes raw contact-sharing text locally before server moderation.
- `savePreferences`, `nextDiscovery`, `openRenter`: persist answers and fetch fresh pages/direct profiles. `background` disconnects chat and clears discovery/profile display; `foreground` refetches those views so a later opt-out is checked again. A response already seen by someone cannot be remotely erased from their memory.
- `uploadEvidence`, `captureEvidence`: bounded PDF/image input or camera capture; `beginEvidence` obtains a role-specific ticket; `retryEvidence`, `finishEvidence`, `confirmStoredEvidence`, `confirmEvidence`: explicit recovery without assuming that an HTTP/storage conflict means a checked document. `submitVerification` requires all current ready evidence.
- `run`: serializes workspace actions, retains coroutine cancellation, handles session expiry centrally, and maps errors to non-sensitive notices. `showNotice`/`dismissNotice` manage those notices.
- `clear` and `onCleared`: cancel work, close the socket, overwrite retained evidence arrays and drop session-specific references. JVM copies/OS buffers cannot be guaranteed erased; this is not a managed-memory secure-erasure claim.

### `ui/SublessorScreens.kt`

`SublessorWorkspace` creates the per-account ViewModel, chooses each route, binds back navigation, bottom tabs, lifecycle and error notices, and sets `FLAG_SECURE` for evidence/private-map views. Removing lifecycle cleanup can leave an old chat connection or sensitive state alive after leaving the workspace.

Screen methods, connections and removal effects:

- `SharedHome`: real name, verification and owned records with short next actions. Removing it removes the workspace entry point.
- `SharedDetails`: room, rent/deposit, household, availability and amenities editor bound to `SharedForm`; no unsupported whole-property choices.
- `SharedMedia`: real upload/confirmation rows, current-session photo and explicit deletion dialog; connected to the media ViewModel operations.
- `SharedLocation`: existing private MapLibre picker and public area, no raw-coordinate text entry or billing/debug prose.
- `SharedPreview`: actual selected room and owned household summary before publication; never a sample listing.
- `SharedListings`, `SharedStates`: current server listing states and available real actions. No moderation approval is inferred.
- `SharedApplications`, `SharedApplication`: real queue/detail and review/approve/reject flow, with a reason input. Removing the reason input breaks the new reject contract.
- `SharedInterests`: existing renter interest actions. `SharedInbox` loads existing threads; `SharedConversation` shows masked server messages and connection state.
- `SharedPayments`: existing paginated history with actual recorded amounts. It does not change rent/deposit/commission ledger logic.
- `SharedPreferenceEditor`: seven categorical choices, budget and explicit opt-in. `SharedPreferenceSummary`/`PreferenceLines`: saved values, not a score.
- `SharedDiscovery`: eligible renter pages. `SharedRenterProfile`: fresh public profile and plain shared/different answers, not weighted ranking. Its inbox action opens existing protected conversations, not an invented unsolicited chat.
- `SublessorVerification`: front/back, camera photo, PDF agreement, submission and actual pending/approved/rejected status. Reuses `EvidenceCamera`; no fake biometric result or automatic approval.
- `SharedHeading`, `SharedCard`, `SharedText`, `SharedStatus`, `SharedNote`, `SharedButton`, `SharedLink`, `SharedInput` and `sharedKes`: small reusable presentation helpers. Removing one breaks its call sites; they keep spacing/colors/input rules and integer KES presentation consistent without new libraries.

### Small reused-file changes

`data/AuthRepository.kt`: the longer evidence-confirm timeout now recognizes both landlord and sublessor paths; otherwise valid malware scans can exceed the normal request timeout. Workspace access errors are role-neutral.

`data/LandlordRepository.kt`: shared `OwnerMedia` gains `confirmed`; `OwnerApplication` gains optional `rejectionReason`; socket construction names the callback to avoid positional ambiguity. Landlord behavior remains on its own channel.

`data/RentalChatSocket.kt`: `ChatChannel` selects RENTAL or ROOMMATE. The existing STOMP subscribe/send builders now use the actual roommate `/user/queue/chat.messages` and `/app/chat.send` destinations when selected. Default remains RENTAL for existing Landlord callers. The existing parser, server-masked content and close cleanup are reused.

`ui/LandlordScreens.kt`: `LocalPhoto` is internal instead of private so the Sublessor can reuse the existing bounded local preview implementation. No new stock assets are added.

`ui/PropertyMapPicker.kt`: `showDeveloperDetails` defaults to true for existing callers; this workspace supplies false. Only explanatory developer prose is hidden; actual map attribution is retained.

## Backend reading guide

See `/home/hp/Desktop/makao-hub-backend/docs/sublessor-workspace.md` for every changed backend contract, service method, migration, security-check walkthrough and test. New supplemental storage is JDBC. Existing JPA document/listing/application services are retained rather than migrating unrelated modules in this task.

## Versions and complexity

Uses the installed project versions: Kotlin 2.2.10, Compose BOM 2026.02.01, Android Gradle Plugin 9.4, Gradle 9.6, CameraX 1.5.3, MapLibre Native 13.6.1, Retrofit 3.0.0, OkHttp 4.12.0; backend Java 21, Spring Boot 4.1.1 and existing PDFBox 3.0.8. No new UI/network library is required.

Seven categorical answers are fixed-size O(1) work; their comparison is not scoring. Rendering one page is O(page size); document transfer/validation is O(file bytes) with existing byte/pixel/page limits. Evidence arrays consume O(file bytes), limited to the configured maximum. Discovery's index supports eligible-row access, but offset paging and COUNT are not O(1): deep pages/counts can scan many rows. Server caps size at 50 and page at 10,000. **RECOMMENDED:** keyset paging if scale requires it. Existing listing/application/interest endpoints still return whole lists; client display chunking is not server pagination.

## Verification and acceptance

Final automated run: **46 Android tests passed**, no failures/errors/skips; `assembleDebug` succeeded. **165 backend tests passed**, no failures/errors/skips, excluding only the configuration-dependent application-context smoke test described below. Clean-schema Flyway integration applied all **55 migrations**. Backend whitespace checks passed. Android emitted existing icon-deprecation/optional analytics-directory warnings; neither prevented the build.

Automated checks cover API payloads/role restrictions, integer amounts, current document readiness, real roommate STOMP destinations, JWT-derived ownership, invalid input, discovery eligibility/opt-out, public contact masking, draft-only household edits, audit immutability and the confirmed-photo publication requirement. Dedicated local PostgreSQL tests run all migrations in isolated synthetic schemas, never against the application's live Supabase database.

The configuration-dependent `MakaoHubBackendApplicationTests` smoke test requires the normal runtime database/secrets and was not included in the isolated suite. A plain full-suite run without those settings fails startup; do not mistake that for a verified deployed backend.

Manual checklist:

1. Restart the backend using its existing valid private development environment. Flyway should add V54/V55 once; do not edit older/applied migrations. Confirm ClamAV is reachable before agreement confirmation.
2. Install `/home/hp/Desktop/makao-hub-frontend/app/build/outputs/apk/debug/app-debug.apk` on the development phone. Use a disposable verified Sublessor or exercise pending/rejected verification with synthetic documents.
3. Check camera denied/granted, both ID sides, PDF-only picker, invalid/oversized files, scan-unavailable failure, retry and background/return. Confirmation failure must not appear as Uploaded/Verified.
4. Create a draft, restart editing, check household/deposit, upload/confirm an image, save a private pin, preview and publish. Try another owner's listing ID and an unverified role; deny both.
5. Save a Renter's preferences using the new Renter API until its Android editor exists. Opt in with a verified role; confirm only eligible profiles appear. Opt out and retry both list and known direct profile ID; the renter must disappear.
6. Review an owned application; reject with a reason and check its response. Exercise accepted-interest inbox, server contact masking and reconnection. Do not infer an unlock from the UI alone.
7. Compare all reference frames on-device at matched width and large text. Camera, map gestures, system bars, keyboard and file-provider behavior need real device testing. Never disable secure-window protection on real documents just for screenshots.

## Remaining risks, confirmations and suggested next task

Risks: no device/rendered visual acceptance yet; no biometric liveness/face-match provider; KMS/storage-envelope-encryption decisions remain open; media ticket expiry and process death may require explicit reselection/replacement; contact pattern masking cannot guarantee every obfuscation is detected. This change does not solve existing full-list pagination, moderation APIs, direct discovery-started chat or all production authorization/audit gaps. Supplemental deposit is display data, **not** a new authoritative booking price: integrating it with checkout requires an explicit pricing decision. Existing approved verification records are not silently invalidated or backfilled.

Provider/lawyer: verify storage retention, deletion, private bucket policy and managed encryption; camera photos must not be represented as biometric verification. Have a Kenyan privacy lawyer review identity/contract processing and public profile consent. No funds moved or custody arrangement changed; regulated custody, commissions, refunds and payout terms remain separate decisions.

**RECOMMENDED next task:** connect the phone and complete one synthetic Sublessor journey, then capture permitted non-sensitive states for visual comparison and wire the Renter preference editor to the new API.
