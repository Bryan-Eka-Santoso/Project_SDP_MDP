package id.sch.santaagnes.sim.domain.model

/** Jenis agenda pada kalender akademik (F-KAL-01, F-KAL-02). */
enum class AcademicEventType(val wire: String, val label: String) {
    EXAM("EXAM", "Ujian"),
    HOLIDAY("HOLIDAY", "Libur"),
    ACTIVITY("ACTIVITY", "Kegiatan");

    companion object {
        fun fromWire(value: String): AcademicEventType =
            entries.firstOrNull { it.wire == value } ?: ACTIVITY
    }
}

/** Satu agenda pada kalender akademik sekolah. */
data class AcademicEvent(
    val id: Int,
    val title: String,
    val type: AcademicEventType,
    val startDate: String,
    val endDate: String,
    val description: String? = null
)
