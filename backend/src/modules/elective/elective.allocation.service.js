const crypto = require("node:crypto");
const prisma = require("../../lib/prisma");
const AppError = require("../../utils/AppError");

// F-MPP-05: pembagian merata, bukan mengisi kelas pertama sampai penuh.
function balancedSizes(count, capacity) {
  if (
    !Number.isInteger(count) ||
    count < 0 ||
    !Number.isInteger(capacity) ||
    capacity < 1
  ) {
    throw new Error("Jumlah siswa dan kapasitas tidak valid.");
  }
  if (count === 0) return [];
  const classCount = Math.ceil(count / capacity);
  const base = Math.floor(count / classCount);
  const extra = count % classCount;
  return Array.from(
    { length: classCount },
    (_, i) => base + (i < extra ? 1 : 0),
  );
}

function signatureOf(packageId, gradeLevel, capacity, ids) {
  return crypto
    .createHash("sha256")
    .update(JSON.stringify({ packageId, gradeLevel, capacity, ids }))
    .digest("hex");
}

function className(packageId, gradeLevel, index) {
  // Maksimal 30 karakter (batas ClassGroup.name pada Prisma).
  const name = `MPP-${packageId}-${gradeLevel}-${index}`;
  if (name.length > 30) {
    throw new AppError(
      400,
      "VALIDATION_ERROR",
      "Kode tingkat terlalu panjang untuk nama kelas.",
    );
  }
  return name;
}

async function plan(tx, { packageId, gradeLevel }) {
  const pkg = await tx.subjectPackage.findUnique({
    where: { id: packageId },
    select: {
      id: true,
      name: true,
      capacity: true,
      academicYearId: true,
      academicYear: { select: { id: true, name: true } },
      selections: {
        select: {
          studentId: true,
          student: {
            select: {
              nis: true,
              user: { select: { name: true } },
              classMemberships: {
                select: {
                  classGroup: {
                    select: { academicYearId: true, gradeLevel: true },
                  },
                },
              },
            },
          },
        },
        orderBy: { studentId: "asc" },
      },
    },
  });
  if (!pkg)
    throw new AppError(
      404,
      "NOT_FOUND",
      "Paket mata pelajaran tidak ditemukan.",
    );
  if (!Number.isInteger(pkg.capacity) || pkg.capacity <= 0) {
    throw new AppError(
      409,
      "INVALID_STATE",
      "Kapasitas paket harus lebih besar dari nol.",
    );
  }
  const members = pkg.selections.map((entry) => ({
    studentId: entry.studentId,
    nis: entry.student.nis,
    studentName: entry.student.user.name,
    otherGrades: entry.student.classMemberships
      .filter((m) => m.classGroup.academicYearId === pkg.academicYearId)
      .map((m) => m.classGroup.gradeLevel),
  }));
  // Mencegah admin memasukkan siswa lintas tingkat pada tahun ajaran yang sama.
  if (members.some((m) => m.otherGrades.some((g) => g !== gradeLevel))) {
    throw new AppError(
      409,
      "GRADE_MISMATCH",
      "Ada siswa paket yang tercatat di tingkat berbeda pada tahun ajaran ini.",
    );
  }
  const ids = members.map((m) => m.studentId);
  const sizes = balancedSizes(members.length, pkg.capacity);
  let offset = 0;
  const classes = sizes.map((size, index) => {
    const students = members.slice(offset, offset + size).map((m) => ({
      studentId: m.studentId,
      nis: m.nis,
      studentName: m.studentName,
    }));
    offset += size;
    return {
      name: className(packageId, gradeLevel, index + 1),
      capacity: pkg.capacity,
      students,
    };
  });
  return {
    packageId: pkg.id,
    packageName: pkg.name,
    academicYearId: pkg.academicYear.id,
    academicYearName: pkg.academicYear.name,
    gradeLevel,
    capacity: pkg.capacity,
    totalStudents: members.length,
    totalClasses: classes.length,
    signature: signatureOf(packageId, gradeLevel, pkg.capacity, ids),
    classes,
  };
}

async function preview(input) {
  return plan(prisma, input);
}

async function apply(input) {
  return prisma.$transaction(async (tx) => {
    const current = await plan(tx, input);
    if (current.signature !== input.signature) {
      throw new AppError(
        409,
        "STALE_PREVIEW",
        "Pilihan siswa telah berubah. Perbarui pratinjau sebelum menyimpan.",
      );
    }
    if (current.classes.length === 0) {
      throw new AppError(
        409,
        "EMPTY_SELECTION",
        "Belum ada siswa yang memilih paket ini.",
      );
    }
    const existing = await tx.classGroup.findMany({
      where: {
        academicYearId: current.academicYearId,
        name: { in: current.classes.map((c) => c.name) },
      },
      select: { name: true },
    });
    if (existing.length) {
      throw new AppError(
        409,
        "ALREADY_ALLOCATED",
        "Kelas pembagian sudah ada. Periksa kelas sebelum menjalankan ulang.",
      );
    }
    const created = [];
    for (const entry of current.classes) {
      const group = await tx.classGroup.create({
        data: {
          academicYearId: current.academicYearId,
          gradeLevel: current.gradeLevel,
          capacity: current.capacity,
          name: entry.name,
        },
        select: { id: true, name: true },
      });
      await tx.classMember.createMany({
        data: entry.students.map((student) => ({
          studentId: student.studentId,
          classGroupId: group.id,
        })),
      });
      created.push({ ...group, studentCount: entry.students.length });
    }
    return {
      packageId: current.packageId,
      academicYearId: current.academicYearId,
      classes: created,
    };
  });
}

// F-MPP-06: bandingkan popularitas NAMA paket antar tahun (ID paket berbeda tiap tahun).
async function trends() {
  const years = await prisma.academicYear.findMany({
    select: {
      id: true,
      name: true,
      startDate: true,
      subjectPackages: {
        select: {
          id: true,
          name: true,
          capacity: true,
          _count: { select: { selections: true } },
        },
        orderBy: { id: "asc" },
      },
    },
    orderBy: [{ startDate: "asc" }, { id: "asc" }],
  });
  const packages = new Map();
  for (const year of years) {
    for (const pkg of year.subjectPackages) {
      const key = pkg.name.trim().toLocaleLowerCase("id-ID");
      if (!packages.has(key))
        packages.set(key, {
          packageName: pkg.name,
          totalSelections: 0,
          byYear: [],
        });
      const group = packages.get(key);
      group.totalSelections += pkg._count.selections;
      group.byYear.push({
        academicYearId: year.id,
        academicYearName: year.name,
        packageId: pkg.id,
        selections: pkg._count.selections,
      });
    }
  }
  return {
    academicYears: years.map(({ id, name }) => ({ id, name })),
    packages: [...packages.values()].sort((a, b) =>
      a.packageName.localeCompare(b.packageName, "id-ID"),
    ),
  };
}

module.exports = { balancedSizes, preview, apply, trends };
