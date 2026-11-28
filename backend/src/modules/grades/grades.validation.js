const { z } = require('zod');
const { paginationQuery } = require('../../utils/pagination');

const id = z.coerce.number().int().positive();
const assignmentParams = z.object({ assignmentId: id });
const lookupQuery = z.object({
  includeHomeroom: z.enum(['true', 'false']).default('false').transform((value) => value === 'true'),
});
const componentParams = z.object({ id });
const componentBody = z.object({
  name: z.string().trim().min(1).max(50),
  weight: z.number().int().min(1).max(100),
});
const componentUpdateBody = componentBody.partial().refine((value) => Object.keys(value).length > 0);
const scoreBody = z.object({
  teachingAssignmentId: z.number().int().positive(),
  gradeComponentId: z.number().int().positive(),
  studentId: z.number().int().positive(),
  value: z.number().min(0).max(100).refine((v) => Math.round(v * 100) === v * 100, {
    message: 'Nilai maksimal dua angka desimal.',
  }),
});
const scoresQuery = paginationQuery.extend({ teachingAssignmentId: id });

const assessmentParams = z.object({ id });
const assessmentCreateBody = z.object({
  gradeComponentId: z.number().int().positive(),
  title: z.string().trim().min(1).max(80).optional(),
});
const assessmentScoreBody = z.object({
  studentId: z.number().int().positive(),
  value: z.number().min(0).max(100).refine((v) => Math.round(v * 100) === v * 100, { message: 'Maksimal dua desimal.' }),
});
module.exports = { lookupQuery, assignmentParams, componentParams, componentBody, componentUpdateBody, scoreBody, scoresQuery, assessmentParams, assessmentCreateBody, assessmentScoreBody };
