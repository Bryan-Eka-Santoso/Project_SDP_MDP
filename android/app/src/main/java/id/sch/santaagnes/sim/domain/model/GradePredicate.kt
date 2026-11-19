package id.sch.santaagnes.sim.domain.model

/** Satu baris skala predikat nilai pada suatu tahun ajaran (F-NIL-06). */
data class GradePredicateItem(
    val id: Int = 0,
    val label: String,
    val minScore: Double,
    val description: String? = null
)

/** Skala predikat nilai lengkap milik satu tahun ajaran. */
data class GradePredicateScale(
    val academicYearId: Int,
    val academicYearName: String,
    val predicates: List<GradePredicateItem>
)
