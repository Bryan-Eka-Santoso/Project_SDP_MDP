const ExcelJS = require('exceljs');
const AppError = require('../src/utils/AppError');
const { readSheet, importInvalid, cellText, MAX_REPORTED_ERRORS } = require('../src/lib/excel');

const COLUMNS = [
  { key: 'a', header: 'Kolom A' },
  { key: 'b', header: 'Kolom B' },
];

// `fill(sheet)` mengisi lembar; baris 1 = header.
async function workbookBuffer(fill) {
  const workbook = new ExcelJS.Workbook();
  fill(workbook.addWorksheet('Data'));
  return Buffer.from(await workbook.xlsx.writeBuffer());
}

const sheetBuffer = (rows, header = ['Kolom A', 'Kolom B']) =>
  workbookBuffer((sheet) => {
    sheet.addRow(header);
    rows.forEach((row) => sheet.addRow(row));
  });

async function failure(promise) {
  try {
    await promise;
  } catch (err) {
    return err;
  }
  throw new Error('Seharusnya melempar error');
}

describe('readSheet', () => {
  it('memetakan kolom lewat teks header: urutan bebas, huruf besar/kecil dan spasi diabaikan, kolom lain dilewati', async () => {
    const buffer = await sheetBuffer([['b1', 'ekstra', 'a1']], ['  kolom   B ', 'Catatan', 'KOLOM A']);

    const rows = await readSheet(buffer, { columns: COLUMNS });

    expect(rows).toEqual([{ row: 2, values: { a: 'a1', b: 'b1' } }]);
  });

  it('melewati baris kosong tetapi nomor baris tetap mengikuti Excel', async () => {
    const buffer = await sheetBuffer([['a1', 'b1'], [null, null], ['   ', null], ['a4', 'b4']]);

    const rows = await readSheet(buffer, { columns: COLUMNS });

    expect(rows.map((r) => r.row)).toEqual([2, 5]);
  });

  it('menyederhanakan nilai sel: formula, teks berformat, hyperlink, tanggal, angka, spasi berlebih', async () => {
    const date = new Date(Date.UTC(1899, 11, 30, 7, 30));
    const buffer = await workbookBuffer((sheet) => {
      sheet.addRow(['Kolom A', 'Kolom B']);
      sheet.addRow([{ formula: '"MA"&"T"', result: 'MAT' }, { richText: [{ text: 'Sen' }, { text: 'in' }] }]);
      sheet.addRow([{ text: 'X-1', hyperlink: 'https://contoh.test' }, '  Ruang    X-1 ']);
      sheet.addRow([date, 42]);
      sheet.addRow([true, { error: '#REF!' }]);
    });

    const rows = await readSheet(buffer, { columns: COLUMNS });

    expect(rows.map((r) => r.values)).toEqual([
      { a: 'MAT', b: 'Senin' },
      { a: 'X-1', b: 'Ruang X-1' },
      { a: date, b: 42 },
      { a: true, b: '#REF!' },
    ]);
  });

  it('kolom opsional yang tidak ada di file dianggap kosong, kolom wajib tetap dicek', async () => {
    const columns = [...COLUMNS, { key: 'c', header: 'Kolom C', optional: true }];

    const rows = await readSheet(await sheetBuffer([['a1', 'b1']]), { columns });

    expect(rows).toEqual([{ row: 2, values: { a: 'a1', b: 'b1', c: null } }]);
    const err = await failure(readSheet(await sheetBuffer([['x']], ['Kolom A', 'Kolom C']), { columns }));
    expect(err.details.map((d) => d.column)).toEqual(['Kolom B']);
  });

  it('422 IMPORT_INVALID per kolom jika header tidak lengkap', async () => {
    const err = await failure(readSheet(await sheetBuffer([['x']], ['Kolom A']), { columns: COLUMNS }));

    expect(err).toBeInstanceOf(AppError);
    expect(err.statusCode).toBe(422);
    expect(err.code).toBe('IMPORT_INVALID');
    expect(err.details).toEqual([
      { row: 1, column: 'Kolom B', message: 'Kolom "Kolom B" tidak ditemukan di baris pertama (header).' },
    ]);
  });

  it('422 jika tidak ada baris data', async () => {
    const err = await failure(readSheet(await sheetBuffer([]), { columns: COLUMNS }));
    expect(err.details).toEqual([{ row: 2, column: null, message: 'File tidak berisi baris data.' }]);
  });

  it('422 jika baris data melebihi batas', async () => {
    const buffer = await sheetBuffer([['1', 'a'], ['2', 'b'], ['3', 'c']]);

    const err = await failure(readSheet(buffer, { columns: COLUMNS, maxRows: 2 }));

    expect(err.statusCode).toBe(422);
    expect(err.details).toEqual([
      { row: 4, column: null, message: 'Jumlah baris data melebihi batas 2 baris per file.' },
    ]);
  });

  it('415 jika isi file bukan xlsx yang valid', async () => {
    const err = await failure(readSheet(Buffer.from('bukan excel'), { columns: COLUMNS }));
    expect(err.statusCode).toBe(415);
    expect(err.code).toBe('UNSUPPORTED_FILE_TYPE');
  });
});

describe('cellText', () => {
  it('teks, kosong, dan angka utuh sampai 15 digit', () => {
    expect(cellText('MAT')).toEqual({ text: 'MAT' });
    expect(cellText(null)).toEqual({ text: null });
    expect(cellText(2026001)).toEqual({ text: '2026001' });
    expect(cellText(123456789012345)).toEqual({ text: '123456789012345' });
  });

  it('menolak angka yang sudah dibulatkan Excel (NIP 18 digit), desimal, dan tipe lain', () => {
    expect(cellText(198001012005011001).error).toContain('Ubah format kolom menjadi Teks');
    expect(cellText(1234567890123456).error).toBeDefined();
    expect(cellText(1.5).error).toBeDefined();
    expect(cellText(true).error).toBe('Isi sel harus berupa teks.');
    expect(cellText(new Date()).error).toBe('Isi sel harus berupa teks.');
  });
});

describe('importInvalid', () => {
  it('mengurutkan per baris dan menghitung kesalahan serta baris', () => {
    const err = importInvalid([
      { row: 5, column: 'B', message: 'x' },
      { row: 2, column: 'A', message: 'y' },
      { row: 2, column: 'B', message: 'z' },
    ]);

    expect(err.statusCode).toBe(422);
    expect(err.code).toBe('IMPORT_INVALID');
    expect(err.message).toBe('Impor dibatalkan: 3 kesalahan di 2 baris. Tidak ada data yang disimpan.');
    expect(err.details.map((d) => [d.row, d.column])).toEqual([[2, 'A'], [2, 'B'], [5, 'B']]);
  });

  it('membatasi jumlah kesalahan yang dikirim dan menyebutkannya di pesan', () => {
    const errors = Array.from({ length: MAX_REPORTED_ERRORS + 50 }, (_, i) => ({ row: i + 2, column: 'A', message: 'm' }));

    const err = importInvalid(errors);

    expect(err.details).toHaveLength(MAX_REPORTED_ERRORS);
    expect(err.message).toContain('250 kesalahan di 250 baris');
    expect(err.message).toContain(`Hanya ${MAX_REPORTED_ERRORS} kesalahan pertama yang ditampilkan.`);
  });
});
