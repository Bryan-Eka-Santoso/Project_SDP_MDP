const prisma = require("../../../lib/prisma");
const AppError = require("../../../utils/AppError");

// Menampilkan semua jenis pelanggaran
async function list({ page, limit, q }) {
  const where = q
    ? {
        name: { contains: q },
      }
    : {};

  const [items, total] = await prisma.$transaction([
    prisma.violationType.findMany({
      where,
      orderBy: { name: "asc" },
      skip: (page - 1) * limit,
      take: limit,
    }),
    prisma.violationType.count({ where }),
  ]);

  return { items, total };
}

// Detail jenis pelanggaran
async function getById(id) {
  const item = await prisma.violationType.findUnique({
    where: { id },
  });

  if (!item) {
    throw new AppError(404, "NOT_FOUND", "Jenis pelanggaran tidak ditemukan.");
  }

  return item;
}

// Tambah jenis pelanggaran
async function create(body) {
  const duplicate = await prisma.violationType.findFirst({
    where: { name: body.name },
  });

  if (duplicate) {
    throw new AppError(409, "DUPLICATE", "Jenis pelanggaran sudah terdaftar.");
  }

  return prisma.violationType.create({
    data: body,
  });
}

// Edit jenis pelanggaran dan bobot poin
async function update(id, body) {
  await getById(id);

  if (body.name !== undefined) {
    const duplicate = await prisma.violationType.findFirst({
      where: {
        name: body.name,
        id: { not: id },
      },
    });

    if (duplicate) {
      throw new AppError(
        409,
        "DUPLICATE",
        "Nama jenis pelanggaran sudah digunakan.",
      );
    }
  }

  return prisma.violationType.update({
    where: { id },
    data: body,
  });
}

// Hapus jenis pelanggaran
async function remove(id) {
  const item = await prisma.violationType.findUnique({
    where: { id },
    include: {
      _count: {
        select: {
          violations: true,
        },
      },
    },
  });

  if (!item) {
    throw new AppError(404, "NOT_FOUND", "Jenis pelanggaran tidak ditemukan.");
  }

  if (item._count.violations > 0) {
    throw new AppError(
      409,
      "INVALID_STATE",
      "Jenis pelanggaran sudah digunakan dalam riwayat pelanggaran siswa.",
    );
  }

  await prisma.violationType.delete({
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
