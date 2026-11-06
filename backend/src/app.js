const express = require('express');
const { sendSuccess } = require('./utils/response');
const { errorHandler, notFoundHandler } = require('./middlewares/errorHandler');

const app = express();

// Jangan umumkan versi Express di header response.
app.disable('x-powered-by');
// Batas body JSON; upload file lewat multipart (Multer), bukan JSON.
app.use(express.json({ limit: '1mb' }));

const api = express.Router();

api.get('/health', (req, res) => {
  sendSuccess(res, { status: 'ok' });
});

// Router modul. Router auth memuat path /auth/* dan /me sekaligus.
api.use(require('./modules/auth/auth.routes'));


app.use('/api/v1', api);

app.use(notFoundHandler);
app.use(errorHandler);

module.exports = app;
