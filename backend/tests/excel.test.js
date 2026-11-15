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
  it('memetakan kolom lewat teks header: urutan bebas, huruf besar/kecil dan spasi diabaikan', async () => {
    const buffer = await sheetBuffer([['b1', 'a1']], ['  kolom   B ', 'KOLOM A']);

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
      {
        row: 1,
        column: 'Kolom B',
        message: 'Kolom "Kolom B" tidak ditemukan di baris pertama (header). Header di file: Kolom A.',
      },
    ]);
  });

  it('422 menyebut bahwa baris pertama kosong jika header tidak ada di baris 1', async () => {
    const buffer = await workbookBuffer((sheet) => {
      sheet.addRow([]);
      sheet.addRow(['Kolom A', 'Kolom B']);
      sheet.addRow(['a1', 'b1']);
    });

    const err = await failure(readSheet(buffer, { columns: COLUMNS }));

    expect(err.details.map((d) => d.message)).toEqual([
      'Kolom "Kolom A" tidak ditemukan di baris pertama (header). Baris pertama kosong; header harus berada di baris 1.',
      'Kolom "Kolom B" tidak ditemukan di baris pertama (header). Baris pertama kosong; header harus berada di baris 1.',
    ]);
  });

  it('422 jika ada kolom yang tidak ada di template (kolom tambahan atau nama kolom diubah)', async () => {
    const columns = [...COLUMNS, { key: 'c', header: 'Kolom C', optional: true }];
    const buffer = await sheetBuffer([['a1', 'b1', 'x']], ['Kolom A', 'Kolom B', 'Kolom Baru']);

    const err = await failure(readSheet(buffer, { columns }));

    expect(err.statusCode).toBe(422);
    expect(err.details).toEqual([
      {
        row: 1,
        column: 'Kolom Baru',
        message:
          'Kolom "Kolom Baru" (kolom C) tidak ada di template. Hapus kolom itu atau pakai template terbaru. ' +
          'Kolom yang dikenal: Kolom A, Kolom B, Kolom C.',
      },
    ]);
  });

  it('422 jika satu header muncul dua kali, dengan huruf kolom keduanya', async () => {
    const buffer = await sheetBuffer([['a1', 'b1', 'a2']], ['Kolom A', 'Kolom B', 'kolom  a']);

    const err = await failure(readSheet(buffer, { columns: COLUMNS }));

    expect(err.details).toEqual([
      {
        row: 1,
        column: 'Kolom A',
        message: 'Header "Kolom A" muncul lebih dari sekali (kolom A dan C). Setiap kolom hanya boleh satu.',
      },
    ]);
  });

  it('422 jika header bukan teks (mis. angka)', async () => {
    const buffer = await sheetBuffer([['a1', 'b1', 'x']], ['Kolom A', 'Kolom B', 2026]);

    const err = await failure(readSheet(buffer, { columns: COLUMNS }));

    expect(err.details).toEqual([{ row: 1, column: null, message: 'Header di kolom C bukan teks.' }]);
  });

  it('melaporkan semua masalah struktur sekaligus (kolom hilang + kolom tak dikenal)', async () => {
    const err = await failure(readSheet(await sheetBuffer([['x', 'y']], ['Kolom A', 'Catatan']), { columns: COLUMNS }));

    expect(err.details.map((d) => d.column)).toEqual(['Catatan', 'Kolom B']);
    expect(err.details[0].message).toContain('tidak ada di template');
    expect(err.details[1].message).toContain('tidak ditemukan di baris pertama');
  });

  it('422 jika ada data di kolom yang tidak punya header (tabel tambahan di sebelah template)', async () => {
    const buffer = await workbookBuffer((sheet) => {
      sheet.addRow(['Kolom A', 'Kolom B']);
      sheet.addRow(['a1', 'b1', null, 'catatan']);
      sheet.addRow(['a2', 'b2', null, 'lain']);
    });

    const err = await failure(readSheet(buffer, { columns: COLUMNS }));

    expect(err.details).toEqual([
      {
        row: 2,
        column: null,
        message: 'Kolom D berisi data tetapi tidak punya header di baris 1. Hapus isinya atau gunakan template yang benar.',
      },
    ]);
  });

  describe('lembar tambahan', () => {
    const withSheets = async (...names) => {
      const workbook = new ExcelJS.Workbook();
      const data = workbook.addWorksheet('Data');
      data.addRow(['Kolom A', 'Kolom B']);
      data.addRow(['a1', 'b1']);
      names.forEach((name) => workbook.addWorksheet(name).addRow(['isi bebas', 'tidak dibaca']));
      return Buffer.from(await workbook.xlsx.writeBuffer());
    };

    it('lembar petunjuk milik template resmi dan berkas contoh boleh ada dan tidak dibaca', async () => {
      const rows = await readSheet(await withSheets('Petunjuk', 'Tentang berkas ini'), { columns: COLUMNS });

      expect(rows).toEqual([{ row: 2, values: { a: 'a1', b: 'b1' } }]);
    });

    it('422 jika ada lembar lain di luar lembar data dan lembar petunjuk', async () => {
      const err = await failure(readSheet(await withSheets('Petunjuk', 'Data 2'), { columns: COLUMNS }));

      expect(err.details).toEqual([
        {
          row: 1,
          column: null,
          message:
            'Lembar "Data 2" tidak ada di template. Hapus lembar itu; file hanya boleh berisi lembar data dan lembar petunjuk.',
        },
      ]);
    });
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
