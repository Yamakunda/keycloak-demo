// Kho sách lưu ở file JSON (data/books.json) — sống qua restart server.
// Đọc 1 lần lúc khởi động vào bộ nhớ, mỗi lần create/remove thì ghi lại cả file.

const fs = require("fs");
const path = require("path");

const DATA_FILE = path.join(__dirname, "..", "..", "data", "books.json");

const SEED = [
  { id: 1, title: "The Pragmatic Programmer", author: "Hunt & Thomas" },
  { id: 2, title: "Clean Code", author: "Robert C. Martin" },
  { id: 3, title: "SAML 2.0 in Action", author: "Jane Doe" },
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
    console.log(`[bookstore-saml-api] Đọc ${books.length} sách từ ${DATA_FILE}`);
  } catch {
    // Chưa có file (lần chạy đầu) hoặc file hỏng → khởi tạo từ dữ liệu mẫu
    books = SEED.map((b) => ({ ...b }));
    save();
    console.log(`[bookstore-saml-api] Tạo mới ${DATA_FILE} với ${books.length} sách mẫu`);
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

function create({ title, author }) {
  const book = { id: nextId++, title, author };
  books.push(book);
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

module.exports = { findAll, findById, create, remove };
