// Melihat tagihan (F-BYR-02). Tagihan hanya informasi dari rekap yayasan (docs/BUSINESS_RULES.md 10).
const prisma = require('../../lib/prisma');
const AppError = require('../../utils/AppError');
const { parseDateOnly, formatDateOnly, todayLocal } = require('../../utils/date');
const { toPrismaPagination } = require('../../utils/pagination');
const { assertCanViewStudentBills, homeroomClassIds } = require('./bills.policy');

const billSelect = {
  id: true,
  billCode: true,
  paymentType: true,
  amount: true,
  dueDate: true,
  status: true,
  paidDate: true,
};

// Menunggak = belum dibayar DAN sudah lewat jatuh tempo, menurut tanggal WIB (hari jatuh tempo belum menunggak).
function toBillDto(bill, today) {
  const dueDate = formatDateOnly(bill.dueDate);
  return {
    id: bill.id,
    billCode: bill.billCode,
    paymentType: bill.paymentType,
    amount: Number(bill.amount),
    dueDate,
    status: bill.status,
    paidDate: bill.paidDate ? formatDateOnly(bill.paidDate) : null,
    isOverdue: bill.status === 'UNPAID' && dueDate < today,
  };
}

function billFilters({ status, overdue, dueFrom, dueTo }, today) {
  const todayDate = parseDateOnly(today);
  const and = [];
  if (status) and.push({ status });
  if (overdue === true) and.push({ status: 'UNPAID', dueDate: { lt: todayDate } });
  if (overdue === false) and.push({ OR: [{ status: 'PAID' }, { dueDate: { gte: todayDate } }] });
  if (dueFrom) and.push({ dueDate: { gte: parseDateOnly(dueFrom) } });
  if (dueTo) and.push({ dueDate: { lte: parseDateOnly(dueTo) } });
  return and;
}

// Tagihan satu siswa: siswa sendiri, wali murid (anak), wali kelas (siswa kelasnya), admin.
async function listStudentBills(user, studentId, { page, limit, ...filters }) {
  await assertCanViewStudentBills(user, studentId);

  const today = todayLocal();
  const where = { AND: [{ studentId }, ...billFilters(filters, today)] };
  const [rows, total] = await Promise.all([
    prisma.bill.findMany({
      where,
      select: billSelect,
      orderBy: [{ dueDate: 'desc' }, { id: 'desc' }],
      ...toPrismaPagination({ page, limit }),
    }),
    prisma.bill.count({ where }),
  ]);
  return { items: rows.map((row) => toBillDto(row, today)), total };
}

// Daftar tagihan banyak siswa: admin (semua) dan wali kelas (hanya kelas binaannya).
async function listBills(user, { page, limit, classGroupId, q, ...filters }) {
  let classIds = classGroupId ? [classGroupId] : null;
  if (user.role === 'TEACHER') {
    const own = await homeroomClassIds(user);
    if (classGroupId && !own.includes(classGroupId)) {
      throw new AppError(404, 'NOT_FOUND', 'Kelas tidak ditemukan.');
    }
    classIds = classGroupId ? [classGroupId] : own;
  }

  const student = {};
  if (classIds) student.classMemberships = { some: { classGroupId: { in: classIds } } };
  if (q) student.OR = [{ nis: { contains: q } }, { user: { name: { contains: q } } }];

  const today = todayLocal();
  const where = { AND: [...billFilters(filters, today)], ...(Object.keys(student).length ? { student } : {}) };

  const [rows, total] = await Promise.all([
    prisma.bill.findMany({
      where,
      select: {
        ...billSelect,
        student: {
          select: {
            id: true,
            nis: true,
            user: { select: { name: true } },
            // Kelas pada tahun ajaran aktif; siswa bisa ada di lebih dari satu kelas (kelas paket).
            classMemberships: {
              where: { classGroup: { academicYear: { isActive: true } } },
              select: { classGroup: { select: { id: true, name: true } } },
            },
          },
        },
      },
      orderBy: [{ dueDate: 'desc' }, { id: 'desc' }],
      ...toPrismaPagination({ page, limit }),
    }),
    prisma.bill.count({ where }),
  ]);

  const items = rows.map((row) => ({
    ...toBillDto(row, today),
    student: {
      id: row.student.id,
      nis: row.student.nis,
      name: row.student.user.name,
      classGroups: row.student.classMemberships.map((m) => m.classGroup),
    },
  }));
  return { items, total };
}

module.exports = { listStudentBills, listBills, toBillDto };
