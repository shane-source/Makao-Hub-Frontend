# Landlord layout alignment — 21 October 2026

## Scope and assumptions

Implement the landlord UI from `/tmp/codex-clipboard-bea7adbc-0441-40f7-971f-9164fd0c51ac.png`
inside the existing native Android app. This is not a new website or a static mock.
The reference contains fifteen screens; some show alternative states, not simultaneous outcomes.
Production content remains sourced from the authenticated owner's records. No sample properties,
names, payment amounts, ID evidence or approval states are inserted into the runtime workspace.

The screenshot supplies visual direction, not new pricing, permissions or financial rules.
Existing APIs and role checks remain in place. No backend, database, migration, authentication,
verification or payment-provider configuration is changed by this layout pass.

## Design and contracts

- Reuse Compose and existing brown/cream tokens, buttons and Material icons. No new dependencies.
- Replace standard outlined fields with the reference's white label/value cards.
- Use an inset white bottom bar with a selected dot, and the reference's page/card hierarchy.
- Keep persistent navigation on workspace pages; creation and decision steps use Back.
- Add local `MEDIA_MANAGE` and `APPLICATION_DECISION` navigation states. These are not server states.
- Media deletion and application decisions continue through authenticated repository methods.
- Open a confirmation page without sending a mutation. Render successful outcomes only from server responses.
- Successful listing-review submission navigates to the saved review status. Publishing remains separate.
- Errors are shown in a snackbar rather than inserting another card into each design.

Versions used from the existing project: Compose BOM `2026.02.01`, Activity Compose `1.13.0`,
Lifecycle `2.11.0`, Retrofit `3.0.0`; test JUnit `4.13.2`, MockWebServer `4.12.0`,
coroutines-test `1.10.2`. Compile/testing uses existing Java 21 and Gradle 9.6.0.

## Changed files and functions

### `/home/hp/Desktop/makao-hub-frontend/app/src/main/java/com/makaohub/android/ui/LandlordScreens.kt`

This file composes the landlord screens and connects their actions to `LandlordViewModel`.
Removing it breaks the landlord workspace route from the authenticated app.

| Function | What it does and why | Connection / effect of removing it |
| --- | --- | --- |
| `LandlordWorkspace` | Selects the page, applies native safe/keyboard insets, resets scroll per page, displays notices without moving the layout, and uses the inset tab bar only on workspace pages. | Hosts every page and existing verification/privacy guards; removal breaks navigation and error visibility. |
| `parentOf` | Defines Back destinations for media confirmation and application decision. | Keeps these local steps in their own workflow instead of returning to unrelated pages. |
| `OwnerHeader` | Draws the back arrow and brand using the reference proportions. | Shared by production and previews; removal loses consistent Back/brand placement. |
| `OwnerBottomBar` | Presents five selectable tabs with a selected dot and accessible tab semantics. | Calls `model.go`; removing semantics harms accessibility, removing the bar breaks workspace navigation. |
| `OwnerHeading` | Uses brown 28sp headings, explicit line height and consistent subtitle gaps. | Shared page hierarchy; removal returns inconsistent typography. |
| `OwnerCard` | Provides optional titles, white fill, border, rounded corners and spacing. | Wraps records and summaries; removal loses the reference's card grouping. |
| `Small` | Applies the same 14sp/21sp body type throughout landlord cards. | Prevents inconsistent compact text. |
| `Notice` | Uses concise colored state blocks with semantic foreground colors. | Used by actual requirements and outcomes; removal hides important state/privacy information. |
| `Badge` | Shows a small state dot and readable server status. | Does not manufacture verification; removal hides record state. |
| `CardLink` | Uses full-width text actions with the reference's title/detail arrangement. | Connects summary cards to existing workflows; removal makes them inert. |
| `OwnerTextAction` | Reuses one full-width, minimum-height secondary action. | Used by media, decisions and Back/cancel actions; removal duplicates styling and loses consistent targets. |
| `OwnerInput` | Puts the label and editable value inside a white card using native `BasicTextField`, retaining labels, disabled states and keyboard types. | Calls existing form setters; removal breaks input and semantics. |
| `OwnerHome` | Passes authenticated data/actions to the shared dashboard layout. | Separates visual composition from the live model without copying production data into previews. |
| `OwnerDashboard` | Builds the heading, live counts, next steps and business cards. | Reused by preview; removing it breaks the home layout. |
| `OwnerListings` | Groups actual listing state/rent/availability with relevant text actions; shows creation on the empty state. | Routes to existing management/pause/boost operations; removal loses listing management. |
| `PropertyEditor` | Uses card inputs and a matching property-type picker with the supported values. | Preserves existing field validation/save actions; removal prevents draft editing. |
| `ListingMedia` | Shows one selected photo, horizontal photo selectors, video card and selected-photo management. | Uses real media records and session photo URIs; removing it prevents media selection/upload/confirmation. |
| `ManageListingMedia` | Separates delete confirmation and Keep action, as in the reference. | Calls deletion only on confirmation; removal loses the explicit safeguard. |
| `PrivateLocation` | Keeps the map but hides developer-only setup explanations. | Does not hide attribution or change storage/access checks; removal prevents private-pin entry. |
| `ListingPreview` | Places the photo inside the summary card and groups real readiness values. | Submits via the existing review API, not publication; removal loses the pre-review summary. |
| `PublicationChecks` | Groups the approved state and Publish/Resume inside the reference's approval card. | Keeps decisions driven by server review status; removal hides publication requirements. |
| `ApplicationReview` | Separates application summary, progress, private-chat action and decision navigation. | Uses only supplied application details; removal prevents hosts from reviewing their applications. |
| `ApplicationDecision` | Shows separate approval/rejection confirmations and the actual saved outcome. | Calls existing decision actions; removing it loses the new confirmation screen. |
| `PaymentHistory` | Uses the reference title; labels test records only in mock-enabled builds. | Displays server records without changing financial calculations. |
| `PayoutDestination` | Keeps the reference card layout but disables unavailable settings honestly. | Does not pretend a destination is saved or absent; removal loses the destination route. |

`LocalPhoto` remains the existing shared decoder, also used by Sublessor. No new remote URL handling
or photo caching was added. A saved media record without a current session URI cannot yet display its photo.

### `/home/hp/Desktop/makao-hub-frontend/app/src/main/java/com/makaohub/android/LandlordViewModel.kt`

The class continues to coordinate owner requests and transient state; it does not grant authorization.
`OwnerScreen` adds two local page values. `selectedMediaId` identifies the media being managed.

- `selectMedia`: ignores selection while busy and accepts only an ID already in the loaded media list.
  Without this check the UI could select a nonexistent record. The backend still checks ownership.
- `manageMedia`: applies the same guard, stores the ID, then changes the local screen only.
  No deletion is sent here. Without that separation an ordinary navigation click could be destructive.
- `resetEditor`: now clears the selected-media ID when switching drafts. Without it, selection from
  one listing would survive into another listing's editor.
- `removeMedia`: retains the authenticated DELETE and now coordinates the confirmation page:
  1. Await `repository.remove(selected.id, id)`. An error exits through the existing error handler;
     the selected record stays visible and retryable. No false success is displayed.
  2. Invalidate cached listing review because required media may have changed.
  3. Remove the session image reference and the successfully deleted record from local media.
     This occurs only after server confirmation, not optimistically before DELETE.
  4. Clear selection and return from `MEDIA_MANAGE` to `MEDIA`. A failed metadata refresh must not
     invite a second deletion of an already-deleted record.
  5. Clear pending upload state only if it belongs to this deleted item.
  6. Fetch remaining media and show the existing success notice. Refresh errors use normal error handling.
- `submitListingReview`: enters `CHECKS` only after successful submission/status retrieval. Removing
  this transition leaves the user on the old submission form after a successful request.
- `clearPrivateState`: also clears selected media on workspace disposal/logout. Without it, this
  transient selection could leak into a later session. Existing evidence/message cleanup stays intact.

These guards prevent accidental mutation and misleading state; they are not substitutes for backend
role/ownership checks. No entitlements, role decisions, prices or payout destinations are trusted from UI input.

### `/home/hp/Desktop/makao-hub-frontend/app/src/debug/java/com/makaohub/android/ui/LandlordLayoutPreviews.kt`

Debug-only previews call the same components, with explicitly synthetic data and inert actions.
They instantiate no account/model, make no network requests and are excluded from release builds.
Removing this file does not affect production; it removes quick Android Studio inspection.

- `LandlordDashboardPreview`: full dashboard at 402dp and a 360dp/1.3 font-scale variant. Checks the
  same card/tab components without needing an authenticated account.
- `LandlordFieldsPreview`: shows the shared white input cards and heading in a native preview.
  It is a component preview, not a complete copy of the property editor.

### `/home/hp/Desktop/makao-hub-frontend/app/src/test/java/com/makaohub/android/LandlordVerificationFlowTest.kt`

- `listingIsSubmittedReviewedAndOnlyThenPublished`: adds an assertion that successful review submission
  opens `CHECKS`. Existing pre-approval publication protections remain tested.
- `mediaManagementNeedsASelectedRecordAndDeletionFailureKeepsItRetryable`: checks unknown selection,
  navigation without DELETE, failed DELETE preserving the record, and successful DELETE followed by
  failed refresh returning safely to media. Removing it loses coverage for destructive-action navigation.
- `openingDecisionDoesNotApproveAndServerFailureNeverShowsSuccess`: checks navigation makes no request
  and a rejected approval request does not produce an approved UI state. Removing it loses this failure check.

All tests use a local mock HTTP server and synthetic tokens/data. They do not mutate real accounts or files.

## Unresolved design/API differences

These remain gaps, not completed features or accepted visual matches:

- Landlord deposits and additional amenities are not present in the current listing write contract.
- Existing uploaded-media thumbnails, replacement/reordering and room labels need supported owner-media APIs.
- Landlord application responses lack applicant name/employment/income/introduction; the rejection endpoint
  has no reason body. Do not expose Renter ID documents to fill these gaps.
- Payout destination changes, incoming payout breakdowns, plan catalogues and plan/boost entitlements
  are not supplied by the present APIs. Mock receipts must not be called active subscriptions/promotions.
- Physical device viewport, font density and reference font identity are unverified.

No existing data is migrated to guess any of these values. RECOMMENDED next implementation work is
to agree the missing API contracts before adding corresponding editable controls.

## Verification

Run from `/home/hp/Desktop/makao-hub-frontend`:

```bash
JAVA_HOME=/usr/lib/jvm/temurin-21-jdk-amd64 ./gradlew --offline --no-daemon -Pkotlin.compiler.execution.strategy=in-process testDebugUnitTest assembleDebug
```

The in-process compiler avoids writing to an unavailable Kotlin daemon directory; it is not a persisted
project configuration change. In Android Studio select the debug variant, open the preview file in Split
or Design, and Run the app normally to update the installed version. No device installation is performed here.

Final check on 21 October 2026: `testDebugUnitTest assembleDebug` succeeded after the last code change;
73 tests, zero failures/errors. `git diff --check` passed. Native pixel comparison remains blocked.

Manual checks: Home at 402dp/360dp; font scaling; keyboard/Back; every tab; draft editing; media failure/retry;
Keep versus confirmed deletion; submit-review status; approval/rejection failure; empty/live records; private
map visibility; TEST labels. Compare matching native screenshots to reference crops before accepting fidelity.

Risks: native rendering remains unverified and the API gaps above prevent all fifteen frames matching fully.
Provider/legal confirmation: no new requirement introduced by layout changes; existing production custody,
payment, document-retention and identity-provider decisions remain unresolved and require the relevant
provider and Kenyan legal review before launch. Suggested next task: native screenshot comparison, then
the owner-media read contract so previously uploaded property photographs can be displayed accurately.
