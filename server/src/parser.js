/**
 * Parser for UPI credit SMS.
 *
 * Currently tuned for Bank of Baroda (BOB) UPI credit alerts, e.g.:
 *
 *   Dear BOB UPI User: Your account is credited with INR 200.00 on
 *   2026-07-17 02:12:30 PM by UPI Ref No 037418191190; AvlBal: Rs2706.45 - BOB
 *
 * The parser is intentionally forgiving: if a specific bank format is not
 * recognised it falls back to generic amount / ref extraction so that
 * new-but-similar formats still yield something useful.
 */

/** Convert "2026-07-17 02:12:30 PM" -> ISO string (interpreted as local time). */
function parseBobDateTime(raw) {
  if (!raw) return null;
  const m = raw.match(
    /(\d{4})-(\d{2})-(\d{2})\s+(\d{1,2}):(\d{2}):(\d{2})\s*(AM|PM)/i
  );
  if (!m) return null;
  let [, y, mo, d, h, min, s, ap] = m;
  let hour = parseInt(h, 10);
  ap = ap.toUpperCase();
  if (ap === "PM" && hour !== 12) hour += 12;
  if (ap === "AM" && hour === 12) hour = 0;
  const dt = new Date(
    parseInt(y, 10),
    parseInt(mo, 10) - 1,
    parseInt(d, 10),
    hour,
    parseInt(min, 10),
    parseInt(s, 10)
  );
  return Number.isNaN(dt.getTime()) ? null : dt.toISOString();
}

function toNumber(str) {
  if (str == null) return null;
  const n = parseFloat(String(str).replace(/,/g, ""));
  return Number.isNaN(n) ? null : n;
}

/**
 * Parse a single SMS body.
 * @returns {null | {
 *   bank: string, amount: number, ref: string|null,
 *   balance: number|null, sentAt: string|null, isCredit: boolean
 * }}
 */
export function parseUpiSms(body) {
  if (!body || typeof body !== "string") return null;
  const text = body.replace(/\s+/g, " ").trim();

  // Only treat as a payment if it looks like a credit.
  const isCredit = /\bcredit(ed)?\b/i.test(text);

  // ---- Bank of Baroda specific ----
  if (/BOB\b/i.test(text) || /BOB UPI User/i.test(text)) {
    const amount = toNumber(
      (text.match(/credited with\s+(?:INR|Rs\.?)\s*([\d,]+\.?\d*)/i) || [])[1]
    );
    const ref = (text.match(/UPI Ref No\.?\s*([0-9]+)/i) || [])[1] || null;
    const balance = toNumber(
      (text.match(/AvlBal:?\s*Rs\.?\s*([\d,]+\.?\d*)/i) || [])[1]
    );
    const sentAt = parseBobDateTime(text);
    if (amount != null) {
      return { bank: "BOB", amount, ref, balance, sentAt, isCredit };
    }
  }

  // ---- Generic fallback (other banks / slightly different wording) ----
  const genAmount = toNumber(
    (text.match(
      /(?:credited(?:\s+with)?|received|deposited)\s+(?:with\s+)?(?:INR|Rs\.?)\s*([\d,]+\.?\d*)/i
    ) ||
      text.match(/(?:INR|Rs\.?)\s*([\d,]+\.?\d*)\s+(?:credited|received)/i) ||
      [])[1]
  );
  if (genAmount != null) {
    const ref =
      (text.match(
        /(?:UPI\s*Ref(?:\s*No)?\.?|Ref(?:\s*No)?\.?|RRN|txn(?:\s*id)?)[:\s]*([0-9]{6,})/i
      ) || [])[1] || null;
    const balance = toNumber(
      (text.match(/(?:AvlBal|Avl Bal|Bal)[:\s]*Rs\.?\s*([\d,]+\.?\d*)/i) ||
        [])[1]
    );
    return { bank: "UNKNOWN", amount: genAmount, ref, balance, sentAt: null, isCredit };
  }

  return null;
}
