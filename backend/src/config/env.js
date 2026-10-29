// Satu-satunya tempat membaca process.env. Modul lain mengimpor dari sini.
const env = {
  port: Number(process.env.PORT) || 3000,
  databaseUrl: process.env.DATABASE_URL,
  jwt: {
    accessSecret: process.env.JWT_ACCESS_SECRET,
    refreshSecret: process.env.JWT_REFRESH_SECRET,
    accessTtl: process.env.JWT_ACCESS_TTL || '15m',
    refreshTtl: process.env.JWT_REFRESH_TTL || '7d',
  },
  cloudinary: {
    cloudName: process.env.CLOUDINARY_CLOUD_NAME,
    apiKey: process.env.CLOUDINARY_API_KEY,
    apiSecret: process.env.CLOUDINARY_API_SECRET,
  },
  firebaseServiceAccountPath: process.env.FIREBASE_SERVICE_ACCOUNT_PATH,
  tzDisplay: process.env.TZ_DISPLAY || 'Asia/Jakarta',
  // Hanya dipakai prisma/seed.js.
  seed: {
    adminEmail: process.env.SEED_ADMIN_EMAIL || 'admin@santaagnes.test',
    adminPassword: process.env.SEED_ADMIN_PASSWORD,
    demoPassword: process.env.SEED_DEMO_PASSWORD,
    demoData: process.env.SEED_DEMO_DATA !== 'false',
  },
};

module.exports = env;
