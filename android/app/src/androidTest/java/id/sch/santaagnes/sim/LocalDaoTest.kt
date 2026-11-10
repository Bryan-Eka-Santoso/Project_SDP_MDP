
package id.sch.santaagnes.sim

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.sch.santaagnes.sim.data.local.AppDatabase
import id.sch.santaagnes.sim.data.local.ScheduleEntity
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

    @Test
    fun saveAndReadSchedule() = runBlocking {
        val dao = database.localDao()

        dao.saveSchedules(
            listOf(
                ScheduleEntity(
                    accountId = 1,
                    scheduleId = 100,
                    subjectName = "Matematika",
                    teacherName = "Guru Demo",
                    className = "X IPA 1",
                    roomName = "Ruang 101",
                    day = "MONDAY",
                    startTime = "07:00",
                    endTime = "08:30"
                )
            )
        )

        val schedules = dao.getSchedules(1).first()

        assertEquals(1, schedules.size)
        assertEquals("Matematika", schedules[0].subjectName)
        assertEquals(100, schedules[0].scheduleId)
    }
}
