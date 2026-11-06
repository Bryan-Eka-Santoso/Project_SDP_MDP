// Env diisi sebelum modul aplikasi di-require karena config/env.js membacanya saat dimuat.
process.env.JWT_ACCESS_SECRET = 'test-secret-yang-panjangnya-lebih-dari-32-karakter';
process.env.JWT_ACCESS_TTL = '15m';
process.env.JWT_REFRESH_TTL = '7d';

// Prisma di-mock: test ini menguji alur auth, bukan koneksi MySQL.
jest.mock('../src/lib/prisma', () => {
  const db = {
    user: { findUnique: jest.fn(), update: jest.fn() },
    refreshToken: { create: jest.fn(), findUnique: jest.fn(), updateMany: jest.fn() },
    deviceToken: { deleteMany: jest.fn() },
    $transaction: jest.fn(),
  };
  db.$transaction.mockImplementation((arg) =>
    typeof arg === 'function' ? arg(db) : Promise.all(arg),
  );
  return db;
});

const bcrypt = require('bcrypt');
const jwt = require('jsonwebtoken');
const request = require('supertest');
const prisma = require('../src/lib/prisma');
const app = require('../src/app');
const { hashRefreshToken } = require('../src/modules/auth/auth.service');

const PASSWORD = 'rahasia123';
const PASSWORD_HASH = bcrypt.hashSync(PASSWORD, 4);
const DAY = 24 * 60 * 60 * 1000;

const studentRow = (overrides = {}) => ({
  id: 5,
  email: 'siswa1@santaagnes.test',
  name: 'Siswa Satu',
  role: 'STUDENT',
  status: 'ACTIVE',
  passwordHash: PASSWORD_HASH,
  teacher: null,
  student: { id: 1, nis: '26001', birthDate: new Date('2010-05-17T00:00:00Z') },
  guardian: null,
  ...overrides,
});

const accessTokenFor = (id, options = {}) =>
  jwt.sign({ role: 'STUDENT' }, process.env.JWT_ACCESS_SECRET, {
    algorithm: 'HS256',
    subject: String(id),
    expiresIn: 900,
    ...options,
  });

beforeEach(() => {
  for (const model of [prisma.user, prisma.refreshToken, prisma.deviceToken]) {
    for (const fn of Object.values(model)) fn.mockReset();
  }
  prisma.$transaction.mockClear();
  prisma.refreshToken.create.mockResolvedValue({});
  prisma.refreshToken.updateMany.mockResolvedValue({ count: 1 });
  prisma.deviceToken.deleteMany.mockResolvedValue({ count: 1 });
});

describe('POST /api/v1/auth/login', () => {
  const login = (body) => request(app).post('/api/v1/auth/login').send(body);

  it('sukses: mengembalikan token dan profil tanpa password', async () => {
    prisma.user.findUnique.mockResolvedValue(studentRow());

    const res = await login({ email: '  Siswa1@SantaAgnes.test ', password: PASSWORD });

    expect(res.status).toBe(200);
    expect(prisma.user.findUnique.mock.calls[0][0].where).toEqual({
      email: 'siswa1@santaagnes.test',
    });
    const { accessToken, refreshToken, user } = res.body.data;
    expect(jwt.verify(accessToken, process.env.JWT_ACCESS_SECRET).sub).toBe('5');
    expect(user).toEqual({
      id: 5,
      email: 'siswa1@santaagnes.test',
      name: 'Siswa Satu',
      role: 'STUDENT',
      teacher: null,
      student: { id: 1, nis: '26001', birthDate: '2010-05-17' },
      guardian: null,
    });
    expect(JSON.stringify(res.body)).not.toContain(PASSWORD_HASH);

    // Yang disimpan hanya hash refresh token, bukan token aslinya.
    const saved = prisma.refreshToken.create.mock.calls[0][0].data;
    expect(saved.tokenHash).toBe(hashRefreshToken(refreshToken));
    expect(saved.tokenHash).not.toBe(refreshToken);
    expect(saved.expiresAt.getTime() - Date.now()).toBeGreaterThan(7 * DAY - 60_000);
  });

  it('wali murid: profil memuat daftar anak', async () => {
    prisma.user.findUnique.mockResolvedValue(
      studentRow({
        role: 'GUARDIAN',
        student: null,
        guardian: {
          id: 3,
          phone: '0812',
          students: [
            { relationship: 'Ibu', student: { id: 1, nis: '26001', user: { name: 'Siswa Satu' } } },
          ],
        },
      }),
    );

    const res = await login({ email: 'wali1@santaagnes.test', password: PASSWORD });

    expect(res.body.data.user.guardian).toEqual({
      id: 3,
      phone: '0812',
      children: [{ id: 1, name: 'Siswa Satu', nis: '26001', relationship: 'Ibu' }],
    });
  });

  it('401 jika password salah', async () => {
    prisma.user.findUnique.mockResolvedValue(studentRow());
    const res = await login({ email: 'siswa1@santaagnes.test', password: 'salah12345' });
    expect(res.status).toBe(401);
    expect(res.body.error.code).toBe('INVALID_CREDENTIALS');
  });

  it('401 dengan pesan sama jika email tidak terdaftar', async () => {
    prisma.user.findUnique.mockResolvedValue(null);
    const res = await login({ email: 'tidakada@santaagnes.test', password: PASSWORD });
    expect(res.status).toBe(401);
    expect(res.body.error).toEqual({
      code: 'INVALID_CREDENTIALS',
      message: 'Email atau password salah.',
    });
  });

  it('403 jika akun diblokir', async () => {
    prisma.user.findUnique.mockResolvedValue(studentRow({ status: 'BLOCKED' }));
    const res = await login({ email: 'siswa1@santaagnes.test', password: PASSWORD });
    expect(res.status).toBe(403);
    expect(res.body.error.code).toBe('ACCOUNT_BLOCKED');
    expect(prisma.refreshToken.create).not.toHaveBeenCalled();
  });

  it('400 jika email tidak valid', async () => {
    const res = await login({ email: 'bukan-email', password: '' });
    expect(res.status).toBe(400);
    expect(res.body.error.code).toBe('VALIDATION_ERROR');
    expect(prisma.user.findUnique).not.toHaveBeenCalled();
  });
});

describe('POST /api/v1/auth/refresh', () => {
  const refresh = (refreshToken) =>
    request(app).post('/api/v1/auth/refresh').send({ refreshToken });

  const storedToken = (overrides = {}) => ({
    id: 10,
    userId: 5,
    expiresAt: new Date(Date.now() + DAY),
    revokedAt: null,
    user: { id: 5, role: 'STUDENT', status: 'ACTIVE' },
    ...overrides,
  });

  it('sukses: token lama dicabut dan diganti token baru', async () => {
    prisma.refreshToken.findUnique.mockResolvedValue(storedToken());

    const res = await refresh('token-lama');

    expect(res.status).toBe(200);
    expect(prisma.refreshToken.findUnique.mock.calls[0][0].where).toEqual({
      tokenHash: hashRefreshToken('token-lama'),
    });
    expect(prisma.refreshToken.updateMany.mock.calls[0][0].where).toEqual({
      id: 10,
      revokedAt: null,
    });
    expect(res.body.data.refreshToken).not.toBe('token-lama');
    expect(res.body.data.accessToken).toEqual(expect.any(String));
  });

  it('401 jika token tidak dikenal', async () => {
    prisma.refreshToken.findUnique.mockResolvedValue(null);
    const res = await refresh('ngawur');
    expect(res.status).toBe(401);
    expect(res.body.error.code).toBe('UNAUTHENTICATED');
  });

  it('401 jika token kedaluwarsa', async () => {
    prisma.refreshToken.findUnique.mockResolvedValue(
      storedToken({ expiresAt: new Date(Date.now() - 1000) }),
    );
    const res = await refresh('token-lama');
    expect(res.status).toBe(401);
  });

  it('token yang sudah dipakai ulang mencabut semua sesi user', async () => {
    prisma.refreshToken.findUnique.mockResolvedValue(storedToken({ revokedAt: new Date() }));

    const res = await refresh('token-curian');

    expect(res.status).toBe(401);
    expect(prisma.refreshToken.updateMany).toHaveBeenCalledWith({
      where: { userId: 5, revokedAt: null },
      data: { revokedAt: expect.any(Date) },
    });
    expect(prisma.refreshToken.create).not.toHaveBeenCalled();
  });

  it('401 jika dua refresh bersamaan memakai token yang sama', async () => {
    prisma.refreshToken.findUnique.mockResolvedValue(storedToken());
    prisma.refreshToken.updateMany.mockResolvedValue({ count: 0 });
    const res = await refresh('token-lama');
    expect(res.status).toBe(401);
    expect(prisma.refreshToken.create).not.toHaveBeenCalled();
  });

  it('403 jika akun diblokir', async () => {
    prisma.refreshToken.findUnique.mockResolvedValue(
      storedToken({ user: { id: 5, role: 'STUDENT', status: 'BLOCKED' } }),
    );
    const res = await refresh('token-lama');
    expect(res.status).toBe(403);
    expect(res.body.error.code).toBe('ACCOUNT_BLOCKED');
  });
});

describe('POST /api/v1/auth/logout', () => {
  it('mencabut refresh token dan menghapus device token milik user', async () => {
    prisma.refreshToken.findUnique.mockResolvedValue({ id: 10, userId: 5 });

    const res = await request(app)
      .post('/api/v1/auth/logout')
      .send({ refreshToken: 'token', deviceToken: 'fcm-abc' });

    expect(res.status).toBe(200);
    expect(res.body).toEqual({ success: true, data: null });
    expect(prisma.refreshToken.updateMany.mock.calls[0][0].where).toEqual({
      id: 10,
      revokedAt: null,
    });
    expect(prisma.deviceToken.deleteMany).toHaveBeenCalledWith({
      where: { token: 'fcm-abc', userId: 5 },
    });
  });

  it('tetap sukses jika token tidak dikenal', async () => {
    prisma.refreshToken.findUnique.mockResolvedValue(null);
    const res = await request(app).post('/api/v1/auth/logout').send({ refreshToken: 'x' });
    expect(res.status).toBe(200);
    expect(prisma.refreshToken.updateMany).not.toHaveBeenCalled();
  });
});

describe('GET /api/v1/me', () => {
  const me = (token) => {
    const req = request(app).get('/api/v1/me');
    return token ? req.set('Authorization', `Bearer ${token}`) : req;
  };

  it('sukses', async () => {
    prisma.user.findUnique
      .mockResolvedValueOnce({ id: 5, role: 'STUDENT', status: 'ACTIVE' }) // authenticate
      .mockResolvedValueOnce(studentRow()); // profil

    const res = await me(accessTokenFor(5));

    expect(res.status).toBe(200);
    expect(res.body.data.name).toBe('Siswa Satu');
  });

  it('401 tanpa token', async () => {
    const res = await me();
    expect(res.status).toBe(401);
    expect(res.body.error.code).toBe('UNAUTHENTICATED');
  });

  it('401 untuk token palsu', async () => {
    const forged = jwt.sign({ role: 'ADMIN' }, 'secret-lain-yang-juga-panjang-sekali-ya', {
      subject: '5',
    });
    const res = await me(forged);
    expect(res.status).toBe(401);
    expect(res.body.error.code).toBe('UNAUTHENTICATED');
  });

  it('401 TOKEN_EXPIRED untuk token kedaluwarsa', async () => {
    const expired = accessTokenFor(5, { expiresIn: -10 });
    const res = await me(expired);
    expect(res.status).toBe(401);
    expect(res.body.error.code).toBe('TOKEN_EXPIRED');
  });

  it('403 jika akun diblokir setelah token dibuat', async () => {
    prisma.user.findUnique.mockResolvedValue({ id: 5, role: 'STUDENT', status: 'BLOCKED' });
    const res = await me(accessTokenFor(5));
    expect(res.status).toBe(403);
    expect(res.body.error.code).toBe('ACCOUNT_BLOCKED');
  });

  it('401 jika user sudah dihapus', async () => {
    prisma.user.findUnique.mockResolvedValue(null);
    const res = await me(accessTokenFor(99));
    expect(res.status).toBe(401);
  });
});

describe('PATCH /api/v1/me/password', () => {
  const changePassword = (body) =>
    request(app)
      .patch('/api/v1/me/password')
      .set('Authorization', `Bearer ${accessTokenFor(5)}`)
      .send(body);

  beforeEach(() => {
    prisma.user.findUnique
      .mockResolvedValueOnce({ id: 5, role: 'STUDENT', status: 'ACTIVE' }) // authenticate
      .mockResolvedValueOnce({ id: 5, role: 'STUDENT', passwordHash: PASSWORD_HASH });
  });

  it('sukses: password diganti, sesi lain dicabut, token baru dikirim', async () => {
    const res = await changePassword({ currentPassword: PASSWORD, newPassword: 'passwordBaru1' });

    expect(res.status).toBe(200);
    expect(res.body.data).toEqual({
      accessToken: expect.any(String),
      refreshToken: expect.any(String),
    });
    const { passwordHash } = prisma.user.update.mock.calls[0][0].data;
    expect(await bcrypt.compare('passwordBaru1', passwordHash)).toBe(true);
    expect(prisma.refreshToken.updateMany).toHaveBeenCalledWith({
      where: { userId: 5, revokedAt: null },
      data: { revokedAt: expect.any(Date) },
    });
  });

  it('400 jika password lama salah', async () => {
    const res = await changePassword({ currentPassword: 'salah12345', newPassword: 'passwordBaru1' });
    expect(res.status).toBe(400);
    expect(res.body.error.details[0].field).toBe('body.currentPassword');
    expect(prisma.user.update).not.toHaveBeenCalled();
  });

  it('400 jika password baru kurang dari 8 karakter', async () => {
    const res = await changePassword({ currentPassword: PASSWORD, newPassword: 'pendek' });
    expect(res.status).toBe(400);
    expect(res.body.error.details[0].field).toBe('body.newPassword');
  });

  it('401 tanpa token', async () => {
    const res = await request(app)
      .patch('/api/v1/me/password')
      .send({ currentPassword: PASSWORD, newPassword: 'passwordBaru1' });
    expect(res.status).toBe(401);
  });
});
