# Panduan prompt untuk anggota tim

Dokumen ini untuk **manusia**, bukan untuk AI. Tujuannya agar prompt setiap anggota menghasilkan kode yang konsisten.

## Prinsip

1. **Satu prompt = satu tugas kecil** (satu endpoint, satu layar, atau satu aturan bisnis). Hindari "buatkan modul jadwal".
2. **Sebut ID fitur PRD** (mis. `F-IZN-04`) dan dokumen yang relevan.
3. **Sebut sisi yang dikerjakan**: backend, Android, atau keduanya.
4. **Tulis kriteria selesai** yang bisa dicek.
5. Kalau AI bertanya soal "Pertanyaan terbuka", jangan jawab sendiri — bawa ke diskusi tim, lalu perbarui `docs/BUSINESS_RULES.md`.

## Template

```
Kerjakan F-<ID> — <nama fitur> di sisi <backend / android / keduanya>.

Konteks:
- Baca docs/PRD.md, docs/PERMISSIONS.md, docs/BUSINESS_RULES.md bagian <nomor>.
- Ikuti pola yang sudah ada di <file/modul contoh>.

Yang harus dibuat:
- <endpoint / layar / fungsi>

Kriteria selesai:
- <perilaku yang harus benar, termasuk kasus gagal>
- Test untuk <kasus> lulus.

Batasan:
- Jangan mengubah file di luar <folder>.
- Jika perlu mengubah schema.prisma, ubah file-nya saja dan tuliskan perintah migrasinya.
```

## Contoh

```
Kerjakan F-IZN-04 — verifikasi pengajuan izin di sisi backend.

Konteks:
- Baca docs/PERMISSIONS.md (baris "Lihat & verifikasi pengajuan izin")
  dan docs/BUSINESS_RULES.md bagian 4.3.
- Ikuti pola modul backend/src/modules/bookings untuk aksi approve/reject.

Yang harus dibuat:
- POST /api/v1/leave-requests/:id/approve
- POST /api/v1/leave-requests/:id/reject

Kriteria selesai:
- Hanya wali kelas dari kelas siswa tersebut yang bisa memverifikasi; guru lain 404.
- Izin yang bukan PENDING → 409 INVALID_STATE.
- Approve mengubah semua Attendance siswa pada sesi yang sudah ada dalam rentang
  tanggal menjadi EXCUSED dalam satu transaksi.
- Wali murid menerima notifikasi LEAVE_REQUEST.
- Test: sukses approve, sukses reject, bukan wali kelas, status bukan PENDING.

Batasan:
- Hanya mengubah backend/src/modules/attendance dan tests terkait.
```

## Sebelum mulai mengerjakan

- Pastikan sudah menarik perubahan terbaru dari repo (oleh kamu sendiri, bukan oleh AI).
- Kerjakan di branch fitur sendiri, mis. `feat/F-IZN-04-verifikasi-izin`.

## Setelah AI selesai

1. Baca ringkasan akhir dari AI (daftar file, test, perintah yang perlu dijalankan).
2. Jalankan sendiri perintah migrasi/git yang disebutkan jika memang perlu.
3. Review diff sebelum commit.
4. Jika ada aturan baru yang disepakati, perbarui dokumen di `docs/` dalam PR yang sama.
