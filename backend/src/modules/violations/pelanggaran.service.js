const prisma = require("../../lib/prisma");
const AppError = require("../../utils/AppError");

const {
  parseDateOnly,
  formatDateOnly,
  todayLocal,
} = require("../../utils/date");

const { notify } = require("../notifications/notifications.service");

const {
  guardianUserIdsOfStudents,
} = require("../notifications/notifications.recipients");

async function getGuru(userId) {
  const guru = await prisma.teacher.findUnique({
    where: { userId },
    select: { id: true },
  });

  if (!guru) {
    throw new AppError(403, "FORBIDDEN", "Akun ini bukan guru.");
  }

  return guru;
}

// Hanya siswa pada kelas yang diajar / diwalikan.
function kelasGuru(guruId) {
  return {
    OR: [
      {
        teachingAssignments: {
          some: {
            teacherId: guruId,
            semester: { isActive: true },
          },
        },
      },
      {
        homeroomTeacherId: guruId,
        academicYear: { isActive: true },
      },
    ],
  };
}

async function daftarSiswa(userId) {
  const guru = await getGuru(userId);

  const siswa = await prisma.student.findMany({
    where: {
      classMemberships: {
        some: {
          classGroup: kelasGuru(guru.id),
        },
      },
    },
    select: {
      id: true,
      nis: true,
      user: {
        select: { name: true },
      },
      classMemberships: {
        where: {
          classGroup: kelasGuru(guru.id),
        },
        select: {
          classGroup: {
            select: { name: true },
          },
        },
      },
    },
    orderBy: { nis: "asc" },
  });

  return siswa.map((item) => ({
    id: item.id,
    nis: item.nis,
    name: item.user.name,
    className: item.classMemberships.map((x) => x.classGroup.name).join(", "),
  }));
}

async function daftarJenis() {
  return prisma.violationType.findMany({
    select: {
      id: true,
      name: true,
      points: true,
    },
    orderBy: { name: "asc" },
  });
}

async function simpan(userId, body) {
  const guru = await getGuru(userId);

  let tanggal;

  try {
    tanggal = parseDateOnly(body.date);
  } catch {
    throw new AppError(400, "INVALID_DATE", "Tanggal pelanggaran tidak valid.");
  }

  if (body.date > todayLocal()) {
    throw new AppError(
      400,
      "INVALID_DATE",
      "Tanggal pelanggaran tidak boleh di masa depan.",
    );
  }

  const siswa = await prisma.student.findFirst({
    where: {
      id: body.studentId,
      classMemberships: {
        some: {
          classGroup: kelasGuru(guru.id),
        },
      },
    },
    select: {
      id: true,
      user: {
        select: { name: true },
      },
    },
  });

  if (!siswa) {
    throw new AppError(
      403,
      "FORBIDDEN",
      "Siswa tidak ditemukan pada kelas yang menjadi tanggung jawab guru.",
    );
  }

  const jenis = await prisma.violationType.findUnique({
    where: { id: body.violationTypeId },
  });

  if (!jenis) {
    throw new AppError(404, "NOT_FOUND", "Jenis pelanggaran tidak ditemukan.");
  }

  const result = await prisma.violation.create({
    data: {
      studentId: siswa.id,
      violationTypeId: jenis.id,
      reporterId: guru.id,
      date: tanggal,
      points: jenis.points,
      description: body.description || null,
    },
  });

  let guardianCount = 0;

  try {
    // Penerima adalah akun wali murid, bukan akun siswa.
    const waliIds = await guardianUserIdsOfStudents([siswa.id]);
    guardianCount = waliIds.length;

    if (waliIds.length > 0) {
      notify(waliIds, {
        type: "VIOLATION",
        title: "Pemberitahuan Pelanggaran Siswa",
        body:
          `${siswa.user.name} tercatat melakukan ` +
          `pelanggaran "${jenis.name}" dengan bobot ` +
          `${jenis.points} poin.`,
        referenceId: result.id,
      });
    }
  } catch (err) {
    console.error("[pelanggaran] gagal memproses notifikasi:", err);
  }

  return {
    id: result.id,
    studentId: siswa.id,
    studentName: siswa.user.name,
    violationTypeId: jenis.id,
    violationName: jenis.name,
    points: result.points,
    date: formatDateOnly(result.date),
    description: result.description,
    guardianCount,
  };
}

module.exports = {
  daftarSiswa,
  daftarJenis,
  simpan,
};
