# Template impor Excel

File contoh untuk impor data dari Excel (aturan: `docs/BUSINESS_RULES.md` bagian 2.3). Format kolom di folder ini adalah sumber kebenaran: jangan mengubah nama header tanpa mengubah kode impornya dan dokumen ini.

## Aturan umum semua impor

- Sistem membaca **lembar pertama**. Baris 1 adalah header; urutan kolom bebas, huruf besar/kecil pada header diabaikan, kolom tambahan diabaikan.
- File `.xlsx`, maksimal 10 MB dan 5000 baris data (pembayaran: 20000). Baris kosong dilewati.
- **Semua atau tidak sama sekali**: satu baris salah membatalkan seluruh impor dan semua kesalahan ditampilkan sekaligus (nomor baris Excel + nama kolom + alasan).
- Kolom kode, NIP, dan NIS harus berformat **Teks**. Excel membulatkan angka di atas 15 digit, jadi NIP 18 digit yang diketik sebagai angka rusak dan ditolak. Kolom NIP di template sudah berformat Teks.

## `jadwal.xlsx`: impor jadwal pelajaran (F-JDW-02)

| Kolom | Isi | Contoh |
|---|---|---|
| `Kelas` | Nama kelas persis seperti di data master, pada tahun ajaran semester tujuan | `X-1` |
| `Kode Mapel` | Kode mata pelajaran di data master | `MAT` |
| `NIP Guru` | NIP guru pengajar (spasi diabaikan) | `198001012005011001` |
| `Hari` | `Senin`, `Selasa`, `Rabu`, `Kamis`, `Jumat`, atau `Sabtu` | `Sabtu` |
| `Jam Mulai` | Jam `HH:mm` (boleh `HH.mm`, atau sel berformat jam) | `07:00` |
| `Jam Selesai` | Jam `HH:mm`, harus setelah jam mulai | `08:30` |
| `Ruangan` | Nama ruangan (fasilitas bertipe ruangan) persis seperti di data master | `Ruang X-1` |

Ketentuan khusus jadwal:

- Penugasan guru (guru × mapel × kelas) harus **sudah ada** lewat "Atur guru pengajar" (F-MST-10). Impor tidak membuat penugasan.
- Jadwal dari file **ditambahkan** ke jadwal semester; jadwal yang sudah ada tidak diubah atau dihapus.
- Dicek bentrok guru, kelas, ruangan, dan siswa yang sama, terhadap jadwal yang sudah ada dan antarbaris di file.
- Jadwal yang persis sama dengan yang sudah ada dilaporkan sebagai duplikat, jadi mengunggah file yang sama dua kali tidak menggandakan data.
- Tiga baris contoh di `jadwal.xlsx` memakai hari **Sabtu** dan data demo (seeder) supaya bisa langsung dicoba tanpa bentrok. Hapus baris contoh sebelum mengisi jadwal sebenarnya.

## `pembayaran.xlsx`: impor rekap pembayaran dari yayasan (F-BYR-01)

| Kolom | Isi | Contoh |
|---|---|---|
| `NIS` | NIS siswa persis seperti di data siswa (format Teks) | `2026001` |
| `Kode Tagihan` | Kode unik tagihan dari yayasan (format Teks) | `SPP-2026-09` |
| `Jenis Pembayaran` | Jenis tagihan, maksimal 50 karakter | `SPP` |
| `Nominal` | Rupiah bulat tanpa desimal | `850000` |
| `Jatuh Tempo` | Tanggal `YYYY-MM-DD` atau `DD/MM/YYYY` (atau sel tanggal) | `2026-09-10` |
| `Status` | `Lunas` atau `Belum Dibayar` ("Menunggak" dihitung otomatis dari jatuh tempo) | `Belum Dibayar` |
| `Tanggal Bayar` | Opsional; hanya untuk tagihan `Lunas`. Kolomnya boleh tidak ada | `2026-09-08` |

Ketentuan khusus pembayaran:

- NIS + Kode Tagihan adalah kunci: tagihan yang sudah ada **diperbarui** jika ada isi yang berbeda, yang belum ada **dibuat**. Tagihan yang tidak ada di file tidak diubah.
- Setelah impor yang mengubah data, siswa dan wali murid yang punya tagihan menunggak (belum dibayar dan lewat jatuh tempo) di file itu menerima notifikasi.
- Tiga baris contoh memakai NIS data demo (seeder) dan jatuh tempo yang sudah lewat, jadi mengimpornya akan menghasilkan notifikasi tunggakan. Hapus baris contoh sebelum mengisi rekap sebenarnya.

## Berkas contoh (`docs/import-samples/`)

Template di atas sudah pernah diimpor ke data demo, jadi mengunggahnya lagi hanya menghasilkan duplikat atau "tidak berubah". Untuk mencoba impor, pakai berkas di `docs/import-samples/` (dibuat dari template ini, setiap berkas punya lembar "Tentang berkas ini" yang menjelaskan isinya):

| Berkas | Hasil yang diharapkan pada data demo (seeder) |
|---|---|
| `jadwal_valid.xlsx` | `201`, 3 jadwal Sabtu baru diimpor. **Sekali pakai**: mengunggah lagi ditolak sebagai duplikat. Hapus ketiga slot lewat layar Jadwal sebelum mencoba ulang. |
| `jadwal_salah.xlsx` | `422 IMPORT_INVALID`, 11 kesalahan di 11 baris (satu per jenis kesalahan), tidak ada jadwal tersimpan. |
| `pembayaran_valid.xlsx` | `201`, 6 tagihan baru, 1 diperbarui, 1 tidak berubah, 3 siswa diberi notifikasi tunggakan (dihitung terhadap tanggal 12 Nov 2026). Aman diulang. Tagihan tidak bisa dihapus dari aplikasi. |
| `pembayaran_salah.xlsx` | `422 IMPORT_INVALID`, 10 kesalahan di 10 baris, tidak ada tagihan tersimpan. |

Aplikasi Android (layar Impor Jadwal dan Impor Pembayaran) memuat `jadwal.xlsx` dan `pembayaran.xlsx` dari folder ini ke dalam APK sebagai aset untuk tombol **Unduh template**: mengubah template di sini otomatis mengubah template di aplikasi pada build berikutnya. Berkas contoh sengaja berada di folder terpisah supaya tidak ikut masuk APK.
