-- AlterTable
ALTER TABLE `peminjaman` ADD COLUMN `alasan_penolakan` VARCHAR(255) NULL;

-- AlterTable
ALTER TABLE `pengajuan_izin` ADD COLUMN `alasan_penolakan` VARCHAR(255) NULL;
