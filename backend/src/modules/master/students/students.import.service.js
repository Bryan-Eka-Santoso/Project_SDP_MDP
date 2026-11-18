// Impor data siswa dari Excel (F-MST-04).
// Semua baris divalidasi lebih dulu; jika ada satu saja kesalahan, tidak ada data yang disimpan.
const prisma = require('../../../lib/prisma');
const { readSheet, importInvalid } = require('../../../lib/excel');
const { parseDateOnly } = require('../../../utils/date');
const {
  readTextCell,
  validateEmailCell,
  validatePasswordCell,
  checkUniqueness,
  hashBatchPasswords,
} = require('../master.import.helper');

// Nama header sama dengan docs/import-templates/siswa.xlsx.
const COLUMNS = [
  { key: 'name', header: 'Nama' },
  { key: 'nis', header: 'NIS' },
  { key: 'birthDate', header: 'Tanggal Lahir' },
  { key: 'email', header: 'Email' },
  { key: 'password', header: 'Password', optional: true },
  { key: 'phone', header: 'No. Telepon', optional: true },
  { key: 'className', header: 'Kelas', optional: true },
];
const HEADER = Object.fromEntries(COLUMNS.map(({ key, header }) => [key, header]));

const DATE_INVALID_MESSAGE =
  'Format tanggal lahir harus YYYY-MM-DD atau DD/MM/YYYY (mis. 2008-05-14).';
const MIN_BIRTH_YEAR = 1950;
const MAX_BIRTH_YEAR = 2100;
const EXCEL_MIN_SERIAL = 18264; // 1950-01-01
const EXCEL_MAX_SERIAL = 73051; // 2100-01-01
const EXCEL_EPOCH_UTC = Date.UTC(1899, 11, 30);
const MS_PER_DAY = 86400000;

function toValidDateOnly(year, month, day) {
  if (year < MIN_BIRTH_YEAR || year > MAX_BIRTH_YEAR) {
    return { error: `Tahun lahir harus antara ${MIN_BIRTH_YEAR} dan ${MAX_BIRTH_YEAR}.` };
  }
  const iso = `${String(year).padStart(4, '0')}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
  try {
    return { date: parseDateOnly(iso) };
  } catch {
    return { error: DATE_INVALID_MESSAGE };
  }
}

function parseBirthDateValue(value) {
  if (value === null || value === undefined || value === '') {
    return { error: 'Wajib diisi.' };
  }
  if (value instanceof Date) {
    if (Number.isNaN(value.getTime())) return { error: DATE_INVALID_MESSAGE };
    return toValidDateOnly(value.getUTCFullYear(), value.getUTCMonth() + 1, value.getUTCDate());
  }
  if (typeof value === 'number') {
    if (!Number.isFinite(value) || value < EXCEL_MIN_SERIAL || value > EXCEL_MAX_SERIAL) {
      return { error: DATE_INVALID_MESSAGE };
    }
    const utcDate = new Date(EXCEL_EPOCH_UTC + Math.trunc(value) * MS_PER_DAY);
    return toValidDateOnly(
      utcDate.getUTCFullYear(),
      utcDate.getUTCMonth() + 1,
      utcDate.getUTCDate(),
    );
  }
  if (typeof value === 'string') {
    const isoMatch = /^(\d{4})[-/](\d{1,2})[-/](\d{1,2})$/.exec(value);
    if (isoMatch) {
      return toValidDateOnly(Number(isoMatch[1]), Number(isoMatch[2]), Number(isoMatch[3]));
    }
    const dmyMatch = /^(\d{1,2})[-/](\d{1,2})[-/](\d{4})$/.exec(value);
    if (dmyMatch) {
      return toValidDateOnly(Number(dmyMatch[3]), Number(dmyMatch[2]), Number(dmyMatch[1]));
    }
  }
  return { error: DATE_INVALID_MESSAGE };
}

function parseRows(rows, errors) {
  return rows.map(({ row, values }) => {
    const name = readTextCell(values.name, HEADER.name, { required: true, maxLength: 100, row, errors });
    const nis = readTextCell(values.nis, HEADER.nis, { required: true, maxLength: 30, row, errors });
    const parsedDate = parseBirthDateValue(values.birthDate);
    if (parsedDate.error) {
      errors.push({ row, column: HEADER.birthDate, message: parsedDate.error });
    }
    const email = validateEmailCell(values.email, HEADER.email, { row, errors });
    const password = validatePasswordCell(values.password, HEADER.password, { row, errors });
    const phone = readTextCell(values.phone, HEADER.phone, { required: false, maxLength: 20, row, errors });
    const className = readTextCell(values.className, HEADER.className, {
      required: false,
      maxLength: 30,
      row,
      errors,
    });

    return {
      row,
      name,
      nis,
      birthDate: parsedDate.date,
      email,
      password,
      phone: phone ?? null,
      className: className ?? null,
      classGroupId: null,
    };
  });
}

async function resolveClasses(parsedRows, errors) {
  const rowsWithClass = parsedRows.filter((item) => item.className);
  if (rowsWithClass.length === 0) return;

  const activeYear = await prisma.academicYear.findFirst({
    where: { isActive: true },
    select: { id: true, name: true },
  });
  if (!activeYear) {
    for (const item of rowsWithClass) {
      errors.push({
        row: item.row,
        column: HEADER.className,
        message: 'Belum ada tahun ajaran aktif sehingga kolom Kelas tidak bisa dipakai.',
      });
    }
    return;
  }

  const classes = await prisma.classGroup.findMany({
    where: { academicYearId: activeYear.id },
    select: { id: true, name: true, capacity: true, _count: { select: { members: true } } },
  });
  const classByName = new Map(classes.map((klass) => [klass.name.toLowerCase(), klass]));
  const plannedAdditions = new Map();

  for (const item of rowsWithClass) {
    const klass = classByName.get(item.className.toLowerCase());
    if (!klass) {
      errors.push({
        row: item.row,
        column: HEADER.className,
        message: `Kelas "${item.className}" tidak ditemukan di tahun ajaran ${activeYear.name}.`,
      });
      continue;
    }

    const currentMembers = klass._count?.members ?? 0;
    const addedSoFar = plannedAdditions.get(klass.id) ?? 0;
    if (currentMembers + addedSoFar + 1 > klass.capacity) {
      errors.push({
        row: item.row,
        column: HEADER.className,
        message: `Kapasitas kelas "${klass.name}" tidak mencukupi (maksimal ${klass.capacity} siswa).`,
      });
      continue;
    }

    plannedAdditions.set(klass.id, addedSoFar + 1);
    item.classGroupId = klass.id;
  }
}

async function validateImport(buffer) {
  const rows = await readSheet(buffer, { columns: COLUMNS });
  const errors = [];
  const parsedRows = parseRows(rows, errors);

  const nisValues = [...new Set(parsedRows.map((item) => item.nis).filter(Boolean))];
  const emailValues = [...new Set(parsedRows.map((item) => item.email).filter(Boolean))];

  const [existingStudents, existingUsers] = await Promise.all([
    nisValues.length > 0
      ? prisma.student.findMany({ where: { nis: { in: nisValues } }, select: { nis: true } })
      : [],
    emailValues.length > 0
      ? prisma.user.findMany({ where: { email: { in: emailValues } }, select: { email: true } })
      : [],
  ]);

  checkUniqueness(parsedRows, {
    valueOf: (item) => item.nis,
    column: HEADER.nis,
    label: 'NIS',
    existingKeys: new Set(existingStudents.map((s) => s.nis.toLowerCase())),
    errors,
  });
  checkUniqueness(parsedRows, {
    valueOf: (item) => item.email,
    column: HEADER.email,
    label: 'Email',
    existingKeys: new Set(existingUsers.map((u) => u.email.toLowerCase())),
    errors,
  });

  await resolveClasses(parsedRows, errors);

  if (errors.length > 0) throw importInvalid(errors);
  return parsedRows;
}

async function importStudents({ buffer }) {
  const candidates = await validateImport(buffer);
  const hashes = await hashBatchPasswords(candidates.map((item) => item.password));

  await prisma.$transaction(
    async (tx) => {
      for (const candidate of candidates) {
        const user = await tx.user.create({
          data: {
            name: candidate.name,
            email: candidate.email,
            passwordHash: hashes.get(candidate.password),
            role: 'STUDENT',
            status: 'ACTIVE',
          },
        });
        const student = await tx.student.create({
          data: {
            userId: user.id,
            nis: candidate.nis,
            birthDate: candidate.birthDate,
            phone: candidate.phone,
          },
        });
        if (candidate.classGroupId !== null) {
          await tx.classMember.create({
            data: {
              studentId: student.id,
              classGroupId: candidate.classGroupId,
            },
          });
        }
      }
    },
    { timeout: 30000 },
  );

  const assignedToClass = candidates.filter((item) => item.classGroupId !== null).length;
  return { imported: candidates.length, assignedToClass };
}

module.exports = { importStudents, COLUMNS };
