const prisma = require("../../../lib/prisma");
const AppError = require("../../../utils/AppError");

// Menampilkan semua mata pelajaran
async function list({ page, limit, search }) {
  const where = search
    ? {
        OR: [
          { code: { contains: search } },
          { name: { contains: search } },
          { type: { contains: search } },
        ],
      }
    : {};

  const [items, total] = await prisma.$transaction([
    prisma.subject.findMany({
      where,
      orderBy: { name: "asc" },
      skip: (page - 1) * limit,
      take: limit,
    }),
    prisma.subject.count({ where }),
  ]);

  return { items, total };
}

// Detail mata pelajaran
async function getById(id) {
  const item = await prisma.subject.findUnique({
    where: { id },
  });

  if (!item) {
    throw new AppError(404, "NOT_FOUND", "Mata pelajaran tidak ditemukan.");
  }

  return item;
}

// Tambah mata pelajaran
async function create(body) {
  const duplicate = await prisma.subject.findUnique({
    where: { code: body.code },
  });

  if (duplicate) {
    throw new AppError(
      409,
      "DUPLICATE",
      "Kode mata pelajaran sudah digunakan.",
    );
  }

  return prisma.subject.create({
    data: body,
  });
}

// Edit mata pelajaran
async function update(id, body) {
  await getById(id);

  const duplicate = await prisma.subject.findFirst({
    where: {
      code: body.code,
      id: { not: id },
    },
  });

  if (duplicate) {
    throw new AppError(
      409,
      "DUPLICATE",
      "Kode mata pelajaran sudah digunakan.",
    );
  }

  return prisma.subject.update({
    where: { id },
    data: body,
  });
}

// Hapus mata pelajaran
async function remove(id) {
  await getById(id);

  const used = await prisma.subject.findUnique({
    where: { id },
    select: {
      _count: {
        select: {
          teachingAssignments: true,
          packageItems: true,
          surveyResponses: true,
        },
      },
    },
  });

  if (Object.values(used._count).some((count) => count > 0)) {
    throw new AppError(
      409,
      "INVALID_STATE",
      "Mata pelajaran sudah digunakan dalam data akademik.",
    );
  }

  await prisma.subject.delete({
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
