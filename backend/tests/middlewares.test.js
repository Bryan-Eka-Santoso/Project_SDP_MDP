const express = require('express');
const request = require('supertest');
const { z } = require('zod');
const validate = require('../src/middlewares/validate');
const authorize = require('../src/middlewares/authorize');
const { errorHandler } = require('../src/middlewares/errorHandler');
const { sendSuccess } = require('../src/utils/response');
const { paginationQuery } = require('../src/utils/pagination');

// App kecil khusus test; `fakeUser` meniru hasil middleware authenticate.
function buildApp(...middlewares) {
  const app = express();
  app.use(express.json());
  app.post('/items/:id', ...middlewares, (req, res) =>
    sendSuccess(res, { validated: req.validated, body: req.body }),
  );
  app.use(errorHandler);
  return app;
}

const fakeUser = (role) => (req, res, next) => {
  req.user = { id: 1, role };
  next();
};

describe('validate', () => {
  const app = buildApp(
    validate({
      params: z.object({ id: z.coerce.number().int().positive() }),
      query: paginationQuery,
      body: z.object({ name: z.string().min(1) }),
    }),
  );

  it('meneruskan data hasil parse dan default', async () => {
    const res = await request(app).post('/items/7?page=2').send({ name: 'XI-1', extra: 'x' });

    expect(res.status).toBe(200);
    expect(res.body.data.validated).toEqual({
      params: { id: 7 },
      query: { page: 2, limit: 20 },
      body: { name: 'XI-1' },
    });
    // Field yang tidak ada di schema dibuang.
    expect(res.body.data.body).toEqual({ name: 'XI-1' });
  });

  it('mengembalikan 400 dengan details per field', async () => {
    const res = await request(app).post('/items/abc?limit=500').send({});

    expect(res.status).toBe(400);
    expect(res.body.success).toBe(false);
    expect(res.body.error.code).toBe('VALIDATION_ERROR');
    const fields = res.body.error.details.map((d) => d.field);
    expect(fields).toEqual(expect.arrayContaining(['params.id', 'query.limit', 'body.name']));
  });
});

describe('authorize', () => {
  it('401 jika belum login', async () => {
    const res = await request(buildApp(authorize('ADMIN'))).post('/items/1');
    expect(res.status).toBe(401);
    expect(res.body.error.code).toBe('UNAUTHENTICATED');
  });

  it('403 jika role tidak diizinkan', async () => {
    const res = await request(buildApp(fakeUser('STUDENT'), authorize('ADMIN', 'TEACHER'))).post(
      '/items/1',
    );
    expect(res.status).toBe(403);
    expect(res.body.error.code).toBe('FORBIDDEN');
  });

  it('lolos jika role diizinkan', async () => {
    const res = await request(buildApp(fakeUser('TEACHER'), authorize('ADMIN', 'TEACHER'))).post(
      '/items/1',
    );
    expect(res.status).toBe(200);
  });

  it('menolak dipasang tanpa role', () => {
    expect(() => authorize()).toThrow();
  });
});
