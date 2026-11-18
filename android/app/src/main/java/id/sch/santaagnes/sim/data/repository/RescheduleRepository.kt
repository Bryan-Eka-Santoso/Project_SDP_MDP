package id.sch.santaagnes.sim.data.repository

import id.sch.santaagnes.sim.core.ApiErrorMapper
import id.sch.santaagnes.sim.core.Resource
import id.sch.santaagnes.sim.data.remote.RescheduleRequest
import id.sch.santaagnes.sim.data.remote.ScheduleApi
import id.sch.santaagnes.sim.domain.model.NamedId
import id.sch.santaagnes.sim.domain.model.RescheduleInput
import retrofit2.HttpException
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Guru memindah atau membatalkan pertemuan dari slot mengajarnya sendiri (F-JDW-04), hanya online.
 * Backend tetap memeriksa kepemilikan slot, bentrok, dan tanggal.
 */
interface RescheduleRepository {

    /** Pilihan ruangan baru. */
    suspend fun rooms(): Resource<List<NamedId>>

    /** Simpan pemindahan; mengirim ulang untuk pertemuan yang sama berarti mengubahnya. */
    suspend fun reschedule(input: RescheduleInput): Resource<Unit>

    /** Batalkan kelas pada satu pertemuan (jenis CANCEL); siswa dan wali murid diberi tahu. */
    suspend fun cancelClass(slotId: Int, date: LocalDate, description: String): Resource<Unit>

    /** Hapus perubahan (pemindahan atau pembatalan kelas): pertemuan kembali ke jadwal aslinya. */
    suspend fun cancel(changeId: Int): Resource<Unit>
}

@Singleton
class RescheduleRepositoryImpl @Inject constructor(
    private val api: ScheduleApi,
    private val errors: ApiErrorMapper
) : RescheduleRepository {

    override suspend fun rooms(): Resource<List<NamedId>> = errors.call {
        api.getRooms().data.orEmpty().map { NamedId(it.id, it.name) }
    }

    override suspend fun reschedule(input: RescheduleInput): Resource<Unit> = errors.call {
        api.reschedule(input.toRequest())
        Unit
    }

    override suspend fun cancelClass(slotId: Int, date: LocalDate, description: String): Resource<Unit> = errors.call {
        api.reschedule(
            RescheduleRequest(
                scheduleSlotId = slotId,
                date = date.toString(),
                description = description.trim(),
                type = "CANCEL"
            )
        )
        Unit
    }

    override suspend fun cancel(changeId: Int): Resource<Unit> = errors.call {
        val response = api.deleteChange(changeId)
        // 404: sudah dibatalkan dari perangkat lain; hasil akhirnya sama.
        if (!response.isSuccessful && response.code() != 404) throw HttpException(response)
    }
}

fun RescheduleInput.toRequest() = RescheduleRequest(
    scheduleSlotId = slotId,
    date = date.toString(),
    description = description.trim(),
    newDate = newDate?.takeIf { it != date }?.toString(),
    newStartTime = newStartTime?.toString()?.take(5),
    newEndTime = newEndTime?.toString()?.take(5),
    newRoomId = newRoomId
)
