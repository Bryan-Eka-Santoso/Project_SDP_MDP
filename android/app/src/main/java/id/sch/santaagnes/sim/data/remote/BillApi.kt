package id.sch.santaagnes.sim.data.remote

import okhttp3.MultipartBody
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/** Siswa pemilik tagihan; hanya ada pada daftar lintas siswa (`GET /bills`). */
data class BillStudentDto(
    val id: Int,
    val nis: String?,
    val name: String,
    val classGroups: List<IdNameDto>? = null
)

/** Tagihan dari rekap yayasan (F-BYR-02). `status` `PAID`/`UNPAID`; tanggal `YYYY-MM-DD`. */
data class BillDto(
    val id: Int,
    val billCode: String,
    val paymentType: String,
    val amount: Long,
    val dueDate: String,
    val status: String,
    val paidDate: String?,
    val isOverdue: Boolean,
    val student: BillStudentDto? = null
)

/** Hasil impor rekap pembayaran (F-BYR-01). */
data class BillImportDto(
    val created: Int,
    val updated: Int,
    val unchanged: Int,
    val notifiedStudents: Int
)

interface BillApi {

    // Siswa sendiri, wali murid untuk anaknya, wali kelas untuk siswa kelasnya, admin untuk semua.
    @GET("students/{studentId}/bills")
    suspend fun getStudentBills(
        @Path("studentId") studentId: Int,
        @Query("page") page: Int,
        @Query("limit") limit: Int,
        @Query("status") status: String?,
        @Query("overdue") overdue: Boolean?
    ): ApiResponse<List<BillDto>>

    // Admin (semua) dan wali kelas (otomatis dibatasi ke kelas binaannya).
    @GET("bills")
    suspend fun getBills(
        @Query("page") page: Int,
        @Query("limit") limit: Int,
        @Query("status") status: String?,
        @Query("overdue") overdue: Boolean?,
        @Query("classGroupId") classGroupId: Int?,
        @Query("q") query: String?
    ): ApiResponse<List<BillDto>>

    @Multipart
    @POST("bills/import")
    suspend fun importBills(@Part file: MultipartBody.Part): ApiResponse<BillImportDto>
}
