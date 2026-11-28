package id.sch.santaagnes.sim.ui.admin.elective

import id.sch.santaagnes.sim.domain.model.MasterClassGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ElectiveDefaultGradeTest {

    @Test
    fun ignoresAllocationClassesSoRegularGradeWins() {
        val classes = listOf(
            group("X-1", "10"),
            group("XI-1", "11"),
            group("MPP-3-X-1", "X"),
            group("MPP-3-X-2", "X")
        )

        assertEquals("10", defaultGradeLevel(classes))
    }

    @Test
    fun fallsBackToAllocationClassesWhenNoRegularClassExists() {
        assertEquals("X", defaultGradeLevel(listOf(group("MPP-3-X-1", "X"))))
    }

    @Test
    fun skipsInactiveYearsAndReturnsNullWithoutClasses() {
        assertNull(defaultGradeLevel(listOf(group("X-1", "10", active = false))))
    }

    private fun group(name: String, grade: String, active: Boolean = true) = MasterClassGroup(
        id = name.hashCode(),
        name = name,
        gradeLevel = grade,
        capacity = 36,
        academicYearId = 1,
        academicYearName = "2026/2027",
        isAcademicYearActive = active
    )
}
