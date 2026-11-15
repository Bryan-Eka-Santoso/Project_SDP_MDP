-- Jam tenggat tugas dari guru (F-JDW-08). Tugas yang sudah ada tetap null = tenggat sampai akhir hari.
ALTER TABLE `tugas` ADD COLUMN `jam_tenggat` TIME(0) NULL;
