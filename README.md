# UPI Payment Monitor

Detect incoming UPI payments from your bank SMS and expose them through a small
HTTP API. Two parts:

```
 [ Bank SMS ] --> [ Android app (this repo) ] --POST--> [ Node API on your VPS ]
                                                              |
                                    /verify  /total  /stats/total  /payments
```

1. **`android/`** — an Android app that listens for incoming UPI SMS, filters
   them, and forwards the raw text to your server.
2. **`server/`** — a Node.js API that parses each SMS, stores the payment
   (deduped by UPI reference number), and answers queries.

Currently tuned for **Bank of Baroda (BOB)** UPI credit alerts, e.g.:

```
Dear BOB UPI User: Your account is credited with INR 200.00 on 2026-07-17 02:12:30 PM by UPI Ref No 037418191190; AvlBal: Rs2706.45 - BOB
```

> This monitors **your own** account's payment SMS on **your own** phone. Keep
> your API behind HTTPS and a strong key.

---

## 1. Server (on your VPS, port 5000)

### Run

```bash
cd server
npm install
cp .env.example .env      # then edit API_KEY (and TZ if not Asia/Kolkata)
API_KEY="your-long-secret" PORT=5000 npm start
```

Keep it alive with pm2:

```bash
npm i -g pm2
API_KEY="your-long-secret" PORT=5000 pm2 start src/index.js --name upi-monitor
pm2 save
```

### Endpoints

All endpoints require the key, sent either as header `X-Api-Key: <key>` or query
`?key=<key>` (handy for testing in a browser).

| Method | Path | Purpose |
|--------|------|---------|
| POST | `/webhook` | Ingest a forwarded SMS `{ sender, body, sentAt?, deviceId? }`. Parses + dedupes by UPI ref. |
| GET | `/verify?amt=299` | Was a payment of this amount received? Defaults to **today**; add `&scope=all` for all history. Optional `&ref=...`. |
| GET | `/total` | **Today's** stats: `{ count, totalAmount }`. |
| GET | `/stats/total` | All-time `{ count, totalAmount }` — total payments received. |
| GET | `/payments?limit=50` | Recent payments (newest first). |
| GET | `/health` | Liveness check (no key needed). |

Examples:

```bash
curl "https://your-vps/verify?amt=299&key=SECRET"
curl "https://your-vps/total?key=SECRET"
curl "https://your-vps/stats/total?key=SECRET"
```

Data persists to `server/data/payments.json`.

### HTTPS (recommended)

Put the API behind a reverse proxy (Caddy/Nginx) with a domain + TLS, then point
the app at `https://your-domain/webhook`. Plain `http://IP:5000` works for testing
but sends your SMS text and key unencrypted.

---

## 2. Android app

The prebuilt debug APK is attached to the GitHub Release. To build it yourself:

```bash
cd android
./gradlew :app:assembleDebug
# -> app/build/outputs/apk/debug/app-debug.apk
```

### Install & configure

1. Copy `app-debug.apk` to your phone and install (allow "install from unknown
   sources").
2. Open **UPI Monitor** and set:
   - **Webhook URL** — `http://YOUR_VPS_IP:5000/webhook` (or your HTTPS domain).
   - **API key** — the same secret as the server.
   - **Sender filter** — `BOB` (leave blank to accept any sender).
   - **Body must contain** — `credited,UPI Ref No` (any one matches).
3. Tap **Save settings**, then **Send test payment to server** — you should see
   `Test sent OK`, and `/total` count should go up by one.
4. Flip **Monitoring enabled** ON and grant the SMS + notification permissions.

The app forwards the **raw SMS text**; the server does the parsing. That means
parsing can be improved server-side without reinstalling the app.

### Poco C75 (HyperOS / MIUI) — keep it running

HyperOS aggressively kills background apps. Do all of these or SMS forwarding
will stop when the screen is off:

- **Autostart:** Settings → Apps → Manage apps → *UPI Monitor* → enable **Autostart**.
- **Battery:** same screen → Battery saver / Battery → set to **No restrictions**.
  The in-app "Disable battery optimization" button also opens this.
- **Lock in recents:** open Recents, long-press *UPI Monitor* → **Lock** (padlock),
  so it isn't cleared.
- Keep the persistent "UPI Monitor active" notification enabled (it's the
  foreground service that keeps the app alive).

---

## Adding more banks

Payment parsing lives in [`server/src/parser.js`](server/src/parser.js). Send me
(or add) a couple of real SMS samples per bank and extend `parseUpiSms()` — the
Android app already forwards everything that matches your keyword filter, so no
app change is needed for new bank formats.
