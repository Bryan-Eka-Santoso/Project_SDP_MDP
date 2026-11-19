package id.sch.santaagnes.sim.data

import id.sch.santaagnes.sim.data.remote.AcademicYearCountDto
import id.sch.santaagnes.sim.data.remote.AcademicYearSummaryDto
import id.sch.santaagnes.sim.data.remote.ClassGroupShortDto
import id.sch.santaagnes.sim.data.remote.ClassMemberItemDto
import id.sch.santaagnes.sim.data.remote.ClassMembersResponseDto
import id.sch.santaagnes.sim.data.remote.MasterAcademicYearDto
import id.sch.santaagnes.sim.data.remote.MasterAssignmentClassDto
import id.sch.santaagnes.sim.data.remote.MasterAssignmentCountDto
import id.sch.santaagnes.sim.data.remote.MasterAssignmentSubjectDto
import id.sch.santaagnes.sim.data.remote.MasterAssignmentTeacherDto
import id.sch.santaagnes.sim.data.remote.MasterAssignmentUserDto
import id.sch.santaagnes.sim.data.remote.MasterClassCountDto
import id.sch.santaagnes.sim.data.remote.MasterClassGroupDto
import id.sch.santaagnes.sim.data.remote.MasterSemesterDto
import id.sch.santaagnes.sim.data.remote.MasterSemesterSummaryDto
import id.sch.santaagnes.sim.data.remote.MasterTeacherDto
import id.sch.santaagnes.sim.data.remote.MasterTeachingAssignmentDto
import id.sch.santaagnes.sim.data.remote.SemesterCountDto
import id.sch.santaagnes.sim.data.remote.StudentClassGroupDto
import id.sch.santaagnes.sim.data.remote.StudentGuardianItemDto
import id.sch.santaagnes.sim.data.remote.StudentItemDto
import id.sch.santaagnes.sim.data.remote.StudentUserDto
import id.sch.santaagnes.sim.data.remote.UserItemDto
import id.sch.santaagnes.sim.data.repository.toDetailDomain
import id.sch.santaagnes.sim.data.repository.toDomain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MasterMappersTest {

    @Test
    fun mapsUserAndStudentDetail() {
        val user = UserItemDto(
            id = 5,
            email = "admin@santaagnes.sch.id",
            name = "Admin SIM",
            role = "ADMIN",
            status = "BLOCKED",
            createdAt = "2026-11-10T01:00:00Z",
            updatedAt = null
        ).toDomain()

        assertEquals("BLOCKED", user.status)
        assertFalse(user.isActive)

        val studentDetail = StudentItemDto(
            id = 12,
            nis = "2026001",
            birthDate = "2008-05-12",
            phone = "0812345",
            user = StudentUserDto(8, "Bryan Eka", "bryan@santaagnes.sch.id", "ACTIVE"),
            currentClass = StudentClassGroupDto(3, "XII IPA 2", "12"),
            guardians = listOf(
                StudentGuardianItemDto(4, "Ayah", "Bpk. Santoso", "08111", "wali@santaagnes.sch.id")
            )
        ).toDetailDomain()

        assertEquals("2008-05-12", studentDetail.student.birthDate)
        assertEquals("XII IPA 2", studentDetail.student.className)
        assertEquals(1, studentDetail.guardians.size)
        assertEquals("Ayah", studentDetail.guardians.first().relationship)
    }

    @Test
    fun mapsAcademicYearAndSemester() {
        val sem = MasterSemesterDto(
            id = 2,
            academicYearId = 1,
            type = "EVEN",
            startDate = "2026-01-05",
            endDate = "2026-06-20",
            isActive = true,
            academicYear = AcademicYearSummaryDto(1, "2025/2026", true),
            _count = SemesterCountDto(14)
        ).toDomain()

        assertEquals("Sem. 2 (Genap)", sem.label)
        assertEquals(14, sem.assignmentCount)
        assertTrue(sem.isActive)

        val year = MasterAcademicYearDto(
            id = 1,
            name = "2025/2026",
            startDate = "2025-07-14",
            endDate = "2026-06-20",
            isActive = true,
            _count = AcademicYearCountDto(classGroups = 6, semesters = 2)
        ).toDomain()

        assertEquals(6, year.classCount)
        assertEquals(2, year.semesterCount)
    }

    @Test
    fun mapsTeacherAssignmentClassAndMembers() {
        val teacher = MasterTeacherDto(
            id = 7,
            employeeNumber = "19850101",
            isHomeroomTeacher = true,
            user = StudentUserDto(15, "Ibu Margaretha", "margaretha@santaagnes.sch.id", "ACTIVE")
        ).toDomain()

        assertTrue(teacher.isHomeroomTeacher)
        assertEquals("19850101", teacher.employeeNumber)

        val assignment = MasterTeachingAssignmentDto(
            id = 20,
            teacher = MasterAssignmentTeacherDto(7, "19850101", MasterAssignmentUserDto(15, "Ibu Margaretha")),
            subject = MasterAssignmentSubjectDto(2, "MTK", "Matematika", "MANDATORY"),
            classGroup = MasterAssignmentClassDto(4, "X-A", "10", AcademicYearSummaryDto(1, "2025/2026", true)),
            semester = MasterSemesterSummaryDto(2, "EVEN", true),
            _count = MasterAssignmentCountDto(scheduleSlots = 3, tasks = 2)
        ).toDomain()

        assertEquals("Matematika", assignment.subjectName)
        assertEquals("X-A", assignment.className)
        assertEquals(5, assignment.usageCount)

        val cls = MasterClassGroupDto(
            id = 4,
            name = "X-A",
            gradeLevel = "10",
            capacity = 36,
            academicYear = AcademicYearSummaryDto(1, "2025/2026", true),
            homeroomTeacher = MasterAssignmentTeacherDto(7, "19850101", MasterAssignmentUserDto(15, "Ibu Margaretha")),
            _count = MasterClassCountDto(members = 30, teachingAssignments = 8)
        ).toDomain()

        assertEquals(30, cls.memberCount)
        assertEquals("Ibu Margaretha", cls.homeroomTeacherName)

        val membersPage = ClassMembersResponseDto(
            classGroup = ClassGroupShortDto(4, "X-A", 36),
            total = 1,
            members = listOf(ClassMemberItemDto(12, "2026001", "Bryan", "b@s.id"))
        ).toDomain()

        assertEquals(1, membersPage.members.size)
        assertEquals("Bryan", membersPage.members.first().name)
    }
}
