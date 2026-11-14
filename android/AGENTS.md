# AGENTS.md — Android (Kotlin)

Aturan ini melengkapi `AGENTS.md` di root. Aturan root tetap berlaku, termasuk larangan menjalankan git dan menambah dependency tanpa persetujuan.

## Struktur package

Nama package dasar mengikuti `applicationId` di `app/build.gradle.kts`. Di bawahnya:

```
<base>/
├── core/            ← util, ekstensi, konstanta, format tanggal WIB, Result wrapper
├── data/
│   ├── remote/      ← interface Retrofit (*Api), DTO (*Dto), interceptor token
│   ├── local/       ← Room: *Entity, *Dao, AppDatabase; DataStore untuk sesi
│   └── repository/  ← *Repository (gabungan remote + local)
├── domain/model/    ← model yang dipakai UI (tanpa anotasi Retrofit/Room)
├── di/              ← modul Hilt
├── notification/    ← FirebaseMessagingService, channel notifikasi
└── ui/
    └── <feature>/   ← *Fragment, *ViewModel, *UiState, adapter RecyclerView
```

Nama feature mengikuti modul backend: `auth`, `home`, `schedule`, `grades`, `attendance`, `discipline`, `announcement`, `calendar`, `elective`, `booking`, `bill`, `report`, `notification`, `profile`, dan `admin/<master>` untuk layar master data admin.

## Pola MVVM

```
Fragment  →  ViewModel  →  Repository  →  Api (Retrofit) / Dao (Room)
   ↑ collect StateFlow<UiState>
```

1. **Fragment** hanya merender `UiState` dan meneruskan aksi pengguna ke ViewModel. Tidak ada pemanggilan API atau logika bisnis di Fragment.
2. **ViewModel** mengekspos satu `StateFlow<XxxUiState>` dan, bila perlu, `SharedFlow` untuk event sekali jalan (navigasi, snackbar). Pakai `viewModelScope`.
3. **UiState** berupa `data class` yang memuat `isLoading`, data, dan `errorMessage`, atau `sealed interface` (`Loading`, `Success`, `Error`, `Empty`). Pilih satu pola per layar dan konsisten.
4. **Repository** mengembalikan `Resource` (`core/Resource.kt`), memetakan DTO → domain model, dan mengubah error menjadi pesan bahasa Indonesia. Bungkus panggilan API dengan `ApiErrorMapper.call { ... }` (`core/`): pesan dari server (`error.message`) dipakai bila ada, selain itu teks bawaan di `strings.xml`. Tanggal dan jam tampil lewat `DateTimeWib` (`core/`).
5. **DTO tidak boleh sampai ke UI.** Selalu dipetakan ke domain model.
6. Collect flow di Fragment memakai `viewLifecycleOwner.lifecycleScope` + `repeatOnLifecycle(Lifecycle.State.STARTED)`.

## Aturan wajib

- **UI**: XML layout + ViewBinding. Jangan memakai Jetpack Compose dan jangan `findViewById` (lihat `docs/DECISIONS.md`).
- **Navigasi**: Navigation Component dengan Safe Args. Graph dipisah per role bila menu berbeda.
- **Tampilan per role**: menu dan tombol disembunyikan sesuai role dan status wali kelas, tetapi **backend tetap sumber otorisasi**. Jangan mengandalkan penyembunyian UI sebagai keamanan.
- **Wali murid**: wajib ada pemilih anak aktif (`activeStudentId` disimpan di DataStore). Semua layar wali murid memakai anak aktif tersebut.
- **Token**: access token dan refresh token disimpan di DataStore. Interceptor menambah header `Authorization`; authenticator OkHttp memperbarui token saat 401. Jika refresh gagal, logout dan kembali ke layar login.
- **Cache offline (Room)**: wajib untuk jadwal dan pengumuman. Data lain boleh online saja. Tampilkan data cache lebih dulu, lalu perbarui dari server.
- **Data lokal (Room) tanpa server**: riwayat notifikasi (`NotificationEntity`, disimpan dari `onMessageReceived`) to-do list siswa (`TodoItem`, opsional `subjectId`), dan tanda selesai tugas dari guru (`TaskDoneEntity`, berisi `taskId`). Semuanya hilang saat aplikasi di-uninstall; itu disengaja (ADR-010, ADR-018, F-JDW-07, F-JDW-09).
- **Teks**: semua teks UI di `res/values/strings.xml` dalam bahasa Indonesia. Jangan hardcode string di layout atau kode.
- **Tanggal**: terima ISO 8601 UTC dari API, tampilkan dalam WIB. Gunakan helper di `core/` (java.time melalui desugaring karena min SDK 24).
- **Ukuran layar**: layar admin harus tetap nyaman di tablet. Hindari tabel lebar; gunakan list + filter + detail.
- **Kalender**: sinkronisasi memakai `CalendarContract` dengan izin `READ_CALENDAR`/`WRITE_CALENDAR` yang diminta saat runtime, **bukan** Google Calendar API. Yang disinkron hanya agenda kalender akademik dan peminjaman yang disetujui milik pengguna; jadwal pelajaran tidak disinkron.
- **Notifikasi**: backend mengirim data message; aplikasi sendiri yang menampilkan notifikasi lewat channel per jenis (jadwal, pengumuman, nilai/presensi, peminjaman, pembayaran) dan menyimpannya ke Room. Minta izin `POST_NOTIFICATIONS` di Android 13+. Kirim token FCM ke backend setelah login dan saat token berganti; hapus token saat logout.
- **Menu wali kelas**: tampilkan berdasarkan `isHomeroomTeacher` dari profil guru; backend tetap memeriksa hak aksesnya.
- **Upload/unduh file**: pilih file lewat Storage Access Framework, kirim sebagai multipart. Ekspor laporan disimpan lewat `CreateDocument`.
- **Profil, ubah password, tagihan (F-AUTH-03, F-BYR-01/02)**: tab Profil (`ui/profile/`) menampilkan profil dan menjadi pintu masuk ke ubah password dan Tagihan. Layar bersama ini tujuan tingkat atas di `nav_graph.xml` (`changePasswordFragment`, `billListFragment`, `billImportFragment`) dengan aksi global `action_global_changePassword` dan `action_global_bills`, jadi layar lain (mis. dashboard Home/Akademik milik tim lain) cukup memanggil aksi itu. Tagihan hanya online dan hanya informasi; tidak ada pembayaran di aplikasi. Impor Excel (jadwal dan tagihan) memakai `core/UploadFiles`; layarnya memakai `ui/common/ImportBodyView` (daftar kolom, tombol Unduh template lewat `core/TemplateFiles` dengan aset dari `docs/import-templates/`, dan kesalahan impor per baris dari `Resource.Error.importErrors`).
- **Admin kelola jadwal (F-JDW-01..04)**: `ui/admin/schedule/` (daftar slot, formulir slot, impor Excel, daftar dan formulir perubahan jadwal), hanya online lewat `AdminScheduleRepository`. Pilihan penugasan/semester memakai endpoint master milik MST (`GET /semesters/active`, `GET /teaching-assignments` di `MasterLookupApi`); ruangan sementara diturunkan dari slot yang sudah ada sampai endpoint fasilitas (MST-12) tersedia. Ganti ke endpoint itu di `AdminScheduleRepositoryImpl.lookups()` saat sudah ada.
- **Laporan admin (F-LAP-07, F-LAP-09, F-LAP-11)**: `ui/admin/report/`, hanya online lewat `ReportRepository` (`GET /reports/:type` dan `/export`). Pintu masuk tombol "Laporan" di tab Profil admin (`action_global_reports`, tujuan tingkat atas di `nav_graph.xml`) menuju hub, lalu layar per laporan. Semua layar laporan menurunkan `ReportFragment` dan `ReportViewModel` (kerangka: filter, daftar kartu berpaginasi, total, tombol Ekspor Excel lewat `CreateDocument` dan `core/ExportFiles`); laporan baru cukup menurunkan keduanya, menambah satu kartu di `fragment_report_hub.xml`, dan mendaftarkan tujuannya di nav graph. Semester selalu semester aktif (tanpa pemilih semester). Pilihan guru dari `GET /teachers`, pilihan kelas diturunkan dari `GET /teaching-assignments` semester aktif (`MasterLookupApi`). Daftar memakai 2 kolom di layar lebar (`values-sw600dp/integers.xml`).

## Penamaan

| Jenis | Pola | Contoh |
|---|---|---|
| Fragment | `<Fitur><Aksi>Fragment` | `ScheduleListFragment` |
| ViewModel | `<Fitur><Aksi>ViewModel` | `GradeInputViewModel` |
| Layout | `fragment_<fitur>_<aksi>.xml`, `item_<nama>.xml` | `fragment_schedule_list.xml` |
| Retrofit | `<Modul>Api` | `ScheduleApi` |
| DTO | `<Nama>Dto`, `<Nama>Request` | `ScheduleSlotDto` |
| Room | `<Nama>Entity`, `<Nama>Dao` | `AnnouncementEntity` |

Nama model mengikuti `docs/GLOSSARY.md`.

## Testing

- Unit test ViewModel dan Repository di `src/test` (JUnit, `kotlinx-coroutines-test`, fake repository).
- Minimal uji: state loading → sukses, state error, dan pemetaan DTO → domain model.

## Perintah

| Tujuan | Perintah |
|---|---|
| Build debug | `./gradlew assembleDebug` |
| Unit test | `./gradlew testDebugUnitTest` |
| Lint | `./gradlew lintDebug` |

Base URL API diambil dari `BuildConfig.BASE_URL`, jangan di-hardcode di kode. Nilainya dari `api.baseUrl` di `android/local.properties` (tidak masuk git, per orang); bila kosong, bawaannya `http://10.0.2.2:3000/api/v1/` (emulator → localhost komputer). Contoh bila backend jalan di port 3001: `api.baseUrl=http://10.0.2.2:3001/api/v1/`. HP fisik: pakai IP LAN komputer atau `adb reverse`.
