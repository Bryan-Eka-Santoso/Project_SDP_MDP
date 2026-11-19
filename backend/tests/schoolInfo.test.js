process.env.JWT_ACCESS_SECRET = 'test-secret-yang-panjangnya-lebih-dari-32-karakter';

jest.mock('../src/lib/prisma', () => ({
  user: {
    findUnique: jest.fn(),
    findMany: jest.fn(),
  },
  academicEvent: {
    count: jest.fn(),
    findMany: jest.fn(),
    findUnique: jest.fn(),
    create: jest.fn(),
    update: jest.fn(),
    delete: jest.fn(),
  },
  announcement: {
    count: jest.fn(),
    findMany: jest.fn(),
    findFirst: jest.fn(),
    findUnique: jest.fn(),
    create: jest.fn(),
    update: jest.fn(),
    delete: jest.fn(),
  },
  classGroup: {
    findFirst: jest.fn(),
    findUnique: jest.fn(),
  },
  studentGuardian: {
    findFirst: jest.fn(),
  },
}));

jest.mock('../src/modules/notifications/notifications.service', () => ({
  notify: jest.fn(),
  registerDeviceToken: jest.fn(),
}));

jest.mock('../src/modules/notifications/notifications.recipients', () => ({
  activeUserIdsByRoles: jest.fn(),
  studentUserIdsOfClasses: jest.fn(),
  guardianUserIdsOfClasses: jest.fn(),
  guardianUserIdsOfStudents: jest.fn(),
}));

jest.mock('../src/lib/cloudinary', () => ({
  uploadBuffer: jest.fn(),
  destroyAsset: jest.fn().mockResolvedValue(undefined),
  isConfigured: jest.fn(),
}));

const jwt = require('jsonwebtoken');
const request = require('supertest');
const prisma = require('../src/lib/prisma');
const cloudinary = require('../src/lib/cloudinary');
const AppError = require('../src/utils/AppError');
const { notify } = require('../src/modules/notifications/notifications.service');
const recipients = require('../src/modules/notifications/notifications.recipients');
const app = require('../src/app');

function auth(role = 'ADMIN', userId = 1) {
  prisma.user.findUnique.mockResolvedValue({ id: userId, role, status: 'ACTIVE' });
  return `Bearer ${jwt.sign({ role }, process.env.JWT_ACCESS_SECRET, {
    subject: String(userId),
    expiresIn: 900,
  })}`;
}

beforeEach(() => {
  jest.clearAllMocks();
});

describe('F-KAL-01 & F-KAL-02: Kalender Akademik', () => {
  const eventRow = (overrides = {}) => ({
    id: 1,
    title: 'Penilaian Tengah Semester',
    type: 'EXAM',
    startDate: new Date('2026-09-21T00:00:00.000Z'),
    endDate: new Date('2026-09-26T00:00:00.000Z'),
    description: 'PTS semester ganjil',
    createdAt: new Date('2026-07-01T00:00:00.000Z'),
    updatedAt: new Date('2026-07-01T00:00:00.000Z'),
    ...overrides,
  });

  it('GET /academic-events bisa diakses semua role & mendukung filter type/rentang tanggal', async () => {
    prisma.academicEvent.count.mockResolvedValue(1);
    prisma.academicEvent.findMany.mockResolvedValue([eventRow()]);

    const res = await request(app)
      .get('/api/v1/academic-events?type=EXAM&from=2026-09-01&to=2026-09-30&q=Tengah')
      .set('Authorization', auth('STUDENT', 10));

    expect(res.status).toBe(200);
    expect(res.body.data[0]).toMatchObject({
      id: 1,
      title: 'Penilaian Tengah Semester',
      type: 'EXAM',
      startDate: '2026-09-21',
      endDate: '2026-09-26',
    });
    expect(prisma.academicEvent.findMany).toHaveBeenCalledWith(
      expect.objectContaining({
        where: {
          type: 'EXAM',
          endDate: { gte: new Date('2026-09-01T00:00:00.000Z') },
          startDate: { lte: new Date('2026-09-30T00:00:00.000Z') },
          OR: [{ title: { contains: 'Tengah' } }, { description: { contains: 'Tengah' } }],
        },
      }),
    );
  });

  it('POST/PATCH/DELETE /academic-events hanya Admin, memvalidasi startDate <= endDate', async () => {
    const teacherRes = await request(app)
      .post('/api/v1/academic-events')
      .set('Authorization', auth('TEACHER', 2))
      .send({
        title: 'Libur',
        type: 'HOLIDAY',
        startDate: '2026-08-17',
        endDate: '2026-08-17',
      });
    expect(teacherRes.status).toBe(403);

    const badRange = await request(app)
      .post('/api/v1/academic-events')
      .set('Authorization', auth('ADMIN', 1))
      .send({
        title: 'Salah Tanggal',
        type: 'ACTIVITY',
        startDate: '2026-10-10',
        endDate: '2026-10-08',
      });
    expect(badRange.status).toBe(400);

    prisma.academicEvent.create.mockResolvedValue(
      eventRow({
        id: 7,
        title: 'HUT RI',
        type: 'HOLIDAY',
        startDate: new Date('2026-08-17T00:00:00.000Z'),
        endDate: new Date('2026-08-17T00:00:00.000Z'),
      }),
    );
    const created = await request(app)
      .post('/api/v1/academic-events')
      .set('Authorization', auth('ADMIN', 1))
      .send({
        title: 'HUT RI',
        type: 'HOLIDAY',
        startDate: '2026-08-17',
        endDate: '2026-08-17',
      });
    expect(created.status).toBe(201);

    // Partial update: startDate sesudah endDate existing -> 400
    prisma.academicEvent.findUnique.mockResolvedValue(eventRow());
    const badPatch = await request(app)
      .patch('/api/v1/academic-events/1')
      .set('Authorization', auth('ADMIN', 1))
      .send({ startDate: '2026-09-29' });
    expect(badPatch.status).toBe(400);

    const deleted = await request(app)
      .delete('/api/v1/academic-events/1')
      .set('Authorization', auth('ADMIN', 1));
    expect(deleted.status).toBe(204);
  });
});

describe('F-PGM-01, F-PGM-02, F-PGM-03: Pengumuman & Lampiran Cloudinary', () => {
  const announcementRow = (overrides = {}) => ({
    id: 12,
    authorId: 1,
    classGroupId: null,
    title: 'Libur Awal Semester',
    content: 'Kegiatan KBM dimulai Senin.',
    scope: 'SCHOOL',
    date: new Date('2026-07-13T00:00:00.000Z'),
    attachmentUrl: null,
    attachmentPublicId: null,
    createdAt: new Date('2026-07-12T00:00:00.000Z'),
    updatedAt: new Date('2026-07-12T00:00:00.000Z'),
    author: { id: 1, name: 'Administrator', role: 'ADMIN' },
    classGroup: null,
    ...overrides,
  });

  it('POST /announcements (SCHOOL) oleh Admin menyimpan data dan memicu notifikasi FCM ke user aktif', async () => {
    prisma.announcement.create.mockResolvedValue(announcementRow());
    recipients.activeUserIdsByRoles.mockResolvedValue([1, 20, 21]);

    const res = await request(app)
      .post('/api/v1/announcements')
      .set('Authorization', auth('ADMIN', 1))
      .send({
        title: 'Libur Awal Semester',
        content: 'Kegiatan KBM dimulai Senin.',
        scope: 'SCHOOL',
        date: '2026-07-13',
      });

    expect(res.status).toBe(201);
    expect(res.body.data.scope).toBe('SCHOOL');
    expect(notify).toHaveBeenCalledWith([20, 21], {
      type: 'ANNOUNCEMENT',
      title: 'Pengumuman: Libur Awal Semester',
      body: 'Kegiatan KBM dimulai Senin.',
      referenceId: 12,
    });
  });

  it('POST /announcements (CLASS) hanya wali kelas aktif dari kelas binaan dan memicu notifikasi ke siswa & wali murid', async () => {
    // Bukan wali kelas kelas target -> 403
    prisma.classGroup.findUnique.mockResolvedValue({
      id: 3,
      name: 'X-1',
      academicYear: { isActive: true },
      homeroomTeacher: { userId: 99 },
    });
    const notOwner = await request(app)
      .post('/api/v1/announcements')
      .set('Authorization', auth('TEACHER', 5))
      .send({
        title: 'Info Study Tour',
        content: 'Bawa surat izin.',
        scope: 'CLASS',
        classGroupId: 3,
      });
    expect(notOwner.status).toBe(403);

    // Wali kelas sah -> 201 + notifikasi ke siswa & wali murid kelas tsb
    prisma.classGroup.findUnique.mockResolvedValue({
      id: 3,
      name: 'X-1',
      academicYear: { isActive: true },
      homeroomTeacher: { userId: 5 },
    });
    prisma.announcement.create.mockResolvedValue(
      announcementRow({
        id: 25,
        authorId: 5,
        classGroupId: 3,
        scope: 'CLASS',
        title: 'Info Study Tour',
        content: 'Bawa surat izin.',
        author: { id: 5, name: 'Bu Maria', role: 'TEACHER' },
        classGroup: { id: 3, name: 'X-1' },
      }),
    );
    recipients.studentUserIdsOfClasses.mockResolvedValue([30, 31]);
    recipients.guardianUserIdsOfClasses.mockResolvedValue([40, 31]);

    const ok = await request(app)
      .post('/api/v1/announcements')
      .set('Authorization', auth('TEACHER', 5))
      .send({
        title: 'Info Study Tour',
        content: 'Bawa surat izin.',
        scope: 'CLASS',
        classGroupId: 3,
      });

    expect(ok.status).toBe(201);
    expect(notify).toHaveBeenCalledWith([30, 31, 40], {
      type: 'ANNOUNCEMENT',
      title: 'Pengumuman X-1: Info Study Tour',
      body: 'Bawa surat izin.',
      referenceId: 25,
    });

    // Admin mengirim pengumuman ke kelas spesifik -> 201 + notifikasi ke kelas tsb
    prisma.classGroup.findUnique.mockResolvedValue({
      id: 4,
      name: 'XI-IPA-1',
      academicYear: { isActive: true },
      homeroomTeacher: { userId: 77 },
    });
    prisma.announcement.create.mockResolvedValue(
      announcementRow({
        id: 26,
        authorId: 1,
        classGroupId: 4,
        scope: 'CLASS',
        title: 'Ujian Praktik Fisika',
        content: 'Bawa jas lab.',
        author: { id: 1, name: 'Administrator', role: 'ADMIN' },
        classGroup: { id: 4, name: 'XI-IPA-1' },
      }),
    );
    recipients.studentUserIdsOfClasses.mockResolvedValue([50]);
    recipients.guardianUserIdsOfClasses.mockResolvedValue([60]);

    const adminClassOk = await request(app)
      .post('/api/v1/announcements')
      .set('Authorization', auth('ADMIN', 1))
      .send({
        title: 'Ujian Praktik Fisika',
        content: 'Bawa jas lab.',
        scope: 'CLASS',
        classGroupId: 4,
      });
    expect(adminClassOk.status).toBe(201);
    expect(adminClassOk.body.data.classGroup.name).toBe('XI-IPA-1');
    expect(notify).toHaveBeenCalledWith([50, 60], {
      type: 'ANNOUNCEMENT',
      title: 'Pengumuman XI-IPA-1: Ujian Praktik Fisika',
      body: 'Bawa jas lab.',
      referenceId: 26,
    });

    // Kelas dari tahun ajaran tidak aktif ditolak (400)
    prisma.classGroup.findUnique.mockResolvedValue({
      id: 9,
      name: 'XII-1',
      academicYear: { isActive: false },
      homeroomTeacher: { userId: 5 },
    });
    const inactiveClass = await request(app)
      .post('/api/v1/announcements')
      .set('Authorization', auth('ADMIN', 1))
      .send({
        title: 'Info Lama',
        content: 'Konten.',
        scope: 'CLASS',
        classGroupId: 9,
      });
    expect(inactiveClass.status).toBe(400);
  });

  it('GET /announcements memfilter cakupan siswa & wali murid, serta menolak studentId untuk bukan wali murid', async () => {
    const nonGuardian = await request(app)
      .get('/api/v1/announcements?studentId=12')
      .set('Authorization', auth('STUDENT', 10));
    expect(nonGuardian.status).toBe(400);

    prisma.studentGuardian.findFirst.mockResolvedValue(null);
    const notOwnChild = await request(app)
      .get('/api/v1/announcements?studentId=99')
      .set('Authorization', auth('GUARDIAN', 40));
    expect(notOwnChild.status).toBe(404);

    prisma.studentGuardian.findFirst.mockResolvedValue({ studentId: 12 });
    prisma.announcement.count.mockResolvedValue(1);
    prisma.announcement.findMany.mockResolvedValue([announcementRow()]);

    const guardianOk = await request(app)
      .get('/api/v1/announcements?studentId=12')
      .set('Authorization', auth('GUARDIAN', 40));
    expect(guardianOk.status).toBe(200);
    expect(guardianOk.body.meta.total).toBe(1);
  });

  it('POST /announcements/attachment mengunggah JPG/PNG/PDF valid, menolak file palsu (415) & non-wali kelas (403)', async () => {
    // Guru bukan wali kelas -> 403 sebelum multer menampung file
    prisma.classGroup.findFirst.mockResolvedValue(null);
    const pngBytes = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x00]);
    const nonHomeroom = await request(app)
      .post('/api/v1/announcements/attachment')
      .set('Authorization', auth('TEACHER', 8))
      .attach('file', pngBytes, { filename: 'info.png', contentType: 'image/png' });
    expect(nonHomeroom.status).toBe(403);

    // Ekstensi .pdf tapi isi bukan %PDF (atau bytes PNG memakai ekstensi .pdf) -> 415
    const spoofed = await request(app)
      .post('/api/v1/announcements/attachment')
      .set('Authorization', auth('ADMIN', 1))
      .attach('file', Buffer.from('not-a-pdf'), { filename: 'surat.pdf', contentType: 'application/pdf' });
    expect(spoofed.status).toBe(415);

    const mismatchedExt = await request(app)
      .post('/api/v1/announcements/attachment')
      .set('Authorization', auth('ADMIN', 1))
      .attach('file', pngBytes, { filename: 'palsu.pdf', contentType: 'application/pdf' });
    expect(mismatchedExt.status).toBe(415);

    // PNG valid oleh Admin -> 201
    cloudinary.uploadBuffer.mockResolvedValue({
      url: 'https://res.cloudinary.com/demo/image/upload/v1/sim/announcements/abc.png',
      publicId: 'sim/announcements/abc',
      fileName: 'info.png',
      mimeType: 'image/png',
      size: pngBytes.length,
    });
    const uploaded = await request(app)
      .post('/api/v1/announcements/attachment')
      .set('Authorization', auth('ADMIN', 1))
      .attach('file', pngBytes, { filename: 'info.png', contentType: 'image/png' });
    expect(uploaded.status).toBe(201);
    expect(uploaded.body.data.publicId).toBe('sim/announcements/abc');
    expect(cloudinary.uploadBuffer).toHaveBeenCalledWith(
      expect.objectContaining({
        folder: 'sim/announcements',
        baseUrl: expect.stringMatching(/^http:\/\/127\.0\.0\.1:\d+$/),
      }),
    );

    // Jika penyimpanan eksternal gagal -> 502
    cloudinary.uploadBuffer.mockRejectedValue(
      new AppError(502, 'EXTERNAL_SERVICE_ERROR', 'Gagal menghubungi layanan penyimpanan.'),
    );
    const upstreamError = await request(app)
      .post('/api/v1/announcements/attachment')
      .set('Authorization', auth('ADMIN', 1))
      .attach('file', pngBytes, { filename: 'info.png', contentType: 'image/png' });
    expect(upstreamError.status).toBe(502);
  });

  it('PATCH/DELETE pengumuman memeriksa kepemilikan & membersihkan aset Cloudinary lama', async () => {
    prisma.announcement.findUnique.mockResolvedValue({
      id: 12,
      scope: 'SCHOOL',
      classGroupId: null,
      attachmentPublicId: 'sim/announcements/old',
    });

    const teacherDelete = await request(app)
      .delete('/api/v1/announcements/12')
      .set('Authorization', auth('TEACHER', 5));
    expect(teacherDelete.status).toBe(403);

    prisma.announcement.update.mockResolvedValue(
      announcementRow({ attachmentUrl: null, attachmentPublicId: null }),
    );
    const adminClearAttachment = await request(app)
      .patch('/api/v1/announcements/12')
      .set('Authorization', auth('ADMIN', 1))
      .send({ title: 'Judul Baru', attachmentUrl: '', attachmentPublicId: '' });
    expect(adminClearAttachment.status).toBe(200);
    expect(cloudinary.destroyAsset).toHaveBeenCalledWith('sim/announcements/old');

    jest.clearAllMocks();
    prisma.announcement.findUnique.mockResolvedValue({
      id: 12,
      scope: 'SCHOOL',
      classGroupId: null,
      attachmentPublicId: 'sim/announcements/old',
    });
    const adminDelete = await request(app)
      .delete('/api/v1/announcements/12')
      .set('Authorization', auth('ADMIN', 1));
    expect(adminDelete.status).toBe(204);
    expect(cloudinary.destroyAsset).toHaveBeenCalledWith('sim/announcements/old');

    // Admin juga berhak mengubah/menghapus pengumuman tingkat CLASS
    jest.clearAllMocks();
    prisma.announcement.findUnique.mockResolvedValue({
      id: 26,
      scope: 'CLASS',
      classGroupId: 4,
      attachmentPublicId: null,
    });
    const adminDeleteClass = await request(app)
      .delete('/api/v1/announcements/26')
      .set('Authorization', auth('ADMIN', 1));
    expect(adminDeleteClass.status).toBe(204);
  });

  it('route tidak dikenal tetap 404', async () => {
    const res = await request(app).get('/api/v1/tidak-ada');
    expect(res.status).toBe(404);
  });
});
