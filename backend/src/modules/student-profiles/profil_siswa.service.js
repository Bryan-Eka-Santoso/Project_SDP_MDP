const prisma = require("../../lib/prisma");
const AppError = require("../../utils/AppError");
const { formatDateOnly } = require("../../utils/date");

function tidakDitemukan() {
  return new AppError(
    404,
    "NOT_FOUND",
    "Siswa tidak ditemukan atau bukan siswa binaan Anda.",
  );
}

// Daftar siswa dari kelas perwalian tahun ajaran aktif.
async function daftarSiswa(userId) {
  const kelas = await prisma.classGroup.findMany({
    where: {
      homeroomTeacher: {
        userId: userId,
      },
      academicYear: {
        isActive: true,
      },
    },
    select: {
      id: true,
      name: true,
      members: {
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
      },
    },
    orderBy: {
      name: "asc",
    },
  });

  const hasil = [];

  for (const item of kelas) {
    for (const anggota of item.members) {
      const siswa = anggota.student;

      hasil.push({
        id: siswa.id,
        nis: siswa.nis,
        name: siswa.user.name,
        classId: item.id,
        className: item.name,
      });
    }
  }

  return hasil.sort((a, b) => a.name.localeCompare(b.name, "id"));
}

// Detail siswa, termasuk seluruh wali murid terhubung.
async function detailSiswa(userId, studentId) {
  // Validasi akses harus dilakukan pada query.
  const siswa = await prisma.student.findFirst({
    where: {
      id: studentId,
      classMemberships: {
        some: {
          classGroup: {
            academicYear: {
              isActive: true,
            },
            homeroomTeacher: {
              userId: userId,
            },
          },
        },
      },
    },
    select: {
      id: true,
      nis: true,
      birthDate: true,
      phone: true,

      user: {
        select: {
          name: true,
          email: true,
          status: true,
        },
      },

      classMemberships: {
        where: {
          classGroup: {
            academicYear: {
              isActive: true,
            },
            homeroomTeacher: {
              userId: userId,
            },
          },
        },
        select: {
          classGroup: {
            select: {
              id: true,
              name: true,
              gradeLevel: true,
              academicYear: {
                select: {
                  name: true,
                },
              },
            },
          },
        },
      },

      guardians: {
        select: {
          relationship: true,
          guardian: {
            select: {
              id: true,
              phone: true,
              user: {
                select: {
                  name: true,
                  email: true,
                },
              },
            },
          },
        },
      },
    },
  });

  if (!siswa) {
    throw tidakDitemukan();
  }

  const kelas = siswa.classMemberships.map((item) => ({
    id: item.classGroup.id,
    name: item.classGroup.name,
    gradeLevel: item.classGroup.gradeLevel,
    academicYear: item.classGroup.academicYear.name,
  }));

  const waliMurid = siswa.guardians.map((item) => ({
    id: item.guardian.id,
    name: item.guardian.user.name,
    relationship: item.relationship,
    phone: item.guardian.phone,
    email: item.guardian.user.email,
  }));

  return {
    id: siswa.id,
    nis: siswa.nis,
    name: siswa.user.name,
    email: siswa.user.email,
    birthDate: formatDateOnly(siswa.birthDate),
    phone: siswa.phone,
    status: siswa.user.status,
    classes: kelas,
    guardians: waliMurid,
  };
}

module.exports = {
  daftarSiswa,
  detailSiswa,
};
