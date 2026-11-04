# Glosarium istilah

Semua kode memakai nama bahasa Inggris pada kolom "Nama di kode". Teks untuk pengguna tetap bahasa Indonesia. Jangan membuat sinonim baru (misalnya `Pupil`, `Parent`, `Lesson`) untuk konsep yang sudah ada di tabel ini.

## Entitas

Nama tabel fisik mengikuti PDM v2 di laporan (ADR-017): bahasa Indonesia, `snake_case` huruf kecil. Kolom juga memakai nama PDM (mis. `id_siswa`, `tanggal_lahir`) dan dipetakan ke field bahasa Inggris dengan `@map`.

| Istilah Indonesia | Nama di kode (model) | Tabel | Catatan |
|---|---|---|---|
| Pengguna / akun | `User` | `akun` | Punya **satu** `role` dan `name` (nama untuk semua role) |
| Admin | — | — | Cukup `User` dengan role `ADMIN` (tidak ada tabel profil) |
| Siswa | `Student` | `siswa` | Profil siswa, relasi 1–1 ke `User` |
| Guru | `Teacher` | `guru` | Profil guru, relasi 1–1 ke `User`. `isHomeroomTeacher` (`is_wali_kelas`) = penanda wali kelas di tahun ajaran aktif |
| Wali murid / orang tua | `Guardian` | `wali_murid` | Profil wali murid, relasi 1–1 ke `User` |
| Relasi wali murid–siswa | `StudentGuardian` | `siswa_wali_murid` | Many-to-many, dengan `relationship` (hubungan) |
| Refresh token | `RefreshToken` | `refresh_token` | Disimpan sebagai hash agar bisa dicabut |
| Token perangkat FCM | `DeviceToken` | `token_perangkat` | |
| Wali kelas | `homeroomTeacher` | — | Field `homeroomTeacherId` (`id_wali_kelas`) di `ClassGroup`, **bukan** role |
| Tahun ajaran | `AcademicYear` | `tahun_ajaran` | Contoh nama: `2026/2027` |
| Semester | `Semester` | `semester` | `type`: `ODD` (ganjil) / `EVEN` (genap), milik satu tahun ajaran |
| Kelas (rombongan belajar) | `ClassGroup` | `kelas` | Jangan pakai `Class` (kata kunci Kotlin/JS) |
| Anggota kelas | `ClassMember` | `siswa_kelas` | Siswa dalam kelas pada tahun ajaran tertentu |
| Tingkat kelas | `gradeLevel` | — | `10`, `11`, `12` |
| Mata pelajaran | `Subject` | `mata_pelajaran` | |
| Penugasan guru mengajar | `TeachingAssignment` | `penugasan_guru` | Guru × mapel × kelas × semester |
| Fasilitas (ruangan/barang) | `Facility` | `fasilitas` | `type`: `ROOM` / `ITEM`. Ruangan di jadwal juga `Facility` bertipe `ROOM` |
| Jam pelajaran | `LessonPeriod` | — | **Belum dipakai** (PO-09); slot jadwal sementara memakai `startTime`–`endTime` |
| Slot jadwal | `ScheduleSlot` | `jadwal` | Penugasan mengajar pada hari + jam mulai–selesai + ruangan |
| Perubahan jadwal | `ScheduleChange` | `perubahan_jadwal` | Override untuk satu tanggal |
| Guru pengganti | `substituteTeacher` | — | Field di `ScheduleChange` |
| Sesi pelajaran / pertemuan | `LessonSession` | `sesi_mengajar` | Satu slot pada satu tanggal; memuat jurnal |
| Jurnal mengajar | `journal` | — | Field teks (`materi`) di `LessonSession` |
| Presensi / absensi | `Attendance` | `presensi` | Status satu siswa di satu sesi |
| Pengajuan izin | `LeaveRequest` | `pengajuan_izin` | Diajukan wali murid, diverifikasi wali kelas |
| Komponen penilaian | `GradeComponent` | `komponen_penilaian` | Per mapel per semester. Contoh: Tugas, UH, UTS, UAS |
| Nilai | `Score` | `nilai` | Nilai satu siswa pada satu komponen |
| Nilai akhir | `finalScore` | — | **Dihitung**, tidak disimpan |
| Catatan perkembangan (rapor) | `ProgressNote` | `catatan_perkembangan` | Ditulis guru per mapel |
| Jenis pelanggaran | `ViolationType` | `jenis_pelanggaran` | Punya bobot `points` |
| Pelanggaran (kejadian) | `Violation` | `pelanggaran` | Menyimpan salinan `points` saat dicatat |
| Buku penghubung | `LiaisonNote` | `buku_penghubung` | Catatan wali kelas untuk wali murid |
| Pengumuman | `Announcement` | `pengumuman` | `scope`: `SCHOOL` / `CLASS` |
| Kalender akademik (agenda) | `AcademicEvent` | `kalender_akademik` | |
| Survei minat | `ElectiveSurvey` | `survei_mapel_pilihan` | |
| Jawaban survei | `ElectiveSurveyResponse` | `jawaban_survei` | Satu baris per mapel yang diminati siswa |
| Paket mata pelajaran pilihan | `SubjectPackage` | `paket_mata_pelajaran` | Per tahun ajaran |
| Isi paket mapel | `SubjectPackageItem` | `paket_mapel_detail` | Many-to-many paket–mapel |
| Pilihan paket siswa | `PackageSelection` | `pemilihan_mapel` | |
| Peminjaman fasilitas | `FacilityBooking` | `peminjaman` | |
| Tagihan / pembayaran | `Bill` | `pembayaran` | Informasi dari yayasan, bukan transaksi |
| Notifikasi | `Notification` (pesan FCM), `NotificationEntity` (Room) | — | Tidak disimpan di server; riwayat disimpan di perangkat |
| To-do pribadi siswa | `TodoItem` (Room) | — | Hanya di Android; opsional `subjectId` |
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
| `NotificationType` | `SCHEDULE_CHANGE`, `ANNOUNCEMENT`, `VIOLATION`, `LEAVE_REQUEST`, `BOOKING`, `BILL_OVERDUE` | Field `type` di payload FCM; tidak ada di `schema.prisma` karena notifikasi tidak disimpan di database (ADR-010) |

Menambah atau mengubah nilai enum termasuk perubahan kontrak: perbarui tabel ini, `schema.prisma`, dan model di Android.
