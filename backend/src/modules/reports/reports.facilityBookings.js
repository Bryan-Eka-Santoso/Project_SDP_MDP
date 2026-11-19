const { z } = require("zod");
const prisma = require("../../lib/prisma");

const {
  parseDateOnly,
  formatDateOnly,
  formatTimeOfDay,
} = require("../../utils/date");

const positiveId = z.coerce.number().int().positive();

const dateOnly = z.string().refine(
  (value) => {
    try {
      parseDateOnly(value);
      return true;
    } catch {
      return false;
    }
  },
  {
    message: "Tanggal harus berformat YYYY-MM-DD.",
  },
);

const STATUS_LABELS = {
  PENDING: "Menunggu",
  APPROVED: "Disetujui",
  REJECTED: "Ditolak",
  RETURNED: "Dikembalikan",
};

const TYPE_LABELS = {
  ROOM: "Ruangan",
  ITEM: "Barang",
};

const makeQuery = (extra) =>
  z
    .object({
      ...extra,
      facilityId: positiveId.optional(),
      status: z
        .enum(["PENDING", "APPROVED", "REJECTED", "RETURNED"])
        .optional(),
      dateFrom: dateOnly.optional(),
      dateTo: dateOnly.optional(),
    })
    .refine(
      (data) => !data.dateFrom || !data.dateTo || data.dateFrom <= data.dateTo,
      {
        path: ["dateTo"],
        message: "Tanggal akhir tidak boleh sebelum tanggal awal.",
      },
    );

const columns = [
  { header: "ID Peminjaman", width: 18 },
  { header: "Tanggal Pinjam", width: 18 },
  { header: "Fasilitas", width: 28 },
  { header: "Jenis Fasilitas", width: 20 },
  { header: "Nama Peminjam", width: 28 },
  { header: "Email Peminjam", width: 30 },
  { header: "Jam Mulai", width: 14 },
  { header: "Jam Selesai", width: 14 },
  { header: "Keperluan", width: 40 },
  { header: "Status", width: 18 },
  { header: "Verifikator", width: 28 },
  { header: "Tanggal Kembali", width: 18 },
];

function toRow(item) {
  return {
    id: item.id,
    facilityId: item.facilityId,
    facilityName: item.facility.name,
    facilityType: item.facility.type,
    requesterId: item.requesterId,
    requesterName: item.requester.name,
    requesterEmail: item.requester.email,
    bookingDate: formatDateOnly(item.bookingDate),
    startTime: formatTimeOfDay(item.startTime),
    endTime: formatTimeOfDay(item.endTime),
    purpose: item.purpose,
    status: item.status,
    verifierName: item.verifier?.name || null,
    returnDate: item.returnDate ? formatDateOnly(item.returnDate) : null,
  };
}

function toCells(row) {
  return [
    row.id,
    row.bookingDate,
    row.facilityName,
    TYPE_LABELS[row.facilityType] || row.facilityType,
    row.requesterName,
    row.requesterEmail,
    row.startTime,
    row.endTime,
    row.purpose,
    STATUS_LABELS[row.status] || row.status,
    row.verifierName || "-",
    row.returnDate || "-",
  ];
}

async function load({ facilityId, status, dateFrom, dateTo }, { skip, take }) {
  const where = {
    ...(facilityId && { facilityId }),
    ...(status && { status }),

    ...(dateFrom || dateTo
      ? {
          bookingDate: {
            ...(dateFrom && {
              gte: parseDateOnly(dateFrom),
            }),
            ...(dateTo && {
              lte: parseDateOnly(dateTo),
            }),
          },
        }
      : {}),
  };

  const [items, total] = await Promise.all([
    prisma.facilityBooking.findMany({
      where,
      select: {
        id: true,
        facilityId: true,
        requesterId: true,
        bookingDate: true,
        startTime: true,
        endTime: true,
        purpose: true,
        status: true,
        returnDate: true,

        facility: {
          select: {
            name: true,
            type: true,
          },
        },

        requester: {
          select: {
            name: true,
            email: true,
          },
        },

        verifier: {
          select: {
            name: true,
          },
        },
      },
      orderBy: [{ bookingDate: "desc" }, { id: "desc" }],
      skip,
      take,
    }),

    prisma.facilityBooking.count({ where }),
  ]);

  return {
    items: items.map(toRow),
    total,
  };
}

module.exports = {
  code: "F-LAP-04",
  title: "Laporan Peminjaman Fasilitas",
  makeQuery,
  columns,
  toCells,
  load,
};
