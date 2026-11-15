package id.sch.santaagnes.sim.data

import id.sch.santaagnes.sim.data.repository.pickReminders
import id.sch.santaagnes.sim.domain.model.Task
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class TaskRemindersTest {

    private fun task(id: Int, date: String, time: String?) = Task(
        id = id,
        title = "Tugas $id",
        description = null,
        dueDate = LocalDate.parse(date),
        dueTime = time?.let(LocalTime::parse),
        subjectName = "Matematika",
        classGroupName = "X-1",
        teacherName = "Guru"
    )

    private val all = listOf(
        task(1, "2026-11-20", "10:00"),
        task(2, "2026-11-18", "15:00"),
        task(3, "2026-11-18", "08:00"),
        task(4, "2026-11-19", null),
        task(5, "2026-11-30", "07:00")
    )

    @Test
    fun picksThreeNearestSortedByDateThenTime() {
        assertEquals(listOf(3, 2, 4), pickReminders(all, emptySet(), 3).map { it.id })
    }

    @Test
    fun skipsTasksMarkedDone() {
        assertEquals(listOf(2, 4, 1), pickReminders(all, setOf(3), 3).map { it.id })
    }

    @Test
    fun taskWithoutTimeSortsAfterTimedTasksOfTheSameDay() {
        val tasks = listOf(task(1, "2026-11-18", null), task(2, "2026-11-18", "23:00"))

        assertEquals(listOf(2, 1), pickReminders(tasks, emptySet(), 3).map { it.id })
    }

    @Test
    fun returnsFewerWhenNotEnoughTasks() {
        assertEquals(listOf(1), pickReminders(listOf(task(1, "2026-11-20", null)), emptySet(), 3).map { it.id })
    }
}
