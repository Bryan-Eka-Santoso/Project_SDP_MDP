-- CreateTable
CREATE TABLE `akun` (
    `id_akun` INTEGER NOT NULL AUTO_INCREMENT,
    `email` VARCHAR(100) NOT NULL,
    `password` VARCHAR(255) NOT NULL,
    `role` ENUM('STUDENT', 'TEACHER', 'GUARDIAN', 'ADMIN') NOT NULL,
    `status` ENUM('ACTIVE', 'BLOCKED') NOT NULL DEFAULT 'ACTIVE',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL,

    UNIQUE INDEX `akun_email_key`(`email`),
    PRIMARY KEY (`id_akun`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `admin` (
    `id_admin` INTEGER NOT NULL AUTO_INCREMENT,
    `id_akun` INTEGER NOT NULL,
    `nama` VARCHAR(100) NOT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL,

    UNIQUE INDEX `admin_id_akun_key`(`id_akun`),
    PRIMARY KEY (`id_admin`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `guru` (
    `id_guru` INTEGER NOT NULL AUTO_INCREMENT,
    `id_akun` INTEGER NOT NULL,
    `nip` VARCHAR(30) NOT NULL,
    `nama` VARCHAR(100) NOT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL,

    UNIQUE INDEX `guru_id_akun_key`(`id_akun`),
    UNIQUE INDEX `guru_nip_key`(`nip`),
    PRIMARY KEY (`id_guru`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `siswa` (
    `id_siswa` INTEGER NOT NULL AUTO_INCREMENT,
    `id_akun` INTEGER NOT NULL,
    `nis` VARCHAR(30) NOT NULL,
    `nama` VARCHAR(100) NOT NULL,
    `tanggal_lahir` DATE NOT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL,

    UNIQUE INDEX `siswa_id_akun_key`(`id_akun`),
    UNIQUE INDEX `siswa_nis_key`(`nis`),
    PRIMARY KEY (`id_siswa`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `wali_murid` (
    `id_wali_murid` INTEGER NOT NULL AUTO_INCREMENT,
    `id_akun` INTEGER NOT NULL,
    `nama` VARCHAR(100) NOT NULL,
    `no_telp` VARCHAR(20) NOT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL,

    UNIQUE INDEX `wali_murid_id_akun_key`(`id_akun`),
    PRIMARY KEY (`id_wali_murid`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `siswa_wali_murid` (
    `id_siswa` INTEGER NOT NULL,
    `id_wali_murid` INTEGER NOT NULL,
    `hubungan` VARCHAR(30) NOT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL,

    PRIMARY KEY (`id_siswa`, `id_wali_murid`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `refresh_token` (
    `id_refresh_token` INTEGER NOT NULL AUTO_INCREMENT,
    `id_akun` INTEGER NOT NULL,
    `token_hash` VARCHAR(255) NOT NULL,
    `kedaluwarsa_pada` DATETIME(3) NOT NULL,
    `dicabut_pada` DATETIME(3) NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    UNIQUE INDEX `refresh_token_token_hash_key`(`token_hash`),
    PRIMARY KEY (`id_refresh_token`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `token_perangkat` (
    `id_token_perangkat` INTEGER NOT NULL AUTO_INCREMENT,
    `id_akun` INTEGER NOT NULL,
    `token` VARCHAR(255) NOT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL,

    UNIQUE INDEX `token_perangkat_token_key`(`token`),
    PRIMARY KEY (`id_token_perangkat`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `tahun_ajaran` (
    `id_tahun_ajaran` INTEGER NOT NULL AUTO_INCREMENT,
    `nama` VARCHAR(30) NOT NULL,
    `tanggal_mulai` DATE NOT NULL,
    `tanggal_selesai` DATE NOT NULL,
    `is_aktif` BOOLEAN NOT NULL DEFAULT false,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL,

    UNIQUE INDEX `tahun_ajaran_nama_key`(`nama`),
    PRIMARY KEY (`id_tahun_ajaran`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `semester` (
    `id_semester` INTEGER NOT NULL AUTO_INCREMENT,
    `id_tahun_ajaran` INTEGER NOT NULL,
    `jenis` ENUM('ODD', 'EVEN') NOT NULL,
    `tanggal_mulai` DATE NOT NULL,
    `tanggal_selesai` DATE NOT NULL,
    `is_aktif` BOOLEAN NOT NULL DEFAULT false,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL,

    UNIQUE INDEX `semester_id_tahun_ajaran_jenis_key`(`id_tahun_ajaran`, `jenis`),
    PRIMARY KEY (`id_semester`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `kelas` (
    `id_kelas` INTEGER NOT NULL AUTO_INCREMENT,
    `id_tahun_ajaran` INTEGER NOT NULL,
    `id_wali_kelas` INTEGER NULL,
    `nama_kelas` VARCHAR(30) NOT NULL,
    `tingkat` VARCHAR(10) NOT NULL,
    `kapasitas` INTEGER NOT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL,

    UNIQUE INDEX `kelas_id_tahun_ajaran_nama_kelas_key`(`id_tahun_ajaran`, `nama_kelas`),
    PRIMARY KEY (`id_kelas`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `siswa_kelas` (
    `id_siswa` INTEGER NOT NULL,
    `id_kelas` INTEGER NOT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    PRIMARY KEY (`id_siswa`, `id_kelas`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `mata_pelajaran` (
    `id_mapel` INTEGER NOT NULL AUTO_INCREMENT,
    `kode_mapel` VARCHAR(20) NOT NULL,
    `nama_mapel` VARCHAR(100) NOT NULL,
    `jenis` VARCHAR(30) NOT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL,

    UNIQUE INDEX `mata_pelajaran_kode_mapel_key`(`kode_mapel`),
    PRIMARY KEY (`id_mapel`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `penugasan_guru` (
    `id_penugasan` INTEGER NOT NULL AUTO_INCREMENT,
    `id_guru` INTEGER NOT NULL,
    `id_mapel` INTEGER NOT NULL,
    `id_kelas` INTEGER NOT NULL,
    `id_semester` INTEGER NOT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL,

    UNIQUE INDEX `penugasan_guru_id_guru_id_mapel_id_kelas_id_semester_key`(`id_guru`, `id_mapel`, `id_kelas`, `id_semester`),
    PRIMARY KEY (`id_penugasan`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `komponen_penilaian` (
    `id_komponen` INTEGER NOT NULL AUTO_INCREMENT,
    `id_mapel` INTEGER NOT NULL,
    `id_semester` INTEGER NOT NULL,
    `nama_komponen` VARCHAR(50) NOT NULL,
    `bobot` INTEGER NOT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL,

    UNIQUE INDEX `komponen_penilaian_id_mapel_id_semester_nama_komponen_key`(`id_mapel`, `id_semester`, `nama_komponen`),
    PRIMARY KEY (`id_komponen`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `jenis_pelanggaran` (
    `id_jenis_pelanggaran` INTEGER NOT NULL AUTO_INCREMENT,
    `nama` VARCHAR(100) NOT NULL,
    `bobot_poin` INTEGER NOT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL,

    PRIMARY KEY (`id_jenis_pelanggaran`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `fasilitas` (
    `id_fasilitas` INTEGER NOT NULL AUTO_INCREMENT,
    `nama` VARCHAR(100) NOT NULL,
    `jenis` ENUM('ROOM', 'ITEM') NOT NULL,
    `jumlah` INTEGER NOT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL,

    PRIMARY KEY (`id_fasilitas`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `paket_mata_pelajaran` (
    `id_paket_mapel` INTEGER NOT NULL AUTO_INCREMENT,
    `id_tahun_ajaran` INTEGER NOT NULL,
    `nama_paket` VARCHAR(100) NOT NULL,
    `kapasitas` INTEGER NOT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL,

    PRIMARY KEY (`id_paket_mapel`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `paket_mapel_detail` (
    `id_paket_mapel` INTEGER NOT NULL,
    `id_mapel` INTEGER NOT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    PRIMARY KEY (`id_paket_mapel`, `id_mapel`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `survei_mapel_pilihan` (
    `id_survei` INTEGER NOT NULL AUTO_INCREMENT,
    `id_tahun_ajaran` INTEGER NOT NULL,
    `tingkat` VARCHAR(10) NOT NULL,
    `judul` VARCHAR(100) NOT NULL,
    `tanggal_buka` DATE NOT NULL,
    `tanggal_tutup` DATE NOT NULL,
    `status` ENUM('DRAFT', 'PUBLISHED', 'CLOSED') NOT NULL DEFAULT 'DRAFT',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL,

    PRIMARY KEY (`id_survei`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `jawaban_survei` (
    `id_survei` INTEGER NOT NULL,
    `id_siswa` INTEGER NOT NULL,
    `id_mapel` INTEGER NOT NULL,
    `tanggal_isi` DATE NOT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL,

    PRIMARY KEY (`id_survei`, `id_siswa`, `id_mapel`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `pemilihan_mapel` (
    `id_siswa` INTEGER NOT NULL,
    `id_paket_mapel` INTEGER NOT NULL,
    `tanggal_pilih` DATE NOT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL,

    PRIMARY KEY (`id_siswa`, `id_paket_mapel`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `jadwal` (
    `id_jadwal` INTEGER NOT NULL AUTO_INCREMENT,
    `id_penugasan` INTEGER NOT NULL,
    `id_fasilitas` INTEGER NOT NULL,
    `hari` ENUM('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY') NOT NULL,
    `jam_mulai` TIME(0) NOT NULL,
    `jam_selesai` TIME(0) NOT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL,

    PRIMARY KEY (`id_jadwal`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `perubahan_jadwal` (
    `id_perubahan` INTEGER NOT NULL AUTO_INCREMENT,
    `id_jadwal` INTEGER NOT NULL,
    `jenis` ENUM('SUBSTITUTE', 'RESCHEDULE', 'CANCEL') NOT NULL,
    `tanggal` DATE NOT NULL,
    `id_guru_pengganti` INTEGER NULL,
    `id_fasilitas_baru` INTEGER NULL,
    `jam_mulai_baru` TIME(0) NULL,
    `jam_selesai_baru` TIME(0) NULL,
    `keterangan` VARCHAR(255) NOT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL,

    UNIQUE INDEX `perubahan_jadwal_id_jadwal_tanggal_key`(`id_jadwal`, `tanggal`),
    PRIMARY KEY (`id_perubahan`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `sesi_mengajar` (
    `id_sesi` INTEGER NOT NULL AUTO_INCREMENT,
    `id_jadwal` INTEGER NOT NULL,
    `tanggal` DATE NOT NULL,
    `materi` VARCHAR(255) NOT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL,

    UNIQUE INDEX `sesi_mengajar_id_jadwal_tanggal_key`(`id_jadwal`, `tanggal`),
    PRIMARY KEY (`id_sesi`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `presensi` (
    `id_sesi` INTEGER NOT NULL,
    `id_siswa` INTEGER NOT NULL,
    `status` ENUM('PRESENT', 'SICK', 'EXCUSED', 'ABSENT') NOT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL,

    PRIMARY KEY (`id_sesi`, `id_siswa`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `pengajuan_izin` (
    `id_izin` INTEGER NOT NULL AUTO_INCREMENT,
    `id_siswa` INTEGER NOT NULL,
    `id_wali_murid` INTEGER NOT NULL,
    `id_verifikator` INTEGER NULL,
    `tanggal_mulai` DATE NOT NULL,
    `tanggal_selesai` DATE NOT NULL,
    `alasan` VARCHAR(255) NOT NULL,
    `lampiran_url` VARCHAR(255) NULL,
    `lampiran_public_id` VARCHAR(255) NULL,
    `status` ENUM('PENDING', 'APPROVED', 'REJECTED') NOT NULL DEFAULT 'PENDING',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL,

    PRIMARY KEY (`id_izin`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `nilai` (
    `id_nilai` INTEGER NOT NULL AUTO_INCREMENT,
    `id_siswa` INTEGER NOT NULL,
    `id_penugasan` INTEGER NOT NULL,
    `id_komponen` INTEGER NOT NULL,
    `nilai` DECIMAL(5, 2) NOT NULL,
    `tanggal_input` DATE NOT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL,

    UNIQUE INDEX `nilai_id_siswa_id_komponen_key`(`id_siswa`, `id_komponen`),
    PRIMARY KEY (`id_nilai`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `catatan_perkembangan` (
    `id_catatan` INTEGER NOT NULL AUTO_INCREMENT,
    `id_siswa` INTEGER NOT NULL,
    `id_penugasan` INTEGER NOT NULL,
    `tanggal` DATE NOT NULL,
    `isi` VARCHAR(500) NOT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL,

    PRIMARY KEY (`id_catatan`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `pelanggaran` (
    `id_pelanggaran` INTEGER NOT NULL AUTO_INCREMENT,
    `id_siswa` INTEGER NOT NULL,
    `id_jenis_pelanggaran` INTEGER NOT NULL,
    `id_guru` INTEGER NOT NULL,
    `tanggal` DATE NOT NULL,
    `poin` INTEGER NOT NULL,
    `keterangan` VARCHAR(255) NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL,

    PRIMARY KEY (`id_pelanggaran`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `buku_penghubung` (
    `id_bp` INTEGER NOT NULL AUTO_INCREMENT,
    `id_siswa` INTEGER NOT NULL,
    `id_wali_kelas` INTEGER NOT NULL,
    `tanggal` DATE NOT NULL,
    `isi` VARCHAR(500) NOT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL,

    PRIMARY KEY (`id_bp`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `pengumuman` (
    `id_pengumuman` INTEGER NOT NULL AUTO_INCREMENT,
    `id_akun_pembuat` INTEGER NOT NULL,
    `id_kelas` INTEGER NULL,
    `judul` VARCHAR(100) NOT NULL,
    `isi` VARCHAR(500) NOT NULL,
    `target` ENUM('SCHOOL', 'CLASS') NOT NULL,
    `tanggal` DATE NOT NULL,
    `lampiran_url` VARCHAR(255) NULL,
    `lampiran_public_id` VARCHAR(255) NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL,

    PRIMARY KEY (`id_pengumuman`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `kalender_akademik` (
    `id_agenda` INTEGER NOT NULL AUTO_INCREMENT,
    `judul` VARCHAR(100) NOT NULL,
    `jenis` ENUM('EXAM', 'HOLIDAY', 'ACTIVITY') NOT NULL,
    `tanggal_mulai` DATE NOT NULL,
    `tanggal_selesai` DATE NOT NULL,
    `keterangan` VARCHAR(255) NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL,

    PRIMARY KEY (`id_agenda`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `notifikasi` (
    `id_notifikasi` INTEGER NOT NULL AUTO_INCREMENT,
    `judul` VARCHAR(100) NOT NULL,
    `isi` VARCHAR(255) NOT NULL,
    `tipe` ENUM('SCHEDULE_CHANGE', 'ANNOUNCEMENT', 'VIOLATION', 'LEAVE_REQUEST', 'BOOKING', 'BILL_OVERDUE') NOT NULL,
    `id_referensi` INTEGER NULL,
    `tanggal` DATE NOT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    PRIMARY KEY (`id_notifikasi`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `notifikasi_penerima` (
    `id_notifikasi` INTEGER NOT NULL,
    `id_akun` INTEGER NOT NULL,
    `is_read` BOOLEAN NOT NULL DEFAULT false,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL,

    PRIMARY KEY (`id_notifikasi`, `id_akun`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `peminjaman` (
    `id_peminjaman` INTEGER NOT NULL AUTO_INCREMENT,
    `id_fasilitas` INTEGER NOT NULL,
    `id_akun_pemohon` INTEGER NOT NULL,
    `id_admin_verifikator` INTEGER NULL,
    `tanggal_pinjam` DATE NOT NULL,
    `jam_mulai` TIME(0) NOT NULL,
    `jam_selesai` TIME(0) NOT NULL,
    `keperluan` VARCHAR(255) NOT NULL,
    `status` ENUM('PENDING', 'APPROVED', 'REJECTED', 'RETURNED') NOT NULL DEFAULT 'PENDING',
    `tanggal_kembali` DATE NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL,

    PRIMARY KEY (`id_peminjaman`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `pembayaran` (
    `id_pembayaran` INTEGER NOT NULL AUTO_INCREMENT,
    `id_siswa` INTEGER NOT NULL,
    `kode_tagihan` VARCHAR(50) NOT NULL,
    `jenis_pembayaran` VARCHAR(50) NOT NULL,
    `nominal` DECIMAL(12, 2) NOT NULL,
    `jatuh_tempo` DATE NOT NULL,
    `status` ENUM('PAID', 'UNPAID') NOT NULL DEFAULT 'UNPAID',
    `tanggal_bayar` DATE NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL,

    UNIQUE INDEX `pembayaran_id_siswa_kode_tagihan_key`(`id_siswa`, `kode_tagihan`),
    PRIMARY KEY (`id_pembayaran`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- AddForeignKey
ALTER TABLE `admin` ADD CONSTRAINT `admin_id_akun_fkey` FOREIGN KEY (`id_akun`) REFERENCES `akun`(`id_akun`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `guru` ADD CONSTRAINT `guru_id_akun_fkey` FOREIGN KEY (`id_akun`) REFERENCES `akun`(`id_akun`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `siswa` ADD CONSTRAINT `siswa_id_akun_fkey` FOREIGN KEY (`id_akun`) REFERENCES `akun`(`id_akun`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `wali_murid` ADD CONSTRAINT `wali_murid_id_akun_fkey` FOREIGN KEY (`id_akun`) REFERENCES `akun`(`id_akun`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `siswa_wali_murid` ADD CONSTRAINT `siswa_wali_murid_id_siswa_fkey` FOREIGN KEY (`id_siswa`) REFERENCES `siswa`(`id_siswa`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `siswa_wali_murid` ADD CONSTRAINT `siswa_wali_murid_id_wali_murid_fkey` FOREIGN KEY (`id_wali_murid`) REFERENCES `wali_murid`(`id_wali_murid`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `refresh_token` ADD CONSTRAINT `refresh_token_id_akun_fkey` FOREIGN KEY (`id_akun`) REFERENCES `akun`(`id_akun`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `token_perangkat` ADD CONSTRAINT `token_perangkat_id_akun_fkey` FOREIGN KEY (`id_akun`) REFERENCES `akun`(`id_akun`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `semester` ADD CONSTRAINT `semester_id_tahun_ajaran_fkey` FOREIGN KEY (`id_tahun_ajaran`) REFERENCES `tahun_ajaran`(`id_tahun_ajaran`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `kelas` ADD CONSTRAINT `kelas_id_tahun_ajaran_fkey` FOREIGN KEY (`id_tahun_ajaran`) REFERENCES `tahun_ajaran`(`id_tahun_ajaran`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `kelas` ADD CONSTRAINT `kelas_id_wali_kelas_fkey` FOREIGN KEY (`id_wali_kelas`) REFERENCES `guru`(`id_guru`) ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `siswa_kelas` ADD CONSTRAINT `siswa_kelas_id_siswa_fkey` FOREIGN KEY (`id_siswa`) REFERENCES `siswa`(`id_siswa`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `siswa_kelas` ADD CONSTRAINT `siswa_kelas_id_kelas_fkey` FOREIGN KEY (`id_kelas`) REFERENCES `kelas`(`id_kelas`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `penugasan_guru` ADD CONSTRAINT `penugasan_guru_id_guru_fkey` FOREIGN KEY (`id_guru`) REFERENCES `guru`(`id_guru`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `penugasan_guru` ADD CONSTRAINT `penugasan_guru_id_mapel_fkey` FOREIGN KEY (`id_mapel`) REFERENCES `mata_pelajaran`(`id_mapel`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `penugasan_guru` ADD CONSTRAINT `penugasan_guru_id_kelas_fkey` FOREIGN KEY (`id_kelas`) REFERENCES `kelas`(`id_kelas`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `penugasan_guru` ADD CONSTRAINT `penugasan_guru_id_semester_fkey` FOREIGN KEY (`id_semester`) REFERENCES `semester`(`id_semester`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `komponen_penilaian` ADD CONSTRAINT `komponen_penilaian_id_mapel_fkey` FOREIGN KEY (`id_mapel`) REFERENCES `mata_pelajaran`(`id_mapel`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `komponen_penilaian` ADD CONSTRAINT `komponen_penilaian_id_semester_fkey` FOREIGN KEY (`id_semester`) REFERENCES `semester`(`id_semester`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `paket_mata_pelajaran` ADD CONSTRAINT `paket_mata_pelajaran_id_tahun_ajaran_fkey` FOREIGN KEY (`id_tahun_ajaran`) REFERENCES `tahun_ajaran`(`id_tahun_ajaran`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `paket_mapel_detail` ADD CONSTRAINT `paket_mapel_detail_id_paket_mapel_fkey` FOREIGN KEY (`id_paket_mapel`) REFERENCES `paket_mata_pelajaran`(`id_paket_mapel`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `paket_mapel_detail` ADD CONSTRAINT `paket_mapel_detail_id_mapel_fkey` FOREIGN KEY (`id_mapel`) REFERENCES `mata_pelajaran`(`id_mapel`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `survei_mapel_pilihan` ADD CONSTRAINT `survei_mapel_pilihan_id_tahun_ajaran_fkey` FOREIGN KEY (`id_tahun_ajaran`) REFERENCES `tahun_ajaran`(`id_tahun_ajaran`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `jawaban_survei` ADD CONSTRAINT `jawaban_survei_id_survei_fkey` FOREIGN KEY (`id_survei`) REFERENCES `survei_mapel_pilihan`(`id_survei`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `jawaban_survei` ADD CONSTRAINT `jawaban_survei_id_siswa_fkey` FOREIGN KEY (`id_siswa`) REFERENCES `siswa`(`id_siswa`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `jawaban_survei` ADD CONSTRAINT `jawaban_survei_id_mapel_fkey` FOREIGN KEY (`id_mapel`) REFERENCES `mata_pelajaran`(`id_mapel`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `pemilihan_mapel` ADD CONSTRAINT `pemilihan_mapel_id_siswa_fkey` FOREIGN KEY (`id_siswa`) REFERENCES `siswa`(`id_siswa`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `pemilihan_mapel` ADD CONSTRAINT `pemilihan_mapel_id_paket_mapel_fkey` FOREIGN KEY (`id_paket_mapel`) REFERENCES `paket_mata_pelajaran`(`id_paket_mapel`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `jadwal` ADD CONSTRAINT `jadwal_id_penugasan_fkey` FOREIGN KEY (`id_penugasan`) REFERENCES `penugasan_guru`(`id_penugasan`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `jadwal` ADD CONSTRAINT `jadwal_id_fasilitas_fkey` FOREIGN KEY (`id_fasilitas`) REFERENCES `fasilitas`(`id_fasilitas`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `perubahan_jadwal` ADD CONSTRAINT `perubahan_jadwal_id_jadwal_fkey` FOREIGN KEY (`id_jadwal`) REFERENCES `jadwal`(`id_jadwal`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `perubahan_jadwal` ADD CONSTRAINT `perubahan_jadwal_id_guru_pengganti_fkey` FOREIGN KEY (`id_guru_pengganti`) REFERENCES `guru`(`id_guru`) ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `perubahan_jadwal` ADD CONSTRAINT `perubahan_jadwal_id_fasilitas_baru_fkey` FOREIGN KEY (`id_fasilitas_baru`) REFERENCES `fasilitas`(`id_fasilitas`) ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `sesi_mengajar` ADD CONSTRAINT `sesi_mengajar_id_jadwal_fkey` FOREIGN KEY (`id_jadwal`) REFERENCES `jadwal`(`id_jadwal`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `presensi` ADD CONSTRAINT `presensi_id_sesi_fkey` FOREIGN KEY (`id_sesi`) REFERENCES `sesi_mengajar`(`id_sesi`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `presensi` ADD CONSTRAINT `presensi_id_siswa_fkey` FOREIGN KEY (`id_siswa`) REFERENCES `siswa`(`id_siswa`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `pengajuan_izin` ADD CONSTRAINT `pengajuan_izin_id_siswa_fkey` FOREIGN KEY (`id_siswa`) REFERENCES `siswa`(`id_siswa`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `pengajuan_izin` ADD CONSTRAINT `pengajuan_izin_id_wali_murid_fkey` FOREIGN KEY (`id_wali_murid`) REFERENCES `wali_murid`(`id_wali_murid`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `pengajuan_izin` ADD CONSTRAINT `pengajuan_izin_id_verifikator_fkey` FOREIGN KEY (`id_verifikator`) REFERENCES `guru`(`id_guru`) ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `nilai` ADD CONSTRAINT `nilai_id_siswa_fkey` FOREIGN KEY (`id_siswa`) REFERENCES `siswa`(`id_siswa`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `nilai` ADD CONSTRAINT `nilai_id_penugasan_fkey` FOREIGN KEY (`id_penugasan`) REFERENCES `penugasan_guru`(`id_penugasan`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `nilai` ADD CONSTRAINT `nilai_id_komponen_fkey` FOREIGN KEY (`id_komponen`) REFERENCES `komponen_penilaian`(`id_komponen`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `catatan_perkembangan` ADD CONSTRAINT `catatan_perkembangan_id_siswa_fkey` FOREIGN KEY (`id_siswa`) REFERENCES `siswa`(`id_siswa`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `catatan_perkembangan` ADD CONSTRAINT `catatan_perkembangan_id_penugasan_fkey` FOREIGN KEY (`id_penugasan`) REFERENCES `penugasan_guru`(`id_penugasan`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `pelanggaran` ADD CONSTRAINT `pelanggaran_id_siswa_fkey` FOREIGN KEY (`id_siswa`) REFERENCES `siswa`(`id_siswa`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `pelanggaran` ADD CONSTRAINT `pelanggaran_id_jenis_pelanggaran_fkey` FOREIGN KEY (`id_jenis_pelanggaran`) REFERENCES `jenis_pelanggaran`(`id_jenis_pelanggaran`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `pelanggaran` ADD CONSTRAINT `pelanggaran_id_guru_fkey` FOREIGN KEY (`id_guru`) REFERENCES `guru`(`id_guru`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `buku_penghubung` ADD CONSTRAINT `buku_penghubung_id_siswa_fkey` FOREIGN KEY (`id_siswa`) REFERENCES `siswa`(`id_siswa`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `buku_penghubung` ADD CONSTRAINT `buku_penghubung_id_wali_kelas_fkey` FOREIGN KEY (`id_wali_kelas`) REFERENCES `guru`(`id_guru`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `pengumuman` ADD CONSTRAINT `pengumuman_id_akun_pembuat_fkey` FOREIGN KEY (`id_akun_pembuat`) REFERENCES `akun`(`id_akun`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `pengumuman` ADD CONSTRAINT `pengumuman_id_kelas_fkey` FOREIGN KEY (`id_kelas`) REFERENCES `kelas`(`id_kelas`) ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `notifikasi_penerima` ADD CONSTRAINT `notifikasi_penerima_id_notifikasi_fkey` FOREIGN KEY (`id_notifikasi`) REFERENCES `notifikasi`(`id_notifikasi`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `notifikasi_penerima` ADD CONSTRAINT `notifikasi_penerima_id_akun_fkey` FOREIGN KEY (`id_akun`) REFERENCES `akun`(`id_akun`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `peminjaman` ADD CONSTRAINT `peminjaman_id_fasilitas_fkey` FOREIGN KEY (`id_fasilitas`) REFERENCES `fasilitas`(`id_fasilitas`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `peminjaman` ADD CONSTRAINT `peminjaman_id_akun_pemohon_fkey` FOREIGN KEY (`id_akun_pemohon`) REFERENCES `akun`(`id_akun`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `peminjaman` ADD CONSTRAINT `peminjaman_id_admin_verifikator_fkey` FOREIGN KEY (`id_admin_verifikator`) REFERENCES `admin`(`id_admin`) ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `pembayaran` ADD CONSTRAINT `pembayaran_id_siswa_fkey` FOREIGN KEY (`id_siswa`) REFERENCES `siswa`(`id_siswa`) ON DELETE RESTRICT ON UPDATE CASCADE;

