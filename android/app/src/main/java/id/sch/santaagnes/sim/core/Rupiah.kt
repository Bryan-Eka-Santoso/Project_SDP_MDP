package id.sch.santaagnes.sim.core

/** Nominal rupiah untuk tampilan, tanpa bergantung pada locale perangkat: 850000 → "IDR 850.000" (awalan IDR sesuai permintaan dosen). */
object Rupiah {

    fun format(amount: Long): String {
        val sign = if (amount < 0) "-" else ""
        val digits = Math.abs(amount).toString()
        val grouped = digits.reversed().chunked(3).joinToString(".").reversed()
        return "${sign}IDR $grouped"
    }
}
