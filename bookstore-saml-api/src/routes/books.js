const express = require("express");
const store = require("../data/books");
const { ensureRole } = require("../middleware/auth");

const router = express.Router();

// Router được mount sau ensureAuth (xem server.js) → GET chỉ cần đăng nhập,
// còn create/delete yêu cầu thêm realm role book-admin từ SAML assertion.

// GET /api/books — xem toàn bộ sách
router.get("/", (req, res) => res.json(store.findAll()));

// GET /api/books/:id — xem một cuốn
router.get("/:id", (req, res) => {
  const book = store.findById(parseInt(req.params.id, 10));
  if (!book) return res.status(404).json({ error: "not_found" });
  res.json(book);
});

// POST /api/books — tạo sách mới (book-admin)
router.post("/", ensureRole("book-admin"), (req, res) => {
  const { title, author } = req.body || {};
  if (typeof title !== "string" || !title.trim() || typeof author !== "string" || !author.trim()) {
    return res.status(400).json({ error: "validation", message: "title và author là bắt buộc (string)" });
  }
  res.status(201).json(store.create({ title: title.trim(), author: author.trim() }));
});

// DELETE /api/books/:id — xóa sách (book-admin)
router.delete("/:id", ensureRole("book-admin"), (req, res) => {
  if (!store.remove(parseInt(req.params.id, 10))) {
    return res.status(404).json({ error: "not_found" });
  }
  res.status(204).end();
});

module.exports = router;
