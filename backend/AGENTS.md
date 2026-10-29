# AGENTS.md — Backend (Express + Prisma)

Aturan ini melengkapi `AGENTS.md` di root. Aturan root tetap berlaku, termasuk larangan menjalankan git dan migrasi Prisma.

## Struktur folder

```
backend/
├── prisma/
│   ├── schema.prisma        ← sumber kebenaran skema database
│   └── seed.js
├── src/
│   ├── app.js               ← setup Express, middleware global, mount router
│   ├── server.js            ← listen port
│   ├── config/              ← baca env (satu tempat), konstanta
│   ├── lib/                 ← prisma client, fcm, cloudinary, excel helper
│   ├── middlewares/         ← authenticate, authorize, validate, upload, errorHandler
│   ├── utils/               ← AppError, response helper, pagination, date helper
│   ├── jobs/                ← pengiriman notifikasi asinkron
│   └── modules/
│       └── <module>/
│           ├── <module>.routes.js
│           ├── <module>.controller.js
│           ├── <module>.service.js
│           ├── <module>.validation.js   ← schema Zod
│           └── <module>.policy.js       ← cek cakupan data (opsional)
└── tests/
```

Daftar modul: `auth`, `users`, `master` (siswa, guru, kelas, mapel, fasilitas, komponen nilai, jenis pelanggaran), `schedules`, `grades`, `attendance` (sesi, jurnal, presensi, izin), `discipline` (pelanggaran, buku penghubung, catatan perkembangan), `announcements`, `calendar`, `elective` (survei, paket, pilihan), `bookings`, `bills`, `reports`, `notifications`. Jangan membuat modul baru tanpa persetujuan.

## Pembagian tanggung jawab layer

| Layer | Boleh | Tidak boleh |
|---|---|---|
| `routes` | Mendaftarkan path, memasang middleware `authenticate`, `authorize(...)`, `validate(schema)` | Logika bisnis |
| `controller` | Ambil data dari `req`, panggil service, kirim response lewat helper | Query Prisma, aturan bisnis |
| `service` | Aturan bisnis, query Prisma, transaksi, memicu notifikasi | Akses `req`/`res` |
| `policy` | Memastikan user boleh mengakses data tertentu (cakupan data) | Mengirim response |

## Aturan wajib

1. **Validasi semua input** (`body`, `params`, `query`) dengan Zod di `<module>.validation.js` melalui middleware `validate`.
2. **Otorisasi dua lapis**: `authorize(...roles)` di route, lalu cek cakupan data di service/policy sesuai `docs/PERMISSIONS.md`. Jangan pernah mempercayai ID siswa/kelas dari request tanpa memeriksa kepemilikannya.
3. **Error** dilempar sebagai `AppError(statusCode, code, message, details?)` dan ditangani di `errorHandler`. Jangan `res.status(...).json(...)` langsung di service.
4. **Response** selalu lewat helper (`sendSuccess`, `sendPaginated`) dengan format di `docs/API_CONVENTIONS.md`.
5. **Transaksi**: operasi yang mengubah lebih dari satu tabel (impor Excel, ACC izin yang mengubah presensi, pembagian kelas) wajib memakai `prisma.$transaction`.
6. **Jangan mengembalikan field sensitif** (`passwordHash`, token) ke client. Gunakan `select` eksplisit.
7. **Notifikasi**: simpan dulu ke tabel `Notification`, lalu kirim FCM lewat `jobs/` setelah transaksi selesai. Request API tidak boleh menunggu pengiriman FCM. Batch maksimal 500 token per panggilan.
8. **Upload file**: Multer `memoryStorage`, batasi ukuran dan tipe file, unggah ke Cloudinary, simpan `url` dan `publicId`.
9. **Impor Excel**: validasi semua baris dulu. Jika ada baris tidak valid, batalkan seluruh impor dan kembalikan daftar error per baris (nomor baris + alasan). Lihat `docs/BUSINESS_RULES.md`.
10. **Query daftar** wajib berpaginasi dan difilter periode (tahun ajaran/semester) bila datanya akademik.
11. **Bahasa**: `message` error untuk pengguna dalam bahasa Indonesia; `code` error dalam bahasa Inggris `UPPER_SNAKE_CASE`.

## Prisma

- Nama model `PascalCase` tunggal bahasa Inggris (`Student`), field `camelCase`. Tabel dan kolom fisik memakai nama PDM laporan dalam `snake_case` huruf kecil: `@@map("siswa")`, `@map("id_siswa")`, `@map("created_at")` (ADR-017, daftar di `docs/GLOSSARY.md`).
- Setiap model punya `id Int @id @default(autoincrement()) @map("id_<tabel>")`, `createdAt`, `updatedAt`. Tabel penghubung many-to-many memakai primary key gabungan (`@@id`).
- Enum ditulis di `schema.prisma` dalam `UPPER_SNAKE_CASE` dan harus sesuai `docs/GLOSSARY.md`.
- Setelah mengubah schema: jalankan `npx prisma format` dan `npx prisma validate` (boleh). **Jangan** menjalankan `migrate`/`db push`; tulis perintah yang disarankan di ringkasan akhir.
- Hindari N+1 query: gunakan `include`/`select` atau satu query agregat.

## Testing

- Jest + Supertest di `tests/`. Nama file `<module>.test.js`.
- Minimal uji untuk setiap endpoint baru: sukses, validasi gagal (400), tanpa token (401), role salah (403), data milik orang lain (403/404).
- Aturan bisnis di `docs/BUSINESS_RULES.md` diuji di level service, termasuk contoh angka yang tertulis di sana.

## Perintah

| Tujuan | Perintah |
|---|---|
| Install | `npm install` |
| Jalankan dev | `npm run dev` |
| Lint | `npm run lint` |
| Test | `npm test` |
| Generate Prisma Client | `npx prisma generate` |

Jika script di `package.json` berbeda dari tabel ini, ikuti `package.json` dan sebutkan perbedaannya di ringkasan.
