package id.sch.santaagnes.sim.data.remote

import retrofit2.http.GET
import retrofit2.http.Path

data class SiswaBinaanDto(
    val id: Int,
    val nis: String,
    val name: String,
    val classId: Int,
    val className: String
)

data class KelasProfilSiswaDto(
    val id: Int,
    val name: String,
    val gradeLevel: String,
    val academicYear: String
)

data class WaliMuridProfilDto(
    val id: Int,
    val name: String,
    val relationship: String,
    val phone: String,
    val email: String
)

data class ProfilSiswaBinaanDto(
    val id: Int,
    val nis: String,
    val name: String,
    val email: String,
    val birthDate: String,
    val phone: String?,
    val status: String,
    val classes: List<KelasProfilSiswaDto>,
    val guardians: List<WaliMuridProfilDto>
)

interface ProfilSiswaBinaanApi {

    @GET("homeroom-profiles/students")
    suspend fun getSiswa():
            ApiResponse<List<SiswaBinaanDto>>

    @GET("homeroom-profiles/students/{studentId}")
    suspend fun getProfil(
        @Path("studentId") studentId: Int
    ): ApiResponse<ProfilSiswaBinaanDto>
}
