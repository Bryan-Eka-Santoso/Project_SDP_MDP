const grades = require('../src/modules/reports/reports.grades');
const attendance = require('../src/modules/reports/reports.attendance');
const journals = require('../src/modules/reports/reports.journals');
const reports = require('../src/modules/reports/reports.service');

test('LAP-01 bobot lengkap 100 persen dihitung tanpa menyimpan nilai akhir', () => {
  const result = grades.calculate([{ id: 1, weight: 40 }, { id: 2, weight: 60 }],
    [{ gradeComponentId: 1, value: 80 }, { gradeComponentId: 2, value: 90 }],
    [{ label: 'A', minScore: 85 }, { label: 'B', minScore: 70 }]);
  expect(result).toEqual({ complete: true, finalScore: 86, predicate: 'A' });
});
test('LAP-01 tidak mengarang nilai apabila komponen belum lengkap', () => {
  const result = grades.calculate([{ id: 1, weight: 40 }, { id: 2, weight: 60 }],
    [{ gradeComponentId: 1, value: 80 }], []);
  expect(result).toEqual({ complete: false, finalScore: null, predicate: null });
});
test('LAP-02 menghitung status terisi tanpa menganggap sesi tanpa data sebagai alpa', () => {
  expect(attendance.countStatuses([{ status: 'PRESENT' }, { status: 'ABSENT' },
    { status: 'EXCUSED' }, { status: 'SICK' }])).toEqual({
    present: 1, absent: 1, excused: 1, sick: 1, total: 4, attendanceRate: 25,
  });
  expect(attendance.countStatuses([]).attendanceRate).toBeNull();
});
test('LAP-02 dan LAP-06 menolak tanggal invalid dan urutan terbalik', () => {
  for (const module of [attendance, journals]) {
    expect(module.makeQuery({}).safeParse({ dateFrom: '2026-02-30' }).success).toBe(false);
    expect(module.makeQuery({}).safeParse({ dateFrom: '2026-11-20', dateTo: '2026-11-01' }).success).toBe(false);
  }
});
test('jenis laporan terdaftar dan format query berpaginasi konsisten', () => {
  for (const type of ['grades', 'attendance', 'teaching-journals']) {
    expect(reports.querySchema(type, 'list').parse({})).toEqual({ page: 1, limit: 20 });
    expect(reports.querySchema(type, 'export').parse({})).toEqual({});
  }
});
