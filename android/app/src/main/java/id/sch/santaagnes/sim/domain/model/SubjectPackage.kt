package id.sch.santaagnes.sim.domain.model

/** Ringkasan mata pelajaran di dalam paket pilihan (F-MPP-03). */
data class PackageSubject(
    val id: Int,
    val code: String,
    val name: String,
    val type: String
)

/** Master paket mata pelajaran pilihan per tahun ajaran (F-MPP-03). */
data class SubjectPackage(
    val id: Int,
    val academicYearId: Int,
    val academicYearName: String,
    val isAcademicYearActive: Boolean,
    val name: String,
    val capacity: Int,
    val subjects: List<PackageSubject>,
    val selectionCount: Int = 0
)
