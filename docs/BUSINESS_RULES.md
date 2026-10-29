# Aturan bisnis

Aturan di dokumen ini wajib diikuti dan diuji. Contoh angka di setiap bagian dipakai sebagai kasus uji. Bagian "Pertanyaan terbuka" di akhir **belum diputuskan**: jangan diimplementasikan dengan asumsi sendiri, tanyakan ke pengguna.

## 1. Periode akademik

- Hanya ada **satu** `AcademicYear` aktif dan **satu** `Semester` aktif pada satu waktu. Mengaktifkan yang baru menonaktifkan yang lama dalam satu transaksi.
- Kelas (`ClassGroup`), anggota kelas, wali kelas, dan paket mapel terikat tahun ajaran. Jadwal, penugasan mengajar, komponen nilai, nilai, sesi, dan presensi terikat semester.
- Data periode lama tidak dihapus; hanya bisa dibaca (kecuali oleh admin untuk koreksi).

## 2. Jadwal

### 2.1 Slot jadwal
- Satu `ScheduleSlot` = satu `TeachingAssignment` (guru × mapel × kelas) pada `dayOfWeek`, `startPeriod`–`endPeriod`, dan satu ruangan (`Facility` bertipe `ROOM`).
- `startPeriod ≤ endPeriod`, dan keduanya harus ada di master `LessonPeriod`.

### 2.2 Cek bentrok (F-JDW-03)
Dua slot pada semester yang sama **bentrok** jika harinya sama, rentang jamnya tumpang tindih, **dan** salah satu berikut sama:

1. guru, atau
2. kelas, atau
3. ruangan, atau
4. ada siswa yang menjadi anggota kedua kelas (mis. kelas reguler dan kelas paket).

Rentang `[a1, a2]` dan `[b1, b2]` tumpang tindih jika `a1 ≤ b2` **dan** `b1 ≤ a2`.

Contoh: slot A Senin jam 1–2, slot B Senin jam 2–3, guru sama → `1 ≤ 3` dan `2 ≤ 2` → **bentrok**. Slot C Senin jam 3–4 dengan guru sama vs A → `1 ≤ 4` tetapi `3 ≤ 2` salah → **tidak bentrok**.

- Saat simpan manual: tolak dengan `409 SCHEDULE_CONFLICT` dan sebutkan slot yang bentrok serta penyebabnya (guru/kelas/ruangan/siswa).
- Saat impor: periksa bentrok antarbaris di file **dan** terhadap data yang sudah ada.

### 2.3 Impor Excel (F-JDW-02, juga berlaku untuk impor siswa, guru, kelas, pembayaran)
- Validasi seluruh baris terlebih dahulu (format, referensi ke data master, bentrok).
- Jika **ada satu saja** baris tidak valid: tidak ada data yang disimpan; kembalikan `422 IMPORT_INVALID` berisi daftar `{ row, column, message }`. Nomor baris mengikuti nomor baris di Excel (baris header = 1).
- Jika semua valid: simpan dalam satu transaksi.
- Format kolom setiap jenis impor ditentukan oleh file contoh di `docs/import-templates/` (dibuat tim). Jangan mengarang format kolom.

### 2.4 Perubahan jadwal (F-JDW-04)
- Perubahan disimpan sebagai `ScheduleChange` untuk **satu tanggal** tertentu; `ScheduleSlot` aslinya tidak diubah.
- Jenis: `SUBSTITUTE` (guru pengganti), `RESCHEDULE` (pindah jam dan/atau ruangan pada tanggal yang sama), `CANCEL` (batal).
- `RESCHEDULE` dan `SUBSTITUTE` juga melalui cek bentrok untuk tanggal tersebut (termasuk dengan perubahan lain di tanggal itu).
- Tanggal harus berada dalam semester slot tersebut dan hari tanggalnya harus sama dengan `dayOfWeek` slot.
- Maksimal satu `ScheduleChange` per slot per tanggal; perubahan berikutnya memperbarui yang ada.
- Pihak yang dinotifikasi: semua siswa anggota kelas, wali murid mereka, guru asli, dan guru pengganti (jika ada).
- Tampilan jadwal untuk tanggal tertentu = slot asli yang ditimpa oleh perubahan pada tanggal itu.

## 3. Nilai

- `GradeComponent` dibuat per semester per mata pelajaran, dengan `weight` dalam persen (bilangan bulat).
- Total bobot komponen satu mapel dalam satu semester **harus tepat 100**. Guru tidak bisa menginput nilai sebelum total bobot = 100 (`409 GRADE_WEIGHTS_INVALID`).
- Nilai (`Score.value`) berada di rentang **0–100**, maksimal 2 angka desimal. Satu siswa memiliki maksimal satu nilai per komponen (input ulang = edit).
- **Nilai akhir dihitung**, tidak disimpan:

  `finalScore = Σ (value_komponen × weight_komponen) / 100`

  Contoh: Tugas 20% = 80, UH 30% = 75, UTS 20% = 70, UAS 30% = 90
  → (80×20 + 75×30 + 70×20 + 90×30) / 100 = (1600 + 2250 + 1400 + 2700) / 100 = **79,5**.

- Jika ada komponen yang belum dinilai, nilai akhir **tidak** ditampilkan sebagai angka; tampilkan status "belum lengkap" beserta komponen yang belum ada. Jangan menganggap nilai kosong sebagai 0.
- Mengubah bobot setelah nilai diinput diperbolehkan; nilai akhir otomatis mengikuti bobot baru.
- Pembulatan hanya untuk tampilan: 2 angka desimal.

## 4. Presensi, jurnal, dan izin

### 4.1 Sesi pelajaran
- `LessonSession` unik per (`scheduleSlotId`, `date`). Dibuat saat guru pertama kali mengisi presensi/jurnal untuk sesi itu.
- Sesi tidak boleh dibuat untuk tanggal di masa depan atau untuk slot yang dibatalkan (`CANCEL`) pada tanggal itu.
- Yang boleh mengisi: guru slot tersebut, atau guru pengganti jika ada `SUBSTITUTE` pada tanggal itu (guru asli tidak boleh).
- Presensi diisi untuk **semua** anggota kelas sekaligus bersama jurnal (materi) dalam satu transaksi.

### 4.2 Status awal presensi
Saat sesi dibuat, status awal setiap siswa:
1. `EXCUSED` jika ada `LeaveRequest` berstatus `APPROVED` yang rentang tanggalnya mencakup tanggal sesi;
2. selain itu `PRESENT`, lalu guru mengubah yang tidak hadir.

### 4.3 Pengajuan izin
- Diajukan wali murid untuk anaknya (`CHILD`) dengan `startDate`, `endDate` (`startDate ≤ endDate`), alasan, dan lampiran opsional.
- Diverifikasi oleh wali kelas dari kelas anak tersebut pada tahun ajaran aktif.
- Saat disetujui: semua `Attendance` siswa itu pada sesi yang **sudah ada** dalam rentang tanggal diubah menjadi `EXCUSED` (dalam transaksi yang sama). Sesi yang dibuat setelahnya mengikuti aturan 4.2.
- Izin yang sudah `APPROVED`/`REJECTED` tidak bisa diubah lagi. Wali murid dinotifikasi hasilnya.

### 4.4 Rekap kehadiran
Rekap per periode = jumlah sesi per status. Persentase kehadiran = `PRESENT / total sesi × 100`.
Contoh: 40 sesi, 36 hadir, 2 sakit, 1 izin, 1 alpa → 36/40 × 100 = **90%**.

## 5. Pelanggaran

- Dicatat dengan memilih `ViolationType`. Nilai `points` **disalin** ke `Violation` saat dicatat, sehingga perubahan bobot di master tidak mengubah riwayat.
- Total poin siswa = jumlah `points` semua pelanggaran pada tahun ajaran tersebut (dihitung, tidak disimpan).
- Contoh: terlambat (5) + atribut tidak lengkap (10) + terlambat lagi (5) = **20 poin**; jika bobot terlambat kemudian diubah menjadi 7, total tetap 20.
- Setiap pencatatan baru mengirim notifikasi ke wali murid siswa tersebut.

## 6. Buku penghubung dan catatan perkembangan

- `LiaisonNote` ditulis wali kelas untuk satu siswa binaan; dibaca wali murid siswa tersebut.
- `ProgressNote` ditulis guru untuk satu siswa pada satu mapel yang diampu, per semester.

## 7. Pengumuman dan kalender

- `SCHOOL`: dibuat admin, diterima semua user.
- `CLASS`: dibuat wali kelas untuk kelas binaannya, diterima siswa kelas tersebut dan wali muridnya.
- Setiap pengumuman baru mengirim notifikasi ke penerimanya. Lampiran opsional (Cloudinary).
- `AcademicEvent` memiliki `startDate` dan `endDate` (`startDate ≤ endDate`).

## 8. Mata pelajaran pilihan

1. Admin membuat `ElectiveSurvey` untuk tahun ajaran dan tingkat kelas tertentu → `PUBLISHED` → `CLOSED`. Siswa hanya bisa mengisi saat `PUBLISHED`, satu jawaban per siswa per survei (boleh diubah selama masih `PUBLISHED`).
2. Admin menyusun `SubjectPackage` (kumpulan mapel + kapasitas per kelas) berdasarkan rekap survei.
3. Siswa memilih **satu** paket per tahun ajaran selama periode pemilihan dibuka.
4. Pembagian kelas (F-MPP-05) per paket:
   - jumlah kelas `k = ⌈n / kapasitas⌉`;
   - siswa dibagi serata mungkin: setiap kelas berisi `⌊n / k⌋` siswa, dan `n mod k` kelas pertama mendapat 1 siswa tambahan.

   Contoh 1: 50 siswa, kapasitas 36 → k = ⌈50/36⌉ = 2 → 50/2 = 25 → **25 dan 25** (bukan 36 dan 14).
   Contoh 2: 80 siswa, kapasitas 36 → k = ⌈80/36⌉ = 3 → ⌊80/3⌋ = 26, sisa 80 mod 3 = 2 → **27, 27, 26**.
5. Hasil pembagian ditampilkan dulu sebagai pratinjau; baru disimpan sebagai `ClassGroup` + `ClassMember` setelah admin konfirmasi. Admin masih dapat memindahkan siswa secara manual.
6. Data survei dan pilihan tidak dihapus agar tren antar tahun bisa dibandingkan.

## 9. Peminjaman fasilitas

- Diajukan siswa atau guru: `facilityId`, `startAt`, `endAt`, `purpose`. Syarat: `startAt < endAt` dan `startAt` di masa depan.
- Alur status: `PENDING` → `APPROVED` / `REJECTED`; `APPROVED` → `RETURNED` (oleh admin).
- Saat admin menyetujui, cek tumpang tindih dengan peminjaman `APPROVED` lain untuk fasilitas yang sama: bentrok jika `startAt < other.endAt` **dan** `other.startAt < endAt`. Untuk ruangan, cek juga slot jadwal (memperhitungkan perubahan jadwal) pada waktu tersebut. Jika bentrok → `409 BOOKING_CONFLICT`.
- Pengajuan `PENDING` boleh saling tumpang tindih; yang disetujui lebih dulu yang berlaku.
- Contoh: A disetujui 08.00–10.00. B 10.00–11.00 → `08.00 < 11.00` dan `10.00 < 10.00` salah → **tidak bentrok**. C 09.30–10.30 → **bentrok**.
- Pemohon dinotifikasi setiap perubahan status.

## 10. Pembayaran

- Admin mengunggah rekap dari yayasan; data dicocokkan dengan NIS siswa dan kode tagihan, lalu dibuat/diperbarui (upsert) mengikuti aturan impor 2.3.
- Status tampil: `PAID` → "Lunas"; `UNPAID` dan belum jatuh tempo → "Belum dibayar"; `UNPAID` dan sudah lewat jatuh tempo → "Menunggak".
- Setelah impor, siswa dan wali murid yang memiliki tagihan menunggak menerima notifikasi `BILL_OVERDUE`.
- Tidak ada pembayaran di dalam aplikasi.

## 11. Akun

- Login dengan email + password. Password minimal 8 karakter, disimpan dengan bcrypt.
- Admin tidak bisa memblokir atau menghapus akunnya sendiri.
- Menghapus user yang sudah memiliki data akademik tidak diperbolehkan; gunakan blokir.

## Pertanyaan terbuka

Tanyakan ke pengguna sebelum mengimplementasikan bagian yang bergantung pada poin berikut.

| Kode | Pertanyaan |
|---|---|
| PO-01 | Apakah komponen penilaian sama untuk semua mapel dalam satu semester, atau bisa berbeda per mapel? (Dokumen ini sementara memakai per mapel.) |
| PO-02 | Bagaimana nilai remedial dicatat dan dihitung (Yosefu menampilkan nilai asli, remidi, dan nilai akhir)? |
| PO-03 | Apakah semua guru boleh mencatat pelanggaran siswa mana pun, atau hanya siswa di kelas yang diampu? |
| PO-04 | Batas waktu pengajuan izin: boleh untuk tanggal yang sudah lewat? Berapa hari ke belakang? |
| PO-05 | Peminjaman barang: apakah barang punya jumlah stok (bisa dipinjam beberapa orang sekaligus)? Bolehkah pemohon membatalkan pengajuan `PENDING`? |
| PO-06 | Setelah admin mereset password, apakah user wajib mengganti password saat login berikutnya? |
| PO-07 | Apakah buku penghubung satu arah (hanya wali kelas menulis) atau wali murid dapat membalas? |
| PO-08 | Urutan pengisian siswa ke kelas paket saat pembagian otomatis (abjad, NIS, atau acak)? |
| PO-09 | Daftar jam pelajaran (jam ke-n dan waktunya) serta hari sekolah (Senin–Jumat atau Senin–Sabtu). |
| PO-10 | Kolom dan format setiap dari 10 laporan (lampiran laporan proyek). |
