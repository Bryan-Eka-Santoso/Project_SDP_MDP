/**
 * Sinkronisasi Teacher.isHomeroomTeacher (ADR-004 & docs/BUSINESS_RULES.md bagian 1).
 *
 * Guru yang menjadi homeroomTeacherId di ClassGroup pada tahun ajaran aktif ditandai
 * isHomeroomTeacher = true. Guru lainnya di-set false.
 * Fungsi ini dijalankan di dalam transaksi (tx).
 */
async function syncHomeroomTeachers(tx, activeAcademicYearId = null) {
  // 1. Reset semua guru menjadi false
  await tx.teacher.updateMany({
    data: { isHomeroomTeacher: false },
  });

  // 2. Ambil tahun ajaran aktif jika tidak diberikan
  let yearId = activeAcademicYearId;
  if (!yearId) {
    const activeYear = await tx.academicYear.findFirst({
      where: { isActive: true },
      select: { id: true },
    });
    if (!activeYear) return;
    yearId = activeYear.id;
  }

  // 3. Cari seluruh id_wali_kelas pada tahun ajaran aktif tersebut
  const classes = await tx.classGroup.findMany({
    where: {
      academicYearId: yearId,
      homeroomTeacherId: { not: null },
    },
    select: { homeroomTeacherId: true },
  });

  const teacherIds = Array.from(
    new Set(classes.map((c) => c.homeroomTeacherId).filter(Boolean)),
  );

  // 4. Update guru yang bertugas sebagai wali kelas menjadi true
  if (teacherIds.length > 0) {
    await tx.teacher.updateMany({
      where: { id: { in: teacherIds } },
      data: { isHomeroomTeacher: true },
    });
  }
}

module.exports = { syncHomeroomTeachers };
