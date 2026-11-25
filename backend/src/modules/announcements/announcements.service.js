// Pengumuman sekolah (F-PGM-01), kelas binaan (F-PGM-02), dan lihat pengumuman (F-PGM-03).
const prisma = require('../../lib/prisma');
const AppError = require('../../utils/AppError');
const cloudinary = require('../../lib/cloudinary');
const { notify } = require('../notifications/notifications.service');
const {
  activeUserIdsByRoles,
  studentUserIdsOfClasses,
  guardianUserIdsOfClasses,
} = require('../notifications/notifications.recipients');
const { toPrismaPagination } = require('../../utils/pagination');
const { formatDateOnly, parseDateOnly, todayLocal } = require('../../utils/date');
const policy = require('./announcements.policy');

const ANNOUNCEMENT_SELECT = {
  id: true,
  authorId: true,
  classGroupId: true,
  title: true,
  content: true,
  scope: true,
  date: true,
  attachmentUrl: true,
  attachmentPublicId: true,
  createdAt: true,
  updatedAt: true,
  author: { select: { id: true, name: true, role: true } },
  classGroup: {
    select: {
      id: true,
      name: true,
      homeroomTeacher: { select: { userId: true } },
    },
  },
};

function mapAnnouncement(item) {
  return {
    id: item.id,
    title: item.title,
    content: item.content,
    scope: item.scope,
    classGroupId: item.classGroupId,
    classGroup: item.classGroup ? { id: item.classGroup.id, name: item.classGroup.name } : null,
    author: item.author,
    date: formatDateOnly(item.date),
    attachmentUrl: item.attachmentUrl,
    attachmentPublicId: item.attachmentPublicId,
    createdAt: item.createdAt,
    updatedAt: item.updatedAt,
  };
}

function toSafeDateOnly(iso) {
  try {
    return parseDateOnly(iso);
  } catch {
    throw new AppError(400, 'VALIDATION_ERROR', 'Tanggal pengumuman tidak valid.', [
      { field: 'body.date', message: `Tanggal "${iso}" tidak valid di kalender.` },
    ]);
  }
}

async function dispatchAnnouncementNotification(created, senderUserId) {
  let userIds;
  let title;

  if (created.scope === 'SCHOOL') {
    const allIds = await activeUserIdsByRoles(['STUDENT', 'TEACHER', 'GUARDIAN', 'ADMIN']);
    userIds = allIds.filter((id) => id !== senderUserId);
    title = `Pengumuman: ${created.title}`;
  } else {
    const [studentIds, guardianIds] = await Promise.all([
      studentUserIdsOfClasses([created.classGroupId]),
      guardianUserIdsOfClasses([created.classGroupId]),
    ]);
    const homeroomUserId = created.classGroup?.homeroomTeacher?.userId;
    userIds = [...new Set([...studentIds, ...guardianIds, ...(homeroomUserId ? [homeroomUserId] : [])])].filter(
      (id) => id !== senderUserId,
    );
    const className = created.classGroup?.name ?? 'Kelas';
    title = `Pengumuman ${className}: ${created.title}`;
  }

  if (userIds.length > 0) {
    notify(userIds, {
      type: 'ANNOUNCEMENT',
      title,
      body: created.content,
      referenceId: created.id,
    });
  }
}

async function uploadAttachment(file, { baseUrl } = {}) {
  return cloudinary.uploadBuffer({
    buffer: file.buffer,
    originalname: file.originalname,
    mimetype: file.mimetype,
    folder: 'sim/announcements',
    baseUrl,
  });
}

async function listAnnouncements(user, query) {
  const where = await policy.buildReadWhere(user, query);
  const { page, limit } = query;

  const [total, items] = await Promise.all([
    prisma.announcement.count({ where }),
    prisma.announcement.findMany({
      where,
      select: ANNOUNCEMENT_SELECT,
      ...toPrismaPagination({ page, limit }),
      orderBy: [{ date: 'desc' }, { id: 'desc' }],
    }),
  ]);

  return { announcements: items.map(mapAnnouncement), total };
}

async function getAnnouncementById(user, id) {
  const scopeWhere = await policy.buildReadWhere(user, {});
  const item = await prisma.announcement.findFirst({
    where: { AND: [{ id }, scopeWhere] },
    select: ANNOUNCEMENT_SELECT,
  });
  if (!item) {
    throw new AppError(404, 'NOT_FOUND', 'Pengumuman tidak ditemukan.');
  }
  return mapAnnouncement(item);
}

async function createAnnouncement(user, body) {
  await policy.assertCanCreate(user, body);
  const dateObj = toSafeDateOnly(body.date ?? todayLocal());

  const created = await prisma.announcement.create({
    data: {
      authorId: user.id,
      classGroupId: body.scope === 'CLASS' ? body.classGroupId : null,
      title: body.title,
      content: body.content,
      scope: body.scope,
      date: dateObj,
      attachmentUrl: body.attachmentUrl ?? null,
      attachmentPublicId: body.attachmentPublicId ?? null,
    },
    select: ANNOUNCEMENT_SELECT,
  });

  // Notifikasi dikirim setelah row tersimpan, kegagalan FCM tidak membatalkan response.
  await dispatchAnnouncementNotification(created, user.id).catch((err) => {
    console.error('[pengumuman] gagal menyiapkan penerima notifikasi:', err);
  });

  return mapAnnouncement(created);
}

async function updateAnnouncement(user, id, body) {
  const existing = await prisma.announcement.findUnique({
    where: { id },
    select: {
      id: true,
      scope: true,
      classGroupId: true,
      attachmentPublicId: true,
    },
  });
  if (!existing) {
    throw new AppError(404, 'NOT_FOUND', 'Pengumuman tidak ditemukan.');
  }
  await policy.assertCanManage(user, existing);

  const nextAttachmentPublicId =
    body.attachmentUrl === null
      ? null
      : (body.attachmentPublicId ?? undefined);

  const updated = await prisma.announcement.update({
    where: { id },
    data: {
      ...(body.title !== undefined && { title: body.title }),
      ...(body.content !== undefined && { content: body.content }),
      ...(body.date !== undefined && { date: toSafeDateOnly(body.date) }),
      ...(body.attachmentUrl !== undefined && { attachmentUrl: body.attachmentUrl }),
      ...(nextAttachmentPublicId !== undefined && { attachmentPublicId: nextAttachmentPublicId }),
    },
    select: ANNOUNCEMENT_SELECT,
  });

  if (
    existing.attachmentPublicId &&
    nextAttachmentPublicId !== undefined &&
    existing.attachmentPublicId !== nextAttachmentPublicId
  ) {
    cloudinary.destroyAsset(existing.attachmentPublicId).catch(() => {});
  }

  return mapAnnouncement(updated);
}

async function deleteAnnouncement(user, id) {
  const existing = await prisma.announcement.findUnique({
    where: { id },
    select: {
      id: true,
      scope: true,
      classGroupId: true,
      attachmentPublicId: true,
    },
  });
  if (!existing) {
    throw new AppError(404, 'NOT_FOUND', 'Pengumuman tidak ditemukan.');
  }
  await policy.assertCanManage(user, existing);

  await prisma.announcement.delete({ where: { id } });

  if (existing.attachmentPublicId) {
    cloudinary.destroyAsset(existing.attachmentPublicId).catch(() => {});
  }
}

module.exports = {
  uploadAttachment,
  listAnnouncements,
  getAnnouncementById,
  createAnnouncement,
  updateAnnouncement,
  deleteAnnouncement,
};
