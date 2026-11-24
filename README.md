# Family Screen Time

A **transparent** Android parental-control app for a parent's own / shared family
phone. It limits and locks app use for young children and shows how time is spent —
without recording the screen, microphone, messages or location.

Repo: `infinitytechcare/monitor-my-childs`

## Features

**Visibility**
- Daily usage dashboard: how long each app ran, with a daily total.

**Limits & blocking (enforced on-device, no internet needed)**
- Per-app daily time limits.
- Total daily screen-time cap across all apps.
- Allowed-apps only (whitelist): block everything except apps you approve — new
  installs are blocked automatically too.
- Scheduled lock windows: bedtime, school, study, or custom times.
- Forced breaks: after N continuous minutes, lock for a cooldown.
- When a rule triggers, a clear full-screen lock appears on the phone.

**Local alerts**
- Notifications on the same phone when a limit is hit, a blocked app is opened,
  or a lock window starts. Nothing is sent anywhere.

**Tamper resistance**
- Optional device-admin so a child can't uninstall the app until an adult turns
  admin off first. (Adds no monitoring ability.)

## What it does NOT do (by design)

No screenshots or screen recording, no microphone/ambient listening, no reading of
other apps' messages or notifications, no keylogging, no location tracking, and no
hidden/stealth mode. Android forces the owner to grant "Usage access" and
"Draw over other apps" by hand, so it can't run as covert spyware. Install it only
on phones whose users know it is there.

For the exact videos a child watched on YouTube, open the YouTube app → History,
or pair this with **YouTube Kids** / **Google Family Link** (free, consent-based).

## Setup on the phone

1. Install the APK (link below).
2. Open the app → **Setup** tab → grant **Usage access** and **Draw over other apps**;
   allow notifications; turn **Protection running** on.
3. Configure **Limits** and **Schedule**. Optionally turn on uninstall protection.

## Building the APK

Pushing to `main` builds a debug APK and publishes it to the **latest** GitHub
release automatically (`.github/workflows/build.yml`).

Direct download: `https://github.com/infinitytechcare/monitor-my-childs/releases/download/latest/app-debug.apk`

## Tech

- Kotlin + Jetpack Compose (Material 3)
- `UsageStatsManager` (app usage), `WindowManager` overlay (lock screen),
  foreground `Service`, optional `DeviceAdminReceiver`
- Gradle 8.9 · AGP 8.6.1 · Kotlin 2.0.20 · compileSdk 34 · minSdk 26

## Roadmap (needs your own cloud setup)

- Firebase remote lock/unlock and "ask for more time" requests (needs a free
  Firebase project + `google-services.json`).
- Auto daily usage summary to Google Drive (needs Drive OAuth setup).
