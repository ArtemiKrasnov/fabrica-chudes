import React, { useEffect, useState } from "react";
import { createRoot } from "react-dom/client";
import { Capacitor } from "@capacitor/core";
import fundLogo from "../assets/fond-logo.jpeg";
import {
  createLocalTestStar,
  donorStarsStorageKey,
  initialReport,
  readStoredValue,
  reportStorageKey,
  updateReportAfterDonation,
} from "./offline-demo.js";
import "../styles.css";

const isNativeApp = Capacitor.isNativePlatform();
const pendingPaymentStorageKey = "fabrica-pending-payment";
const apiBaseUrl = import.meta.env.VITE_API_BASE_URL?.trim().replace(/\/+$/, "")
  || (import.meta.env.DEV ? "" : null);
const apiAvailable = apiBaseUrl !== null;
const apiUrl = (path) => `${apiBaseUrl ?? ""}${path}`;

const floors = [
  {
    number: 3,
    title: "Самое срочное",
    windows: [
      {
        id: "medical",
        title: "Медико-социальная программа",
        icon: "❤️",
        scene: "Сердце и карта выездов",
        donationText: "Помоги детям и семьям получить медицинскую и социальную поддержку.",
        amounts: [300, 700, 1500],
      },
      {
        id: "you-are-not-alone",
        title: "Ты не один",
        icon: "🚑",
        scene: "Машина фонда с маячком",
        donationText: "Поддержи выезды команды фонда к тем, кому нужна помощь.",
        amounts: [300, 500, 1000],
      },
      {
        id: "warm-home",
        title: "Тёплый дом (первые дни)",
        icon: "🍼",
        scene: "Люлька и ночник в роддоме",
        donationText: "Помоги создать тёплое и безопасное место для первых дней малыша.",
        amounts: [500, 1000, 2000],
      },
    ],
  },
  {
    number: 2,
    title: "Добрый дом",
    windows: [
      {
        id: "kind-kitchen",
        title: "Кухня добра",
        icon: "🍕",
        scene: "Ребята готовят пиццу, над кастрюлей пар",
        donationText: "Поддержи занятия, где дети учатся готовить и проводить время вместе.",
        amounts: [200, 500, 1000],
      },
      {
        id: "kind-wardrobe",
        title: "Добрый шкаф",
        icon: "🧥",
        scene: "Полки с вещами и куртка для новой жизни",
        donationText: "Помоги собрать нужные вещи детям и семьям.",
        amounts: [300, 600, 1200],
      },
      {
        id: "children-in-family",
        title: "Дети в семье. Регион 58",
        icon: "🏠",
        scene: "Дети играют, мама беседует со специалистом",
        donationText: "Поддержи семьи и консультации специалистов в Пензенской области.",
        amounts: [300, 700, 1500],
      },
    ],
  },
  {
    number: 1,
    title: "Навигаторы детства",
    windows: [
      {
        id: "childhood-navigators",
        title: "Навигаторы детства",
        icon: "🗺️",
        scene: "Карта, флажки и маршруты",
        donationText: "Помоги организовать поездки и добрые события для детей.",
        amounts: [200, 500, 1000],
      },
      {
        id: "school-theaters",
        title: "Школьные театры",
        icon: "🎭",
        scene: "Занавес, сцена и софиты",
        donationText: "Поддержи школьные спектакли, сцену и театральные занятия.",
        amounts: [300, 600, 1200],
      },
      {
        id: "profession-workshop",
        title: "Мастерская профессий",
        icon: "🛠️",
        scene: "Верстак, инструменты и чертежи",
        donationText: "Помоги детям знакомиться с профессиями и пробовать себя в мастерской.",
        amounts: [300, 700, 1500],
      },
    ],
  },
];

function Snow() {
  return (
    <div className="snow" aria-hidden="true">
      {[...Array(28)].map((_, index) => (
        <i key={index} style={{ "--x": `${(index * 37) % 100}%`, "--d": `${(index % 7) + 5}s`, "--s": `${10 + (index % 8) * 2}px` }} />
      ))}
    </div>
  );
}

function House({ onProjectClick, report, progress }) {
  return (
    <section className="house-section" id="house" aria-label="Дом проектов">
      <div className="house">
        <span className="chimney" aria-hidden="true" />
        <div className="roof"><span className="garland" /></div>
        <div className="hall">
          <div className="child-helper"><span className="lamp" /><b>Сменщик Саша</b></div>
          <p>Выбери окно — загляни — помоги</p>
        </div>
        {floors.map((floor) => (
          <div className="floor" key={floor.number}>
            <span className="floor-title">{floor.title}</span>
            <div className="windows">
              {floor.windows.map((project, index) => (
                <button
                  className={`window window-${floor.number}-${index + 1} window-${project.id}`}
                  type="button"
                  key={project.id}
                  onClick={() => onProjectClick(project)}
                  aria-label={`Помочь проекту «${project.title}»: ${project.scene}`}
                >
                  <span className="window-snow" />
                  <span className="window-scene" aria-hidden="true">{project.icon}</span>
                  <b>{project.title}</b>
                  <small>{project.scene}</small>
                </button>
              ))}
            </div>
          </div>
        ))}
        <section className="cellar" aria-label="Сменный отчёт">
          <div className="cellar-heading">
            <span aria-hidden="true">♨</span>
            <div>
              <p>Сменный отчёт — обновляется каждую неделю</p>
            </div>
          </div>
          <div className="cellar-report">
            <div className="cellar-total">
              <strong>{report.collected.toLocaleString("ru-RU")} ₽</strong>
              <span>собрано из {report.goal.toLocaleString("ru-RU")} ₽</span>
            </div>
            <div className="progress-track" aria-hidden="true">
              <i style={{ width: `${progress}%` }} />
            </div>
            <ul>
              {report.paid.map((item) => <li key={item}>✓ {item}</li>)}
            </ul>
          </div>
        </section>
      </div>
    </section>
  );
}

function DonationModal({ project, onClose, onDonationComplete }) {
  const [amount, setAmount] = useState(project.amounts[0]);
  const [name, setName] = useState("");
  const [status, setStatus] = useState("");

  async function submit(event) {
    event.preventDefault();
    setStatus("loading");
    try {
      if (isNativeApp) {
        const normalizedName = name.trim();
        const donationAmount = Number(amount);
        if (!normalizedName || !Number.isInteger(donationAmount) || donationAmount < 100 || donationAmount > 1000000) {
          throw new Error("Укажите имя и целую сумму от 100 до 1 000 000 рублей.");
        }
        onDonationComplete(createLocalTestStar(project.title, donationAmount, normalizedName));
        setStatus("success");
        return;
      }

      if (!apiAvailable) {
        throw new Error("Для оплаты подключите backend API и задайте VITE_API_BASE_URL.");
      }

      const response = await fetch(apiUrl("/api/payments"), {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ amount: Number(amount), project: project.title, name }),
      });
      const data = await response.json();
      if (!response.ok) throw new Error(data.error || "Не удалось создать оплату.");
      if (data.confirmationUrl) {
        localStorage.setItem(pendingPaymentStorageKey, JSON.stringify({
          paymentId: data.paymentId,
          amount: data.amount,
        }));
        window.location.assign(data.confirmationUrl);
        return;
      }
      const confirmationResponse = await fetch(apiUrl(`/api/payments/${data.paymentId}/confirm`), {
        method: "POST",
      });
      const confirmation = await confirmationResponse.json();
      if (!confirmationResponse.ok) {
        throw new Error(confirmation.error || "Не удалось подтвердить оплату.");
      }
      onDonationComplete(confirmation.star);
      setStatus("success");
    } catch (error) {
      setStatus(error.message);
    }
  }

  return (
    <div className="modal-backdrop" role="presentation" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
      <section className="modal" role="dialog" aria-modal="true" aria-labelledby="donation-title">
        <button className="modal-close" type="button" aria-label="Закрыть" onClick={onClose}>×</button>
        <p className="eyebrow">Помощь проекту</p>
        <h2 id="donation-title">{project.title}</h2>
        <p className="project-scene">{project.scene}</p>
        {status === "success" ? (
          <div className="payment-success">
            <span>✦</span>
            <p>Оплата прошла успешно.</p>
            <button type="button" onClick={onClose}>Вернуться к дому</button>
          </div>
        ) : (
          <form onSubmit={submit}>
            <p className="project-donation-copy">{project.donationText}</p>
            <label>Имя или никнейм
              <input type="text" autoComplete="nickname" value={name} onChange={(event) => setName(event.target.value)} maxLength={32} placeholder="@ваше имя" required />
            </label>
            <label>Сумма ₽
              <input value={amount} onChange={(event) => setAmount(event.target.value)} type="number" min="100" max="1000000" required />
            </label>
            <div className="quick-amounts">
              {project.amounts.map((value) => (
                <button
                  className={amount === value ? "selected" : ""}
                  type="button"
                  key={value}
                  aria-pressed={amount === value}
                  onClick={() => setAmount(value)}
                >
                  {value} ₽
                </button>
              ))}
            </div>
            <button className="pay-button" type="submit" disabled={status === "loading"}>{status === "loading" ? "Создаём оплату…" : "Оплатить"}</button>
            {status !== "loading" && status && <p className="form-error">{status}</p>}
            <p className="demo-note">
              {isNativeApp
                ? "Автономный демо-режим: данные сохраняются только на этом устройстве, реальные деньги не списываются."
                : "Оплата проводится через безопасную страницу ЮKassa."}
            </p>
          </form>
        )}
      </section>
    </div>
  );
}

function DonorStars({ stars }) {
  const [openStarId, setOpenStarId] = useState(null);

  return (
    <div className="star-field" aria-label="Звёзды донатеров">
      {stars.map((star) => (
        <div
          className={`donor-star${openStarId === star.id ? " is-open" : ""}`}
          key={star.id}
          style={{ left: `${star.x}%`, top: `${star.y}%` }}
          data-side={star.x < 50 ? "left" : "right"}
        >
          <button
            className="donor-star-button"
            type="button"
            aria-label={`Звезда донатера ${star.name}, ${star.amount.toLocaleString("ru-RU")} рублей`}
            aria-expanded={openStarId === star.id}
            aria-controls={`donor-star-popup-${star.id}`}
            onClick={(event) => {
              event.stopPropagation();
              setOpenStarId((current) => current === star.id ? null : star.id);
            }}
          />
          <span className="donor-star-popup" id={`donor-star-popup-${star.id}`} role="tooltip">
            <strong>{star.name}</strong>
            <small>{star.amount.toLocaleString("ru-RU")} ₽</small>
          </span>
        </div>
      ))}
    </div>
  );
}

function App() {
  const [project, setProject] = useState(null);
  const [donorStars, setDonorStars] = useState(() => (
    isNativeApp ? readStoredValue(localStorage, donorStarsStorageKey, []) : []
  ));
  const [report, setReport] = useState(() => (
    isNativeApp ? readStoredValue(localStorage, reportStorageKey, initialReport) : initialReport
  ));

  useEffect(() => {
    if (isNativeApp || !apiAvailable) return;
    fetch(apiUrl("/api/stars"))
      .then(async (response) => {
        if (!response.ok) throw new Error("Не удалось загрузить звёзды донатеров.");
        const data = await response.json();
        setDonorStars(data);
      })
      .catch((error) => console.error(error));
    fetch(apiUrl("/api/report"))
      .then((response) => response.ok ? response.json() : null)
      .then((data) => data && setReport(data))
      .catch(() => {});
  }, []);

  useEffect(() => {
    if (isNativeApp || !apiAvailable) return;
    const pending = localStorage.getItem(pendingPaymentStorageKey);
    if (!pending) return;

    let cancelled = false;
    let attempts = 0;

    const checkPayment = async () => {
      const payment = JSON.parse(pending);
      const response = await fetch(apiUrl(`/api/payments/${payment.paymentId}/confirm`), { method: "POST" });
      const confirmation = await response.json();
      if (!response.ok) throw new Error(confirmation.error || "Не удалось проверить оплату.");

      if (confirmation.status === "succeeded" && confirmation.star) {
        if (!cancelled) {
          setDonorStars((current) => current.some((star) => star.id === confirmation.star.id)
            ? current
            : [...current, confirmation.star]);
          setReport((current) => ({
            ...current,
            collected: current.collected + confirmation.star.amount,
          }));
          localStorage.removeItem(pendingPaymentStorageKey);
        }
        return true;
      }
      return false;
    };

    const poll = async () => {
      try {
        while (!cancelled && attempts < 12) {
          attempts += 1;
          if (await checkPayment()) return;
          await new Promise((resolve) => setTimeout(resolve, 2000));
        }
      } catch (error) {
        console.error(error);
      }
    };

    poll();
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    if (!project) return;
    const onKey = (event) => event.key === "Escape" && setProject(null);
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [project]);

  const progress = Math.min(100, Math.round(report.collected / report.goal * 100));

  return (
    <>
      <main>
        <section className="hero">
          <Snow />
          <DonorStars stars={donorStars} />
          <img className="fund-logo" src={fundLogo} alt="Российский детский фонд — Пензенское отделение" />
          <p className="fund-name">Пензенское отделение Российского детского фонда</p>
          <h1>Фабрика чудес</h1>
          <p className="tagline">Чудо — это ручная работа</p>
          <a className="visit-button" href="#house">Пойдём в гости <span>↓</span></a>
        </section>

        <House onProjectClick={setProject} report={report} progress={progress} />

        <section className="faq" id="faq">
          <p className="eyebrow">Ответы на вопросы</p>
          <h2>Как рождается помощь</h2>
          <details><summary>Куда идут пожертвования?</summary><p>На проекты фонда: поездки, театр, мастерские, медико-социальную поддержку и помощь семьям.</p></details>
          <details><summary>Можно ли помочь конкретному проекту?</summary><p>Да. Выберите любое окно в доме, откройте проект и сделайте пожертвование.</p></details>
          <details><summary>Это безопасная оплата?</summary><p>Оплата обрабатывается через защищённый сервис ЮKassa. Данные карты вводятся только на странице провайдера.</p></details>
        </section>
      </main>
      {project && (
        <DonationModal
          project={project}
          onClose={() => setProject(null)}
          onDonationComplete={(star) => {
            const updatedStars = [...donorStars, star];
            const updatedReport = isNativeApp
              ? updateReportAfterDonation(report, star.amount)
              : report;
            if (isNativeApp) {
              localStorage.setItem(donorStarsStorageKey, JSON.stringify(updatedStars));
              localStorage.setItem(reportStorageKey, JSON.stringify(updatedReport));
            }
            setDonorStars(updatedStars);
            setReport(updatedReport);
          }}
        />
      )}
    </>
  );
}

createRoot(document.getElementById("root")).render(<App />);
