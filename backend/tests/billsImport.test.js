process.env.JWT_ACCESS_SECRET = 'test-secret-yang-panjangnya-lebih-dari-32-karakter';

jest.mock('../src/lib/prisma', () => ({
  user: { findUnique: jest.fn() },
  student: { findMany: jest.fn() },
  studentGuardian: { findMany: jest.fn() },
  bill: { findMany: jest.fn(), createMany: jest.fn(), update: jest.fn() },
  $transaction: jest.fn(),
}));
jest.mock('../src/modules/notifications/notifications.service', () => ({ notify: jest.fn() }));

const fs = require('fs');
const path = require('path');
const ExcelJS = require('exceljs');
const jwt = require('jsonwebtoken');
const request = require('supertest');
const prisma = require('../src/lib/prisma');
const app = require('../src/app');
const { notify } = require('../src/modules/notifications/notifications.service');
const { parseAmount, parseDateCell } = require('../src/modules/bills/bills.import.service');

const URL = '/api/v1/bills/import';
const XLSX_TYPE = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';
const HEADER = ['NIS', 'Kode Tagihan', 'Jenis Pembayaran', 'Nominal', 'Jatuh Tempo', 'Status', 'Tanggal Bayar'];

const d = (ymd) => new Date(`${ymd}T00:00:00.000Z`);
const flush = () => new Promise((resolve) => setImmediate(resolve));

async function xlsx(rows, header = HEADER) {
  const workbook = new ExcelJS.Workbook();
  const sheet = workbook.addWorksheet('Rekap');
  sheet.addRow(header);
  for (const values of rows) {
    const row = sheet.addRow(values);
    row.eachCell((cell) => {
      if (cell.value instanceof Date) cell.numFmt = 'yyyy-mm-dd';
    });
  }
  return Buffer.from(await workbook.xlsx.writeBuffer());
}

function loginAs(role) {
  prisma.user.findUnique.mockResolvedValue({ id: 1, role, status: 'ACTIVE' });
  return `Bearer ${jwt.sign({ role }, process.env.JWT_ACCESS_SECRET, { subject: '1', expiresIn: 900 })}`;
}

const upload = (buffer, { filename = 'rekap.xlsx', role = 'ADMIN' } = {}) =>
  request(app).post(URL).set('Authorization', loginAs(role)).attach('file', buffer, { filename, contentType: XLSX_TYPE });

const existing = (o) => ({
  id: 100,
  studentId: 1,
  billCode: 'SPP-2026-08',
  paymentType: 'SPP',
  amount: '850000',
  dueDate: d('2026-08-10'),
  status: 'UNPAID',
  paidDate: null,
  ...o,
});

beforeEach(() => {
  jest.resetAllMocks();
  prisma.student.findMany.mockResolvedValue([
    { id: 1, nis: '2026001', userId: 11, user: { name: 'Angela Putri' } },
    { id: 2, nis: '2026002', userId: 12, user: { name: 'Benediktus Santoso' } },
  ]);
  prisma.bill.findMany.mockResolvedValue([]);
  prisma.studentGuardian.findMany.mockResolvedValue([
    { studentId: 1, guardian: { userId: 301 } },
    { studentId: 1, guardian: { userId: 302 } },
    { studentId: 2, guardian: { userId: 303 } },
  ]);
  prisma.bill.createMany.mockResolvedValue({ count: 0 });
  prisma.bill.update.mockResolvedValue({});
  prisma.$transaction.mockImplementation((operations) => Promise.all(operations));
});

describe('POST /api/v1/bills/import: berhasil (F-BYR-01)', () => {
  it('201: tagihan baru dibuat, yang sudah ada (NIS + kode) diperbarui, yang sama dibiarkan', async () => {
    prisma.bill.findMany.mockResolvedValue([
      existing({}), // siswa 1, SPP-2026-08, belum dibayar → akan jadi lunas
      existing({ id: 101, studentId: 2, billCode: 'SPP-2026-07', dueDate: d('2026-07-10'), status: 'PAID', paidDate: d('2026-07-08') }), // identik
    ]);
    const buffer = await xlsx([
      [2026001, 'SPP-2026-09', 'SPP', 850000, d('2020-09-10'), 'Belum Dibayar', null], // baru (NIS angka, tanggal sel)
      ['2026001', 'spp-2026-08', 'SPP', '850.000', '2026-08-10', 'LUNAS', '09/08/2026'], // berubah (kode beda huruf besar/kecil)
      ['2026002', 'SPP-2026-07', 'SPP', 850000, '10-07-2026', 'Lunas', '2026-07-08'], // tidak berubah
    ]);

    const res = await upload(buffer);

    expect(res.status).toBe(201);
    expect(res.body.data).toEqual({ created: 1, updated: 1, unchanged: 1, notifiedStudents: 1 });
    expect(prisma.$transaction).toHaveBeenCalledTimes(1);
    expect(prisma.bill.createMany.mock.calls[0][0].data).toEqual([
      {
        studentId: 1, billCode: 'SPP-2026-09', paymentType: 'SPP', amount: '850000',
        dueDate: d('2020-09-10'), status: 'UNPAID', paidDate: null,
      },
    ]);
    expect(prisma.bill.update).toHaveBeenCalledTimes(1);
    expect(prisma.bill.update).toHaveBeenCalledWith({
      where: { id: 100 },
      data: { paymentType: 'SPP', amount: '850000', dueDate: d('2026-08-10'), status: 'PAID', paidDate: d('2026-08-09') },
    });
  });

  it('F-BYR-03: siswa dan semua wali muridnya diberi tahu tentang tagihan yang menunggak', async () => {
    await upload(await xlsx([['2026001', 'SPP-2020-09', 'SPP', 850000, '2020-09-10', 'Belum Dibayar', null]]));
    await flush();

    expect(notify).toHaveBeenCalledTimes(1);
    const [userIds, payload] = notify.mock.calls[0];
    expect(userIds.sort()).toEqual([11, 301, 302]);
    expect(payload).toEqual({
      type: 'BILL_OVERDUE',
      title: 'Tunggakan pembayaran',
      body: 'Angela Putri: tagihan SPP (SPP-2020-09) sebesar Rp 850.000 jatuh tempo 10 September 2020 dan belum dibayar.',
      referenceId: 1,
    });
  });

  it('beberapa tagihan menunggak satu siswa digabung dalam satu notifikasi', async () => {
    await upload(
      await xlsx([
        ['2026002', 'SPP-2020-01', 'SPP', 800000, '2020-01-10', 'Belum Dibayar', null],
        ['2026002', 'SPP-2020-02', 'SPP', 700000, '2020-02-10', 'Menunggak', null],
      ]),
    );
    await flush();

    expect(notify).toHaveBeenCalledTimes(1);
    expect(notify.mock.calls[0][0].sort()).toEqual([12, 303]);
    expect(notify.mock.calls[0][1].body).toBe(
      'Benediktus Santoso: 2 tagihan belum dibayar dan sudah lewat jatuh tempo, total Rp 1.500.000.',
    );
  });

  it('tidak ada notifikasi untuk tagihan lunas atau yang belum jatuh tempo', async () => {
    const res = await upload(
      await xlsx([
        ['2026001', 'SPP-2099-01', 'SPP', 850000, '2099-01-10', 'Belum Dibayar', null], // belum jatuh tempo
        ['2026002', 'SPP-2020-01', 'SPP', 850000, '2020-01-10', 'Lunas', '2020-01-08'], // lunas
      ]),
    );
    await flush();

    expect(res.body.data).toMatchObject({ created: 2, notifiedStudents: 0 });
    expect(notify).not.toHaveBeenCalled();
  });

  it('file yang tidak mengubah apa pun tidak menulis dan tidak mengirim ulang notifikasi', async () => {
    prisma.bill.findMany.mockResolvedValue([existing({ billCode: 'SPP-2020-09', dueDate: d('2020-09-10') })]);

    const res = await upload(await xlsx([['2026001', 'SPP-2020-09', 'SPP', 850000, '2020-09-10', 'Belum Dibayar', null]]));
    await flush();

    expect(res.status).toBe(201);
    expect(res.body.data).toEqual({ created: 0, updated: 0, unchanged: 1, notifiedStudents: 0 });
    expect(prisma.$transaction).not.toHaveBeenCalled();
    expect(notify).not.toHaveBeenCalled();
  });

  it('kolom Tanggal Bayar boleh tidak ada di file', async () => {
    const buffer = await xlsx([['2026001', 'SPP-2099-01', 'SPP', 850000, '2099-01-10', 'Belum Dibayar']], HEADER.slice(0, 6));

    const res = await upload(buffer);

    expect(res.status).toBe(201);
    expect(prisma.bill.createMany.mock.calls[0][0].data[0].paidDate).toBeNull();
  });

  it('file contoh docs/import-templates/pembayaran.xlsx lolos validasi', async () => {
    const template = fs.readFileSync(path.join(__dirname, '../../docs/import-templates/pembayaran.xlsx'));
    prisma.student.findMany.mockResolvedValue([
      { id: 1, nis: '2026001', userId: 11, user: { name: 'Angela Putri' } },
      { id: 2, nis: '2026002', userId: 12, user: { name: 'Benediktus Santoso' } },
      { id: 3, nis: '2025001', userId: 13, user: { name: 'Dominikus Halim' } },
    ]);

    const res = await upload(template);

    expect(res.status).toBe(201);
    expect(res.body.data.created).toBe(3);
  });
});

describe('POST /api/v1/bills/import: validasi baris (422 IMPORT_INVALID)', () => {
  it('mengumpulkan semua kesalahan dengan nomor baris dan kolom; tidak ada yang disimpan', async () => {
    const buffer = await xlsx([
      ['9999999', 'SPP-1', 'SPP', 850000, '2026-09-10', 'Lunas', null], // 2: NIS tidak ada
      ['2026001', 'SPP-2', 'SPP', 0, '2026-02-30', 'Dibayar', null], // 3: nominal, tanggal, status
      ['2026001', 'SPP-3', 'SPP', 850000, '2026-09-10', 'Belum Dibayar', '2026-09-08'], // 4: tanggal bayar untuk belum bayar
      ['2026001', 'K'.repeat(51), 'SPP', 12.5, '2026-09-10', 'Lunas', null], // 5: kode kepanjangan, nominal desimal
      ['2026002', 'SPP-4', 'SPP', 850000, '2026-09-10', 'Lunas', null], // 6: valid
      ['2026002', 'spp-4', 'SPP', 850000, '2026-09-10', 'Lunas', null], // 7: kembar baris 6 (huruf besar/kecil diabaikan)
      ['2026002', null, null, null, null, null, null], // 8: hampir kosong
    ]);

    const res = await upload(buffer);

    expect(res.status).toBe(422);
    expect(res.body.error.code).toBe('IMPORT_INVALID');
    expect(res.body.error.details).toEqual([
      { row: 2, column: 'NIS', message: 'NIS “9999999” tidak ditemukan di data siswa.' },
      { row: 3, column: 'Nominal', message: 'Nominal harus angka rupiah bulat lebih dari 0, mis. 850000.' },
      { row: 3, column: 'Jatuh Tempo', message: 'Format tanggal harus YYYY-MM-DD atau DD/MM/YYYY, mis. 2026-09-10.' },
      { row: 3, column: 'Status', message: 'Status “Dibayar” tidak dikenal. Gunakan Lunas atau Belum Dibayar.' },
      { row: 4, column: 'Tanggal Bayar', message: 'Tanggal bayar hanya boleh diisi untuk tagihan yang lunas.' },
      { row: 5, column: 'Kode Tagihan', message: 'Maksimal 50 karakter.' },
      { row: 5, column: 'Nominal', message: 'Nominal harus angka rupiah bulat lebih dari 0, mis. 850000.' },
      { row: 7, column: null, message: 'NIS dan kode tagihan sama dengan baris 6.' },
      { row: 8, column: 'Kode Tagihan', message: 'Wajib diisi.' },
      { row: 8, column: 'Jenis Pembayaran', message: 'Wajib diisi.' },
      { row: 8, column: 'Nominal', message: 'Wajib diisi.' },
      { row: 8, column: 'Jatuh Tempo', message: 'Wajib diisi.' },
      { row: 8, column: 'Status', message: 'Wajib diisi.' },
    ]);
    expect(prisma.$transaction).not.toHaveBeenCalled();
    expect(prisma.bill.createMany).not.toHaveBeenCalled();
    expect(notify).not.toHaveBeenCalled();
  });

  it('422 jika header wajib tidak ada', async () => {
    const res = await upload(await xlsx([['2026001', 'SPP-1', 'SPP', 850000]], ['NIS', 'Kode Tagihan', 'Jenis Pembayaran', 'Nominal']));

    expect(res.status).toBe(422);
    expect(res.body.error.details.map((e) => [e.row, e.column])).toEqual([[1, 'Jatuh Tempo'], [1, 'Status']]);
  });
});

describe('POST /api/v1/bills/import: file dan akses', () => {
  const valid = () => xlsx([['2026001', 'SPP-2099-01', 'SPP', 850000, '2099-01-10', 'Belum Dibayar', null]]);

  it('415 jika bukan .xlsx; 400 jika tidak ada file', async () => {
    expect((await upload(await valid(), { filename: 'rekap.csv' })).status).toBe(415);
    const none = await request(app).post(URL).set('Authorization', loginAs('ADMIN'));
    expect(none.status).toBe(400);
  });

  it('401 tanpa login; 403 untuk siswa, guru, dan wali murid', async () => {
    expect((await request(app).post(URL).attach('file', await valid(), 'rekap.xlsx')).status).toBe(401);
    for (const role of ['STUDENT', 'TEACHER', 'GUARDIAN']) {
      expect((await upload(await valid(), { role })).status).toBe(403);
    }
    expect(prisma.student.findMany).not.toHaveBeenCalled();
  });
});

describe('parseAmount / parseDateCell', () => {
  it('nominal: angka bulat, teks dengan titik ribuan, awalan Rp', () => {
    expect(parseAmount(850000)).toBe(850000);
    expect(parseAmount('850000')).toBe(850000);
    expect(parseAmount('1.500.000')).toBe(1500000);
    expect(parseAmount('Rp 1.500.000')).toBe(1500000);
    expect(parseAmount('Rp. 850.000,00')).toBe(850000);
  });

  it('nominal: menolak nol, negatif, desimal, melebihi batas kolom, dan teks lain', () => {
    for (const bad of [0, -5, 12.5, 10_000_000_000, 'abc', '1,5', '', true]) {
      expect(parseAmount(bad)).toBeNull();
    }
  });

  it('tanggal: sel tanggal, angka seri Excel, teks ISO dan DD/MM/YYYY', () => {
    expect(parseDateCell(new Date('2026-09-10T00:00:00.000Z'))).toEqual(d('2026-09-10'));
    expect(parseDateCell(46275)).toEqual(d('2026-09-10')); // seri Excel untuk 10 Sep 2026
    expect(parseDateCell('2026-09-10')).toEqual(d('2026-09-10'));
    expect(parseDateCell('10/09/2026')).toEqual(d('2026-09-10'));
    expect(parseDateCell('1-9-2026')).toEqual(d('2026-09-01'));
  });

  it('tanggal: menolak tanggal yang tidak ada, di luar 2000–2100, dan format lain', () => {
    for (const bad of ['2026-02-30', '31/04/2026', '1999-12-31', '2101-01-01', 'besok', 5, null]) {
      expect(parseDateCell(bad)).toBeNull();
    }
  });
});
