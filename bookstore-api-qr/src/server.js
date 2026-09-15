const express = require("express");
const cors = require("cors");
const config = require("./config");
const deviceRouter = require("./routes/device");
const qrSessionRouter = require("./routes/qrSession");

const app = express();
// FE (bookstore-fe-qr) gọi qua trình duyệt, port khác nên cần CORS.
app.use(cors());
app.use(express.json());

app.get("/health", (req, res) => res.json({ status: "ok" }));

app.use("/device", deviceRouter);
app.use("/qr", qrSessionRouter);

app.use((req, res) => res.status(404).json({ error: "not_found" }));

app.listen(config.PORT, () =>
  console.log(`[Bookstore API QR] Running at http://localhost:${config.PORT} (Keycloak: ${config.ISSUER})`)
);
