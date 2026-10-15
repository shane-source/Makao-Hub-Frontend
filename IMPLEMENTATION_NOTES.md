# Onboarding and authentication implementation

Current map replacement (14 October 2026): MapLibre Native 13.6.1 with OpenFreeMap replaces Google Maps. The map SDK dependency, key lookup, MAPS_CONFIGURED flag and Google map manifest metadata are removed. The owner still uses the same location API, map selection, private-pin protection and optional platform Geocoder search. No map API key or billing account is needed. Google OAuth remains unchanged. See `/home/hp/Desktop/makao-hub-frontend/MAPS_IMPLEMENTATION.md`; historical Google map setup below is superseded.

Final map verification: OpenGL renderer selected explicitly for compatibility; SDK-contributed GPS permissions removed, and coordinate-bearing default map feedback replaced with fixed attribution links. All 33 Android tests passed and the final debug APK built. The merged manifest has no GPS permission, Google map key metadata or Vulkan-only hardware requirement. No phone is attached, so native rendering/gesture/lifecycle/real address search are still manual-test gaps.

## Supplied splash artwork — 14 October 2026

The owner supplied a complete splash graphic: brown house logo, "makao hub", "Find Your Dream Home" and a sunset house photograph. This supersedes the plain-brown logo/text splash described in older notes below. Only presentation changes; no backend, account data, API, OTP, role, navigation state, dependencies or security rules change.

- `/home/hp/Desktop/makao-hub-frontend/app/src/main/res/drawable-nodpi/makao_splash_screen.png`: an unchanged copy of the owner's 804 × 1748 PNG. `drawable-nodpi` avoids treating its pixel dimensions as density-dependent layout dimensions. Without it the new splash cannot compile/render. Older logo and house-photo resources remain in use elsewhere.
- `/home/hp/Desktop/makao-hub-frontend/app/src/main/java/com/makaohub/android/MainActivity.kt`, `Splash`: renders the supplied artwork on the existing brown background. `ContentScale.Fit` preserves the complete logo/headline/photo without stretching or cutting off words; different aspect ratios may have brown bands. The content description exposes the brand/headline to accessibility services, but raster text cannot reflow with font scaling. Removing `Image` removes the artwork; removing `Fit` may crop/distort it. The parent `MakaoApp` branch retains the 2.5-second timer and tap action. `AuthViewModel.finishSplash` still guards against stale callbacks; neither performs authentication or calls the backend.
- `/home/hp/Desktop/makao-hub-frontend/design-qa.md`: records the new source and required device comparison. No connected phone or installed AVD was found, so visual acceptance is blocked, not certified from compilation.

RECOMMENDED verification: check the full headline on the phone, wait for automatic continuation, relaunch and tap early, then continue onward and check there is no late navigation jump. Check TalkBack and another screen aspect ratio. Existing splash state tests cover guarded navigation; native appearance and real timing require a device.

Risks: raster text does not resize independently; the supplied image has rounded transparent corners, and fit may leave bands. No new payment/provider/legal decision is introduced; existing production requirements remain unchanged. Suggested next task: verify the splash on the development phone before accepting visual fidelity.

Verification: `./gradlew --offline testDebugUnitTest assembleDebug --console=plain` passed after this change; 30 Android tests passed, including the two splash navigation checks. The debug APK was rebuilt. The source and installed resource have matching SHA-256 hashes, proving the artwork is unmodified. Kotlin daemon writes were restricted by the sandbox; Gradle's fallback compilation succeeded. No email, backend, storage or money request was made. This is a build/test pass, not a native visual-QA pass.

Current landlord update (14 October 2026): unverified landlords now open the ID front/back, selfie and ownership-evidence screen after authentication. A Google Map picker replaces coordinate inputs; underlying owner coordinates still use the existing protected API. Configure MAKAO_MAPS_API_KEY privately before rebuilding. See /home/hp/Desktop/makao-hub-frontend/LANDLORD_MAPS_VERIFICATION.md for setup, changed methods, security sequence, testing and production limitations. This supersedes older notes that describe verification screens/maps as absent.

Current OTP update (14 October 2026): the backend generates six-digit email codes and sends them through authenticated SMTP (Gmail for developer testing). Registration and login return a challenge, not an immediate session; the recipient field is `maskedEmail`. Password reset uses email too. Signup/reset now require a minimum of 8 characters, preserving the 72 UTF-8 byte limit. Routes and Makao JWTs remain in use. No external OTP-generation provider is active. Older SMS/immediate-token history below is superseded. See `/home/hp/Desktop/makao-hub-backend/docs/email-otp.md` for private Gmail setup, class/method explanations and security checks.

## Scope and decisions

### Splash and welcome update — 14 October 2026

The owner requested a 2.5-second splash, tap-to-continue, a current-screen guard, and removal of the welcome sign-in button. No account data, API contract, backend logic, dependencies or brand assets changed. The supplied Screen/backStack example is adapted to the existing Page/ViewModel navigation rather than introducing a second navigation system.

- /home/hp/Desktop/makao-hub-frontend/app/src/main/java/com/makaohub/android/MainActivity.kt: MakaoApp's Page.SPLASH branch starts a LaunchedEffect(Unit) with delay(2500L) and calls finishSplash. The full-screen Box also calls finishSplash on tap and exposes a button role/action label for accessibility. Leaving the branch cancels its effect. Removing the effect disables automatic continuation; removing clickable disables early continuation. Splash now accepts a Modifier and only renders the existing brown background, logo and makao hub text; removing its old timer prevents two competing timers. EntryScreen drops its unused onLogin callback and sign-in button, retaining the welcome photo and Explore Makao Hub button. Sign-in remains on RegisterScreen and after logout. Page.ENTRY is the existing welcome/onboarding entry; because this app stores only a current Page, replacing it already avoids keeping Splash in a back stack.
- /home/hp/Desktop/makao-hub-frontend/app/src/main/java/com/makaohub/android/AuthViewModel.kt: finishSplash checks page == Page.SPLASH before calling navigate(Page.ENTRY). Both tap and timer use it. Removing this guard could let a stale callback replace the user's later page. It never assigns tokens, chooses an authenticated role or bypasses OTP.
- /home/hp/Desktop/makao-hub-frontend/app/src/test/java/com/makaohub/android/SplashNavigationTest.kt: verifies the guarded transition leaves profile/challenge empty, tolerates repeated completion, and ignores a late callback after navigating onward. No network or mail calls occur. Without this test the tap/timer regression has no state-level check.

This supersedes the older 1.5-second/entry-sign-in description below. Current Compose dependencies are unchanged (BOM 2026.02.01); no new navigation framework or security bypass was added. RECOMMENDED device verification: launch and wait 2.5 seconds, relaunch and tap early, continue the walkthrough and check that no late jump occurs, and reach Sign in from registration. Device timing/appearance still needs a real device check. This UI-only change introduces no new legal/payment/provider decision; existing pre-production privacy and transport requirements remain.

Verification completed: all 24 Android tests passed (including two new splash state checks), and the debug APK built successfully. The initial build attempt was blocked by the sandbox's local socket restriction; after network permission was granted the build completed. No backend request, real email or payment was triggered by these checks.

The visual target is `/tmp/codex-clipboard-d8537059-9b6f-4fe4-ad12-4aaa70f17679.png`.
This change implements its three walkthrough screens and role selector, and wires the existing authentication screens to the backend. It does not modify the backend or its database.

The reference says the server stores the role "before registration". The current backend stores it during registration. The UI says "when you create your account" to reflect that actual contract. Role selection is still before the signup form. Sign-in never sends a role.

RECOMMENDED: keep sessions in memory during this integration phase. Persistent sign-in will need a separate Android Keystore-backed refresh-token store and restoration tests. No plaintext token preferences were added.

## Flow and API contract

| Action | Backend call | Next state |
|---|---|---|
| Choose a role | None yet | Registration form |
| Submit registration | POST `/api/v1/auth/register` with fullName, email, phoneNumber, password, role, termsAndPrivacyAccepted | On 201: store returned tokens and load profile/account screen |
| Sign in | POST `/api/v1/auth/login` with email and password | On success: server challenge ID, masked phone and expiry |
| Verify code | POST `/api/v1/auth/login/verify-otp` with challengeId and code | Store token pair in memory; GET profile |
| Google sign in | Native Credential Manager, then POST `/api/v1/auth/google` | OTP screen |
| Google registration | POST `/api/v1/auth/google/register` | Store returned tokens and load profile/account screen |
| Google OTP | POST `/api/v1/auth/google/verify-otp` | Store token pair; GET profile |
| Forgot password | POST `/api/v1/auth/forgot-password` with email | Neutral code-entry screen on 202 |
| Reset password | POST `/api/v1/auth/forgot-password/reset` with email, code, newPassword | Login on 204 |
| Load profile | GET `/api/v1/profile/me` with bearer token | Show actual server role and verification status |
| Expired access token | POST `/api/v1/auth/refresh` | Rotate pair once, retry GET once |
| Sign out | POST `/api/v1/auth/logout` with refreshToken | Clear local session even on remote failure |

## Files, classes and methods

### MainActivity.kt

- `MainActivity.onCreate` configures native system bars and launches Compose. It is the manifest entry point; removing it prevents launch.
- `MakaoApp` maps `Page` values to screens and wires system back to the same destinations as onscreen back. It never fabricates login state. Removing a branch disconnects that part of the journey.
- `Splash` displays the supplied brand assets for 1.5 seconds; `EntryScreen` separates new-user walkthrough from returning-user login. These connect the app launch to authentication.

### ui/OnboardingScreens.kt

- `MakaoTheme` centralizes brown, cream, text and border colors. Brown `#573B2D`, background `#FAF7F1`, and selected-card `#EDE2D7` were sampled from the reference. Removing it restores Material defaults and loses the supplied visual identity.
- `PageBody` supplies insets, keyboard padding, scrolling and the brand header. Without it, small-screen content can be hidden by the keyboard/system bars. `showBack=false` removes the action at the authenticated root.
- `Heading`, `BodyCopy`, `Gap`, `PrimaryButton`, `SkipButton`, `WhiteCard`, `ProgressLabel` and `Note` reproduce shared typography, spacing, cards, navigation and callouts. They are reused so the four screens do not drift in style. Each screen depends on these helpers for its layout.
- `ReferencePhoto` decodes only each photo region from the original 3488 × 2108 board, on the IO dispatcher, and closes its decoder. Removing it removes the real imagery. It does not show a screenshot as an interactive screen and does not fake system chrome.
- `WelcomeScreen` is the first reference page; Continue opens the first walkthrough, Skip opens role selection.
- `HomesWalkthrough` is step 1 of 2; Next opens the payment explanation, Back opens Welcome.
- `PeaceWalkthrough` is step 2 of 2; Choose my role and Skip open the same role selector. The mock-payment notice matches this testing build; no payment is created here.
- `RoleScreen` uses one enum value and accessible radio semantics. Selection updates the card and Continue label. Removing its selection state would lose the role sent during signup. No public Admin option is supplied.

### data/MakaoApi.kt

- `SignupRole` restricts public UI choices to RENTER, LANDLORD and SUBLESSOR. It is only a registration input, never a source of authenticated authorization.
- Request classes describe exact Java record field names. `RegisterRequest` includes explicit consent. `LoginRequest` deliberately omits role. `VerifyOtpRequest` includes the server challenge. `ForgotPasswordRequest` and `ResetPasswordRequest` carry the existing reset contract. `RefreshRequest`, `GoogleRequest` and `GoogleRegisterRequest` carry credentials only in request bodies.
- Response classes model only fields this slice needs: `AcceptedResponse`, `OtpChallenge`, `AuthTokens`, `MyProfile`. Both registration endpoints return `AuthTokens`, matching the backend. Credential classes have no generated data-class `toString`, reducing accidental logging risk.
- `MakaoApi.register`, `login`, `verifyOtp`, `google`, `googleRegister`, `verifyGoogleOtp`, `forgotPassword`, `resetPassword`, `refresh`, `logout`, and `profile` each map one row in the API table above to a suspend HTTP request. Removing an API method breaks its repository action. Retrofit awaits them without blocking Android's main thread.

### data/AuthRepository.kt

- `register` and `registerGoogle` store the token pair returned by successful account creation. This makes the new account signed in without another password entry.
- `login` and `google` begin a new sign-in and receive an OTP challenge. `verify` stores tokens only after OTP succeeds; thus sign-in after logout still requires OTP.
- `forgot` and `reset` forward to those exact APIs. Reset checks status before reporting success; errors are not converted into success.
- `profile` owns authenticated loading and one refresh/retry cycle. Its mutex makes rotation serial: without it, parallel calls could reuse a refresh token and trigger family revocation.
- `logout` clears the local session before calling revocation. Without local clearing, a network error could leave the user signed in after pressing Sign out. A failure is still displayed; remote revocation is not claimed.
- `requireSuccess` throws for unsuccessful empty-body responses, including reset and logout.
- `createMakaoApi` configures a fixed base URL, finite timeouts, normal TLS validation, no body logger and no redirects/automatic connection retries. This keeps credentials scoped to the configured server and avoids hidden retries after an uncertain POST response.
- `SessionExpired` tells the ViewModel to return to login. `friendlyError` maps failures to bounded messages without displaying raw server internals.

#### Security-critical sequence, statement by statement

1. `verify`: call the server first. A six-character input alone is never proof of verification.
2. `sessionLock.withLock { tokens = response }`: install a session only from a successful OTP response. No client-created token or signup role is trusted.
3. `profile`: reject when `tokens` is null; this prevents a login challenge from acting as a session.
4. `Authorization: Bearer ...`: attach the access token to the protected profile request, never its URL.
5. Catch only a 401 for refresh. Other errors are surfaced rather than misinterpreted as token expiry.
6. Refresh while holding the mutex; another profile request cannot use the same refresh token concurrently.
7. Replace the old pair before retrying the GET. A refresh failure or cancellation discards the uncertain pair, avoiding replay after a response was lost.
8. Retry the GET only once. A second 401 clears the session and requests login, preventing a refresh loop.
9. No credentials are written to saved state, files, request logs or analytics. This limits exposure through backup and logging.
10. Release network policy denies HTTP. Debug policy permits it only for emulator/USB loopback addresses, supporting local development without disabling TLS for remote hosts.

### AuthViewModel.kt

- `Page` defines navigable states; `AuthViewModel` retains state through configuration changes and owns one repository.
- `navigate` blocks navigation during requests, clears stale messages/challenges, and changes page. `selectRole` changes only the signup selection; `showMessage` reports local Google/configuration errors.
- `register` checks backend-compatible input/consent, awaits account creation, stores the returned session, and loads the account profile. `googleRegister` follows the same authenticated destination. `login` and `googleLogin` still wait for OTP.
- `verify` checks six numeric digits, calls the repository, then loads the account. Failure leaves the user with an error, not a pretend dashboard.
- `forgot` stores the reset email and shows neutral text; `reset` submits code/new password and returns to login only after success. `reloadProfile` reloads the server profile; `logout` clears displayed account data even if revocation fails.
- `perform` centralizes loading, duplicate-tap prevention, cancellation and failure handling. Removing it would allow duplicate SMS/signup submissions and stale success navigation.
- `normalizeEmail`, `normalizePhone`, `validEmail`, `validPhone`, `passwordError`, and `registrationError` mirror the existing DTO constraints, including bcrypt's 72-byte boundary. Backend validation remains authoritative. Validation work is linear in the bounded input length.

### ui/AuthScreens.kt

- `RequestState` displays actual loading/error state. `Field` controls input and masks passwords by default; the visibility toggle does not alter the password.
- `RegisterScreen`, `LoginScreen`, `OtpScreen`, `ForgotScreen`, `ResetScreen` bind form values to ViewModel actions. Removing their actions disconnects the corresponding API calls. Passwords and codes use `remember`, not saved-instance state; consent is opt-in and login does not ask for a role.
- `AccountScreen` displays the actual profile and supports refresh/sign-out. It replaces the previous fake dashboard. Role dashboards will be a later slice.
- `GoogleButton` validates required registration details and launches the account picker only when configured. `getGoogleToken` obtains and checks the credential type, then passes its token to the server for verification. A Google token alone never opens the authenticated app; registration must return a Makao Hub session.
- No fictitious resend endpoint is called. The OTP screen offers a return to login because the inspected backend has no standalone resend route.

### Configuration and tests

- `app/build.gradle.kts` enables BuildConfig and adds Retrofit 3.0.0, its Gson converter, Credential Manager 1.5.0, Google ID 1.1.1, lifecycle ViewModel Compose 2.11.0, JUnit 4.13.2 and MockWebServer 4.12.0. It allows a configurable backend URL/public Google client ID; never a client secret.
- `AndroidManifest.xml` selects the network policy; the main XML denies cleartext and the debug-only XML permits the three local development hosts. Without the debug override, emulator HTTP calls fail; without the release restriction, credentials could travel over HTTP.
- `AuthContractTest` executes real Retrofit serialization against a local mock server. Its tests cover single-role signup, login without role, no session before OTP, invalid OTP rejection, server-owned role, serialized refresh, reset contracts, logout failure and input limits. Removing them removes regression coverage for these boundaries.

## Limits and next verification

The backend was not listening on port 8080 during this implementation. Live SMS, Google configuration and actual profile data therefore still need an end-to-end run. There is no attached Android device or installed emulator system image in this environment, so visual device comparison is pending. See `design-qa.md`.

Registration now issues a session before phone OTP; this follows the requested signup experience. Role verification and endpoint authorization must continue to gate sensitive owner and renter actions. Terms/privacy document links need actual product URLs before release. The mock-payment notice is appropriate only for this test build. No payment/custody rules were changed, so no new legal/provider decision is introduced here.

Suggested next task: run the app and backend together on your emulator, compare these four pages with the supplied design, and complete a disposable-account OTP/profile flow before building the role dashboards.
