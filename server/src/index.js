import express from "express";
import { parseUpiSms } from "./parser.js";
import { load, insert, all, recent } from "./store.js";

const PORT = process.env.PORT || 5000;
const API_KEY = process.env.API_KEY || "change-me-to-a-long-random-secret";
const TZ = process.env.TZ || "Asia/Kolkata";

load();

const app = express();
app.use(express.json({ limit: "64kb" }));
// Accept form-encoded bodies too, so simple SMS-forwarder apps also work.
app.use(express.urlencoded({ extended: true }));

/* ----------------------------- helpers ----------------------------- */

/** Local YYYY-MM-DD for a given instant, in the configured timezone. */
function localDate(iso) {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: TZ,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(new Date(iso));
}

/** The timestamp we treat a payment as happening at. */
function whenOf(p) {
  return p.sentAt || p.receivedAt;
}

/** Auth via X-Api-Key header or ?key= query (handy for browser GETs). */
function authed(req) {
  const provided = req.get("X-Api-Key") || req.query.key;
  return provided && provided === API_KEY;
}

function requireAuth(req, res, next) {
  if (!authed(req)) {
    return res.status(401).json({ ok: false, error: "unauthorized" });
  }
  next();
}

function money(n) {
  return Math.round((n + Number.EPSILON) * 100) / 100;
}

/* ----------------------------- routes ------------------------------ */

app.get("/health", (_req, res) => {
  res.json({ ok: true, service: "upi-monitor", tz: TZ, time: new Date().toISOString() });
});

/**
 * Ingest a forwarded SMS.
 * Body: { sender?, body, sentAt?, deviceId? }  (body is required)
 * The server parses the raw body so parsing can be improved without
 * reinstalling the Android app.
 */
app.post("/webhook", requireAuth, (req, res) => {
  const body = req.body?.body ?? req.body?.text ?? req.body?.message;
  if (!body || typeof body !== "string") {
    return res.status(400).json({ ok: false, error: "missing 'body' (SMS text)" });
  }

  const parsed = parseUpiSms(body);
  if (!parsed || !parsed.isCredit || parsed.amount == null) {
    return res.status(200).json({ ok: true, ignored: true, reason: "not a UPI credit SMS" });
  }

  const payment = {
    id: parsed.ref || `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    bank: parsed.bank,
    amount: parsed.amount,
    ref: parsed.ref,
    balance: parsed.balance,
    sender: req.body?.sender || null,
    deviceId: req.body?.deviceId || null,
    sentAt: parsed.sentAt || req.body?.sentAt || null,
    receivedAt: new Date().toISOString(),
    body,
  };

  const { inserted, payment: stored } = insert(payment);
  res.status(inserted ? 201 : 200).json({ ok: true, inserted, duplicate: !inserted, payment: stored });
});

/**
 * Verify whether a payment of a given amount was received.
 *   GET /verify?amt=299            -> checks today by default
 *   GET /verify?amt=299&scope=all  -> checks all history
 *   GET /verify?amt=299&ref=0376.. -> also match a specific ref
 */
app.get("/verify", requireAuth, (req, res) => {
  const amt = parseFloat(String(req.query.amt ?? req.query.amount ?? "").replace(/,/g, ""));
  if (Number.isNaN(amt)) {
    return res.status(400).json({ ok: false, error: "provide ?amt=<amount>" });
  }
  const scope = (req.query.scope || "today").toString();
  const ref = req.query.ref ? String(req.query.ref) : null;
  const today = localDate(new Date().toISOString());

  const matches = all().filter((p) => {
    if (money(p.amount) !== money(amt)) return false;
    if (ref && p.ref !== ref) return false;
    if (scope === "today" && localDate(whenOf(p)) !== today) return false;
    return true;
  });

  res.json({
    ok: true,
    amount: money(amt),
    scope,
    found: matches.length > 0,
    count: matches.length,
    matches: matches.map((p) => ({
      ref: p.ref,
      amount: p.amount,
      sentAt: p.sentAt,
      receivedAt: p.receivedAt,
      bank: p.bank,
    })),
  });
});

/**
 * Today's stats (count + total amount received today).
 *   GET /total
 */
app.get("/total", requireAuth, (req, res) => {
  const today = localDate(new Date().toISOString());
  const todays = all().filter((p) => localDate(whenOf(p)) === today);
  const sum = todays.reduce((acc, p) => acc + p.amount, 0);
  res.json({
    ok: true,
    date: today,
    tz: TZ,
    count: todays.length,
    totalAmount: money(sum),
  });
});

/**
 * All-time stats.
 *   GET /stats/total   -> { count, totalAmount } across all history
 */
app.get("/stats/total", requireAuth, (_req, res) => {
  const list = all();
  const sum = list.reduce((acc, p) => acc + p.amount, 0);
  res.json({
    ok: true,
    count: list.length,
    totalAmount: money(sum),
  });
});

/**
 * Recent payments list.
 *   GET /payments?limit=50
 */
app.get("/payments", requireAuth, (req, res) => {
  const limit = Math.min(parseInt(req.query.limit || "50", 10) || 50, 500);
  res.json({ ok: true, count: all().length, payments: recent(limit) });
});

app.listen(PORT, () => {
  console.log(`UPI monitor API listening on http://localhost:${PORT} (tz=${TZ})`);
});
