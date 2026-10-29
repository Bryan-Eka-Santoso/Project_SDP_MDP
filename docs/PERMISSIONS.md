# Hak akses dan cakupan data

Otorisasi selalu dilakukan di **backend** dalam dua lapis:

1. **Role**: middleware `authorize(...roles)` di route.
2. **Cakupan data**: service/policy memastikan data yang diminta memang boleh diakses user tersebut.

Android hanya menyembunyikan menu sesuai role; itu bukan pengaman.

## Definisi cakupan

| Kode | Arti |
|---|---|
| `SELF` | Data milik siswa itu sendiri |
| `CHILD` | Data anak yang terhubung ke wali murid lewat `StudentGuardian`. Jika anak lebih dari satu, client mengirim `studentId` dan backend memverifikasi relasinya |
| `TAUGHT` | Kelas dan mapel yang diampu guru lewat `TeachingAssignment` pada semester tersebut |
| `HOMEROOM` | Kelas yang wali kelasnya adalah guru tersebut pada tahun ajaran tersebut |
| `OWN` | Data yang dibuat oleh user itu sendiri (mis. pengajuan peminjaman) |
| `ALL` | Semua data |

"Wali kelas" = user `TEACHER` yang menjadi `homeroomTeacherId` di sebuah `ClassGroup` tahun ajaran aktif. Wali kelas juga memiliki semua hak guru biasa.

## Matriks fitur

Tanda `—` berarti tidak punya akses (backend mengembalikan 403).

| Fitur | Siswa | Guru | Wali kelas (tambahan) | Wali murid | Admin |
|---|---|---|---|---|---|
| Login, logout, profil, ubah password | ✓ | ✓ | | ✓ | ✓ |
| Notifikasi | milik sendiri | milik sendiri | | milik sendiri | milik sendiri |
| Lihat pengumuman | `SCHOOL` + `CLASS` kelasnya | `SCHOOL` + `CLASS` kelas yang diampu | `CLASS` kelas binaan | `SCHOOL` + `CLASS` kelas anak | `ALL` |
| Kelola pengumuman sekolah | — | — | — | — | ✓ |
| Kirim pengumuman kelas | — | — | `HOMEROOM` | — | — |
| Lihat kalender akademik | ✓ | ✓ | | ✓ | ✓ |
| Kelola kalender akademik | — | — | — | — | ✓ |
| Lihat jadwal | `SELF` (kelas reguler + kelas paket) | jadwal mengajarnya | `HOMEROOM` | `CHILD` | `ALL` (lewat master jadwal) |
| Kelola jadwal, impor, perubahan jadwal | — | — | — | — | ✓ |
| Lihat nilai + rekap | `SELF` | `TAUGHT` | `HOMEROOM` (semua mapel) | `CHILD` | `ALL` (lewat laporan) |
| Input/edit nilai | — | `TAUGHT` | | — | — |
| Kelola komponen penilaian | — | — | — | — | ✓ |
| Catatan perkembangan (rapor) | — | tulis `TAUGHT` | baca `HOMEROOM` | — | baca |
| Lihat presensi + rekap | `SELF` | `TAUGHT` | `HOMEROOM` | `CHILD` | `ALL` (lewat laporan) |
| Input presensi + jurnal | — | sesi miliknya (termasuk sebagai guru pengganti) | | — | — |
| Lihat jurnal mengajar | — | miliknya | | — | `ALL` |
| Lihat daftar siswa kelas | — | `TAUGHT` | `HOMEROOM` | — | `ALL` |
| Lihat profil lengkap siswa + data wali murid | — | — | `HOMEROOM` | — | `ALL` |
| Ajukan izin, lihat status izin | — | — | — | `CHILD` | — |
| Lihat & verifikasi pengajuan izin | — | — | `HOMEROOM` | — | — |
| Lihat pelanggaran + poin | `SELF` | `TAUGHT` | `HOMEROOM` | `CHILD` | `ALL` |
| Input pelanggaran | — | siswa `TAUGHT` | `HOMEROOM` | — | — |
| Kelola jenis pelanggaran | — | — | — | — | ✓ |
| Tulis buku penghubung | — | — | `HOMEROOM` | — | — |
| Baca buku penghubung | — | — | `HOMEROOM` | `CHILD` | — |
| Isi survei & pilih paket mapel | `SELF` | — | — | — | — |
| Kelola survei, paket, pembagian kelas | — | — | — | — | ✓ |
| Ajukan peminjaman | ✓ | ✓ | | — | — |
| Lihat peminjaman | `OWN` | `OWN` | | — | `ALL` |
| Setujui/tolak, konfirmasi pengembalian | — | — | — | — | ✓ |
| Lihat pembayaran | `SELF` | — | `HOMEROOM` | `CHILD` | `ALL` |
| Unggah rekap pembayaran | — | — | — | — | ✓ |
| Master user, role, reset password, data master | — | — | — | — | ✓ |
| Laporan dan ekspor | — | — | — | — | ✓ |

## Aturan tambahan

- Akun `BLOCKED` ditolak saat login dan saat refresh token.
- Guru pengganti mendapat hak input presensi + jurnal **hanya** untuk sesi pada tanggal penggantian tersebut.
- Data yang tidak boleh diakses dikembalikan sebagai **404** jika keberadaannya sendiri sensitif (mis. siswa kelas lain), atau **403** untuk fitur yang tidak boleh diakses role tersebut. Lihat `docs/API_CONVENTIONS.md`.
- Admin tidak menginput nilai, presensi, atau pelanggaran. Admin memantau lewat laporan.
- Baris "Input pelanggaran" untuk guru dibatasi pada siswa di kelas yang diampu; jika sekolah ingin semua guru bisa mencatat pelanggaran siswa mana pun, ubah baris ini dan `docs/BUSINESS_RULES.md` (pertanyaan terbuka PO-03).
