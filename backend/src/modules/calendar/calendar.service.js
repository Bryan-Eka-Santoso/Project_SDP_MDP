// Kalender akademik (F-KAL-01 kelola admin, F-KAL-02 baca semua user).
const prisma = require('../../lib/prisma');
const AppError = require('../../utils/AppError');
const { toPrismaPagination } = require('../../utils/pagination');
const { formatDateOnly, parseDateOnly } = require('../../utils/date');

function mapEvent(item) {
  return {
    id: item.id,
    title: item.title,
    type: item.type,
    startDate: formatDateOnly(item.startDate),
    endDate: formatDateOnly(item.endDate),
    description: item.description,
    createdAt: item.createdAt,
    updatedAt: item.updatedAt,
  };
}

function toSafeDateOnly(iso, field) {
  try {
    return parseDateOnly(iso);
  } catch {
    throw new AppError(400, 'VALIDATION_ERROR', 'Tanggal tidak valid.', [
      { field, message: `Tanggal "${iso}" tidak valid di kalender.` },
    ]);
  }
}

async function listEvents({ page, limit, type, from, to, q }) {
  const where = {};
  if (type) where.type = type;
  if (from) where.endDate = { gte: toSafeDateOnly(from, 'query.from') };
  if (to) where.startDate = { lte: toSafeDateOnly(to, 'query.to') };
  if (q) {
    where.OR = [
      { title: { contains: q } },
      { description: { contains: q } },
    ];
  }

  const [total, items] = await Promise.all([
    prisma.academicEvent.count({ where }),
    prisma.academicEvent.findMany({
      where,
      ...toPrismaPagination({ page, limit }),
      orderBy: [{ startDate: 'asc' }, { id: 'asc' }],
    }),
  ]);

  return { events: items.map(mapEvent), total };
}

async function getEventById(id) {
  const item = await prisma.academicEvent.findUnique({ where: { id } });
  if (!item) {
    throw new AppError(404, 'NOT_FOUND', 'Agenda kalender tidak ditemukan.');
  }
  return mapEvent(item);
}

async function createEvent({ title, type, startDate, endDate, description }) {
  const start = toSafeDateOnly(startDate, 'body.startDate');
  const end = toSafeDateOnly(endDate, 'body.endDate');

  const created = await prisma.academicEvent.create({
    data: {
      title,
      type,
      startDate: start,
      endDate: end,
      description: description ?? null,
    },
  });

  return mapEvent(created);
}

async function updateEvent(id, { title, type, startDate, endDate, description }) {
  const existing = await prisma.academicEvent.findUnique({ where: { id } });
  if (!existing) {
    throw new AppError(404, 'NOT_FOUND', 'Agenda kalender tidak ditemukan.');
  }

  const nextStartIso = startDate ?? formatDateOnly(existing.startDate);
  const nextEndIso = endDate ?? formatDateOnly(existing.endDate);
  const nextStart = toSafeDateOnly(nextStartIso, 'body.startDate');
  const nextEnd = toSafeDateOnly(nextEndIso, 'body.endDate');

  if (nextStart > nextEnd) {
    throw new AppError(400, 'VALIDATION_ERROR', 'Data yang dikirim tidak valid.', [
      { field: 'body.endDate', message: 'Tanggal selesai tidak boleh sebelum tanggal mulai.' },
    ]);
  }

  const updated = await prisma.academicEvent.update({
    where: { id },
    data: {
      ...(title !== undefined && { title }),
      ...(type !== undefined && { type }),
      ...(startDate !== undefined && { startDate: nextStart }),
      ...(endDate !== undefined && { endDate: nextEnd }),
      ...(description !== undefined && { description }),
    },
  });

  return mapEvent(updated);
}

async function deleteEvent(id) {
  const existing = await prisma.academicEvent.findUnique({
    where: { id },
    select: { id: true },
  });
  if (!existing) {
    throw new AppError(404, 'NOT_FOUND', 'Agenda kalender tidak ditemukan.');
  }
  await prisma.academicEvent.delete({ where: { id } });
}

module.exports = {
  listEvents,
  getEventById,
  createEvent,
  updateEvent,
  deleteEvent,
};
