# Landlord Android workspace — implementation notes

## Scope and flow

This slice adds native Kotlin/Jetpack Compose landlord screens in `/home/hp/Desktop/makao-hub-frontend`. It uses the existing Java/JPA backend in `/home/hp/Desktop/makao-hub-backend`; it does not change that backend, its database, OTP provider or financial rules.

The visual source is the landlord board attached on 13 October 2026: `/tmp/codex-clipboard-4ed54047-05e1-4d4a-868f-a5802a79105d.png`. The board informs the cream/brown styling, rounded cards, headings, four-step listing flow, five bottom tabs and confirmation dialogs. Actual device comparison is still blocked; this is not a pixel-perfect acceptance claim. See `/home/hp/Desktop/makao-hub-frontend/design-qa.md`.

The server must return a successful authenticated profile with active role `LANDLORD` before the app opens this workspace. A role selected during signup is not proof of authorization. Existing authentication/OTP remains in force.

Listing flow: Home → Create property listing → save private draft → choose actual photos/video → obtain signed upload URL → upload bytes to Supabase → confirm through Makao → save approximate area and private coordinates → preview without revealing coordinates → server publication checks → published/paused states. A successful file transfer alone does not mean confirmation or publication succeeded.

Application flow: Applications → selected application → mark under review → confirm approve/reject → show the server's returned status. Approval does not transfer money.

Messaging flow: paginated conversations → cursor-based saved history → authenticated native STOMP WebSocket → send with client message ID → render the server's saved/masked echo. The app does not display an optimistic unmasked outgoing message.

Test checkout: quote request with stable idempotency key → show server amount → explicit mock success/failure → show simulated receipt. No actual SMS is used by these automated checks and no real payment is made.

## Contract differences that remain unresolved

These are inspected API gaps, not features silently invented by the frontend:

| Reference feature | Current supported behavior / limitation |
| --- | --- |
| Deposit, amenities and private address | Listing DTO accepts rent, type, description, bedrooms, bathrooms, furnished and availability. Deposit, additional amenities and address are not saved. |
| Property map/search | Area and coordinate entry work. Interactive map/address search needs provider configuration and integration. No dummy map pin is stored. |
| Media cover/reorder and old thumbnails | Server returns media metadata without read URLs, confirmation status or cover/reorder contract. Session-selected photos have local previews. Metadata is never labeled confirmed merely because a row exists. |
| Staff review, listing approval/rejection | Current listing endpoint publishes directly after verification/media/location checks. No staff-review submission is simulated. |
| Renter name, income, move-in date | Application API supplies identifiers, status and timestamps only. No private ID/face evidence or invented renter details are displayed. |
| Saved rejection reason and renter notification | Existing rejection endpoint takes no reason. UI says this explicitly; delivery of a notification is not claimed. |
| Incoming rent and payout history | Existing payment history is the caller's outgoing payments. It must not be relabeled an incoming rent ledger. |
| Payout destination changes | No destination endpoint was found. Inputs and save action are disabled; re-verification and cooling-off are not bypassed. |
| Paid plan allowances, boost duration/activation | No catalogue or entitlement response. Mock receipts are not treated as active subscriptions/promotions. |
| Pagination of owner listings/applications | Existing APIs return whole arrays. UI reveals 20 at a time, but that is not server pagination. Conversations/payments use server pages; message history uses a server cursor. |

No new prices, commission rates, cooling-off duration or payment custody rules were decided.

## Files, classes and methods

### `/home/hp/Desktop/makao-hub-frontend/app/src/main/java/com/makaohub/android/MainActivity.kt`

Changed `MakaoApp`'s ACCOUNT branch: it opens `LandlordWorkspace` only for the profile's active `LANDLORD` role. Other roles keep their existing account screen. Without this branch the new workspace is unreachable; using a signup choice here would route on untrusted input. Existing splash/onboarding are unchanged.

### `/home/hp/Desktop/makao-hub-frontend/app/src/main/java/com/makaohub/android/AuthViewModel.kt`

The existing repository reference becomes `internal` so the landlord repository can use the same session and refresh lock. It is not a second login or token store. Removing this connection would force duplicate session handling or break workspace requests.

### `/home/hp/Desktop/makao-hub-frontend/app/src/main/java/com/makaohub/android/data/AuthRepository.kt`

`profile()` now delegates to the generic `authorized(requiredRole, action)` protected-call path. This is the same serialized refresh behavior used for owner actions. `WorkspaceUnavailable` represents an incompatible active role; `friendlyError` adds its fixed display text.

Security-critical `authorized` sequence, statement by statement:

1. Acquire `sessionLock`: prevents concurrent requests from replaying the same rotating refresh token.
2. Require existing server-issued tokens: an OTP challenge or selected role is not a session.
3. Check `requiredRole` against the session role: prevents accidental wrong-workspace calls locally. The backend still must independently enforce role and ownership.
4. Call the supplied API action with `Authorization: Bearer ...`: credentials are headers, not URL parameters.
5. Catch only an explicit HTTP 401 for refresh: a 403, validation failure or lost network response is not permission to retry a mutation.
6. Refresh once under the same lock: serializes rotation.
7. On failed/cancelled refresh clear the uncertain token pair: a lost response could conceal successful rotation, so the old refresh token is not replayed.
8. Replace tokens with the returned pair and recheck role: a refreshed session cannot silently inherit landlord access.
9. Retry the original action once after explicit unauthentication. A second 401 clears the session instead of looping.

Removing the common path would allow inconsistent token handling. Requests still use the existing no-body-logging client, finite timeouts and disabled redirects/connection retries.

### `/home/hp/Desktop/makao-hub-frontend/app/src/main/java/com/makaohub/android/data/MakaoApi.kt`

Each added suspend Retrofit method declares one inspected backend route. Without the corresponding method its repository action cannot make a request:

| Methods | Purpose and connection |
| --- | --- |
| `landlordDashboard`, `landlordListings` | Read real summary and owned listing array for Home/Listings. |
| `createListing`, `updateListing` | Save exact supported draft fields, not a client-supplied owner ID. |
| `publishListing`, `pauseListing`, `resumeListing` | Request legal server transitions; UI reflects the response. |
| `ownerLocation`, `saveLocation` | Authenticated private coordinate retrieval/update; coordinates are request bodies, not query parameters. |
| `ownerMedia`, `mediaUpload`, `confirmMedia`, `deleteMedia` | Read metadata, create upload ticket, check uploaded object, remove owned media. |
| `landlordApplications`, `reviewApplication`, `approveApplication`, `rejectApplication` | Read applications for owned listings and request server decisions. |
| `landlordThreads`, `landlordMessages` | Page size 20 and cursor limit 50 for protected conversation/history endpoints. |
| `ownerPayments`, `ownerCheckout`, `completeOwnerMock` | Page outgoing payments; create server-priced intent with idempotency header; explicitly complete a mock receipt. |

The Retrofit annotations and request/response classes are the serialization contract, not a substitute for backend authorization.

### `/home/hp/Desktop/makao-hub-frontend/app/src/main/java/com/makaohub/android/data/LandlordRepository.kt`

Models: `LandlordSummary` supplies counts; `OwnerListing` and `ListingDraft` separate returned state from editable fields; `OwnerLocation` holds private BigDecimal coordinates; `OwnerMedia`, `MediaUploadRequest`, `MediaUploadTicket` represent metadata and the signed transfer; `OwnerApplication` represents decisions; `OwnerPage<T>`, `OwnerThread`, `OwnerMessagePage`, `OwnerMessage` model pagination and masked messages; `OwnerPayment`, `OwnerCheckout`, `MockCompletion` model receipts and requests. These match the server rather than the illustrative names/amounts in the board. Sensitive location, URL and payment classes have no generated data-class `toString`.

`MediaTransferFailed` is a sanitized transfer failure, not the raw provider response.

Repository method responsibilities:

- `summary`, `listings`, `applications`, `media`, `location`, `threads`, `messages`, `payments`: obtain server data through `authorized("LANDLORD")`. Removing one breaks its screen's loader.
- `save`: choose create versus update from an existing server listing ID; prevents editing from accidentally making another draft.
- `transition`, `decide`: explicitly whitelist supported actions; unknown operations are not converted to guessed endpoints.
- `saveLocation`, `uploadTicket`, `confirm`, `remove`: save private pin, reserve a media record, validate transfer and delete through owned routes. `remove` checks the HTTP result before claiming deletion.
- `checkout`, `complete`: create a server-priced intent and submit an explicit mock outcome. The UI supplies no owner ID or amount.
- `connectChat`: reload profile through refresh handling, check active landlord role, then authenticate the socket. Without it an expired access token could open a doomed connection.
- `transfer`: perform a streaming upload on the IO dispatcher using a separate OkHttp client. Without separation the Makao bearer token could leak to storage; redirects and automatic retries are disabled, and timeouts are bounded.
- `signedMediaRequest`: require HTTPS, a `.supabase.co` host and the signed-upload path, then build PUT with no Makao Authorization header. Tests reject HTTP, a look-alike host and an unrelated path. Custom storage domains are not yet supported. The signed URL is a secret capability and is never displayed/logged.

### `/home/hp/Desktop/makao-hub-frontend/app/src/main/java/com/makaohub/android/LandlordViewModel.kt`

`OwnerScreen` names navigation states; `ListingForm` holds bounded editor inputs. `error` rejects invalid lengths, unsupported type, fractional/overflow/negative rent, bedroom/bathroom limits and invalid ISO dates. `request` converts validated whole-KES input without floating-point rounding. `from` loads an existing draft. Without these steps malformed values could crash conversions or send mismatched fields.

`nextMediaSortOrder` uses a HashSet of occupied positions and picks the lowest free slot from 0–30. It reuses deletion gaps, unlike array length. Time/space O(n) for the small bounded media list.

`LandlordViewModel` owns screen data, busy/error state and in-memory private values:

- `go`: move to a permitted screen and load its data; close chat when leaving it. `refresh`: load summary/listings/applications; only mark counts loaded after success.
- `newListing`, `edit`, `resetEditor`: initialize a new or saved draft and clear stale media/location/upload state.
- `saveDraft`: validate before create/update and optionally advance to media. `loadMedia`: refresh saved records. `removeMedia`: await deletion, remove the local preview and reload metadata before claiming success.
- `uploadFile`: inspect MIME/known size off the main thread; allow JPEG/PNG/WebP/MP4; get a free order and ticket; stream at most the declared bytes in 8KB chunks; reject shortened/extra streams; PUT; then confirm. It does not load a 50MB file into memory. Server limits and actual-object validation remain authoritative.
- `confirmPendingUpload`, `retryConfirmation`: when transfer succeeded but confirmation failed, retry only confirmation; keep the ticket until the server succeeds. No second transfer is necessary. Recovery is in-memory; process death still needs server-record cleanup/recovery.
- `openLocation`: treat only 404 as an unset location. `saveLocation`: validate public area and Kenya coordinate bounds with BigDecimal, then store through the private endpoint.
- `listingAction`: await publish/pause/resume, refresh listings and show returned state; no client-set publication flag.
- `viewApplication`, `decision`: select an application and await review/approve/reject; no assumed notification or money movement.
- `loadThreads`, `openThread`, `olderMessages`: load server pages/history, connect only open threads, merge older messages by ID. Manual history expansion is O(n) for merging/deduplication.
- `connectChat`, `reconnectChat`: establish a socket and consume events on the main dispatcher. Generation checks ignore late events from logged-out, previous or background-closed connections.
- `sendMessage`, `changeChatDraft`: bounded draft, stable client message ID and one outstanding send. Do not clear/render unmasked draft until its matching persisted echo arrives. A retry keeps the same ID when text is unchanged, for server deduplication.
- `disconnectChat`: invalidate previous callbacks, close socket, clear connected/pending indicators. Saved server history and the unsent in-memory draft are not declared lost.
- `loadPayments`, `openCheckout`, `createCheckout`, `finishMock`: page receipts, choose only plan/boost with valid target shape, preserve an idempotency key for the attempt, obtain quote and complete only in debug builds.
- `dismissNotice`: clear an acknowledged status message.
- `run`: prevent overlapping taps, run suspend work, propagate cancellation, show fixed safe messages and return expired/wrong-role sessions to login. Workspace HTTP messages do not suggest signing in to fix a publication conflict.
- `clearPrivateState`, `onCleared`: cancel work, invalidate callbacks and discard location/media/URLs/chat/payment state on logout/disposal. Without these, late work could repopulate a previous user's data. Cancellation does not undo a mutation already accepted by the backend; refresh its saved state after signing in again.

### `/home/hp/Desktop/makao-hub-frontend/app/src/main/java/com/makaohub/android/data/RentalChatSocket.kt`

`ChatEvent` carries Connected, saved Message or safe Unavailable text. `RentalChatSocket` owns native `/ws` STOMP 1.2 communication without adding a STOMP dependency.

- `connect`: open `/ws` against the configured Makao server.
- `onOpen`: reject CR/LF in the authorization value; send CONNECT with bearer token and supported protocol, preventing header injection. `heart-beat:0,0` explicitly negotiates no STOMP heartbeat; OkHttp sends transport pings every 20 seconds.
- Both `onMessage` overloads feed received UTF-8/text or binary bytes into `receive`.
- `receive`: require CONNECTED 1.2, subscribe only to the current user's message/error queues, validate saved message fields, emit safe errors and close malformed connections. It never subscribes to a public conversation topic.
- `send`: require a connected socket, nonblank text ≤2,000 characters and serialized frame ≤8,192 bytes, compute UTF-8 content length and submit SEND with client message ID. It reports queued/not queued, not persisted/not persisted.
- `onFailure`, `onClosing`, `onClosed`, `unavailable`: clear readiness and report disconnection without raw exception/frame details. `close`: mark intentional stop, close transport and suppress an intentional-close warning.
- `StompFrame` represents parsed command/headers/body. `StompFrames.feed` handles partial and multiple frames, byte lengths, CRLF/LF and heartbeat newlines within a bounded buffer. `unescape` accepts only STOMP header escape sequences. Without byte-oriented parsing, split Unicode and content-length would be mishandled. Invalid lengths, malformed headers and overlarge input fail closed.

Reference: [STOMP 1.2 specification](https://stomp.github.io/stomp-specification-1.2.html). This frontend implementation does not replace the server's per-SEND/SUBSCRIBE authentication, participation checks, masking or audit.

### `/home/hp/Desktop/makao-hub-frontend/app/src/main/java/com/makaohub/android/ui/LandlordScreens.kt`

`LandlordWorkspace` binds the shared session to one owner ViewModel, uses native safe/keyboard insets, shows loading/errors, manages five tabs and closes sockets on background/disposal. `parentOf` maps system/header Back to the same parent. Removing their lifecycle cleanup risks stale connections/private state.

Reusable visuals: `OwnerHeading` establishes hierarchy; `OwnerCard` gives white rounded sections; `Small` provides body/secondary text; `Notice` separates warnings/success; `Badge` formats server status; `CardLink` supplies actions; `OwnerInput` binds fields/keyboard; `kes` formats whole-KES rent; `moneyMinor` uses BigDecimal scale 2 for receipts; `shortId` abbreviates display only, never the ID sent to APIs; `stamp` shows timestamps in Africa/Nairobi and a safe fallback. Removing these breaks shared layout/formatting, not server authorization.

Screen composables:

- `OwnerHome`: actual name, verification, counts, drafts/applications and business links, with refresh; no sample totals.
- `OwnerListings`: saved draft/published/paused states, resume/pause, test boost; progressive 20-row display is not backend pagination.
- `PropertyEditor`: supported draft fields and type dropdown, validation/save; unsupported fields are clearly absent rather than silently lost.
- `LocalPhoto`: decode user-selected images off the main thread with downsampling. No fake listing image or persisted signed read URL.
- `ListingMedia`: device file picker, local previews, metadata, retry confirmation and refresh; deletion requires an exact-file confirmation dialog. No unsupported cover/reordering action.
- `PrivateLocation`: public area plus private coordinate inputs. It explains the missing map integration instead of sending coordinates to an unconfigured third party.
- `ListingPreview`: public-facing listing details and approximate area only; never renders exact coordinates in the renter preview.
- `PublicationChecks`: required verification/media/location notices and returned publication state. Backend independently validates the action; a media row is not treated as proof of readiness.
- `ApplicationList`, `ApplicationReview`: actual IDs/status/timestamps, decision dialogs, no renter identity documents or invented employment data. Rejection reason is not pretended to persist.
- `Conversations`, `Conversation`: page controls/history cursor, server-masked content/flag badge, reconnect/draft/send; closed threads cannot send.
- `PaymentHistory`, `Pagination`: server pages and exact minor-unit receipts; incoming rent/payout gap is stated. Removing page controls would strand additional rows.
- `PayoutDestination`: disabled destination editing until the secure backend contract exists; removing the disabled state would suggest an unsupported financial operation.
- `TestCheckout`: server quote, explicit simulation buttons and receipt. Successful simulation is never labeled an active plan/boost.
- `OwnerProfile`: actual profile, payments/settings links, profile refresh and sign-out that clears private workspace state.

### `/home/hp/Desktop/makao-hub-frontend/app/build.gradle.kts`

Adds `MOCK_PAYMENTS_ENABLED=true` for debug and false for release. Without the build distinction mock completion could appear in a release UI. This is defense-in-depth; the backend must also restrict its mock endpoints. Existing debug-only local HTTP policy is retained for testing, not a production TLS claim. Release requires a configured HTTPS API URL.

Uses existing Compose BOM 2026.02.01, Lifecycle Compose 2.11.0, Retrofit/Gson converter 3.0.0, resolved OkHttp/MockWebServer 4.12.0 and JUnit 4.13.2. No new library dependency was introduced.

### Automated checks

`/home/hp/Desktop/makao-hub-frontend/app/src/test/java/com/makaohub/android/LandlordContractTest.kt`: `setup` creates local MockWebServer and shared repositories; `respond` supplies synthetic HTTP replies; `session` establishes a synthetic verified session through the existing API; `close` shuts down the local server. Tests verify each named contract: draft fields/integer money; private location and owned deletion route; wrong-role no-request and preserved 403; one refresh after explicit 401; application decision/mock idempotency/server amount; paginated/cursor masked history; form boundaries; sort gaps; HTTPS signed destination and absent Makao authorization. Removing these loses regression coverage, not actual endpoint enforcement.

`/home/hp/Desktop/makao-hub-frontend/app/src/test/java/com/makaohub/android/RentalChatSocketTest.kt`: parser fragmented Unicode/multiple frames/heartbeat handling; oversized and malformed input rejection; real local WebSocket handshake, bearer CONNECT, bounded sends and masked saved echo. These validate transport code, not production backend authorization or live provider connectivity.

Existing `AuthContractTest` remains in the same test run. No test sends an Infobip SMS or changes a live Supabase object/payment.

## Verification and remaining risks

Build and local automated checks:

```bash
cd /home/hp/Desktop/makao-hub-frontend
./gradlew :app:testDebugUnitTest :app:assembleDebug --console=plain -Pkotlin.compiler.execution.strategy=in-process
```

APK: `/home/hp/Desktop/makao-hub-frontend/app/build/outputs/apk/debug/app-debug.apk`.

Verification result on 13 October 2026: BUILD SUCCESSFUL; 22 tests passed (10 existing authentication, 9 landlord contracts, 3 STOMP/WebSocket). These are local automated checks, not live backend or visual acceptance.

RECOMMENDED next device checks: sign in as a landlord, test wrong-role access server-side, create/save/reopen a disposable draft, upload/confirm/delete real media, save a private pin, attempt publication before/after verification, approve/reject disposable applications, chat with a renter and test masked phone/email, background/reconnect chat, page histories and simulate one mock checkout. Use the existing configured backend address; localhost on a phone is not the computer. Do not spend additional trial SMS without the owner's approval.

Still at risk: no rendered design comparison or device lifecycle/SAF test yet; no live backend end-to-end proof; pending uploads need cross-process recovery/cleanup; metadata/read-URL/cover APIs and server pagination gaps; no map integration; authenticated private state intentionally does not survive process death; server audit, scanning/encryption, WebSocket authorization and entitlement enforcement remain backend responsibilities rather than guarantees from frontend unit tests.

Provider/lawyer confirmations: none newly implemented here. Before real money, confirm regulated custody, release/refund/payout authorization and provider capability with the payment provider and Kenyan counsel. Before production identity/media use, resolve managed encryption/scanning/retention and legal privacy requirements. The frontend does not establish that Paystack or another provider offers escrow.

Suggested next task: connect the phone, run this APK against the testing backend, capture the landlord states for visual comparison, then prioritize the explicit missing API contracts with the owner.
