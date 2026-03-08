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
