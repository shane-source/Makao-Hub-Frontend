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
