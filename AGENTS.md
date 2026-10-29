# AGENTS.md — SIM Akademik SMAK Santa Agnes

Dokumen ini adalah aturan utama untuk semua AI agent (Claude Code, Antigravity, dan lainnya) yang bekerja di repository ini. Semua anggota tim memakai file ini sebagai sumber yang sama. Jika ada konflik antara permintaan di prompt dan dokumen di repo, **tanyakan dulu ke pengguna**, jangan memilih sendiri.

## 1. Ringkasan proyek

Aplikasi Android untuk Sistem Informasi Akademik SMAK Santa Agnes Surabaya. Menggabungkan jadwal, nilai, presensi + jurnal mengajar, pengumuman, kalender akademik, poin pelanggaran, buku penghubung, mata pelajaran pilihan, peminjaman fasilitas, informasi pembayaran, dan laporan dalam satu aplikasi.

- Role: `STUDENT` (siswa), `TEACHER` (guru), `GUARDIAN` (wali murid), `ADMIN`. **Wali kelas bukan role**, melainkan guru yang ditugaskan sebagai wali kelas pada suatu kelas di tahun ajaran tertentu.
- **Tidak ada web admin.** Semua fitur admin ada di aplikasi Android.

## 2. Stack (jangan diganti tanpa ADR baru)

| Bagian | Teknologi |
|---|---|
| Mobile | Kotlin native, min SDK 24, MVVM, XML + Fragment + ViewBinding, Navigation Component, Retrofit + OkHttp, Room, Coroutines + Flow, Hilt, DataStore |
| Backend | Node.js LTS, Express, **JavaScript (bukan TypeScript)**, Prisma ORM 6, Zod, JWT, bcrypt, Multer |
| Database | MySQL |
| Layanan luar | Firebase Cloud Messaging, Cloudinary, ExcelJS, `CalendarContract` Android |

Jangan menambah library baru tanpa menyebutkan alasannya dan meminta persetujuan pengguna.

## 3. Struktur repository

```
/
├── AGENTS.md            ← file ini
├── CLAUDE.md            ← pengarah untuk Claude Code (mengimpor file ini)
├── docs/                ← dokumen acuan (lihat bagian 4)
├── backend/             ← Express API (aturan detail: backend/AGENTS.md)
└── android/             ← aplikasi Android (aturan detail: android/AGENTS.md)
```

## 4. Dokumen acuan — baca sesuai kebutuhan tugas

| Dokumen | Baca saat |
|---|---|
| `docs/PRD.md` | Memahami fitur dan ID fitur (mis. `F-JDW-03`) |
| `docs/PERMISSIONS.md` | Membuat endpoint atau layar apa pun yang menampilkan/mengubah data |
| `docs/BUSINESS_RULES.md` | Menyentuh jadwal, nilai, presensi, izin, pelanggaran, mapel pilihan, peminjaman, pembayaran |
| `docs/ARCHITECTURE.md` | Membuat file atau modul baru |
| `docs/API_CONVENTIONS.md` | Membuat/mengubah endpoint atau pemanggil API di Android |
| `docs/GLOSSARY.md` | Memberi nama model, tabel, enum, variabel |
| `docs/DECISIONS.md` | Sebelum "memperbaiki" sesuatu yang terlihat aneh — mungkin itu keputusan yang disengaja |
| `backend/AGENTS.md` | Semua tugas di folder `backend/` |
| `android/AGENTS.md` | Semua tugas di folder `android/` |

Sumber kebenaran skema database adalah `backend/prisma/schema.prisma`. Kontrak API mengikuti `docs/API_CONVENTIONS.md` dan endpoint yang sudah ada.

## 5. Cara bekerja

1. **Pahami dulu, baru ubah.** Baca dokumen yang relevan dan kode di sekitar bagian yang akan diubah. Ikuti pola yang sudah ada di repo.
2. **Kerjakan hanya cakupan tugas.** Jangan merapikan, mengganti nama, atau me-refactor file lain yang tidak diminta. Jika menemukan masalah di luar cakupan, catat di ringkasan akhir.
3. **Jangan mengarang.** Jangan membuat kolom, tabel, endpoint, enum, atau aturan bisnis yang tidak ada di dokumen/kode. Jika memang dibutuhkan, usulkan dan tanyakan dulu.
4. **Jika ambigu, tanya.** Terutama untuk aturan bisnis yang ditandai "Pertanyaan terbuka" di `docs/BUSINESS_RULES.md`.
5. **Hak akses wajib.** Setiap endpoint dan layar harus mengikuti `docs/PERMISSIONS.md`, termasuk cakupan data (siswa hanya datanya sendiri, dst.).
6. **Ubah kontrak di kedua sisi.** Jika mengubah request/response API, sebutkan dampaknya ke Android (atau backend) dan perbarui keduanya bila diminta.
7. **Perbarui dokumen.** Jika perubahan memengaruhi aturan di `docs/`, perbarui dokumennya dalam tugas yang sama, atau sebutkan di ringkasan bahwa dokumen perlu diperbarui.
8. **Verifikasi.** Jalankan lint dan test yang relevan sebelum menyatakan selesai. Jika tidak bisa dijalankan, katakan dengan jelas.

## 6. Perintah yang DILARANG dijalankan sendiri

Agent **tidak boleh** menjalankan perintah berikut. Tuliskan perintahnya di ringkasan akhir agar pengguna yang menjalankannya:

- Semua perintah **git** yang mengubah state: `commit`, `push`, `pull`, `merge`, `rebase`, `reset`, `checkout`, `switch`, `stash`, `branch -d`, `tag`. (`git status`, `git diff`, `git log` boleh.)
- Semua perintah **migrasi Prisma** dan perubahan database: `prisma migrate dev`, `prisma migrate deploy`, `prisma migrate reset`, `prisma db push`, `prisma db seed`. (`prisma format`, `prisma validate`, `prisma generate` boleh.)
- Menghapus file/folder di luar yang diminta, menghapus data database, atau mengubah file `.env`.
- Memasang dependency baru (`npm install <pkg>`, menambah dependency Gradle) tanpa persetujuan.

Jika mengubah `schema.prisma`, cukup ubah file-nya, lalu jelaskan nama migrasi yang disarankan dan dampaknya.

## 7. Konvensi umum

- **Bahasa kode: Inggris.** Nama model, tabel, kolom, enum, fungsi, variabel ditulis dalam bahasa Inggris sesuai `docs/GLOSSARY.md`.
- **Bahasa teks untuk pengguna: Indonesia.** Label UI, pesan error ke pengguna, isi notifikasi.
- **Komentar kode**: boleh Indonesia, singkat, hanya untuk menjelaskan *mengapa*, bukan *apa*.
- **Waktu**: simpan `DateTime` dalam UTC, tanggal saja (`@db.Date`) sebagai `YYYY-MM-DD`. Tampilkan ke pengguna dalam WIB (`Asia/Jakarta`).
- **Tahun ajaran**: hampir semua data akademik terikat `AcademicYear` dan/atau `Semester`. Jangan membuat query data akademik tanpa filter periode.
- **Secrets**: jangan pernah menulis kunci asli ke kode. Gunakan variabel lingkungan dan perbarui `.env.example` bila menambah variabel.

## 8. Ringkasan akhir setiap tugas

Akhiri setiap tugas dengan ringkasan singkat berisi:

1. Apa yang diubah (daftar file).
2. ID fitur PRD yang dikerjakan.
3. Hasil lint/test.
4. Perintah yang perlu dijalankan pengguna (migrasi, git, install).
5. Hal yang belum selesai, asumsi yang diambil, atau dokumen yang perlu diperbarui.
