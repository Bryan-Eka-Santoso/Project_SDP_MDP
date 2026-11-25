
package id.sch.santaagnes.sim.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

data class MapelDalamPaketDto(
    val id: Int,
    val code: String,
    val name: String
)

data class PaketPilihanSiswaDto(
    val id: Int,
    val name: String,
    val capacity: Int,
    val selectionCount: Int,
    val remainingCapacity: Int,
    val isSelected: Boolean,
    val subjects: List<MapelDalamPaketDto>
)

data class DaftarPaketSiswaDto(
    val academicYearName: String,
    val selectedPackageId: Int?,
    val selectedDate: String?,
    val packages: List<PaketPilihanSiswaDto>
)

data class PilihPaketRequest(
    val packageId: Int
)

data class HasilPilihPaketDto(
    val packageId: Int,
    val packageName: String,
    val selectedDate: String,
    val alreadySelected: Boolean,
    val message: String
)

interface PilihPaketApi {

    @GET("student-subject-packages")
    suspend fun getDaftarPaket():
            ApiResponse<DaftarPaketSiswaDto>

    @POST("student-subject-packages")
    suspend fun pilihPaket(
        @Body request: PilihPaketRequest
    ): ApiResponse<HasilPilihPaketDto>
}
