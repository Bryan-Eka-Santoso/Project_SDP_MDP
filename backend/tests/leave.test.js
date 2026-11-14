const { createLeaveBody, leaveListQuery } = require('../src/modules/attendance/leave.validation');
describe('IZN-01 dan IZN-02 - validasi', () => {
  it('menolak tanggal akhir sebelum tanggal awal', () => {
    expect(createLeaveBody.safeParse({
      studentId: 1, startDate: '2026-11-12', endDate: '2026-11-11', reason: 'Sakit demam',
    }).success).toBe(false);
  });
  it('menolak alasan kosong dan ID siswa tidak valid', () => {
    expect(createLeaveBody.safeParse({
      studentId: -1, startDate: '2026-11-12', endDate: '2026-11-12', reason: '',
    }).success).toBe(false);
  });
  it('memiliki pagination default', () => {
    expect(leaveListQuery.parse({})).toEqual({ page: 1, limit: 20 });
  });
});
