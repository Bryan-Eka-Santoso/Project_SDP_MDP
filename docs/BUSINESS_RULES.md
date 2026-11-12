# Aturan bisnis

Aturan di dokumen ini wajib diikuti dan diuji. Contoh angka di setiap bagian dipakai sebagai kasus uji. Bagian "Pertanyaan terbuka" di akhir **belum diputuskan**: jangan diimplementasikan dengan asumsi sendiri, tanyakan ke pengguna.

## 1. Periode akademik

- Hanya ada **satu** `AcademicYear` aktif dan **satu** `Semester` aktif pada satu waktu. Mengaktifkan yang baru menonaktifkan yang lama dalam satu transaksi.
- Kelas (`ClassGroup`), anggota kelas, wali kelas, dan paket mapel terikat tahun ajaran. Jadwal, penugasan mengajar, komponen nilai, nilai, sesi, dan presensi terikat semester.
- Data periode lama tidak dihapus; hanya bisa dibaca (kecuali oleh admin untuk koreksi).
- `Teacher.isHomeroomTeacher` = guru tersebut menjadi `homeroomTeacherId` di sebuah `ClassGroup` pada tahun ajaran aktif. Dihitung ulang dalam transaksi yang sama setiap kali wali kelas diatur/diubah, kelas dihapus, atau tahun ajaran aktif diganti. Tidak bisa diubah manual lewat API.

## 2. Jadwal

### 2.1 Slot jadwal
- Satu `ScheduleSlot` = satu `TeachingAssignment` (guru × mapel × kelas × semester) pada `dayOfWeek`, `startTime`–`endTime`, dan satu ruangan (`Facility` bertipe `ROOM`).
- `startTime < endTime`. (Master `LessonPeriod` belum dipakai sampai PO-09 dijawab; lihat ADR-017.)
- Ruangan slot harus `Facility` bertipe `ROOM`.
- Slot yang sudah memiliki `LessonSession` atau `ScheduleChange` tidak bisa dihapus (`409 INVALID_STATE`) agar riwayat presensi, jurnal, dan perubahan jadwal tidak hilang.

### 2.2 Cek bentrok (F-JDW-03)
Dua slot pada semester yang sama **bentrok** jika harinya sama, rentang jamnya tumpang tindih, **dan** salah satu berikut sama:

1. guru, atau
2. kelas, atau
3. ruangan, atau
4. ada siswa yang menjadi anggota kedua kelas (mis. kelas reguler dan kelas paket).

Karena slot memakai jam mulai–selesai, rentang `[a1, a2)` dan `[b1, b2)` tumpang tindih jika `a1 < b2` **dan** `b1 < a2` (slot yang bersambung tidak bentrok).

Contoh: slot A Senin 07.00–08.30, slot B Senin 08.00–09.30, guru sama → `07.00 < 09.30` dan `08.00 < 08.30` → **bentrok**. Slot C Senin 08.30–10.00 dengan guru sama vs A → `08.30 < 08.30` salah → **tidak bentrok**.

- Saat simpan manual: tolak dengan `409 SCHEDULE_CONFLICT` dan sebutkan slot yang bentrok serta penyebabnya (guru/kelas/ruangan/siswa). `details` berisi satu entri per slot per penyebab: `{ field, reason: TEACHER|CLASS|ROOM|STUDENT, slotId, message }`.
- Saat impor: periksa bentrok antarbaris di file **dan** terhadap data yang sudah ada.

### 2.3 Impor Excel (F-JDW-02, juga berlaku untuk impor siswa, guru, kelas, pembayaran)
- Validasi seluruh baris terlebih dahulu (format, referensi ke data master, bentrok).
- Jika **ada satu saja** baris tidak valid: tidak ada data yang disimpan; kembalikan `422 IMPORT_INVALID` berisi daftar `{ row, column, message }`. Nomor baris mengikuti nomor baris di Excel (baris header = 1). `column` = teks header kolom, atau `null` jika kesalahan menyangkut seluruh baris atau file. Semua kesalahan satu file dikumpulkan sekaligus (bukan berhenti di yang pertama), dengan batas 200 kesalahan yang ditampilkan; `message` menyebut jumlah totalnya.
- Jika semua valid: simpan dalam satu transaksi.
- Format kolom setiap jenis impor ditentukan oleh file contoh di `docs/import-templates/` (dibuat tim; penjelasannya di `docs/import-templates/README.md`). Jangan mengarang format kolom.
- Dibaca **lembar pertama**; baris 1 = header, urutan kolom bebas, kolom tambahan diabaikan. Maksimal 5000 baris data per file (impor pembayaran: 20000); baris kosong dilewati.
- Kolom kode/NIP/NIS harus bertipe **Teks**: Excel membulatkan angka di atas 15 digit (NIP 18 digit rusak), sehingga angka seperti itu ditolak dengan petunjuk mengubah format kolom.

#### 2.3.1 Impor jadwal (F-JDW-02)
- `POST /schedule-slots/import`, hanya Admin. Form multipart: `file` (.xlsx, maksimal 10 MB) dan `semesterId` (opsional; tanpa itu memakai semester aktif, `409 INVALID_STATE` jika tidak ada). Boleh ke semester yang tidak aktif.
- Kolom: `Kelas`, `Kode Mapel`, `NIP Guru`, `Hari` (Senin–Sabtu), `Jam Mulai`, `Jam Selesai`, `Ruangan`. Jam berupa sel jam Excel atau teks `HH:mm` / `HH.mm`.
- Referensi dicocokkan tanpa membedakan huruf besar/kecil: kelas menurut nama pada tahun ajaran semester tersebut, mapel menurut kode, guru menurut NIP (spasi diabaikan), ruangan menurut nama `Facility` bertipe `ROOM`. Nama yang cocok dengan lebih dari satu data master ditolak karena tidak bisa dipastikan.
- Impor **hanya merujuk penugasan guru yang sudah ada** (F-MST-10: guru × mapel × kelas × semester) dan tidak membuat penugasan baru, supaya salah ketik NIP atau kode tidak diam-diam menugaskan guru yang keliru. Baris tanpa penugasan ditolak dengan pesan yang menunjuk ke "Atur guru pengajar".
- Slot dari file **ditambahkan** ke jadwal semester; slot yang sudah ada tidak diubah atau dihapus. Cek bentrok sesuai 2.2 terhadap slot yang sudah ada **dan** antarbaris di file (dilaporkan pada baris yang lebih bawah, menyebut nomor baris pasangannya). Kolom yang ditandai mengikuti penyebab: `NIP Guru` (guru), `Kelas` (kelas atau siswa yang sama), `Ruangan` (ruangan).
- Jadwal yang persis sama (penugasan, hari, jam, ruangan) dengan slot yang sudah ada atau baris di atasnya dilaporkan satu kali sebagai duplikat (`column` null), bukan sebagai bentrok guru + kelas + ruangan sekaligus.
- Semua baris valid → semua slot disimpan dalam satu transaksi; respons `201` berisi `{ semesterId, imported }`. Tidak ada notifikasi.

### 2.4 Perubahan jadwal (F-JDW-04)
- Perubahan disimpan sebagai `ScheduleChange` untuk **satu tanggal** tertentu; `ScheduleSlot` aslinya tidak diubah.
- Jenis: `SUBSTITUTE` (guru pengganti), `RESCHEDULE` (pindah jam dan/atau ruangan pada tanggal yang sama), `CANCEL` (batal).
- `RESCHEDULE` dan `SUBSTITUTE` juga melalui cek bentrok untuk tanggal tersebut (termasuk dengan perubahan lain di tanggal itu).
- Tanggal harus berada dalam semester slot tersebut dan hari tanggalnya harus sama dengan `dayOfWeek` slot.
- Maksimal satu `ScheduleChange` per slot per tanggal; perubahan berikutnya memperbarui yang ada.
- `SUBSTITUTE`: guru pengganti wajib berbeda dari guru asli dan akunnya aktif. `RESCHEDULE`: wajib mengisi ruangan baru dan/atau jam mulai+selesai baru (keduanya bersamaan), dan hasilnya tidak boleh sama persis dengan slot asli; ruangan baru harus bertipe `ROOM`. Kolom yang tidak relevan dengan jenisnya dikosongkan.
- Cek bentrok tanggal: kandidat (jam/ruangan baru atau guru pengganti) dibandingkan dengan slot lain di hari itu **setelah** perubahan mereka pada tanggal yang sama diterapkan (slot yang `CANCEL` diabaikan, slot `RESCHEDULE` memakai jam/ruangan barunya, slot `SUBSTITUTE` memakai guru penggantinya). `CANCEL` tidak dicek.
- Menghapus `ScheduleChange` = jadwal tanggal itu kembali seperti slot asli; pihak yang sama dinotifikasi ("kembali sesuai jadwal biasa").
- Pihak yang dinotifikasi: semua siswa anggota kelas, wali murid mereka, guru asli, dan guru pengganti (jika ada). Jika perubahan diperbarui dan guru pengganti sebelumnya diganti, guru pengganti lama juga dinotifikasi. Penerima dihitung saat pengiriman; notifikasi tidak disimpan di server (ADR-010).
- Tampilan jadwal untuk tanggal tertentu = slot asli yang ditimpa oleh perubahan pada tanggal itu.

### 2.4.1 Melihat jadwal (F-JDW-05)
- `GET /schedule` (siswa, guru, wali murid; admin memakai master jadwal `/schedule-slots` sehingga `403`). Query: `dateFrom`, `dateTo` (`YYYY-MM-DD`; tanpa keduanya = tujuh hari mulai hari ini WIB; `dateTo` wajib disertai `dateFrom`; maksimal 31 hari), `studentId`, `classGroupId`.
- Cakupan: **siswa** = jadwal semua kelasnya (reguler dan paket); `studentId` boleh diisi hanya dengan id dirinya sendiri. **Wali murid** = jadwal kelas anak; `studentId` anak wajib (di luar anaknya `404`). **Guru** = jadwal mengajarnya (slot yang ia ajar, termasuk yang digantikan orang lain pada tanggal itu, ditambah slot guru lain yang ia gantikan pada tanggal itu). **Wali kelas** = guru dengan `classGroupId` kelas binaannya pada tahun ajaran aktif (di luar itu `404`; ditentukan dari `homeroomTeacherId`, bukan flag `isHomeroomTeacher`). `classGroupId` ditolak (`400`) untuk siswa dan wali murid; `studentId` ditolak untuk guru.
- Slot mingguan diperluas menjadi satu entri per tanggal, hanya untuk tanggal di dalam semester slot itu. Respons: daftar `{ date, slotId, dayOfWeek, startTime, endTime, room, teacher, subject, classGroup, change, original? }`, urut tanggal lalu jam mulai. `startTime`, `endTime`, `room`, `teacher` adalah nilai **efektif** setelah perubahan; jika ada perubahan, `change = { id, type, description }` dan `original` memuat jam, ruangan, dan guru aslinya. Slot yang dibatalkan tetap muncul dengan `change.type = CANCEL` agar pengguna tahu pelajarannya tidak ada.
- Hari libur di kalender akademik tidak menyembunyikan entri; kalender dan jadwal ditampilkan terpisah.

### 2.5 Tugas dari guru (F-JDW-08, F-JDW-09)
- Guru membuat `Task` pada `TeachingAssignment` miliknya; pilihan penugasan (kelas + mapel) diambil dari `GET /me/teaching-assignments` (semester aktif, hanya miliknya). Tugas terlihat oleh siswa anggota kelas penugasan itu, dikelompokkan per mapel.
- `dueDate` saat dibuat tidak boleh sebelum hari ini (WIB).
- Tidak ada pengumpulan atau penilaian tugas. Tanda "selesai" milik siswa hanya disimpan di perangkat (Room) dan tidak dikirim ke server.
- Tugas baru → notifikasi `TASK` ke siswa kelas tersebut. Ubah dan hapus tidak mengirim notifikasi.
- To-do pribadi siswa (F-JDW-07) terpisah dari `Task`: dibuat siswa sendiri dan hanya ada di perangkat.
- Endpoint: `POST /tasks`, `PATCH /tasks/:id`, `DELETE /tasks/:id` (hanya guru; di luar penugasannya sendiri: `PATCH`/`DELETE` → `404`, `POST` dengan `teachingAssignmentId` yang bukan miliknya → `400` pada field itu). `GET /tasks` dan `GET /tasks/:id` untuk guru (tugas penugasannya) dan siswa (tugas kelasnya); wali murid dan admin `403`; tugas di luar cakupan → `404`.
- `POST /tasks`: `teachingAssignmentId`, `title` (1–150 karakter), `description` (opsional, maksimal 2000; kosong disimpan `null`), `dueDate` (`YYYY-MM-DD`). Penugasan tidak bisa dipindah dengan `PATCH`. Pada `PATCH`, tenggat tidak dicek terhadap hari ini (aturan itu hanya saat dibuat).
- `GET /tasks`: berpaginasi, urut tenggat lalu id, default semester aktif; filter `semesterId`, `teachingAssignmentId`, `subjectId`, `classGroupId`, `dueFrom`, `dueTo`. Tiap item memuat `teachingAssignment` (guru, mapel, kelas) agar klien bisa mengelompokkan per mapel. Penerima notifikasi tugas baru hanya siswa kelas penugasan itu (wali murid tidak).

## 3. Nilai

- `GradeComponent` dibuat oleh guru pengajar untuk satu `TeachingAssignment` (guru × mapel × kelas × semester), dengan `weight` dalam persen (bilangan bulat). Guru lain yang mengajar mapel sama di kelas lain mengatur komponennya sendiri (ADR-018).
- Total bobot komponen satu penugasan **harus tepat 100**. Guru tidak bisa menginput nilai sebelum total bobot = 100 (`409 GRADE_WEIGHTS_INVALID`).
- `Score.gradeComponentId` wajib milik `Score.teachingAssignmentId` yang sama.
- Nilai (`Score.value`) berada di rentang **0–100**, maksimal 2 angka desimal. Satu siswa memiliki maksimal satu nilai per komponen (input ulang = edit).
- **Nilai akhir dihitung**, tidak disimpan:

  `finalScore = Σ (value_komponen × weight_komponen) / 100`

  Contoh: Tugas 20% = 80, UH 30% = 75, UTS 20% = 70, UAS 30% = 90
  → (80×20 + 75×30 + 70×20 + 90×30) / 100 = (1600 + 2250 + 1400 + 2700) / 100 = **79,5**.

- Jika ada komponen yang belum dinilai, nilai akhir **tidak** ditampilkan sebagai angka; tampilkan status "belum lengkap" beserta komponen yang belum ada. Jangan menganggap nilai kosong sebagai 0.
- Mengubah bobot setelah nilai diinput diperbolehkan; nilai akhir otomatis mengikuti bobot baru.
- Pembulatan hanya untuk tampilan: 2 angka desimal.
- **Predikat** diambil dari `GradePredicate` tahun ajaran semester tersebut: label dengan `minScore` tertinggi yang ≤ nilai akhir (sebelum pembulatan). Skala diatur admin (F-NIL-06).
  Contoh skala A 90, B 80, C 70, D 0: nilai akhir 79,5 → **C**; 80 → **B**; 100 → **A**.
- Skala predikat satu tahun ajaran wajib punya baris `minScore = 0`, label unik, dan `minScore` unik di rentang 0–100. Jika nilai akhir belum lengkap, predikat tidak ditampilkan.

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
- Tidak ada batas maksimum, ambang sanksi, atau label status poin (keputusan 07/11/2026). Layar pelanggaran hanya menampilkan total poin dan riwayat kejadian.

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

- Diajukan siswa atau guru: `facilityId`, `bookingDate`, `startTime`, `endTime`, `purpose` (satu hari). Syarat: `startTime < endTime` dan waktu mulai di masa depan. Pada rumus di bawah, `startAt`/`endAt` = `bookingDate` + jam.
- Alur status: `PENDING` → `APPROVED` / `REJECTED`; `APPROVED` → `RETURNED` (oleh admin).
- Saat admin menyetujui, cek tumpang tindih dengan peminjaman `APPROVED` lain untuk fasilitas yang sama: bentrok jika `startAt < other.endAt` **dan** `other.startAt < endAt`. Untuk ruangan, cek juga slot jadwal (memperhitungkan perubahan jadwal) pada waktu tersebut. Jika bentrok → `409 BOOKING_CONFLICT`.
- Pengajuan `PENDING` boleh saling tumpang tindih; yang disetujui lebih dulu yang berlaku.
- Contoh: A disetujui 08.00–10.00. B 10.00–11.00 → `08.00 < 11.00` dan `10.00 < 10.00` salah → **tidak bentrok**. C 09.30–10.30 → **bentrok**.
- Pemohon dinotifikasi setiap perubahan status.

## 10. Pembayaran

- Admin mengunggah rekap dari yayasan; data dicocokkan dengan NIS siswa dan kode tagihan, lalu dibuat/diperbarui (upsert) mengikuti aturan impor 2.3.
- `POST /bills/import`, hanya Admin (form multipart `file`, .xlsx, maksimal 10 MB). Kolom: `NIS`, `Kode Tagihan`, `Jenis Pembayaran`, `Nominal`, `Jatuh Tempo`, `Status`, `Tanggal Bayar` (opsional; kolomnya boleh tidak ada). Format di `docs/import-templates/pembayaran.xlsx`. Maksimal 20000 baris data per file.
- Kunci tagihan = NIS + kode tagihan (huruf besar/kecil diabaikan). Jika sudah ada: diperbarui hanya bila ada isi yang berbeda; jika belum: dibuat. Dua baris dengan NIS + kode tagihan yang sama dalam satu file ditolak. Tagihan yang tidak ada di file tidak diubah atau dihapus. Respons `201` berisi `{ created, updated, unchanged, notifiedStudents }`.
- `Nominal`: rupiah bulat lebih dari 0 (sel angka, atau teks `850000` / `850.000` / `Rp 850.000`), maksimal 9.999.999.999. `Jatuh Tempo` dan `Tanggal Bayar`: sel tanggal atau teks `YYYY-MM-DD` / `DD/MM/YYYY`. `Status`: `Lunas` → `PAID`; `Belum Dibayar`, `Belum Lunas`, atau `Menunggak` → `UNPAID`. `Tanggal Bayar` hanya boleh diisi untuk tagihan lunas (boleh kosong).
- NIS yang tidak ditemukan di data siswa ditolak. Kode tagihan dan jenis pembayaran maksimal 50 karakter.
- Status tampil: `PAID` → "Lunas"; `UNPAID` dan belum jatuh tempo → "Belum dibayar"; `UNPAID` dan sudah lewat jatuh tempo → "Menunggak".
- Setelah impor, siswa dan wali murid yang memiliki tagihan menunggak menerima notifikasi `BILL_OVERDUE`. Penerima = siswa yang **ada di file impor** dan punya tagihan `UNPAID` yang sudah lewat jatuh tempo (tanggal WIB; jatuh tempo hari ini belum menunggak), beserta semua wali muridnya. Satu notifikasi per siswa (isinya merangkum tagihan menunggak siswa itu). Impor yang tidak mengubah data apa pun (tidak ada tagihan baru atau berubah) tidak mengirim notifikasi, agar mengunggah file yang sama dua kali tidak mengulang notifikasi ke orang tua.
- Melihat tagihan: `GET /students/:studentId/bills` (siswa sendiri, wali murid untuk anaknya, wali kelas untuk siswa kelas binaannya pada tahun ajaran aktif, admin untuk semua; di luar cakupan → `404`) dan `GET /bills` (admin untuk semua; wali kelas otomatis dibatasi ke kelas binaannya, guru yang bukan wali kelas `403`). Setiap tagihan membawa `status` (`PAID`/`UNPAID`) dan `isOverdue` (status tampil "Menunggak" = `UNPAID` dan `isOverdue`).
- Tidak ada pembayaran di dalam aplikasi.

## 11. Akun

- Login dengan email + password. Password minimal 8 karakter, disimpan dengan bcrypt.
- Password awal setiap akun ditentukan admin saat membuat akun. User boleh mengganti password sendiri kapan saja; tidak wajib saat login pertama maupun setelah reset (PO-06).
- Tidak ada lupa password mandiri. User yang lupa menghubungi admin; admin mereset password (F-MST-03) dan reset itu mencabut semua refresh token user tersebut.
- Mengganti password sendiri mencabut sesi di perangkat lain; perangkat yang dipakai menerima token baru.
- Pembatasan percobaan (dalam memori, jendela 15 menit; lewat batas → `429 TOO_MANY_REQUESTS` + header `Retry-After`): login yang gagal dibatasi 8 kali per kombinasi IP + email (login berhasil mengosongkannya) dan 100 kali per IP (longgar karena jaringan sekolah dipakai bersama); `POST /auth/refresh` 300 request per IP; `PATCH /me/password` 10 kali gagal per akun. Hanya percobaan gagal yang dihitung, jadi pengguna sah tidak terkunci oleh orang lain dari IP lain. Di belakang reverse proxy, isi `TRUST_PROXY` agar IP klien terbaca benar.
- Admin tidak bisa memblokir atau menghapus akunnya sendiri.
- Menghapus user yang sudah memiliki data akademik tidak diperbolehkan; gunakan blokir.

## 12. Laporan (F-LAP-*)
- Hanya Admin. `GET /reports/:type` (tampilan berpaginasi) dan `GET /reports/:type/export` (F-LAP-11, berkas `.xlsx` berisi data dan filter yang sama dengan tampilan, tanpa paginasi). `:type` tidak dikenal → `404`. Ekspor lebih dari 20000 baris → `422 EXPORT_TOO_LARGE` (persempit filter). Nama berkas `laporan-<type>-<tanggal WIB>.xlsx`.
- Tanpa `semesterId` laporan memakai semester aktif.
- **`schedule-changes` (F-LAP-07), kolom usulan:** tanggal, hari, kelas, mata pelajaran, guru asli, jenis (Ganti guru / Pindah jadwal / Dibatalkan), guru pengganti, jam asli, jam baru, ruangan asli, ruangan baru, keterangan. Filter: `semesterId`, `classGroupId`, `teacherId` (guru asli atau pengganti), `type`, `dateFrom`, `dateTo`. Urut tanggal.
- **`teaching-load` (F-LAP-09), kolom usulan:** per guru: nama, NIP, jumlah pertemuan per minggu, total jam per minggu (menit dibagi 60, dua desimal), jumlah kelas, mata pelajaran, dan jam per hari Senin sampai Sabtu. Filter: `semesterId`, `teacherId`. Dihitung dari jadwal master semester (slot mingguan); pengganti dan pembatalan per tanggal tidak mengubah angka. Semua guru tampil, termasuk yang belum punya jadwal (0 jam). Tanpa insentif atau tarif.
- Laporan lain (F-LAP-01..06, 08, 10) belum dibuat; kolomnya menunggu keputusan PO-10. Laporan baru cukup menambah satu berkas definisi dan mendaftarkannya di `reports.service.js`.

## Pertanyaan terbuka

Tanyakan ke pengguna sebelum mengimplementasikan bagian yang bergantung pada poin berikut.

| Kode | Pertanyaan |
|---|---|
| ~~PO-01~~ | **Terjawab:** komponen penilaian diatur guru per penugasan (guru × mapel × kelas × semester), ADR-018. |
| PO-02 | Bagaimana nilai remedial dicatat dan dihitung (Yosefu menampilkan nilai asli, remidi, dan nilai akhir)? |
| PO-03 | Apakah semua guru boleh mencatat pelanggaran siswa mana pun, atau hanya siswa di kelas yang diampu? |
| PO-04 | Batas waktu pengajuan izin: boleh untuk tanggal yang sudah lewat? Berapa hari ke belakang? |
| PO-05 | Peminjaman barang: apakah barang punya jumlah stok (bisa dipinjam beberapa orang sekaligus)? Bolehkah pemohon membatalkan pengajuan `PENDING`? |
| ~~PO-06~~ | **Terjawab:** tidak wajib; user boleh mengganti password kapan saja (07/11/2026). |
| PO-07 | Apakah buku penghubung satu arah (hanya wali kelas menulis) atau wali murid dapat membalas? |
| PO-08 | Urutan pengisian siswa ke kelas paket saat pembagian otomatis (abjad, NIS, atau acak)? |
| PO-09 | Daftar jam pelajaran (jam ke-n dan waktunya) serta hari sekolah (Senin–Jumat atau Senin–Sabtu). |
| PO-10 | Kolom dan format setiap dari 10 laporan (lampiran laporan proyek). Kolom F-LAP-07 dan F-LAP-09 sudah dibuat sebagai **usulan** (bagian 12), menunggu persetujuan tim. |
