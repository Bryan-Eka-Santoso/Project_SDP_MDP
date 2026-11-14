// Impor rekap pembayaran dari yayasan (F-BYR-01, docs/BUSINESS_RULES.md 2.3 dan 10) dan
// notifikasi tunggakan sesudahnya (F-BYR-03). Alurnya sama dengan impor jadwal: validasi SEMUA
// baris dulu, satu kesalahan membatalkan seluruh impor (ADR-012).
const prisma = require('../../lib/prisma');
const { parseDateOnly, todayLocal } = require('../../utils/date');
const { readSheet, importInvalid, cellText } = require('../../lib/excel');
const { notify } = require('../notifications/notifications.service');

// Satu rekap bisa memuat banyak bulan untuk seluruh siswa, jadi batasnya lebih besar dari default.
const MAX_ROWS = 20000;
const MAX_AMOUNT = 9_999_999_999; // batas kolom Decimal(12,2)
const MAX_TEXT = 50; // VarChar(50): kode tagihan dan jenis pembayaran

// Nama header sama dengan docs/import-templates/pembayaran.xlsx.
const COLUMNS = [
  { key: 'nis', header: 'NIS' },
  { key: 'billCode', header: 'Kode Tagihan' },
  { key: 'paymentType', header: 'Jenis Pembayaran' },
  { key: 'amount', header: 'Nominal' },
  { key: 'dueDate', header: 'Jatuh Tempo' },
  { key: 'status', header: 'Status' },
  { key: 'paidDate', header: 'Tanggal Bayar', optional: true },
];
const HEADER = Object.fromEntries(COLUMNS.map(({ key, header }) => [key, header]));

// "Menunggak" hanya status tampilan (BUSINESS_RULES 10): di data tetap UNPAID. Rekap yayasan
// kadang memakai istilah itu, jadi dikenali sebagai belum dibayar.
const STATUS_BY_TEXT = new Map([
  ['lunas', 'PAID'],
  ['belum dibayar', 'UNPAID'],
  ['belum lunas', 'UNPAID'],
  ['menunggak', 'UNPAID'],
]);

const idr = new Intl.NumberFormat('id-ID');
const longDate = new Intl.DateTimeFormat('id-ID', { day: 'numeric', month: 'long', year: 'numeric', timeZone: 'UTC' });

// Angka bulat rupiah. Sel angka Excel, atau teks "1500000", "1.500.000", "Rp 1.500.000".
function parseAmount(value) {
  let amount = null;
  if (typeof value === 'number') {
    amount = value;
  } else if (typeof value === 'string') {
    const match = /^(?:rp\.?\s*)?(\d{1,3}(?:\.\d{3})+|\d+)(?:,00)?$/i.exec(value);
    if (match) amount = Number(match[1].replace(/\./g, ''));
  }
  return Number.isInteger(amount) && amount > 0 && amount <= MAX_AMOUNT ? amount : null;
}

const pad = (number) => String(number).padStart(2, '0');

/**
 * Tanggal dari sel Excel: sel tanggal (Date UTC), angka seri Excel, atau teks YYYY-MM-DD /
 * DD/MM/YYYY / DD-MM-YYYY. Mengembalikan Date UTC tengah malam (cocok untuk `@db.Date`) atau null.
 */
function parseDateCell(value) {
  let ymd = null;
  if (value instanceof Date) {
    ymd = `${value.getUTCFullYear()}-${pad(value.getUTCMonth() + 1)}-${pad(value.getUTCDate())}`;
  } else if (typeof value === 'number' && value > 20000 && value < 80000) {
    // Angka seri Excel = hari sejak 30 Desember 1899.
    ymd = new Date((Math.floor(value) - 25569) * 86400000).toISOString().slice(0, 10);
  } else if (typeof value === 'string') {
    const iso = /^(\d{4})-(\d{1,2})-(\d{1,2})$/.exec(value);
    const local = /^(\d{1,2})[/-](\d{1,2})[/-](\d{4})$/.exec(value);
    if (iso) ymd = `${iso[1]}-${pad(iso[2])}-${pad(iso[3])}`;
    else if (local) ymd = `${local[3]}-${pad(local[2])}-${pad(local[1])}`;
  }
  if (ymd === null) return null;

  try {
    const date = parseDateOnly(ymd);
    const year = date.getUTCFullYear();
    return year >= 2000 && year <= 2100 ? date : null;
  } catch {
    return null; // tanggal yang tidak ada, mis. 2026-02-30
  }
}

/**
 * Validasi satu baris. Semua kesalahan baris itu dicatat ke `errors`, bukan berhenti di yang
 * pertama. Mengembalikan data tagihan jika valid, null jika tidak.
 */
function validateRow({ row, values }, studentsByNis, errors) {
  const before = errors.length;
  const fail = (column, message) => errors.push({ row, column, message });

  const text = (key, { max } = {}) => {
    const cell = cellText(values[key]);
    if (cell.error) fail(HEADER[key], cell.error);
    else if (cell.text === null) fail(HEADER[key], 'Wajib diisi.');
    else if (max && cell.text.length > max) fail(HEADER[key], `Maksimal ${max} karakter.`);
    else return cell.text;
    return undefined;
  };

  const nis = text('nis');
  const billCode = text('billCode', { max: MAX_TEXT });
  const paymentType = text('paymentType', { max: MAX_TEXT });

  const student = nis === undefined ? undefined : studentsByNis.get(nis);
  if (nis !== undefined && !student) fail(HEADER.nis, `NIS “${nis}” tidak ditemukan di data siswa.`);

  let amount;
  if (values.amount === null) fail(HEADER.amount, 'Wajib diisi.');
  else {
    amount = parseAmount(values.amount);
    if (amount === null) fail(HEADER.amount, 'Nominal harus angka rupiah bulat lebih dari 0, mis. 850000.');
  }

  let dueDate;
  if (values.dueDate === null) fail(HEADER.dueDate, 'Wajib diisi.');
  else {
    dueDate = parseDateCell(values.dueDate);
    if (dueDate === null) fail(HEADER.dueDate, 'Format tanggal harus YYYY-MM-DD atau DD/MM/YYYY, mis. 2026-09-10.');
  }

  let status;
  const statusText = text('status');
  if (statusText !== undefined) {
    status = STATUS_BY_TEXT.get(statusText.toLowerCase());
    if (!status) fail(HEADER.status, `Status “${statusText}” tidak dikenal. Gunakan Lunas atau Belum Dibayar.`);
  }

  let paidDate = null;
  if (values.paidDate !== null) {
    paidDate = parseDateCell(values.paidDate);
    if (paidDate === null) fail(HEADER.paidDate, 'Format tanggal harus YYYY-MM-DD atau DD/MM/YYYY, mis. 2026-09-08.');
    else if (status === 'UNPAID') fail(HEADER.paidDate, 'Tanggal bayar hanya boleh diisi untuk tagihan yang lunas.');
  }

  if (errors.length > before) return null;
  return { row, student, billCode, paymentType, amount, dueDate, status, paidDate };
}

const keyOf = (studentId, billCode) => `${studentId}|${billCode.toLowerCase()}`;

const sameDate = (a, b) => (a === null || b === null ? a === b : a.getTime() === b.getTime());

const isChanged = (current, next) =>
  current.paymentType !== next.paymentType ||
  Number(current.amount) !== next.amount ||
  !sameDate(current.dueDate, next.dueDate) ||
  current.status !== next.status ||
  !sameDate(current.paidDate, next.paidDate);

/**
 * Baca dan validasi file tanpa menyimpan apa pun; melempar 422 IMPORT_INVALID jika ada kesalahan.
 * Mengembalikan rencana: tagihan baru, tagihan yang berubah, dan jumlah yang tidak berubah.
 */
async function planImport({ buffer }) {
  const rows = await readSheet(buffer, { columns: COLUMNS, maxRows: MAX_ROWS });

  // NIS dibaca dulu (tanpa melaporkan kesalahan) untuk memuat siswa dan tagihan lamanya sekaligus.
  const nisList = [
    ...new Set(
      rows
        .map(({ values }) => cellText(values.nis).text)
        .filter((nis) => typeof nis === 'string'),
    ),
  ];
  const students = await prisma.student.findMany({
    where: { nis: { in: nisList } },
    select: { id: true, nis: true, userId: true, user: { select: { name: true } } },
  });
  const studentsByNis = new Map(
    students.map((student) => [student.nis, { id: student.id, userId: student.userId, name: student.user.name }]),
  );
  const existingBills = await prisma.bill.findMany({
    where: { studentId: { in: students.map((student) => student.id) } },
    select: { id: true, studentId: true, billCode: true, paymentType: true, amount: true, dueDate: true, status: true, paidDate: true },
  });
  const existing = new Map(existingBills.map((bill) => [keyOf(bill.studentId, bill.billCode), bill]));

  const errors = [];
  const seen = new Map();
  const valid = [];
  for (const raw of rows) {
    const bill = validateRow(raw, studentsByNis, errors);
    if (!bill) continue;

    // Satu tagihan = NIS + kode tagihan (unik di database); dua baris yang sama ambigu.
    const key = keyOf(bill.student.id, bill.billCode);
    if (seen.has(key)) {
      errors.push({ row: bill.row, column: null, message: `NIS dan kode tagihan sama dengan baris ${seen.get(key)}.` });
      continue;
    }
    seen.set(key, bill.row);
    valid.push(bill);
  }
  if (errors.length > 0) throw importInvalid(errors);

  const creates = [];
  const updates = [];
  for (const bill of valid) {
    const current = existing.get(keyOf(bill.student.id, bill.billCode));
    if (!current) creates.push(bill);
    else if (isChanged(current, bill)) updates.push({ ...bill, id: current.id });
  }
  return { valid, creates, updates, unchanged: valid.length - creates.length - updates.length };
}

// Tunggakan per siswa dari baris-baris rekap: belum dibayar dan sudah lewat jatuh tempo (WIB).
function overdueByStudent(valid, today) {
  const todayDate = parseDateOnly(today);
  const groups = new Map();
  for (const bill of valid) {
    if (bill.status !== 'UNPAID' || bill.dueDate.getTime() >= todayDate.getTime()) continue;
    if (!groups.has(bill.student.id)) groups.set(bill.student.id, { student: bill.student, bills: [] });
    groups.get(bill.student.id).bills.push(bill);
  }
  return groups;
}

function overdueMessage({ student, bills }) {
  if (bills.length === 1) {
    const [bill] = bills;
    return `${student.name}: tagihan ${bill.paymentType} (${bill.billCode}) sebesar Rp ${idr.format(bill.amount)} jatuh tempo ${longDate.format(bill.dueDate)} dan belum dibayar.`;
  }
  const total = bills.reduce((sum, bill) => sum + bill.amount, 0);
  return `${student.name}: ${bills.length} tagihan belum dibayar dan sudah lewat jatuh tempo, total Rp ${idr.format(total)}.`;
}

/**
 * F-BYR-03: kirim BILL_OVERDUE ke siswa dan wali muridnya. Tidak ditunggu; kegagalan hanya
 * dicatat di log karena data impor sudah tersimpan.
 */
function notifyOverdue(groups) {
  (async () => {
    const studentIds = [...groups.keys()];
    const links = await prisma.studentGuardian.findMany({
      where: { studentId: { in: studentIds } },
      select: { studentId: true, guardian: { select: { userId: true } } },
    });
    const guardiansByStudent = new Map();
    for (const link of links) {
      if (!guardiansByStudent.has(link.studentId)) guardiansByStudent.set(link.studentId, []);
      guardiansByStudent.get(link.studentId).push(link.guardian.userId);
    }

    for (const group of groups.values()) {
      notify([group.student.userId, ...(guardiansByStudent.get(group.student.id) ?? [])], {
        type: 'BILL_OVERDUE',
        title: 'Tunggakan pembayaran',
        body: overdueMessage(group),
        referenceId: group.student.id,
      });
    }
  })().catch((err) => console.error('[notifikasi] gagal menyiapkan tunggakan pembayaran:', err));
}

// F-BYR-01: tagihan baru dibuat, tagihan yang sudah ada (NIS + kode tagihan) diperbarui.
async function importBills({ buffer }) {
  const { valid, creates, updates, unchanged } = await planImport({ buffer });

  const operations = [];
  if (creates.length > 0) {
    operations.push(
      prisma.bill.createMany({
        data: creates.map((bill) => ({
          studentId: bill.student.id,
          billCode: bill.billCode,
          paymentType: bill.paymentType,
          amount: String(bill.amount),
          dueDate: bill.dueDate,
          status: bill.status,
          paidDate: bill.paidDate,
        })),
      }),
    );
  }
  for (const bill of updates) {
    operations.push(
      prisma.bill.update({
        where: { id: bill.id },
        data: {
          paymentType: bill.paymentType,
          amount: String(bill.amount),
          dueDate: bill.dueDate,
          status: bill.status,
          paidDate: bill.paidDate,
        },
      }),
    );
  }
  // Satu transaksi: semua tagihan tersimpan atau tidak sama sekali.
  if (operations.length > 0) await prisma.$transaction(operations);

  // Impor yang tidak mengubah apa pun (mis. file yang sama terunggah dua kali) tidak mengirim ulang
  // notifikasi ke orang tua yang sama.
  let notifiedStudents = 0;
  if (operations.length > 0) {
    const groups = overdueByStudent(valid, todayLocal());
    notifiedStudents = groups.size;
    if (groups.size > 0) notifyOverdue(groups);
  }

  return { created: creates.length, updated: updates.length, unchanged, notifiedStudents };
}

module.exports = { importBills, planImport, COLUMNS, parseAmount, parseDateCell };
