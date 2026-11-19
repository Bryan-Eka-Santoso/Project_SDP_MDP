const path = require("node:path");
const express = require("express");
const { sendSuccess } = require("./utils/response");
const { errorHandler, notFoundHandler } = require("./middlewares/errorHandler");
const env = require("./config/env");

const app = express();

// Jangan umumkan versi Express di header response.
app.disable("x-powered-by");
// Di belakang reverse proxy, isi TRUST_PROXY (jumlah proxy) agar IP klien terbaca benar.
if (env.trustProxy > 0) app.set("trust proxy", env.trustProxy);
// Batas body JSON; upload file lewat multipart (Multer), bukan JSON.
app.use(express.json({ limit: "1mb" }));

const api = express.Router();

api.get("/health", (req, res) => {
  sendSuccess(res, { status: "ok" });
});

// Router modul. Router auth memuat path /auth/* dan /me sekaligus.
api.use(require("./modules/auth/auth.routes"));
api.use(require("./modules/grades/grades.routes"));
api.use(require("./modules/grades/grades.lookup.routes"));
api.use(require("./modules/grades/grades.read.routes"));
// NIL-05: Catatan perkembangan siswa
api.use(require("./modules/grades/grades.progress.routes"));
api.use(require("./modules/attendance/attendance.routes"));

// ABS-03 & ABS-04: Rekap presensi dan monitoring jurnal mengajar
api.use(require("./modules/attendance/attendance.read.routes"));

api.use(require("./modules/attendance/leave.routes"));

// IZN-03 & IZN-04: Pemeriksaan izin oleh Wali Kelas
api.use(require("./modules/attendance/leave.review.routes"));

// MPP-05 & MPP-06: Pembagian kelas dan tren paket mapel
api.use(require("./modules/elective/elective.allocation.routes"));
api.use(require("./modules/notifications/notifications.routes"));
api.use(require("./modules/schedules/schedules.routes"));
api.use(require("./modules/bills/bills.routes"));
api.use(require("./modules/reports/reports.routes"));
api.use("/users", require("./modules/users/users.routes"));
api.use(require("./modules/master/master.routes"));
api.use("/subject-packages", require("./modules/elective/elective.routes"));
api.use("/academic-events", require("./modules/calendar/calendar.routes"));
api.use("/announcements", require("./modules/announcements/announcements.routes"));

// File lampiran lokal sementara (fallback jika env Cloudinary belum diisi).
app.use("/uploads", express.static(path.resolve(__dirname, "../uploads"), { dotfiles: "ignore" }));
api.use("/violations", require("./modules/violations/pelanggaran.routes"));
api.use(
  "/violation-history",
  require("./modules/violations/riwayat_pelanggaran.routes"),
);
api.use(
  "/booking-requests",
  require("./modules/bookings/ajukan_peminjaman.routes"),
);
api.use("/facility-bookings", require("./modules/bookings/peminjaman.routes"));
api.use("/liaison-notes", require("./modules/liaison/buku_penghubung.routes"));
api.use(
  "/liaison-history",
  require("./modules/liaison/baca_buku_penghubung.routes"),
);
api.use(
  "/homeroom-profiles",
  require("./modules/student-profiles/profil_siswa.routes"),
);
api.use("/elective-surveys", require("./modules/elective/survei.routes"));
api.use(
  "/student-elective-surveys",
  require("./modules/elective/isi_survei.routes"),
);

app.use("/api/v1", api);

app.use(notFoundHandler);
app.use(errorHandler);

module.exports = app;
