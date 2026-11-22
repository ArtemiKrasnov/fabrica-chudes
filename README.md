# Фабрика чудес

Одностраничный сайт Пензенского отделения Российского детского фонда: React-фронтенд на JavaScript, Java API на Spring Boot, дом из девяти проектных окон, формы донатов, звёзды донатеров, статичный отчёт и FAQ.

## Запуск

Установите зависимости:

```sh
npm install
```

Для Java API требуется JDK 21 или новее. Maven Wrapper в корне проекта автоматически загрузит Maven при первом запуске. Запустите backend в одном терминале:

```sh
npm run server
```

Запустите frontend в другом терминале:

```sh
npm run dev
```

Команда запускает Spring Boot API на порту `3001`. Vite проксирует запросы `/api` на backend `http://localhost:3001`.

Проверка backend-тестов:

```sh
npm run server:test
```

Сборка Android APK для тестирования:

```sh
npm run android:apk
```

Файл появится в `apk/fabrica-chudes-debug.apk`. Мобильное приложение работает в автономном демо-режиме: тестовые донаты, звёзды и сумма в отчёте сохраняются только на устройстве. Реальные платежи не выполняются; подключение backend для приложения настраивается отдельно.

## Платежи

GitHub Pages размещает только фронтенд и не может запускать Java API. Для отображения звёзд и реальной оплаты сначала разверните `server-java` на отдельном сервере с публичным HTTPS-адресом. В настройках репозитория добавьте Actions variable `VITE_API_BASE_URL` со значением этого адреса, например `https://api.example.ru`. API должен разрешать CORS для адреса сайта.

Интеграция подготовлена для ЮKassa с методом Yandex Pay или другим доступным способом ЮKassa. Данные магазина задаются в среде backend:

```sh
YOOKASSA_ENABLED=true
YOOKASSA_SHOP_ID=<идентификатор магазина>
YOOKASSA_SECRET_KEY=<секретный ключ>
YOOKASSA_RETURN_URL=https://artemikrasnov.github.io/fabrica-chudes/
```

Секреты храните только в переменных среды или секрет-хранилище backend, а не в Git. Yandex Pay должен быть отдельно разрешён в настройках магазина ЮKassa. В кабинете ЮKassa укажите webhook `https://api.example.ru/api/payments/webhook` для события `payment.succeeded`. Замените `api.example.ru` на реальный адрес backend.

Без `VITE_API_BASE_URL` сайт откроется в статическом режиме, но форма оплаты сообщит, что API не подключён; успешная оплата не будет имитироваться.

## Публикация на GitHub Pages

Workflow `.github/workflows/deploy-pages.yml` собирает сайт и публикует его при каждом push в `main`. Один раз включите публикацию в репозитории: **Settings → Pages → Build and deployment → Source → GitHub Actions**. Адрес сайта: <https://artemikrasnov.github.io/fabrica-chudes/>.
