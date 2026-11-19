const express = require('express');
const authenticate = require('../../middlewares/authenticate');
const authorize = require('../../middlewares/authorize');
const validate = require('../../middlewares/validate');
const controller = require('./calendar.controller');
const {
  idParams,
  listEventsQuery,
  createEventBody,
  updateEventBody,
} = require('./calendar.validation');

const router = express.Router();

router.use(authenticate);

// F-KAL-02: Lihat kalender akademik (semua role)
router.get('/', validate({ query: listEventsQuery }), controller.listEvents);
router.get('/:id', validate({ params: idParams }), controller.getEventById);

// F-KAL-01: Kelola kalender akademik (hanya Admin)
router.post(
  '/',
  authorize('ADMIN'),
  validate({ body: createEventBody }),
  controller.createEvent,
);
router.patch(
  '/:id',
  authorize('ADMIN'),
  validate({ params: idParams, body: updateEventBody }),
  controller.updateEvent,
);
router.delete(
  '/:id',
  authorize('ADMIN'),
  validate({ params: idParams }),
  controller.deleteEvent,
);

module.exports = router;
