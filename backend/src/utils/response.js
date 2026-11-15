function sendSuccess(res, data, statusCode = 200) {
  return res.status(statusCode).json({ success: true, data });
}

// `extra`: field meta tambahan khusus endpoint (mis. studentCount pada daftar tagihan).
function sendPaginated(res, data, { page, limit, total, ...extra }) {
  return res.status(200).json({
    success: true,
    data,
    meta: {
      page,
      limit,
      total,
      totalPages: Math.ceil(total / limit),
      ...extra,
    },
  });
}

module.exports = { sendSuccess, sendPaginated };
