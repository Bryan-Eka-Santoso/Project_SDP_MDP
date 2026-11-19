package id.sch.santaagnes.sim.data.repository

import id.sch.santaagnes.sim.data.remote.ElectiveAllocationApi
import id.sch.santaagnes.sim.data.remote.ElectiveApplyRequest
import id.sch.santaagnes.sim.data.remote.ElectiveAppliedDto
import id.sch.santaagnes.sim.data.remote.ElectivePreviewDto
import id.sch.santaagnes.sim.data.remote.ElectiveTrendsDto
import javax.inject.Inject

class ElectiveAllocationRepository @Inject constructor(private val api: ElectiveAllocationApi) {
    suspend fun trends(): ElectiveTrendsDto {
        val result = api.trends()
        if (!result.success) error(result.error?.message ?: "Gagal mengambil daftar paket pilihan.")
        return result.data ?: error("Data paket pilihan tidak tersedia.")
    }

    suspend fun preview(packageId: Int, gradeLevel: String): ElectivePreviewDto {
        val result = api.preview(packageId, gradeLevel)
        if (!result.success) error(result.error?.message ?: "Gagal menghitung pratinjau alokasi.")
        return result.data ?: error("Pratinjau tidak tersedia.")
    }

    suspend fun apply(preview: ElectivePreviewDto): ElectiveAppliedDto {
        val result = api.apply(ElectiveApplyRequest(preview.packageId, preview.gradeLevel, preview.signature))
        if (!result.success) error(result.error?.message ?: "Alokasi gagal dijalankan.")
        return result.data ?: error("Hasil alokasi tidak tersedia.")
    }
}
