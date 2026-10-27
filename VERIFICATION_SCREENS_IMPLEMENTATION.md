# Verification screens implementation — 25 October 2026

## Task, assumptions and undecided items

The thirteen supplied mobile references define role introductions, identity evidence, role-specific documents, review and outcomes for Renter, Landlord and Sublessor. This change applies that presentation to the existing native Android flows without changing the backend verification model or creating sample identity data.

Assumptions used:

- The pending, approved and rejected cards in each reference are alternative states. The app renders only the status returned for the authenticated active role.
- The displayed filenames, 64% progress and rejection explanations are examples. Production values come from the real upload and server response.
- The shared identity sequence is ID front, ID back and a fresh camera selfie. Landlord ownership proof and Sublessor rental agreement follow as role-specific evidence.

Undecided (`TBC`):

- Identity/face-match provider, liveness requirements and provider-specific camera SDK.
- Managed KMS, evidence retention/deletion policy and whether users may securely read back already-uploaded evidence.
- Notification channels and review service-level targets.

## Design

Data model: unchanged. Each document keeps its server-issued ID, role-scoped document type, upload status, security-check status and server response. No identity value or role decision is stored in Compose state.

State transitions: an unverified role opens its checklist, completes backend-confirmed evidence, reviews the checklist and submits. `PENDING`, `VERIFIED` and `REJECTED` remain server states. A rejected role may replace evidence and resubmit; client copy alone never changes verification status.

API contract: unchanged. Existing authenticated role repositories create uploads, send bytes to private storage, confirm saved evidence, submit the role verification and refresh the current role's state. The client supplies no account/role owner identifier to authorize access.

Failure modes: upload progress disables continuation; interrupted or unconfirmed uploads retain a retry/confirmation action; missing required evidence disables submit; camera cancellation returns without submission; server rejection displays only the server-provided reason or neutral replacement guidance.

## Changed files

- `app/src/main/java/com/makaohub/android/ui/IdDocumentStep.kt`
  - Adds the shared checklist, selfie, role-document, review and outcome composables and retains the shared ID front/back component.
  - Sharing these components prevents role-specific privacy wording and disabled-state behavior from drifting. Removing them would return three separate flows with inconsistent evidence handling.
  - `VerificationReviewSummary` requires a complete-evidence flag before enabling submit. This prevents navigation/state restoration from exposing an actionable submit button before every server-confirmed document is ready.
  - `VerificationOutcomeScreen` maps only the current server state. It never derives approval from uploaded filenames, local progress or UI labels.
- `app/src/main/java/com/makaohub/android/ui/RenterScreens.kt`
  - `RenterVerification` now starts with the Renter checklist, uses the shared three-part identity sequence, adds an explicit review step and renders the current server outcome.
  - It continues using the Renter repository's role-scoped upload, confirmation, submit and refresh methods. Removing this wiring would leave the references disconnected from the secure API.
- `app/src/main/java/com/makaohub/android/ui/LandlordVerificationScreen.kt`
  - `LandlordVerificationScreen` now adds the role introduction and reuses the shared identity, ownership-proof, review and outcome presentation.
  - Ownership evidence remains PDF-only and required before review submission. The upload confirmation/retry path remains available for interrupted storage confirmation.
  - The existing verification repository remains authoritative; no listing or verification permission is inferred locally.
- `app/src/main/java/com/makaohub/android/ui/SublessorScreens.kt`
  - `SublessorVerification` now adds the role introduction and reuses the shared identity, rental-agreement, review and outcome presentation.
  - The agreement remains private, PDF-only and separate from listing/discovery data. Removing this boundary would risk exposing tenancy evidence to roommate discovery.
- `design-qa.md`
  - Records the visual sources, intentional security differences, functional evidence and blocked native screenshot comparison. Removing it would hide the remaining acceptance gap.

## Security-critical behavior

1. The UI reads status from the authenticated role verification response; this prevents a forged local “verified” label from granting role privileges.
2. Every required evidence item must be server-confirmed `READY` with its security check before review submission becomes actionable; this prevents incomplete or unchecked uploads from being treated as complete.
3. Uploads continue through private role repositories and private storage confirmation; marketplace users never receive evidence URLs or bytes.
4. `FLAG_SECURE` remains enabled for verification routes; this reduces exposure through task-switcher previews and ordinary screenshots.
5. No dummy preview endpoint was added. Without an explicit ownership-checked read contract, previewing stored evidence would create an IDOR and sensitive-document disclosure risk.
6. Live selfie capture remains camera-only. Allowing gallery substitution before the liveness/provider contract is decided could weaken impersonation resistance.

## Verification

- `./gradlew -Pkotlin.compiler.execution.strategy=in-process testDebugUnitTest assembleDebug` passed.
- 75 tests passed with zero failures, errors or skips.
- The debug APK assembled successfully.
- `git diff --check` passed.
- Native visual comparison remains blocked: no attached Android device or configured AVD was available.

## Remaining risks, tests and confirmations

Risks: visual fidelity and accessibility are not device-verified; the final face/liveness provider is undecided; evidence retention, deletion and application-layer encryption depend on the KMS/provider decision; reviewer authorization/audit behavior must stay enforced on the backend.

Test next: run all three roles with synthetic files across empty, uploading, interrupted, confirmed, pending, verified and rejected states; test process backgrounding, offline retry, rotation, large text and TalkBack; confirm a role cannot read or submit another role's documents.

Confirm with a Kenyan lawyer: necessity, consent, retention, deletion and staff-access language for identity, face, ownership and tenancy evidence under the Kenya Data Protection Act, 2019. Confirm with the selected identity provider: liveness, callback authentication, replay protection, evidence minimization and regional processing/storage.

Suggested next task: configure a synthetic Android emulator test account and complete the non-sensitive visual/accessibility comparison recorded in `design-qa.md`.
