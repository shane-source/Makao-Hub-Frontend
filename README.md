# Makao Hub Android client

This is the Kotlin/Jetpack Compose Android client for Makao Hub.

## Current map setup — 14 October 2026

The landlord private-location picker now uses MapLibre Native 13.6.1 with OpenFreeMap instead of Google Maps. No map key, Google Cloud account or billing setup is required. Open a listing draft and continue to **Set the private location**, search an address if your device supports geocoding, or pan/zoom and tap a point. Enter its public area label and explicitly Save private pin. The authenticated backend API and location protections are unchanged. Google sign-in is separate and retains its OAuth configuration.

See [MAPS_IMPLEMENTATION.md](MAPS_IMPLEMENTATION.md) for the changed files/methods, provider references, testing and remaining risks. Older map-key instructions in historical notes are superseded. Current OTP uses email, not the phone/SMS flow described in this original scaffold below; the landlord workspace also exists now. Refer to current implementation notes for those completed slices.

## Open in Android Studio

Open `/home/hp/Desktop/makao-hub-frontend` as an existing Gradle project. The app uses the supplied Makao logo and splash photo, then guides the user through:

1. Welcome: A safer place to start
2. Walkthrough: Find a home that fits your life
3. Walkthrough: Pay with peace of mind
4. Role selection (Renter, Landlord, or Sublessor)
5. Registration or sign-in
6. Phone OTP verification
7. Forgot-password and reset-password screens
8. An authenticated profile loaded from the backend

Registration, login/OTP, Google authentication, password reset, profile, refresh and logout now use Retrofit and the existing Spring Boot controllers. Google requires the OAuth setup below. The role dashboards, listing feeds, messaging and payment screens are not implemented by this slice. The account screen displays the actual server profile instead of sample listings.

Sessions stay in memory for this slice. Rotation preserves the ViewModel/session, but process death requires a fresh sign-in. Passwords, OTPs and tokens are not saved to disk, saved-instance state, analytics or HTTP logs. There is deliberately no nonfunctional "Remember me" control.

## Local backend address

When the backend runs on the same computer and the app runs in the Android emulator, use:

`http://10.0.2.2:8080/`

The debug network policy permits HTTP only to `10.0.2.2`, `localhost`, and `127.0.0.1`. Release builds deny all cleartext traffic. No release backend hostname has been invented.

For a USB-connected physical phone, run `adb reverse tcp:8080 tcp:8080` and build with `-PMAKAO_API_BASE_URL=http://127.0.0.1:8080/`. For an HTTPS server, set `MAKAO_API_BASE_URL` to its real URL with a trailing slash. Do not disable TLS certificate validation.

## Build and test

```bash
cd /home/hp/Desktop/makao-hub-frontend
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

The HTTP contract tests run against MockWebServer. They cannot prove that the real server, database, Twilio configuration or Google OAuth configuration is working.

## Google configuration

Set the public Web OAuth client ID accepted by the backend in your local Gradle properties:

```properties
MAKAO_GOOGLE_WEB_CLIENT_ID=YOUR_WEB_CLIENT_ID.apps.googleusercontent.com
```

Use your real ID; it is not a client secret. Configure the Android OAuth client for package `com.makaohub.android` and your signing certificate in Google Cloud. Never put a client secret into the Android app. The app explains that setup is needed if the ID is absent.

Email and Google registration both return an authenticated token pair. The app loads `/api/v1/profile/me` and opens the authenticated account screen immediately after registration. Later sign-ins still require the backend's password or Google identity check followed by phone OTP. Signing out revokes the refresh-token family and returns the app to sign-in.

## Manual verification

- Start the backend using its normal local configuration. The frontend does not start it or change its secrets.
- Open Explore Makao Hub and follow all three screens. Back, Next and Skip should work.
- Choose each public role: only one card is selected, and the button names it.
- Register a disposable test account; the selected role is stored by the backend and the app opens the authenticated account screen. Sign out, then sign in and use the actual SMS code.
- An invalid code must show an error and must not open the account screen.
- After valid OTP, verify that the displayed profile and role match the server.
- Try a failed connection, a duplicate signup and a rate-limited request. None should advance as success.
- Reset a disposable account's password and sign in again. No passwords or OTPs should appear in logs.
- Verify the four onboarding screens on a device at normal and enlarged font size. They scroll when needed.

## Assets

- `app/src/main/res/drawable/makao_logo.png` — Makao branding
- `app/src/main/res/drawable/makao_splash.jpg` — splash/onboarding home image
- `app/src/main/res/raw/walkthrough_reference.png` — the supplied four-screen design board. Only its three photograph regions are decoded; all text, cards, controls and status bars are native UI.

See `IMPLEMENTATION_NOTES.md` for how each class and method connects, and `design-qa.md` for visual verification status.
