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

async function upsertUser(email, role, name, passwordHash) {
  return prisma.user.upsert({
    where: { email },
    update: {},
    create: { email, role, name, passwordHash },
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
  return upsertUser(env.seed.adminEmail, 'ADMIN', 'Administrator', passwordHash);
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

  // Skala predikat bawaan; admin bisa mengubahnya per tahun ajaran (F-NIL-06).
  const predicates = [
    { label: 'A', minScore: '90', description: 'Sangat Baik' },
    { label: 'B', minScore: '80', description: 'Baik' },
    { label: 'C', minScore: '70', description: 'Cukup' },
    { label: 'D', minScore: '0', description: 'Perlu Bimbingan' },
  ];
  for (const predicate of predicates) {
    await prisma.gradePredicate.upsert({
      where: { academicYearId_label: { academicYearId: academicYear.id, label: predicate.label } },
      update: {},
      create: { ...predicate, academicYearId: academicYear.id },
    });
  }

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

// Komponen per penugasan guru (ADR-018); di data demo semua kelas memakai set yang sama
// per mapel. Seni Budaya sengaja berbeda.
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
      { nis: '2026001', name: 'Angela Putri', birthDate: '2010-03-14', phone: '085712340001' },
      { nis: '2026002', name: 'Benediktus Santoso', birthDate: '2010-06-02', phone: '085712340002' },
      { nis: '2026003', name: 'Clara Wijaya', birthDate: '2010-09-21', phone: null },
    ],
  },
  {
    name: 'XI-1',
    gradeLevel: '11',
    room: 'Ruang XI-1',
    homeroomIndex: 1,
    students: [
      { nis: '2025001', name: 'Dominikus Halim', birthDate: '2009-01-30', phone: '085712340004' },
      { nis: '2025002', name: 'Elisabeth Tanoto', birthDate: '2009-04-17', phone: '085712340005' },
      { nis: '2025003', name: 'Fransiskus Gunawan', birthDate: '2009-11-05', phone: null },
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
    const user = await upsertUser(`guru${i + 1}@${DEMO_EMAIL_DOMAIN}`, 'TEACHER', teacher.name, passwordHash);
    teachers.push(
      await prisma.teacher.upsert({
        where: { employeeNumber: teacher.employeeNumber },
        update: {},
        create: { employeeNumber: teacher.employeeNumber, userId: user.id },
      }),
    );
  }

  const studentsByNis = {};
  let studentNo = 0;
  for (const cls of CLASSES) {
    for (const student of cls.students) {
      studentNo += 1;
      const user = await upsertUser(
        `siswa${studentNo}@${DEMO_EMAIL_DOMAIN}`,
        'STUDENT',
        student.name,
        passwordHash,
      );
      studentsByNis[student.nis] = await prisma.student.upsert({
        where: { nis: student.nis },
        update: {},
        create: {
          nis: student.nis,
          birthDate: date(student.birthDate),
          phone: student.phone,
          userId: user.id,
        },
      });
    }
  }

  const savedGuardians = [];
  for (const [i, guardian] of GUARDIANS.entries()) {
    const user = await upsertUser(`wali${i + 1}@${DEMO_EMAIL_DOMAIN}`, 'GUARDIAN', guardian.name, passwordHash);
    const saved = await prisma.guardian.upsert({
      where: { userId: user.id },
      update: {},
      create: { userId: user.id, phone: guardian.phone },
    });
    savedGuardians.push(saved);
    for (const [nis, relationship] of guardian.children) {
      const studentId = studentsByNis[nis].id;
      await prisma.studentGuardian.upsert({
        where: { studentId_guardianId: { studentId, guardianId: saved.id } },
        update: {},
        create: { studentId, guardianId: saved.id, relationship },
      });
    }
  }

  const rooms = {};
  for (const cls of CLASSES) {
    rooms[cls.name] = await findOrCreate(prisma.facility, { name: cls.room }, { type: 'ROOM', quantity: 1 });
  }
  const facilities = {
    lab: await findOrCreate(prisma.facility, { name: 'Laboratorium Biologi' }, { type: 'ROOM', quantity: 1 }),
    hall: await findOrCreate(prisma.facility, { name: 'Aula' }, { type: 'ROOM', quantity: 1 }),
    projector: await findOrCreate(prisma.facility, { name: 'Proyektor' }, { type: 'ITEM', quantity: 3 }),
  };

  const classData = {};
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
      const assignment = await prisma.teachingAssignment.upsert({
        where: { teacherId_subjectId_classGroupId_semesterId: key },
        update: {},
        create: key,
      });
      assignments.push(assignment);

      const components = COMPONENTS_BY_SUBJECT[subject.code] ?? DEFAULT_COMPONENTS;
      for (const component of components) {
        await prisma.gradeComponent.upsert({
          where: {
            teachingAssignmentId_name: { teachingAssignmentId: assignment.id, name: component.name },
          },
          update: {},
          create: { ...component, teachingAssignmentId: assignment.id },
        });
      }
    }

    // Contoh tugas dari guru (F-JDW-08) untuk dua mapel pertama kelas ini.
    for (const [i, title] of ['Kerjakan LKS bab 3', 'Rangkum materi pertemuan 5'].entries()) {
      await findOrCreate(
        prisma.task,
        { teachingAssignmentId: assignments[i].id, title },
        { description: 'Dikerjakan di buku tulis.', dueDate: date(`2026-11-${20 + i}`) },
      );
    }

    // Urutan mapel kelas berikutnya digeser satu agar guru yang sama tidak bentrok di jam yang sama.
    classData[cls.name] = { classGroup, assignments, slots: {} };
    for (const [d, dayOfWeek] of DAYS.entries()) {
      for (const [p, [start, end]] of PERIODS.entries()) {
        const assignment = assignments[(d * PERIODS.length + p + classIndex) % assignments.length];
        classData[cls.name].slots[`${dayOfWeek}-${start}`] = await findOrCreate(
          prisma.scheduleSlot,
          { teachingAssignmentId: assignment.id, dayOfWeek, startTime: time(start) },
          { roomId: rooms[cls.name].id, endTime: time(end) },
        );
      }
    }
  }

  // Aturan yang sama dengan backend: is_wali_kelas = tercantum sebagai wali kelas di tahun ajaran aktif.
  await prisma.$transaction([
    prisma.teacher.updateMany({ data: { isHomeroomTeacher: false } }),
    prisma.teacher.updateMany({
      where: { homeroomClasses: { some: { academicYear: { isActive: true } } } },
      data: { isHomeroomTeacher: true },
    }),
  ]);

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
    { title: 'Ulang Tahun Sekolah', type: 'ACTIVITY', startDate: '2026-11-27', endDate: '2026-11-27' },
    { title: 'Retret Siswa Kelas X', type: 'ACTIVITY', startDate: '2026-11-26', endDate: '2026-11-27' },
    { title: 'Pembagian Rapor Semester Ganjil', type: 'ACTIVITY', startDate: '2026-12-19', endDate: '2026-12-19' },
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
      authorId: admin.id,
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
      { billCode: 'SPP-2026-09', dueDate: '2026-09-10', status: 'PAID', paidDate: '2026-09-09' },
      { billCode: 'SPP-2026-11', dueDate: '2026-11-30', status: 'UNPAID', paidDate: null },
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

  await seedActivity({
    admin,
    academicYear,
    teachers,
    subjects,
    studentsByNis,
    savedGuardians,
    classData,
    facilities,
  });

  return { teachers: teachers.length, students: studentNo, guardians: GUARDIANS.length };
}

// ---------------------------------------------------------------------
// Data aktivitas demo: nilai, presensi, pelanggaran, peminjaman, dst.
// Tanggal disusun di sekitar 19 Nov 2026 (hari demo) dan aman dijalankan berulang.
// ---------------------------------------------------------------------

const WEEKDAY_NAMES = ['SUNDAY', 'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY'];
const weekdayOf = (iso) => WEEKDAY_NAMES[new Date(`${iso}T00:00:00.000Z`).getUTCDay()];

// Hari sekolah sebelum hari demo; dipakai untuk jurnal dan presensi.
const PAST_SCHOOL_DAYS = [
  '2026-11-09', '2026-11-10', '2026-11-11', '2026-11-12', '2026-11-13',
  '2026-11-16', '2026-11-17', '2026-11-18',
];

async function seedActivity(ctx) {
  const { admin, academicYear, teachers, subjects, studentsByNis, savedGuardians, classData, facilities } = ctx;
  const studentsOf = (cls) => cls.students.map((s) => studentsByNis[s.nis]);

  // Jurnal dan presensi: sebagian besar hadir; satu sakit, satu alpa, satu izin untuk contoh rekap.
  for (const cls of CLASSES) {
    const students = studentsOf(cls);
    let meeting = 0;
    for (const iso of PAST_SCHOOL_DAYS) {
      for (const [start] of PERIODS) {
        const slot = classData[cls.name].slots[`${weekdayOf(iso)}-${start}`];
        if (!slot) continue;
        meeting += 1;
        const session = await findOrCreate(
          prisma.lessonSession,
          { scheduleSlotId: slot.id, date: date(iso) },
          { journal: `Pembahasan materi pertemuan ke-${meeting}` },
        );
        for (const [si, student] of students.entries()) {
          let status = 'PRESENT';
          if (si === 1 && iso === '2026-11-11') status = 'SICK';
          if (si === 2 && iso === '2026-11-12') status = 'ABSENT';
          if (si === 0 && iso === '2026-11-16') status = 'EXCUSED';
          await prisma.attendance.upsert({
            where: { sessionId_studentId: { sessionId: session.id, studentId: student.id } },
            update: {},
            create: { sessionId: session.id, studentId: student.id, status },
          });
        }
      }
    }
  }

  // Nilai per komponen. Siswa ketiga sengaja tanpa komponen terakhir (contoh "nilai belum lengkap"),
  // dan komponen UAS belum diisi siapa pun karena ujiannya baru Desember.
  // Komponen "Tugas" Matematika memakai rincian per penilaian; nilai komponennya adalah rata-rata.
  for (const [classIndex, cls] of CLASSES.entries()) {
    const students = studentsOf(cls);
    for (const [ai, assignment] of classData[cls.name].assignments.entries()) {
      const components = await prisma.gradeComponent.findMany({
        where: { teachingAssignmentId: assignment.id },
        orderBy: { id: 'asc' },
      });
      for (const [ci, component] of components.entries()) {
        if (component.name === 'UAS') continue;
        for (const [si, student] of students.entries()) {
          if (si === 2 && ci === components.length - 1) continue;
          const base = 70 + ((si * 7 + ci * 5 + ai * 3 + classIndex * 2) % 28);
          let value = base;
          if (ai === 0 && component.name === 'Tugas') {
            const parts = [base - 4, base + 6];
            for (const [n, part] of parts.entries()) {
              const assessment = await findOrCreate(
                prisma.gradeAssessment,
                { gradeComponentId: component.id, sequence: n + 1 },
                { teachingAssignmentId: assignment.id, title: `Tugas ${n + 1}` },
              );
              await prisma.gradeAssessmentScore.upsert({
                where: { assessmentId_studentId: { assessmentId: assessment.id, studentId: student.id } },
                update: {},
                create: { assessmentId: assessment.id, studentId: student.id, value: part },
              });
            }
            value = parts.reduce((a, b) => a + b, 0) / parts.length;
          }
          await prisma.score.upsert({
            where: { studentId_gradeComponentId: { studentId: student.id, gradeComponentId: component.id } },
            update: {},
            create: {
              studentId: student.id,
              teachingAssignmentId: assignment.id,
              gradeComponentId: component.id,
              value,
              inputDate: date('2026-11-13'),
            },
          });
        }
      }
    }
  }

  // Tugas bertenggat jam: satu mepet (merah), satu masih lama (biru), satu sudah lewat.
  const taskSamples = [
    { cls: 'X-1', subject: 0, title: 'Latihan soal bab 4', due: '2026-11-20', at: '08:00' },
    { cls: 'X-1', subject: 1, title: 'Esai Hari Pahlawan', due: '2026-11-27', at: '15:00' },
    { cls: 'X-1', subject: 2, title: 'Laporan praktikum sel', due: '2026-11-17', at: '16:00' },
    { cls: 'XI-1', subject: 0, title: 'Latihan soal bab 6', due: '2026-11-21', at: '10:00' },
    { cls: 'XI-1', subject: 3, title: 'Rangkuman hukum Newton', due: '2026-11-26', at: '12:00' },
  ];
  for (const t of taskSamples) {
    await findOrCreate(
      prisma.task,
      { teachingAssignmentId: classData[t.cls].assignments[t.subject].id, title: t.title },
      { description: 'Kumpulkan sesuai tenggat.', dueDate: date(t.due), dueTime: time(t.at) },
    );
  }

  // Pelanggaran, buku penghubung, dan catatan perkembangan.
  const violationType = (name) => prisma.violationType.findFirst({ where: { name } });
  const late = await violationType('Terlambat');
  const attire = await violationType('Atribut tidak lengkap');
  const skip = await violationType('Membolos');
  const violations = [
    { nis: '2026001', type: late, on: '2026-11-10', by: teachers[0], note: 'Terlambat 15 menit.' },
    { nis: '2026002', type: attire, on: '2026-11-12', by: teachers[0], note: 'Tidak memakai dasi.' },
    { nis: '2025001', type: skip, on: '2026-11-05', by: teachers[1], note: 'Tidak masuk tanpa keterangan.' },
    { nis: '2026001', type: late, on: '2026-11-17', by: teachers[1], note: 'Terlambat 10 menit.' },
  ];
  for (const v of violations) {
    await findOrCreate(
      prisma.violation,
      { studentId: studentsByNis[v.nis].id, violationTypeId: v.type.id, date: date(v.on) },
      { reporterId: v.by.id, points: v.type.points, description: v.note },
    );
  }

  const liaison = [
    { nis: '2026001', on: '2026-11-11', by: teachers[0], text: 'Angela aktif di kelas, mohon dukung kebiasaan belajar malam di rumah.' },
    { nis: '2026003', on: '2026-11-13', by: teachers[0], text: 'Clara perlu didampingi mengerjakan tugas Biologi.' },
    { nis: '2025001', on: '2026-11-12', by: teachers[1], text: 'Mohon konfirmasi alasan ketidakhadiran Dominikus tanggal 5 November.' },
  ];
  for (const n of liaison) {
    await findOrCreate(
      prisma.liaisonNote,
      { studentId: studentsByNis[n.nis].id, date: date(n.on) },
      { homeroomTeacherId: n.by.id, content: n.text },
    );
  }

  const progress = [
    { nis: '2026001', cls: 'X-1', subject: 0, on: '2026-11-13', text: 'Cepat memahami aljabar, bisa diberi soal tantangan.' },
    { nis: '2026002', cls: 'X-1', subject: 1, on: '2026-11-13', text: 'Perlu latihan menulis paragraf argumentatif.' },
    { nis: '2025002', cls: 'XI-1', subject: 3, on: '2026-11-12', text: 'Aktif bertanya saat praktikum fisika.' },
  ];
  for (const n of progress) {
    await findOrCreate(
      prisma.progressNote,
      {
        studentId: studentsByNis[n.nis].id,
        teachingAssignmentId: classData[n.cls].assignments[n.subject].id,
        date: date(n.on),
      },
      { content: n.text },
    );
  }

  // Pengajuan izin: satu disetujui (cocok dengan presensi sakit 11 Nov), satu menunggu, satu ditolak.
  const leaves = [
    { nis: '2026002', guardian: 1, from: '2026-11-11', to: '2026-11-11', reason: 'Demam, istirahat sesuai anjuran dokter.', status: 'APPROVED', by: teachers[0] },
    { nis: '2026003', guardian: 2, from: '2026-11-23', to: '2026-11-24', reason: 'Mengikuti lomba paduan suara tingkat kota.', status: 'PENDING', by: null },
    { nis: '2025003', guardian: 1, from: '2026-11-18', to: '2026-11-18', reason: 'Acara keluarga.', status: 'REJECTED', by: teachers[1] },
  ];
  for (const l of leaves) {
    await findOrCreate(
      prisma.leaveRequest,
      { studentId: studentsByNis[l.nis].id, startDate: date(l.from) },
      {
        guardianId: savedGuardians[l.guardian].id,
        endDate: date(l.to),
        reason: l.reason,
        status: l.status,
        verifierId: l.by ? l.by.id : null,
      },
    );
  }

  // Peminjaman fasilitas dalam semua status; yang APPROVED ikut tersinkron ke kalender HP (F-JDW-06).
  const bookings = [
    { by: teachers[0].userId, facility: facilities.hall, on: '2026-11-24', from: '10:00', to: '12:00', purpose: 'Rapat panitia class meeting', status: 'APPROVED' },
    { by: studentsByNis['2026001'].userId, facility: facilities.lab, on: '2026-11-26', from: '13:00', to: '14:30', purpose: 'Praktikum kelompok Biologi', status: 'APPROVED' },
    { by: studentsByNis['2026002'].userId, facility: facilities.projector, on: '2026-11-27', from: '09:00', to: '11:00', purpose: 'Presentasi proyek kelompok', status: 'PENDING' },
    { by: teachers[1].userId, facility: facilities.hall, on: '2026-11-24', from: '11:00', to: '13:00', purpose: 'Latihan paduan suara', status: 'REJECTED' },
    { by: teachers[2].userId, facility: facilities.projector, on: '2026-11-12', from: '07:00', to: '09:00', purpose: 'Pemutaran film dokumenter', status: 'RETURNED', returned: '2026-11-12' },
  ];
  for (const b of bookings) {
    await findOrCreate(
      prisma.facilityBooking,
      { facilityId: b.facility.id, requesterId: b.by, bookingDate: date(b.on), startTime: time(b.from) },
      {
        endTime: time(b.to),
        purpose: b.purpose,
        status: b.status,
        verifierId: b.status === 'PENDING' ? null : admin.id,
        returnDate: b.returned ? date(b.returned) : null,
      },
    );
  }

  // Pengumuman: satu untuk seluruh sekolah dan satu khusus kelas X-1.
  await findOrCreate(
    prisma.announcement,
    { title: 'Jadwal Penilaian Akhir Semester' },
    {
      authorId: admin.id,
      scope: 'SCHOOL',
      content: 'Penilaian Akhir Semester Ganjil berlangsung 1 sampai 8 Desember 2026. Siswa wajib hadir 15 menit sebelum ujian.',
      date: date('2026-11-17'),
    },
  );
  await findOrCreate(
    prisma.announcement,
    { title: 'Pengumpulan tugas kelompok X-1' },
    {
      authorId: admin.id,
      scope: 'CLASS',
      classGroupId: classData['X-1'].classGroup.id,
      content: 'Tugas kelompok dikumpulkan paling lambat Jumat, 20 November 2026, kepada wali kelas.',
      date: date('2026-11-18'),
    },
  );

  // Perubahan jadwal minggu depan: guru pengganti, kelas dibatalkan, dan pindah jam/ruang.
  const changes = [
    {
      slot: classData['X-1'].slots['MONDAY-07:00'],
      on: '2026-11-23',
      type: 'SUBSTITUTE',
      note: 'Guru berhalangan, digantikan oleh guru lain.',
      extra: { substituteTeacherId: teachers[2].id },
    },
    {
      slot: classData['X-1'].slots['TUESDAY-07:00'],
      on: '2026-11-24',
      type: 'CANCEL',
      note: 'Guru mengikuti pelatihan, kelas ditiadakan.',
      extra: {},
    },
    {
      slot: classData['XI-1'].slots['WEDNESDAY-08:45'],
      on: '2026-11-25',
      type: 'RESCHEDULE',
      note: 'Dipindah ke Laboratorium Biologi untuk praktikum.',
      extra: { newRoomId: facilities.lab.id, newStartTime: time('13:00'), newEndTime: time('14:30') },
    },
  ];
  for (const c of changes) {
    await findOrCreate(prisma.scheduleChange, { scheduleSlotId: c.slot.id, date: date(c.on) }, {
      type: c.type,
      description: c.note,
      ...c.extra,
    });
  }

  // Mapel pilihan: dua paket dan satu survei yang sedang dibuka untuk kelas X.
  const subjectByCode = Object.fromEntries(subjects.map((s) => [s.code, s]));
  const packages = [
    { name: 'Paket Saintek (Biologi dan Fisika)', capacity: 30, codes: ['BIO', 'FIS'] },
    { name: 'Paket Seni (Biologi dan Seni Budaya)', capacity: 20, codes: ['BIO', 'SBD'] },
  ];
  for (const p of packages) {
    const saved = await findOrCreate(
      prisma.subjectPackage,
      { academicYearId: academicYear.id, name: p.name },
      { capacity: p.capacity },
    );
    for (const code of p.codes) {
      await prisma.subjectPackageItem.upsert({
        where: { packageId_subjectId: { packageId: saved.id, subjectId: subjectByCode[code].id } },
        update: {},
        create: { packageId: saved.id, subjectId: subjectByCode[code].id },
      });
    }
  }
  const survey = await findOrCreate(
    prisma.electiveSurvey,
    { academicYearId: academicYear.id, title: 'Survei Peminatan Kelas XI' },
    { gradeLevel: '10', openDate: date('2026-11-10'), closeDate: date('2026-11-30'), status: 'PUBLISHED' },
  );
  const responses = [
    ['2026001', 'BIO'], ['2026001', 'FIS'], ['2026002', 'BIO'],
  ];
  for (const [nis, code] of responses) {
    const key = { surveyId: survey.id, studentId: studentsByNis[nis].id, subjectId: subjectByCode[code].id };
    await prisma.electiveSurveyResponse.upsert({
      where: { surveyId_studentId_subjectId: key },
      update: {},
      create: { ...key, filledDate: date('2026-11-12') },
    });
  }

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
