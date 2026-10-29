# SIM Akademik SMAK Santa Agnes

Monorepo aplikasi Sistem Informasi Akademik berbasis Android.

| Folder | Isi |
|---|---|
| `backend/` | REST API Express + Prisma + MySQL |
| `android/` | Aplikasi Android Kotlin |
| `docs/` | PRD, aturan bisnis, hak akses, konvensi API, glosarium, keputusan arsitektur |

## Cara menjalankan proyek (Windows)

Prasyarat: [Git](https://git-scm.com/download/win), [Node.js](https://nodejs.org/) versi LTS (minimal 22.9), MySQL 8 yang sudah berjalan, dan [Android Studio](https://developer.android.com/studio).

1. **Clone repository** (PowerShell atau Git Bash):
   ```powershell
   git clone <url-repository> sim-santa-agnes
   cd sim-santa-agnes
   ```
2. **Pasang dependency backend:**
   ```powershell
   cd backend
   npm install
   ```
3. **Siapkan file environment.** Salin `.env.example` menjadi `.env`, lalu isi nilainya (minimal `DATABASE_URL` sesuai user, password, dan nama database MySQL Anda):
   ```powershell
   Copy-Item .env.example .env
   ```
   File `.env` tidak boleh di-commit.
4. **Generate Prisma Client:**
   ```powershell
   npx prisma generate
   ```
5. **Jalankan backend:**
   ```powershell
   npm run dev
   ```
   Cek di browser: <http://localhost:3000/api/v1/health> harus menampilkan `{"success":true,"data":{"status":"ok"}}`.
6. **Jalankan aplikasi Android:** buka Android Studio → **File → Open** → pilih folder `android/` (bukan root repo), tunggu Gradle sync selesai, lalu jalankan di emulator atau perangkat. Dari emulator, backend lokal diakses lewat `http://10.0.2.2:3000/api/v1`.

## Untuk anggota tim yang memakai AI

Aturan AI ditulis sekali di `AGENTS.md` (root) serta `backend/AGENTS.md` dan `android/AGENTS.md`.

- **Antigravity** membaca `AGENTS.md` secara otomatis (di root dan subfolder).
- **Claude Code** membaca `CLAUDE.md`, yang hanya mengimpor `AGENTS.md` di folder yang sama.

Jangan menulis aturan di `CLAUDE.md` atau di pengaturan rules pribadi masing-masing tool; tulis di `AGENTS.md` atau `docs/` agar semua anggota mendapat aturan yang sama. Batas Antigravity adalah 12.000 karakter per file aturan, jadi pindahkan detail panjang ke `docs/`.

Cara menulis prompt: `docs/PROMPTING.md`.
