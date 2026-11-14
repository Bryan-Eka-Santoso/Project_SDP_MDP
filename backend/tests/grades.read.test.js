const {
  calculateGrade,
  findPredicate,
} = require("../src/modules/grades/grades.read.service");

const parts = [
  { id: 1, name: "Tugas", weight: 20 },
  { id: 2, name: "Ulangan Harian", weight: 30 },
  { id: 3, name: "UTS", weight: 20 },
  { id: 4, name: "UAS", weight: 30 },
];

describe("NIL-03 dan NIL-04: kalkulasi nilai", () => {
  test("nilai akhir menghitung bobot yang benar", () => {
    const result = calculateGrade(
      parts,
      new Map([
        [1, 50],
        [2, 100],
        [3, 90],
        [4, 100],
      ]),
    );
    expect(result.complete).toBe(true);
    expect(result.finalScore).toBe(88);
  });
  test("komponen kosong tidak dianggap nol", () => {
    const result = calculateGrade(parts, new Map([[1, 80]]));
    expect(result.complete).toBe(false);
    expect(result.finalScore).toBeNull();
    expect(result.components[1].value).toBeNull();
  });
  test("total bobot kurang dari 100 dianggap belum lengkap", () => {
    const result = calculateGrade(
      [{ id: 1, name: "Tugas", weight: 20 }],
      new Map([[1, 80]]),
    );
    expect(result.complete).toBe(false);
    expect(result.finalScore).toBeNull();
  });
});

describe("NIL-03 predikat dari skala database", () => {
  const predicates = [
    { label: "C", minScore: 70 },
    { label: "A", minScore: 90 },
    { label: "D", minScore: 0 },
    { label: "B", minScore: 80 },
  ];
  test("nilai 88 mendapat B sesuai ambang database", () => {
    expect(findPredicate(88, predicates)).toBe("B");
  });
  test("nilai pada batas minimum benar", () => {
    expect(findPredicate(90, predicates)).toBe("A");
    expect(findPredicate(79.5, predicates)).toBe("C");
    expect(findPredicate(0, predicates)).toBe("D");
  });
  test("nilai belum lengkap tidak punya predikat", () => {
    expect(findPredicate(null, predicates)).toBeNull();
  });
  test("jika skala tidak ditemukan predikat null", () => {
    expect(findPredicate(85, [])).toBeNull();
  });
});
