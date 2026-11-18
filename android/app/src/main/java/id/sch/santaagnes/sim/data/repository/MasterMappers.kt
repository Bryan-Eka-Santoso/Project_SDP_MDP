package id.sch.santaagnes.sim.data.repository

import id.sch.santaagnes.sim.data.remote.ClassMembersResponseDto
import id.sch.santaagnes.sim.data.remote.MasterAcademicYearDto
import id.sch.santaagnes.sim.data.remote.MasterClassGroupDto
import id.sch.santaagnes.sim.data.remote.MasterGuardianDto
import id.sch.santaagnes.sim.data.remote.MasterImportDto
import id.sch.santaagnes.sim.data.remote.MasterSemesterDto
import id.sch.santaagnes.sim.data.remote.MasterTeacherDto
import id.sch.santaagnes.sim.data.remote.MasterTeachingAssignmentDto
import id.sch.santaagnes.sim.data.remote.StudentItemDto
import id.sch.santaagnes.sim.data.remote.UserItemDto
import id.sch.santaagnes.sim.domain.model.MasterAcademicYear
import id.sch.santaagnes.sim.domain.model.MasterAcademicYearDetail
import id.sch.santaagnes.sim.domain.model.MasterClassGroup
import id.sch.santaagnes.sim.domain.model.MasterClassMember
import id.sch.santaagnes.sim.domain.model.MasterClassMembersPage
import id.sch.santaagnes.sim.domain.model.MasterGuardian
import id.sch.santaagnes.sim.domain.model.MasterImportResult
import id.sch.santaagnes.sim.domain.model.MasterSemester
import id.sch.santaagnes.sim.domain.model.MasterStudent
import id.sch.santaagnes.sim.domain.model.MasterStudentDetail
import id.sch.santaagnes.sim.domain.model.MasterStudentGuardian
import id.sch.santaagnes.sim.domain.model.MasterTeacher
import id.sch.santaagnes.sim.domain.model.MasterTeacherDetail
import id.sch.santaagnes.sim.domain.model.MasterTeachingAssignment
import id.sch.santaagnes.sim.domain.model.MasterUser
import retrofit2.HttpException
import retrofit2.Response

internal fun ensureDeleteSucceeded(response: Response<Unit>, allowNotFound: Boolean = false) {
    if (!response.isSuccessful && !(allowNotFound && response.code() == 404)) {
        throw HttpException(response)
    }
}

fun UserItemDto.toDomain(): MasterUser = MasterUser(
    id = id,
    email = email,
    name = name,
    role = role,
    status = status,
    createdAt = createdAt
)

fun StudentItemDto.toDomain(): MasterStudent = MasterStudent(
    id = id,
    userId = user.id,
    name = user.name,
    email = user.email,
    nis = nis,
    status = user.status,
    birthDate = birthDate,
    phone = phone,
    className = currentClass?.name,
    classId = currentClass?.id,
    guardianCount = guardians?.size ?: 0
)

fun StudentItemDto.toDetailDomain(): MasterStudentDetail = MasterStudentDetail(
    student = toDomain(),
    guardians = guardians.orEmpty().map {
        MasterStudentGuardian(
            guardianId = it.guardianId,
            relationship = it.relationship.orEmpty(),
            name = it.name,
            phone = it.phone,
            email = it.email
        )
    }
)

fun MasterAcademicYearDto.toDomain(): MasterAcademicYear = MasterAcademicYear(
    id = id,
    name = name,
    startDate = startDate,
    endDate = endDate,
    isActive = isActive,
    classCount = _count?.classGroups ?: 0,
    semesterCount = _count?.semesters ?: semesters?.size ?: 0
)

fun MasterSemesterDto.toDomain(): MasterSemester = MasterSemester(
    id = id,
    academicYearId = academicYearId,
    academicYearName = academicYear?.name,
    type = type,
    startDate = startDate,
    endDate = endDate,
    isActive = isActive,
    assignmentCount = _count?.teachingAssignments ?: 0
)

fun MasterAcademicYearDto.toDetailDomain(): MasterAcademicYearDetail = MasterAcademicYearDetail(
    academicYear = toDomain(),
    semesters = semesters.orEmpty().map { it.toDomain() }
)

fun MasterTeacherDto.toDomain(): MasterTeacher = MasterTeacher(
    id = id,
    userId = user.id,
    employeeNumber = employeeNumber,
    name = user.name,
    email = user.email,
    status = user.status,
    isHomeroomTeacher = isHomeroomTeacher
)

fun MasterTeacherDto.toDetailDomain(): MasterTeacherDetail = MasterTeacherDetail(
    teacher = toDomain(),
    homeroomClasses = homeroomClasses.orEmpty().map { cls ->
        val yr = cls.academicYear?.name?.let { " ($it)" }.orEmpty()
        "${cls.name}$yr"
    },
    taughtSubjectsSummary = teachingAssignments.orEmpty().map { ta ->
        "${ta.subject.name} · ${ta.classGroup.name}"
    }
)

fun MasterTeachingAssignmentDto.toDomain(): MasterTeachingAssignment {
    val count = (_count?.scheduleSlots ?: 0) +
        (_count?.scores ?: 0) +
        (_count?.gradeComponents ?: 0) +
        (_count?.tasks ?: 0)
    return MasterTeachingAssignment(
        id = id,
        teacherId = teacher.id,
        teacherName = teacher.user.name,
        teacherNip = teacher.employeeNumber,
        subjectId = subject.id,
        subjectCode = subject.code.orEmpty(),
        subjectName = subject.name,
        subjectType = subject.type.orEmpty(),
        classGroupId = classGroup.id,
        className = classGroup.name,
        academicYearName = classGroup.academicYear?.name,
        semesterId = semester.id,
        semesterType = semester.type,
        usageCount = count
    )
}

fun MasterClassGroupDto.toDomain(): MasterClassGroup = MasterClassGroup(
    id = id,
    name = name,
    gradeLevel = gradeLevel,
    capacity = capacity,
    academicYearId = academicYear.id,
    academicYearName = academicYear.name,
    isAcademicYearActive = academicYear.isActive,
    homeroomTeacherId = homeroomTeacher?.id,
    homeroomTeacherName = homeroomTeacher?.user?.name,
    homeroomTeacherNip = homeroomTeacher?.employeeNumber,
    memberCount = _count?.members ?: 0,
    assignmentCount = _count?.teachingAssignments ?: 0
)

fun ClassMembersResponseDto.toDomain(): MasterClassMembersPage = MasterClassMembersPage(
    classId = classGroup.id,
    className = classGroup.name,
    capacity = classGroup.capacity,
    members = members.map {
        MasterClassMember(
            studentId = it.studentId,
            nis = it.nis,
            name = it.name,
            email = it.email,
            phone = it.phone,
            birthDate = it.birthDate
        )
    }
)

fun MasterGuardianDto.toDomain(): MasterGuardian = MasterGuardian(
    id = id,
    userId = user.id,
    name = user.name,
    email = user.email,
    phone = phone,
    status = user.status,
    studentCount = _count?.students ?: 0
)

fun MasterImportDto.toDomain(): MasterImportResult = MasterImportResult(
    imported = imported,
    assignedToClass = assignedToClass
)
