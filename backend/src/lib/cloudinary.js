// Klien penyimpanan lampiran: Cloudinary (jika CLOUDINARY_* terisi) atau local storage server sementara.
const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');
const env = require('../config/env');
const AppError = require('../utils/AppError');

const DEFAULT_FOLDER = 'sim/announcements';
const LOCAL_PREFIX = 'local:';
const UPLOADS_DIR = path.resolve(__dirname, '../../uploads');
const ALLOWED_LOCAL_EXTS = new Set(['.jpg', '.jpeg', '.png', '.pdf']);

let localFallbackWarned = false;

function getConfig() {
  const { cloudName, apiKey, apiSecret } = env.cloudinary;
  if (!cloudName || !apiKey || !apiSecret) return null;
  return { cloudName, apiKey, apiSecret };
}

function signParams(params, apiSecret) {
  const serialized = Object.keys(params)
    .filter((key) => params[key] !== undefined && params[key] !== null && params[key] !== '')
    .sort()
    .map((key) => `${key}=${params[key]}`)
    .join('&');
  return crypto.createHash('sha1').update(`${serialized}${apiSecret}`).digest('hex');
}

function sanitizeSubfolder(folder) {
  const cleaned = String(folder || 'announcements')
    .replace(/^sim\/+/, '')
    .replace(/[^a-zA-Z0-9/_-]/g, '')
    .replace(/\.\./g, '')
    .replace(/^\/+|\/+$/g, '');
  return cleaned || 'announcements';
}

async function saveToLocal({ buffer, originalname, mimetype, folder, baseUrl }) {
  if (!localFallbackWarned) {
    localFallbackWarned = true;
    console.warn('[storage] CLOUDINARY_* kosong: lampiran disimpan sementara di local storage (backend/uploads/).');
  }

  const subfolder = sanitizeSubfolder(folder);
  const rawExt = path.extname(originalname || '').toLowerCase();
  const ext = ALLOWED_LOCAL_EXTS.has(rawExt) ? rawExt : '.bin';
  const filename = `${Date.now()}-${crypto.randomUUID()}${ext}`;
  const targetDir = path.join(UPLOADS_DIR, subfolder);
  const targetPath = path.join(targetDir, filename);

  await fs.promises.mkdir(targetDir, { recursive: true });
  await fs.promises.writeFile(targetPath, buffer);

  const relPath = `${subfolder}/${filename}`.replace(/\\/g, '/');
  const origin = (baseUrl || `http://localhost:${env.port}`).replace(/\/+$/, '');

  return {
    url: `${origin}/uploads/${relPath}`,
    publicId: `${LOCAL_PREFIX}${relPath}`,
    resourceType: 'local',
    fileName: originalname,
    mimeType: mimetype,
    size: buffer.length,
  };
}

async function removeFromLocal(publicId) {
  const relPath = publicId.slice(LOCAL_PREFIX.length);
  if (!relPath || relPath.includes('..')) return;
  const resolved = path.resolve(UPLOADS_DIR, relPath);
  if (!resolved.startsWith(`${UPLOADS_DIR}${path.sep}`)) return;
  await fs.promises.unlink(resolved).catch(() => {});
}

/**
 * Unggah buffer gambar/PDF ke Cloudinary, atau ke local storage server (`backend/uploads/`)
 * sementara jika kredensial Cloudinary belum diisi.
 */
async function uploadBuffer({ buffer, originalname, mimetype, folder = DEFAULT_FOLDER, baseUrl }) {
  const cfg = getConfig();
  if (!cfg) {
    return saveToLocal({ buffer, originalname, mimetype, folder, baseUrl });
  }

  const timestamp = Math.floor(Date.now() / 1000);
  const signature = signParams({ folder, timestamp }, cfg.apiSecret);

  const form = new FormData();
  form.append('file', new Blob([buffer], { type: mimetype || 'application/octet-stream' }), originalname);
  form.append('api_key', cfg.apiKey);
  form.append('timestamp', String(timestamp));
  form.append('folder', folder);
  form.append('signature', signature);

  const endpoint = `https://api.cloudinary.com/v1_1/${encodeURIComponent(cfg.cloudName)}/auto/upload`;
  let response;
  try {
    response = await fetch(endpoint, { method: 'POST', body: form });
  } catch (err) {
    throw new AppError(502, 'EXTERNAL_SERVICE_ERROR', `Gagal menghubungi Cloudinary: ${err.message}`);
  }

  const data = await response.json().catch(() => ({}));
  if (!response.ok || !data.secure_url || !data.public_id) {
    const reason = data?.error?.message || `HTTP ${response.status}`;
    throw new AppError(502, 'EXTERNAL_SERVICE_ERROR', `Gagal mengunggah lampiran ke Cloudinary: ${reason}`);
  }

  return {
    url: data.secure_url,
    publicId: data.public_id,
    resourceType: data.resource_type,
    fileName: originalname,
    mimeType: mimetype,
    size: buffer.length,
  };
}

/**
 * Hapus aset dari local storage atau Cloudinary (best-effort; tidak menggagalkan operasi utama).
 */
async function destroyAsset(publicId) {
  if (!publicId) return;
  if (publicId.startsWith(LOCAL_PREFIX)) {
    await removeFromLocal(publicId);
    return;
  }

  const cfg = getConfig();
  if (!cfg) return;

  for (const resourceType of ['image', 'raw']) {
    const timestamp = Math.floor(Date.now() / 1000);
    const signature = signParams({ public_id: publicId, timestamp }, cfg.apiSecret);

    const form = new FormData();
    form.append('public_id', publicId);
    form.append('api_key', cfg.apiKey);
    form.append('timestamp', String(timestamp));
    form.append('signature', signature);

    const endpoint = `https://api.cloudinary.com/v1_1/${encodeURIComponent(cfg.cloudName)}/${resourceType}/destroy`;
    try {
      const response = await fetch(endpoint, { method: 'POST', body: form });
      const data = await response.json().catch(() => ({}));
      if (response.ok && data.result === 'ok') return;
    } catch {
      return;
    }
  }
}

module.exports = {
  uploadBuffer,
  destroyAsset,
  isConfigured: () => Boolean(getConfig()),
  UPLOADS_DIR,
};
