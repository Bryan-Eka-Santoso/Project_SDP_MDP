# Arsitektur sistem

## 1. Gambaran umum

```
┌──────────────────────────┐        HTTPS + JWT        ┌──────────────────────────┐
│  Aplikasi Android        │ ───────────────────────▶  │  REST API (Express)      │
│  Kotlin, MVVM, Room      │ ◀───────────────────────  │  JavaScript + Prisma     │
└────────────┬─────────────┘        JSON               └──┬─────────┬──────────┬──┘
             │ push                                       │         │          │
             │                                            ▼         ▼          ▼
     ┌───────┴────────┐                            ┌─────────┐ ┌─────────┐ ┌────────────┐
     │ Firebase Cloud │ ◀───── kirim notifikasi ── │  MySQL  │ │Cloudinary│ │ ExcelJS    │
     │ Messaging      │                            │         │ │ (file)  │ │ (impor/    │
     └────────────────┘                            └─────────┘ └─────────┘ │  ekspor)   │
                                                                           └────────────┘
  Kalender perangkat ◀── CalendarContract (ditulis langsung oleh aplikasi Android)
```

- **Satu backend** (modular monolith) melayani semua role. Tidak ada web admin.
- **MySQL** adalah satu-satunya database. Skema: `backend/prisma/schema.prisma`.
- **Room** di Android hanya cache (jadwal, pengumuman, notifikasi), bukan sumber kebenaran.

Detail struktur folder: `backend/AGENTS.md` dan `android/AGENTS.md`.

## 2. Modul

| Modul backend | Feature Android | ID PRD |
|---|---|---|
| `auth`, `users` | `auth`, `profile`, `admin/user` | AUTH, MST-01..03 |
| `master` | `admin/*` | MST-04..12, NIL-01, PLG-01 |
| `schedules` | `schedule`, `admin/schedule` | JDW |
| `grades` | `grades` | NIL |
| `attendance` | `attendance` | ABS, IZN |
| `discipline` | `discipline` | PLG, BKP |
| `announcements` | `announcement` | PGM |
| `calendar` | `calendar` | KAL |
| `elective` | `elective` | MPP |
| `bookings` | `booking` | PJM |
| `bills` | `bill` | BYR |
| `reports` | `report` | LAP |
| `notifications` | `notification` | NTF |

Modul boleh memanggil **service** modul lain (mis. `schedules` memanggil `notifications.service`), tetapi tidak boleh mengakses route/controller modul lain.

## 3. Alur request

```
Request → authenticate (JWT) → authorize(role) → validate(Zod)
        → controller → service (policy cakupan data, aturan bisnis, Prisma)
        → response helper → JSON envelope
Error di mana pun → AppError → errorHandler → JSON envelope error
```

## 4. Alur notifikasi

```
Service (mis. simpan perubahan jadwal)
  └─ dalam transaksi: simpan data + baris Notification untuk setiap penerima
  └─ setelah commit: jobs/notificationDispatcher
        ├─ ambil DeviceToken penerima
        ├─ kirim FCM per batch ≤ 500 token
        └─ hapus token yang dilaporkan tidak valid oleh FCM
```

- Request admin selesai tanpa menunggu FCM.
- Daftar notifikasi di aplikasi selalu dibaca dari tabel `Notification`, jadi tetap lengkap walaupun push gagal.
- Payload FCM berisi `type` dan `referenceId` agar Android bisa membuka layar yang tepat.

## 5. Alur data di Android

```
Layar dibuka → ViewModel minta data → Repository
   ├─ (jika ada cache) emit data Room lebih dulu
   └─ panggil API → simpan ke Room (untuk data yang di-cache) → emit data terbaru
Error jaringan → tampilkan data cache + pesan "Tidak dapat terhubung"
```

Notifikasi perubahan jadwal yang diterima memicu refresh cache jadwal dan memperbarui event di kalender perangkat (jika siswa mengaktifkan sinkronisasi).

## 6. Keamanan

- Password bcrypt; JWT access token pendek + refresh token dirotasi dan disimpan (hash) di database agar bisa dicabut saat logout/blokir.
- Otorisasi role + cakupan data di backend (lihat `docs/PERMISSIONS.md`).
- Upload dibatasi tipe dan ukuran; file disimpan di Cloudinary, bukan di server.
- Secrets hanya lewat env. Daftar variabel ada di `backend/.env.example`:

| Variabel | Kegunaan |
|---|---|
| `DATABASE_URL` | Koneksi MySQL untuk Prisma |
| `PORT` | Port API |
| `JWT_ACCESS_SECRET`, `JWT_REFRESH_SECRET` | Kunci tanda tangan token |
| `JWT_ACCESS_TTL`, `JWT_REFRESH_TTL` | Masa berlaku token |
| `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET` | Upload file |
| `FIREBASE_SERVICE_ACCOUNT_PATH` | Kredensial Firebase Admin SDK |
| `TZ_DISPLAY` | `Asia/Jakarta`, untuk perhitungan tanggal lokal |

## 7. Lingkungan

| Lingkungan | Kegunaan |
|---|---|
| Lokal | Pengembangan tiap anggota; MySQL lokal atau Docker |
| Staging/demo | Server bersama untuk pengujian tim dan demo ke sekolah |

Android memilih base URL lewat build type (`debug` → lokal/staging, `release` → demo).
