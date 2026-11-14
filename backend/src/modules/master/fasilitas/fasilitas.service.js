const prisma = require("../../../lib/prisma");
const AppError = require("../../../utils/AppError");

async function list({ page, limit, type, q }) {
  const where = {
    ...(type && { type }),
    ...(q && {
      name: { contains: q },
    }),
  };

  const [items, total] = await prisma.$transaction([
    prisma.facility.findMany({
      where,
      orderBy: { name: "asc" },
      skip: (page - 1) * limit,
      take: limit,
    }),
    prisma.facility.count({ where }),
  ]);

  return { items, total };
}

async function getById(id) {
  const fasilitas = await prisma.facility.findUnique({
    where: { id },
  });

  if (!fasilitas) {
    throw new AppError(404, "NOT_FOUND", "Fasilitas tidak ditemukan.");
  }

  return fasilitas;
}

async function create(body) {
  return prisma.facility.create({
    data: body,
  });
}

async function update(id, body) {
  await getById(id);

  return prisma.facility.update({
    where: { id },
    data: body,
  });
}

async function remove(id) {
  const fasilitas = await prisma.facility.findUnique({
    where: { id },
    include: {
      _count: {
        select: {
          scheduleSlots: true,
          rescheduledChanges: true,
          bookings: true,
        },
      },
    },
  });

  if (!fasilitas) {
    throw new AppError(404, "NOT_FOUND", "Fasilitas tidak ditemukan.");
  }

  const used = fasilitas._count;

  if (
    used.scheduleSlots > 0 ||
    used.rescheduledChanges > 0 ||
    used.bookings > 0
  ) {
    throw new AppError(
      409,
      "INVALID_STATE",
      "Fasilitas sudah digunakan dalam jadwal atau peminjaman.",
    );
  }

  await prisma.facility.delete({
    where: { id },
  });
}

module.exports = {
  list,
  getById,
  create,
  update,
  remove,
};
