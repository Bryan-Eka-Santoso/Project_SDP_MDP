const prisma = require("../../lib/prisma");
const AppError = require("../../utils/AppError");

const { notify } = require("../notifications/notifications.service");

const { toPrismaPagination } = require("../../utils/pagination");

const includeDetail = {
  facility: {
    select: {
      id: true,
      name: true,
      type: true,
    },
  },
  requester: {
    select: {
      id: true,
      name: true,
      email: true,
    },
  },
  verifier: {
    select: {
      id: true,
      name: true,
    },
  },
};

// Format tanggal dan jam untuk aplikasi Android.
function formatData(item) {
  return {
    id: item.id,
    facilityId: item.facilityId,
    facilityName: item.facility.name,
    facilityType: item.facility.type,
    requesterId: item.requesterId,
    requesterName: item.requester.name,
    bookingDate: item.bookingDate.toISOString().slice(0, 10),
    startTime: item.startTime.toISOString().slice(11, 16),
    endTime: item.endTime.toISOString().slice(11, 16),
    purpose: item.purpose,
    status: item.status,
    verifierName: item.verifier?.name || null,
  };
}

async function list(query) {
  const { page, limit, status } = query;

  const where = status ? { status } : {};

  const [items, total] = await prisma.$transaction([
    prisma.facilityBooking.findMany({
      where,
      include: includeDetail,
      orderBy: {
        createdAt: "desc",
      },
      ...toPrismaPagination({ page, limit }),
    }),
    prisma.facilityBooking.count({ where }),
  ]);

  return {
    items: items.map(formatData),
    total,
  };
}

async function getById(id) {
  const item = await prisma.facilityBooking.findUnique({
    where: { id },
    include: includeDetail,
  });

  if (!item) {
    throw new AppError(
      404,
      "NOT_FOUND",
      "Pengajuan peminjaman tidak ditemukan.",
    );
  }

  return formatData(item);
}

function bentrok(startA, endA, startB, endB) {
  return startA < endB && startB < endA;
}

// Mengembalikan slot jadwal efektif pada tanggal tertentu.
// Perubahan CANCEL diabaikan.
// Perubahan RESCHEDULE memakai jam dan ruangan baru.
async function cekBentrokJadwal(tx, booking) {
  if (booking.facility.type !== "ROOM") return;

  const namaHari = [
    "SUNDAY",
    "MONDAY",
    "TUESDAY",
    "WEDNESDAY",
    "THURSDAY",
    "FRIDAY",
    "SATURDAY",
  ];

  const hari = namaHari[booking.bookingDate.getUTCDay()];

  const slots = await tx.scheduleSlot.findMany({
    where: {
      dayOfWeek: hari === "SUNDAY" ? undefined : hari,
      ...(hari === "SUNDAY" && {
        id: { equals: -1 },
      }),
    },
    include: {
      changes: {
        where: {
          date: booking.bookingDate,
        },
      },
    },
  });

  // Perubahan jadwal pada tanggal tersebut juga diperiksa.
  const changes = await tx.scheduleChange.findMany({
    where: {
      date: booking.bookingDate,
    },
    include: {
      scheduleSlot: true,
    },
  });

  const jadwal = new Map();

  for (const slot of slots) {
    jadwal.set(slot.id, {
      roomId: slot.roomId,
      startTime: slot.startTime,
      endTime: slot.endTime,
      active: true,
    });
  }

  for (const change of changes) {
    const slot = change.scheduleSlot;

    if (change.type === "CANCEL") {
      jadwal.delete(slot.id);
      continue;
    }

    jadwal.set(slot.id, {
      roomId: change.newRoomId ?? slot.roomId,
      startTime: change.newStartTime ?? slot.startTime,
      endTime: change.newEndTime ?? slot.endTime,
      active: true,
    });
  }

  for (const slot of jadwal.values()) {
    if (
      slot.roomId === booking.facilityId &&
      bentrok(booking.startTime, booking.endTime, slot.startTime, slot.endTime)
    ) {
      throw new AppError(
        409,
        "BOOKING_CONFLICT",
        "Ruangan sedang digunakan pada jadwal pelajaran.",
      );
    }
  }
}

// Cek bentrok pengajuan yang telah disetujui.
async function cekBentrokPeminjaman(tx, booking) {
  const lainnya = await tx.facilityBooking.findMany({
    where: {
      facilityId: booking.facilityId,
      bookingDate: booking.bookingDate,
      status: "APPROVED",
      id: {
        not: booking.id,
      },
    },
  });

  const adaBentrok = lainnya.some((item) =>
    bentrok(booking.startTime, booking.endTime, item.startTime, item.endTime),
  );

  if (adaBentrok) {
    throw new AppError(
      409,
      "BOOKING_CONFLICT",
      "Fasilitas sudah dipinjam pada waktu tersebut.",
    );
  }
}

// Setujui atau tolak pengajuan.
// Perubahan status harus berasal dari PENDING.
async function putuskan(id, adminId, keputusan) {
  const status = keputusan === "approve" ? "APPROVED" : "REJECTED";

  const result = await prisma.$transaction(
    async (tx) => {
      const booking = await tx.facilityBooking.findUnique({
        where: { id },
        include: {
          facility: true,
        },
      });

      if (!booking) {
        throw new AppError(404, "NOT_FOUND", "Pengajuan tidak ditemukan.");
      }

      if (booking.status !== "PENDING") {
        throw new AppError(
          409,
          "INVALID_STATE",
          "Pengajuan sudah diproses sebelumnya.",
        );
      }

      if (status === "APPROVED") {
        await cekBentrokPeminjaman(tx, booking);
        await cekBentrokJadwal(tx, booking);
      }

      // Hanya update jika masih PENDING.
      const updated = await tx.facilityBooking.updateMany({
        where: {
          id,
          status: "PENDING",
        },
        data: {
          status,
          verifierId: adminId,
        },
      });

      if (updated.count !== 1) {
        throw new AppError(409, "INVALID_STATE", "Pengajuan sudah diproses.");
      }

      return tx.facilityBooking.findUnique({
        where: { id },
        include: includeDetail,
      });
    },
    {
      isolationLevel: "Serializable",
    },
  );

  // Kirim notifikasi setelah transaksi berhasil.
  notify([result.requesterId], {
    type: "BOOKING",
    title:
      status === "APPROVED" ? "Peminjaman Disetujui" : "Peminjaman Ditolak",
    body:
      status === "APPROVED"
        ? `Pengajuan ${result.facility.name} telah disetujui.`
        : `Pengajuan ${result.facility.name} telah ditolak.`,
    referenceId: result.id,
  });

  return formatData(result);
}

async function approve(id, adminId) {
  return putuskan(id, adminId, "approve");
}

async function reject(id, adminId) {
  return putuskan(id, adminId, "reject");
}

module.exports = {
  list,
  getById,
  approve,
  reject,
};
