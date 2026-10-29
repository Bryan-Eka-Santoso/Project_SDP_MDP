# Konvensi API

Berlaku untuk semua endpoint backend dan semua pemanggil di Android.

## 1. URL

- Base path: `/api/v1`.
- Resource jamak, `kebab-case`: `/api/v1/schedule-slots`, `/api/v1/leave-requests`.
- Aksi yang bukan CRUD memakai sub-resource kata kerja: `POST /leave-requests/:id/approve`, `POST /facility-bookings/:id/return`, `POST /schedule-slots/import`.
- Data milik user yang login memakai `/me`: `GET /me`, `GET /me/notifications`.
- Data wali murid untuk anak tertentu: `GET /students/:studentId/scores` (backend memverifikasi relasi `StudentGuardian`).

| Method | Kegunaan | Status sukses |
|---|---|---|
| `GET` | Ambil data | 200 |
| `POST` | Buat data / jalankan aksi | 201 (buat), 200 (aksi) |
| `PATCH` | Ubah sebagian | 200 |
| `DELETE` | Hapus | 204 |

`PUT` tidak dipakai.

## 2. Format data

- JSON dengan key `camelCase`, sama dengan nama field Prisma.
- `DateTime` dikirim sebagai ISO 8601 UTC: `"2026-10-23T01:30:00.000Z"`.
- Tanggal saja: `"2026-10-23"`.
- Enum dikirim apa adanya (`"APPROVED"`), label Indonesia dibuat di Android.
- Uang dalam rupiah sebagai bilangan bulat (`1500000`).
- Nilai desimal (nilai siswa) sebagai number (`79.5`).

## 3. Response envelope

Sukses (satu objek):
```json
{ "success": true, "data": { "id": 12, "name": "XI-1" } }
```

Sukses (daftar berpaginasi):
```json
{
  "success": true,
  "data": [ { "id": 1 }, { "id": 2 } ],
  "meta": { "page": 1, "limit": 20, "total": 57, "totalPages": 3 }
}
```

Gagal:
```json
{
  "success": false,
  "error": {
    "code": "SCHEDULE_CONFLICT",
    "message": "Jadwal bentrok dengan slot lain.",
    "details": [ { "field": "teacherId", "message": "Guru sudah mengajar di XI-2 pada jam 2." } ]
  }
}
```

`message` berbahasa Indonesia dan aman ditampilkan langsung ke pengguna. `details` opsional.

## 4. Pagination, filter, sort

- `?page=1&limit=20` (default 20, maksimal 100).
- Filter memakai nama field: `?semesterId=3&classGroupId=5&status=PENDING`.
- Pencarian teks: `?q=budi`.
- Urutan: `?sort=createdAt&order=desc`.
- Endpoint daftar data akademik tanpa `semesterId`/`academicYearId` memakai periode aktif.

## 5. Kode error

| HTTP | `code` | Kapan |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Input tidak lolos Zod |
| 401 | `UNAUTHENTICATED` | Token tidak ada/tidak valid |
| 401 | `TOKEN_EXPIRED` | Access token kedaluwarsa (Android lalu memanggil refresh) |
| 401 | `INVALID_CREDENTIALS` | Email/password salah |
| 403 | `ACCOUNT_BLOCKED` | Akun diblokir |
| 403 | `FORBIDDEN` | Role tidak boleh mengakses fitur |
| 404 | `NOT_FOUND` | Data tidak ada **atau** di luar cakupan user |
| 409 | `SCHEDULE_CONFLICT` | Bentrok jadwal |
| 409 | `BOOKING_CONFLICT` | Bentrok peminjaman |
| 409 | `GRADE_WEIGHTS_INVALID` | Total bobot komponen ≠ 100 |
| 409 | `INVALID_STATE` | Aksi tidak sesuai status (mis. menyetujui izin yang sudah ditolak) |
| 409 | `DUPLICATE` | Melanggar data unik (email, NIS, dll.) |
| 413 | `FILE_TOO_LARGE` | File melebihi batas |
| 415 | `UNSUPPORTED_FILE_TYPE` | Tipe file tidak diizinkan |
| 422 | `IMPORT_INVALID` | Impor Excel ada baris tidak valid; `details` = `[{ row, column, message }]` |
| 500 | `INTERNAL_ERROR` | Error tak terduga (detail hanya di log server) |

Menambah kode error baru: tambahkan ke tabel ini.

## 6. Autentikasi

- `POST /auth/login` → `{ accessToken, refreshToken, user }`.
- `POST /auth/refresh` → `{ accessToken, refreshToken }` (refresh token dirotasi).
- `POST /auth/logout` → menghapus refresh token dan device token perangkat tersebut.
- Header: `Authorization: Bearer <accessToken>`.
- Masa berlaku: access token 15 menit, refresh token 7 hari. Nilai diatur lewat env.
- `POST /me/device-tokens` `{ token, platform: "ANDROID" }` untuk mendaftarkan token FCM.

## 7. Upload dan unduh

- Upload: `multipart/form-data`, nama field `file` (satu file) atau `files` (beberapa).
- Lampiran (izin, pengumuman): JPG, PNG, PDF; maksimal 5 MB per file.
- Impor Excel: `.xlsx`; maksimal 10 MB.
- Respons upload menyertakan `{ url, publicId, fileName, mimeType, size }`.
- Ekspor laporan: `GET /reports/:type/export?...` → file `.xlsx` dengan header `Content-Disposition: attachment`.

## 8. Contoh lengkap

`POST /api/v1/leave-requests`
```json
{ "studentId": 41, "startDate": "2026-10-26", "endDate": "2026-10-27", "reason": "Sakit demam", "attachmentUrl": "https://res.cloudinary.com/...", "attachmentPublicId": "sim/leave/abc123" }
```
Respons `201`:
```json
{ "success": true, "data": { "id": 88, "studentId": 41, "startDate": "2026-10-26", "endDate": "2026-10-27", "status": "PENDING", "createdAt": "2026-10-23T08:12:00.000Z" } }
```
