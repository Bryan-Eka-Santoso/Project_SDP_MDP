const { z } = require('zod');
const { paginationQuery } = require('../../utils/pagination');

const idParams = z.object({
  id: z.coerce.number().int().positive({ message: 'ID paket tidak valid.' }),
});

const listSubjectPackagesQuery = paginationQuery.extend({
  academicYearId: z.coerce.number().int().positive().optional(),
  q: z.string().trim().max(100).optional(),
});

const subjectIdsSchema = z
  .array(z.coerce.number().int().positive({ message: 'ID mata pelajaran tidak valid.' }))
  .min(1, { message: 'Pilih minimal satu mata pelajaran.' })
  .max(30, { message: 'Maksimal 30 mata pelajaran per paket.' })
  .refine((ids) => new Set(ids).size === ids.length, {
    message: 'Daftar mata pelajaran tidak boleh duplikat.',
  });

const createSubjectPackageBody = z.object({
  academicYearId: z.coerce
    .number({ message: 'Tahun ajaran wajib dipilih.' })
    .int()
    .positive({ message: 'ID tahun ajaran tidak valid.' }),
  name: z
    .string({ message: 'Nama paket wajib diisi.' })
    .trim()
    .min(1, { message: 'Nama paket wajib diisi.' })
    .max(100, { message: 'Nama paket maksimal 100 karakter.' }),
  capacity: z.coerce
    .number({ message: 'Kapasitas wajib berupa angka.' })
    .int({ message: 'Kapasitas harus berupa bilangan bulat.' })
    .positive({ message: 'Kapasitas minimal 1 siswa.' })
    .max(100, { message: 'Kapasitas maksimal 100 siswa per kelas.' }),
  subjectIds: subjectIdsSchema,
});

const updateSubjectPackageBody = z
  .object({
    name: z
      .string()
      .trim()
      .min(1, { message: 'Nama paket tidak boleh kosong.' })
      .max(100, { message: 'Nama paket maksimal 100 karakter.' })
      .optional(),
    capacity: z.coerce
      .number()
      .int({ message: 'Kapasitas harus berupa bilangan bulat.' })
      .positive({ message: 'Kapasitas minimal 1 siswa.' })
      .max(100, { message: 'Kapasitas maksimal 100 siswa per kelas.' })
      .optional(),
    subjectIds: subjectIdsSchema.optional(),
  })
  .refine(
    (data) =>
      data.name !== undefined ||
      data.capacity !== undefined ||
      data.subjectIds !== undefined,
    { message: 'Minimal satu field harus diisi untuk pembaruan.' },
  );

module.exports = {
  idParams,
  listSubjectPackagesQuery,
  createSubjectPackageBody,
  updateSubjectPackageBody,
};
