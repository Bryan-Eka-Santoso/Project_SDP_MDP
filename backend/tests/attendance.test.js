process.env.JWT_ACCESS_SECRET =
  "test-secret-yang-panjangnya-lebih-dari-32-karakter";
process.env.JWT_ACCESS_TTL = "15m";
process.env.JWT_REFRESH_TTL = "7d";

jest.mock("../src/lib/prisma", () => {
  const fn = () => jest.fn();
  const db = {
    user: { findUnique: fn() },
    teacher: { findUnique: fn() },
    scheduleSlot: { findUnique: fn(), findMany: fn() },
    scheduleChange: { findUnique: fn() },
    classMember: { findMany: fn() },
    lessonSession: { findUnique: fn(), upsert: fn() },
    attendance: { upsert: fn() },
    leaveRequest: { findMany: fn() },
    $transaction: fn(),
  };
  db.$transaction.mockImplementation((callback) => callback(db));
  return db;
});

const request = require("supertest");
const jwt = require("jsonwebtoken");
const prisma = require("../src/lib/prisma");
const app = require("../src/app");
const service = require("../src/modules/attendance/attendance.service");

const token = (role = "TEACHER") =>
  jwt.sign({ role }, process.env.JWT_ACCESS_SECRET, {
    algorithm: "HS256",
    subject: "4",
    expiresIn: "15m",
  });

const date = "2026-11-09"; // Senin
const slot = {
  id: 1,
  dayOfWeek: "MONDAY",
  teachingAssignment: {
    id: 1,
    teacherId: 2,
    semesterId: 1,
    semester: {
      isActive: true,
      startDate: new Date("2026-07-01T00:00:00.000Z"),
      endDate: new Date("2026-12-31T00:00:00.000Z"),
    },
    subject: { id: 1, name: "Matematika" },
    classGroup: { id: 1, name: "X-1", academicYearId: 1 },
    teacher: { user: { name: "Yohanes" } },
  },
};
const members = [
  { studentId: 1, student: { nis: "26001", user: { name: "Angela" } } },
  { studentId: 2, student: { nis: "26002", user: { name: "Benediktus" } } },
];

beforeEach(() => {
  for (const value of Object.values(prisma)) {
    if (typeof value === "function" && value.mockReset) value.mockReset();
    else if (typeof value === "object") {
      for (const fn of Object.values(value)) {
        if (typeof fn === "function" && fn.mockReset) fn.mockReset();
      }
    }
  }
  prisma.$transaction.mockImplementation((callback) => callback(prisma));
  prisma.user.findUnique.mockResolvedValue({
    id: 4,
    role: "TEACHER",
    status: "ACTIVE",
  });
  prisma.teacher.findUnique.mockResolvedValue({ id: 2 });
  prisma.scheduleSlot.findUnique.mockResolvedValue(slot);
  prisma.scheduleChange.findUnique.mockResolvedValue(null);
  prisma.classMember.findMany.mockResolvedValue(members);
  prisma.leaveRequest.findMany.mockResolvedValue([]);
  prisma.lessonSession.upsert.mockResolvedValue({ id: 8 });
  prisma.attendance.upsert.mockResolvedValue({});
});

describe("ABS-01 / ABS-05 / ABS-02 API", () => {
  it("route tidak dikenal tetap 404, bukan 401", async () => {
    const res = await request(app).get("/api/v1/tidak-ada");
    expect(res.status).toBe(404);
  });

  it("tanpa token menghasilkan 401", async () => {
    const res = await request(app).get("/api/v1/me/attendance-schedule-slots");
    expect(res.status).toBe(401);
  });

  it("role salah menghasilkan 403", async () => {
    prisma.user.findUnique.mockResolvedValue({
      id: 4,
      role: "STUDENT",
      status: "ACTIVE",
    });
    const res = await request(app)
      .get("/api/v1/me/attendance-schedule-slots")
      .set("Authorization", `Bearer ${token("STUDENT")}`);
    expect(res.status).toBe(403);
  });

  it("daftar siswa kelas yang diampu bisa dibaca", async () => {
    const res = await request(app)
      .get("/api/v1/schedule-slots/1/students")
      .set("Authorization", `Bearer ${token()}`);
    expect(res.status).toBe(200);
    expect(res.body.data.map((x) => x.name)).toEqual(["Angela", "Benediktus"]);
  });

  it("slot milik guru lain disembunyikan sebagai 404", async () => {
    prisma.teacher.findUnique.mockResolvedValue({ id: 999 });
    const res = await request(app)
      .get("/api/v1/schedule-slots/1/students")
      .set("Authorization", `Bearer ${token()}`);
    expect(res.status).toBe(404);
  });

  it("menolak duplikat siswa dan daftar presensi tidak lengkap", async () => {
    const send = (attendances) =>
      request(app)
        .post("/api/v1/schedule-slots/1/attendance")
        .set("Authorization", `Bearer ${token()}`)
        .send({ date, journal: "Pecahan", attendances });
    const duplicate = await send([
      { studentId: 1, status: "PRESENT" },
      { studentId: 1, status: "ABSENT" },
    ]);
    expect(duplicate.status).toBe(400);
    const missing = await send([{ studentId: 1, status: "PRESENT" }]);
    expect(missing.status).toBe(400);
    expect(prisma.lessonSession.upsert).not.toHaveBeenCalled();
  });

  it("menyimpan jurnal dan semua presensi dalam transaksi", async () => {
    const res = await request(app)
      .post("/api/v1/schedule-slots/1/attendance")
      .set("Authorization", `Bearer ${token()}`)
      .send({
        date,
        journal: "Pecahan",
        attendances: [
          { studentId: 1, status: "PRESENT" },
          { studentId: 2, status: "ABSENT" },
        ],
      });
    expect(res.status).toBe(200);
    expect(res.body.data.count).toBe(2);
    expect(prisma.$transaction).toHaveBeenCalledTimes(1);
    expect(prisma.lessonSession.upsert).toHaveBeenCalledTimes(1);
    expect(prisma.attendance.upsert).toHaveBeenCalledTimes(2);
  });

  it("izin approved selalu menjadi EXCUSED", async () => {
    prisma.leaveRequest.findMany.mockResolvedValue([{ studentId: 1 }]);
    await service.saveSession(4, 1, {
      date,
      journal: "Pecahan",
      attendances: [
        { studentId: 1, status: "PRESENT" },
        { studentId: 2, status: "PRESENT" },
      ],
    });
    expect(prisma.attendance.upsert.mock.calls[0][0].create.status).toBe(
      "EXCUSED",
    );
  });

  it("tanggal tidak sesuai slot ditolak", async () => {
    const res = await request(app)
      .post("/api/v1/schedule-slots/1/attendance")
      .set("Authorization", `Bearer ${token()}`)
      .send({
        date: "2026-11-10",
        journal: "Pecahan",
        attendances: [
          { studentId: 1, status: "PRESENT" },
          { studentId: 2, status: "PRESENT" },
        ],
      });
    expect(res.status).toBe(400);
  });

  it("guru pengganti dapat menyimpan hanya pada tanggal penggantian", async () => {
    prisma.teacher.findUnique.mockResolvedValue({ id: 99 });
    prisma.scheduleChange.findUnique.mockResolvedValue({
      type: "SUBSTITUTE",
      substituteTeacherId: 99,
    });
    const res = await request(app)
      .post("/api/v1/schedule-slots/1/attendance")
      .set("Authorization", `Bearer ${token()}`)
      .send({
        date,
        journal: "Pecahan",
        attendances: [
          { studentId: 1, status: "PRESENT" },
          { studentId: 2, status: "PRESENT" },
        ],
      });
    expect(res.status).toBe(200);
  });
});
