const express = require("express");

const router = express.Router();

// F-MST-06: Master tahun ajaran dan semester
router.use(
  "/academic-years",
  require("./academic-years/academic-years.routes"),
);
router.use("/semesters", require("./academic-years/semesters.routes"));

// F-MST-05: Master guru
router.use("/teachers", require("./teachers/teachers.routes"));

// F-MST-04: Master siswa dan wali murid
router.use("/students", require("./students/students.routes"));
router.use("/guardians", require("./students/guardians.routes"));

// F-MST-07, F-MST-08, F-MST-09: Master kelas, wali kelas, dan siswa dalam kelas
router.use("/classes", require("./classes/classes.routes"));

// F-MST-10: Penugasan guru mengajar per mapel dan kelas
router.use(
  "/teaching-assignments",
  require("./teaching-assignments/teaching-assignments.routes"),
);

router.use("/subjects", require("./subjects/mata_pelajaran.routes"));

router.use("/facilities", require("./fasilitas/fasilitas.routes"));

router.use(
  "/violation-types",
  require("./jenis-pelanggaran/jenis_pelanggaran.routes"),
);

module.exports = router;
