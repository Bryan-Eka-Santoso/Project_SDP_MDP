process.env.JWT_ACCESS_SECRET = 'test-secret-yang-panjangnya-lebih-dari-32-karakter';

jest.mock('../src/lib/prisma', () => ({
  user: { findUnique: jest.fn() },
  deviceToken: { upsert: jest.fn(), findMany: jest.fn(), deleteMany: jest.fn() },
}));
// FCM tiruan: test tidak boleh mengirim notifikasi sungguhan.
jest.mock('../src/lib/fcm', () => ({ getFcm: jest.fn() }));

const jwt = require('jsonwebtoken');
const request = require('supertest');
const prisma = require('../src/lib/prisma');
const { getFcm } = require('../src/lib/fcm');
const app = require('../src/app');
const { dispatch, BATCH_SIZE } = require('../src/jobs/notificationDispatcher');
const { notify } = require('../src/modules/notifications/notifications.service');

const tokenFor = (id) =>
  jwt.sign({ role: 'STUDENT' }, process.env.JWT_ACCESS_SECRET, { subject: String(id), expiresIn: 900 });

const payload = { type: 'SCHEDULE_CHANGE', title: 'Perubahan jadwal', body: 'Matematika pindah ke Lab', referenceId: 12 };

// Hasil sendEachForMulticast tiruan: token yang diawali "stale" dianggap sudah tidak terdaftar.
function fakeMessaging() {
  return {
    sendEachForMulticast: jest.fn(async ({ tokens }) => {
      const responses = tokens.map((t) =>
        t.startsWith('stale')
          ? { success: false, error: { code: 'messaging/registration-token-not-registered' } }
          : { success: true },
      );
      const failureCount = responses.filter((r) => !r.success).length;
      return { responses, successCount: tokens.length - failureCount, failureCount };
    }),
  };
}

beforeEach(() => {
  jest.clearAllMocks();
  prisma.deviceToken.deleteMany.mockResolvedValue({ count: 0 });
});

describe('POST /api/v1/me/device-tokens', () => {
  it('201: token disimpan atas nama user yang login', async () => {
    prisma.user.findUnique.mockResolvedValue({ id: 5, role: 'STUDENT', status: 'ACTIVE' });
    prisma.deviceToken.upsert.mockResolvedValue({});

    const res = await request(app)
      .post('/api/v1/me/device-tokens')
      .set('Authorization', `Bearer ${tokenFor(5)}`)
      .send({ token: 'fcm-abc', platform: 'ANDROID' });

    expect(res.status).toBe(201);
    expect(prisma.deviceToken.upsert).toHaveBeenCalledWith({
      where: { token: 'fcm-abc' },
      update: { userId: 5 },
      create: { token: 'fcm-abc', userId: 5 },
    });
  });

  it('400 jika token kosong', async () => {
    prisma.user.findUnique.mockResolvedValue({ id: 5, role: 'STUDENT', status: 'ACTIVE' });
    const res = await request(app)
      .post('/api/v1/me/device-tokens')
      .set('Authorization', `Bearer ${tokenFor(5)}`)
      .send({ token: '' });
    expect(res.status).toBe(400);
    expect(prisma.deviceToken.upsert).not.toHaveBeenCalled();
  });

  it('401 tanpa login', async () => {
    const res = await request(app).post('/api/v1/me/device-tokens').send({ token: 'fcm-abc' });
    expect(res.status).toBe(401);
  });
});

describe('dispatch', () => {
  it('mengirim data message berisi string ke semua token user', async () => {
    const fcm = fakeMessaging();
    getFcm.mockReturnValue(fcm);
    prisma.deviceToken.findMany.mockResolvedValue([{ token: 'a' }, { token: 'b' }]);

    const result = await dispatch([5, 5, 6], payload);

    // userId duplikat dibuang sebelum query.
    expect(prisma.deviceToken.findMany.mock.calls[0][0].where).toEqual({ userId: { in: [5, 6] } });
    const message = fcm.sendEachForMulticast.mock.calls[0][0];
    expect(message.tokens).toEqual(['a', 'b']);
    expect(message.notification).toBeUndefined();
    expect(message.data).toEqual({
      type: 'SCHEDULE_CHANGE',
      title: 'Perubahan jadwal',
      body: 'Matematika pindah ke Lab',
      referenceId: '12',
      sentAt: expect.any(String),
    });
    expect(result).toEqual({ tokens: 2, sent: 2, failed: 0, removed: 0 });
  });

  it('dipecah per 500 token', async () => {
    const fcm = fakeMessaging();
    getFcm.mockReturnValue(fcm);
    prisma.deviceToken.findMany.mockResolvedValue(
      Array.from({ length: 1200 }, (_, i) => ({ token: `t${i}` })),
    );

    await dispatch([1], payload);

    const sizes = fcm.sendEachForMulticast.mock.calls.map(([m]) => m.tokens.length);
    expect(sizes).toEqual([BATCH_SIZE, BATCH_SIZE, 200]);
  });

  it('menghapus token yang sudah tidak terdaftar', async () => {
    getFcm.mockReturnValue(fakeMessaging());
    prisma.deviceToken.findMany.mockResolvedValue([{ token: 'ok' }, { token: 'stale-1' }]);

    const result = await dispatch([1], payload);

    expect(prisma.deviceToken.deleteMany).toHaveBeenCalledWith({ where: { token: { in: ['stale-1'] } } });
    expect(result).toMatchObject({ sent: 1, failed: 1, removed: 1 });
  });

  it('mode log jika Firebase belum dikonfigurasi', async () => {
    getFcm.mockReturnValue(null);
    prisma.deviceToken.findMany.mockResolvedValue([{ token: 'a' }]);
    const info = jest.spyOn(console, 'info').mockImplementation(() => {});

    const result = await dispatch([1], payload);

    expect(info).toHaveBeenCalledWith(expect.stringContaining('SCHEDULE_CHANGE'));
    expect(result.sent).toBe(0);
    info.mockRestore();
  });

  it('tidak memanggil FCM jika penerima tidak punya token', async () => {
    const fcm = fakeMessaging();
    getFcm.mockReturnValue(fcm);
    prisma.deviceToken.findMany.mockResolvedValue([]);

    await dispatch([1], payload);
    await dispatch([], payload);

    expect(fcm.sendEachForMulticast).not.toHaveBeenCalled();
    expect(prisma.deviceToken.findMany).toHaveBeenCalledTimes(1);
  });
});

describe('notify', () => {
  it('menolak jenis notifikasi yang tidak dikenal', () => {
    expect(() => notify([1], { ...payload, type: 'RANDOM' })).toThrow('tidak dikenal');
  });

  it('tidak menunggu pengiriman dan memotong body yang terlalu panjang', async () => {
    const fcm = fakeMessaging();
    getFcm.mockReturnValue(fcm);
    prisma.deviceToken.findMany.mockResolvedValue([{ token: 'a' }]);

    notify([1], { ...payload, body: 'x'.repeat(600) });
    expect(fcm.sendEachForMulticast).not.toHaveBeenCalled(); // belum terkirim saat notify() kembali

    await new Promise((resolve) => setImmediate(resolve));
    await new Promise((resolve) => setImmediate(resolve));

    const { body } = fcm.sendEachForMulticast.mock.calls[0][0].data;
    expect(body).toHaveLength(500);
    expect(body.endsWith('…')).toBe(true);
  });
});
