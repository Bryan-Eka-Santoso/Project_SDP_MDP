# PRD — Sistem Informasi Akademik SMAK Santa Agnes

Sumber: BAB I (Pendahuluan) dan BAB II (Analisis Sistem) laporan proyek. Jika dokumen ini berbeda dengan laporan, laporan yang berlaku dan dokumen ini harus diperbarui.

## 1. Latar belakang

Informasi akademik SMAK Santa Agnes tersebar di Yosefu, Excel/Google Sheet, grup WhatsApp, kertas, dan kartu fisik. Akibatnya:

- Bentrok jadwal dicek manual dan perubahan jadwal disampaikan satu per satu.
- Penyesuaian perhitungan nilai bergantung pada vendor Yosefu.
- Pengumuman di WhatsApp mudah tertimbun.
- Jurnal mengajar ditulis di kertas lalu dipindah ke sheet.
- Poin pelanggaran dicatat di kartu fisik; halaman Ketertiban Yosefu error.
- Pembagian kelas mata pelajaran pilihan dilakukan manual dari Google Form.
- Peminjaman fasilitas dicatat manual sehingga rawan bentrok.

## 2. Tujuan

1. Siswa melihat jadwal terbaru, nilai, dan pengumuman dari satu aplikasi.
2. Guru mengelola nilai, presensi, jurnal, dan pelanggaran secara terstruktur.
3. Wali murid memantau nilai, kehadiran, poin, dan perkembangan anaknya.
4. Sekolah mengelola data akademik dan memantau semuanya dari satu platform tanpa bergantung pada vendor.

## 3. Pengguna

| Aktor | Role di sistem | Keterangan |
|---|---|---|
| Siswa | `STUDENT` | Melihat data miliknya sendiri |
| Guru | `TEACHER` | Mengelola kelas dan mapel yang diampu |
| Wali kelas | `TEACHER` + penugasan wali kelas | Guru dengan tanggung jawab tambahan atas satu kelas |
| Wali murid | `GUARDIAN` | Memantau satu atau lebih anak |
| Admin | `ADMIN` | Mengelola data master, jadwal, informasi, peminjaman, laporan |

Detail hak akses dan cakupan data: `docs/PERMISSIONS.md`.

## 4. Batasan (di luar cakupan)

- Hanya Android, native Kotlin, min API 24. **Tidak ada web admin.**
- Sistem tidak menyusun jadwal otomatis; penentuan slot dan guru tetap oleh bagian kurikulum. Sistem menyimpan, memeriksa bentrok, menampilkan, dan menotifikasi perubahan.
- Peminjaman tidak mencakup biaya sewa, denda, atau transaksi pembayaran.
- Pembayaran hanya informasi dari rekap yayasan; tidak ada transaksi pembayaran di aplikasi.
- Tidak menghitung insentif guru; hanya rekap beban mengajar.

## 5. Fitur

Format ID: `F-<MODUL>-<nomor>`. Gunakan ID ini di prompt, tiket, commit, dan pengujian Black Box.

### 5.1 Akun dan umum (`AUTH`, `NTF`)

| ID | Fitur | Aktor |
|---|---|---|
| F-AUTH-01 | Login dengan email dan password; menu sesuai role | Semua |
| F-AUTH-02 | Logout | Semua |
| F-AUTH-03 | Lihat profil dan ubah password | Semua |
| F-NTF-01 | Daftar notifikasi (perubahan jadwal, pengumuman, pelanggaran, tunggakan, status peminjaman, status izin) dan tandai sudah dibaca | Semua |
| F-NTF-02 | Push notification FCM | Semua |

### 5.2 Data master dan pengguna (`MST`) — Admin

| ID | Fitur |
|---|---|
| F-MST-01 | Master user: tambah, ubah, blokir, hapus akun |
| F-MST-02 | Atur role user |
| F-MST-03 | Reset password user |
| F-MST-04 | Master siswa (manual + impor Excel), termasuk relasi wali murid |
| F-MST-05 | Master guru (manual + impor Excel) |
| F-MST-06 | Master tahun ajaran dan semester, termasuk penanda periode aktif |
| F-MST-07 | Master kelas per tahun ajaran (manual + impor Excel) |
| F-MST-08 | Atur wali kelas setiap kelas |
| F-MST-09 | Atur siswa dalam kelas (tempatkan, pindahkan) |
| F-MST-10 | Atur guru pengajar per mata pelajaran dan kelas |
| F-MST-11 | Master mata pelajaran |
| F-MST-12 | Master fasilitas (ruangan dan barang) |

### 5.3 Jadwal (`JDW`)

| ID | Fitur | Aktor |
|---|---|---|
| F-JDW-01 | Master jadwal pelajaran semester berjalan | Admin |
| F-JDW-02 | Impor jadwal satu semester dari Excel | Admin |
| F-JDW-03 | Cek bentrok otomatis (guru, kelas, ruangan) saat simpan dan impor | Sistem |
| F-JDW-04 | Catat perubahan jadwal per tanggal (guru pengganti, pindah waktu/ruang, batal) + notifikasi ke pihak terdampak | Admin |
| F-JDW-05 | Lihat jadwal beserta perubahannya, sesuai cakupan role | Siswa, Guru, Wali kelas, Wali murid |
| F-JDW-06 | Sinkronkan jadwal dan agenda ke kalender perangkat (pengingat) | Siswa |

### 5.4 Nilai (`NIL`)

| ID | Fitur | Aktor |
|---|---|---|
| F-NIL-01 | Master komponen penilaian dan bobot | Admin |
| F-NIL-02 | Input dan edit nilai per komponen | Guru |
| F-NIL-03 | Lihat nilai dan rekap satu periode | Siswa, Wali murid |
| F-NIL-04 | Lihat rekap nilai satu kelas | Guru, Wali kelas |
| F-NIL-05 | Input catatan perkembangan siswa per mapel (bahan rapor) | Guru |

### 5.5 Presensi, jurnal, dan izin (`ABS`, `IZN`)

| ID | Fitur | Aktor |
|---|---|---|
| F-ABS-01 | Lihat kelas dan mapel yang diajar semester berjalan | Guru |
| F-ABS-02 | Input presensi sekaligus jurnal mengajar per sesi | Guru |
| F-ABS-03 | Lihat presensi per pertemuan dan rekap per periode | Siswa, Wali murid, Guru, Wali kelas |
| F-ABS-04 | Pantau jurnal mengajar seluruh guru | Admin |
| F-ABS-05 | Lihat daftar siswa suatu kelas | Guru, Wali kelas |
| F-IZN-01 | Ajukan izin ketidakhadiran (alasan + lampiran) | Wali murid |
| F-IZN-02 | Lihat status pengajuan izin | Wali murid |
| F-IZN-03 | Lihat daftar pengajuan izin yang menunggu | Wali kelas |
| F-IZN-04 | Setujui/tolak izin; jika disetujui presensi menjadi izin | Wali kelas |

### 5.6 Kesiswaan (`PLG`, `BKP`)

| ID | Fitur | Aktor |
|---|---|---|
| F-PLG-01 | Master jenis pelanggaran dan bobot poin | Admin |
| F-PLG-02 | Input pelanggaran siswa; poin bertambah otomatis + notifikasi ke wali murid | Guru |
| F-PLG-03 | Lihat akumulasi poin dan rincian kejadian, sesuai cakupan role | Semua |
| F-BKP-01 | Tulis catatan buku penghubung untuk wali murid | Wali kelas |
| F-BKP-02 | Baca buku penghubung | Wali kelas, Wali murid |
| F-BKP-03 | Lihat profil lengkap siswa binaan beserta data wali murid | Wali kelas |

### 5.7 Informasi sekolah (`PGM`, `KAL`)

| ID | Fitur | Aktor |
|---|---|---|
| F-PGM-01 | Kelola pengumuman tingkat sekolah + notifikasi | Admin |
| F-PGM-02 | Kirim pengumuman ke kelas binaan + notifikasi | Wali kelas |
| F-PGM-03 | Lihat pengumuman | Semua |
| F-KAL-01 | Kelola kalender akademik (ujian, libur, kegiatan) | Admin |
| F-KAL-02 | Lihat kalender akademik | Semua |

### 5.8 Mata pelajaran pilihan (`MPP`)

| ID | Fitur | Aktor |
|---|---|---|
| F-MPP-01 | Buat dan terbitkan survei minat; lihat hasil rekap | Admin |
| F-MPP-02 | Isi survei minat | Siswa |
| F-MPP-03 | Master paket mata pelajaran pilihan per tahun ajaran | Admin |
| F-MPP-04 | Pilih paket mata pelajaran | Siswa |
| F-MPP-05 | Pembagian kelas otomatis berdasarkan pilihan dan kapasitas | Admin |
| F-MPP-06 | Lihat tren peminat paket antar tahun | Admin |

### 5.9 Peminjaman fasilitas (`PJM`)

| ID | Fitur | Aktor |
|---|---|---|
| F-PJM-01 | Ajukan peminjaman ruangan/barang (waktu + keperluan) | Siswa, Guru |
| F-PJM-02 | Lihat status dan riwayat peminjaman | Siswa, Guru (miliknya), Admin (semua) |
| F-PJM-03 | Setujui/tolak pengajuan + notifikasi | Admin |
| F-PJM-04 | Konfirmasi pengembalian | Admin |

### 5.10 Pembayaran (`BYR`)

| ID | Fitur | Aktor |
|---|---|---|
| F-BYR-01 | Unggah rekap pembayaran dari yayasan (Excel) | Admin |
| F-BYR-02 | Lihat tagihan, nominal, jatuh tempo, status lunas/menunggak | Siswa, Wali murid, Wali kelas, Admin |
| F-BYR-03 | Notifikasi tunggakan ke siswa dan wali murid | Sistem |

### 5.11 Laporan (`LAP`) — Admin

| ID | Laporan |
|---|---|
| F-LAP-01 | Akademik (nilai) |
| F-LAP-02 | Kehadiran siswa |
| F-LAP-03 | Pelanggaran siswa |
| F-LAP-04 | Peminjaman fasilitas |
| F-LAP-05 | Status pembayaran |
| F-LAP-06 | Jurnal mengajar |
| F-LAP-07 | Perubahan jadwal |
| F-LAP-08 | Survei mata pelajaran pilihan |
| F-LAP-09 | Beban mengajar guru (jumlah jam dan sebaran, tanpa insentif) |
| F-LAP-10 | Statistik kesiswaan |
| F-LAP-11 | Ekspor data/laporan yang sedang ditampilkan ke berkas Excel |

Setiap laporan menerima parameter jenis laporan dan periode. Rincian kolom setiap laporan mengikuti lampiran laporan proyek; jika belum tersedia, **tanyakan** sebelum membuatnya.

## 6. Kebutuhan non-fungsional

| Aspek | Kebutuhan |
|---|---|
| Platform | Android 7.0 (API 24) sampai versi terbaru saat pengujian |
| Keamanan | Password di-hash bcrypt; JWT; otorisasi role + cakupan data di backend; akun yang diblokir tidak bisa login |
| Ketersediaan data | Jadwal, pengumuman, dan notifikasi tetap terbaca saat offline (cache) |
| Notifikasi | Perubahan jadwal dan pengumuman baru sampai ke penerima tanpa membuat admin menunggu |
| Bahasa | Antarmuka berbahasa Indonesia, waktu ditampilkan dalam WIB |
| Pengujian | Black Box Testing per ID fitur (BAB IV) |
