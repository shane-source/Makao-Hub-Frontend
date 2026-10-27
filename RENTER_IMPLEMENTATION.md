# Renter workspace implementation — 24 October 2026

## Task and decisions

The supplied 34 mobile references describe one Renter journey: discover and save homes, inspect a listing, buy separately-scoped access, apply, book, inspect payment history, confirm move-in, and report problems. The implementation keeps those journeys in the existing native Android workspace and uses the existing Spring services rather than adding screenshot-only routes or sample records.

Assumptions used:

- Search dates mean “a listing with a known `available_from` date on or before this date.” A null availability date is not guessed to mean “available now.”
- Listing reports are text-only in this slice. The existing audited report service masks contact attempts, rate-limits reporters, and accepts only published, approved listings.
- Unlock prices and booking totals come from the server. The KSh 50/100/200/91,000 values in the references are illustrative and are not product defaults.
- `RECOMMENDED`: keep mock-payment controls debug-only until the regulated custody arrangement and provider transitions are approved.

Undecided or blocked (`TBC`):

- Application income, employer and supporting-document persistence. These are sensitive fields and documents; the KMS/provider, retention, staff access, deletion and host-visible subset are not decided.
- Dispute evidence, refunds, automatic release, manual overrides and release windows. These depend on the regulated custody and provider arrangement.
- Chat unlock policy and prices for every unlock.
- Exact postal-address storage. The current server returns only an entitled exact coordinate and area label; adding plaintext address storage would violate the security baseline.
- Cross-source amenity semantics. Landlord amenities are typed columns while Sublessor amenities are currently free text, so the filter does not pretend they are equivalent.

## Design

Data model: `HomeFilter` carries public search values only. `RenterHome` remains a public projection with an approximate area; exact location, phone and video are separate server responses. A report contains a server-authenticated reporter, a source-derived listing subject, a fixed category and a bounded description. Payment and booking amounts remain integer minor units.

State transitions: a denied access read moves to checkout; a successful server payment may move to the matching access view; pending and failed payments reveal nothing. Debug builds can ask the existing server mock endpoint to settle an attempt as `SUCCESS` or `FAILED`; the server remains the authority for the returned state and entitlement. Applications use the server states `SUBMITTED`, `UNDER_REVIEW`, `APPROVED`, `REJECTED` and `WITHDRAWN`. Bookings use the existing server states and do not gain a new dispute or refund transition in this change.

API contract: `GET /api/v1/renter/homes` now accepts optional `propertyType` and ISO `availableBy`. `POST /api/v1/reports` receives a listing kind derived from the loaded listing, never a user ID. All calls still use the authenticated Renter token; the app sends no renter/owner identity parameter.

Failure modes: invalid ranges, property types and dates are rejected before a search. Unknown report categories, blank/oversized descriptions, unpublished listings, excessive report volume and wrong-role access are rejected. Network errors keep the current screen and appear through the existing snackbar. Sensitive access is cleared when the app backgrounds.

## File-by-file explanation

### Android

- `app/src/main/java/com/makaohub/android/data/MakaoApi.kt`
  - `ReportRequest` and `ReportResponse` express the existing moderation endpoint without placing the reporter identity in the request.
  - `homes` adds type and availability query parameters. Removing them makes the matching reference filters inert.
  - `report` submits the private report to the server. Removing it leaves the report screen unable to create an audited record.
- `app/src/main/java/com/makaohub/android/data/RenterRepository.kt`
  - `HomeFilter.error` validates amounts, source, type and ISO date at the trust boundary. This prevents malformed or unsupported search input reaching SQL.
  - `homes` passes only validated public filters.
  - `report` derives `LANDLORD_LISTING` or `SHARED_LISTING` from the loaded home and bounds the category/description. This prevents a client field from choosing a reporter or arbitrary report subject type.
  - `MockPaymentResult` represents the backend's complete mock-outcome allowlist. `complete` sends only the server-issued provider reference and this typed outcome; removing the type would reopen arbitrary outcome strings at the client boundary.
- `app/src/main/java/com/makaohub/android/RenterViewModel.kt`
  - `RenterScreen.REPORT`, `reportCategory` and `reportDescription` own the report form state.
  - `save(RenterHome, Boolean)` lets a result card use the same owned save endpoint as the detail page.
  - `openReport` resets stale form data; `submitReport` submits once through the existing serialized work guard and returns to the listing only after success.
  - `simulatePayment` accepts the typed mock result, asks the server to settle the pending attempt, remembers the authoritative response, and refreshes any linked booking. Removing the booking refresh would leave a successful or failed booking checkout visibly stale.
- `app/src/main/java/com/makaohub/android/ui/RenterScreens.kt`
  - `RenterWorkspace` routes the new report page and restores the listing on Back.
  - `RenterHomes` adds direct save, clearer empty-state copy and semantic verified/status styling without adding fake counts or recent searches.
  - `RenterFilters` adds server-backed property type and known-availability filters. It intentionally does not show nonfunctional amenity controls.
  - `RenterDetail` adds the report entry point while preserving separate unlock actions.
  - `RenterReport` mirrors the supplied reason/description/privacy hierarchy. Screenshot evidence is omitted because no secure report-evidence storage contract exists.
  - `RenterApplicationDetail`, `RenterBookingDetail`, `RenterCheckout`, `RenterStatus`, `paymentTitle` and `paymentStatus` translate server states into the supplied user-facing hierarchy. They never grant access from UI text.
  - The checkout payment state exposes separate successful and failed simulation actions only when `MOCK_PAYMENTS_ENABLED` is true. The failure action is secondary and exists to exercise the supplied failure reference without creating a production payment control.
  - `RenterInput` supports bounded multiline report text as well as the existing single-line fields.
- `app/src/test/java/com/makaohub/android/RenterContractTest.kt`
  - `realMixedHomeFilterUsesServerPaginationAndSource` now proves invalid type/date rejection.
  - `listingReportUsesServerOwnedReporterAndTypedListingKind` proves the request has the correct listing kind and no reporter/owner ID.
  - `failedMockPaymentUsesTypedProviderOutcomeOnly` proves a failure request sends `FAILED` plus the server-issued provider reference, with no renter ID, listing ID or client amount.

### Spring backend

- `src/main/java/com/makaohub/backend/property/api/RenterHomeController.java`
  - `search` binds optional `propertyType` and ISO `availableBy`, then continues deriving the actor from the JWT.
- `src/main/java/com/makaohub/backend/property/service/RenterHomeService.java`
  - `search` allowlists the union's real property/space types and parameterizes both new predicates. This prevents SQL injection and filters only already-public, approved listings.
- `src/test/java/com/makaohub/backend/property/service/RenterHomeIntegrationTest.java`
  - The search integration test covers the new type/date behavior and rejects an unknown type. It runs only when `MAKAO_ROLE_TEST_DB_URL` points to the isolated local test database.

## Verification

- Android: `testDebugUnitTest assembleDebug` passed; 75 tests, zero failures/errors/skips; debug APK assembled. The Renter contract class contains 8 passing tests, including the failed-settlement request.
- Backend: focused Renter workspace authorization tests passed (2 tests). A disposable local PostgreSQL database then applied all 60 Flyway migrations and the four Renter home integration tests passed with zero failures/errors/skips. The disposable database and role were removed afterward.
- Both repositories: `git diff --check` passed.

## Security decisions and attacks prevented

- Server-only entitlement reads prevent a forged “Paid” screen from exposing coordinates, phone numbers or video.
- JWT-derived ownership prevents IDOR through renter/owner identifiers supplied by the app.
- Fixed report categories, length limits, contact masking and parameterized SQL reduce stored-abuse, PII leakage and injection risks.
- Public search selects approximate coordinates only; exact coordinates remain behind the listing-scoped entitlement service.
- Debug-only mock completion prevents a release build from manufacturing a paid state.
- Clearing sensitive access on background reduces shoulder-surfing and task-switcher exposure.

## Remaining risks, confirmation and next task

Risks: the existing backend still contains a hard-coded 24-hour mock release gate even though release policy is open; there is no production dispute/refund workflow; exact address encryption and private application/dispute evidence are absent; native visual parity is not verified without a rendered device capture.

Test next: exercise search/report/unlock/application/booking on a disposable Android test account with network loss, replayed payment requests, large text and process backgrounding.

Confirm with a Kenyan lawyer and the regulated payment partner: custody wording, release/refund/dispute rules, evidence retention/access, income-data necessity and consent. Confirm Daraja callback authentication and query semantics with current official Safaricom documentation before enabling live M-Pesa.

Suggested next task: decide the application-data visibility/retention contract and KMS provider, then add the application profile and private supporting-document flow without exposing renter verification evidence to hosts.
