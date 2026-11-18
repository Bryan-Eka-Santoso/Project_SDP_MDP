const { z } = require("zod");
const prisma = require("../../lib/prisma");
const { parseDateOnly, formatDateOnly } = require("../../utils/date");

const positiveId = z.coerce.number().int().positive();

const dateOnly = z.string().refine(
  (value) => {
    try {
      parseDateOnly(value);
      return true;
    } catch {
      return false;
    }
  },
  {
    message: "Tanggal harus berformat YYYY-MM-DD.",
  },
);

const makeQuery = (extra) =>
  z
    .object({
      ...extra,
      classGroupId: positiveId.optional(),
      violationTypeId: positiveId.optional(),
      dateFrom: dateOnly.optional(),
      dateTo: dateOnly.optional(),
    })
    .refine(
      (data) => !data.dateFrom || !data.dateTo || data.dateFrom <= data.dateTo,
      {
        path: ["dateTo"],
        message: "Tanggal akhir tidak boleh sebelum tanggal awal.",
      },
    );

const columns = [
  { header: "Tanggal", width: 16 },
  { header: "NIS", width: 18 },
  { header: "Nama Siswa", width: 28 },
  { header: "Kelas", width: 18 },
  { header: "Jenis Pelanggaran", width: 32 },
  { header: "Poin", width: 12 },
  { header: "Guru Pelapor", width: 28 },
  { header: "Keterangan", width: 45 },
];

function toCells(row) {
  return [
    row.date,
    row.nis,
    row.studentName,
    row.className,
    row.violationTypeName,
    row.points,
    row.reporterName,
    row.description || "",
  ];
}

function toRow(item) {
  const kelas = item.student.classMemberships
    .map((member) => member.classGroup.name)
    .join(", ");

  return {
    id: item.id,
    date: formatDateOnly(item.date),
    studentId: item.studentId,
    nis: item.student.nis,
    studentName: item.student.user.name,
    className: kelas || "-",
    violationTypeId: item.violationTypeId,
    violationTypeName: item.violationType.name,
    points: item.points,
    reporterName: item.reporter.user.name,
    description: item.description,
  };
}

async function load(
  { classGroupId, violationTypeId, dateFrom, dateTo },
  { skip, take },
) {
  const where = {
    ...(violationTypeId && {
      violationTypeId,
    }),

    ...(dateFrom || dateTo
      ? {
          date: {
            ...(dateFrom && {
              gte: parseDateOnly(dateFrom),
            }),
            ...(dateTo && {
              lte: parseDateOnly(dateTo),
            }),
          },
        }
      : {}),

    ...(classGroupId && {
      student: {
        classMemberships: {
          some: {
            classGroupId,
          },
        },
      },
    }),
  };

  const [items, total] = await Promise.all([
    prisma.violation.findMany({
      where,
      select: {
        id: true,
        date: true,
        studentId: true,
        violationTypeId: true,
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

        student: {
          select: {
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
        },
      },
      orderBy: [{ date: "desc" }, { id: "desc" }],
      skip,
      take,
    }),

    prisma.violation.count({ where }),
  ]);

  return {
    items: items.map(toRow),
    total,
  };
}

module.exports = {
  code: "F-LAP-03",
  title: "Laporan Pelanggaran Siswa",
  makeQuery,
  columns,
  toCells,
  load,
};
