-- Komponen penilaian pindah dari per mapel+semester ke per penugasan guru (ADR-018),
-- tambah tugas dari guru, predikat nilai per tahun ajaran, siswa.no_telp.
-- Disunting manual dari `prisma migrate diff` agar komponen dan nilai yang sudah ada tidak hilang.

-- 1. Kolom baru sederhana
ALTER TABLE `siswa` ADD COLUMN `no_telp` VARCHAR(20) NULL;

-- 2. Komponen penilaian
ALTER TABLE `komponen_penilaian` DROP FOREIGN KEY `komponen_penilaian_id_mapel_fkey`;
ALTER TABLE `komponen_penilaian` DROP FOREIGN KEY `komponen_penilaian_id_semester_fkey`;
DROP INDEX `komponen_penilaian_id_mapel_id_semester_nama_komponen_key` ON `komponen_penilaian`;
ALTER TABLE `komponen_penilaian` ADD COLUMN `id_penugasan` INTEGER NULL;

-- 2a. Salin setiap komponen lama ke semua penugasan guru dengan mapel + semester yang sama.
INSERT INTO `komponen_penilaian` (`id_penugasan`, `id_mapel`, `id_semester`, `nama_komponen`, `bobot`, `created_at`, `updated_at`)
SELECT pg.`id_penugasan`, k.`id_mapel`, k.`id_semester`, k.`nama_komponen`, k.`bobot`, k.`created_at`, CURRENT_TIMESTAMP(3)
FROM `komponen_penilaian` k
JOIN `penugasan_guru` pg ON pg.`id_mapel` = k.`id_mapel` AND pg.`id_semester` = k.`id_semester`
WHERE k.`id_penugasan` IS NULL;

-- 2b. Arahkan nilai ke salinan komponen milik penugasan nilai tersebut (nilai.id_penugasan).
UPDATE `nilai` n
JOIN `komponen_penilaian` lama ON lama.`id_komponen` = n.`id_komponen` AND lama.`id_penugasan` IS NULL
JOIN `komponen_penilaian` baru ON baru.`id_penugasan` = n.`id_penugasan` AND baru.`nama_komponen` = lama.`nama_komponen`
SET n.`id_komponen` = baru.`id_komponen`;

-- 2c. Hapus komponen lama. Jika masih ada nilai yang belum terpetakan, FK nilai (RESTRICT)
-- membuat migrasi gagal sehingga tidak ada nilai yang hilang diam-diam.
DELETE FROM `komponen_penilaian` WHERE `id_penugasan` IS NULL;

ALTER TABLE `komponen_penilaian` DROP COLUMN `id_mapel`,
    DROP COLUMN `id_semester`,
    MODIFY `id_penugasan` INTEGER NOT NULL;

CREATE UNIQUE INDEX `komponen_penilaian_id_penugasan_nama_komponen_key` ON `komponen_penilaian`(`id_penugasan`, `nama_komponen`);

ALTER TABLE `komponen_penilaian` ADD CONSTRAINT `komponen_penilaian_id_penugasan_fkey` FOREIGN KEY (`id_penugasan`) REFERENCES `penugasan_guru`(`id_penugasan`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- 3. Predikat nilai
CREATE TABLE `predikat_nilai` (
    `id_predikat` INTEGER NOT NULL AUTO_INCREMENT,
    `id_tahun_ajaran` INTEGER NOT NULL,
    `predikat` VARCHAR(5) NOT NULL,
    `nilai_minimum` DECIMAL(5, 2) NOT NULL,
    `keterangan` VARCHAR(50) NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL,

    UNIQUE INDEX `predikat_nilai_id_tahun_ajaran_predikat_key`(`id_tahun_ajaran`, `predikat`),
    UNIQUE INDEX `predikat_nilai_id_tahun_ajaran_nilai_minimum_key`(`id_tahun_ajaran`, `nilai_minimum`),
    PRIMARY KEY (`id_predikat`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

ALTER TABLE `predikat_nilai` ADD CONSTRAINT `predikat_nilai_id_tahun_ajaran_fkey` FOREIGN KEY (`id_tahun_ajaran`) REFERENCES `tahun_ajaran`(`id_tahun_ajaran`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- 4. Tugas dari guru
CREATE TABLE `tugas` (
    `id_tugas` INTEGER NOT NULL AUTO_INCREMENT,
    `id_penugasan` INTEGER NOT NULL,
    `judul` VARCHAR(150) NOT NULL,
    `catatan` TEXT NULL,
    `tenggat` DATE NOT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL,

    INDEX `tugas_id_penugasan_tenggat_idx`(`id_penugasan`, `tenggat`),
    PRIMARY KEY (`id_tugas`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

ALTER TABLE `tugas` ADD CONSTRAINT `tugas_id_penugasan_fkey` FOREIGN KEY (`id_penugasan`) REFERENCES `penugasan_guru`(`id_penugasan`) ON DELETE RESTRICT ON UPDATE CASCADE;
