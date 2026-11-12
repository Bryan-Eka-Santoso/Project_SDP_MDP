package id.sch.santaagnes.sim.data.local

import androidx.room.Entity

/** Tanda "selesai" siswa untuk tugas dari guru (F-JDW-09). Hanya di perangkat; server tidak tahu. */
@Entity(
    tableName = "task_done",
    primaryKeys = ["accountId", "taskId"]
)
data class TaskDoneEntity(
    val accountId: Int,
    val taskId: Int
)
