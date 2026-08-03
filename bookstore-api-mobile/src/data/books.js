// Kho sách lưu ở file JSON (data/books.json) — sống qua restart server.
// Đọc 1 lần lúc khởi động vào bộ nhớ, mỗi lần create/update/remove thì ghi lại cả file.

const fs = require("fs");
const path = require("path");

const DATA_FILE = path.join(__dirname, "..", "..", "data", "books.json");

const SEED = [
  { id: 1, cover: "📕", title: "Clean Code", author: "Robert C. Martin", price: 320000, genre: "Technology" },
  { id: 2, cover: "📗", title: "The Pragmatic Programmer", author: "Hunt & Thomas", price: 280000, genre: "Technology" },
  { id: 3, cover: "📘", title: "Dune", author: "Frank Herbert", price: 180000, genre: "Sci-Fi" },
  { id: 4, cover: "📙", title: "Atomic Habits", author: "James Clear", price: 150000, genre: "Self-help" },
  { id: 5, cover: "📔", title: "Design Patterns", author: "Gang of Four", price: 350000, genre: "Technology" },
];

let books;
let nextId;

function save() {
  fs.mkdirSync(path.dirname(DATA_FILE), { recursive: true });
  fs.writeFileSync(DATA_FILE, JSON.stringify(books, null, 2) + "\n");
}

function load() {
  try {
    books = JSON.parse(fs.readFileSync(DATA_FILE, "utf8"));
    console.log(`[Bookstore API] Đọc ${books.length} sách từ ${DATA_FILE}`);
  } catch {
    // Chưa có file (lần chạy đầu) hoặc file hỏng → khởi tạo từ dữ liệu mẫu
    books = SEED.map((b) => ({ ...b }));
    save();
    console.log(`[Bookstore API] Tạo mới ${DATA_FILE} với ${books.length} sách mẫu`);
  }
  nextId = books.reduce((max, b) => Math.max(max, b.id), 0) + 1;
}

load();

function findAll() {
  return books;
}

function findById(id) {
  return books.find((b) => b.id === id);
}

function create({ title, author, genre, price, cover }) {
  const book = {
    id: nextId++,
    cover: cover || "📚",
    title,
    author,
    genre: genre || "Khác",
    price,
  };
  books.push(book);
  save();
  return book;
}

function update(id, { title, author, genre, price, cover }) {
  const book = findById(id);
  if (!book) return null;
  if (title !== undefined) book.title = title;
  if (author !== undefined) book.author = author;
  if (genre !== undefined) book.genre = genre;
  if (price !== undefined) book.price = price;
  if (cover !== undefined) book.cover = cover;
  save();
  return book;
}

function remove(id) {
  const index = books.findIndex((b) => b.id === id);
  if (index === -1) return false;
  books.splice(index, 1);
  save();
  return true;
}

module.exports = { findAll, findById, create, update, remove };
