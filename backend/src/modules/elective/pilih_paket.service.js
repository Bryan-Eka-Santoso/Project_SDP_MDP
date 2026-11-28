const prisma = require("../../lib/prisma");
const AppError = require("../../utils/AppError");

const {
  todayLocal,
  parseDateOnly,
  formatDateOnly,
} = require("../../utils/date");

function fail(status, code, message) {
  throw new AppError(status, code, message);
}

async function getStudent(userId, db = prisma) {
  const student = await db.student.findUnique({
    where: { userId },
    select: { id: true },
  });

  if (!student) {
    fail(403, "FORBIDDEN", "Akun siswa tidak ditemukan.");
  }

  return student;
}

async function getActiveYear(db = prisma) {
  const year = await db.academicYear.findFirst({
    where: { isActive: true },
    select: {
      id: true,
      name: true,
    },
  });

  if (!year) {
    fail(404, "NOT_FOUND", "Tahun ajaran aktif tidak tersedia.");
  }

  return year;
}

async function checkClass(studentId, academicYearId, db = prisma) {
  const member = await db.classMember.findFirst({
    where: {
      studentId,
      classGroup: { academicYearId },
    },
    select: { studentId: true },
  });

  if (!member) {
    fail(
      403,
      "FORBIDDEN",
      "Anda belum terdaftar pada kelas tahun ajaran aktif.",
    );
  }
}

async function daftarPaket(userId) {
  const student = await getStudent(userId);
  const year = await getActiveYear();

  await checkClass(student.id, year.id);

  const [packages, selected] = await Promise.all([
    prisma.subjectPackage.findMany({
      where: { academicYearId: year.id },
      select: {
        id: true,
        name: true,
        capacity: true,
        items: {
          select: {
            subject: {
              select: {
                id: true,
                code: true,
                name: true,
              },
            },
          },
          orderBy: {
            subject: { name: "asc" },
          },
        },
        _count: {
          select: { selections: true },
        },
      },
      orderBy: [{ name: "asc" }, { id: "asc" }],
    }),

    prisma.packageSelection.findFirst({
      where: {
        studentId: student.id,
        package: {
          academicYearId: year.id,
        },
      },
      select: {
        packageId: true,
        selectedDate: true,
      },
    }),
  ]);

  return {
    academicYearName: year.name,
    selectedPackageId: selected?.packageId ?? null,
    selectedDate: selected ? formatDateOnly(selected.selectedDate) : null,
    packages: packages.map((pkg) => ({
      id: pkg.id,
      name: pkg.name,
      capacity: pkg.capacity,
      selectionCount: pkg._count.selections,
      // Kapasitas berlaku per kelas, bukan batas peminat paket (BUSINESS_RULES 8.4).
      classCount: Math.ceil(pkg._count.selections / pkg.capacity),
      isSelected: pkg.id === selected?.packageId,
      subjects: pkg.items.map((item) => item.subject),
    })),
  };
}

async function pilihPaket(userId, packageId) {
  const today = todayLocal();
  const selectedDate = parseDateOnly(today);

  // Retry untuk konflik transaksi serializable (P2034).
  for (let attempt = 0; attempt < 3; attempt++) {
    try {
      return await prisma.$transaction(
        async (tx) => {
          const student = await getStudent(userId, tx);
          const year = await getActiveYear(tx);

          await checkClass(student.id, year.id, tx);

          const pkg = await tx.subjectPackage.findFirst({
            where: {
              id: packageId,
              academicYearId: year.id,
            },
            select: {
              id: true,
              name: true,
              capacity: true,
              _count: {
                select: { items: true },
              },
            },
          });

          if (!pkg) {
            fail(
              404,
              "NOT_FOUND",
              "Paket tidak tersedia pada tahun ajaran aktif.",
            );
          }

          if (pkg._count.items === 0) {
            fail(409, "EMPTY_PACKAGE", "Paket belum mempunyai mata pelajaran.");
          }

          const existing = await tx.packageSelection.findMany({
            where: {
              studentId: student.id,
              package: {
                academicYearId: year.id,
              },
            },
            select: { packageId: true },
          });

          if (existing.length === 1 && existing[0].packageId === packageId) {
            return {
              packageId,
              packageName: pkg.name,
              selectedDate: today,
              alreadySelected: true,
              message: "Paket sudah menjadi pilihan Anda.",
            };
          }

          await tx.packageSelection.deleteMany({
            where: {
              studentId: student.id,
              package: {
                academicYearId: year.id,
              },
            },
          });

          await tx.packageSelection.create({
            data: {
              studentId: student.id,
              packageId,
              selectedDate,
            },
          });

          return {
            packageId,
            packageName: pkg.name,
            selectedDate: today,
            alreadySelected: false,
            message: "Paket mata pelajaran berhasil dipilih.",
          };
        },
        {
          isolationLevel: "Serializable",
        },
      );
    } catch (error) {
      if (error.code === "P2034" && attempt < 2) {
        continue;
      }

      if (error.code === "P2034") {
        fail(
          409,
          "CONCURRENT_UPDATE",
          "Paket sedang dipilih siswa lain. Silakan coba lagi.",
        );
      }

      throw error;
    }
  }
}

module.exports = {
  daftarPaket,
  pilihPaket,
};
