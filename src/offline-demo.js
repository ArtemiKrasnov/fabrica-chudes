export const donorStarsStorageKey = "fabrica-chudes:donor-stars";
export const reportStorageKey = "fabrica-chudes:report";
export const initialReport = { collected: 184750, goal: 300000, paid: [] };

export function readStoredValue(storage, key, fallback) {
  const storedValue = storage.getItem(key);
  if (!storedValue) return fallback;

  try {
    return JSON.parse(storedValue);
  } catch (error) {
    console.error(`Не удалось прочитать локальные данные (${key}).`, error);
    return fallback;
  }
}

export function createLocalTestStar(project, amount, name, random = Math.random, id = crypto.randomUUID()) {
  const x = random() < 0.5
    ? 5 + random() * 15
    : 80 + random() * 15;
  return {
    id,
    name: name.trim(),
    project,
    amount,
    x,
    y: 8 + random() * 50,
    createdAt: new Date().toISOString(),
  };
}

export function updateReportAfterDonation(report, amount) {
  return { ...report, collected: report.collected + amount };
}
