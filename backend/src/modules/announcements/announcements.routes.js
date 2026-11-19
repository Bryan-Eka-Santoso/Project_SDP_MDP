const express = require('express');
const authenticate = require('../../middlewares/authenticate');
const authorize = require('../../middlewares/authorize');
const validate = require('../../middlewares/validate');
const { uploadAttachment } = require('../../middlewares/upload');
const { assertCanUploadAttachment } = require('./announcements.policy');
const controller = require('./announcements.controller');
const {
  idParams,
  listAnnouncementsQuery,
  createAnnouncementBody,
  updateAnnouncementBody,
} = require('./announcements.validation');

const router = express.Router();

router.use(authenticate);

// Lampiran Cloudinary (JPG/PNG/PDF, maks. 5 MB) — cek hak akses sebelum menampung file.
async function ensureAttachmentUploader(req, res, next) {
  await assertCanUploadAttachment(req.user);
  return next();
}

router.post(
  '/attachment',
  authorize('ADMIN', 'TEACHER'),
  ensureAttachmentUploader,
  uploadAttachment('file'),
  controller.uploadAttachment,
);

// F-PGM-03: Baca pengumuman sesuai cakupan peran
router.get('/', validate({ query: listAnnouncementsQuery }), controller.listAnnouncements);
router.get('/:id', validate({ params: idParams }), controller.getAnnouncementById);

// F-PGM-01 (Admin -> SCHOOL) & F-PGM-02 (Wali kelas -> CLASS binaan)
router.post(
  '/',
  authorize('ADMIN', 'TEACHER'),
  validate({ body: createAnnouncementBody }),
  controller.createAnnouncement,
);
router.patch(
  '/:id',
  authorize('ADMIN', 'TEACHER'),
  validate({ params: idParams, body: updateAnnouncementBody }),
  controller.updateAnnouncement,
);
router.delete(
  '/:id',
  authorize('ADMIN', 'TEACHER'),
  validate({ params: idParams }),
  controller.deleteAnnouncement,
);

module.exports = router;
