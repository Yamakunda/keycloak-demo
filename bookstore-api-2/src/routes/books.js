const express = require("express");
const store = require("../data/books");

const router = express.Router();

function validate(body, { partial = false } = {}) {
  const errors = [];
  const { title, author, price } = body;

  if (!partial || title !== undefined) {
    if (typeof title !== "string" || !title.trim()) errors.push("title là bắt buộc (string)");
  }
  if (!partial || author !== undefined) {
    if (typeof author !== "string" || !author.trim()) errors.push("author là bắt buộc (string)");
  }
  if (!partial || price !== undefined) {
    if (typeof price !== "number" || !Number.isFinite(price) || price < 0) {
      errors.push("price là bắt buộc (number ≥ 0)");
    }
  }
  return errors;
}

// GET /api/books — xem toàn bộ sách
router.get("/", (req, res) => {
  const books = store.findAll();
  res.json({
    total: books.length,
    authenticatedAs: req.tokenInfo.preferred_username || req.tokenInfo.sub,
    books,
  });
});

// GET /api/books/:id — xem một cuốn
router.get("/:id", (req, res) => {
  const book = store.findById(parseInt(req.params.id, 10));
  if (!book) return res.status(404).json({ error: "not_found", message: "Sách không tồn tại" });
  res.json(book);
});

// POST /api/books — tạo sách mới
router.post("/", (req, res) => {
  const errors = validate(req.body);
  if (errors.length) return res.status(400).json({ error: "validation", messages: errors });

  const { title, author, genre, price, cover } = req.body;
  const book = store.create({
    title: title.trim(),
    author: author.trim(),
    genre: genre?.trim(),
    price,
    cover,
  });
  res.status(201).json(book);
});

// PUT /api/books/:id — update sách (gửi trường nào update trường đó)
router.put("/:id", (req, res) => {
  const errors = validate(req.body, { partial: true });
  if (errors.length) return res.status(400).json({ error: "validation", messages: errors });

  const book = store.update(parseInt(req.params.id, 10), req.body);
  if (!book) return res.status(404).json({ error: "not_found", message: "Sách không tồn tại" });
  res.json(book);
});

// DELETE /api/books/:id — xóa sách
router.delete("/:id", (req, res) => {
  const removed = store.remove(parseInt(req.params.id, 10));
  if (!removed) return res.status(404).json({ error: "not_found", message: "Sách không tồn tại" });
  res.status(204).end();
});

module.exports = router;
