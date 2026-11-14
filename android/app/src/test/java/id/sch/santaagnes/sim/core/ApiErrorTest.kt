package id.sch.santaagnes.sim.core

import id.sch.santaagnes.sim.domain.model.ImportRowError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ApiErrorTest {

    @Test
    fun readsCodeAndMessageFromServerEnvelope() {
        val body = """{"success":false,"error":{"code":"VALIDATION_ERROR","message":"Email tidak valid."}}"""

        val error = parseApiError(body)

        assertEquals("VALIDATION_ERROR", error?.code)
        assertEquals("Email tidak valid.", error?.message)
    }

    @Test
    fun readsValidationDetails() {
        val body = """{"success":false,"error":{"code":"VALIDATION_ERROR","message":"Data yang dikirim tidak valid.",
            "details":[{"field":"body.title","message":"Judul terlalu panjang."},{"field":"body.dueDate","message":"Tenggat tidak valid.","extra":1}]}}"""

        val error = parseApiError(body)

        assertEquals(listOf("Judul terlalu panjang.", "Tenggat tidak valid."), error?.details?.map { it.message })
    }

    @Test
    fun readsImportRowAndColumnDetails() {
        val body = """{"success":false,"error":{"code":"IMPORT_INVALID","message":"Berkas tidak valid.",
            "details":[{"row":3,"column":"Hari","message":"Hari tidak dikenal."},{"row":5,"message":"Guru tidak ditemukan."}]}}"""

        val details = parseApiError(body)?.details.orEmpty()

        assertEquals(listOf<Int?>(3, 5), details.map { it.row })
        assertEquals(listOf<String?>("Hari", null), details.map { it.column })
    }

    @Test
    fun returnsNullForEmptyOrMissingBody() {
        assertNull(parseApiError(null))
        assertNull(parseApiError(""))
        assertNull(parseApiError("   "))
    }

    @Test
    fun returnsNullWhenBodyIsNotAnEnvelope() {
        assertNull(parseApiError("<html>502 Bad Gateway</html>"))
        assertNull(parseApiError("""{"success":true,"data":[]}"""))
        assertNull(parseApiError("\"teks biasa\""))
    }

    @Test
    fun importErrorsKeepServerOrderForImportInvalid() {
        val body = """{"success":false,"error":{"code":"IMPORT_INVALID","message":"Berkas tidak valid.",
            "details":[{"row":3,"column":"Hari","message":"Hari tidak dikenal."},{"row":5,"column":null,"message":"Guru tidak ditemukan."},
            {"row":null,"message":"Kolom NIS tidak ada."},{"row":6,"column":"","message":""}]}}"""

        val errors = importErrorsOf(parseApiError(body))

        // entri tanpa pesan dibuang; kolom kosong dianggap tidak ada
        assertEquals(
            listOf(
                ImportRowError(3, "Hari", "Hari tidak dikenal."),
                ImportRowError(5, null, "Guru tidak ditemukan."),
                ImportRowError(null, null, "Kolom NIS tidak ada.")
            ),
            errors
        )
    }

    @Test
    fun importErrorsAreEmptyForOtherCodesAndMissingBody() {
        val validation = """{"success":false,"error":{"code":"VALIDATION_ERROR","message":"x",
            "details":[{"row":3,"message":"Tidak valid."}]}}"""

        assertEquals(emptyList<ImportRowError>(), importErrorsOf(parseApiError(validation)))
        assertEquals(emptyList<ImportRowError>(), importErrorsOf(null))
    }
}
