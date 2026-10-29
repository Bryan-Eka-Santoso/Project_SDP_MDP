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
4. **Repository** mengembalikan `Result`/`Resource` yang sudah ada di `core/`, memetakan DTO → domain model, dan mengubah error HTTP menjadi pesan bahasa Indonesia.
5. **DTO tidak boleh sampai ke UI.** Selalu dipetakan ke domain model.
6. Collect flow di Fragment memakai `viewLifecycleOwner.lifecycleScope` + `repeatOnLifecycle(Lifecycle.State.STARTED)`.

## Aturan wajib

- **UI**: XML layout + ViewBinding. Jangan memakai Jetpack Compose dan jangan `findViewById` (lihat `docs/DECISIONS.md`).
- **Navigasi**: Navigation Component dengan Safe Args. Graph dipisah per role bila menu berbeda.
- **Tampilan per role**: menu dan tombol disembunyikan sesuai role dan status wali kelas, tetapi **backend tetap sumber otorisasi**. Jangan mengandalkan penyembunyian UI sebagai keamanan.
- **Wali murid**: wajib ada pemilih anak aktif (`activeStudentId` disimpan di DataStore). Semua layar wali murid memakai anak aktif tersebut.
- **Token**: access token dan refresh token disimpan di DataStore. Interceptor menambah header `Authorization`; authenticator OkHttp memperbarui token saat 401. Jika refresh gagal, logout dan kembali ke layar login.
- **Cache offline (Room)**: wajib untuk jadwal, pengumuman, dan notifikasi. Data lain boleh online saja. Tampilkan data cache lebih dulu, lalu perbarui dari server.
- **Teks**: semua teks UI di `res/values/strings.xml` dalam bahasa Indonesia. Jangan hardcode string di layout atau kode.
- **Tanggal**: terima ISO 8601 UTC dari API, tampilkan dalam WIB. Gunakan helper di `core/` (java.time melalui desugaring karena min SDK 24).
- **Ukuran layar**: layar admin harus tetap nyaman di tablet. Hindari tabel lebar; gunakan list + filter + detail.
- **Kalender**: sinkronisasi pengingat siswa memakai `CalendarContract` dengan izin `READ_CALENDAR`/`WRITE_CALENDAR` yang diminta saat runtime, **bukan** Google Calendar API.
- **Notifikasi**: buat channel per jenis (jadwal, pengumuman, nilai/presensi, peminjaman, pembayaran). Minta izin `POST_NOTIFICATIONS` di Android 13+. Kirim token FCM ke backend setelah login dan saat token berganti.
- **Upload/unduh file**: pilih file lewat Storage Access Framework, kirim sebagai multipart. Ekspor laporan disimpan lewat `CreateDocument`.

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

Base URL API diambil dari `BuildConfig` (diatur per build type), jangan di-hardcode di kode.
