process.env.JWT_ACCESS_SECRET = 'test-secret-yang-panjangnya-lebih-dari-32-karakter';
process.env.JWT_ACCESS_TTL = '15m';
process.env.JWT_REFRESH_TTL = '7d';

jest.mock('../src/lib/prisma', () => {
  const db = {
    user: { findUnique: jest.fn(), update: jest.fn() },
    refreshToken: { create: jest.fn(), findUnique: jest.fn(), updateMany: jest.fn() },
    deviceToken: { deleteMany: jest.fn() },
    $transaction: jest.fn(),
  };
  db.$transaction.mockImplementation((arg) => (typeof arg === 'function' ? arg(db) : Promise.all(arg)));
  return db;
});

const bcrypt = require('bcrypt');
const express = require('express');
const jwt = require('jsonwebtoken');
const request = require('supertest');
const prisma = require('../src/lib/prisma');
const app = require('../src/app');
const { createLimiter, resetRateLimits } = require('../src/middlewares/rateLimit');
const { errorHandler } = require('../src/middlewares/errorHandler');

const PASSWORD = 'rahasia123';
const HASH = bcrypt.hashSync(PASSWORD, 4);
const MINUTE = 60 * 1000;

const user = {
  id: 5,
  email: 'siswa1@santaagnes.test',
  name: 'Siswa Satu',
  role: 'STUDENT',
  status: 'ACTIVE',
  passwordHash: HASH,
  teacher: null,
  student: { id: 1, nis: '26001', birthDate: new Date('2010-05-17T00:00:00Z') },
  guardian: null,
};

const login = (email, password = 'salah-salah-1') =>
  request(app).post('/api/v1/auth/login').send({ email, password });

beforeEach(() => {
  jest.resetAllMocks();
  resetRateLimits();
  prisma.$transaction.mockImplementation((arg) => (typeof arg === 'function' ? arg(prisma) : Promise.all(arg)));
  prisma.refreshToken.create.mockResolvedValue({});
  prisma.user.findUnique.mockResolvedValue(null); // email tidak terdaftar
});

afterEach(() => jest.restoreAllMocks());

describe('login', () => {
  it('login gagal ke-9 dari IP dan email yang sama: 429 + Retry-After, tanpa menyentuh database', async () => {
    for (let i = 0; i < 8; i += 1) {
      expect((await login('korban@santaagnes.test')).status).toBe(401);
    }
    const res = await login('korban@santaagnes.test');

    expect(res.status).toBe(429);
    expect(res.body.error.code).toBe('TOO_MANY_REQUESTS');
    expect(res.body.error.message).toMatch(/15 menit/);
    expect(Number(res.headers['retry-after'])).toBeGreaterThan(0);
    expect(prisma.user.findUnique).toHaveBeenCalledTimes(8);
  });

  it('email berbeda (dari IP yang sama) tidak ikut terhambat', async () => {
    for (let i = 0; i < 9; i += 1) await login('korban@santaagnes.test');
    expect((await login('lain@santaagnes.test')).status).toBe(401);
  });

  it('huruf besar/kecil dan spasi pada email tidak menghindari batas', async () => {
    for (let i = 0; i < 8; i += 1) await login(i % 2 ? ' KORBAN@santaagnes.test ' : 'korban@santaagnes.test');
    expect((await login('Korban@SantaAgnes.test')).status).toBe(429);
  });

  it('request paralel tidak bisa melewati batas: hanya 8 yang sampai ke database', async () => {
    const results = await Promise.all(Array.from({ length: 20 }, () => login('korban@santaagnes.test')));

    expect(results.filter((r) => r.status === 401)).toHaveLength(8);
    expect(results.filter((r) => r.status === 429)).toHaveLength(12);
    expect(prisma.user.findUnique).toHaveBeenCalledTimes(8);
  });

  it('login berhasil membersihkan hitungan gagal sebelumnya', async () => {
    for (let i = 0; i < 7; i += 1) await login(user.email);

    prisma.user.findUnique.mockResolvedValue(user);
    expect((await login(user.email, PASSWORD)).status).toBe(200);

    prisma.user.findUnique.mockResolvedValue(null);
    for (let i = 0; i < 8; i += 1) expect((await login(user.email)).status).toBe(401);
    expect((await login(user.email)).status).toBe(429);
  });

  it('request dengan format salah (bukan login gagal) tidak dihitung', async () => {
    for (let i = 0; i < 12; i += 1) {
      const res = await request(app).post('/api/v1/auth/login').send({ email: 'korban@santaagnes.test', password: '' });
      expect(res.status).toBe(400);
    }
    expect((await login('korban@santaagnes.test')).status).toBe(401);
  });

  it('body tanpa email string tidak dibatasi per email (ditolak validasi)', async () => {
    const res = await request(app).post('/api/v1/auth/login').send({ email: { $ne: null }, password: 'x' });
    expect(res.status).toBe(400);
  });

  it('setelah jendela 15 menit lewat, percobaan boleh lagi', async () => {
    const now = Date.now();
    const spy = jest.spyOn(Date, 'now').mockReturnValue(now);
    for (let i = 0; i < 9; i += 1) await login('korban@santaagnes.test');
    expect((await login('korban@santaagnes.test')).status).toBe(429);

    spy.mockReturnValue(now + 15 * MINUTE + 1000);
    expect((await login('korban@santaagnes.test')).status).toBe(401);
  });
});

describe('ganti password', () => {
  const token = (id) => `Bearer ${jwt.sign({ role: 'STUDENT' }, process.env.JWT_ACCESS_SECRET, { subject: String(id), expiresIn: 900 })}`;
  const change = (id, currentPassword) =>
    request(app)
      .patch('/api/v1/me/password')
      .set('Authorization', token(id))
      .send({ currentPassword, newPassword: 'password-baru-1' });

  beforeEach(() => {
    prisma.user.findUnique.mockImplementation(({ where }) => Promise.resolve({ ...user, id: where.id }));
    prisma.user.update.mockResolvedValue({});
    prisma.refreshToken.updateMany.mockResolvedValue({ count: 0 });
  });

  it('password lama salah ke-11 pada akun yang sama: 429', async () => {
    for (let i = 0; i < 10; i += 1) expect((await change(5, 'salah-salah-1')).status).toBe(400);
    const res = await change(5, 'salah-salah-1');
    expect(res.status).toBe(429);
    expect(res.body.error.code).toBe('TOO_MANY_REQUESTS');
  });

  it('akun lain tidak terpengaruh; ganti password yang berhasil tidak dihitung', async () => {
    for (let i = 0; i < 10; i += 1) await change(5, 'salah-salah-1');
    expect((await change(6, 'salah-salah-1')).status).toBe(400);

    resetRateLimits();
    for (let i = 0; i < 12; i += 1) expect((await change(5, PASSWORD)).status).toBe(200);
  });
});

describe('refresh', () => {
  it('banjir request refresh dari satu IP dibatasi (300 per 15 menit)', async () => {
    prisma.refreshToken.findUnique.mockResolvedValue(null);
    const send = () => request(app).post('/api/v1/auth/refresh').send({ refreshToken: 'x' });

    const results = await Promise.all(Array.from({ length: 305 }, send));
    expect(results.filter((r) => r.status === 401)).toHaveLength(300);
    expect(results.filter((r) => r.status === 429)).toHaveLength(5);
  });
});

describe('createLimiter', () => {
  function appWith(limiter) {
    const small = express();
    small.get('/x', limiter, (req, res) => res.status(Number(req.query.status ?? 200)).json({}));
    small.use(errorHandler);
    return small;
  }

  it('hanya menghitung status yang tidak di-refund, dan membersihkan kunci pada clearOn', async () => {
    const limiter = createLimiter({
      windowMs: MINUTE,
      limit: 2,
      key: () => 'k',
      refundOn: (status) => status !== 401,
      clearOn: (status) => status === 204,
    });
    const small = appWith(limiter);

    await request(small).get('/x?status=401');
    await request(small).get('/x?status=200'); // di-refund
    await request(small).get('/x?status=200');
    expect((await request(small).get('/x?status=401')).status).toBe(401);
    expect((await request(small).get('/x?status=401')).status).toBe(429);

    expect((await request(small).get('/x?status=401')).status).toBe(429);
    resetRateLimits();
    await request(small).get('/x?status=401');
    await request(small).get('/x?status=204'); // dihitung lalu dibersihkan
    expect((await request(small).get('/x?status=401')).status).toBe(401);
    expect((await request(small).get('/x?status=401')).status).toBe(401);
    expect((await request(small).get('/x?status=401')).status).toBe(429);
  });

  it('key null = tidak dibatasi', async () => {
    const small = appWith(createLimiter({ windowMs: MINUTE, limit: 1, key: () => null }));
    for (let i = 0; i < 5; i += 1) expect((await request(small).get('/x')).status).toBe(200);
  });
});
