package id.sch.santaagnes.sim.ui.admin

import id.sch.santaagnes.sim.ui.admin.report.ReportExportPreviewFormatter
import org.junit.Assert.assertEquals
import org.junit.Test

class ReportExportPreviewTest {

    @Test
    fun `pratinjau membatasi maksimal 8 baris dan menghitung sisa baris`() {
        val rows = (1..15).map { listOf("Siswa $it", "X-1", "90") }
        val visible = ReportExportPreviewFormatter.visibleRows(rows)

        assertEquals(8, visible.size)
        assertEquals(7, ReportExportPreviewFormatter.remainingCount(totalCount = 15, visibleCount = visible.size))
        assertEquals("Menampilkan 8 dari 15 baris", ReportExportPreviewFormatter.rowCountSummary(15, visible.size))
    }

    @Test
    fun `sisa baris tidak pernah negatif dan total kosong memberi pesan jelas`() {
        assertEquals(0, ReportExportPreviewFormatter.remainingCount(totalCount = 3, visibleCount = 5))
        assertEquals(
            "Belum ada baris data pada filter ini",
            ReportExportPreviewFormatter.rowCountSummary(totalCount = 0, visibleCount = 0)
        )
        assertEquals(
            "Menampilkan seluruh 4 baris",
            ReportExportPreviewFormatter.rowCountSummary(totalCount = 4, visibleCount = 4)
        )
    }

    @Test
    fun `ringkasan filter menggabungkan filter aktif dan memberi default saat kosong`() {
        assertEquals(
            "Semua data semester aktif",
            ReportExportPreviewFormatter.filterSummary(listOf("", "   "))
        )
        assertEquals(
            "Kelas: X-1 · Status: Lunas",
            ReportExportPreviewFormatter.filterSummary(listOf(" Kelas: X-1 ", "", "Status: Lunas"))
        )
    }
}
