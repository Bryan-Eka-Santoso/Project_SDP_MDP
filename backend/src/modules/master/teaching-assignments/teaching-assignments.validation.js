const { z } = require('zod');
const { paginationQuery } = require('../../../utils/pagination');

const idParams = z.object({
  id: z.coerce.number().int().positive({ message: 'ID penugasan harus berupa bilangan bulat positif.' }),
});

const listTeachingAssignmentsQuery = paginationQuery.extend({
  semesterId: z.coerce.number().int().positive().optional(),
  teacherId: z.coerce.number().int().positive().optional(),
  classGroupId: z.coerce.number().int().positive().optional(),
  subjectId: z.coerce.number().int().positive().optional(),
});

const createTeachingAssignmentBody = z.object({
  teacherId: z.coerce.number().int().positive({ message: 'ID guru tidak valid.' }),
  subjectId: z.coerce.number().int().positive({ message: 'ID mata pelajaran tidak valid.' }),
  classGroupId: z.coerce.number().int().positive({ message: 'ID kelas tidak valid.' }),
  semesterId: z.coerce.number().int().positive({ message: 'ID semester tidak valid.' }),
});

const updateTeachingAssignmentBody = z.object({
  teacherId: z.coerce.number().int().positive({ message: 'ID guru tidak valid.' }),
});

module.exports = {
  idParams,
  listTeachingAssignmentsQuery,
  createTeachingAssignmentBody,
  updateTeachingAssignmentBody,
};
