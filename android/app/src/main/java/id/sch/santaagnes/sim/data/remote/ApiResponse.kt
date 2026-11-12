package id.sch.santaagnes.sim.data.remote

/** Amplop respons backend: `{success, data}` atau `{success:false, error:{code, message}}`. */
data class ApiResponse<T>(
    val success: Boolean,
    val data: T?,
    val error: ApiErrorBody? = null,
    // hanya ada pada daftar berpaginasi
    val meta: PageMetaDto? = null
)

data class PageMetaDto(
    val page: Int,
    val limit: Int,
    val total: Int,
    val totalPages: Int
)

data class ApiErrorBody(
    val code: String?,
    val message: String?,
    // Rincian validasi: tiap entri punya `message` bahasa Indonesia yang aman ditampilkan.
    val details: List<ApiErrorDetail>? = null
)

data class ApiErrorDetail(
    val field: String? = null,
    val message: String? = null,
    // Kesalahan impor Excel (IMPORT_INVALID): nomor baris dan kolom; kolom null untuk kesalahan seluruh baris
    val row: Int? = null,
    val column: String? = null
)
