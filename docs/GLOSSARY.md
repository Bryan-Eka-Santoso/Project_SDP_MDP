# Glosarium istilah

Semua kode memakai nama bahasa Inggris pada kolom "Nama di kode". Teks untuk pengguna tetap bahasa Indonesia. Jangan membuat sinonim baru (misalnya `Pupil`, `Parent`, `Lesson`) untuk konsep yang sudah ada di tabel ini.

## Entitas

| Istilah Indonesia | Nama di kode (model) | Tabel | Catatan |
|---|---|---|---|
| Pengguna / akun | `User` | `users` | Punya satu `role` |
| Siswa | `Student` | `students` | Profil siswa, relasi 1–1 ke `User` |
| Guru | `Teacher` | `teachers` | Profil guru, relasi 1–1 ke `User` |
| Wali murid / orang tua | `Guardian` | `guardians` | Profil wali murid, relasi 1–1 ke `User` |
| Relasi wali murid–siswa | `StudentGuardian` | `student_guardians` | Many-to-many |
| Admin | — | — | Cukup `User` dengan role `ADMIN` |
| Wali kelas | `homeroomTeacher` | — | Field `homeroomTeacherId` di `ClassGroup`, **bukan** role |
| Tahun ajaran | `AcademicYear` | `academic_years` | Contoh nama: `2026/2027` |
| Semester | `Semester` | `semesters` | `ODD` (ganjil) / `EVEN` (genap) |
| Kelas (rombongan belajar) | `ClassGroup` | `class_groups` | Jangan pakai `Class` (kata kunci Kotlin/JS) |
| Anggota kelas | `ClassMember` | `class_members` | Siswa dalam kelas pada tahun ajaran tertentu |
| Tingkat kelas | `gradeLevel` | — | `10`, `11`, `12` |
| Mata pelajaran | `Subject` | `subjects` | |
| Penugasan guru mengajar | `TeachingAssignment` | `teaching_assignments` | Guru × mapel × kelas × semester |
| Fasilitas (ruangan/barang) | `Facility` | `facilities` | `type`: `ROOM` / `ITEM`. Ruangan di jadwal juga `Facility` bertipe `ROOM` |
| Jam pelajaran | `LessonPeriod` | `lesson_periods` | Jam ke-n beserta waktu mulai–selesai; slot jadwal merujuk `startPeriod`–`endPeriod` |
| Slot jadwal | `ScheduleSlot` | `schedule_slots` | Penugasan mengajar pada hari + rentang jam + ruangan |
| Perubahan jadwal | `ScheduleChange` | `schedule_changes` | Override untuk satu tanggal |
| Guru pengganti | `substituteTeacher` | — | Field di `ScheduleChange` |
| Sesi pelajaran / pertemuan | `LessonSession` | `lesson_sessions` | Satu slot pada satu tanggal; memuat jurnal |
| Jurnal mengajar | `journal` | — | Field teks (materi) di `LessonSession` |
| Presensi / absensi | `Attendance` | `attendances` | Status satu siswa di satu sesi |
| Pengajuan izin | `LeaveRequest` | `leave_requests` | Diajukan wali murid, diverifikasi wali kelas |
| Komponen penilaian | `GradeComponent` | `grade_components` | Contoh: Tugas, UH, UTS, UAS |
| Nilai | `Score` | `scores` | Nilai satu siswa pada satu komponen |
| Nilai akhir | `finalScore` | — | **Dihitung**, tidak disimpan |
| Catatan perkembangan (rapor) | `ProgressNote` | `progress_notes` | Ditulis guru per mapel |
| Jenis pelanggaran | `ViolationType` | `violation_types` | Punya bobot `points` |
| Pelanggaran (kejadian) | `Violation` | `violations` | Menyimpan salinan `points` saat dicatat |
| Buku penghubung | `LiaisonNote` | `liaison_notes` | Catatan wali kelas untuk wali murid |
| Pengumuman | `Announcement` | `announcements` | `scope`: `SCHOOL` / `CLASS` |
| Kalender akademik (agenda) | `AcademicEvent` | `academic_events` | |
| Survei minat | `ElectiveSurvey` | `elective_surveys` | |
| Jawaban survei | `ElectiveSurveyResponse` | `elective_survey_responses` | |
| Paket mata pelajaran pilihan | `SubjectPackage` | `subject_packages` | Per tahun ajaran |
| Pilihan paket siswa | `PackageSelection` | `package_selections` | |
| Peminjaman fasilitas | `FacilityBooking` | `facility_bookings` | |
| Tagihan / pembayaran | `Bill` | `bills` | Informasi dari yayasan, bukan transaksi |
| Notifikasi | `Notification` | `notifications` | Disimpan, lalu dikirim lewat FCM |
| Token perangkat FCM | `DeviceToken` | `device_tokens` | |
| NIS / NISN | `nis` / `nisn` | — | Nomor induk siswa |
| NIP / kode guru | `employeeNumber` | — | |

## Enum

| Enum | Nilai | Arti |
|---|---|---|
| `Role` | `STUDENT`, `TEACHER`, `GUARDIAN`, `ADMIN` | |
| `UserStatus` | `ACTIVE`, `BLOCKED` | |
| `SemesterType` | `ODD`, `EVEN` | Ganjil, genap |
| `DayOfWeek` | `MONDAY` … `SATURDAY` | Hari sekolah |
| `FacilityType` | `ROOM`, `ITEM` | Ruangan, barang |
| `ScheduleChangeType` | `SUBSTITUTE`, `RESCHEDULE`, `CANCEL` | Guru pengganti, pindah waktu/ruang, batal |
| `AttendanceStatus` | `PRESENT`, `SICK`, `EXCUSED`, `ABSENT` | Hadir, sakit, izin, alpa |
| `LeaveRequestStatus` | `PENDING`, `APPROVED`, `REJECTED` | |
| `AnnouncementScope` | `SCHOOL`, `CLASS` | |
| `AcademicEventType` | `EXAM`, `HOLIDAY`, `ACTIVITY` | Ujian, libur, kegiatan |
| `BookingStatus` | `PENDING`, `APPROVED`, `REJECTED`, `RETURNED` | Diajukan, disetujui, ditolak, sudah dikembalikan |
| `BillStatus` | `PAID`, `UNPAID` | Menunggak = `UNPAID` dan sudah lewat jatuh tempo (dihitung) |
| `SurveyStatus` | `DRAFT`, `PUBLISHED`, `CLOSED` | |
| `NotificationType` | `SCHEDULE_CHANGE`, `ANNOUNCEMENT`, `VIOLATION`, `LEAVE_REQUEST`, `BOOKING`, `BILL_OVERDUE` | |

Menambah atau mengubah nilai enum termasuk perubahan kontrak: perbarui tabel ini, `schema.prisma`, dan model di Android.
