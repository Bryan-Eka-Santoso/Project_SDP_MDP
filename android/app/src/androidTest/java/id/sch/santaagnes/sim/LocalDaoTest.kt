package id.sch.santaagnes.sim

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.sch.santaagnes.sim.data.local.AppDatabase
import id.sch.santaagnes.sim.data.local.ScheduleEntity
import id.sch.santaagnes.sim.data.local.TaskDoneEntity
import id.sch.santaagnes.sim.data.local.TodoItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalDaoTest {

    private lateinit var database: AppDatabase

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).build()
    }

    @After
    fun teardown() {
        database.close()
    }

    private fun row(
        accountId: Int = 1,
        scope: Int = 10,
        date: String,
        slotId: Int,
        subject: String = "Matematika",
        startTime: String = "07:00"
    ) = ScheduleEntity(
        accountId = accountId,
        scope = scope,
        date = date,
        slotId = slotId,
        startTime = startTime,
        endTime = "08:30",
        subjectId = 2,
        subjectCode = "MAT",
        subjectName = subject,
        teacherName = "Guru Demo",
        roomName = "Ruang 101",
        classGroupName = "X-1",
        changeType = null,
        changeDescription = null,
        originalStartTime = null,
        originalEndTime = null,
        originalRoomName = null,
        originalTeacherName = null
    )

    @Test
    fun observesOnlyRowsInRangeForAccountAndScope() = runBlocking {
        val dao = database.localDao()
        dao.insertSchedules(
            listOf(
                row(date = "2026-11-10", slotId = 2, startTime = "09:00"),
                row(date = "2026-11-10", slotId = 1, startTime = "07:00"),
                row(date = "2026-11-17", slotId = 3), // di luar rentang
                row(scope = 11, date = "2026-11-10", slotId = 4), // anak lain
                row(accountId = 2, date = "2026-11-10", slotId = 5) // akun lain
            )
        )

        val rows = dao.observeSchedules(1, 10, "2026-11-09", "2026-11-14").first()

        assertEquals(listOf(1, 2), rows.map { it.slotId }) // urut tanggal lalu jam
    }

    @Test
    fun deletingRangeRemovesEntriesGoneFromServerAndKeepsOtherWeeks() = runBlocking {
        val dao = database.localDao()
        dao.insertSchedules(
            listOf(
                row(date = "2026-11-10", slotId = 1),
                row(date = "2026-11-11", slotId = 2),
                row(date = "2026-11-17", slotId = 3)
            )
        )

        dao.deleteSchedulesInRange(1, 10, "2026-11-09", "2026-11-14")
        dao.insertSchedules(listOf(row(date = "2026-11-10", slotId = 1, subject = "Fisika")))

        val thisWeek = dao.observeSchedules(1, 10, "2026-11-09", "2026-11-14").first()
        val nextWeek = dao.observeSchedules(1, 10, "2026-11-16", "2026-11-21").first()

        assertEquals(listOf("Fisika"), thisWeek.map { it.subjectName })
        assertEquals(listOf(3), nextWeek.map { it.slotId })
    }

    @Test
    fun todosAreOrderedOpenFirstThenByDueDateAndScopedToAccount() = runBlocking {
        val dao = database.localDao()
        dao.addTodo(TodoItem(accountId = 1, title = "Lusa", dueDate = "2026-11-20"))
        dao.addTodo(TodoItem(accountId = 1, title = "Besok", dueDate = "2026-11-12"))
        val doneId = dao.addTodo(TodoItem(accountId = 1, title = "Selesai", dueDate = "2026-11-01"))
        dao.addTodo(TodoItem(accountId = 2, title = "Milik lain", dueDate = "2026-11-02"))
        dao.updateTodo(doneId, 1, true)

        val todos = dao.getTodos(1).first()

        assertEquals(listOf("Besok", "Lusa", "Selesai"), todos.map { it.title })

        dao.deleteTodo(doneId, 2) // akun lain tidak boleh menghapus
        assertEquals(3, dao.getTodos(1).first().size)
    }

    @Test
    fun taskDoneMarksAreStoredPerAccountAndCanBeRemoved() = runBlocking {
        val dao = database.localDao()
        dao.markTaskDone(TaskDoneEntity(1, 10))
        dao.markTaskDone(TaskDoneEntity(1, 10)) // dua kali tidak menggandakan
        dao.markTaskDone(TaskDoneEntity(1, 11))
        dao.markTaskDone(TaskDoneEntity(2, 12))

        assertEquals(listOf(10, 11), dao.observeDoneTaskIds(1).first().sorted())

        dao.unmarkTaskDone(1, 10)
        assertEquals(listOf(11), dao.observeDoneTaskIds(1).first())
    }

    @Test
    fun subjectsAreDistinctAndSortedByName() = runBlocking {
        val dao = database.localDao()
        dao.insertSchedules(
            listOf(
                row(date = "2026-11-10", slotId = 1, subject = "Matematika"),
                row(date = "2026-11-11", slotId = 2, subject = "Matematika"),
                row(date = "2026-11-11", slotId = 3, subject = "Biologi").copy(subjectId = 3)
            )
        )

        val subjects = dao.observeSubjects(1, 10).first()

        assertEquals(listOf("Biologi", "Matematika"), subjects.map { it.subjectName })
    }
}
