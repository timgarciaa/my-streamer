# My Streamer

A local video streaming server. Run it on your PC and watch your videos on any device on your network — TV, phone, tablet.

## Requirements

- [Node.js](https://nodejs.org/) (v18 or newer)

## Setup

```bash
# 1. Clone the repo
git clone https://github.com/your-username/my-streamer.git
cd my-streamer

# 2. Install dependencies
npm install

# 3. Configure your videos folder
cp .env.example .env
```

Open `.env` and set `VIDEOS_DIR` to your videos folder:

**macOS/Linux:**
```
VIDEOS_DIR=/Users/yourname/Movies
```

**Windows:**
```
VIDEOS_DIR=C:\Users\yourname\Videos
```

> If you skip this step, it defaults to `~/Videos` (macOS/Linux) or `C:\Users\yourname\Videos` (Windows).

## Running

```bash
npm start
```

Then open the **Local** URL in your browser, or share the **Network** URL with other devices on the same Wi-Fi:

```
  My Streamer is running!

  Local:    http://localhost:3000
  Network:  http://192.168.1.x:3000

  Videos directory: C:\Users\yourname\Videos
```

## Windows Notes

- Use `npm start` in **Command Prompt**, **PowerShell**, or **Windows Terminal** — all work fine.
- In `.env`, you can use backslashes (`C:\Users\...`) or forward slashes (`C:/Users/...`) — both work.
- Make sure Node.js is in your PATH (the installer does this by default).
- If your firewall blocks other devices from connecting, allow Node.js through **Windows Defender Firewall** when prompted.

## Supported Formats

**Video:** `.mp4`, `.mkv`, `.avi`, `.mov`, `.wmv`, `.flv`, `.webm`, `.m4v`, `.ts`, `.m2ts`

**Images:** `.jpg`, `.jpeg`, `.png`, `.gif`, `.webp`

## Stopping the Server

Press `Ctrl+C` in the terminal.

## Android TV App

The `android/` folder contains a native Android TV app for browsing and playing videos from the server.

### Features

- Browse your video library in a 5-column grid
- Full-screen playback with Media3 (ExoPlayer)
- Subtitle support: embedded MKV tracks and external `.srt`/`.vtt` files
- D-pad navigation optimized for TV remotes
- Settings screen to configure the server URL (press **Menu** on the remote)

### Configure Server URL

Before building, open the app's Settings screen and enter your server's network address (e.g. `http://192.168.1.x:3000`). The default is `http://192.168.100.13:3001`.

Alternatively, set it directly in `SettingsActivity` before building.

### Build

Requires Android Studio (for the bundled JDK) and the Android SDK.

```bash
cd android
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
ANDROID_HOME="$HOME/Library/Android/sdk" \
./gradlew assembleDebug
```

Output APK: `android/app/build/outputs/apk/debug/app-debug.apk`

### Install on Android TV

**Option 1 — ADB over Wi-Fi (recommended):**

1. On the TV: Settings → Device Preferences → About → enable **Developer options** → enable **USB debugging**
2. From your PC:

```bash
adb connect <TV_IP_ADDRESS>:5555
adb install android/app/build/outputs/apk/debug/app-debug.apk
```

**Option 2 — USB drive:**

1. Copy the APK to a USB drive
2. Plug it into the TV, open the Files app, and tap the APK to install
