// Laporan status pembayaran (F-LAP-05, PO-10): rincian per tagihan siswa beserta status tampilnya.
const { z } = require('zod');
const prisma = require('../../lib/prisma');
const { parseDateOnly, todayLocal } = require('../../utils/date');
const { billFilters, studentBillSelect, toStudentBillDto } = require('../bills/bills.service');
const { positiveId, dateOnlySchema } = require('./reports.helpers');

const STATUS_LABELS = Object.freeze({
  PAID: 'Lunas',
  OVERDUE: 'Menunggak',
  UNPAID: 'Belum dibayar',
});

const filters = {
  classGroupId: positiveId.optional(),
  status: z.enum(['PAID', 'UNPAID', 'OVERDUE']).optional(),
  dueFrom: dateOnlySchema.optional(),
  dueTo: dateOnlySchema.optional(),
};

const makeQuery = (extra) =>
  z.object({ ...extra, ...filters }).refine((d) => !d.dueFrom || !d.dueTo || d.dueFrom <= d.dueTo, {
    path: ['dueTo'],
    message: 'Tanggal akhir tidak boleh sebelum tanggal awal.',
  });

const columns = [
  { header: 'NIS', width: 16 },
  { header: 'Nama Siswa', width: 26 },
  { header: 'Kelas', width: 16 },
  { header: 'Kode Tagihan', width: 20 },
  { header: 'Jenis Pembayaran', width: 24 },
  { header: 'Nominal (IDR)', width: 18 },
  { header: 'Jatuh Tempo', width: 14 },
  { header: 'Status', width: 16 },
  { header: 'Tanggal Bayar', width: 14 },
];

function displayStatus(row) {
  if (row.status === 'PAID') return 'PAID';
  return row.isOverdue ? 'OVERDUE' : 'UNPAID';
}

const toCells = (row) => [
  row.student.nis,
  row.student.name,
  row.student.classGroups.map((group) => group.name).join(', '),
  row.billCode,
  row.paymentType,
  row.amount,
  row.dueDate,
  STATUS_LABELS[displayStatus(row)],
  row.paidDate ?? '',
];

function statusCondition(status, today) {
  if (status === 'PAID') return billFilters({ status: 'PAID' }, today);
  if (status === 'OVERDUE') return billFilters({ overdue: true }, today);
  // UNPAID pada laporan = belum dibayar dan belum lewat jatuh tempo (dibedakan dari Menunggak).
  if (status === 'UNPAID') return [{ status: 'UNPAID', dueDate: { gte: parseDateOnly(today) } }];
  return [];
}

async function load({ classGroupId, status, dueFrom, dueTo }, { skip, take }) {
  const today = todayLocal();
  const and = [
    ...statusCondition(status, today),
    ...billFilters({ dueFrom, dueTo }, today),
    ...(classGroupId ? [{ student: { classMemberships: { some: { classGroupId } } } }] : []),
  ];
  const where = and.length > 0 ? { AND: and } : {};

  const [rows, total] = await Promise.all([
    prisma.bill.findMany({
      where,
      select: studentBillSelect,
      orderBy: [{ dueDate: 'desc' }, { id: 'desc' }],
      skip,
      take,
    }),
    prisma.bill.count({ where }),
  ]);

  return { items: rows.map((row) => toStudentBillDto(row, today)), total };
}

module.exports = {
  code: 'F-LAP-05',
  title: 'Status Pembayaran',
  makeQuery,
  columns,
  toCells,
  load,
};
