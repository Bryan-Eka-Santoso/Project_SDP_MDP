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
- **Kalender**: sinkronisasi memakai `CalendarContract` dengan izin `READ_CALENDAR`/`WRITE_CALENDAR` yang diminta saat runtime, **bukan** Google Calendar API. Yang disinkron hanya agenda kalender akademik dan peminjaman yang disetujui milik pengguna; jadwal pelajaran tidak disinkron. Implementasi (F-JDW-06): tombol di tab Profil (`CalendarSyncViewModel`, default mati; izin diminta saat dinyalakan), `CalendarSyncRepository` menulis ke kalender utama perangkat (`DeviceCalendar`, dipilih: `IS_PRIMARY`, lalu akun Google, lalu kalender lokal yang bisa ditulis). Pencocokan sinkron ulang oleh `core/CalendarSyncPlanner` (tambah, perbarui, hapus berdasarkan kunci `acad-<id>`/`book-<id>` dan sidik isi); id event disimpan per akun di DataStore `calendar_sync` (`CalendarSyncStore`). Sinkron ulang jalan di `MainActivity.onStart` (maksimal tiap 10 menit) dan langsung (`force`) saat push bertipe BOOKING masuk di `SimFirebaseMessagingService`; gagal memuat salah satu sumber membatalkan sinkron supaya agenda lama tidak terhapus. Peminjaman hanya untuk siswa/guru peminjam dengan status APPROVED. Keluar akun menghapus agenda dari kalender HP, mematikan tombol menghapus juga.
- **Notifikasi**: backend mengirim data message; aplikasi sendiri yang menampilkan notifikasi lewat channel per jenis (jadwal, pengumuman, nilai/presensi, peminjaman, pembayaran) dan menyimpannya ke Room. Minta izin `POST_NOTIFICATIONS` di Android 13+. Kirim token FCM ke backend setelah login dan saat token berganti; hapus token saat logout.
- **Menu wali kelas**: tampilkan berdasarkan `isHomeroomTeacher` dari profil guru; backend tetap memeriksa hak aksesnya.
- **Upload/unduh file**: pilih file lewat Storage Access Framework, kirim sebagai multipart. Ekspor laporan disimpan lewat `CreateDocument`.
- **Profil, ubah password, tagihan (F-AUTH-03, F-BYR-01/02)**: tab Profil (`ui/profile/`) menampilkan profil dan menjadi pintu masuk ke ubah password dan Tagihan. Untuk siswa tampil baris Kelas dan Wali Kelas (dari `student.classGroups` pada `GET /me`), serta No. Telepon bila ada (siswa/wali murid), mengikuti Figma Halaman Profil. Layar bersama ini tujuan tingkat atas di `nav_graph.xml` (`changePasswordFragment`, `billListFragment`, `billImportFragment`) dengan aksi global `action_global_changePassword` dan `action_global_bills`, jadi layar lain (mis. dashboard Home/Akademik milik tim lain) cukup memanggil aksi itu. Tagihan hanya online dan hanya informasi; tidak ada pembayaran di aplikasi. Impor Excel (jadwal dan tagihan) memakai `core/UploadFiles`; layarnya memakai `ui/common/ImportBodyView` (daftar kolom, tombol Unduh template lewat `core/TemplateFiles` dengan aset dari `docs/import-templates/`, dan kesalahan impor per baris dari `Resource.Error.importErrors`).
- **Admin kelola jadwal (F-JDW-01..04)**: `ui/admin/schedule/` (daftar slot, formulir slot, impor Excel, daftar dan formulir perubahan jadwal), hanya online lewat `AdminScheduleRepository`. Pilihan penugasan/semester memakai endpoint master milik MST (`GET /semesters/active`, `GET /teaching-assignments` di `MasterLookupApi`); ruangan sementara diturunkan dari slot yang sudah ada sampai endpoint fasilitas (MST-12) tersedia. Ganti ke endpoint itu di `AdminScheduleRepositoryImpl.lookups()` saat sudah ada.
- **Guru memindah jadwal (F-JDW-04)**: di tab Jadwal guru (lingkup jadwal mengajar sendiri) kartu pertemuan yang belum lewat dan tanpa guru pengganti bisa diketuk menuju `ui/schedule/RescheduleFormFragment` (tanggal baru, jam baru, ruangan baru dari `GET /schedule-rooms`, keterangan; tombol "Batalkan pemindahan" bila sudah pernah dipindah, tombol "Batalkan kelas ini" yang memakai keterangan sebagai alasan dan meminta konfirmasi). Kartu yang sudah dibatalkan membuka form mode `cancelled` yang hanya menawarkan "Pulihkan jadwal". Hanya online lewat `RescheduleRepository`; backend tetap memeriksa kepemilikan slot dan bentrok. Pindah lintas hari tampil sebagai dua entri: tanggal asal dicoret dengan "Dipindah ke ...", tanggal tujuan dengan "Pindahan dari ..." (`ScheduleChange.movedTo/movedFrom`, ikut di-cache di Room v5 lewat `MIGRATION_4_5`). Form perubahan jadwal admin punya kolom "Tanggal baru" untuk hal yang sama.
- **Channel notifikasi jadwal**: `NotificationChannelSpec.SCHEDULE` memakai id `schedule_v2` dengan `IMPORTANCE_HIGH` agar banner muncul; id lama `schedule` dihapus di `createAll`. Android tidak mengubah importance channel yang sudah ada, jadi mengubah importance channel lain berarti mengganti id-nya juga.
- **Bottom nav**: `MainActivity` memasang inset sistem pada root, jadi `BottomNavigationView` di `activity_main.xml` wajib `app:paddingBottomSystemWindowInsets="false"` dan `MainActivity` memasang listener inset kosong pada `bottomNav` (kalau tidak, inset bawah terpasang dua kali dan muncul ruang kosong di bawah ikon; atribut saja tidak cukup di Material 1.14). Tampilannya mengikuti Figma: latar putih, semua label tampil, tab aktif biru tanpa kapsul penanda.
- **Laporan admin (F-LAP-07, F-LAP-09, F-LAP-11)**: `ui/admin/report/`, hanya online lewat `ReportRepository` (`GET /reports/:type` dan `/export`). Pintu masuk tombol "Laporan" di tab Profil admin (`action_global_reports`, tujuan tingkat atas di `nav_graph.xml`) menuju hub, lalu layar per laporan. Semua layar laporan menurunkan `ReportFragment` dan `ReportViewModel` (kerangka: filter, daftar kartu berpaginasi, total, tombol Ekspor Excel lewat `CreateDocument` dan `core/ExportFiles`); laporan baru cukup menurunkan keduanya, menambah satu kartu di `fragment_report_hub.xml`, dan mendaftarkan tujuannya di nav graph. Semester selalu semester aktif (tanpa pemilih semester). Pilihan guru dari `GET /teachers`, pilihan kelas diturunkan dari `GET /teaching-assignments` semester aktif (`MasterLookupApi`). Daftar memakai 2 kolom di layar lebar (`values-sw600dp/integers.xml`).
- **Dashboard admin dan menu (F-MST)**: bottom nav admin lima tab (Home, Jadwal, Akademik, Notifikasi, Profil; maksimal 5 item untuk `BottomNavigationView`). Dashboard (`ui/admin/dashboard/`) menampilkan jumlah seluruh data lewat `AdminOverviewRepository` (satu `meta.total` per daftar dengan `limit=1`, tanpa endpoint baru) dan empat pintasan: Manajemen (tab Akademik), Pembayaran (`action_global_bills`), Operasional (`ui/admin/operations/`: mata pelajaran, fasilitas, jenis pelanggaran, peminjaman), Laporan (`action_global_reports`). Layar daftar admin menampilkan baris "Total: N" dari `meta.total` (`PageResult.total`).
- **Beranda siswa dan wali murid (F-JDW-08)**: `ui/home/HomeFragment` menampilkan sapaan, kartu "Pengingat Tugas" (tiga tugas dengan tenggat terdekat dari `TaskRepository.reminders`; siswa membuang tugas yang ditandai selesai di perangkat, wali murid memakai `studentId` anak terpilih) dan pintasan berbentuk tile (`HomeShortcutAdapter`, isinya mengikuti role). Empat kartu ringkasan mengikuti Figma: rata-rata nilai (`GET /me/scores` atau `/students/:id/scores`), kehadiran bulan ini (`GET /me/attendance-summary` untuk siswa, `GET /students/:id/attendance-summary` untuk wali murid), jumlah pertemuan hari ini (jadwal), dan total poin pelanggaran; yang gagal dimuat atau kosong tampil "-". Pengumuman terbaru (3 item) tampil di bawah pengingat. Tema mematikan `elevationOverlayEnabled` supaya kartu putih tidak berubah kebiruan. Tenggat tugas berwarna merah bila terlambat atau sisa 24 jam atau kurang (`core/DueCountdown.isDueUrgent`; tanpa jam dianggap 23:59); jam tenggat wajib diisi guru saat membuat tugas.
- **Loading di tombol dan Menu Cepat guru**: aksi simpan/kirim/impor/login menampilkan animasi loading di dalam tombolnya lewat `core/ButtonLoading.setLoading` (ikon `IndeterminateDrawable`, klik diabaikan selama loading), bukan spinner terpisah; spinner layar penuh hanya untuk memuat data. Tombol "Kembali" di header memakai ikon panah `ic_arrow_back` + teks 15sp tebal (`@string/back_previous`). Home guru memakai grid Menu Cepat (`HomeShortcutAdapter`, tile Tulis/Baca Buku dan Profil Binaan muncul hanya bila server mengizinkan). Tab Home di `MainActivity.setupBottomNav` memakai `popBackStack` ke tujuan awal supaya tetap berfungsi saat ada layar didorong di atas Home.
- **Layar awal tiap role**: graph `nav_student`, `nav_guardian`, dan `nav_teacher` memakai tab Home sebagai `startDestination` (sama dengan Figma); Back dari layar turunan kembali ke Home, dan Back dari Home menutup aplikasi.

- **Pesan error**: tampilkan pesan dari server lewat `e.userMessage()` / `serverMessage()` (`core/UserMessage.kt`), bukan `e.message` mentah. Kesalahan simpan di dialog atau bottom sheet ditampilkan di sana dan dialognya tetap terbuka.
- **Layar dan keyboard**: `MainActivity` menangani sendiri perubahan konfigurasi (`android:configChanges` di manifest) agar isian formulir tidak hilang saat layar diputar, dan menyembunyikan bottom nav selama keyboard tampil.
- **Notifikasi di Room (v6)**: riwayat diisi dari push dan dari sinkron saat aplikasi dibuka. Judul dan `referenceId` hasil sinkron harus sama dengan push backend. Penggabungan ada di `data/repository/NotificationMerge.kt`: push menggantikan baris hasil sinkron, sinkron tidak menimpa baris push, dan waktunya memakai `createdAt`/`updatedAt` data (bukan tanggal kejadian). `MIGRATION_5_6` menyesuaikan baris versi lama.
- **Sinkron kalender**: agenda yang dihapus pengguna di kalender HP dibuat lagi pada sinkron berikutnya; hasil sinkron terakhir (termasuk yang otomatis) disimpan untuk ditampilkan di Profil.
- **Wali kelas dan guru pengganti**: menu binaan (profil, presensi, catatan) muncul bila profil guru `isHomeroomTeacher`; data kelas binaan milik guru lain hanya bisa dibaca (`includeHomeroom`, `isOwn`). "Presensi Pengganti" muncul bila ada slot pengganti. Wali murid punya rekap presensi anak aktif.
- **Tagihan admin**: bisa difilter per kelas tahun ajaran aktif; ringkasannya memakai `meta.studentCount` dan `meta.totalAmount`.

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
