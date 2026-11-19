const prisma = require("../../lib/prisma");
const AppError = require("../../utils/AppError");

const { toPrismaPagination } = require("../../utils/pagination");

const { formatData } = require("./peminjaman.service");

async function daftar(user, query) {
  const { page, limit, status } = query;

  if (!["ADMIN", "STUDENT", "TEACHER"].includes(user.role)) {
    throw new AppError(403, "FORBIDDEN", "Tidak memiliki akses.");
  }

  const where = {};

  // Siswa dan Guru hanya boleh melihat
  // riwayat peminjamannya sendiri.
  if (user.role !== "ADMIN") {
    where.requesterId = user.id;
  }

  if (status) {
    where.status = status;
  }

  const [data, total] = await prisma.$transaction([
    prisma.facilityBooking.findMany({
      where,
      include: {
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
      },
      orderBy: [{ createdAt: "desc" }, { id: "desc" }],
      ...toPrismaPagination({ page, limit }),
    }),

    prisma.facilityBooking.count({ where }),
  ]);

  return {
    items: data.map(formatData),
    total,
  };
}

module.exports = {
  daftar,
};
