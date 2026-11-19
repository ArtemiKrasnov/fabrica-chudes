import express from "express";
import cors from "cors";
import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const app = express();
const port = Number(process.env.PORT || 3001);
const dataDirectory = path.join(path.dirname(fileURLToPath(import.meta.url)), "data");
const starsFile = path.join(dataDirectory, "stars.json");

fs.mkdirSync(dataDirectory, { recursive: true });

let stars = [];
try {
  stars = JSON.parse(fs.readFileSync(starsFile, "utf8"));
  if (!Array.isArray(stars)) throw new Error("Список звёзд должен быть массивом.");
} catch (error) {
  if (error.code !== "ENOENT") throw error;
  fs.writeFileSync(starsFile, "[]\n", "utf8");
}

app.use(cors());
app.use(express.json({ limit: "32kb" }));

const report = {
  collected: 184750,
  goal: 300000,
  updatedAt: "обновляется каждую неделю",
  paid: [
    "Аренда помещения — 42 300 ₽",
    "Продукты для мастерской — 28 900 ₽",
    "Проезд по маршрутам — 19 600 ₽",
    "Оборудование театра — 33 950 ₽",
  ],
};

const payments = new Map();

app.get("/api/report", (_req, res) => res.json(report));
app.get("/api/stars", (_req, res) => res.json(stars));

app.post("/api/payments", (req, res) => {
  const amount = Number(req.body?.amount);
  const project = String(req.body?.project || "").trim();
  const name = String(req.body?.name || "").trim();

  if (!Number.isSafeInteger(amount) || amount < 100 || amount > 1000000) {
    return res.status(400).json({ error: "Укажите сумму от 100 до 1 000 000 рублей." });
  }
  if (!project) {
    return res.status(400).json({ error: "Не выбран проект." });
  }
  if (!name || name.length > 32) {
    return res.status(400).json({ error: "Укажите ник длиной не более 32 символов." });
  }

  const paymentId = crypto.randomUUID();
  payments.set(paymentId, { amount, project, name, status: "pending" });
  res.status(201).json({
    paymentId,
    status: "test",
    amount,
    confirmationUrl: null,
  });
});

app.post("/api/payments/:id/confirm", (req, res) => {
  const payment = payments.get(req.params.id);
  if (!payment) {
    return res.status(404).json({ error: "Платёж не найден." });
  }
  if (payment.status === "succeeded") {
    return res.json({ paymentId: req.params.id, status: payment.status, test: true, star: payment.star });
  }

  const star = {
    id: crypto.randomUUID(),
    name: payment.name,
    project: payment.project,
    amount: payment.amount,
    x: Math.random() < 0.5 ? 5 + Math.random() * 15 : 80 + Math.random() * 15,
    y: 8 + Math.random() * 50,
    createdAt: new Date().toISOString(),
  };

  try {
    stars.push(star);
    fs.writeFileSync(starsFile, `${JSON.stringify(stars, null, 2)}\n`, "utf8");
  } catch (error) {
    stars.pop();
    console.error("Не удалось сохранить звезду донатера:", error);
    return res.status(500).json({ error: "Не удалось сохранить донат. Попробуйте ещё раз." });
  }

  payment.status = "succeeded";
  payment.star = star;
  return res.json({ paymentId: req.params.id, status: payment.status, test: true, star });
});

app.listen(port, () => {
  console.log(`Фабрика чудес API: http://localhost:${port}`);
});
