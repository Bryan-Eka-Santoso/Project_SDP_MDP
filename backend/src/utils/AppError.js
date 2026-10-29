// Error yang aman dikirim ke client. `code` UPPER_SNAKE_CASE (docs/API_CONVENTIONS.md bagian 5),
// `message` berbahasa Indonesia.
class AppError extends Error {
  constructor(statusCode, code, message, details) {
    super(message);
    this.name = 'AppError';
    this.statusCode = statusCode;
    this.code = code;
    this.details = details;
  }
}

module.exports = AppError;
