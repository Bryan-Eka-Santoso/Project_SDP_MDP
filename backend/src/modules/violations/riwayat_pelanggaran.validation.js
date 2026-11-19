const { z } = require("zod");

const siswaParams = z.object({
    studentId: z.coerce.number().int().positive()
});

module.exports = {
    siswaParams
};