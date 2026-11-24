# Family Screen Time

A simple, **transparent** Android app that shows how long each app was used on a
phone, broken down by day. Built so a parent can see how much time young children
spend in apps like YouTube on a shared family phone.

Repo: `infinitytechcare/monitor-my-childs`

## What it does

- Lists every app used on the phone and how long it ran in the foreground.
- Shows a daily total and lets you step back through previous days.
- Lets you share a day's report as plain text (to yourself via email, Drive,
  WhatsApp — whatever you pick from the Android share sheet).

## What it does NOT do (by design)

- **No screenshots.** It never captures what is on screen.
- **No hidden operation.** It has a normal visible icon, and Android forces the
  phone's owner to turn on "Usage access" by hand in Settings — the app cannot
  enable itself silently or run as spyware.
- **No secret upload.** Nothing leaves the phone unless you tap "Share".

For a 4–6 year old watching YouTube, this plus YouTube's own **watch history**
(which shows the exact videos) covers what you need without recording anyone's
private screen content. Install it only on phones whose users know it is there.

## How to see the actual videos watched

Open the **YouTube** app on the phone → Library/You → **History**. It lists every
video, with time. For stronger controls, pair this with **YouTube Kids** or
**Google Family Link** (free, built for exactly this).

## Building the APK

Pushing to `main` builds a debug APK automatically via GitHub Actions
(`.github/workflows/build.yml`).

1. Create the repo `infinitytechcare/monitor-my-childs` on GitHub.
2. From this folder:
   ```bash
   git init
   git add .
   git commit -m "Initial commit: Family Screen Time"
   git branch -M main
   git remote add origin https://github.com/infinitytechcare/monitor-my-childs.git
   git push -u origin main
   ```
3. Open the repo's **Actions** tab → the "Build APK" run → download the
   **app-debug-apk** artifact.

Install that APK on the phone, open the app, tap **Open Usage Access settings**,
and switch on "Family Screen Time".

## Tech

- Kotlin + Jetpack Compose (Material 3)
- `UsageStatsManager` for app usage (needs the user-granted `PACKAGE_USAGE_STATS`)
- Gradle 8.9 · AGP 8.6.1 · Kotlin 2.0.20 · compileSdk 34 · minSdk 26
