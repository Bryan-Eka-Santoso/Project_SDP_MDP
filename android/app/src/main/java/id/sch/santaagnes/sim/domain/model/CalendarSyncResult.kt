package id.sch.santaagnes.sim.domain.model

/** Akun/kalender perangkat yang dapat ditulis (memprioritaskan Google Calendar). */
data class DeviceCalendarAccount(
    val id: Long,
    val displayName: String,
    val accountName: String,
    val accountType: String,
    val isPrimary: Boolean
) {
    val isGoogle: Boolean get() = accountType == GOOGLE_ACCOUNT_TYPE

    /** Label ringkas untuk UI: email Google bila akun Google, atau nama tampilan kalender. */
    val shortLabel: String
        get() = when {
            isGoogle && accountName.isNotBlank() -> accountName
            displayName.isNotBlank() -> displayName
            accountName.isNotBlank() -> accountName
            else -> "Kalender #$id"
        }

    companion object {
        const val GOOGLE_ACCOUNT_TYPE = "com.google"
    }
}

/** Ringkasan satu kali sinkron ke kalender perangkat / Google Calendar (F-JDW-06). */
data class CalendarSyncResult(
    val added: Int,
    val updated: Int,
    val removed: Int,
    // Jumlah agenda dari aplikasi yang kini ada di kalender perangkat.
    val total: Int,
    val accountLabel: String? = null
)
