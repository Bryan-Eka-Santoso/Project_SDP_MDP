const express = require('express');
const { sendSuccess } = require('./utils/response');
const { errorHandler, notFoundHandler } = require('./middlewares/errorHandler');
const env = require('./config/env');

const app = express();

// Jangan umumkan versi Express di header response.
app.disable('x-powered-by');
// Di belakang reverse proxy, isi TRUST_PROXY (jumlah proxy) agar IP klien terbaca benar.
if (env.trustProxy > 0) app.set('trust proxy', env.trustProxy);
// Batas body JSON; upload file lewat multipart (Multer), bukan JSON.
app.use(express.json({ limit: '1mb' }));

const api = express.Router();

api.get('/health', (req, res) => {
  sendSuccess(res, { status: 'ok' });
});

// Router modul. Router auth memuat path /auth/* dan /me sekaligus.
api.use(require('./modules/auth/auth.routes'));
api.use(require('./modules/notifications/notifications.routes'));
api.use(require('./modules/schedules/schedules.routes'));
api.use(require('./modules/bills/bills.routes'));
api.use(require('./modules/reports/reports.routes'));
api.use('/users', require('./modules/users/users.routes'));
api.use(require('./modules/master/master.routes'));


app.use('/api/v1', api);

app.use(notFoundHandler);
app.use(errorHandler);

module.exports = app;
