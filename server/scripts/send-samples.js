/**
 * Sends the sample BOB SMS to the running server for a quick end-to-end test.
 *   node scripts/send-samples.js
 */
const BASE = process.env.BASE || "http://localhost:5000";
const KEY = process.env.API_KEY || "change-me-to-a-long-random-secret";

const samples = [
  "Dear BOB UPI User: Your account is credited with INR 200.00 on 2026-07-17 02:12:30 PM by UPI Ref No 037418191190; AvlBal: Rs2706.45 - BOB",
  "Dear BOB UPI User: Your account is credited with INR 299.00 on 2026-07-17 01:47:22 PM by UPI Ref No 083618301437; AvlBal: Rs2506.45 - BOB",
  "Dear BOB UPI User: Your account is credited with INR 299.00 on 2026-07-17 01:45:18 PM by UPI Ref No 656454547065; AvlBal: Rs2207.45 - BOB",
  // duplicate of the first (same ref) -> should be ignored
  "Dear BOB UPI User: Your account is credited with INR 200.00 on 2026-07-17 02:12:30 PM by UPI Ref No 037418191190; AvlBal: Rs2706.45 - BOB",
];

async function main() {
  for (const body of samples) {
    const r = await fetch(`${BASE}/webhook`, {
      method: "POST",
      headers: { "Content-Type": "application/json", "X-Api-Key": KEY },
      body: JSON.stringify({ sender: "AD-BOBTXN", body }),
    });
    const j = await r.json();
    console.log(r.status, JSON.stringify(j));
  }
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
