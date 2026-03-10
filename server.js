require('dotenv').config();
const express = require('express');
const fs = require('fs');
const path = require('path');
const mime = require('mime-types');
const os = require('os');
const { spawn } = require('child_process');

const app = express();
const PORT = process.env.PORT || 3000;

app.use((req, res, next) => {
  const start = Date.now();
  res.on('finish', () => {
    const ms = Date.now() - start;
    console.log(`[${new Date().toISOString()}] ${req.method} ${req.url} → ${res.statusCode} (${ms}ms)`);
  });
  next();
});

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
    stream.on('error', err => console.error(`[stream error] ${fullPath}: ${err.message}`));
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

  // Fallback: try embedded subtitles for MKV files
  const ext = path.extname(fullPath).toLowerCase();
  if (ext === '.mkv') {
    const ffprobe = spawn('ffprobe', [
      '-v', 'quiet', '-print_format', 'json',
      '-show_streams', '-select_streams', 's', fullPath
    ]);
    let probeOut = '';
    ffprobe.stdout.on('data', d => { probeOut += d; });
    ffprobe.on('close', code => {
      let streams = [];
      try { streams = JSON.parse(probeOut).streams || []; } catch (_) {}
      if (code !== 0 || streams.length === 0) {
        return res.status(404).send('No subtitle found');
      }
      // Filter out image-based subtitle codecs (PGS, VOBSUB) — not convertible to WebVTT
      const TEXT_SUBTITLE_CODECS = new Set([
        'subrip', 'srt', 'ass', 'ssa', 'webvtt', 'mov_text', 'text', 'hdmv_text_subtitle'
      ]);
      const textStreams = streams.filter(s => TEXT_SUBTITLE_CODECS.has(s.codec_name));
      if (textStreams.length === 0) {
        return res.status(404).send('No subtitle found');
      }
      // Prefer English; fall back to first text stream
      const engStream = textStreams.find(s =>
        s.tags?.language === 'eng' || s.tags?.language === 'en'
      );
      const selectedStream = engStream || textStreams[0];
      const streamIndex = streams.indexOf(selectedStream);
      const ffmpeg = spawn('ffmpeg', [
        '-i', fullPath, '-map', `0:s:${streamIndex}`, '-f', 'webvtt', 'pipe:1'
      ]);
      ffmpeg.stderr.resume();
      res.setHeader('Content-Type', 'text/vtt');
      let dataSent = false;
      ffmpeg.stdout.on('data', chunk => { dataSent = true; res.write(chunk); });
      ffmpeg.stdout.on('end', () => res.end());
      ffmpeg.on('close', code => {
        if (code !== 0 && !dataSent) {
          if (!res.headersSent) res.status(500).send('Subtitle conversion failed');
          else res.end();
        }
      });
      ffmpeg.on('error', err => {
        console.error(`[subtitle error] ${fullPath}: ${err.message}`);
        if (!res.headersSent) res.status(404).send('No subtitle found');
      });
    });
    ffprobe.on('error', () => res.status(404).send('No subtitle found'));
    return;
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
