const prisma = require("../../lib/prisma");
const AppError = require("../../utils/AppError");
const { formatDateOnly } = require("../../utils/date");

function tolakAkses() {
  throw new AppError(
    404,
    "NOT_FOUND",
    "Siswa tidak ditemukan atau tidak dapat diakses.",
  );
}

// Daftar siswa yang boleh dibaca user.
async function daftarSiswa(user) {
  let where;

  if (user.role === "GUARDIAN") {
    where = {
      guardians: {
        some: {
          guardian: { userId: user.id },
        },
      },
    };
  } else if (user.role === "TEACHER") {
    where = {
      classMemberships: {
        some: {
          classGroup: {
            homeroomTeacher: {
              userId: user.id,
            },
            academicYear: {
              isActive: true,
            },
          },
        },
      },
    };
  } else {
    throw new AppError(403, "FORBIDDEN", "Akses ditolak.");
  }

  const siswa = await prisma.student.findMany({
    where,
    select: {
      id: true,
      nis: true,
      user: {
        select: { name: true },
      },
      classMemberships: {
        where: {
          classGroup: {
            academicYear: { isActive: true },
          },
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

  return siswa
    .map((s) => ({
      id: s.id,
      nis: s.nis,
      name: s.user.name,
      className:
        s.classMemberships.map((x) => x.classGroup.name).join(", ") || "-",
    }))
    .sort((a, b) => a.name.localeCompare(b.name, "id"));
}

// Mengambil catatan siswa setelah memeriksa akses.
async function riwayat(user, studentId) {
  let allowed;

  if (user.role === "GUARDIAN") {
    allowed = await prisma.studentGuardian.findFirst({
      where: {
        studentId,
        guardian: { userId: user.id },
      },
      select: { studentId: true },
    });
  } else if (user.role === "TEACHER") {
    allowed = await prisma.student.findFirst({
      where: {
        id: studentId,
        classMemberships: {
          some: {
            classGroup: {
              homeroomTeacher: {
                userId: user.id,
              },
              academicYear: {
                isActive: true,
              },
            },
          },
        },
      },
      select: { id: true },
    });
  }

  if (!allowed) tolakAkses();

  const siswa = await prisma.student.findUnique({
    where: { id: studentId },
    select: {
      id: true,
      nis: true,
      user: {
        select: { name: true },
      },
    },
  });

  if (!siswa) tolakAkses();

  // Wali Kelas hanya membaca catatan yang ditulisnya.
  // Wali Murid membaca seluruh catatan anaknya.
  const where = { studentId };

  if (user.role === "TEACHER") {
    const guru = await prisma.teacher.findUnique({
      where: { userId: user.id },
      select: { id: true },
    });

    where.homeroomTeacherId = guru.id;
  }

  const catatan = await prisma.liaisonNote.findMany({
    where,
    include: {
      homeroomTeacher: {
        select: {
          user: {
            select: { name: true },
          },
        },
      },
    },
    orderBy: [{ date: "desc" }, { id: "desc" }],
  });

  return {
    student: {
      id: siswa.id,
      nis: siswa.nis,
      name: siswa.user.name,
    },
    total: catatan.length,
    notes: catatan.map((item) => ({
      id: item.id,
      date: formatDateOnly(item.date),
      content: item.content,
      teacherName: item.homeroomTeacher.user.name,
    })),
  };
}

module.exports = {
  daftarSiswa,
  riwayat,
};
