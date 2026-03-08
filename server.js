require('dotenv').config();
const express = require('express');
const fs = require('fs');
const path = require('path');
const mime = require('mime-types');
const os = require('os');
const { spawn, execSync } = require('child_process');

const app = express();
const PORT = process.env.PORT || 3000;

// Configure your videos directory here
const VIDEOS_DIR = process.env.VIDEOS_DIR || path.join(os.homedir(), 'Videos');

const VIDEO_EXTENSIONS = new Set(['.mp4', '.mkv', '.avi', '.mov', '.wmv', '.flv', '.webm', '.m4v', '.ts', '.m2ts']);
const IMAGE_EXTENSIONS = new Set(['.jpg', '.jpeg', '.png', '.gif', '.webp']);
const SUBTITLE_EXTENSIONS = new Set(['.srt', '.vtt']);

function isVideo(filename) {
  return VIDEO_EXTENSIONS.has(path.extname(filename).toLowerCase());
}

function isImage(filename) {
  return IMAGE_EXTENSIONS.has(path.extname(filename).toLowerCase());
}

function getNetworkIP() {
  const interfaces = os.networkInterfaces();
  for (const name of Object.keys(interfaces)) {
    for (const iface of interfaces[name]) {
      if (iface.family === 'IPv4' && !iface.internal) {
        return iface.address;
      }
    }
  }
  return 'localhost';
}

// Serve static frontend
app.use(express.static(path.join(__dirname, 'public')));

// API: list directory contents
app.get('/api/browse', (req, res) => {
  const subPath = req.query.path || '';
  const fullPath = path.resolve(path.join(VIDEOS_DIR, subPath));

  // Security: prevent path traversal outside VIDEOS_DIR
  if (!fullPath.startsWith(path.resolve(VIDEOS_DIR))) {
    return res.status(403).json({ error: 'Access denied' });
  }

  if (!fs.existsSync(fullPath)) {
    return res.status(404).json({ error: 'Directory not found' });
  }

  const stat = fs.statSync(fullPath);
  if (!stat.isDirectory()) {
    return res.status(400).json({ error: 'Not a directory' });
  }

  try {
    const entries = fs.readdirSync(fullPath);
    const items = entries
      .map(name => {
        const itemPath = path.join(fullPath, name);
        let itemStat;
        try {
          itemStat = fs.statSync(itemPath);
        } catch {
          return null;
        }
        const ext = path.extname(name).toLowerCase();
        const isDir = itemStat.isDirectory();
        if (!isDir && !isVideo(name) && !isImage(name)) return null;

        return {
          name,
          type: isDir ? 'directory' : 'file',
          ext: isDir ? null : ext,
          size: isDir ? null : itemStat.size,
          mtime: itemStat.mtime,
          relativePath: path.join(subPath, name).replace(/\\/g, '/'),
        };
      })
      .filter(Boolean)
      .sort((a, b) => {
        if (a.type !== b.type) return a.type === 'directory' ? -1 : 1;
        return a.name.localeCompare(b.name);
      });

    res.json({
      currentPath: subPath,
      parentPath: subPath ? path.dirname(subPath).replace(/\\/g, '/') : null,
      items,
    });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// Video streaming with range support
app.get('/stream', (req, res) => {
  const subPath = req.query.path || '';
  const fullPath = path.resolve(path.join(VIDEOS_DIR, subPath));

  // Security: prevent path traversal
  if (!fullPath.startsWith(path.resolve(VIDEOS_DIR))) {
    return res.status(403).send('Access denied');
  }

  if (!fs.existsSync(fullPath)) {
    return res.status(404).send('File not found');
  }

  const stat = fs.statSync(fullPath);
  if (!stat.isFile()) {
    return res.status(400).send('Not a file');
  }

  // Transcoded mode: re-encode audio to stereo AAC, mux into fragmented MP4
  if (req.query.transcode) {
    res.writeHead(200, { 'Content-Type': 'video/mp4' });

    const startSec = parseFloat(req.query.start) || 0;

    const ffmpeg = spawn('ffmpeg', [
      '-ss', String(startSec),
      '-i', fullPath,
      '-vcodec', 'copy',
      '-acodec', 'aac',
      '-ac', '2',
      '-output_ts_offset', String(startSec),
      '-movflags', 'frag_keyframe+empty_moov',
      '-f', 'mp4',
      'pipe:1',
    ]);

    ffmpeg.stdout.pipe(res);
    ffmpeg.stderr.on('data', () => {}); // suppress ffmpeg log output

    req.on('close', () => ffmpeg.kill());
    return;
  }

  const fileSize = stat.size;
  const mimeType = mime.lookup(fullPath) || 'video/mp4';
  const range = req.headers.range;

  if (range) {
    const parts = range.replace(/bytes=/, '').split('-');
    const start = parseInt(parts[0], 10);
    const end = parts[1] ? parseInt(parts[1], 10) : fileSize - 1;
    const chunkSize = end - start + 1;

    res.writeHead(206, {
      'Content-Range': `bytes ${start}-${end}/${fileSize}`,
      'Accept-Ranges': 'bytes',
      'Content-Length': chunkSize,
      'Content-Type': mimeType,
    });

    const stream = fs.createReadStream(fullPath, { start, end });
    stream.pipe(res);
  } else {
    res.writeHead(200, {
      'Content-Length': fileSize,
      'Content-Type': mimeType,
      'Accept-Ranges': 'bytes',
    });
    fs.createReadStream(fullPath).pipe(res);
  }
});

// Subtitle serving (VTT or SRT→VTT on the fly)
app.get('/subtitle', (req, res) => {
  const subPath = req.query.path || '';
  const fullPath = path.resolve(path.join(VIDEOS_DIR, subPath));

  if (!fullPath.startsWith(path.resolve(VIDEOS_DIR))) {
    return res.status(403).send('Access denied');
  }

  const base = fullPath.replace(/\.[^.]+$/, '');

  const vttPath = base + '.vtt';
  if (fs.existsSync(vttPath)) {
    res.setHeader('Content-Type', 'text/vtt');
    return fs.createReadStream(vttPath).pipe(res);
  }

  const srtPath = base + '.srt';
  if (fs.existsSync(srtPath)) {
    const srt = fs.readFileSync(srtPath, 'utf8');
    const vtt = 'WEBVTT\n\n' + srt
      .replace(/\r\n/g, '\n')
      .replace(/^\d+\n/gm, '')
      .replace(/(\d{2}:\d{2}:\d{2}),(\d{3})/g, '$1.$2');
    res.setHeader('Content-Type', 'text/vtt');
    return res.send(vtt);
  }

  res.status(404).send('No subtitle found');
});

// Thumbnail/image serving
app.get('/image', (req, res) => {
  const subPath = req.query.path || '';
  const fullPath = path.resolve(path.join(VIDEOS_DIR, subPath));

  if (!fullPath.startsWith(path.resolve(VIDEOS_DIR))) {
    return res.status(403).send('Access denied');
  }

  if (!fs.existsSync(fullPath) || !isImage(fullPath)) {
    return res.status(404).send('Not found');
  }

  const mimeType = mime.lookup(fullPath) || 'image/jpeg';
  res.setHeader('Content-Type', mimeType);
  fs.createReadStream(fullPath).pipe(res);
});

// Warn if ffmpeg is not available (needed for audio transcoding)
try {
  execSync('which ffmpeg', { stdio: 'ignore' });
} catch {
  console.warn('  WARNING: ffmpeg not found in PATH. Audio transcoding will not work.');
  console.warn('  Install it with: brew install ffmpeg');
  console.warn('');
}

app.listen(PORT, '0.0.0.0', () => {
  const ip = getNetworkIP();
  console.log('');
  console.log('  My Streamer is running!');
  console.log('');
  console.log(`  Local:    http://localhost:${PORT}`);
  console.log(`  Network:  http://${ip}:${PORT}`);
  console.log('');
  console.log(`  Videos directory: ${VIDEOS_DIR}`);
  console.log('');
  console.log('  Share the Network URL with your TV, phone, or tablet.');
  console.log('  Press Ctrl+C to stop.');
  console.log('');
});
