# Listing moderation flow

## Scope, assumptions and state transitions

This change connects the existing Android Landlord and Sublessor listing screens
to the backend moderation endpoints introduced with the staff workspace. It does
not alter approval authority, completeness rules or database state. No migration
or dependency was added.

The server-backed transition is:

1. `DRAFT` plus unsubmitted `PENDING` moderation can be edited.
2. `submit-review` records `moderation_submitted_at`; the listing remains hidden.
3. Staff records `APPROVED` or `REJECTED` through the existing permissioned API.
4. Only an `APPROVED` saved version can call the existing publish/resume endpoint.
5. A rejected owner starts revision, edits the draft and submits the new version.
6. Details, media or location changes invalidate an earlier approval in the
   existing database trigger; local cached review state is cleared immediately.

Unknown prices, payments, boosts and moderation rules remain TBC and are not
hardcoded. A network failure preserves the last server-backed state and cannot
create approval.

## Changed files and methods

### `app/src/main/java/com/makaohub/android/data/LandlordRepository.kt`

- `OwnerListingReview` is the read model for the existing owner review endpoint.
  `SerializedName` maps its SQL-backed snake-case response without changing the
  server contract. It carries state, reason and timestamps only; it contains no
  private staff or identity-document data.
- `review`, `submitReview` and `reviseReview` use the authenticated Landlord
  session and never accept an owner ID. Mutations are followed by a fresh owned
  GET, so the UI never treats HTTP success alone as the resulting state.

### `app/src/main/java/com/makaohub/android/data/SublessorRepository.kt`

- `review`, `submitReview` and `reviseReview` mirror the same flow under the
  authenticated Sublessor role. Removing the role-specific routes would either
  recreate direct publication or risk crossing listing ownership boundaries.

### `app/src/main/java/com/makaohub/android/data/MakaoApi.kt`

- Adds the already-existing owner review, submit and revise endpoints for both
  roles. Mutation methods return `Response<Unit>` so the repositories can reject
  any non-success status before reading the authoritative review state.

### `app/src/main/java/com/makaohub/android/LandlordViewModel.kt`

- `listingReview` stores only the selected listing's server response.
- `manage` loads the owned review before choosing the editor or checks screen.
- `go(CHECKS)` reloads moderation, confirmed media and private owner location so
  eligibility is never based on stale screen data.
- Successful detail, media and location changes clear `listingReview` because the
  database invalidates approval for a changed version.
- `submitListingReview` submits and retains the refreshed `PENDING` result.
- `reviseListingReview` uses the server revision transition, refreshes the owned
  listing and opens its editor.
- `listingAction` refuses a local publish attempt unless the last server response
  is `APPROVED`, then still relies on the backend/database guard. It refreshes the
  review and listing after a successful transition. Removing the local guard
  recreates noisy constraint failures; removing the server guard would permit a
  modified client to bypass moderation.
- `clearPrivateState` now clears moderation with the rest of the account session.

### `app/src/main/java/com/makaohub/android/SublessorViewModel.kt`

- Adds the equivalent review state, management routing, refresh, submit, revise,
  approval check and session clearing for shared listings. Existing household,
  media and location writes clear cached approval. Role-specific verification and
  rental-agreement requirements remain separate from Landlord verification.

### `app/src/main/java/com/makaohub/android/ui/LandlordScreens.kt`

- Draft cards open the server-backed moderation state. Paused listings no longer
  call resume before checking approval.
- Preview text now states the real staff-review requirement.
- Publication checks render only the saved states: not submitted, under review,
  rejected with its owner-visible reason, approved, or published. Submit requires
  a verified role plus locally refreshed confirmed image and location; the backend
  repeats all checks. Publish/resume is offered only for `APPROVED`.

### `app/src/main/java/com/makaohub/android/ui/SublessorScreens.kt`

- Preview submits rather than publishes. The states screen uses the same saved
  moderation outcomes and exposes refresh, revision and post-approval publication
  without adding unrelated information.

### Tests

- `LandlordVerificationFlowTest.listingIsSubmittedReviewedAndOnlyThenPublished`
  exercises the real Retrofit/ViewModel route sequence and proves an unapproved
  local publish creates no request.
- `SublessorContractTest.sharedListingUsesTheSameServerModerationContract` proves
  snake-case state/reason mapping and the role-specific revise/submit routes.

## Security-critical sequence

The JWT selects the role; the backend derives the owner; the submit endpoint locks
the owned record and repeats verification/media/location checks; staff approval is
permissioned and audited; the Android app reads the resulting owned record; the
database trigger remains the final publication guard. This prevents role spoofing,
foreign-listing changes, client-forged approval and publishing a changed version.

## Remaining risks and verification

- Review refresh is user-triggered; the notification channel is still TBC. An
  outage delays display but cannot approve or publish a listing.
- Run both apps from Android Studio with test accounts: submit a complete draft,
  confirm direct publication is absent, approve/reject through the assigned staff
  role, refresh, then publish or revise as shown.
- Kenyan counsel should confirm listing moderation reasons, retention and owner
  disclosure. Safaricom/Tende Pay must still confirm payment behavior separately;
  no money or custody logic changed.
- Suggested next task: add a non-sensitive moderation summary to the owner listing
  collection response if product design requires every card to show review state
  without one owned review request per opened listing.
