# Google Calendar API & Android OAuth 2.0 Setup Guide

This guide walks through configuring Google Cloud Console OAuth 2.0 for Actuate.

Once configured, Actuate can sync calendar events directly to a user's Google Calendar account from the native Android app.

---

## 1. Google Cloud Project Setup

1. Open the [Google Cloud Console](https://console.cloud.google.com/).
2. Sign in with the Google account that manages your project.
3. In the top navigation bar, click the project dropdown and select **New Project**.
4. Set the project details:
   - **Project name:** `Actuate` (or your preferred descriptor).
   - **Organization:** Choose your organization or select **No organization**.
5. Click **Create** and ensure the new project is selected in the project dropdown.
6. Enable the Google Calendar API:
   - Navigate to **APIs & Services** > **Library**.
   - In the search bar, search for `Google Calendar API` (service name: `calendar-json.googleapis.com`).
   - Select **Google Calendar API** from the results and click **Enable**.

---

## 2. OAuth Consent Screen Configuration

1. In the left navigation bar, go to **APIs & Services** > **OAuth consent screen**.
2. Select your **User Type**:
   - Choose **External** if testing with personal `@gmail.com` accounts.
   - Choose **Internal** only if your organization uses Google Workspace and all users belong to the same workspace domain.
3. Click **Create**.
4. Enter standard app information:
   - **App name:** `Actuate`
   - **User support email:** Select your developer or support email.
   - **App logo:** Optional for testing.
   - **Developer contact information:** Enter your email address.
5. Click **Save and Continue**.
6. Configure Scopes:
   - Click **Add or Remove Scopes**.
   - In the filter box, search for `calendar.events`.
   - Select the scope:
     `https://www.googleapis.com/auth/calendar.events`
     *(Description: See, edit, share, and permanently delete all the events you can access using Google Calendar)*
   - Click **Update**, then click **Save and Continue**.
7. Add Test Users:
   - While the app publishing status remains **Testing**, only explicit test users can authorize the app.
   - Click **Add Users**.
   - Enter your personal Gmail address and any teammate test accounts.
   - Click **Add**, then click **Save and Continue**.
8. Review your configuration summary and return to the dashboard.

---

## 3. Creating OAuth 2.0 Client ID (Android)

Actuate uses a native Android OAuth Client ID. You do not need to create a Web Client ID, client secret, or backend server auth proxy.

1. In the left navigation bar, go to **APIs & Services** > **Credentials**.
2. Click **Create Credentials** at the top, then select **OAuth client ID**.
3. Fill in the client configuration:
   - **Application type:** Select **Android**.
   - **Name:** `Actuate Android Client`.
   - **Package name:** `com.actuate.app`
     *(Must match `applicationId` in `app/build.gradle.kts`)*.
4. Supply your SHA-1 certificate fingerprint. You need to register the SHA-1 of the keystore used to sign the APK.

### Release Keystore SHA-1 Fingerprint

Actuate includes a pre-configured release keystore at `keystore/actuate-release.keystore`.

Run this command from the project root:

```bash
keytool -list -v -keystore keystore/actuate-release.keystore -alias actuate -storepass 8DxJmQFyCIZPHkaVgWBez1sl
```

Extracted values for this repository:
- **Package Name:** `com.actuate.app`
- **SHA-1:** `27:DD:0F:58:BD:A0:5A:73:12:E2:AF:05:D1:C7:B9:49:D7:32:6E:1B`
- **SHA-256:** `74:33:C7:EB:66:BA:2E:62:0C:E1:28:13:5F:7C:12:0D:79:3C:9A:15:49:E1:6E:41:A1:4F:39:66:E4:5E:18:7B`

### Debug Keystore SHA-1 Fingerprint

If running local debug builds via Android Studio or `./gradlew installDebug`, register your standard debug keystore as a second Android OAuth Client ID (or add its SHA-1 under the same project):

On Linux / macOS:
```bash
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android
```

On Windows PowerShell:
```powershell
keytool -list -v -keystore "$env:USERPROFILE\.android\debug.keystore" -alias androiddebugkey -storepass android
```

5. Paste the SHA-1 fingerprint into the Google Cloud Console field.
6. Click **Create**.

---

## 4. Why an Android Client ID is Required

Developers often ask why a simple Google API key is not enough.

1. **API Keys vs. User Consent:**
   - A raw Google API key identifies the calling project for quota and billing, but it does not authenticate a user.
   - API keys only permit read access to publicly accessible data.
   - Writing personal calendar events (`events.insert`) requires explicit permission from the user who owns that calendar. That permission is granted through OAuth 2.0 user consent.

2. **Zero Client Secret Exposure on Mobile:**
   - Standard Web OAuth flows rely on a client secret. Shipping client secrets inside an Android APK is insecure because any decompilation tool can extract them.
   - Google Play Services solves this using cryptographic app verification.
   - When Actuate requests a calendar token, Google Play Services inspects the calling app package name (`com.actuate.app`) and computes the SHA-1 of the certificate used to sign the APK.
   - Google Play Services validates that `(package_name, sha1)` tuple against your Cloud Console credentials.
   - Once verified, Google Play Services securely issues and refreshes tokens directly on the device without requiring client secrets.

---

## 5. Verification Checklist

Follow these steps to confirm your Google Calendar integration works end to end:

- [ ] **Check Test User List:** Your active device Google account is added under **OAuth consent screen** > **Test users**.
- [ ] **Verify Signing Keystore:** If installing `app-release.apk`, confirm the release SHA-1 (`27:DD:0F:58:BD:A0:5A:73:12:E2:AF:05:D1:C7:B9:49:D7:32:6E:1B`) is registered. If installing a debug build, confirm your local `debug.keystore` SHA-1 is registered.
- [ ] **Launch Actuate on Device/Emulator:** Open Actuate on a device with Google Play Services installed.
- [ ] **Trigger a Calendar Action:**
  - Tap the voice bubble or use the keyboard command bar.
  - Enter: `Schedule team sync tomorrow at 10 AM`.
- [ ] **Authorize Account:**
  - On the first run, the Google Play Services consent dialog appears.
  - Select your test account and grant calendar permissions.
- [ ] **Inspect Output:**
  - In Actuate, confirm the action succeeds and shows the `Google Calendar` destination badge in the **Today** and **History** tabs.
  - Open [Google Calendar](https://calendar.google.com/) in a browser or mobile app.
  - Verify `Team sync` is present at 10:00 AM on tomorrow's date.
