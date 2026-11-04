-- Penyederhanaan akun dan notifikasi (lihat ADR-004, ADR-010, ADR-017):
--   * nama pindah dari tabel profil ke akun; tabel admin dihapus
--   * peminjaman diverifikasi oleh akun (role ADMIN), bukan baris admin
--   * guru.is_wali_kelas sebagai penanda cepat wali kelas
--   * notifikasi tidak lagi disimpan di database (hanya FCM + Room di perangkat)
-- Disusun dari `prisma migrate diff`, lalu ditambah langkah pemindahan data agar
-- database yang sudah berisi data tidak gagal dimigrasi atau kehilangan nama.

-- DropForeignKey
ALTER TABLE `admin` DROP FOREIGN KEY `admin_id_akun_fkey`;
ALTER TABLE `notifikasi_penerima` DROP FOREIGN KEY `notifikasi_penerima_id_notifikasi_fkey`;
ALTER TABLE `notifikasi_penerima` DROP FOREIGN KEY `notifikasi_penerima_id_akun_fkey`;
ALTER TABLE `peminjaman` DROP FOREIGN KEY `peminjaman_id_admin_verifikator_fkey`;

-- akun.nama: tambah nullable, salin dari tabel profil, lalu wajibkan
ALTER TABLE `akun` ADD COLUMN `nama` VARCHAR(100) NULL;

UPDATE `akun` a JOIN `admin` p ON p.`id_akun` = a.`id_akun` SET a.`nama` = p.`nama`;
UPDATE `akun` a JOIN `guru` p ON p.`id_akun` = a.`id_akun` SET a.`nama` = p.`nama`;
UPDATE `akun` a JOIN `siswa` p ON p.`id_akun` = a.`id_akun` SET a.`nama` = p.`nama`;
UPDATE `akun` a JOIN `wali_murid` p ON p.`id_akun` = a.`id_akun` SET a.`nama` = p.`nama`;
-- Akun tanpa profil: pakai email agar kolom tidak kosong
UPDATE `akun` SET `nama` = LEFT(`email`, 100) WHERE `nama` IS NULL;

ALTER TABLE `akun` MODIFY `nama` VARCHAR(100) NOT NULL;

-- peminjaman: verifikator merujuk akun admin
ALTER TABLE `peminjaman` ADD COLUMN `id_akun_verifikator` INTEGER NULL;

UPDATE `peminjaman` pj JOIN `admin` ad ON ad.`id_admin` = pj.`id_admin_verifikator`
SET pj.`id_akun_verifikator` = ad.`id_akun`;

ALTER TABLE `peminjaman` DROP COLUMN `id_admin_verifikator`;

-- guru.is_wali_kelas: diisi dari kelas pada tahun ajaran aktif
ALTER TABLE `guru` ADD COLUMN `is_wali_kelas` BOOLEAN NOT NULL DEFAULT false;

UPDATE `guru` g SET g.`is_wali_kelas` = EXISTS (
    SELECT 1 FROM `kelas` k
    JOIN `tahun_ajaran` t ON t.`id_tahun_ajaran` = k.`id_tahun_ajaran`
    WHERE k.`id_wali_kelas` = g.`id_guru` AND t.`is_aktif` = true
);

-- Hapus kolom nama yang sudah dipindah
ALTER TABLE `guru` DROP COLUMN `nama`;
ALTER TABLE `siswa` DROP COLUMN `nama`;
ALTER TABLE `wali_murid` DROP COLUMN `nama`;

-- DropTable
DROP TABLE `admin`;
DROP TABLE `notifikasi_penerima`;
DROP TABLE `notifikasi`;

-- AddForeignKey
ALTER TABLE `peminjaman` ADD CONSTRAINT `peminjaman_id_akun_verifikator_fkey` FOREIGN KEY (`id_akun_verifikator`) REFERENCES `akun`(`id_akun`) ON DELETE SET NULL ON UPDATE CASCADE;
