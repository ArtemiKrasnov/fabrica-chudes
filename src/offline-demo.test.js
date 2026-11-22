import assert from "node:assert/strict";
import test from "node:test";
import {
  createLocalTestStar,
  initialReport,
  readStoredValue,
  updateReportAfterDonation,
} from "./offline-demo.js";

test("local test donations create stars with normalized donor details", () => {
  const values = [0.2, 0.5, 0.4];
  const star = createLocalTestStar(
    "Кухня добра",
    500,
    "  Данила  ",
    () => values.shift(),
    "test-star",
  );

  assert.equal(star.id, "test-star");
  assert.equal(star.name, "Данила");
  assert.equal(star.project, "Кухня добра");
  assert.equal(star.amount, 500);
  assert.equal(star.x, 12.5);
  assert.equal(star.y, 28);
  assert.ok(star.createdAt);
});

test("local test donations increase the collected amount without replacing report details", () => {
  const report = updateReportAfterDonation(initialReport, 500);

  assert.equal(report.collected, initialReport.collected + 500);
  assert.equal(report.goal, initialReport.goal);
  assert.deepEqual(report.paid, initialReport.paid);
});

test("saved demo data is restored and invalid JSON falls back with an error", () => {
  const storage = {
    getItem: (key) => key === "valid" ? "[1,2]" : "{",
  };
  const error = console.error;
  const errors = [];
  console.error = (...args) => errors.push(args);

  try {
    assert.deepEqual(readStoredValue(storage, "valid", []), [1, 2]);
    assert.deepEqual(readStoredValue(storage, "invalid", []), []);
    assert.equal(errors.length, 1);
  } finally {
    console.error = error;
  }
});
