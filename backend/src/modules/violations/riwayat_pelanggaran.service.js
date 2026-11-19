const prisma = require("../../lib/prisma");
const AppError = require("../../utils/AppError");

function tidakDitemukan() {
  return new AppError(
    404,
    "NOT_FOUND",
    "Data siswa atau riwayat pelanggaran tidak ditemukan.",
  );
}

// Mengambil daftar siswa sesuai role.
async function daftarSiswa(user) {
  let where = {};

  if (user.role === "STUDENT") {
    where = {
      userId: user.id,
    };
  } else if (user.role === "GUARDIAN") {
    where = {
      guardians: {
        some: {
          guardian: {
            userId: user.id,
          },
        },
      },
    };
  } else if (user.role === "TEACHER") {
    where = {
      classMemberships: {
        some: {
          classGroup: {
            academicYear: {
              isActive: true,
            },
            OR: [
              {
                homeroomTeacher: {
                  userId: user.id,
                },
              },
              {
                teachingAssignments: {
                  some: {
                    teacher: {
                      userId: user.id,
                    },
                    semester: {
                      isActive: true,
                    },
                  },
                },
              },
            ],
          },
        },
      },
    };
  } else if (user.role !== "ADMIN") {
    throw new AppError(403, "FORBIDDEN", "Tidak memiliki akses.");
  }

  const siswa = await prisma.student.findMany({
    where,
    select: {
      id: true,
      nis: true,
      user: {
        select: {
          name: true,
        },
      },
      classMemberships: {
        where: {
          classGroup: {
            academicYear: {
              isActive: true,
            },
          },
        },
        select: {
          classGroup: {
            select: {
              name: true,
            },
          },
        },
      },
    },
    orderBy: {
      nis: "asc",
    },
  });

  return siswa
    .map((item) => ({
      id: item.id,
      nis: item.nis,
      name: item.user.name,
      className:
        item.classMemberships.map((x) => x.classGroup.name).join(", ") || "-",
    }))
    .sort((a, b) => a.name.localeCompare(b.name, "id"));
}

// Memastikan user boleh membaca siswa yang dipilih.
async function cekAkses(user, studentId) {
  if (user.role === "ADMIN") {
    return;
  }

  if (user.role === "STUDENT") {
    const siswa = await prisma.student.findFirst({
      where: {
        id: studentId,
        userId: user.id,
      },
      select: { id: true },
    });

    if (!siswa) throw tidakDitemukan();
    return;
  }

  if (user.role === "GUARDIAN") {
    const relasi = await prisma.studentGuardian.findFirst({
      where: {
        studentId,
        guardian: {
          userId: user.id,
        },
      },
      select: {
        studentId: true,
      },
    });

    if (!relasi) throw tidakDitemukan();
    return;
  }

  if (user.role === "TEACHER") {
    const siswa = await prisma.student.findFirst({
      where: {
        id: studentId,
        classMemberships: {
          some: {
            classGroup: {
              academicYear: {
                isActive: true,
              },
              OR: [
                {
                  homeroomTeacher: {
                    userId: user.id,
                  },
                },
                {
                  teachingAssignments: {
                    some: {
                      teacher: {
                        userId: user.id,
                      },
                      semester: {
                        isActive: true,
                      },
                    },
                  },
                },
              ],
            },
          },
        },
      },
      select: { id: true },
    });

    if (!siswa) throw tidakDitemukan();
    return;
  }

  throw new AppError(403, "FORBIDDEN", "Tidak memiliki akses.");
}

// Melihat akumulasi dan rincian kejadian.
async function getRiwayat(user, studentId) {
  await cekAkses(user, studentId);

  const siswa = await prisma.student.findUnique({
    where: { id: studentId },
    select: {
      id: true,
      nis: true,
      user: {
        select: {
          name: true,
        },
      },
      classMemberships: {
        where: {
          classGroup: {
            academicYear: {
              isActive: true,
            },
          },
        },
        select: {
          classGroup: {
            select: {
              name: true,
            },
          },
        },
      },
    },
  });

  if (!siswa) throw tidakDitemukan();

  const [rekap, riwayat] = await prisma.$transaction([
    prisma.violation.aggregate({
      where: {
        studentId,
      },
      _sum: {
        points: true,
      },
      _count: {
        id: true,
      },
    }),

    prisma.violation.findMany({
      where: {
        studentId,
      },
      select: {
        id: true,
        date: true,
        points: true,
        description: true,
        violationType: {
          select: {
            name: true,
          },
        },
        reporter: {
          select: {
            user: {
              select: {
                name: true,
              },
            },
          },
        },
      },
      orderBy: [{ date: "desc" }, { id: "desc" }],
    }),
  ]);

  return {
    student: {
      id: siswa.id,
      nis: siswa.nis,
      name: siswa.user.name,
      className:
        siswa.classMemberships.map((x) => x.classGroup.name).join(", ") || "-",
    },

    totalPoints: rekap._sum.points ?? 0,
    totalViolations: rekap._count.id,

    violations: riwayat.map((item) => ({
      id: item.id,
      name: item.violationType.name,
      date: item.date.toISOString().slice(0, 10),
      points: item.points,
      description: item.description,
      reporterName: item.reporter.user.name,
    })),
  };
}

module.exports = {
  daftarSiswa,
  getRiwayat,
};
