# Catatan keputusan (ADR)

Keputusan arsitektur yang sudah diambil beserta alasannya. Sebelum mengusulkan perubahan pada hal di bawah, baca alasannya dulu. Keputusan berstatus **Diusulkan** tetap diikuti sampai tim mengubahnya.

Format menambah keputusan baru: nomor berikutnya, judul, status, konteks, keputusan, konsekuensi.

---

### ADR-001 Monorepo
- **Status:** Diterima
- **Keputusan:** Backend dan Android dalam satu repo (`backend/`, `android/`) dengan dokumen bersama di `docs/`.
- **Alasan:** Kontrak API dan aturan bisnis cukup ditulis sekali dan dibaca oleh AI di kedua sisi.

### ADR-002 Android native Kotlin, tanpa web admin
- **Status:** Diterima
- **Keputusan:** Satu aplikasi Android (min API 24) untuk semua role, termasuk admin.
- **Konsekuensi:** Layar admin (impor, master data, laporan) harus nyaman di HP dan tablet; laporan berbasis filter + ekspor, bukan tabel lebar.

### ADR-003 Express + JavaScript + Prisma + MySQL
- **Status:** Diterima
- **Keputusan:** Backend Express dengan JavaScript (bukan TypeScript), ORM Prisma, database MySQL.
- **Konsekuensi:** Validasi tipe dilakukan dengan Zod di setiap input.

### ADR-004 Wali kelas bukan role
- **Status:** Diterima
- **Keputusan:** Wali kelas = `Teacher` yang menjadi `homeroomTeacherId` pada `ClassGroup` di tahun ajaran tertentu.
- **Alasan:** Satu guru tidak perlu dua akun, dan wali kelas otomatis berganti setiap tahun ajaran.
- **Tambahan (30/10/2026):** `Teacher.isHomeroomTeacher` (`guru.is_wali_kelas`) dipakai sebagai penanda cepat untuk menu Android. Nilainya **diturunkan** dari `homeroomTeacherId` pada tahun ajaran aktif dan diperbarui backend setiap admin mengatur wali kelas atau mengganti tahun ajaran aktif; tidak diubah manual. Hak akses wali kelas tetap dicek dari `ClassGroup.homeroomTeacherId`.

### ADR-005 Semua data akademik terikat periode
- **Status:** Diterima
- **Keputusan:** Kelas terikat `AcademicYear`; jadwal, nilai, dan presensi terikat `Semester`.
- **Alasan:** Laporan per periode dan tren mapel pilihan antar tahun.

### ADR-006 Perubahan jadwal sebagai override per tanggal
- **Status:** Diterima
- **Keputusan:** `ScheduleChange` menimpa `ScheduleSlot` untuk satu tanggal; slot asli tidak diubah.
- **Alasan:** Riwayat perubahan dan laporan perubahan jadwal tetap tersedia.

### ADR-007 Nilai akhir dihitung, tidak disimpan
- **Status:** Diterima
- **Keputusan:** `finalScore` dihitung dari `Score` × bobot saat dibaca.
- **Alasan:** Perubahan bobot oleh guru langsung berlaku tanpa sinkronisasi data.

### ADR-008 Poin pelanggaran disalin saat dicatat
- **Status:** Diterima
- **Keputusan:** `Violation.points` adalah salinan bobot saat kejadian dicatat.
- **Alasan:** Perubahan master pelanggaran tidak boleh mengubah riwayat.

### ADR-009 Kalender perangkat lewat CalendarContract
- **Status:** Diterima
- **Keputusan:** Sinkronisasi menulis langsung ke kalender perangkat lewat `CalendarContract`, bukan Google Calendar API. Yang disinkron hanya agenda kalender akademik dan peminjaman yang disetujui; jadwal pelajaran tidak disinkron (diganti to-do list lokal, F-JDW-07).
- **Alasan:** Scope Google Calendar API termasuk sensitif dan membutuhkan verifikasi OAuth dari Google. Event di kalender perangkat yang terhubung ke akun Google tetap tersinkron ke Google Calendar.

### ADR-010 Notifikasi lewat FCM, riwayat di perangkat
- **Status:** Diterima (diubah 30/10/2026; sebelumnya notifikasi disimpan di tabel `notifikasi`)
- **Keputusan:** Notifikasi **tidak disimpan di database server**. Setelah transaksi data selesai, backend mengirim FCM di proses latar (in-process), batch ≤ 500 token, ke `DeviceToken` milik penerima. Pesan dikirim sebagai **data message** (tanpa blok `notification`) agar `onMessageReceived` selalu terpanggil, termasuk saat aplikasi di latar belakang; Android lalu menampilkan notifikasi dan menyimpannya ke Room sebagai riwayat (F-NTF-01).
- **Alasan:** Mengikuti arsitektur sistem tim (Express → FCM → Android) dan mengurangi tabel. Satu perubahan jadwal bisa mengenai ratusan penerima; admin tidak boleh menunggu. Tidak memakai Redis/antrian eksternal.
- **Konsekuensi:** Riwayat notifikasi hanya ada di perangkat yang menerimanya; hilang saat aplikasi di-uninstall atau ganti HP. Notifikasi untuk user yang belum punya token aktif (belum pernah login di HP) tidak terkirim. Data aslinya (jadwal, pengumuman, izin, dll.) tetap bisa dilihat dari layar masing-masing.

### ADR-011 File di Cloudinary
- **Status:** Diterima
- **Keputusan:** Lampiran disimpan di Cloudinary; database menyimpan `url` dan `publicId`.

### ADR-012 Impor Excel semua-atau-tidak-sama-sekali
- **Status:** Diterima
- **Keputusan:** Impor divalidasi penuh; jika ada baris salah, tidak ada yang disimpan dan semua error dikembalikan per baris.
- **Alasan:** Mencegah data setengah masuk yang sulit dibersihkan admin dari HP.

### ADR-013 UI Android: XML + Fragment + ViewBinding
- **Status:** Diusulkan
- **Keputusan:** Tidak memakai Jetpack Compose.
- **Alasan:** Navigation Component berbasis Fragment sudah dipilih, dan semua anggota menguasai pola yang sama.

### ADR-014 Dependency injection dengan Hilt
- **Status:** Diusulkan
- **Keputusan:** Hilt untuk menyediakan Retrofit, Room, Repository, dan ViewModel.
- **Alasan:** Pola standar yang dikenali AI dengan konsisten; menghindari pembuatan objek manual yang berbeda-beda tiap anggota.

### ADR-015 AI tidak menjalankan git dan migrasi database
- **Status:** Diterima
- **Keputusan:** Agent hanya mengubah file; perintah git yang mengubah state dan perintah migrasi/perubahan database dijalankan manusia (lihat `AGENTS.md` bagian 6).
- **Alasan:** Mencegah commit, rebase, atau migrasi yang tidak disengaja di repo bersama.

### ADR-016 Prisma dikunci ke versi 6
- **Status:** Diterima
- **Keputusan:** `prisma` dan `@prisma/client` memakai versi 6 dengan generator `prisma-client-js` (diimpor dari `@prisma/client`).
- **Alasan:** Prisma 8 belum mendukung MySQL; Prisma 7 berorientasi TypeScript/ESM, sedangkan backend memakai JavaScript CommonJS.

### ADR-017 Skema database mengikuti PDM v2 laporan
- **Status:** Diterima
- **Konteks:** Laporan (BAB III dan lampiran CDM/PDM) memakai rancangan 36 tabel dengan nama Indonesia (`query_database_v2.sql`), sedangkan dokumen di repo memakai nama Inggris.
- **Keputusan:** `schema.prisma` mengikuti struktur PDM v2. Nama model/field tetap bahasa Inggris sesuai `docs/GLOSSARY.md`; nama tabel/kolom fisik memakai nama PDM dalam `snake_case` huruf kecil lewat `@@map`/`@map`. Huruf kecil dipakai karena MySQL di Windows mengubah nama tabel menjadi huruf kecil, sehingga nama campuran (`TahunAjaran`) berbeda antara laptop Windows dan server Linux.
- **Penyesuaian terhadap PDM v2** (agar aturan bisnis bisa dijalankan; PDM dan BAB III perlu diperbarui):
  1. `Role` + `AkunRole` dihapus; `akun.role` berisi satu role (lihat ADR-004). `nama` disimpan di `akun`; tabel `admin` dihapus karena admin tidak punya data khusus (30/10/2026). `guru.is_wali_kelas` ditambahkan (ADR-004).
  2. Tabel `semester` dipisah dari `tahun_ajaran`; `penugasan_guru` merujuk `id_semester`.
  3. `komponen_penilaian` per penugasan guru (`id_penugasan`), bobot persen bulat (ADR-018; sebelumnya per mapel per semester).
  4. `pelanggaran.poin` menyimpan salinan bobot (ADR-008).
  5. `perubahan_jadwal` untuk satu `tanggal` + `jenis` + jam/ruangan baru untuk `RESCHEDULE` (ADR-006).
  6. `pengajuan_izin` memakai `tanggal_mulai`–`tanggal_selesai`; lampiran memakai `lampiran_url` + `lampiran_public_id` (ADR-011), juga pada `pengumuman`.
  7. Tabel baru `refresh_token` dan `token_perangkat`.
  8. Kolom baru: `pembayaran.kode_tagihan`, `survei_mapel_pilihan.tingkat` dan `status`, `kalender_akademik.jenis`, `notifikasi.id_referensi`, `created_at`/`updated_at`.
  9. `kelas.id_wali_kelas` boleh kosong (kelas hasil pembagian paket dibuat sebelum wali kelas diatur).
  10. Tabel baru `tugas` (ADR-018) dan `predikat_nilai`; kolom baru `siswa.no_telp` (07/11/2026).
- **Konsekuensi:** Tabel `notifikasi` + `notifikasi_penerima` dari PDM v2 sempat dibuat lalu dihapus (ADR-010). Jam pelajaran memakai `jam_mulai`/`jam_selesai` sampai PO-09 dijawab. Total skema: 36 tabel; `query_database_v4.sql` (34 tabel) perlu diperbarui.

### ADR-018 Bobot nilai oleh guru per penugasan; tugas dari guru tanpa pengumpulan
- **Status:** Diterima (07/11/2026)
- **Konteks:** Prototype Figma menampilkan tugas dari guru, dan tim memutuskan bobot nilai diatur guru mapel, bukan admin.
- **Keputusan:**
  1. `GradeComponent` terikat `TeachingAssignment`. Setiap guru mengatur komponen dan bobot kelas yang ia ajar; mapel sama di kelas lain boleh berbeda.
  2. `Task` (tabel `tugas`) dibuat guru per `TeachingAssignment` dan dilihat siswa kelas itu. Tidak ada pengumpulan atau penilaian tugas; tanda selesai siswa hanya di perangkat.
  3. Skala predikat (`GradePredicate`) per tahun ajaran, diatur admin.
- **Alasan:** Guru yang paling tahu proporsi penilaian kelasnya. Tanpa pengumpulan, fitur tugas tetap kecil dan cukup sebagai pengingat. Skala per tahun ajaran menjaga predikat rapor lama tidak berubah saat skala baru diatur.
- **Konsekuensi:** Rekap nilai lintas kelas untuk mapel yang sama bisa memakai bobot berbeda. Endpoint tugas ditempatkan di modul `schedules`, komponen nilai di modul `grades`, predikat di modul `master`.
