/**
 * Tiny append-only JSON store for payments.
 *
 * Payments are kept in memory and persisted to data/payments.json on every
 * change. This is plenty for a single-device UPI monitor (thousands of rows).
 * Swap for SQLite/Postgres if you outgrow it.
 */
import { readFileSync, writeFileSync, mkdirSync, existsSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = dirname(fileURLToPath(import.meta.url));
const DATA_FILE = resolve(__dirname, "../data/payments.json");

let payments = [];
/** ref -> payment, for O(1) dedupe. Falls back to a synthetic key when no ref. */
const seen = new Map();

function keyOf(p) {
  return p.ref ? `ref:${p.ref}` : `syn:${p.amount}:${p.sentAt || p.body}`;
}

function persist() {
  mkdirSync(dirname(DATA_FILE), { recursive: true });
  writeFileSync(DATA_FILE, JSON.stringify(payments, null, 2));
}

export function load() {
  if (existsSync(DATA_FILE)) {
    try {
      payments = JSON.parse(readFileSync(DATA_FILE, "utf8"));
      for (const p of payments) seen.set(keyOf(p), p);
    } catch {
      payments = [];
    }
  }
}

/**
 * Insert a payment if not already seen.
 * @returns {{ inserted: boolean, payment: object }}
 */
export function insert(payment) {
  const key = keyOf(payment);
  if (seen.has(key)) {
    return { inserted: false, payment: seen.get(key) };
  }
  seen.set(key, payment);
  payments.push(payment);
  persist();
  return { inserted: true, payment };
}

export function all() {
  return payments;
}

/** Most recent first. */
export function recent(limit = 50) {
  return [...payments]
    .sort((a, b) => (b.receivedAt || "").localeCompare(a.receivedAt || ""))
    .slice(0, limit);
}
