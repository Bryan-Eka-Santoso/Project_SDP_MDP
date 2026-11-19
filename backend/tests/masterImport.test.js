process.env.JWT_ACCESS_SECRET = 'test-secret-yang-panjangnya-lebih-dari-32-karakter';

jest.mock('../src/lib/prisma', () => ({
  user: {
    findUnique: jest.fn(),
    findMany: jest.fn(),
    create: jest.fn(),
  },
  student: {
    findMany: jest.fn(),
    create: jest.fn(),
  },
  teacher: {
    findMany: jest.fn(),
    create: jest.fn(),
  },
  academicYear: {
    findFirst: jest.fn(),
  },
  classGroup: {
    findMany: jest.fn(),
  },
  classMember: {
    create: jest.fn(),
  },
  $transaction: jest.fn(),
}));

const fs = require('fs');
const path = require('path');
const bcrypt = require('bcrypt');
const ExcelJS = require('exceljs');
const jwt = require('jsonwebtoken');
const request = require('supertest');
const prisma = require('../src/lib/prisma');
const app = require('../src/app');

const STUDENTS_URL = '/api/v1/students/import';
const TEACHERS_URL = '/api/v1/teachers/import';
const XLSX_TYPE = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';
const STUDENT_HEADER = ['Nama', 'NIS', 'Tanggal Lahir', 'Email', 'Password', 'No. Telepon', 'Kelas'];
const TEACHER_HEADER = ['Nama', 'NIP', 'Email', 'Password'];

async function makeXlsx(header, rows, sheetName = 'Data') {
  const workbook = new ExcelJS.Workbook();
  const sheet = workbook.addWorksheet(sheetName);
  sheet.addRow(header);
  for (const row of rows) {
    sheet.addRow(row);
  }
  return Buffer.from(await workbook.xlsx.writeBuffer());
}

function loginAs(role = 'ADMIN') {
  prisma.user.findUnique.mockResolvedValue({ id: 1, role, status: 'ACTIVE' });
  return `Bearer ${jwt.sign({ role }, process.env.JWT_ACCESS_SECRET, { subject: '1', expiresIn: 900 })}`;
}

function postImport(url, buffer, { filename = 'data.xlsx', field = 'file', role = 'ADMIN' } = {}) {
  return request(app)
    .post(url)
    .set('Authorization', loginAs(role))
    .attach(field, buffer, { filename, contentType: XLSX_TYPE });
}

beforeEach(() => {
  jest.clearAllMocks();
  prisma.user.findMany.mockResolvedValue([]);
  prisma.student.findMany.mockResolvedValue([]);
  prisma.teacher.findMany.mockResolvedValue([]);
  prisma.academicYear.findFirst.mockResolvedValue({ id: 5, name: '2026/2027' });
  prisma.classGroup.findMany.mockResolvedValue([
    { id: 10, name: 'X-1', capacity: 36, _count: { members: 34 } },
    { id: 11, name: 'XI-1', capacity: 30, _count: { members: 30 } },
  ]);

  let nextUserId = 100;
  let nextStudentId = 200;
  prisma.user.create.mockImplementation(async ({ data }) => ({ id: ++nextUserId, ...data }));
  prisma.student.create.mockImplementation(async ({ data }) => ({ id: ++nextStudentId, ...data }));
  prisma.teacher.create.mockImplementation(async ({ data }) => ({ id: 300, ...data }));
  prisma.classMember.create.mockResolvedValue({});
  prisma.$transaction.mockImplementation((fn) =>
    typeof fn === 'function' ? fn(prisma) : Promise.all(fn),
  );
});

describe('POST /api/v1/students/import', () => {
  it('201 mengimpor siswa dengan password default & kustom, format tanggal beragam, serta penempatan kelas opsional', async () => {
    const buffer = await makeXlsx(STUDENT_HEADER, [
      ['Angela Putri', '2026001', '2009-05-14', 'Angela@santaagnes.test', '', '081234567890', 'x-1'],
      ['Budi Santoso', '2026002', '15/08/2009', 'budi@santaagnes.test', 'Rahasia99', null, null],
      ['Clara Wijaya', '2026003', new Date(Date.UTC(2009, 10, 2)), 'clara@santaagnes.test', null, '081999888777', 'X-1'],
    ]);

    const res = await postImport(STUDENTS_URL, buffer);

    expect(res.status).toBe(201);
    expect(res.body.data).toEqual({ imported: 3, assignedToClass: 2 });
    expect(prisma.user.create).toHaveBeenCalledTimes(3);
    expect(prisma.student.create).toHaveBeenCalledTimes(3);
    expect(prisma.classMember.create).toHaveBeenCalledTimes(2);

    const firstUserData = prisma.user.create.mock.calls[0][0].data;
    expect(firstUserData.email).toBe('angela@santaagnes.test');
    expect(firstUserData.role).toBe('STUDENT');
    expect(await bcrypt.compare('12345678', firstUserData.passwordHash)).toBe(true);

    const secondUserData = prisma.user.create.mock.calls[1][0].data;
    expect(await bcrypt.compare('Rahasia99', secondUserData.passwordHash)).toBe(true);

    expect(prisma.student.create.mock.calls[1][0].data.birthDate).toEqual(new Date('2009-08-15T00:00:00.000Z'));
    expect(prisma.student.create.mock.calls[2][0].data.birthDate).toEqual(new Date('2009-11-02T00:00:00.000Z'));
    expect(prisma.classMember.create.mock.calls[0][0].data).toEqual({ studentId: 201, classGroupId: 10 });
  });

  it('201 berhasil tanpa tahun ajaran aktif jika kolom Kelas kosong atau tidak disertakan', async () => {
    prisma.academicYear.findFirst.mockResolvedValue(null);
    const buffer = await makeXlsx(
      ['Nama', 'NIS', 'Tanggal Lahir', 'Email'],
      [['Doni Kusuma', '2026010', 39947, 'doni@santaagnes.test']],
    );

    const res = await postImport(STUDENTS_URL, buffer);

    expect(res.status).toBe(201);
    expect(res.body.data).toEqual({ imported: 1, assignedToClass: 0 });
    expect(prisma.academicYear.findFirst).not.toHaveBeenCalled();
  });

  it('template resmi docs/import-templates/siswa.xlsx dapat dibaca & diimpor bersih', async () => {
    const templatePath = path.resolve(__dirname, '../../docs/import-templates/siswa.xlsx');
    const buffer = fs.readFileSync(templatePath);

    const res = await postImport(STUDENTS_URL, buffer, { filename: 'siswa.xlsx' });

    expect(res.status).toBe(201);
    expect(res.body.data.imported).toBeGreaterThan(0);
  });

  it('422 IMPORT_INVALID menolak duplikat NIS/Email, tanggal salah, password pendek, dan kapasitas kelas penuh tanpa menulis ke DB', async () => {
    prisma.student.findMany.mockResolvedValue([{ nis: '2026999' }]);
    prisma.user.findMany.mockResolvedValue([{ email: 'ada@santaagnes.test' }]);

    const buffer = await makeXlsx(STUDENT_HEADER, [
      ['Duplikat DB', '2026999', '2008-02-30', 'Ada@santaagnes.test', '1234', null, 'XI-1'],
      ['Duplikat File 1', '2026111', '2009-01-10', 'sama@santaagnes.test', '', null, 'TidakAda'],
      ['Duplikat File 2', '2026111', '1930-01-01', 'Sama@santaagnes.test', '', null, 'X-1'],
    ]);

    const res = await postImport(STUDENTS_URL, buffer);

    expect(res.status).toBe(422);
    expect(res.body.error.code).toBe('IMPORT_INVALID');
    const messages = res.body.error.details.map((d) => `${d.row}:${d.column}:${d.message}`);
    expect(messages).toEqual(
      expect.arrayContaining([
        expect.stringContaining('2:NIS:NIS "2026999" sudah terdaftar.'),
        expect.stringContaining('2:Email:Email "ada@santaagnes.test" sudah terdaftar.'),
        expect.stringContaining('2:Tanggal Lahir:Format tanggal lahir'),
        expect.stringContaining('2:Password:Password minimal 8 karakter.'),
        expect.stringContaining('2:Kelas:Kapasitas kelas "XI-1" tidak mencukupi'),
        expect.stringContaining('3:Kelas:Kelas "TidakAda" tidak ditemukan'),
        expect.stringContaining('4:NIS:NIS "2026111" duplikat di file'),
        expect.stringContaining('4:Email:Email "sama@santaagnes.test" duplikat di file'),
        expect.stringContaining('4:Tanggal Lahir:Tahun lahir harus antara 1950 dan 2100.'),
      ]),
    );
    expect(prisma.$transaction).not.toHaveBeenCalled();
  });

  it('422 menolak kelebihan kapasitas akibat gabungan beberapa baris dalam satu file', async () => {
    // X-1 punya kapasitas 36, anggota saat ini 34 -> tersisa 2 kursi.
    const buffer = await makeXlsx(STUDENT_HEADER, [
      ['S1', '3001', '2009-01-01', 's1@santaagnes.test', '', '', 'X-1'],
      ['S2', '3002', '2009-01-02', 's2@santaagnes.test', '', '', 'X-1'],
      ['S3', '3003', '2009-01-03', 's3@santaagnes.test', '', '', 'X-1'],
    ]);

    const res = await postImport(STUDENTS_URL, buffer);

    expect(res.status).toBe(422);
    expect(res.body.error.details).toEqual([
      {
        row: 4,
        column: 'Kelas',
        message: 'Kapasitas kelas "X-1" tidak mencukupi (maksimal 36 siswa).',
      },
    ]);
    expect(prisma.$transaction).not.toHaveBeenCalled();
  });

  it('401 tanpa token, 403 non-admin, 400 tanpa file, 415 bukan xlsx', async () => {
    const valid = await makeXlsx(STUDENT_HEADER, [['A', '1', '2009-01-01', 'a@t.id', '', '', '']]);

    expect((await request(app).post(STUDENTS_URL).attach('file', valid, 'a.xlsx')).status).toBe(401);
    expect((await postImport(STUDENTS_URL, valid, { role: 'TEACHER' })).status).toBe(403);
    expect((await request(app).post(STUDENTS_URL).set('Authorization', loginAs('ADMIN'))).status).toBe(400);
    expect(
      (await postImport(STUDENTS_URL, Buffer.from('bukan zip'), { filename: 'a.xlsx' })).status,
    ).toBe(415);
  });
});

describe('POST /api/v1/teachers/import', () => {
  it('201 mengimpor guru dengan NIP teks dan password default jika kosong', async () => {
    const buffer = await makeXlsx(TEACHER_HEADER, [
      ['Antonius Wijaya', '199204102020011001', 'Antonius@santaagnes.test', ''],
      ['Beatrix Lestari', '199508212022032004', 'beatrix@santaagnes.test', 'GuruHebat1'],
    ]);

    const res = await postImport(TEACHERS_URL, buffer);

    expect(res.status).toBe(201);
    expect(res.body.data).toEqual({ imported: 2 });
    expect(prisma.teacher.create).toHaveBeenCalledTimes(2);
    const firstUser = prisma.user.create.mock.calls[0][0].data;
    expect(firstUser.role).toBe('TEACHER');
    expect(await bcrypt.compare('12345678', firstUser.passwordHash)).toBe(true);
    expect(prisma.teacher.create.mock.calls[0][0].data).toEqual({
      userId: 101,
      employeeNumber: '199204102020011001',
      isHomeroomTeacher: false,
    });
  });

  it('template resmi docs/import-templates/guru.xlsx dapat dibaca & diimpor bersih', async () => {
    const templatePath = path.resolve(__dirname, '../../docs/import-templates/guru.xlsx');
    const buffer = fs.readFileSync(templatePath);

    const res = await postImport(TEACHERS_URL, buffer, { filename: 'guru.xlsx' });

    expect(res.status).toBe(201);
    expect(res.body.data.imported).toBeGreaterThan(0);
  });

  it('422 menolak NIP numerik 18 digit yang terbulatkan Excel serta duplikat NIP/Email', async () => {
    prisma.teacher.findMany.mockResolvedValue([{ employeeNumber: '198001012005011002' }]);
    prisma.user.findMany.mockResolvedValue([{ email: 'guru.ada@santaagnes.test' }]);

    const buffer = await makeXlsx(TEACHER_HEADER, [
      ['Numerik Panjang', 198001012005011001, 'num@santaagnes.test', ''],
      ['Duplikat DB', '198001012005011002', 'guru.ada@santaagnes.test', 'pendek'],
      ['Duplikat 1', '199900001111222233', 'g1@santaagnes.test', ''],
      ['Duplikat 2', '199900001111222233', 'g1@santaagnes.test', ''],
    ]);

    const res = await postImport(TEACHERS_URL, buffer);

    expect(res.status).toBe(422);
    expect(res.body.error.code).toBe('IMPORT_INVALID');
    const messages = res.body.error.details.map((d) => `${d.row}:${d.column}:${d.message}`);
    expect(messages).toEqual(
      expect.arrayContaining([
        expect.stringContaining('2:NIP:'),
        expect.stringContaining('Ubah format kolom menjadi Teks'),
        expect.stringContaining('3:NIP:NIP "198001012005011002" sudah terdaftar.'),
        expect.stringContaining('3:Email:Email "guru.ada@santaagnes.test" sudah terdaftar.'),
        expect.stringContaining('3:Password:Password minimal 8 karakter.'),
        expect.stringContaining('5:NIP:NIP "199900001111222233" duplikat di file'),
        expect.stringContaining('5:Email:Email "g1@santaagnes.test" duplikat di file'),
      ]),
    );
    expect(prisma.$transaction).not.toHaveBeenCalled();
  });

  it('403 untuk siswa dan 415 untuk file non-xlsx', async () => {
    const valid = await makeXlsx(TEACHER_HEADER, [['Guru A', '123456', 'g@t.id', '']]);

    expect((await postImport(TEACHERS_URL, valid, { role: 'STUDENT' })).status).toBe(403);
    expect((await postImport(TEACHERS_URL, Buffer.from('not-xlsx'))).status).toBe(415);
  });
});
