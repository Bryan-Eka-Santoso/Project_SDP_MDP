const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');

describe('lib/cloudinary (mode lokal & Cloudinary)', () => {
  const ORIGINAL_ENV = { ...process.env };
  const ORIGINAL_FETCH = global.fetch;

  beforeEach(() => {
    jest.resetModules();
    process.env = { ...ORIGINAL_ENV };
  });

  afterEach(() => {
    process.env = ORIGINAL_ENV;
    global.fetch = ORIGINAL_FETCH;
  });

  afterAll(async () => {
    await fs.promises.rm(path.resolve(__dirname, '../uploads'), { recursive: true, force: true });
  });

  it('menyimpan ke local storage sementara jika env Cloudinary belum lengkap, lalu hapus saat destroyAsset', async () => {
    delete process.env.CLOUDINARY_CLOUD_NAME;
    delete process.env.CLOUDINARY_API_KEY;
    delete process.env.CLOUDINARY_API_SECRET;
    const cloudinary = require('../src/lib/cloudinary');

    expect(cloudinary.isConfigured()).toBe(false);
    const out = await cloudinary.uploadBuffer({
      buffer: Buffer.from([0x89, 0x50, 0x4e, 0x47]),
      originalname: 'foto.png',
      mimetype: 'image/png',
      folder: 'sim/announcements',
      baseUrl: 'http://192.168.1.45:3005',
    });

    expect(out.publicId).toMatch(/^local:announcements\/\d+-.+\.png$/);
    expect(out.url).toMatch(/^http:\/\/192\.168\.1\.45:3005\/uploads\/announcements\/\d+-.+\.png$/);

    const diskPath = path.join(cloudinary.UPLOADS_DIR, out.publicId.slice('local:'.length));
    expect(fs.existsSync(diskPath)).toBe(true);

    await cloudinary.destroyAsset(out.publicId);
    expect(fs.existsSync(diskPath)).toBe(false);

    // Path traversal tidak boleh memicu error ataupun menghapus di luar UPLOADS_DIR.
    await expect(cloudinary.destroyAsset('local:../../package.json')).resolves.toBeUndefined();
  });

  it('mengunggah ke Cloudinary dengan signature SHA-1 valid saat env terisi', async () => {
    process.env.CLOUDINARY_CLOUD_NAME = 'demo-cloud';
    process.env.CLOUDINARY_API_KEY = 'key123';
    process.env.CLOUDINARY_API_SECRET = 'secretXYZ';

    let capturedUrl;
    let capturedBody;
    global.fetch = jest.fn(async (url, opts) => {
      capturedUrl = url;
      capturedBody = opts.body;
      return {
        ok: true,
        status: 200,
        json: async () => ({
          secure_url: 'https://res.cloudinary.com/demo-cloud/image/upload/v1/sim/announcements/item.png',
          public_id: 'sim/announcements/item',
          resource_type: 'image',
        }),
      };
    });

    const cloudinary = require('../src/lib/cloudinary');
    expect(cloudinary.isConfigured()).toBe(true);

    const out = await cloudinary.uploadBuffer({
      buffer: Buffer.from([0x89, 0x50, 0x4e, 0x47]),
      originalname: 'foto.png',
      mimetype: 'image/png',
      folder: 'sim/announcements',
    });

    expect(capturedUrl).toBe('https://api.cloudinary.com/v1_1/demo-cloud/auto/upload');
    const timestamp = capturedBody.get('timestamp');
    const expectedSig = crypto
      .createHash('sha1')
      .update(`folder=sim/announcements&timestamp=${timestamp}secretXYZ`)
      .digest('hex');
    expect(capturedBody.get('signature')).toBe(expectedSig);
    expect(out).toMatchObject({
      url: 'https://res.cloudinary.com/demo-cloud/image/upload/v1/sim/announcements/item.png',
      publicId: 'sim/announcements/item',
      fileName: 'foto.png',
      mimeType: 'image/png',
      size: 4,
    });
  });

  it('destroyAsset di Cloudinary bersifat best-effort dan berhenti jika sukses', async () => {
    process.env.CLOUDINARY_CLOUD_NAME = 'demo-cloud';
    process.env.CLOUDINARY_API_KEY = 'key123';
    process.env.CLOUDINARY_API_SECRET = 'secretXYZ';

    global.fetch = jest.fn(async () => ({
      ok: true,
      json: async () => ({ result: 'ok' }),
    }));

    const cloudinary = require('../src/lib/cloudinary');
    await cloudinary.destroyAsset('sim/announcements/item');

    expect(global.fetch).toHaveBeenCalledTimes(1);
    expect(global.fetch.mock.calls[0][0]).toContain('/image/destroy');
  });
});
