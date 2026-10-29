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
- **Alasan:** Perubahan bobot oleh admin langsung berlaku tanpa sinkronisasi data.

### ADR-008 Poin pelanggaran disalin saat dicatat
- **Status:** Diterima
- **Keputusan:** `Violation.points` adalah salinan bobot saat kejadian dicatat.
- **Alasan:** Perubahan master pelanggaran tidak boleh mengubah riwayat.

### ADR-009 Kalender perangkat lewat CalendarContract
- **Status:** Diterima
- **Keputusan:** Sinkronisasi pengingat siswa menulis langsung ke kalender perangkat lewat `CalendarContract`, bukan Google Calendar API.
- **Alasan:** Scope Google Calendar API termasuk sensitif dan membutuhkan verifikasi OAuth dari Google. Event di kalender perangkat yang terhubung ke akun Google tetap tersinkron ke Google Calendar.

### ADR-010 Notifikasi disimpan dulu, dikirim asinkron
- **Status:** Diterima
- **Keputusan:** Simpan `Notification` di database dalam transaksi, kirim FCM setelah commit di proses latar (in-process), batch ≤ 500 token.
- **Alasan:** Satu perubahan jadwal bisa mengenai ratusan penerima; admin tidak boleh menunggu. Tidak memakai Redis/antrian eksternal agar infrastruktur tetap sederhana.

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
