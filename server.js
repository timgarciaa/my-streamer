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

// API: get video duration via ffprobe
app.get('/api/duration', (req, res) => {
  const relPath = req.query.path || ''
  const absPath = path.join(VIDEOS_DIR, relPath)
  const args = [
    '-v', 'quiet',
    '-show_entries', 'format=duration',
    '-of', 'csv=p=0',
    absPath
  ]
  const probe = spawn('ffprobe', args)
  let output = ''
  probe.stdout.on('data', d => { output += d.toString() })
  probe.on('close', code => {
    const secs = parseFloat(output.trim())
    if (isNaN(secs)) return res.status(404).json({ error: 'unknown duration' })
    res.json({ durationMs: Math.round(secs * 1000) })
  })
})

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

// HLS playlist endpoint
app.get('/hls/playlist', (req, res) => {
  const relPath = req.query.path || '';
  const fullPath = path.resolve(path.join(VIDEOS_DIR, relPath));

  if (!fullPath.startsWith(path.resolve(VIDEOS_DIR))) {
    return res.status(403).send('Access denied');
  }

  if (!fs.existsSync(fullPath)) {
    return res.status(404).send('File not found');
  }

  const args = ['-v', 'quiet', '-show_entries', 'format=duration', '-of', 'csv=p=0', fullPath];
  const probe = spawn('ffprobe', args);
  let output = '';
  probe.stdout.on('data', d => { output += d.toString(); });
  probe.on('close', () => {
    const totalSec = parseFloat(output.trim());
    if (isNaN(totalSec)) return res.status(500).send('Could not determine duration');

    const segDur = 10;
    const numSegs = Math.ceil(totalSec / segDur);
    const encodedPath = encodeURIComponent(relPath);

    let m3u8 = '#EXTM3U\n';
    m3u8 += '#EXT-X-VERSION:3\n';
    m3u8 += `#EXT-X-TARGETDURATION:${segDur}\n`;
    m3u8 += '#EXT-X-MEDIA-SEQUENCE:0\n';

    for (let i = 0; i < numSegs; i++) {
      const start = i * segDur;
      const dur = Math.min(segDur, totalSec - start);
      m3u8 += `#EXTINF:${dur.toFixed(3)},\n`;
      m3u8 += `/hls/segment?path=${encodedPath}&index=${i}\n`;
    }

    m3u8 += '#EXT-X-ENDLIST\n';

    res.setHeader('Content-Type', 'application/vnd.apple.mpegurl');
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.send(m3u8);
  });
});

// HLS segment endpoint
app.get('/hls/segment', (req, res) => {
  const relPath = req.query.path || '';
  const fullPath = path.resolve(path.join(VIDEOS_DIR, relPath));
  const index = parseInt(req.query.index) || 0;

  if (!fullPath.startsWith(path.resolve(VIDEOS_DIR))) {
    return res.status(403).send('Access denied');
  }

  if (!fs.existsSync(fullPath)) {
    return res.status(404).send('File not found');
  }

  const start = index * 10;

  res.setHeader('Content-Type', 'video/mp2t');
  res.setHeader('Access-Control-Allow-Origin', '*');

  const ffmpeg = spawn('ffmpeg', [
    '-ss', String(start),
    '-i', fullPath,
    '-t', '10',
    '-c:v', 'libx264', '-preset', 'ultrafast', '-crf', '23',
    '-c:a', 'aac', '-ac', '2',
    '-avoid_negative_ts', 'make_zero',
    '-f', 'mpegts',
    'pipe:1',
  ]);

  ffmpeg.stdout.pipe(res);
  ffmpeg.stderr.on('data', () => {});
  req.on('close', () => ffmpeg.kill());
});

// Subtitle serving (VTT or SRT→VTT on the fly)
app.get('/subtitle', (req, res) => {
  const subPath = req.query.path || '';
  const fullPath = path.resolve(path.join(VIDEOS_DIR, subPath));

  if (!fullPath.startsWith(path.resolve(VIDEOS_DIR))) {
    return res.status(403).send('Access denied');
  }

  res.setHeader('Access-Control-Allow-Origin', '*');

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
