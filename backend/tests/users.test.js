process.env.JWT_ACCESS_SECRET = 'test-secret-yang-panjangnya-lebih-dari-32-karakter';
process.env.JWT_ACCESS_TTL = '15m';
process.env.JWT_REFRESH_TTL = '7d';

jest.mock('../src/lib/prisma', () => {
  const db = {
    user: {
      findUnique: jest.fn(),
      findMany: jest.fn(),
      count: jest.fn(),
      create: jest.fn(),
      update: jest.fn(),
      delete: jest.fn(),
    },
    refreshToken: { updateMany: jest.fn() },
    deviceToken: { deleteMany: jest.fn() },
    $transaction: jest.fn(),
  };
  db.$transaction.mockImplementation((arg) =>
    typeof arg === 'function' ? arg(db) : Promise.all(arg),
  );
  return db;
});

const jwt = require('jsonwebtoken');
const request = require('supertest');
const prisma = require('../src/lib/prisma');
const app = require('../src/app');

const ADMIN_ID = 1;
const TARGET_ID = 2;

const tokenFor = (id, role = 'ADMIN') =>
  jwt.sign({ role }, process.env.JWT_ACCESS_SECRET, {
    algorithm: 'HS256',
    subject: String(id),
    expiresIn: 900,
  });

beforeEach(() => {
  for (const model of [prisma.user, prisma.refreshToken, prisma.deviceToken]) {
    for (const fn of Object.values(model)) fn.mockReset();
  }
  prisma.$transaction.mockClear();
  prisma.refreshToken.updateMany.mockResolvedValue({ count: 1 });
  prisma.deviceToken.deleteMany.mockResolvedValue({ count: 1 });

  // Default middleware authenticate mock: user pemanggil adalah admin aktif
  prisma.user.findUnique.mockImplementation(({ where }) => {
    if (where.id === ADMIN_ID) {
      return Promise.resolve({ id: ADMIN_ID, role: 'ADMIN', status: 'ACTIVE' });
    }
    return Promise.resolve(null);
  });
});

describe('Otorisasi Modul Users (/api/v1/users)', () => {
  it('menolak request tanpa token (401 UNAUTHENTICATED)', async () => {
    const res = await request(app).get('/api/v1/users');
    expect(res.status).toBe(401);
    expect(res.body.error.code).toBe('UNAUTHENTICATED');
  });

  it('menolak request dari role selain ADMIN (403 FORBIDDEN)', async () => {
    const studentToken = tokenFor(5, 'STUDENT');
    prisma.user.findUnique.mockImplementation(({ where }) => {
      if (where.id === 5) {
        return Promise.resolve({ id: 5, role: 'STUDENT', status: 'ACTIVE' });
      }
      return Promise.resolve(null);
    });

    const res = await request(app)
      .get('/api/v1/users')
      .set('Authorization', `Bearer ${studentToken}`);

    expect(res.status).toBe(403);
    expect(res.body.error.code).toBe('FORBIDDEN');
  });
});

describe('F-MST-01: List & Detail Pengguna', () => {
  it('mengembalikan daftar pengguna berpaginasi', async () => {
    const adminToken = tokenFor(ADMIN_ID);
    prisma.user.count.mockResolvedValue(1);
    prisma.user.findMany.mockResolvedValue([
      {
        id: 2,
        email: 'guru@test.com',
        name: 'Guru Budi',
        role: 'TEACHER',
        status: 'ACTIVE',
        createdAt: new Date(),
        updatedAt: new Date(),
      },
    ]);

    const res = await request(app)
      .get('/api/v1/users?page=1&limit=10&role=TEACHER')
      .set('Authorization', `Bearer ${adminToken}`);

    expect(res.status).toBe(200);
    expect(res.body.success).toBe(true);
    expect(res.body.meta).toEqual({ page: 1, limit: 10, total: 1, totalPages: 1 });
    expect(res.body.data).toHaveLength(1);
  });

  it('mengembalikan detail pengguna jika ada', async () => {
    const adminToken = tokenFor(ADMIN_ID);
    prisma.user.findUnique.mockImplementation(({ where }) => {
      if (where.id === ADMIN_ID) {
        return Promise.resolve({ id: ADMIN_ID, role: 'ADMIN', status: 'ACTIVE' });
      }
      if (where.id === TARGET_ID) {
        return Promise.resolve({
          id: TARGET_ID,
          email: 'target@test.com',
          name: 'Target User',
          role: 'STUDENT',
          status: 'ACTIVE',
          teacher: null,
          student: { id: 1, nis: '26001', birthDate: new Date('2010-01-01'), phone: null },
          guardian: null,
          createdAt: new Date(),
          updatedAt: new Date(),
        });
      }
      return Promise.resolve(null);
    });

    const res = await request(app)
      .get(`/api/v1/users/${TARGET_ID}`)
      .set('Authorization', `Bearer ${adminToken}`);

    expect(res.status).toBe(200);
    expect(res.body.data.id).toBe(TARGET_ID);
    expect(res.body.data.student.nis).toBe('26001');
  });

  it('mengembalikan 404 jika pengguna tidak ditemukan', async () => {
    const adminToken = tokenFor(ADMIN_ID);
    const res = await request(app)
      .get('/api/v1/users/999')
      .set('Authorization', `Bearer ${adminToken}`);

    expect(res.status).toBe(404);
    expect(res.body.error.code).toBe('NOT_FOUND');
  });
});

describe('F-MST-01: Tambah Pengguna Baru', () => {
  it('berhasil membuat pengguna baru dengan password awal (201)', async () => {
    const adminToken = tokenFor(ADMIN_ID);
    prisma.user.findUnique.mockImplementation(({ where }) => {
      if (where.id === ADMIN_ID) {
        return Promise.resolve({ id: ADMIN_ID, role: 'ADMIN', status: 'ACTIVE' });
      }
      return Promise.resolve(null); // email belum terdaftar
    });

    prisma.user.create.mockResolvedValue({
      id: 3,
      email: 'guru.baru@test.com',
      name: 'Guru Baru',
      role: 'TEACHER',
      status: 'ACTIVE',
      createdAt: new Date(),
      updatedAt: new Date(),
    });

    const res = await request(app)
      .post('/api/v1/users')
      .set('Authorization', `Bearer ${adminToken}`)
      .send({
        email: 'guru.baru@test.com',
        password: 'passwordAwal123',
        name: 'Guru Baru',
        role: 'TEACHER',
      });

    expect(res.status).toBe(201);
    expect(res.body.data.email).toBe('guru.baru@test.com');
  });

  it('menolak email yang sudah terdaftar (409 DUPLICATE)', async () => {
    const adminToken = tokenFor(ADMIN_ID);
    prisma.user.findUnique.mockImplementation(({ where }) => {
      if (where.id === ADMIN_ID) {
        return Promise.resolve({ id: ADMIN_ID, role: 'ADMIN', status: 'ACTIVE' });
      }
      if (where.email === 'sudahada@test.com') {
        return Promise.resolve({ id: 10 });
      }
      return Promise.resolve(null);
    });

    const res = await request(app)
      .post('/api/v1/users')
      .set('Authorization', `Bearer ${adminToken}`)
      .send({
        email: 'sudahada@test.com',
        password: 'passwordAwal123',
        name: 'User Kembar',
        role: 'TEACHER',
      });

    expect(res.status).toBe(409);
    expect(res.body.error.code).toBe('DUPLICATE');
  });

  it('menolak password yang kurang dari 8 karakter (400 VALIDATION_ERROR)', async () => {
    const adminToken = tokenFor(ADMIN_ID);
    const res = await request(app)
      .post('/api/v1/users')
      .set('Authorization', `Bearer ${adminToken}`)
      .send({
        email: 'pendek@test.com',
        password: 'pendek',
        name: 'User Pendek',
        role: 'STUDENT',
      });

    expect(res.status).toBe(400);
    expect(res.body.error.code).toBe('VALIDATION_ERROR');
  });
});

describe('F-MST-01: Blokir dan Buka Blokir Pengguna', () => {
  it('berhasil memblokir pengguna dan mencabut token sesinya', async () => {
    const adminToken = tokenFor(ADMIN_ID);
    prisma.user.findUnique.mockImplementation(({ where }) => {
      if (where.id === ADMIN_ID) {
        return Promise.resolve({ id: ADMIN_ID, role: 'ADMIN', status: 'ACTIVE' });
      }
      if (where.id === TARGET_ID) {
        return Promise.resolve({ id: TARGET_ID, status: 'ACTIVE' });
      }
      return Promise.resolve(null);
    });

    prisma.user.update.mockResolvedValue({
      id: TARGET_ID,
      email: 'target@test.com',
      name: 'Target',
      role: 'STUDENT',
      status: 'BLOCKED',
    });

    const res = await request(app)
      .patch(`/api/v1/users/${TARGET_ID}/status`)
      .set('Authorization', `Bearer ${adminToken}`)
      .send({ status: 'BLOCKED' });

    expect(res.status).toBe(200);
    expect(res.body.data.status).toBe('BLOCKED');
    expect(prisma.refreshToken.updateMany).toHaveBeenCalledWith(
      expect.objectContaining({ where: { userId: TARGET_ID, revokedAt: null } }),
    );
  });

  it('admin tidak dapat memblokir akunnya sendiri (409 INVALID_STATE)', async () => {
    const adminToken = tokenFor(ADMIN_ID);
    const res = await request(app)
      .patch(`/api/v1/users/${ADMIN_ID}/status`)
      .set('Authorization', `Bearer ${adminToken}`)
      .send({ status: 'BLOCKED' });

    expect(res.status).toBe(409);
    expect(res.body.error.code).toBe('INVALID_STATE');
  });
});

describe('F-MST-02: Atur Role Pengguna', () => {
  it('berhasil mengubah role pengguna', async () => {
    const adminToken = tokenFor(ADMIN_ID);
    prisma.user.findUnique.mockImplementation(({ where }) => {
      if (where.id === ADMIN_ID) {
        return Promise.resolve({ id: ADMIN_ID, role: 'ADMIN', status: 'ACTIVE' });
      }
      if (where.id === TARGET_ID) {
        return Promise.resolve({
          id: TARGET_ID,
          role: 'TEACHER',
          teacher: null,
          student: null,
          guardian: null,
        });
      }
      return Promise.resolve(null);
    });

    prisma.user.update.mockResolvedValue({
      id: TARGET_ID,
      role: 'ADMIN',
    });

    const res = await request(app)
      .patch(`/api/v1/users/${TARGET_ID}/role`)
      .set('Authorization', `Bearer ${adminToken}`)
      .send({ role: 'ADMIN' });

    expect(res.status).toBe(200);
    expect(res.body.data.role).toBe('ADMIN');
  });

  it('admin tidak dapat mengubah role akunnya sendiri ke non-admin (409 INVALID_STATE)', async () => {
    const adminToken = tokenFor(ADMIN_ID);
    const res = await request(app)
      .patch(`/api/v1/users/${ADMIN_ID}/role`)
      .set('Authorization', `Bearer ${adminToken}`)
      .send({ role: 'TEACHER' });

    expect(res.status).toBe(409);
    expect(res.body.error.code).toBe('INVALID_STATE');
  });

  it('menolak perubahan role jika akun sudah memiliki data profil yang bentrok (409 INVALID_STATE)', async () => {
    const adminToken = tokenFor(ADMIN_ID);
    prisma.user.findUnique.mockImplementation(({ where }) => {
      if (where.id === ADMIN_ID) {
        return Promise.resolve({ id: ADMIN_ID, role: 'ADMIN', status: 'ACTIVE' });
      }
      if (where.id === TARGET_ID) {
        return Promise.resolve({
          id: TARGET_ID,
          role: 'STUDENT',
          student: { id: 10 },
          teacher: null,
          guardian: null,
        });
      }
      return Promise.resolve(null);
    });

    const res = await request(app)
      .patch(`/api/v1/users/${TARGET_ID}/role`)
      .set('Authorization', `Bearer ${adminToken}`)
      .send({ role: 'TEACHER' });

    expect(res.status).toBe(409);
    expect(res.body.error.code).toBe('INVALID_STATE');
  });
});

describe('F-MST-03: Reset Password Pengguna oleh Admin', () => {
  it('berhasil mereset password pengguna dan mencabut token aktifnya', async () => {
    const adminToken = tokenFor(ADMIN_ID);
    prisma.user.findUnique.mockImplementation(({ where }) => {
      if (where.id === ADMIN_ID) {
        return Promise.resolve({ id: ADMIN_ID, role: 'ADMIN', status: 'ACTIVE' });
      }
      if (where.id === TARGET_ID) {
        return Promise.resolve({ id: TARGET_ID });
      }
      return Promise.resolve(null);
    });

    const res = await request(app)
      .post(`/api/v1/users/${TARGET_ID}/reset-password`)
      .set('Authorization', `Bearer ${adminToken}`)
      .send({ newPassword: 'passwordBaru123' });

    expect(res.status).toBe(200);
    expect(prisma.refreshToken.updateMany).toHaveBeenCalledWith(
      expect.objectContaining({ where: { userId: TARGET_ID, revokedAt: null } }),
    );
  });
});

describe('F-MST-01: Hapus Pengguna', () => {
  it('admin tidak dapat menghapus akunnya sendiri (409 INVALID_STATE)', async () => {
    const adminToken = tokenFor(ADMIN_ID);
    prisma.user.findUnique.mockImplementation(({ where }) => {
      if (where.id === ADMIN_ID) {
        return Promise.resolve({
          id: ADMIN_ID,
          role: 'ADMIN',
          status: 'ACTIVE',
          teacher: null,
          student: null,
          guardian: null,
          announcements: [],
          facilityBookings: [],
        });
      }
      return Promise.resolve(null);
    });

    const res = await request(app)
      .delete(`/api/v1/users/${ADMIN_ID}`)
      .set('Authorization', `Bearer ${adminToken}`);

    expect(res.status).toBe(409);
    expect(res.body.error.code).toBe('INVALID_STATE');
  });

  it('menolak penghapusan pengguna yang memiliki data profil/akademik (409 INVALID_STATE)', async () => {
    const adminToken = tokenFor(ADMIN_ID);
    prisma.user.findUnique.mockImplementation(({ where }) => {
      if (where.id === ADMIN_ID) {
        return Promise.resolve({ id: ADMIN_ID, role: 'ADMIN', status: 'ACTIVE' });
      }
      if (where.id === TARGET_ID) {
        return Promise.resolve({
          id: TARGET_ID,
          role: 'STUDENT',
          teacher: null,
          student: { id: 1 },
          guardian: null,
          announcements: [],
          facilityBookings: [],
        });
      }
      return Promise.resolve(null);
    });

    const res = await request(app)
      .delete(`/api/v1/users/${TARGET_ID}`)
      .set('Authorization', `Bearer ${adminToken}`);

    expect(res.status).toBe(409);
    expect(res.body.error.code).toBe('INVALID_STATE');
  });

  it('berhasil menghapus pengguna yang belum memiliki data akademik (204)', async () => {
    const adminToken = tokenFor(ADMIN_ID);
    prisma.user.findUnique.mockImplementation(({ where }) => {
      if (where.id === ADMIN_ID) {
        return Promise.resolve({ id: ADMIN_ID, role: 'ADMIN', status: 'ACTIVE' });
      }
      if (where.id === TARGET_ID) {
        return Promise.resolve({
          id: TARGET_ID,
          role: 'STUDENT',
          teacher: null,
          student: null,
          guardian: null,
          announcements: [],
          facilityBookings: [],
        });
      }
      return Promise.resolve(null);
    });

    prisma.user.delete.mockResolvedValue({});

    const res = await request(app)
      .delete(`/api/v1/users/${TARGET_ID}`)
      .set('Authorization', `Bearer ${adminToken}`);

    expect(res.status).toBe(204);
    expect(prisma.user.delete).toHaveBeenCalledWith({ where: { id: TARGET_ID } });
  });
});
