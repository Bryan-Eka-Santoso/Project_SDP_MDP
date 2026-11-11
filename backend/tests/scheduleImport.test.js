process.env.JWT_ACCESS_SECRET = 'test-secret-yang-panjangnya-lebih-dari-32-karakter';

jest.mock('../src/lib/prisma', () => ({
  user: { findUnique: jest.fn() },
  semester: { findFirst: jest.fn() },
  classGroup: { findMany: jest.fn() },
  subject: { findMany: jest.fn() },
  teacher: { findMany: jest.fn() },
  facility: { findMany: jest.fn() },
  teachingAssignment: { findMany: jest.fn() },
  classMember: { findMany: jest.fn() },
  scheduleSlot: { findMany: jest.fn(), createMany: jest.fn() },
  $transaction: jest.fn(),
}));

const fs = require('fs');
const path = require('path');
const ExcelJS = require('exceljs');
const jwt = require('jsonwebtoken');
const request = require('supertest');
const prisma = require('../src/lib/prisma');
const app = require('../src/app');
const { buildClassPeers, findConflictsInBatch } = require('../src/modules/schedules/schedules.conflicts');

const URL = '/api/v1/schedule-slots/import';
const XLSX_TYPE = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';
const HEADER = ['Kelas', 'Kode Mapel', 'NIP Guru', 'Hari', 'Jam Mulai', 'Jam Selesai', 'Ruangan'];
const NIP_YOHANES = '198001012005011001';
const NIP_MARIA = '198203152006042002';

const t = (hhmm) => new Date(`1970-01-01T${hhmm}:00.000Z`);
const clock = (h, m) => new Date(Date.UTC(1899, 11, 30, h, m)); // sel jam Excel

async function xlsx(rows, header = HEADER) {
  const workbook = new ExcelJS.Workbook();
  const sheet = workbook.addWorksheet('Jadwal');
  sheet.addRow(header);
  for (const values of rows) {
    const row = sheet.addRow(values);
    row.eachCell((cell) => {
      if (cell.value instanceof Date) cell.numFmt = 'hh:mm';
    });
  }
  return Buffer.from(await workbook.xlsx.writeBuffer());
}

function loginAs(role) {
  prisma.user.findUnique.mockResolvedValue({ id: 1, role, status: 'ACTIVE' });
  return `Bearer ${jwt.sign({ role }, process.env.JWT_ACCESS_SECRET, { subject: '1', expiresIn: 900 })}`;
}

function upload(buffer, { filename = 'jadwal.xlsx', field = 'file', semesterId, role = 'ADMIN' } = {}) {
  const req = request(app)
    .post(URL)
    .set('Authorization', loginAs(role))
    .attach(field, buffer, { filename, contentType: XLSX_TYPE });
  return semesterId === undefined ? req : req.field('semesterId', semesterId);
}

// Slot yang sudah tersimpan, bentuknya sama dengan hasil select di schedules.conflicts.js.
function savedSlot({ id = 50, assignmentId = 8, day = 'MONDAY', start = '07:00', end = '08:30', roomId = 100, teacherId = 2, classGroupId = 10 } = {}) {
  return {
    id,
    teachingAssignmentId: assignmentId,
    dayOfWeek: day,
    startTime: t(start),
    endTime: t(end),
    roomId,
    room: { name: roomId === 100 ? 'Ruang X-1' : 'Ruang XI-1' },
    teachingAssignment: {
      teacherId,
      classGroupId,
      teacher: { user: { name: teacherId === 1 ? 'Yohanes Prasetyo' : 'Maria Lestari' } },
      subject: { name: teacherId === 1 ? 'Matematika' : 'Bahasa Indonesia' },
      classGroup: { name: classGroupId === 10 ? 'X-1' : 'XI-1' },
    },
  };
}

beforeEach(() => {
  jest.resetAllMocks();
  prisma.semester.findFirst.mockResolvedValue({ id: 3, academicYearId: 1, academicYear: { name: '2026/2027' } });
  prisma.classGroup.findMany.mockResolvedValue([{ id: 10, name: 'X-1' }, { id: 11, name: 'XI-1' }]);
  prisma.subject.findMany.mockResolvedValue([
    { id: 2, code: 'MAT', name: 'Matematika' },
    { id: 3, code: 'BIN', name: 'Bahasa Indonesia' },
  ]);
  prisma.teacher.findMany.mockResolvedValue([
    { id: 1, employeeNumber: NIP_YOHANES, user: { name: 'Yohanes Prasetyo' } },
    { id: 2, employeeNumber: NIP_MARIA, user: { name: 'Maria Lestari' } },
  ]);
  prisma.facility.findMany.mockResolvedValue([
    { id: 100, name: 'Ruang X-1', type: 'ROOM' },
    { id: 101, name: 'Ruang XI-1', type: 'ROOM' },
    { id: 200, name: 'Proyektor', type: 'ITEM' },
    // Dua ruangan bernama sama (beda huruf besar/kecil): tidak bisa dipastikan.
    { id: 103, name: 'Aula', type: 'ROOM' },
    { id: 104, name: 'aula', type: 'ROOM' },
  ]);
  prisma.teachingAssignment.findMany.mockResolvedValue([
    { id: 7, teacherId: 1, subjectId: 2, classGroupId: 10 }, // Yohanes, MAT, X-1
    { id: 8, teacherId: 2, subjectId: 3, classGroupId: 10 }, // Maria, BIN, X-1
    { id: 9, teacherId: 1, subjectId: 2, classGroupId: 11 }, // Yohanes, MAT, XI-1
  ]);
  prisma.scheduleSlot.findMany.mockResolvedValue([]);
  prisma.classMember.findMany.mockResolvedValue([]);
  prisma.scheduleSlot.createMany.mockResolvedValue({ count: 0 });
  prisma.$transaction.mockImplementation((operations) => Promise.all(operations));
});

describe('POST /api/v1/schedule-slots/import: berhasil', () => {
  it('201: semua baris disimpan dalam satu transaksi; huruf, spasi NIP, dan format jam bervariasi dikenali', async () => {
    const buffer = await xlsx([
      ['x-1', 'mat', NIP_YOHANES, 'senin', '07:00', '08:30', 'ruang x-1'],
      // sel jam Excel (Date), pecahan hari (10:15), NIP dengan spasi, "Jum'at"
      ['XI-1', 'MAT', '19800101 200501 1 001', "Jum'at", clock(8, 45), 615 / 1440, 'Ruang XI-1'],
      ['X-1', 'BIN', NIP_MARIA, 'Sabtu', '8.45', '10.15', 'Ruang X-1'],
    ]);

    const res = await upload(buffer);

    expect(res.status).toBe(201);
    expect(res.body.data).toEqual({ semesterId: 3, imported: 3 });
    expect(prisma.$transaction).toHaveBeenCalledTimes(1);
    expect(prisma.scheduleSlot.createMany).toHaveBeenCalledTimes(1);
    expect(prisma.scheduleSlot.createMany.mock.calls[0][0].data).toEqual([
      { teachingAssignmentId: 7, roomId: 100, dayOfWeek: 'MONDAY', startTime: t('07:00'), endTime: t('08:30') },
      { teachingAssignmentId: 9, roomId: 101, dayOfWeek: 'FRIDAY', startTime: t('08:45'), endTime: t('10:15') },
      { teachingAssignmentId: 8, roomId: 100, dayOfWeek: 'SATURDAY', startTime: t('08:45'), endTime: t('10:15') },
    ]);
  });

  it('urutan kolom di file bebas dan header tidak membedakan huruf besar/kecil', async () => {
    const buffer = await xlsx(
      [['Ruang X-1', 'X-1', NIP_YOHANES, 'Selasa', 'MAT', '09:00', '07:30']],
      ['ruangan', '  kelas ', 'NIP Guru', 'HARI', 'Kode  Mapel', 'jam selesai', 'jam mulai'],
    );

    const res = await upload(buffer);

    expect(res.status).toBe(201);
    expect(prisma.scheduleSlot.createMany.mock.calls[0][0].data).toEqual([
      { teachingAssignmentId: 7, roomId: 100, dayOfWeek: 'TUESDAY', startTime: t('07:30'), endTime: t('09:00') },
    ]);
  });

  it('slot yang bersambung dengan slot tersimpan (selesai 08:30, mulai 08:30) tidak bentrok', async () => {
    prisma.scheduleSlot.findMany.mockResolvedValue([savedSlot({ teacherId: 1, assignmentId: 7, roomId: 100, classGroupId: 10 })]);

    const res = await upload(await xlsx([['XI-1', 'MAT', NIP_YOHANES, 'Senin', '08:30', '10:00', 'Ruang XI-1']]));

    expect(res.status).toBe(201);
  });

  it('semesterId dipakai untuk semester dan tahun ajaran yang dibandingkan', async () => {
    prisma.semester.findFirst.mockResolvedValue({ id: 5, academicYearId: 2, academicYear: { name: '2025/2026' } });

    const res = await upload(await xlsx([['X-1', 'MAT', NIP_YOHANES, 'Senin', '07:00', '08:30', 'Ruang X-1']]), { semesterId: '5' });

    expect(res.status).toBe(201);
    expect(res.body.data.semesterId).toBe(5);
    expect(prisma.semester.findFirst.mock.calls[0][0].where).toEqual({ id: 5 });
    expect(prisma.classGroup.findMany.mock.calls[0][0].where).toEqual({ academicYearId: 2 });
    expect(prisma.teachingAssignment.findMany.mock.calls[0][0].where).toEqual({ semesterId: 5 });
    expect(prisma.scheduleSlot.findMany.mock.calls[0][0].where).toEqual({ teachingAssignment: { semesterId: 5 } });
  });

  it('semesterId kosong di form berarti semester aktif', async () => {
    const res = await upload(await xlsx([['X-1', 'MAT', NIP_YOHANES, 'Senin', '07:00', '08:30', 'Ruang X-1']]), { semesterId: '' });

    expect(res.status).toBe(201);
    expect(prisma.semester.findFirst.mock.calls[0][0].where).toEqual({ isActive: true });
  });

  it('file contoh docs/import-templates/jadwal.xlsx lolos validasi', async () => {
    const template = fs.readFileSync(path.join(__dirname, '../../docs/import-templates/jadwal.xlsx'));

    const res = await upload(template);

    expect(res.status).toBe(201);
    expect(res.body.data.imported).toBe(3);
    expect(prisma.scheduleSlot.createMany.mock.calls[0][0].data.map((d) => d.dayOfWeek)).toEqual(['SATURDAY', 'SATURDAY', 'SATURDAY']);
  });
});

describe('POST /api/v1/schedule-slots/import: validasi baris (422 IMPORT_INVALID)', () => {
  it('mengumpulkan semua kesalahan dengan nomor baris Excel dan nama kolom; tidak ada yang disimpan', async () => {
    const buffer = await xlsx([
      ['X-9', 'MAT', NIP_YOHANES, 'Senin', '07:00', '08:30', 'Ruang X-1'], // baris 2
      ['X-1', 'ZZZ', '1234', 'Minggu', '25:00', '08:30', 'Proyektor'], // baris 3
      ['X-1', 'MAT', NIP_YOHANES, 'Senin', '09:00', '09:00', 'Aula'], // baris 4
      [null, null, null, null, null, null, null], // baris 5 kosong: dilewati
      ['X-1', null, NIP_YOHANES, 'Senin', '07:00', '08:30', 'Ruang X-1'], // baris 6
    ]);

    const res = await upload(buffer);

    expect(res.status).toBe(422);
    expect(res.body.error.code).toBe('IMPORT_INVALID');
    expect(res.body.error.message).toBe('Impor dibatalkan: 9 kesalahan di 4 baris. Tidak ada data yang disimpan.');
    expect(res.body.error.details).toEqual([
      { row: 2, column: 'Kelas', message: 'Kelas “X-9” tidak ditemukan di tahun ajaran 2026/2027.' },
      { row: 3, column: 'Kode Mapel', message: 'Kode mapel “ZZZ” tidak ditemukan.' },
      { row: 3, column: 'NIP Guru', message: 'NIP “1234” tidak ditemukan di data guru.' },
      { row: 3, column: 'Ruangan', message: '“Proyektor” adalah barang, bukan ruangan.' },
      { row: 3, column: 'Hari', message: 'Hari “Minggu” tidak dikenal. Gunakan Senin, Selasa, Rabu, Kamis, Jumat, atau Sabtu.' },
      { row: 3, column: 'Jam Mulai', message: 'Format jam harus HH:mm, mis. 07:30.' },
      { row: 4, column: 'Ruangan', message: '“Aula” terdaftar lebih dari satu kali di data master, sehingga tidak bisa dipastikan.' },
      { row: 4, column: 'Jam Selesai', message: 'Jam selesai harus setelah jam mulai.' },
      { row: 6, column: 'Kode Mapel', message: 'Wajib diisi.' },
    ]);
    expect(prisma.$transaction).not.toHaveBeenCalled();
    expect(prisma.scheduleSlot.createMany).not.toHaveBeenCalled();
  });

  it('NIP yang diketik sebagai angka 18 digit ditolak dengan petunjuk mengubah format ke Teks', async () => {
    const res = await upload(await xlsx([['X-1', 'MAT', 198001012005011001, 'Senin', '07:00', '08:30', 'Ruang X-1']]));

    expect(res.status).toBe(422);
    expect(res.body.error.details).toEqual([
      {
        row: 2,
        column: 'NIP Guru',
        message: 'Angka terlalu panjang dan sudah dibulatkan Excel. Ubah format kolom menjadi Teks lalu ketik ulang.',
      },
    ]);
  });

  it('baris ditolak jika guru belum ditugaskan pada mapel dan kelas itu (impor tidak membuat penugasan)', async () => {
    const res = await upload(await xlsx([['XI-1', 'BIN', NIP_MARIA, 'Senin', '07:00', '08:30', 'Ruang XI-1']]));

    expect(res.status).toBe(422);
    expect(res.body.error.details).toEqual([
      {
        row: 2,
        column: null,
        message:
          'Guru Maria Lestari belum ditugaskan mengajar Bahasa Indonesia di kelas XI-1 pada semester ini. ' +
          'Tambahkan lewat "Atur guru pengajar" di data master, lalu impor ulang.',
      },
    ]);
    expect(prisma.scheduleSlot.createMany).not.toHaveBeenCalled();
  });

  it('422 jika header tidak lengkap', async () => {
    const res = await upload(await xlsx([['X-1', 'MAT', NIP_YOHANES, 'Senin', '07:00']], ['Kelas', 'Kode Mapel', 'NIP Guru', 'Hari', 'Jam Mulai']));

    expect(res.status).toBe(422);
    expect(res.body.error.details).toEqual([
      { row: 1, column: 'Jam Selesai', message: 'Kolom "Jam Selesai" tidak ditemukan di baris pertama (header).' },
      { row: 1, column: 'Ruangan', message: 'Kolom "Ruangan" tidak ditemukan di baris pertama (header).' },
    ]);
  });

  it('422 jika file hanya berisi header', async () => {
    const res = await upload(await xlsx([]));

    expect(res.status).toBe(422);
    expect(res.body.error.details).toEqual([{ row: 2, column: null, message: 'File tidak berisi baris data.' }]);
  });
});

describe('POST /api/v1/schedule-slots/import: bentrok (F-JDW-03)', () => {
  it('bentrok dengan jadwal yang sudah tersimpan: ditandai pada kolom penyebabnya', async () => {
    // Ruang X-1 sudah dipakai Maria untuk BIN kelas X-1, Senin 07.00–08.30.
    prisma.scheduleSlot.findMany.mockResolvedValue([savedSlot()]);

    const res = await upload(await xlsx([['XI-1', 'MAT', NIP_YOHANES, 'Senin', '07:30', '09:00', 'Ruang X-1']]));

    expect(res.status).toBe(422);
    expect(res.body.error.details).toEqual([
      {
        row: 2,
        column: 'Ruangan',
        message: 'Ruangan Ruang X-1 sudah dipakai untuk Bahasa Indonesia di X-1 pada Senin 07:00–08:30.',
      },
    ]);
  });

  it('bentrok antarbaris di file: dilaporkan pada baris bawah dengan nomor baris yang bentrok', async () => {
    const res = await upload(
      await xlsx([
        ['X-1', 'MAT', NIP_YOHANES, 'Senin', '07:00', '08:30', 'Ruang X-1'], // baris 2
        ['XI-1', 'MAT', NIP_YOHANES, 'Senin', '08:00', '09:30', 'Ruang XI-1'], // baris 3: guru sama
      ]),
    );

    expect(res.status).toBe(422);
    expect(res.body.error.details).toEqual([
      {
        row: 3,
        column: 'NIP Guru',
        message: 'Guru Yohanes Prasetyo sudah mengajar Matematika di X-1 pada Senin 07:00–08:30 (baris 2 di file).',
      },
    ]);
  });

  it('jadwal yang persis sama dengan yang tersimpan dilaporkan sekali sebagai duplikat', async () => {
    prisma.scheduleSlot.findMany.mockResolvedValue([savedSlot({ assignmentId: 7, teacherId: 1 })]);

    const res = await upload(await xlsx([['X-1', 'MAT', NIP_YOHANES, 'Senin', '07:00', '08:30', 'Ruang X-1']]));

    expect(res.status).toBe(422);
    expect(res.body.error.details).toEqual([
      { row: 2, column: null, message: 'Jadwal ini sudah ada di semester tersebut.' },
    ]);
  });

  it('baris kembar di dalam file dilaporkan sebagai duplikat baris pertama', async () => {
    const row = ['X-1', 'MAT', NIP_YOHANES, 'Senin', '07:00', '08:30', 'Ruang X-1'];

    const res = await upload(await xlsx([row, row, row]));

    expect(res.status).toBe(422);
    expect(res.body.error.details).toEqual([
      { row: 3, column: null, message: 'Baris ini sama persis dengan baris 2.' },
      { row: 4, column: null, message: 'Baris ini sama persis dengan baris 2.' },
    ]);
  });

  it('siswa yang sama di dua kelas (mis. kelas paket) bentrok walau guru, kelas, dan ruangan berbeda', async () => {
    prisma.classMember.findMany.mockResolvedValue([
      { classGroupId: 10, studentId: 1 },
      { classGroupId: 11, studentId: 1 },
    ]);
    prisma.scheduleSlot.findMany.mockResolvedValue([
      savedSlot({ assignmentId: 9, teacherId: 1, classGroupId: 11, roomId: 101 }),
    ]);

    const res = await upload(await xlsx([['X-1', 'BIN', NIP_MARIA, 'Senin', '07:30', '08:00', 'Ruang X-1']]));

    expect(res.status).toBe(422);
    expect(res.body.error.details).toEqual([
      { row: 2, column: 'Kelas', message: 'Ada siswa yang juga mengikuti Matematika di XI-1 pada Senin 07:00–08:30.' },
    ]);
    expect(prisma.classMember.findMany.mock.calls[0][0].where).toEqual({ classGroup: { academicYearId: 1 } });
  });
});

describe('POST /api/v1/schedule-slots/import: semester', () => {
  it('409 INVALID_STATE jika tidak ada semester aktif dan semesterId tidak diisi', async () => {
    prisma.semester.findFirst.mockResolvedValue(null);

    const res = await upload(await xlsx([['X-1', 'MAT', NIP_YOHANES, 'Senin', '07:00', '08:30', 'Ruang X-1']]));

    expect(res.status).toBe(409);
    expect(res.body.error.code).toBe('INVALID_STATE');
  });

  it('400 jika semesterId tidak ditemukan', async () => {
    prisma.semester.findFirst.mockResolvedValue(null);

    const res = await upload(await xlsx([['X-1', 'MAT', NIP_YOHANES, 'Senin', '07:00', '08:30', 'Ruang X-1']]), { semesterId: '99' });

    expect(res.status).toBe(400);
    expect(res.body.error.details).toEqual([{ field: 'body.semesterId', message: 'Semester tidak ditemukan.' }]);
  });

  it('400 jika semesterId bukan angka', async () => {
    const res = await upload(await xlsx([['X-1', 'MAT', NIP_YOHANES, 'Senin', '07:00', '08:30', 'Ruang X-1']]), { semesterId: 'abc' });
    expect(res.status).toBe(400);
    expect(res.body.error.details[0].field).toBe('body.semesterId');
  });
});

describe('POST /api/v1/schedule-slots/import: file dan akses', () => {
  const valid = () => xlsx([['X-1', 'MAT', NIP_YOHANES, 'Senin', '07:00', '08:30', 'Ruang X-1']]);

  it('400 jika tidak ada file', async () => {
    const res = await request(app).post(URL).set('Authorization', loginAs('ADMIN')).field('semesterId', '3');

    expect(res.status).toBe(400);
    expect(res.body.error.details).toEqual([{ field: 'file', message: 'Kirim file pada field "file".' }]);
  });

  it('400 jika request bukan multipart', async () => {
    const res = await request(app).post(URL).set('Authorization', loginAs('ADMIN')).send({ semesterId: 3 });
    expect(res.status).toBe(400);
    expect(res.body.error.code).toBe('VALIDATION_ERROR');
  });

  it('400 jika file dikirim pada field yang salah', async () => {
    const res = await upload(await valid(), { field: 'berkas' });

    expect(res.status).toBe(400);
    expect(res.body.error.details).toEqual([{ field: 'file', message: 'File harus dikirim pada field "file".' }]);
  });

  it('415 jika ekstensi bukan .xlsx', async () => {
    const res = await upload(await valid(), { filename: 'jadwal.xls' });
    expect(res.status).toBe(415);
    expect(res.body.error.code).toBe('UNSUPPORTED_FILE_TYPE');
  });

  it('415 jika isi file bukan arsip Excel walau ekstensinya .xlsx', async () => {
    const res = await upload(Buffer.from('ini hanya teks biasa'));
    expect(res.status).toBe(415);
    expect(res.body.error.message).toBe('Tipe file tidak didukung. Gunakan file Excel (.xlsx).');
  });

  it('415 jika arsip ZIP rusak / bukan workbook Excel', async () => {
    const res = await upload(Buffer.concat([Buffer.from([0x50, 0x4b, 0x03, 0x04]), Buffer.from('bukan-xlsx')]));
    expect(res.status).toBe(415);
    expect(res.body.error.code).toBe('UNSUPPORTED_FILE_TYPE');
  });

  it('413 jika file lebih dari 10 MB', async () => {
    const res = await upload(Buffer.alloc(10 * 1024 * 1024 + 1, 0x50));

    expect(res.status).toBe(413);
    expect(res.body.error).toEqual({ code: 'FILE_TOO_LARGE', message: 'Ukuran file melebihi batas 10 MB.' });
  });

  it('401 tanpa login', async () => {
    const res = await request(app).post(URL).attach('file', await valid(), 'jadwal.xlsx');
    expect(res.status).toBe(401);
  });

  it('403 untuk guru dan file tidak dibaca', async () => {
    const res = await upload(await valid(), { role: 'TEACHER' });
    expect(res.status).toBe(403);
    expect(prisma.semester.findFirst).not.toHaveBeenCalled();
  });
});

describe('findConflictsInBatch / buildClassPeers', () => {
  it('buildClassPeers: hanya siswa yang ada di lebih dari satu kelas yang menghubungkan kelas', () => {
    const peers = buildClassPeers([
      { studentId: 1, classGroupId: 10 },
      { studentId: 1, classGroupId: 30 },
      { studentId: 2, classGroupId: 10 },
      { studentId: 3, classGroupId: 11 },
    ]);

    expect([...peers.get(10)]).toEqual([30]);
    expect([...peers.get(30)]).toEqual([10]);
    expect(peers.has(11)).toBe(false);
  });

  it('kandidat yang bentrok tetap diingat agar baris berikutnya ikut diperiksa terhadapnya', () => {
    const base = { dayOfWeek: 'MONDAY', roomId: 1, classGroupId: 10, teacherName: 'G', className: 'K', subjectName: 'M', roomName: 'R' };
    const a = { ...base, row: 2, teachingAssignmentId: 1, teacherId: 1, startTime: t('07:00'), endTime: t('08:00') };
    const b = { ...base, row: 3, teachingAssignmentId: 2, teacherId: 1, classGroupId: 11, roomId: 2, startTime: t('07:30'), endTime: t('08:30') };
    const c = { ...base, row: 4, teachingAssignmentId: 3, teacherId: 1, classGroupId: 12, roomId: 3, startTime: t('07:45'), endTime: t('08:45') };

    const found = findConflictsInBatch([a, b, c], []);

    expect(found.map((f) => [f.row, f.reason])).toEqual([
      [3, 'TEACHER'],
      [4, 'TEACHER'],
      [4, 'TEACHER'],
    ]);
  });
});
