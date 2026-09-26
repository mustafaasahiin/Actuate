# RevenueCat Setup & Production Configuration Guide

This guide details the end-to-end configuration required for RevenueCat in Actuate, explains the root causes of common SDK errors, outlines the backend webhook integration, and provides a verification checklist for testing subscription and quota gating flows on device or emulator.

---

## 1. Root Cause: `PurchasesErrorCode.ConfigurationError`

In the RevenueCat Android SDK, `PurchasesErrorCode.ConfigurationError` ("there is an issue with your configuration, check the underlying error for more details") indicates that the client SDK was unable to validate the application configuration against RevenueCat's servers.

### Primary Triggers

1. **Invalid or Unlinked Public SDK Key**
   - The key in `app/src/main/res/values/revenuecat.xml` does not match the Google Play app registered in RevenueCat, is empty, or uses an incorrect platform prefix (Google Play keys must begin with `goog_`).
2. **Package Name Mismatch**
   - The Android application ID (`com.actuate.app` defined in `app/build.gradle.kts`) does not match the Google Play Package Name registered in the RevenueCat Project Settings.
3. **Missing or Incomplete Google Play Service Account Credentials**
   - RevenueCat requires a Google Cloud Service Account JSON key linked to the Google Play Developer Console with permissions to view financial data and manage subscriptions. If the key is missing or permissions are revoked, Google Play rejects subscription queries.
4. **Unpublished or Inactive Google Play Subscriptions**
   - In-app subscription product IDs (`actuate_pro_monthly`, `actuate_pro_annual`) must exist in the Google Play Console under the registered package name, with base plans activated, and the app must be published to at least an internal test track.
5. **Missing Entitlement or Offering Mapping**
   - If the products exist in RevenueCat but are not assigned to the `pro` entitlement, or if the `default` offering lacks `$rc_monthly` and `$rc_annual` packages, RevenueCat returns an empty offering list or configuration error.
6. **Untrusted / Non-Tester Google Account**
   - When running on an emulator or physical device, the active Google account signed into the Google Play Store must be added to the license testers list in the Google Play Console.

### Actuate Defensive Architecture

Actuate isolates your app from `ConfigurationError` crashes:
- **Eager Initialization (`ActuateApplication.kt`):** The SDK is configured in `Application.onCreate()` safely wrapped in `runCatching`.
- **Reactive UI Fallback (`PaywallSheet.kt`):** If offerings fail to load due to `ConfigurationError`, the paywall displays an inline notice with a "Retry" button rather than freezing or crashing.
- **Judge Pass (`SHIPATON2026`):** The emergency judge pass remains completely decoupled and functional even if RevenueCat is unreachable or unconfigured.

---

## 2. Values You Must Supply

RevenueCat requires specific identifiers that tie the Google Play Store, the RevenueCat Dashboard, and the Actuate Android client together:

| Parameter | Required Value | Location |
|---|---|---|
| **Package Name** | `com.actuate.app` | Google Play Console & RevenueCat Project Settings |
| **Public SDK Key** | `goog_...` (valid key from RevenueCat) | `app/src/main/res/values/revenuecat.xml` |
| **Monthly Product ID** | `actuate_pro_monthly` | Google Play Console & RevenueCat Products |
| **Annual Product ID** | `actuate_pro_annual` | Google Play Console & RevenueCat Products |
| **Entitlement ID** | `pro` | RevenueCat Entitlements |
| **Offering ID** | `default` | RevenueCat Offerings |
| **Monthly Package** | `$rc_monthly` | RevenueCat Default Offering Packages |
| **Annual Package** | `$rc_annual` | RevenueCat Default Offering Packages |
| **Webhook Secret** | Any secure string | `server/.env` (`REVENUECAT_WEBHOOK_SECRET`) |

---

## 3. Step-by-Step RevenueCat Dashboard Setup

### Step 3.1: Register Google Play App in RevenueCat

1. Log in to the [RevenueCat Dashboard](https://app.revenuecat.com/).
2. Select your Project (or create a new project named **Actuate**).
3. Navigate to **Project Settings** (gear icon) → **Apps** → **+ New App**.
4. Select **Google Play**.
5. Set **App Name**: `Actuate Android`.
6. Set **Google Play package name**: `com.actuate.app`.
7. Keep this page open to upload credentials in the next step.

### Step 3.2: Connect Google Play Developer Console Credentials

RevenueCat requires access to the Google Play Developer API to validate purchases and subscription renewals:

1. Open the [Google Cloud Console](https://console.cloud.google.com/) for your Google Play Developer organization.
2. Navigate to **IAM & Admin** → **Service Accounts** → **Create Service Account**:
   - Service account name: `revenuecat-billing-sync`.
   - Role: **Service Account User**.
3. Once created, click on the service account → **Keys** tab → **Add Key** → **Create new key** → Choose **JSON** → Download the private key file.
4. Open the [Google Play Console](https://play.google.com/console) → **Users & Permissions** → **Invite new user**:
   - Enter the service account email generated in step 2.
   - Under **App permissions**, select `com.actuate.app`.
   - Under **Account permissions**, enable:
     - **View financial data, orders, and cancellation survey responses**
     - **Manage orders and subscriptions**
   - Click **Invite user** to grant access.
5. Return to **RevenueCat Dashboard** → **Project Settings** → **Apps** → Select **Actuate Android**.
6. Upload the downloaded Service Account JSON key file in the **Google Play Service Account credentials** section and save.

### Step 3.3: Configure Google Play Subscriptions

In the [Google Play Console](https://play.google.com/console) for `com.actuate.app`:

1. Navigate to **Monetize** → **Subscriptions**.
2. Click **Create subscription**:
   - **Product ID**: `actuate_pro_monthly`
   - **Name**: `Actuate Pro Monthly`
   - Add a base plan: Billing period: **Monthly**, set your pricing, and click **Activate base plan**.
3. Click **Create subscription**:
   - **Product ID**: `actuate_pro_annual`
   - **Name**: `Actuate Pro Annual`
   - Add a base plan: Billing period: **Yearly**, set your pricing, and click **Activate base plan**.

### Step 3.4: Configure Products in RevenueCat

1. In the RevenueCat Dashboard, navigate to **Products** → **+ New Product**.
2. Configure Monthly Subscription:
   - **Identifier**: `actuate_pro_monthly`
   - **App**: `Actuate Android (Google Play)`
   - **Type**: Subscription
   - **Google Play Product ID**: `actuate_pro_monthly`
3. Configure Annual Subscription:
   - **Identifier**: `actuate_pro_annual`
   - **App**: `Actuate Android (Google Play)`
   - **Type**: Subscription
   - **Google Play Product ID**: `actuate_pro_annual`

### Step 3.5: Configure Entitlement

1. In the RevenueCat Dashboard, navigate to **Entitlements** → **+ New Entitlement**.
2. **Identifier**: `pro` (display name: `Actuate Pro`).
3. Click **Attach**:
   - Select `actuate_pro_monthly`.
   - Select `actuate_pro_annual`.

### Step 3.6: Configure Default Offering

1. In the RevenueCat Dashboard, navigate to **Offerings**.
2. If a `default` offering already exists, select it. Otherwise, click **+ New Offering** with Identifier: `default`.
3. In the offering details:
   - Click **+ New Package**:
     - Identifier: `$rc_monthly` (select monthly preset).
     - Attach Product: `actuate_pro_monthly`.
   - Click **+ New Package**:
     - Identifier: `$rc_annual` (select annual preset).
     - Attach Product: `actuate_pro_annual`.
4. Ensure the offering is set as the **Current Offering**.

---

## 4. Public SDK Key Configuration

1. In the RevenueCat Dashboard, go to **Project Settings** → **API Keys**.
2. Locate the **Public API Key** for Google Play. It has the format:
   ```
   goog_xxxxxxxxxxxxxxxxxxxxxxxxxxxx
   ```
3. Open `app/src/main/res/values/revenuecat.xml` in the repository and set your key:
   ```xml
   <?xml version="1.0" encoding="utf-8"?>
   <resources>
       <string name="revenuecat_public_key">goog_YOUR_ACTUAL_REVENUECAT_KEY</string>
   </resources>
   ```

*Security Note:* Do not commit real production secret keys to public version control. The RevenueCat Public SDK key is client-safe for reading offerings and executing sandbox purchases.

---

## 5. Webhook Setup for Backend Synchronization

Actuate includes a production-ready webhook endpoint in `server/src/routes/webhooks.js` that synchronizes subscription status with your user database.

### Webhook Specification

- **Endpoint URL:** `https://<YOUR_API_DOMAIN>/api/v1/webhooks/revenuecat`
- **Method:** `POST`
- **Signature Verification:** Uses `x-request-signature` header (HMAC-SHA256 of the raw body).
- **Environment Configuration (`server/.env`):**
  ```env
  REVENUECAT_WEBHOOK_SECRET=your_webhook_signing_secret_here
  ```

### How the Synchronization Works

1. When a user launches Actuate, `RevenueCatEntitlementProvider` passes the anonymous installation ID (`PermissionState.deviceId()`) as the `appUserID` to RevenueCat.
2. When the user purchases or renews a subscription:
   - RevenueCat dispatches an event (`INITIAL_PURCHASE`, `RENEWAL`, `PRODUCT_CHANGE`, etc.) to `/api/v1/webhooks/revenuecat`.
   - The server verifies the HMAC signature if `REVENUECAT_WEBHOOK_SECRET` is configured.
   - The server matches `event.app_user_id` to the local user record in `server/data/db.json`.
   - The server sets `isPro: true` and `proSource: "revenuecat"`.
3. When a subscription lapses or expires:
   - RevenueCat dispatches an `EXPIRATION` event.
   - The server sets `isPro: false` and `proSource: null`, returning the user to the free 3-action weekly rolling quota.

---

## 6. Manual Verification Checklist (On-Device / Simulator)

Execute these steps on an Android emulator or physical device to verify Phase 1 end-to-end:

### Test Case 1: Cold Boot & Safe Initialization
- [ ] Install and launch the Actuate debug build: `.\gradlew.bat installDebug`.
- [ ] Observe that the app launches directly into `HomeScreen` without crashing.
- [ ] Check Android logcat (`adb logcat -s Actuate:* Purchases:*`):
  - If a valid `goog_...` key is present: `Purchases is configured`.
  - If unconfigured: App logs fallback warning and initializes `LocalQuotaEntitlementProvider`.

### Test Case 2: Free Tier 3-Action Limit Enforcement
- [ ] On a fresh installation, check the quota indicator on the Home screen or Settings:
  - Expected: `"Free · 3 left this week"`.
- [ ] Execute Action 1 (Voice or Quick Chip, e.g. "Add standup meeting tomorrow at 9 AM"):
  - Expected: Action executes; quota updates to `"Free · 2 left this week"`.
- [ ] Execute Action 2 (e.g. "Put almond milk on groceries"):
  - Expected: Action executes; quota updates to `"Free · 1 left this week"`.
- [ ] Execute Action 3 (e.g. "Remind me to submit expense report at 5 PM"):
  - Expected: Action executes; quota updates to `"Free · 0 left this week"`.
- [ ] Attempt Action 4 (e.g. "Schedule dentist on Friday at 2 PM"):
  - Expected: Execution is blocked.
  - Expected Message: `"Free tier: 3 actions per week. Upgrade for unlimited."`.
  - Destination badge shows `None`, and action is marked failed in History.

### Test Case 3: Paywall Sheet & Error Resilience
- [ ] Tap **Settings** → **Upgrade to Pro** (or trigger paywall from blocked quota).
- [ ] If RevenueCat offerings are configured:
  - Monthly and Annual packages display localized prices retrieved from Google Play.
- [ ] If RevenueCat offerings fail (network offline or `ConfigurationError`):
  - Paywall displays an inline card: *"Unable to load subscription plans right now. [Retry]"*.
  - Tapping **Retry** re-invokes the offering fetch without crashing the application.
- [ ] Verify the **Judge Pass / Promo Code** section is visible and expandable at the bottom of the sheet.

### Test Case 4: Unlock Pro Flow
- [ ] **Method A (Sandbox Google Play):**
  - Sign into an emulator with a Google Play license tester account.
  - Tap **Subscribe Monthly** or **Subscribe Annual**.
  - Complete the Google Play test purchase ("Always approve" sandbox card).
  - Expected: Sheet closes with success feedback, and quota status updates to `"Pro · unlimited"`.
- [ ] **Method B (Instant Judge Bypass):**
  - Open **Upgrade to Pro** → Tap *"Have a promo or judge pass?"*.
  - Enter `SHIPATON2026` and tap **Redeem Pass**.
  - Expected: Success checkmark; Pro is instantly activated; quota status updates to `"Pro · unlimited"`.

### Test Case 5: Pro Tier Unlimited Executions
- [ ] With Pro status active (`Pro · unlimited`), execute 5+ consecutive voice or text actions.
- [ ] Verify all 5+ actions execute and schedule without hitting any weekly quota limit or block dialogs.
- [ ] Verify History tab logs all 5+ items as completed (`Done`).
