// Seeder data awal + data demo. Aman dijalankan berulang (upsert / cari-dulu-baru-buat).
// Jalankan lewat `npx prisma db seed` (dikonfigurasi di prisma.config.ts).
const bcrypt = require('bcrypt');
const prisma = require('../src/lib/prisma');
const env = require('../src/config/env');

const BCRYPT_ROUNDS = 10;
const DEMO_EMAIL_DOMAIN = 'santaagnes.test';

// Kolom @db.Date dan @db.Time disimpan sebagai DateTime UTC.
const date = (iso) => new Date(`${iso}T00:00:00.000Z`);
const time = (hhmm) => new Date(`1970-01-01T${hhmm}:00.000Z`);

function requirePassword(name, value) {
  if (!value || value.length < 8) {
    throw new Error(`${name} wajib diisi di .env dan minimal 8 karakter.`);
  }
  return value;
}

async function findOrCreate(model, where, data) {
  const existing = await model.findFirst({ where });
  return existing ?? model.create({ data: { ...where, ...data } });
}

async function upsertUser(email, role, passwordHash) {
  return prisma.user.upsert({
    where: { email },
    update: {},
    create: { email, role, passwordHash },
  });
}

// ---------------------------------------------------------------------
// Data minimal: admin pertama + periode aktif
// ---------------------------------------------------------------------

async function seedAdmin() {
  const passwordHash = await bcrypt.hash(
    requirePassword('SEED_ADMIN_PASSWORD', env.seed.adminPassword),
    BCRYPT_ROUNDS,
  );
  const user = await upsertUser(env.seed.adminEmail, 'ADMIN', passwordHash);
  return prisma.admin.upsert({
    where: { userId: user.id },
    update: {},
    create: { userId: user.id, name: 'Administrator' },
  });
}

async function seedPeriod() {
  const academicYear = await prisma.academicYear.upsert({
    where: { name: '2026/2027' },
    update: {},
    create: { name: '2026/2027', startDate: date('2026-07-13'), endDate: date('2027-06-30') },
  });
  const odd = await prisma.semester.upsert({
    where: { academicYearId_type: { academicYearId: academicYear.id, type: 'ODD' } },
    update: {},
    create: {
      academicYearId: academicYear.id,
      type: 'ODD',
      startDate: date('2026-07-13'),
      endDate: date('2026-12-19'),
    },
  });
  await prisma.semester.upsert({
    where: { academicYearId_type: { academicYearId: academicYear.id, type: 'EVEN' } },
    update: {},
    create: {
      academicYearId: academicYear.id,
      type: 'EVEN',
      startDate: date('2027-01-04'),
      endDate: date('2027-06-30'),
    },
  });

  // Hanya satu tahun ajaran dan satu semester aktif (BUSINESS_RULES 1).
  await prisma.$transaction([
    prisma.academicYear.updateMany({ where: { id: { not: academicYear.id } }, data: { isActive: false } }),
    prisma.academicYear.update({ where: { id: academicYear.id }, data: { isActive: true } }),
    prisma.semester.updateMany({ where: { id: { not: odd.id } }, data: { isActive: false } }),
    prisma.semester.update({ where: { id: odd.id }, data: { isActive: true } }),
  ]);

  return { academicYear, semester: odd };
}

// ---------------------------------------------------------------------
// Data demo
// ---------------------------------------------------------------------

const SUBJECTS = [
  { code: 'MAT', name: 'Matematika', type: 'Wajib' },
  { code: 'BIN', name: 'Bahasa Indonesia', type: 'Wajib' },
  { code: 'BIO', name: 'Biologi', type: 'Pilihan' },
  { code: 'FIS', name: 'Fisika', type: 'Pilihan' },
  { code: 'SBD', name: 'Seni Budaya', type: 'Wajib' },
];

// Satu guru per mapel, urutan sama dengan SUBJECTS.
const TEACHERS = [
  { employeeNumber: '198001012005011001', name: 'Yohanes Prasetyo' },
  { employeeNumber: '198203152006042002', name: 'Maria Lestari' },
  { employeeNumber: '198507202009031003', name: 'Stefanus Wibowo' },
  { employeeNumber: '198811112010012004', name: 'Theresia Kurniawati' },
  { employeeNumber: '199002252014021005', name: 'Agustinus Hartono' },
];

// Komponen per mapel per semester (PO-01). Seni Budaya sengaja berbeda.
const DEFAULT_COMPONENTS = [
  { name: 'Tugas', weight: 20 },
  { name: 'Ulangan Harian', weight: 30 },
  { name: 'UTS', weight: 20 },
  { name: 'UAS', weight: 30 },
];
const COMPONENTS_BY_SUBJECT = {
  SBD: [
    { name: 'Praktik', weight: 60 },
    { name: 'Tugas', weight: 40 },
  ],
};

const CLASSES = [
  {
    name: 'X-1',
    gradeLevel: '10',
    room: 'Ruang X-1',
    homeroomIndex: 0,
    students: [
      { nis: '2026001', name: 'Angela Putri', birthDate: '2010-03-14' },
      { nis: '2026002', name: 'Benediktus Santoso', birthDate: '2010-06-02' },
      { nis: '2026003', name: 'Clara Wijaya', birthDate: '2010-09-21' },
    ],
  },
  {
    name: 'XI-1',
    gradeLevel: '11',
    room: 'Ruang XI-1',
    homeroomIndex: 1,
    students: [
      { nis: '2025001', name: 'Dominikus Halim', birthDate: '2009-01-30' },
      { nis: '2025002', name: 'Elisabeth Tanoto', birthDate: '2009-04-17' },
      { nis: '2025003', name: 'Fransiskus Gunawan', birthDate: '2009-11-05' },
    ],
  },
];

// Siswa NIS 2026002 dan 2025003 bersaudara, jadi berbagi satu wali murid (relasi M:N).
const GUARDIANS = [
  { name: 'Robertus Putra', phone: '081234567801', children: [['2026001', 'Ayah']] },
  { name: 'Lucia Santoso', phone: '081234567802', children: [['2026002', 'Ibu'], ['2025003', 'Ibu']] },
  { name: 'Hendra Wijaya', phone: '081234567803', children: [['2026003', 'Ayah']] },
  { name: 'Veronika Halim', phone: '081234567804', children: [['2025001', 'Ibu']] },
  { name: 'Paulus Tanoto', phone: '081234567805', children: [['2025002', 'Wali']] },
];

const DAYS = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'];
const PERIODS = [
  ['07:00', '08:30'],
  ['08:45', '10:15'],
  ['10:30', '12:00'],
];

async function seedDemo({ admin, academicYear, semester }) {
  const passwordHash = await bcrypt.hash(
    requirePassword('SEED_DEMO_PASSWORD', env.seed.demoPassword),
    BCRYPT_ROUNDS,
  );

  const subjects = [];
  for (const subject of SUBJECTS) {
    subjects.push(
      await prisma.subject.upsert({ where: { code: subject.code }, update: {}, create: subject }),
    );
  }

  const teachers = [];
  for (const [i, teacher] of TEACHERS.entries()) {
    const user = await upsertUser(`guru${i + 1}@${DEMO_EMAIL_DOMAIN}`, 'TEACHER', passwordHash);
    teachers.push(
      await prisma.teacher.upsert({
        where: { employeeNumber: teacher.employeeNumber },
        update: {},
        create: { ...teacher, userId: user.id },
      }),
    );
  }

  const studentsByNis = {};
  let studentNo = 0;
  for (const cls of CLASSES) {
    for (const student of cls.students) {
      studentNo += 1;
      const user = await upsertUser(`siswa${studentNo}@${DEMO_EMAIL_DOMAIN}`, 'STUDENT', passwordHash);
      studentsByNis[student.nis] = await prisma.student.upsert({
        where: { nis: student.nis },
        update: {},
        create: {
          nis: student.nis,
          name: student.name,
          birthDate: date(student.birthDate),
          userId: user.id,
        },
      });
    }
  }

  for (const [i, guardian] of GUARDIANS.entries()) {
    const user = await upsertUser(`wali${i + 1}@${DEMO_EMAIL_DOMAIN}`, 'GUARDIAN', passwordHash);
    const saved = await prisma.guardian.upsert({
      where: { userId: user.id },
      update: {},
      create: { userId: user.id, name: guardian.name, phone: guardian.phone },
    });
    for (const [nis, relationship] of guardian.children) {
      const studentId = studentsByNis[nis].id;
      await prisma.studentGuardian.upsert({
        where: { studentId_guardianId: { studentId, guardianId: saved.id } },
        update: {},
        create: { studentId, guardianId: saved.id, relationship },
      });
    }
  }

  for (const subject of subjects) {
    const components = COMPONENTS_BY_SUBJECT[subject.code] ?? DEFAULT_COMPONENTS;
    for (const component of components) {
      await prisma.gradeComponent.upsert({
        where: {
          subjectId_semesterId_name: { subjectId: subject.id, semesterId: semester.id, name: component.name },
        },
        update: {},
        create: { ...component, subjectId: subject.id, semesterId: semester.id },
      });
    }
  }

  const rooms = {};
  for (const cls of CLASSES) {
    rooms[cls.name] = await findOrCreate(prisma.facility, { name: cls.room }, { type: 'ROOM', quantity: 1 });
  }
  await findOrCreate(prisma.facility, { name: 'Laboratorium Biologi' }, { type: 'ROOM', quantity: 1 });
  await findOrCreate(prisma.facility, { name: 'Aula' }, { type: 'ROOM', quantity: 1 });
  await findOrCreate(prisma.facility, { name: 'Proyektor' }, { type: 'ITEM', quantity: 3 });

  for (const [classIndex, cls] of CLASSES.entries()) {
    const classGroup = await prisma.classGroup.upsert({
      where: { academicYearId_name: { academicYearId: academicYear.id, name: cls.name } },
      update: {},
      create: {
        academicYearId: academicYear.id,
        name: cls.name,
        gradeLevel: cls.gradeLevel,
        capacity: 36,
        homeroomTeacherId: teachers[cls.homeroomIndex].id,
      },
    });

    for (const student of cls.students) {
      const studentId = studentsByNis[student.nis].id;
      await prisma.classMember.upsert({
        where: { studentId_classGroupId: { studentId, classGroupId: classGroup.id } },
        update: {},
        create: { studentId, classGroupId: classGroup.id },
      });
    }

    const assignments = [];
    for (const [i, subject] of subjects.entries()) {
      const key = {
        teacherId: teachers[i].id,
        subjectId: subject.id,
        classGroupId: classGroup.id,
        semesterId: semester.id,
      };
      assignments.push(
        await prisma.teachingAssignment.upsert({
          where: { teacherId_subjectId_classGroupId_semesterId: key },
          update: {},
          create: key,
        }),
      );
    }

    // Urutan mapel kelas berikutnya digeser satu agar guru yang sama tidak bentrok di jam yang sama.
    for (const [d, dayOfWeek] of DAYS.entries()) {
      for (const [p, [start, end]] of PERIODS.entries()) {
        const assignment = assignments[(d * PERIODS.length + p + classIndex) % assignments.length];
        await findOrCreate(
          prisma.scheduleSlot,
          { teachingAssignmentId: assignment.id, dayOfWeek, startTime: time(start) },
          { roomId: rooms[cls.name].id, endTime: time(end) },
        );
      }
    }
  }

  const violationTypes = [
    { name: 'Terlambat', points: 5 },
    { name: 'Atribut tidak lengkap', points: 10 },
    { name: 'Membolos', points: 20 },
  ];
  for (const { name, points } of violationTypes) {
    await findOrCreate(prisma.violationType, { name }, { points });
  }

  const events = [
    { title: 'Penilaian Tengah Semester Ganjil', type: 'EXAM', startDate: '2026-09-21', endDate: '2026-09-26' },
    { title: 'Penilaian Akhir Semester Ganjil', type: 'EXAM', startDate: '2026-12-01', endDate: '2026-12-08' },
    { title: 'Class Meeting', type: 'ACTIVITY', startDate: '2026-12-14', endDate: '2026-12-17' },
    { title: 'Libur Natal dan Tahun Baru', type: 'HOLIDAY', startDate: '2026-12-21', endDate: '2027-01-02' },
  ];
  for (const event of events) {
    await findOrCreate(
      prisma.academicEvent,
      { title: event.title },
      { type: event.type, startDate: date(event.startDate), endDate: date(event.endDate) },
    );
  }

  await findOrCreate(
    prisma.announcement,
    { title: 'Selamat datang di SIM Akademik' },
    {
      authorId: admin.userId,
      scope: 'SCHOOL',
      content: 'Aplikasi SIM Akademik SMAK Santa Agnes kini dapat digunakan oleh siswa, guru, dan wali murid.',
      date: date('2026-07-13'),
    },
  );

  // Satu tagihan lunas dan satu tagihan lewat jatuh tempo per siswa, untuk contoh status "Menunggak".
  for (const student of Object.values(studentsByNis)) {
    const bills = [
      { billCode: 'SPP-2026-07', dueDate: '2026-07-10', status: 'PAID', paidDate: '2026-07-08' },
      { billCode: 'SPP-2026-08', dueDate: '2026-08-10', status: 'UNPAID', paidDate: null },
    ];
    for (const bill of bills) {
      await prisma.bill.upsert({
        where: { studentId_billCode: { studentId: student.id, billCode: bill.billCode } },
        update: {},
        create: {
          studentId: student.id,
          billCode: bill.billCode,
          paymentType: 'SPP',
          amount: '850000',
          dueDate: date(bill.dueDate),
          status: bill.status,
          paidDate: bill.paidDate ? date(bill.paidDate) : null,
        },
      });
    }
  }

  return { teachers: teachers.length, students: studentNo, guardians: GUARDIANS.length };
}

async function main() {
  const admin = await seedAdmin();
  const period = await seedPeriod();
  console.log(`Admin: ${env.seed.adminEmail}; periode aktif: ${period.academicYear.name} ganjil`);

  if (!env.seed.demoData) {
    console.log('Data demo dilewati (SEED_DEMO_DATA=false).');
    return;
  }
  const demo = await seedDemo({ admin, ...period });
  console.log(
    `Data demo: ${demo.teachers} guru, ${demo.students} siswa, ${demo.guardians} wali murid ` +
      `(email guruN/siswaN/waliN@${DEMO_EMAIL_DOMAIN}).`,
  );
}

main()
  .catch((err) => {
    console.error(err);
    process.exitCode = 1;
  })
  .finally(() => prisma.$disconnect());
