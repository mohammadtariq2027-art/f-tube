# Implementation Plan: Android OS-Level Google Sign-In & Daily Auto-Backup

## 1. How Official YouTube App Handles Login Internally
- **Token System in Official YouTube**: Yes, the official YouTube app **100% runs on the same token architecture** (OAuth 2.0 Access & Refresh Tokens). For security reasons, no Google app ever sends your actual password across the internet after the initial sign-in.
- **Why You Never Notice It in Official YouTube**:
  - In Android OS, there is a built-in core service called **Google Play Services (`AccountManager`)**.
  - When you set up your Android phone, your Google Account is stored securely at the OS level (`Settings -> Accounts`).
  - When the official YouTube app opens, it requests a short-lived Access Token (typically 1 hour) from `AccountManager`.
  - When the token expires, Android OS silently and instantly generates a new token in milliseconds without showing any UI or asking the user for anything.

---

## 2. How FocusTube Will Replicate the Official YouTube Experience
FocusTube will use the exact same **Android OS Google Play Services Integration**:
1. **Device Account Picker (Zero Password Typing)**:
   - When you tap "Sign in with Google", Android's native system dialog displays the Google account(s) already logged into your phone.
   - You simply tap your account once.
2. **Permanent System Binding via Google Play Services**:
   - The app connects to Android's `GoogleSignInClient` / `CredentialManager` with `offlineAccess = true`.
   - Android OS grants the app an authorization refresh capability linked directly to the device's Google Play Services account.
3. **Silent Background Token Refresh (`silentSignIn()`)**:
   - Just like the official YouTube app, whenever FocusTube needs to sync a playlist or add a video, it asks Google Play Services in the background (`silentSignIn()`).
   - Android OS immediately provides a fresh token behind the scenes.
   - **Result**: You sign in **only once**. The app will **never ask you to log in again** unless you manually tap "Logout", clear app storage, or factory reset the phone.

---

## 3. Official YouTube Playlist Sync (Background Direct Insert)
1. **Direct API Integration**:
   - Uses YouTube Data API v3 (`playlistItems.insert`) with the silent token.
   - Adding a video in FocusTube automatically and instantly reflects inside your official YouTube app playlist with **0 extra taps**.
2. **Duplicate Prevention**:
   - Before syncing, the app checks whether the video is already present in the playlist to prevent any duplicate copies.

---

## 4. Daily Automatic Backup & Zero-Loss Restore
1. **Daily Auto-Backup (`WorkManager`)**:
   - Runs automatically in the background every evening.
   - Backs up all custom groups, added channels, playlists, and notes into:
     - `Documents/FocusTube/focustube_backup.json` (persists on device even if app is uninstalled).
     - Android OS Cloud Backup (Google Drive App Data).
2. **Instant Auto-Restore on App Reinstallation**:
   - If the app is uninstalled and reinstalled, on the first launch it detects the existing backup file and automatically restores all groups and channels in 1 click.

---

## 5. Verification & Testing Plan
- Test single-tap account selection using native Android Google Play Services dialog.
- Verify `silentSignIn()` succeeds on app restart without showing any login UI.
- Verify that adding a video to a playlist successfully inserts it into the user's real YouTube playlist.
- Test backup generation and automatic restore after simulating a fresh app install.
- Run `compile_applet` and unit tests to ensure stability and zero crashes.
