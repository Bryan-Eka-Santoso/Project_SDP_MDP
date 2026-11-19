const { z } = require('zod');
const { paginationQuery } = require('../../utils/pagination');

const positiveId = z.coerce.number().int().positive();
const assignmentParams = z.object({ assignmentId: positiveId });
const studentParams = z.object({ studentId: positiveId });
const noteParams = z.object({ noteId: positiveId });
const dateString = z.iso.date();
const createBody = z.object({
  teachingAssignmentId: z.number().int().positive(),
  studentId: z.number().int().positive(),
  date: dateString,
  content: z.string().trim().min(1).max(500),
});
const updateBody = z.object({
  date: dateString.optional(),
  content: z.string().trim().min(1).max(500).optional(),
}).refine((value) => Object.keys(value).length > 0, { message: 'Isi minimal satu perubahan.' });
const studentListQuery = paginationQuery.extend({ teachingAssignmentId: positiveId.optional() });
const assignmentListQuery = paginationQuery.extend({ studentId: positiveId.optional() });

module.exports = { assignmentParams, studentParams, noteParams, createBody, updateBody, studentListQuery, assignmentListQuery };
