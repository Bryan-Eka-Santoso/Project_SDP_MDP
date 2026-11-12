package id.sch.santaagnes.sim.core

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
}
