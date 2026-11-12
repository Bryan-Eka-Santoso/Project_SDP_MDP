const express = require('express');
const authenticate = require('../../middlewares/authenticate');
const authorize = require('../../middlewares/authorize');
const validate = require('../../middlewares/validate');
const { uploadXlsx } = require('../../middlewares/upload');
const controller = require('./schedules.controller');
const {
  idParams,
  createSlotBody,
  updateSlotBody,
  listSlotsQuery,
  saveChangeBody,
  listChangesQuery,
  importSlotsBody,
  viewQuery,
  createTaskBody,
  updateTaskBody,
  listTasksQuery,
} = require('./schedules.validation');

const router = express.Router();

// F-JDW-01..04. Kelola jadwal dan perubahan jadwal hanya admin (docs/PERMISSIONS.md).
const admin = [authenticate, authorize('ADMIN')];

// F-JDW-05. Jadwal untuk siswa, guru (termasuk wali kelas), dan wali murid; admin memakai master jadwal.
router.get(
  '/schedule',
  authenticate,
  authorize('STUDENT', 'TEACHER', 'GUARDIAN'),
  validate({ query: viewQuery }),
  controller.viewSchedule,
);

// F-JDW-08/09. Guru mengelola tugas penugasannya sendiri; siswa melihat tugas kelasnya.
const teacher = [authenticate, authorize('TEACHER')];
// Pilihan kelas + mapel untuk form tugas; hanya penugasan milik guru yang login.
router.get('/me/teaching-assignments', ...teacher, controller.listMyTeachingAssignments);
router.get('/tasks', authenticate, authorize('TEACHER', 'STUDENT'), validate({ query: listTasksQuery }), controller.listTasks);
router.get('/tasks/:id', authenticate, authorize('TEACHER', 'STUDENT'), validate({ params: idParams }), controller.getTask);
router.post('/tasks', ...teacher, validate({ body: createTaskBody }), controller.createTask);
router.patch('/tasks/:id', ...teacher, validate({ params: idParams, body: updateTaskBody }), controller.updateTask);
router.delete('/tasks/:id', ...teacher, validate({ params: idParams }), controller.deleteTask);

router.get('/schedule-slots', ...admin, validate({ query: listSlotsQuery }), controller.listSlots);
// F-JDW-02. Upload dibaca setelah authenticate + authorize; semesterId (opsional) ikut di form.
router.post(
  '/schedule-slots/import',
  ...admin,
  uploadXlsx('file'),
  validate({ body: importSlotsBody }),
  controller.importSlots,
);
router.get('/schedule-slots/:id', ...admin, validate({ params: idParams }), controller.getSlot);
router.post('/schedule-slots', ...admin, validate({ body: createSlotBody }), controller.createSlot);
router.patch(
  '/schedule-slots/:id',
  ...admin,
  validate({ params: idParams, body: updateSlotBody }),
  controller.updateSlot,
);
router.delete('/schedule-slots/:id', ...admin, validate({ params: idParams }), controller.deleteSlot);

// F-JDW-04. POST untuk tanggal yang sudah punya perubahan = memperbarui perubahan itu.
router.get('/schedule-changes', ...admin, validate({ query: listChangesQuery }), controller.listChanges);
router.get('/schedule-changes/:id', ...admin, validate({ params: idParams }), controller.getChange);
router.post('/schedule-changes', ...admin, validate({ body: saveChangeBody }), controller.saveChange);
router.delete('/schedule-changes/:id', ...admin, validate({ params: idParams }), controller.deleteChange);

module.exports = router;
