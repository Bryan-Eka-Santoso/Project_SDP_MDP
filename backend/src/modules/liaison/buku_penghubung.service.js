const prisma = require("../../lib/prisma");
const AppError = require("../../utils/AppError");

const {
  parseDateOnly,
  formatDateOnly,
  todayLocal,
} = require("../../utils/date");

// Mengambil data Guru dari user yang login.
async function getGuru(userId) {
  const guru = await prisma.teacher.findUnique({
    where: { userId },
    select: { id: true },
  });

  if (!guru) {
    throw new AppError(403, "FORBIDDEN", "Akun ini bukan Guru.");
  }

  return guru;
}

// Mengambil kelas yang diwalikan pada tahun ajaran aktif.
async function daftarKelas(userId) {
  const guru = await getGuru(userId);

  return prisma.classGroup.findMany({
    where: {
      homeroomTeacherId: guru.id,
      academicYear: {
        isActive: true,
      },
    },
    select: {
      id: true,
      name: true,
      gradeLevel: true,
    },
    orderBy: {
      name: "asc",
    },
  });
}

// Mengambil siswa dalam kelas perwalian.
async function daftarSiswa(userId, classId) {
  const guru = await getGuru(userId);

  const kelas = await prisma.classGroup.findFirst({
    where: {
      id: classId,
      homeroomTeacherId: guru.id,
      academicYear: {
        isActive: true,
      },
    },
    select: {
      id: true,
    },
  });

  if (!kelas) {
    throw new AppError(
      403,
      "FORBIDDEN",
      "Kelas bukan tanggung jawab Wali Kelas.",
    );
  }

  const anggota = await prisma.classMember.findMany({
    where: {
      classGroupId: classId,
    },
    select: {
      student: {
        select: {
          id: true,
          nis: true,
          user: {
            select: {
              name: true,
            },
          },
        },
      },
    },
  });

  return anggota
    .map((item) => ({
      id: item.student.id,
      nis: item.student.nis,
      name: item.student.user.name,
    }))
    .sort((a, b) => a.name.localeCompare(b.name, "id"));
}

// Menyimpan catatan Buku Penghubung.
async function simpan(userId, body) {
  const guru = await getGuru(userId);

  let tanggal;

  try {
    tanggal = parseDateOnly(body.date);
  } catch {
    throw new AppError(400, "INVALID_DATE", "Tanggal catatan tidak valid.");
  }

  if (body.date > todayLocal()) {
    throw new AppError(
      400,
      "INVALID_DATE",
      "Tanggal catatan tidak boleh di masa depan.",
    );
  }

  // Validasi kelas dan keanggotaan siswa.
  const kelas = await prisma.classGroup.findFirst({
    where: {
      id: body.classId,
      homeroomTeacherId: guru.id,
      academicYear: {
        isActive: true,
      },
      members: {
        some: {
          studentId: body.studentId,
        },
      },
    },
    select: {
      id: true,
      name: true,
    },
  });

  if (!kelas) {
    throw new AppError(
      403,
      "FORBIDDEN",
      "Siswa tidak termasuk kelas perwalian Anda.",
    );
  }

  const catatan = await prisma.liaisonNote.create({
    data: {
      studentId: body.studentId,
      homeroomTeacherId: guru.id,
      date: tanggal,
      content: body.content,
    },
    include: {
      student: {
        select: {
          nis: true,
          user: {
            select: {
              name: true,
            },
          },
        },
      },
    },
  });

  return {
    id: catatan.id,
    studentId: catatan.studentId,
    studentName: catatan.student.user.name,
    nis: catatan.student.nis,
    classId: kelas.id,
    className: kelas.name,
    date: formatDateOnly(catatan.date),
    content: catatan.content,
  };
}

module.exports = {
  daftarKelas,
  daftarSiswa,
  simpan,
};
