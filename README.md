# MUFCA Bot Client for Android

A lightweight native Android client for the **MUFCA trading bot**.

The application provides a native Android interface to the existing MUFCA web dashboard and adds **Firebase Cloud Messaging (FCM)** push notifications for trading signals and TP1 events.

The Android client does not duplicate the trading dashboard. It loads the existing web application inside a WebView while handling Android-specific functionality such as push notifications, device registration, secure credential storage, and notification deep links.

> **Platform:** Android 7.0+ (API 24)  
> **Compile SDK:** 34  
> **Target SDK:** 34  
> **Application ID:** `com.mufcabot.app`

---

## Features

- Native Android application
- Existing MUFCA web dashboard loaded inside a WebView
- Firebase Cloud Messaging (FCM) push notifications
- Trading signal notifications
- TP1 notifications
- Notification deep links to the relevant dashboard tab, trading pair, and timeframe
- Native server connection test
- Automatic FCM device registration
- FCM token refresh handling
- Encrypted local credential storage when Android Keystore is available
- Fallback local storage for devices where encrypted storage cannot be initialized
- Independent WebView dashboard authentication
- Pull-to-refresh support
- Chart-aware pull-to-refresh handling
- External links opened outside the WebView
- Android 13+ notification permission handling
- Self-hosted MUFCA backend support
- No Discord dependency for mobile notifications

---

# Architecture

The Android application is intentionally lightweight.

The existing MUFCA web dashboard remains responsible for the trading UI and business logic.

```text
                    +----------------------+
                    |      MUFCA Bot       |
                    |      Backend         |
                    +----------+-----------+
                               |
                               | FCM
                               v
                    +----------------------+
                    | Firebase Cloud       |
                    | Messaging            |
                    +----------+-----------+
                               |
                               v
                    +----------------------+
                    | Android Client       |
                    |                      |
                    | FCM Service          |
                    | MainActivity         |
                    | WebView              |
                    +----------+-----------+
                               |
                               v
                    +----------------------+
                    | MUFCA Web Dashboard  |
                    | React application    |
                    +----------------------+
```

The Android application does **not** implement the trading strategy.

Trading logic, indicators, charts, signal generation, history, and dashboard functionality remain part of the MUFCA backend/web application.

---

# Project Structure

```text
app/src/main/java/com/mufcabot/app/
├── MufcaApp.kt
│   └── Creates the notification channel
│
├── Prefs.kt
│   └── Stores server URL and native API credentials
│
├── ApiClient.kt
│   └── Native HTTP communication:
│       ├── Connection test
│       └── FCM device registration
│
├── SettingsActivity.kt
│   └── Initial server and credential configuration
│
├── MainActivity.kt
│   └── WebView dashboard, navigation, refresh, and deep links
│
└── MufcaFirebaseMessagingService.kt
    └── Receives FCM messages and creates Android notifications
```

The dashboard itself is not duplicated.

The following sections remain the same web application used in a browser:

```text
Status
Chart
History
Settings
```

---

# Requirements

To build and run the application, you need:

- Android Studio
- Android SDK
- Java 17
- Android SDK 34
- Android 7.0 / API 24 or newer
- A running MUFCA Bot backend
- A Firebase project
- A Firebase Android application registered as:

```text
com.mufcabot.app
```

The project currently uses:

```text
Gradle 8.13
Android Gradle Plugin 8.13.2
Kotlin 1.9.24
```

---

# Firebase Setup

Firebase is required for push notifications.

Two different Firebase credential files are involved.

## `google-services.json`

This file belongs to the **Android application**.

In Firebase Console, register an Android application using:

```text
com.mufcabot.app
```

Download:

```text
google-services.json
```

and place it at:

```text
mufca-android/app/google-services.json
```

The Google Services Gradle plugin uses this file during the Android build.

Without it, the Firebase configuration step will fail.

---

## `firebase-credentials.json`

This file belongs to the **MUFCA Bot backend**, not the Android project.

In Firebase Console:

```text
Project settings
→ Service accounts
→ Generate new private key
```

Download the generated service-account credentials.

Place the file on the MUFCA Bot server.

For example:

```text
firebase-credentials.json
```

The backend obtains the file location from:

```text
FIREBASE_CREDENTIALS_PATH
```

in the bot's `.env` configuration.

### Security

Both Firebase credential files contain sensitive information.

**Never commit them to Git.**

They should remain excluded through `.gitignore`.

The Android release signing keystore must also never be committed.

---

# Open the Project in Android Studio

Open Android Studio and select:

```text
File → Open
```

Then choose the repository root:

```text
MufcaAndroidClient
```

The repository contains the Gradle wrapper configuration.

The current wrapper is configured to use:

```text
Gradle 8.13
```

The first Gradle synchronization downloads the required Gradle distribution and project dependencies.

---

# Build and Run

## Debug Build

Connect an Android device or start an emulator.

Then select:

```text
Run ▶
```

in Android Studio.

The debug application can be installed directly on the device.

Debug builds also allow self-signed TLS certificates for development/testing.

---

## Release APK

To generate a signed APK:

```text
Build
→ Generate Signed Bundle / APK
→ APK
```

Create or select a release keystore and choose:

```text
release
```

The release configuration currently enables:

```text
minifyEnabled true
shrinkResources true
```

### Important

Keep the release keystore and its passwords safe.

The same signing key is required for future updates of the application.

---

# First Launch

When the application starts for the first time, it opens the native setup screen.

You need to provide:

## Server URL

Enter the URL of your MUFCA dashboard.

Example:

```text
https://mufca.home.pvt.ge
```

## Username and Password

Enter the credentials configured for the MUFCA web/API access:

```text
WEB_USERNAME
WEB_PASSWORD
```

Then select:

```text
Save and connect
```

The application tests the configured server before opening the dashboard.

The native client uses the backend API endpoint:

```text
/api/config
```

If the server is unreachable or authentication fails, the application reports the problem during setup.

After a successful connection:

1. The dashboard opens inside the WebView.
2. The WebView uses its own dashboard login session.
3. The device FCM token is registered with the backend.
4. Push notifications can be received by the Android application.

The configured server URL and credentials can later be changed from:

```text
⋮ → Settings
```

---

# Authentication

The Android client uses two separate authentication contexts.

This means the same credentials may be entered twice.

This is intentional.

## Native Android Authentication

The credentials stored by the Android client are used by the native Kotlin code for backend communication.

The main native API operation is:

```http
POST /api/devices/register
```

which associates the Android device's FCM token with the MUFCA backend.

The WebView is not involved in this native request.

---

## WebView Authentication

The WebView maintains its own dashboard authentication session.

This is separate from the native Android credentials.

It is used for normal dashboard functionality:

```text
Status
Chart
History
Settings
```

This separation allows the Android application to handle device registration and push notifications without changing the existing web dashboard authentication flow.

---

# Local Credential Storage

The Android client attempts to store the native server URL and credentials using:

```text
EncryptedSharedPreferences
```

with an Android Keystore-backed AES-256 encryption scheme.

If encrypted storage cannot be initialized on a particular device or emulator, the current implementation falls back to regular `SharedPreferences` so that the application can continue working.

Therefore, the application should **not** be described as guaranteeing encrypted credential storage on every device.

For maximum security, use a normal supported Android device with a functioning Android Keystore.

---

# Push Notifications

The MUFCA backend sends **data-only FCM messages**.

The Android client receives these messages through:

```text
MufcaFirebaseMessagingService.onMessageReceived()
```

and builds the Android notification itself.

This design allows the application to attach information required for notification deep links, including:

```text
Notification type
Trading pair
Timeframe
Target dashboard tab
```

The current notification handling distinguishes between:

```text
signal
```

and:

```text
tp1_hit
```

A signal notification opens the Chart tab, while a TP1 notification opens the Status tab.

---

# Notification Flow

```text
MUFCA Bot Backend
        |
        v
Firebase Cloud Messaging
        |
        v
MufcaFirebaseMessagingService
        |
        v
Android Notification
        |
        v
User taps notification
        |
        v
MainActivity
        |
        v
WebView
        |
        v
window.mufcaOpenSignal(ticker, tf, tab)
        |
        v
MUFCA Web Dashboard
        |
        v
Requested tab / pair / timeframe
```

The web dashboard exposes:

```javascript
window.mufcaOpenSignal(ticker, tf, tab)
```

The Android client uses this JavaScript hook to request navigation to the relevant dashboard location.

Deep-link navigation is intentionally **best-effort**.

If the WebView has not authenticated yet and the JavaScript hook is unavailable, the dashboard simply remains on its normal page/login flow.

---

# FCM Token Registration

The device token is registered with the backend when the application obtains an FCM token.

The application also handles FCM token refresh through:

```text
FirebaseMessagingService.onNewToken()
```

The refreshed token is registered again with the MUFCA backend.

This prevents the application from permanently depending on an outdated FCM registration token.

---

# Notification Permission

Android 13 and newer require the application to request:

```text
POST_NOTIFICATIONS
```

The MUFCA Android client requests this permission when required.

If notification permission is denied, FCM messages may still reach the application, but Android will not display normal notification UI to the user.

The user can change the permission later through the Android system settings.

---

# WebView Security

The WebView is intentionally restricted to the configured dashboard host.

If a page attempts to navigate to another host, the Android client opens that URL in the external browser instead of loading it inside the WebView.

This prevents the WebView from becoming a general-purpose browser while exposing the MUFCA JavaScript bridge.

The application also declares:

```text
android:usesCleartextTraffic="false"
```

so unencrypted HTTP traffic is not allowed by default.

---

# Chart Interaction

The dashboard supports interactive charts.

Because the Android client provides pull-to-refresh through `SwipeRefreshLayout`, a normal vertical gesture on the chart could otherwise be interpreted as a page refresh.

To avoid this conflict, the Android client exposes:

```javascript
window.AndroidChartBridge
```

The web chart can notify the Android application when the user is interacting with the chart.

During chart interaction, pull-to-refresh is temporarily disabled.

This allows chart gestures and page refresh gestures to coexist without modifying the normal browser version of the dashboard.

The bridge is Android-specific and is ignored by the normal web application when unavailable.

---

# Navigation

The Android client supports normal WebView navigation.

When the user presses the Android Back button:

1. If the WebView has browser history, it navigates back.
2. Otherwise, normal Android back navigation is performed.

Notification deep links are handled through Android intents and can be processed both when the application is newly launched and when an existing `MainActivity` receives a new intent.

---

# Security

The project handles several sensitive assets.

## Firebase Credentials

Never commit:

```text
google-services.json
firebase-credentials.json
```

to a public repository.

## Android Credentials

Native server credentials are stored using encrypted storage when available, with a compatibility fallback to standard `SharedPreferences`.

Do not assume the fallback storage is encrypted.

## Release Keystore

The Android release keystore is highly sensitive.

Do not publish:

```text
*.jks
*.keystore
```

or their passwords.

## TLS

Production deployments should use a valid TLS certificate.

---

# Known Limitations

## Android Version

Minimum supported version:

```text
Android 7.0+
API 24
```

The application targets:

```text
API 34
```

---

## Notification Delivery

FCM delivery depends on Android, Google Play services/device configuration, network availability, battery optimization, and Firebase delivery behavior.

The application is designed to handle data-only FCM messages natively, but no mobile application can guarantee delivery under every device or network condition.

---

## Application Icon

The current application uses a simple drawable icon:

```text
Dark square
+ Amber circle
+ "M"
```

It is not currently implemented as a full adaptive Android icon with separate foreground and background layers.

A proper adaptive icon can be created later using Android Studio's Image Asset Studio.

---

## SSL / TLS

Debug builds allow self-signed certificates through the WebView SSL handling.

This is intended for local development/testing.

Release builds use the normal WebView SSL behavior and do not accept self-signed certificates.

Production deployments should therefore use a valid TLS certificate.

---

## Credential Storage Fallback

On some devices or emulator configurations, Android Keystore-backed encrypted preferences may fail to initialize.

The current implementation falls back to standard `SharedPreferences` rather than crashing during startup.

This improves compatibility but means credentials may not be encrypted in that fallback case.

---

# Troubleshooting

## Build fails because `google-services.json` is missing

Make sure the file exists at:

```text
app/google-services.json
```

and belongs to the Firebase Android application:

```text
com.mufcabot.app
```

---

## Gradle synchronization fails

Verify that Android Studio has access to:

```text
Google Maven
Maven Central
Gradle distribution services
```

The project currently uses Gradle 8.13 and Android Gradle Plugin 8.13.2.

---

## The application cannot connect to the server

Check:

1. The server URL is correct.
2. The MUFCA backend is running.
3. `/api/config` is reachable.
4. The configured credentials are correct.
5. The Android device can reach the server.
6. The TLS certificate is valid for the release build.
7. The server is not blocking the Android device's network.

---

## Push notifications do not appear

Check:

1. Firebase is configured correctly.
2. `google-services.json` belongs to `com.mufcabot.app`.
3. `firebase-credentials.json` exists on the backend.
4. `FIREBASE_CREDENTIALS_PATH` points to the correct credentials file.
5. The device has notification permission enabled.
6. The FCM token was successfully registered.
7. The MUFCA backend is successfully sending FCM messages.
8. Android battery/background restrictions are not preventing normal application operation.

---

## Notification opens the application but does not navigate to the signal

Check that the web dashboard provides:

```javascript
window.mufcaOpenSignal(ticker, tf, tab)
```

Also make sure the WebView session is authenticated.

If the user has not logged in yet, the normal dashboard login screen may appear before deep-link navigation can take place.

Deep-link handling is best-effort by design.

---

# Design Principles

The Android client follows three main principles.

## 1. Do Not Duplicate the Dashboard

The existing MUFCA web dashboard remains the single source of truth for:

- Trading signals
- Charts
- Indicators
- History
- Settings
- Trading-related UI

## 2. Keep Android-Specific Features Native

The Android application handles:

- FCM
- Device registration
- Notifications
- Notification deep links
- Native credential storage
- Native connection testing
- Android navigation
- WebView-specific behavior

## 3. Reuse the Existing Backend

The Android client communicates with the same MUFCA backend used by the web dashboard.

This avoids duplicating trading logic and keeps the Android client relatively small and easy to maintain.

---

# Summary

MUFCA Android combines:

```text
Existing MUFCA Web Dashboard
            +
Native Android WebView
            +
Firebase Cloud Messaging
            +
Native Notifications
            +
Notification Deep Links
```

The result is a lightweight Android client for the existing MUFCA system, providing native mobile notifications and signal navigation without requiring a separate mobile trading implementation or Discord.
