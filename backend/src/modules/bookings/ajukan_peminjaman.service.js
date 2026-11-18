const prisma = require("../../lib/prisma");
const AppError = require("../../utils/AppError");

const {
  parseDateOnly,
  parseTimeOfDay,
  todayLocal,
  timeLocal,
} = require("../../utils/date");

async function daftarFasilitas() {
  return prisma.facility.findMany({
    where: {
      quantity: { gt: 0 },
    },
    select: {
      id: true,
      name: true,
      type: true,
      quantity: true,
    },
    orderBy: {
      name: "asc",
    },
  });
}

async function ajukan(userId, body) {
  let tanggal;
  let jamMulai;
  let jamSelesai;

  try {
    tanggal = parseDateOnly(body.bookingDate);
    jamMulai = parseTimeOfDay(body.startTime);
    jamSelesai = parseTimeOfDay(body.endTime);
  } catch {
    throw new AppError(
      400,
      "INVALID_DATE_TIME",
      "Tanggal atau jam peminjaman tidak valid.",
    );
  }

  const hariIni = todayLocal();

  if (body.bookingDate < hariIni) {
    throw new AppError(
      400,
      "INVALID_DATE",
      "Tanggal peminjaman tidak boleh di masa lalu.",
    );
  }

  if (body.bookingDate === hariIni && body.startTime <= timeLocal()) {
    throw new AppError(
      400,
      "INVALID_TIME",
      "Jam mulai peminjaman harus setelah waktu sekarang.",
    );
  }

  const fasilitas = await prisma.facility.findUnique({
    where: { id: body.facilityId },
  });

  if (!fasilitas || fasilitas.quantity <= 0) {
    throw new AppError(404, "NOT_FOUND", "Fasilitas tidak tersedia.");
  }

  // Memeriksa peminjaman yang sudah disetujui.
  // Pengajuan tetap diverifikasi kembali oleh Admin.
  const approved = await prisma.facilityBooking.findMany({
    where: {
      facilityId: fasilitas.id,
      bookingDate: tanggal,
      status: "APPROVED",
    },
  });

  const bentrok = approved.some(
    (item) => jamMulai < item.endTime && item.startTime < jamSelesai,
  );

  if (bentrok) {
    throw new AppError(
      409,
      "BOOKING_CONFLICT",
      "Fasilitas telah dipinjam pada waktu tersebut.",
    );
  }

  const result = await prisma.facilityBooking.create({
    data: {
      facilityId: fasilitas.id,
      requesterId: userId,
      bookingDate: tanggal,
      startTime: jamMulai,
      endTime: jamSelesai,
      purpose: body.purpose,
      status: "PENDING",
    },
  });

  return {
    id: result.id,
    facilityId: fasilitas.id,
    facilityName: fasilitas.name,
    facilityType: fasilitas.type,
    bookingDate: body.bookingDate,
    startTime: body.startTime,
    endTime: body.endTime,
    purpose: result.purpose,
    status: result.status,
  };
}

module.exports = {
  daftarFasilitas,
  ajukan,
};
