-- Pindah jadwal lintas hari (F-JDW-04): pertemuan pada `tanggal` dipindah ke `tanggal_baru`.
-- Perubahan yang sudah ada tetap null = pindah jam/ruangan pada tanggal yang sama.
ALTER TABLE `perubahan_jadwal` ADD COLUMN `tanggal_baru` DATE NULL;

CREATE INDEX `perubahan_jadwal_tanggal_baru_idx` ON `perubahan_jadwal`(`tanggal_baru`);
