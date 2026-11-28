package id.sch.santaagnes.sim.ui.attendance

/** Tanggal awal form guru pengganti: penggantian terakhir yang sudah tiba, atau yang paling dekat. */
fun substituteDefaultDate(dates: List<String>, today: String): String? =
    dates.filter { it <= today }.maxOrNull() ?: dates.minOrNull()
