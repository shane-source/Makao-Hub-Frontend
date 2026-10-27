# Protected chat screens design QA — 25 October 2026

## Reference and target

- Source visual truth: the seven chat PNGs supplied in the current request: `/tmp/codex-clipboard-450a93dc-88ee-4852-b87d-a2fdcf8469f9.png`, `/tmp/codex-clipboard-cb49ff03-8724-4341-b9aa-815a9061ae59.png`, `/tmp/codex-clipboard-49b29230-7160-421e-9aa8-c74e9bac635b.png`, `/tmp/codex-clipboard-9c251859-712b-44a0-8bb2-a97f013b5b69.png`, `/tmp/codex-clipboard-7ae0eef8-8bbd-490c-951f-cb95c1db92d9.png`, `/tmp/codex-clipboard-4cefb9cd-8e4c-4817-9eae-e0fe8cf3d4b5.png`, and `/tmp/codex-clipboard-9706df4a-2cb1-4fb8-b04c-b211a0bb793f.png` in attachment order.
- Target: the existing Android Compose Renter, Landlord and Sublessor inbox and conversation routes. Android owns the real system bars; the app owns the cream content surface, Makao header, cards, bubbles, state notices and bottom navigation.
- Implemented surfaces: searchable role-safe inboxes; rental and roommate conversations; incoming/outgoing saved-message bubbles; server-confirmed contact-masking warning; history pagination; disconnected/reconnect state; closed-thread state; and guarded composition.
- Implementation screenshot path, viewport and density: unavailable because `adb devices` reports no attached device and no configured AVD is present.

## Findings and intentional contract differences

- [P1 acceptance blocker] No rendered implementation capture exists, so typography, wrapping, card/bubble width, long-scroll rhythm, keyboard insets, bottom-navigation placement, touch targets, contrast, large-text behavior and TalkBack order cannot be visually accepted.
- [P1 product-contract difference] The references show names, listing titles, unread counts, live presence, typing and read/delivery receipts. Current Android thread/message responses do not provide all of those semantics. The UI uses role labels and owned references and does not invent activity or delivery claims.
- [P1 product-contract difference] Report, block and end-conversation actions are not rendered because their backend authorization, retention, audit and state-transition contracts do not exist.
- [P1 open decision] The KSh 50 chat-unlock checkout and its pending/paid/failed boards are not implemented. `AGENTS.md` explicitly marks the chat-unlock scope and prices as undecided. Locked Renter threads remain disabled and visibly `TBC`.
- A sent message appears only after the persisted, filtered server echo and is labelled `Saved`, not delivered/read.
- Contact masking continues on both sides and after any entitlement. The client displays only filtered server content and masking metadata.
- Sample people, listing names, messages, unread counts, payment references and prices are not shipped as production data.

## Functional evidence

- `./gradlew testDebugUnitTest`: passed, including timestamp failure handling.
- Kotlin daemon startup could not write its sandboxed marker file; Gradle's non-daemon compiler fallback completed successfully.
- Functional tests do not substitute for visual evidence.

## Required native pass

Run each role with synthetic threads and messages at a known device size and font scale. Capture inbox search/empty/locked states, landlord and sublessor conversations, masked contact attempts, reconnect, pending server echo and closed history. Compare each image with the matching reference for heading wraps, bubble alignment, card spacing, scroll/keyboard behavior and bottom navigation. Exercise TalkBack and 200% text. Do not include real contact details, private application data or identity evidence in captures.

final result: blocked

---

# Shared verification screens design QA — 25 October 2026

## Reference and target

- Source visual truth: the thirteen verification PNGs supplied in the current conversation, from `/tmp/codex-clipboard-4c625173-7811-48cb-8832-b56e38b3c879.png` through `/tmp/codex-clipboard-4916d784-72e5-4860-9568-de70a045c08c.png` in attachment order.
- Target: the existing native Android Compose verification flows for Renter, Landlord and Sublessor. Android owns status/navigation chrome; the app owns the cream content surface, Makao header, cards, actions and state copy.
- Implemented surfaces: three role-specific introductions; shared ID front/back upload and progress; live camera selfie; landlord ownership PDF; sublessor rental-agreement PDF; private review summary; and one active server-derived pending, approved or rejected outcome.
- Implementation screenshot path, device density and normalized pixel comparison: unavailable because `adb devices` reports no attached device and no configured AVD is present.

## Findings and intentional security differences

- [P1 acceptance blocker] A rendered implementation capture is unavailable, so exact typography, wrapping, vertical rhythm, safe insets, touch targets, contrast, large-text behavior and camera composition cannot yet be accepted visually.
- The reference's dummy private-document preview is not implemented. The current API has no ownership-checked read contract for verification evidence, and adding a screenshot-only preview would expand access to identity documents.
- The selfie remains a fresh camera capture. A gallery fallback is not added because the product baseline requires a face scan/live face check; the identity-provider and liveness contract remain `TBC`.
- ID files remain JPEG/PNG, while PDF is reserved for role documents, matching the current validated backend contract. The reference's ID-PDF wording is not treated as authority to broaden the upload surface.
- Mock filenames, fixed progress percentages and sample rejection reasons are not hardcoded. Filenames and byte progress come from the selected upload; rejection copy comes from the role-scoped server response.
- The outcome page renders only the current server status. The references combine mutually exclusive alternatives on one design board; the production UI does not imply simultaneous pending, approved and rejected states.

## Functional evidence

- Android `testDebugUnitTest assembleDebug`: passed; 75 tests, zero failures/errors/skips; debug APK assembled.
- `git diff --check`: passed.
- Existing `FLAG_SECURE`, private upload validation, server confirmation and role-scoped verification APIs remain in use. This functional evidence does not substitute for visual evidence.

## Required native pass

Run each role with synthetic evidence in Android Studio at a known device size and font scale. Capture only non-sensitive empty/uploading/review/outcome states, then compare each image with its matching reference for heading wraps, card spacing, disabled states, upload recovery, Back behavior, camera permission recovery and TalkBack order. Do not disable `FLAG_SECURE` or capture real IDs, selfies, ownership proofs, agreements or rejection details.

final result: blocked

---

# Renter workspace design QA — 24 October 2026

## Reference and implementation evidence

- Source visual truth: the 34 renter PNGs supplied in the current conversation, from `/tmp/codex-clipboard-b852f324-4c99-491f-b08f-05df0e2a2393.png` through `/tmp/codex-clipboard-e8c69fc8-0d1f-404a-9022-29d0ad97a8e5.png` as attached in message order.
- Source dimensions: 824px wide. Example heights are 3362px (home), 1974px (area), 2662px (application editor) and 2258px (held payment). These are long state compositions, not one fixed Android viewport.
- Target: existing native Android Compose workspace in `ui/RenterScreens.kt`; Android owns status/navigation chrome.
- Implementation screenshot path: unavailable.
- Actual viewport, device density, normalized implementation pixels and CSS size: unavailable; CSS size does not apply to native Compose.
- State inspected in code: home, area, results, filters, details, saved, report, empty/loading, separate location/video/phone access, mock payment success/failure, application list/detail, booking, payment history, protected chat and profile.

## Required comparison surfaces

- Fonts/typography: the native workspace uses the existing Makao sans-serif scale, 25sp bold headings and 14sp body text. Exact weight, wrapping, antialiasing and large-text behavior cannot be compared without a render.
- Spacing/layout: cream page, brown header, white bordered cards, 22dp page inset, 14dp card radius and bottom navigation follow the established app system. Long-page rhythm, keyboard insets and CTA placement remain unverified.
- Colors/tokens: existing brown/cream/sand tokens and semantic green/amber/red statuses are used. Rendered contrast and exact source sampling remain unverified.
- Image quality: listing photos and entitled videos use actual signed, allowlisted Supabase media. Reference sample interiors are not shipped as fake marketplace inventory. Crop, sharpness and media playback presentation require a device.
- Copy/content: sample names, prices, addresses, coordinates, counts and receipts are replaced by authenticated server data. Unsupported application documents and dispute/refund/release claims are not simulated as working production actions.
- Icons/interactions/accessibility: native material navigation, Back, dropdowns, radios, report submission, pagination and primary actions are wired. Touch targets, TalkBack order, focus, text scaling, offline recovery and map gestures require device testing.

## Findings

- [P1 acceptance blocker] There is no rendered implementation capture. Full-view and focused source/implementation comparisons, including the home cards, filter controls, report form, entitlement states, booking states and bottom navigation, are blocked.
- [P1 product-contract difference] The application editor's employment, income and private document fields are not implemented because managed KMS, retention and host-visible fields are undecided. The app retains its minimal consent-and-submit application.
- [P1 product-contract difference] Dispute evidence, refunds and automatic/manual release actions are not implemented because the regulated custody and resolution policy is TBC. Existing server booking states remain visible, but the app does not create unsupported transitions.
- [P2 reference difference] Approximate device-area permission, report screenshots, downloadable receipts, exact postal address and external directions/call actions are absent. Current contracts either do not provide them or would risk putting private data into external URIs.
- [P2 reference difference] Amenity filters are omitted because source schemas do not yet share typed semantics. Property type and known availability date are functional server filters.

## Comparison history

- 23 October 2026: source home, application editor and held-payment frames were opened at original resolution; the remaining supplied frames were inspected from the provided board. Existing native routes and contracts were reconciled, and unsupported screenshot-only policy was excluded.
- 24 October 2026: the existing debug-only mock settlement contract was exposed as separate success and failure actions. The failure request is typed, keeps access locked and is covered by an Android contract test. No production provider or custody behavior changed.
- Android functional evidence: 75 tests passed and the debug APK assembled. Backend evidence: 2 focused authorization tests plus 4 database-backed Renter home tests passed after all 60 Flyway migrations ran on a disposable PostgreSQL database. These are not visual evidence.
- Post-fix visual evidence: unavailable. `adb devices` reports no attached device and no installed emulator image/AVD was found. Full-view and focused comparisons therefore remain blocked.

## Required native pass

Run the Renter workspace from Android Studio with synthetic listings and payments, capture every unique non-sensitive state at a known width/density, and pair each capture with its matching source frame. Check heading wraps, photo crops, card widths, long-scroll rhythm, filter keyboard behavior, status colors, Back/navigation, loading/error/empty states, large text and all entitlement boundaries. Do not disable `FLAG_SECURE` or capture real identity evidence, exact locations or phone numbers.

final result: blocked

---

# Landlord workspace design QA — 22 October 2026

## Reference and target

- Source visual truth: the twenty landlord PNGs supplied in the current conversation. The unique reference states are the dashboard, four-step listing editor, photo/video management and delete/error states, private-location editor, preview, listing management and publish outcomes, application list/review, mock payment history, plans, and boost checkout.
- Primary source paths: `/tmp/codex-clipboard-74539b0a-d874-489e-8770-83401b18d56c.png`, `/tmp/codex-clipboard-40e10ae3-551b-4ba0-9c90-6d4eb7018ea6.png`, `/tmp/codex-clipboard-cf10e23f-c3cb-4568-909b-4b6031deeaf3.png`, `/tmp/codex-clipboard-45db09f1-f51b-4229-9992-a4b622257ae8.png`, `/tmp/codex-clipboard-0c8df524-bddf-4dd3-b8da-437aa5d1f3b1.png`, `/tmp/codex-clipboard-9705cf68-a9b5-48f1-a13b-3a9373dd18b7.png`, `/tmp/codex-clipboard-8ced9383-a98a-4f19-9bb6-fc6e801b8d02.png`, `/tmp/codex-clipboard-bd0fec2e-9ba0-4623-ac07-52601ba50e33.png`, `/tmp/codex-clipboard-aee0f7c7-c601-4442-b4c1-746dbcfdf203.png`, `/tmp/codex-clipboard-ff8a6e7c-1768-40db-9a11-f584e96b5f11.png`, `/tmp/codex-clipboard-5aa8b00c-04e1-42af-955e-faa53c98b7fd.png`, `/tmp/codex-clipboard-70b8dc63-8bed-469e-8644-2bace8405865.png`, and `/tmp/codex-clipboard-15f338e9-3cd7-445e-8d0d-bc74c922f386.png`.
- Target: the existing native Android Compose application. The sources are 824px-wide portrait phone frames with varying heights. Android owns its real status/navigation bars.
- Requested exception: the payout-destination reference (`/tmp/codex-clipboard-2cc9fc7c-4e19-4159-b510-05693609bb01.png`) is intentionally excluded and its route and entry points were removed.

## Implementation evidence

- Implementation screenshot path: unavailable.
- Actual viewport and density: unavailable because no Android emulator/device render was captured in this run.
- CSS viewport size: not applicable to native Compose. Density normalization, full-view overlay, and focused-region pixel comparisons are unavailable without an implementation capture.
- Implemented states: dashboard; listing details including deposit and amenities; media upload/replace/delete/retry; private pin and approximate area; preview/submission; draft deletion; publish/pause/resume; application list/review/approve/reject-with-reason; protected chat entry; mock payment history; illustrative plans; mock boost.
- Functional evidence: Android unit/contract tests and debug assembly pass. Focused backend landlord tests pass. These results do not prove rendered fidelity.

## Findings and intentional differences

- [P1 acceptance blocker] No rendered implementation screenshot exists, so typography, exact spacing, line wrapping, safe insets, scrolling, keyboard behavior, touch targets, contrast, large-text behavior, and state-by-state visual parity cannot be accepted yet.
- The payout-destination screen is absent by explicit product direction even though one supplied image depicts it.
- Live data replaces all sample people, balances, filenames, progress percentages, and listing images. No screenshot-only fake values were introduced.
- The exact private pin is stored and server-protected. Free-text private-address persistence is not added because application-layer encryption/KMS remains a product decision; storing that address in plaintext would violate the repository security baseline.
- Plan and boost checkout remain explicitly TEST/mock because production prices, entitlements, and payment policy are not decided.

## Verification history

- 22 October 2026: implementation source, contracts, builds, and focused landlord tests checked; payout-destination navigation removed. Source PNG files had expired from `/tmp` before a local dimension/overlay pass, and no implementation render was authorized or available.
- Required next visual pass: run the landlord flow from Android Studio with synthetic data, capture every unique state at the reference width/font scale, normalize device density, compare full pages and focused controls, fix discrepancies, and repeat accessibility checks.

final result: blocked

---

# Sublessor workspace reconciliation — 22 October 2026

## Evidence

- Source visual truth: the twelve supplied files `/tmp/codex-clipboard-f38270a4-8be5-42dd-8844-ae0e5fedc180.png`, `/tmp/codex-clipboard-bf162d1a-6e32-4d17-b066-b5c004f9d984.png`, `/tmp/codex-clipboard-9fb64e47-47ed-44f3-b5db-b4b3d4849de5.png`, `/tmp/codex-clipboard-e1401200-4f49-4c68-85ad-50a073c410d6.png`, `/tmp/codex-clipboard-19e4b33d-b97c-42f8-b9d5-48f62ede6f1e.png`, `/tmp/codex-clipboard-9b6b82ea-55e0-4cb8-a9b4-a7132848f896.png`, `/tmp/codex-clipboard-b7bad356-2a17-4c0e-a38d-6876e64e804b.png`, `/tmp/codex-clipboard-1452893d-06ed-4057-a918-2a16523b1784.png`, `/tmp/codex-clipboard-a0682e72-4f1a-48bc-a837-dc0b347e710b.png`, `/tmp/codex-clipboard-cab25d74-cac8-4018-a328-057a174c0b03.png`, `/tmp/codex-clipboard-4524a1d1-9810-4503-98ce-98e008134f7d.png`, and `/tmp/codex-clipboard-1a3f8f91-613b-4874-995d-fa2864ea5f1d.png`.
- Implementation inspected: `/home/hp/Desktop/makao-hub-frontend/app/src/main/java/com/makaohub/android/ui/SublessorScreens.kt` and its production ViewModel/repository contracts.
- Rendered implementation screenshot: unavailable. No configured emulator or permitted device capture was used.
- Native viewport/density: not measured; the references are tall mobile compositions, but their pixels are not treated as Android dp.

## State and content coverage

The implementation has production routes for all twelve reference areas: dashboard, four-step room creation, media failure/deletion recovery, private location, preview/moderation, preference editing and summary, opted-in discovery, renter public profile, application review, test payment history, and listing state outcomes. Empty/loading/error states use real repository state and snackbar/error notices rather than sample names, counts, photos, scores, or balances.

The supplied 88%/75% compatibility examples are not accepted as production truth. Matching weights and rounding are explicitly TBC, so discovery remains deterministic but unranked and the profile explains why a percentage is absent. Direct discovery-started chat is also not accepted because the unlock model is TBC; accepted interests continue to open the protected, server-masked chat. Exact addresses, coordinates, IDs, selfies, rental agreements, phone numbers, emails, and social handles are excluded from discovery responses.

## Checks and findings

- Android `testDebugUnitTest assembleDebug`: passed, 73 tests, no failures or errors; debug APK assembled.
- Focused backend Sublessor authorization/application/publication/verification suite: passed, 22 tests, no failures or errors.
- `git diff --check`: passed in both repositories.
- P0/P1/P2 visual findings cannot be asserted without a rendered implementation image at a measured viewport. Source inspection confirms the shared cream/brown design system, cards, bottom navigation, headings, forms, dialogs, map, and state banners are present, but does not prove pixel fidelity.

## Required native comparison

Open the Sublessor workspace from Android Studio and capture one non-sensitive example of each route at the target device size. Pair each capture with its source frame and check heading wrapping, card width/spacing, long content, keyboard/Back behavior, map gestures, media picker and failure recovery, bottom navigation, large text, and all moderation/application states. Do not capture real identity evidence or exact home coordinates.

final result: blocked

---

# ID front/back design QA

## Reference

- Front-ID reference: `/tmp/codex-clipboard-b8fb38ee-13e8-4593-b315-c375cf8bad75.png`
- Back-ID reference: `/tmp/codex-clipboard-11131a70-14f8-428f-97e6-aa514b753dea.png`
- Target: the existing native Android Compose application, using its current cream, brown, border and typography tokens.

## Implementation comparison

- The native app keeps the Android-owned status bar, system navigation and back behavior rather than drawing them into a bitmap.
- Both pages use the reference hierarchy: Makao header, large side-specific title, step copy, bordered upload card, progress or guidance state, bottom primary action, and the back-page replacement note.
- The same component is used by Renter, Landlord and Sublessor, preventing role-specific layout drift.
- Intentional accuracy changes:
  - ID input says JPEG/PNG up to 10 MiB because that is what the backend accepts; PDF remains for ownership proof or rental agreement only.
  - No dummy file name or fake 64% progress is displayed. The name comes from the selected file and progress comes from bytes written.
  - `Continue` requires backend-confirmed `READY` plus current security checks.

## Render verification

**BLOCKED — not visually passed.** The project compiles and unit tests exercise its state logic, but this environment has no configured Android virtual device/system image. The user previously chose to run the app from Android Studio and did not authorize installing the debug app on the connected phone. The temporary reference files were also no longer present after the development session compacted, so no fresh pixel overlay could be produced.

Required manual check in Android Studio:

1. Open each role's verification flow on the same device and font scale.
2. Capture front-ID empty, back-ID uploading, confirmation-failed and replacement-selected states.
3. Compare spacing, wrapping, scroll behavior, disabled contrast and keyboard/accessibility font scaling against the supplied references.
4. Confirm camera and gallery replacement both work after an OCR rejection and after a temporary confirmation failure.

No visual-fidelity pass should be claimed until those rendered captures are reviewed.

---

# Onboarding design verification

## Shared Sublessor/Landlord pending dashboard — 15 October 2026

Target: the existing Landlord pending dashboard in `ui/LandlordVerificationScreen.kt`, grounded in the previously supplied `/tmp/codex-clipboard-885d27b3-07dd-4ab6-9bc3-b02dc92b0a01.png` (804 × 1748). That PNG is no longer present locally. The same Compose body now renders both owner roles through `PendingVerificationDashboard`; Sublessor supplies a rental-agreement label and its own data/navigation. State: Sublessor HOME with real PENDING verification after Close & Back to Main.

Implementation screenshot, actual viewport/density, normalization/overlay, full-view and focused-region comparisons: unavailable. A phone is connected, but the user explicitly declined installation/inspection and will run from Android Studio. No device mutation or screenshot was performed. Native system bars remain OS-owned.

Required surfaces: typography, spacing/radii, cream/amber/white colors and four-tab composition reuse the same Landlord implementation; rendered name/document wrapping and large text are unverified. Imagery: no fake draft photo or ID asset added; only owned draft titles are shown. Copy: same pending/locked labels, with Rental Agreement (PDF) replacing ownership proof and no unsupported biometric/SLA claim. No code-based judgment is presented as a rendered visual pass.

Finding [P1 acceptance blocker]: source-file reopening and native comparison unavailable. Code changes address the wrong-dashboard flow, but do not close this visual acceptance gap. Compare pending notice, document card, locked action, drafts and bottom tabs after the user runs the app. Functional test/build evidence is recorded separately in `SUBLESSOR_IMPLEMENTATION.md`.

Functional evidence only: 49 Android unit/contract tests passed and `assembleDebug` succeeded. The new pending-main regression proves role-owned drafts and the creation lock, not pixel fidelity. No phone installation or inspection was performed.

final result: blocked
---

## Sublessor workspace — 15 October 2026

Source visual truth: `/tmp/codex-clipboard-977ff12e-f032-4078-a453-f9e814740eb9.png`, 3680 × 8820, supplied twelve-frame Sublessor board. Target: native Android Compose, portrait phone. Implementation: `ui/SublessorScreens.kt`, reused native camera and private MapLibre picker. Screenshot path, actual viewport/density and normalized overlay: unavailable; no connected phone/AVD was available for rendered inspection. Functional tests/build are not visual evidence.

Implemented reference surfaces: warm cream/brown palette, white rounded cards, short headings, shared-home editor/media/map/preview, listing states, application review, preferences, opted-in discovery/profile and history. Native Android owns status/navigation bars. Actual session media replaces source example photos and real authenticated data replaces sample names/amounts.

Typography, spacing, wrapping, scroll/keyboard behavior, map gestures, camera presentation, safe insets, touch sizes, contrast and large text all require on-device comparison. No pixel-exact or accessibility acceptance is claimed.

Approved differences: no invented match percentages/weights; discovery is unranked and labels describe saved/shared preferences. Sublessor verification is separate ID front/back + live camera photo + PDF rental agreement, not an automatic biometric result. No fake payment balances/commission. Missing listing-moderation and direct profile-started-chat contracts are not simulated: actual publish/inbox actions are used. No profile-report button is added without an API. Existing owner proof-of-ownership flow is not reused as a Sublessor ownership claim.

Functional checks: 46 Android tests passed and APK assembled; 165 backend tests passed excluding the runtime-configured startup smoke test, with all 55 migrations applied on an isolated test database. Details are recorded in `SUBLESSOR_IMPLEMENTATION.md`; they cannot replace visual capture. [P1 acceptance blocker] Connect the phone, capture allowed non-sensitive states with synthetic data, normalize source/device density, compare all frames and focused regions, fix differences and repeat. Keep FLAG_SECURE for real evidence/private maps; do not disable document protection for a screenshot.

final result: blocked (native visual comparison only)

---

## Revised landlord verification and pending dashboard — 14 October 2026

Source visual truth: the eight PNGs supplied in the current request, inspected in the conversation:

- `/tmp/codex-clipboard-a943281e-e825-46e4-8061-1c83c3392f87.png` — ID, cropped/transparent background.
- `/tmp/codex-clipboard-c7f9eb8f-85b3-4a2b-8cc9-d53935db3456.png` — selfie, cropped/transparent background.
- `/tmp/codex-clipboard-663f89b6-8c66-4213-8dec-88aa2ca71aca.png` — ownership PDF.
- `/tmp/codex-clipboard-8cabc931-85e0-42b6-b30f-5db7650ff358.png` — review submission.
- `/tmp/codex-clipboard-9b3374f1-73f9-43cd-91d0-a27ebb5810a4.png` — pending review.
- `/tmp/codex-clipboard-8c401a8f-6609-42c9-965e-4537a27cdfff.png` — approved.
- `/tmp/codex-clipboard-1e37664a-9bc3-425f-93f1-9e09dae448b6.png` — rejected.
- `/tmp/codex-clipboard-885d27b3-07dd-4ab6-9bc3-b02dc92b0a01.png` — pending dashboard.

Target: native Android, 402dp-wide reference derived from the 804px-wide source; full-height frames are 1748px high. The first two images are cropped and transparent, so their transparent regions use the same cream surface as the remaining frames. Android retains control of real system bars. Actual implementation viewport/density: unavailable. Implementation screenshot path: unavailable; ADB lists no connected devices and no AVD/system image is installed. No source/rendered normalization, overlay, full-view comparison, or focused-region comparison is possible yet.

States implemented: step 2 (no step 1); front/back source dialog, live camera, real upload rows; step 3 camera/current-session selfie; step 4 PDF; review submission; backend pending/approved/rejected; restricted pending home.

Required fidelity surfaces, awaiting rendered evidence:

- Typography: native sans-serif; 24sp extra-bold main headings, 15sp/21sp body copy, 17sp bottom actions, reference-sized metadata. Font weight, wrapping and large-font layout require device comparison.
- Layout: 24dp margins, 4dp header progress, rounded white cards, dashed upload borders, 260dp circular camera with brown outline, 96dp outcome icon circles, anchored bottom actions, four-tab pending dashboard. Exact geometry and safe-area behavior remain unverified.
- Colors: cream `#FAF7F4`, brown `#6A2E00`, ink `#21150E`, muted `#706259`, success `#417653`, amber and red outcome surfaces. Rendered samples and contrast require capture.
- Images: only the user's actual session thumbnails/camera preview are shown. No stock ID/person image is used as evidence. The API currently supplies no draft thumbnail or view count, so the pending dashboard renders real draft titles without inventing the mockup's example property photo.
- Copy/content: owner explicitly approved accurate labels and PDF-only limits. No fabricated phone/M-Pesa/face-match result, 24-hour SLA, Kenya encryption claim, 82% progress or 3× inquiries. The contact row uses the authenticated phone (email fallback), with **View** linking the existing profile because there is no contact-update action in this flow. Ownership is PDF-only, 10 MiB. Rejection reason is server text.

Interaction changes: extra introduction, persistent refresh controls and technical explanations removed. Real failures use a snackbar. The file row resolves stored incomplete confirmations; the bottom action retries the current retained upload. The source dialog keeps both required ID sides reachable without adding permanent page controls. The reference's delete icon is not connected to an invented deletion: tapping a stored file offers explicit replacement. Live selfie capture remains CameraX; reference portraits are not shipped as fake evidence.

Comparison history: replaced the previous explanation-heavy layout from code with the selected references; no rendered comparison has been performed. Functional build/test evidence must not be treated as visual evidence.

Functional verification: final full Android suite passed **38 tests, zero failures/errors/skips**, and `assembleDebug` produced the debug APK. The new pending-owner navigation guard passed. The full suite's mock-WebSocket shutdown timeout was corrected by acknowledging the close handshake in its test listener; no production messaging code was changed.

Findings: [P1, acceptance blocker] native visual capture unavailable. Exact appearance cannot be certified. The missing real draft-image contract and missing document-delete endpoint are explicit API limitations, not a visual pass.

Implementation checklist: connect the development phone, capture each state using disposable evidence, compare at matched pixel density (including header, upload card, circle, summary rows, outcome card, bottom action and pending tabs), test large text/camera permission/picker return/retry, fix P0/P1/P2 differences, repeat capture. `FLAG_SECURE` remains enabled for real evidence; do not disable it on customer documents to obtain screenshots.

final result: blocked

---

## Landlord identity and ownership verification — 14 October 2026

Source visual truth was opened and inspected for all six supplied 804 × 1748 PNG frames:

- `/tmp/codex-clipboard-861c66d1-28ab-4eb1-9467-2684fe86e30f.png` — ID upload.
- `/tmp/codex-clipboard-7924d2f0-b1f2-444d-afad-bab95f8ad967.png` — selfie/live-face capture.
- `/tmp/codex-clipboard-9deb0c56-c5df-45ca-a0fa-5fc293b01ee0.png` — ownership proof.
- `/tmp/codex-clipboard-705f16a2-753a-481b-a989-dd3a0e1560a6.png` — pending review.
- `/tmp/codex-clipboard-9f025b7c-b57b-4b8c-945d-d0a1d7021df9.png` — approved.
- `/tmp/codex-clipboard-1aec3f2b-7a79-41e8-9322-2d131f613ed6.png` — rejected.

Implementation: native Compose in `ui/LandlordVerificationScreen.kt` and `ui/EvidenceCamera.kt`. Implementation screenshot: unavailable. Actual viewport/density: unavailable because no phone/emulator is connected. Density normalization and source-versus-rendered overlay are therefore unavailable. Compilation and unit tests are not visual evidence.

States represented: explanation; ID front/back; live camera/selfie; PDF ownership proof; submission summary; backend pending; backend verified; backend rejected. The supplied phone status and gesture bars were not baked into the native UI; Android owns system chrome.

Required fidelity surfaces:

- Typography: 26sp bold screen headings, 15sp body copy, compact status/metadata labels. Exact font, line wrapping, weight, and large-font behavior remain unverified.
- Layout: 24dp margins, progress/header area, rounded white cards, 52dp bottom actions, circular selfie guide, ID guide, and scroll behavior. Actual aspect ratio/keyboard/safe-inset behavior remains unverified.
- Color: cream `#FAF7F4`, brown `#6A2E00`, ink `#21150E`, muted `#706259`, success green `#417653`. Rendered contrast and color-management differences remain unmeasured.
- Images: no reference portrait/ID was shipped as fake user evidence. The camera or current private in-memory selection supplies the preview. Crop, sharpness, lens behavior, and permission UI require device capture.
- Interaction: camera permission, gallery/PDF picker, replace, retry confirmation, submit, refresh, rejection correction, and verified-to-listing actions are wired. Touch behavior requires a device.

Intentional security/product differences from the source:

- Ownership proof is PDF-only and 10 MiB, per the owner's explicit instruction; the source's JPG/15 MB wording is not used.
- No “encrypted in Kenya” claim appears because storage/encryption geography and KMS are not confirmed.
- No “face aligned”, automatic live-face match, or biometric result appears because no liveness provider is implemented.
- Pending has no 24-hour guarantee and no M-Pesa/face-match claims unsupported by backend state.
- Approved has no “3x inquiries” claim.
- Rejected displays only the server's actual rejection reason; the source's blurry-ID example is not hardcoded.
- Progress is indeterminate and names the current real step; the source's illustrative 82% is not fabricated.

Functional evidence: 37 Android unit/contract tests passed and the debug APK assembled. Backend confirmation/security tests and all backend tests also passed separately. These checks do not establish pixel fidelity.

Required acceptance work: connect the development phone, capture all editable and terminal states at the same viewport, compare full views and focused crops for header/progress/camera/file row/bottom action, test 1.0× and large font, exercise denied camera permission and process restart, fix any P0/P1/P2 findings, then repeat capture.

final result: blocked

---

## Map provider replacement — 14 October 2026

The functional map provider has changed from Google Maps to MapLibre Native 13.6.1/OpenFreeMap with the owner's approval. Existing layout and private-location form flow are retained, with visible map credits and a safe retry. No pixel-identical Google map styling is claimed. The provider's Liberty style replaces Google's cartography.

Final functional check: OpenGL APK assembled, all 33 host tests passed, and manifest inspection confirmed no Google map key, coarse/fine location permissions or Vulkan-only hardware requirement. These checks do not certify native visual behaviour.

Implementation screenshot: unavailable (no ADB-connected device). Native gesture, lifecycle, text/attribution layout, marker visibility and rendering remain device-test gaps. Compilation and local contract tests are not visual evidence. See MAPS_IMPLEMENTATION.md for the manual device checklist.

final result: blocked

---

## Latest splash change — 14 October 2026

Source visual truth: `/tmp/codex-clipboard-92deaf70-bbc6-478a-b496-8383ce5c7f50.png`, 804 × 1748 PNG, opened and inspected. Preserved copy: `app/src/main/res/drawable-nodpi/makao_splash_screen.png`.

Implementation: native Compose `Splash` in `MainActivity.kt`. Implementation screenshot: unavailable. Viewport and device density: unavailable (no ADB-connected device or installed AVD). State: initial Page.SPLASH before its existing 2.5-second/tap continuation. CSS size: not applicable to native Android. Normalization: unavailable without native capture.

Full-view comparison evidence: blocked by missing rendered device screenshot. Focused comparison evidence: likewise blocked; logo/headline spacing and photo crop require capture, not code inspection.

Required fidelity surfaces: typography and copy preserved within the owner's raster artwork (TalkBack description added; independent font scaling is not supported by raster text); spacing/layout uses Fit to preserve full content, pending device comparison; colours remain in the original PNG with brown surround; image copied unchanged without resampling (source/resource SHA-256 hashes match); no duplicate headline or logo layers added. Sharpness, system-bar overlap and transparent-corner appearance remain unverified.

Functional checks: 30 Android unit/contract tests passed and debug APK assembly succeeded. Those checks do not replace rendered evidence.

Comparison history: selected reference opened; plain brown logo/text implementation replaced with supplied artwork. No native capture available; no visual pass claimed.

Findings: acceptance blocker is missing native visual evidence. Potential bands on differing aspect ratios and raster-text scaling need device review. Existing navigation remains separate from presentation.

Implementation checklist: capture splash on connected phone, normalize to reference ratio/density, compare full view and focused logo/headline/photo together, test automatic and early-tap continuation, check TalkBack and font scaling, fix any P0/P1/P2 findings and repeat capture.

final result: blocked

---

Source visual truth: `/tmp/codex-clipboard-d8537059-9b6f-4fe4-ad12-4aaa70f17679.png` (3488 × 2108 pixels).

Implementation: native Android Jetpack Compose screens in `ui/OnboardingScreens.kt`.
Implementation screenshot path: unavailable.
Viewport: responsive native screen, design measurements normalized to a 390dp-wide viewport.
State: Welcome; Homes walkthrough; Peace of mind walkthrough; Renter selected.
Density normalization: source frame widths are approximately 824px, normalized to 390dp. Device pixel density cannot be confirmed without an attached device.

## Evidence and limits

The source reference was inspected. Its brown (#573B2D), cream (#FAF7F1), selected-card fill (#EDE2D7), photo regions, wording and layout hierarchy are reflected in the implementation. The three original photo regions are decoded from the board rather than replaced with different photos.

No device is attached to adb; the installed SDK has no emulator system image/AVD. There is therefore no rendered implementation screenshot and no valid side-by-side comparison. Build success is not evidence of pixel fidelity. No QA pass is claimed.

## Required fidelity surfaces

- Fonts/typography: native sans-serif with measured 28sp bold headings, 15sp body and 12–14sp small text. Actual wrapping and font rendering still need device comparison.
- Spacing/layout: margins, cards, image proportions and sequence implemented; screen heights vary in the reference, so content scrolls on shorter devices. Actual display requires verification.
- Colors: key fills sampled from the source. Borders/text contrast still require rendered review.
- Image quality: exact original photo regions used; crop boundaries/rounded corners need visual device confirmation.
- Copy: reference copied, except the role-storage note correctly says "when you create your account" to match the inspected backend.

## Comparison history

Initial source review found three missing walkthrough screens and a different role-card design. Both were implemented. Post-change visual evidence remains unavailable; this does not constitute a passed visual iteration.

Full-view comparison evidence: blocked by missing rendered Android screenshot.
Focused comparison evidence: blocked for the same reason.

## Implementation checklist

- Run on an emulator/phone; capture each of the four states at a known width/density.
- Compare source and captures together, including the photo edges, heading wraps, vertical spacing and selected-role state.
- Exercise Continue/Next/Skip/Back and role selection; test scrolling and increased font size.
- Fix visible P0/P1/P2 differences and record new screenshots before marking passed.

final result: blocked

---

# Landlord workspace — 13 October 2026

Source visual truth: `/tmp/codex-clipboard-4ed54047-05e1-4d4a-868f-a5802a79105d.png`, attached landlord board, reported dimensions 3680 × 10726 pixels (inline preview 703 × 2048).

Implementation: native Compose workspace at `/home/hp/Desktop/makao-hub-frontend/app/src/main/java/com/makaohub/android/ui/LandlordScreens.kt`.
Implementation screenshot path: unavailable.
Viewport: intended responsive native phone content; actual viewport/density unavailable.
Implementation pixel dimensions: unavailable; CSS size is not applicable to native Compose.
Density normalization: not performed without a source crop and rendered device screenshot.
States: home, draft, media/delete, private location, preview/checks, listings, applications/decisions, chat, outgoing payments, disabled payout destination, test plans/boost.

## Evidence and blockers

The inline landlord board was available for structural reference. Its original temporary file is no longer readable. ADB reports no connected phone, and no installed emulator image/AVD was found. No native screen capture or side-by-side comparison can therefore be produced. Compilation and local HTTP/WebSocket contract checks are not visual evidence.

Full-view comparison evidence: unavailable; blocked.
Focused region comparison evidence: unavailable; blocked, not unnecessary.

## Required fidelity surfaces

- Fonts/typography: bold native sans headings and compact body labels implemented; matching font, wrapping, optical weights and scaled-text behavior unverified.
- Spacing/layout: four-step flow, rounded cards, five tabs, scrolling and native insets implemented; actual device proportions/touch layout unverified.
- Colors/tokens: existing Makao brown/cream/sand palette retained; rendered contrast and match to this board not measured.
- Image quality/assets: only actual user-selected media is previewed, not copied sample property imagery presented as real data. Existing-media thumbnails cannot be obtained from the current metadata API. Rendered crop/sharpness unverified.
- Copy/content: headings follow the board; truthful messages replace unsupported staff moderation, payout changes, catalogue/entitlement, rejection-reason and renter-summary features. These are outstanding contract differences, not accepted design-fidelity passes.

## Findings / open constraints

- Acceptance blocker: missing device screenshots and inaccessible original source file prevent visual QA. Reattach the original design and connect a phone/emulator, then capture matching states together for comparison.
- Functional/design gap: maps/search, existing media previews and cover/reorder, deposits/amenities, moderation, renter summaries/rejection reasons, incoming payout ledgers and secure destination changes need contracts/integration. They are explicitly unavailable in this slice, not silently mocked.

## Comparison history

No visual comparison iteration completed. Code/build/test troubleshooting does not count as a visual iteration. No P0/P1/P2 visual findings or fixes are asserted from source code alone.

## Implementation checklist

1. Reattach the original board or supply readable individual frames.
2. Connect a phone/emulator, record its viewport/density, and capture actual landlord states.
3. Compare the source/capture pairs at matching content/state/density, with focused typography, media, form and bottom-nav crops.
4. Verify keyboard, Back, loading/error/empty states, delete/decision dialogs, background chat reconnection and increased font scale.
5. Resolve the explicitly listed API gaps and any visible P0/P1/P2 mismatches; repeat comparison before accepting fidelity.

final result: blocked
# Landlord workspace visual alignment — 21 October 2026

Source visual truth: `/tmp/codex-clipboard-a12f5b79-7eef-49cd-9a9e-ef086e8e4c42.png`, the supplied landlord workspace board.

Implementation changes: `ui/LandlordScreens.kt` now uses the board's concise product copy for the property editor, media, private location, preview/checks, applications, payments, payout destination and plan/boost surfaces. Technical API explanations were removed from customer-facing cards; only server-backed listing, moderation, payment and privacy states remain. Existing owner data still supplies listing titles, application references, dates and payment records; no sample landlord or property data was added.

The publication state flow remains server-authoritative: a draft is submitted for staff review, waits in `PENDING`, can be revised after `REJECTED`, and can be published only after `APPROVED`. The database approval constraint was not changed.

Rendered device comparison is still blocked because this environment has no configured Android emulator or permitted device capture. The Android source was rebuilt previously with the same state-flow changes and passed 69 unit/contract tests; after this copy-only update, the Gradle runner could not start because the sandbox could not determine a wildcard IP for Gradle's file-lock service. Android Studio should run the final compile on the user's machine.

Required manual check: open the 15 landlord board states in Android Studio at the reference phone size, compare heading wraps, card spacing, bottom navigation and CTA placement, then capture the result before claiming pixel fidelity.

final result: blocked

---

# Landlord structural layout pass — 21 October 2026

This section supersedes the preceding copy-only pass for the current landlord implementation.

## Source and implementation evidence

- Source visual truth: `/tmp/codex-clipboard-bea7adbc-0441-40f7-971f-9164fd0c51ac.png`, 17960 × 2854 pixels.
- Inspected source crops: `/tmp/makao-landlord-reference-start.png`, `/tmp/makao-landlord-reference-middle.png`, `/tmp/makao-landlord-reference-end.png`.
- Implementation: `/home/hp/Desktop/makao-hub-frontend/app/src/main/java/com/makaohub/android/ui/LandlordScreens.kt`.
- Implementation screenshot: unavailable. No configured native Android emulator. No phone installation/capture performed, respecting the owner's earlier decision to run through Android Studio.
- Intended inspection viewport: 402dp width; added 360dp / 1.3 font-scale debug preview. Actual device pixel density and dimensions are not measured. CSS viewport is not applicable to this native app.
- Density normalization: source crops enlarged for legibility only; no pixel-accurate comparison possible without a rendered implementation.
- States covered by source/layout work: authenticated home, property form, media/confirmation, private map, preview, listing/review states, application/review/decision, payment history and disabled payout settings.
- Full-view combined comparison: unavailable; blocked.
- Focused combined comparisons: unavailable; still required for forms, tabs, typography and images.

## Implementation changes, not visual acceptance claims

- Shared page header, white card surfaces, label/value form cards, larger type and consistent spacing now replace the former outlined-field layout.
- An inset white tab bar uses the selected dot treatment instead of the standard icon navigation bar.
- Dashboard uses actual records in summary, next-step and business cards. Scroll resets at page transitions.
- Media selection, delete confirmation and application decisions have separate layouts rather than being folded into unrelated screens.
- Successful review submission goes to the saved moderation state; approval and publication remain separate server operations.
- Errors use a snackbar so a failed request does not permanently alter the card layout.
- Debug-only previews reuse the production components without account access or network traffic.

## Required fidelity surfaces

| Surface | Current evidence and remaining check |
| --- | --- |
| Typography | Source crop inspected; heading/body sizes and line heights adjusted. Actual font metrics, wrapping and scaled text require native rendering. |
| Spacing/layout | Source card/nav/form grouping implemented. Above-the-fold density, safe insets and touch behavior remain unverified visually. |
| Colors | Existing brown/cream palette retained with semantic success/error/warning colors. Native rendered contrast and exact reference match unverified. |
| Images | Only real session-selected property images are shown. Existing-media signed read URLs are unavailable in the current API. No stock image is passed off as a user's property. This is still a major visible gap. |
| Copy/content | Actual owner records replace mock names and counters. Unsupported deposit/amenities, applicant summaries, rejection reasons, payout/catalogue/entitlement APIs remain gaps, not fake values or accepted matches. |

## Checks and comparison history

The reference was opened at a legible scale. Structural edits compile and local contract/navigation tests were run;
those checks are not native visual comparison. No completed screenshot comparison iteration is claimed.
No source-versus-render severity finding is invented without both images.

Final build verification: `testDebugUnitTest assembleDebug` passed on the final source, 73 tests with no
failures or errors. `git diff --check` passed. The APK was built but not installed on the user's phone.

Current blockers: missing native rendered evidence and the unresolved API-dependent surfaces described in
`/home/hp/Desktop/makao-hub-frontend/docs/landlord-layout-alignment.md`.

## Next verification steps

1. In Android Studio, select the debug variant and inspect `LandlordLayoutPreviews.kt` at both supplied sizes.
2. Run the rebuilt app normally. Capture the real landlord home, form, media and decision states.
3. Align native screenshots and source crops by content width/density; put each pair in one comparison image.
4. Check typography, above-fold card proportions, tab behavior, keyboard/Back, loading/errors, long content and larger text.
5. Resolve supported API gaps without exposing private evidence or inventing data, then repeat the visual comparison.

final result: blocked
