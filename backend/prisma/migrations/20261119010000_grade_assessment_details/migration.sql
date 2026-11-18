-- Tambahan tabel detail penilaian Tugas dan Ulangan, tanpa menghapus nilai lama.
CREATE TABLE `penilaian_detail` (
 `id_penilaian_detail` INTEGER NOT NULL AUTO_INCREMENT,
 `id_penugasan` INTEGER NOT NULL,
 `id_komponen` INTEGER NOT NULL,
 `judul` VARCHAR(80) NOT NULL,
 `urutan` INTEGER NOT NULL,
 `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 `updated_at` DATETIME(3) NOT NULL,
 UNIQUE INDEX `penilaian_detail_id_komponen_urutan_key`(`id_komponen`, `urutan`),
 PRIMARY KEY (`id_penilaian_detail`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE TABLE `nilai_detail` (
 `id_nilai_detail` INTEGER NOT NULL AUTO_INCREMENT,
 `id_penilaian_detail` INTEGER NOT NULL,
 `id_siswa` INTEGER NOT NULL,
 `nilai` DECIMAL(5,2) NOT NULL,
 `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 `updated_at` DATETIME(3) NOT NULL,
 UNIQUE INDEX `nilai_detail_id_penilaian_detail_id_siswa_key`(`id_penilaian_detail`, `id_siswa`),
 PRIMARY KEY (`id_nilai_detail`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE `penilaian_detail` ADD CONSTRAINT `penilaian_detail_id_penugasan_fkey` FOREIGN KEY (`id_penugasan`) REFERENCES `penugasan_guru`(`id_penugasan`) ON DELETE RESTRICT ON UPDATE CASCADE;
ALTER TABLE `penilaian_detail` ADD CONSTRAINT `penilaian_detail_id_komponen_fkey` FOREIGN KEY (`id_komponen`) REFERENCES `komponen_penilaian`(`id_komponen`) ON DELETE RESTRICT ON UPDATE CASCADE;
ALTER TABLE `nilai_detail` ADD CONSTRAINT `nilai_detail_id_penilaian_detail_fkey` FOREIGN KEY (`id_penilaian_detail`) REFERENCES `penilaian_detail`(`id_penilaian_detail`) ON DELETE RESTRICT ON UPDATE CASCADE;
ALTER TABLE `nilai_detail` ADD CONSTRAINT `nilai_detail_id_siswa_fkey` FOREIGN KEY (`id_siswa`) REFERENCES `siswa`(`id_siswa`) ON DELETE RESTRICT ON UPDATE CASCADE;
