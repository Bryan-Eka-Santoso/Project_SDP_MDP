const { paginationQuery, toPrismaPagination } = require('../src/utils/pagination');
const { parseDateOnly, formatDateOnly, todayLocal } = require('../src/utils/date');

describe('pagination', () => {
  it('default page 1, limit 20', () => {
    expect(paginationQuery.parse({})).toEqual({ page: 1, limit: 20 });
  });

  it('menolak limit di atas 100', () => {
    expect(paginationQuery.safeParse({ limit: '101' }).success).toBe(false);
  });

  it('mengubah ke skip/take Prisma', () => {
    expect(toPrismaPagination({ page: 3, limit: 20 })).toEqual({ skip: 40, take: 20 });
  });
});

describe('date', () => {
  it('parse dan format tanggal bolak-balik', () => {
    const date = parseDateOnly('2026-11-04');
    expect(date.toISOString()).toBe('2026-11-04T00:00:00.000Z');
    expect(formatDateOnly(date)).toBe('2026-11-04');
  });

  it('menolak tanggal yang tidak ada', () => {
    expect(() => parseDateOnly('2026-02-30')).toThrow(RangeError);
    expect(() => parseDateOnly('04-11-2026')).toThrow(RangeError);
  });

  it('hari ini memakai WIB, bukan UTC', () => {
    // 3 Nov 2026 18.30 UTC = 4 Nov 2026 01.30 WIB
    expect(todayLocal(new Date('2026-11-03T18:30:00Z'), 'Asia/Jakarta')).toBe('2026-11-04');
  });
});
