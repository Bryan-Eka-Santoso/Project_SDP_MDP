const { z } = require('zod');
const { paginationQuery } = require('../../utils/pagination');

const studentParams = z.object({ studentId: z.coerce.number().int().positive() });
const assignmentParams = z.object({ assignmentId: z.coerce.number().int().positive() });
const optionalFilters = {
  startDate: z.iso.date().optional(),
  endDate: z.iso.date().optional(),
};
const dateRange = (schema) => schema.refine(
  ({ startDate, endDate }) => !startDate || !endDate || startDate <= endDate,
  { message: 'Tanggal mulai harus sebelum atau sama dengan tanggal akhir.', path: ['endDate'] },
);
const summaryQuery = dateRange(z.object(optionalFilters));
const journalQuery = dateRange(paginationQuery.extend({
  ...optionalFilters,
  teacherId: z.coerce.number().int().positive().optional(),
  classGroupId: z.coerce.number().int().positive().optional(),
}));

module.exports = { studentParams, assignmentParams, summaryQuery, journalQuery };
