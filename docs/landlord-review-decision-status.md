# Landlord review decision status

## Scope and decisions

The landlord app now refreshes a submitted verification while its pending
dashboard or verification outcome is visible. The backend remains the only
authority that can change `PENDING` to `VERIFIED` or `REJECTED`; the client never
derives approval from a document acknowledgement. No schema, notification
channel, upload retry or verification rule was added.

`RECOMMENDED`: a 15-second foreground-only refresh interval is used until the
notification channel is decided. It avoids background work and stops when the
screen leaves the resumed state or the backend returns a final outcome.

## Changed files and methods

### `app/src/main/java/com/makaohub/android/LandlordViewModel.kt`

- `refreshReviewStatus` performs only the existing owner-scoped verification GET.
  It runs only for a locally pending verification on `HOME` or `VERIFICATION`,
  and only when no other ViewModel operation is busy. It assigns exactly the
  server response and opens the existing outcome screen only for `VERIFIED` or
  `REJECTED`. Removing the guards can race document selection or poll unrelated
  screens; locally constructing a final status would let the client forge a
  decision.
- The shared request failure handling now describes an unavailable document
  checker only while a document confirmation is actually pending. A failed
  status read preserves the last server-backed `PENDING` value and uses the
  ordinary connection message. Removing this distinction would misreport a
  harmless refresh failure as a malware-scanner failure.

### `app/src/main/java/com/makaohub/android/ui/LandlordScreens.kt`

- `LandlordScreens` starts the refresh loop through `repeatOnLifecycle(RESUMED)`
  only while the model status is `PENDING`. The status is part of the effect key,
  so a final server decision cancels further reads. Removing lifecycle scoping
  would continue work while the app is in the background; removing the status
  key would keep polling after the decision.

### `app/src/test/java/com/makaohub/android/LandlordVerificationFlowTest.kt`

- `pendingDashboard` constructs a synthetic pending owner dashboard using the
  real repository/ViewModel contract.
- The approval test proves the server response opens the existing approved
  outcome and that a terminal state is not polled again.
- The rejection test proves the server's reason reaches the existing rejected
  outcome without logging out or changing the account.
- The acknowledgement/failure test proves a pending response and a later 503
  cannot be interpreted as approval and do not replace the pending dashboard.

## Security-critical sequence

1. The existing authenticated owner endpoint determines which landlord record
   can be read; the app supplies no user ID or desired state.
2. The app stores only the returned status, review time and rejection reason.
3. `PENDING` remains `PENDING` after document acknowledgement or network failure.
4. Only `VERIFIED` or `REJECTED` returned by the backend selects a final outcome.
5. The loop stops outside the foreground and after a final outcome, limiting
   unnecessary authenticated requests.

## Risks and verification

- A provider or network outage delays display of the decision; it never creates
  a final state. Push delivery is TBC under the notification-channel decision.
- Run the debug app from Android Studio, leave a synthetic/test landlord on the
  pending dashboard, then make one authorised reviewer decision and verify the
  corresponding outcome appears within about 15 seconds.
- Kenyan counsel still needs to confirm identity-document review, retention and
  rejection-reason disclosure. No M-Pesa, Safaricom or custody behavior changed.
- Suggested next task: apply the same server-decision refresh contract to the
  Sublessor and Renter pending dashboards after confirming the desired cadence.
