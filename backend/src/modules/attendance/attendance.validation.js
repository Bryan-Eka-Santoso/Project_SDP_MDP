const { z } = require('zod');

const positiveId = z.coerce.number().int().positive();
const dateString = z.iso.date();

const slotParams = z.object({
  slotId: positiveId,
});

const dateQuery = z.object({
  date: dateString,
});

const saveAttendanceBody = z.object({
  date: dateString,
  journal: z.string().trim().min(1).max(255),
  attendances: z.array(z.object({
    studentId: positiveId,
    status: z.enum(['PRESENT', 'SICK', 'EXCUSED', 'ABSENT']),
  })).min(1),
}).superRefine((data, ctx) => {
  const ids = data.attendances.map((a) => a.studentId);
  if (new Set(ids).size !== ids.length) {
    ctx.addIssue({
      code: 'custom',
      path: ['attendances'],
      message: 'Satu siswa tidak boleh dicantumkan lebih dari sekali.',
    });
  }
});

module.exports = { slotParams, dateQuery, saveAttendanceBody };
