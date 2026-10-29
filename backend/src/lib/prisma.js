const { PrismaClient } = require('@prisma/client');

// Satu instance untuk seluruh aplikasi agar pool koneksi MySQL tidak berlipat.
const prisma = new PrismaClient();

module.exports = prisma;
