// Upload file (docs/API_CONVENTIONS.md bagian 7). Multer memoryStorage: file dibaca ke memori
// (dibatasi ukurannya) lalu diproses atau diteruskan ke Cloudinary oleh service.
const multer = require('multer');
const AppError = require('../utils/AppError');

const MEGABYTE = 1024 * 1024;

// .xlsx adalah arsip ZIP; semua arsip ZIP diawali "PK\x03\x04".
const ZIP_SIGNATURE = Buffer.from([0x50, 0x4b, 0x03, 0x04]);

function toUploadError(err, { field, maxBytes }) {
  if (err.code === 'LIMIT_FILE_SIZE') {
    return new AppError(413, 'FILE_TOO_LARGE', `Ukuran file melebihi batas ${maxBytes / MEGABYTE} MB.`);
  }
  if (err.code === 'LIMIT_UNEXPECTED_FILE') {
    return new AppError(400, 'VALIDATION_ERROR', 'Data yang dikirim tidak valid.', [
      { field, message: `File harus dikirim pada field "${field}".` },
    ]);
  }
  // Multipart rusak (boundary hilang, body terpotong, dst.) adalah kesalahan klien.
  return new AppError(400, 'VALIDATION_ERROR', 'Unggahan file tidak valid.');
}

/**
 * Middleware untuk satu file pada field `field`. Pasang SETELAH `authenticate` dan `authorize`
 * agar pengguna yang belum berhak tidak sempat membuat server menampung file.
 *
 * Tipe file dicek dari ekstensi dan isi file (`signatures`), bukan dari Content-Type: klien Android
 * sering mengirim `application/octet-stream` untuk .xlsx. Hasilnya di `req.file` (`buffer`,
 * `originalname`, `size`); field teks lain di `req.body` (divalidasi dengan `validate({ body })`).
 */
function singleFile({ field = 'file', maxBytes, extensions, signatures, typeLabel }) {
  const parse = multer({
    storage: multer.memoryStorage(),
    limits: { fileSize: maxBytes, files: 1, fields: 10 },
  }).single(field);

  return (req, res, next) => {
    parse(req, res, (err) => {
      if (err) return next(toUploadError(err, { field, maxBytes }));

      const { file } = req;
      if (!file) {
        return next(
          new AppError(400, 'VALIDATION_ERROR', 'File wajib diunggah.', [
            { field, message: `Kirim file pada field "${field}".` },
          ]),
        );
      }

      const name = file.originalname.toLowerCase();
      const extensionOk = extensions.some((extension) => name.endsWith(extension));
      const contentOk = signatures.some((signature) => file.buffer.subarray(0, signature.length).equals(signature));
      if (!extensionOk || !contentOk) {
        return next(new AppError(415, 'UNSUPPORTED_FILE_TYPE', `Tipe file tidak didukung. Gunakan ${typeLabel}.`));
      }
      return next();
    });
  };
}

// Impor Excel: .xlsx, maksimal 10 MB. Isi file yang rusak/bukan Excel ditolak oleh `lib/excel.js`.
const uploadXlsx = (field = 'file') =>
  singleFile({
    field,
    maxBytes: 10 * MEGABYTE,
    extensions: ['.xlsx'],
    signatures: [ZIP_SIGNATURE],
    typeLabel: 'file Excel (.xlsx)',
  });

module.exports = { singleFile, uploadXlsx };
