const express = require('express');
const { sendSuccess } = require('./utils/response');
const { errorHandler, notFoundHandler } = require('./middlewares/errorHandler');

const app = express();

app.use(express.json());

const api = express.Router();

api.get('/health', (req, res) => {
  sendSuccess(res, { status: 'ok' });
});

// Router modul dipasang di sini, mis. api.use('/auth', require('./modules/auth/auth.routes'));

app.use('/api/v1', api);

app.use(notFoundHandler);
app.use(errorHandler);

module.exports = app;
