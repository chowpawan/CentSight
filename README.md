# CentSight

![Node](https://img.shields.io/badge/node-%3E%3D22-5FA04E?logo=node.js&logoColor=white)
![React](https://img.shields.io/badge/React-19-61DAFB?logo=react&logoColor=black)
![Express](https://img.shields.io/badge/Express-5-000000?logo=express&logoColor=white)
![SQLite](https://img.shields.io/badge/SQLite-better--sqlite3-003B57?logo=sqlite&logoColor=white)
![Plaid](https://img.shields.io/badge/Plaid-API-111111)

A personal finance dashboard built on Plaid. It shows every bank and credit card balance, what you owe versus what you have, spending by category and merchant for each month, and recurring charges.

```
client/  React + Vite dashboard (port 5173, proxies /api to the server)
server/  Express + SQLite + Plaid SDK (port 8000)
```

## Setup

You need Node 22 or newer and a free Plaid account.

1. Get your `client_id` and Sandbox secret from https://dashboard.plaid.com/developers/keys.
2. Configure the server:
   ```bash
   cd server
   cp .env.example .env
   # fill in PLAID_CLIENT_ID and PLAID_SECRET, then:
   echo "ENCRYPTION_KEY=$(openssl rand -hex 32)" >> .env
   npm install
   npm run dev
   ```
3. In a second terminal, start the dashboard:
   ```bash
   cd client
   npm install
   npm run dev
   ```
4. Open http://localhost:5173, click **Connect an account**, pick any bank, and sign in with `user_good` / `pass_good`. Sandbox institutions such as "First Platypus Bank" include credit cards with liability data.

Transactions and recurring streams can take a few seconds to be ready after linking. The app refreshes once automatically; after that, use **Refresh**.

## How data flows

| What | Plaid endpoint | Where |
|---|---|---|
| Connect a bank | `/link/token/create`, then `/item/public_token/exchange` | `server/src/index.js` |
| Balances | `/accounts/get` (cached) | `server/src/sync.js` |
| Card statement, minimum, due date, APR | `/liabilities/get` | `server/src/sync.js` |
| Transactions | `/transactions/sync` (incremental with a cursor) | `server/src/sync.js` |
| Subscriptions, bills, paychecks | `/transactions/recurring/get` | `server/src/sync.js` |

Access tokens are encrypted with AES-256-GCM before they are written to SQLite.

**Spending** counts outgoing, posted transactions and excludes transfers, loan and card payments, and income, so paying your credit card isn't counted twice.

**Recurring** per-month amounts convert each stream's frequency (weekly, every two weeks, yearly and so on) into a monthly figure.

## API

| Method | Path | Returns |
|---|---|---|
| POST | `/api/link/token` | Link token for Plaid Link |
| POST | `/api/link/exchange` | Saves a new connection and syncs it |
| POST | `/api/sync` | Refreshes every connection |
| GET | `/api/items` | Connected banks |
| DELETE | `/api/items/:id` | Disconnects a bank and deletes its data |
| GET | `/api/summary` | Accounts, assets, debts, net worth |
| GET | `/api/months` | Months that have transactions |
| GET | `/api/spending?month=YYYY-MM` | Categories, top merchants, income, 6-month trend |
| GET | `/api/recurring` | Active recurring inflows and outflows |
| GET | `/api/transactions?limit=50` | Recent transactions |
| POST | `/api/webhook` | Plaid webhook receiver |

## Live updates (optional)

Plaid can notify you when new transactions arrive. Expose the server with a tunnel such as `ngrok http 8000`, then set `PLAID_WEBHOOK_URL=https://<your-tunnel>/api/webhook` in `.env`. The webhook only applies to banks you connect after setting it.

## Before using real accounts

- **Add authentication.** The app currently assumes a single local user (`client_user_id: 'local-user'`). Add login and scope every table by user before anyone else can reach it.
- **Verify webhooks** using the `Plaid-Verification` header: https://plaid.com/docs/api/webhooks/webhook-verification/
- **Get Production access** in the Plaid dashboard, then switch `PLAID_ENV=production` and use your production secret. Some banks require OAuth, which needs a `redirect_uri` registered in the dashboard.
- **Choose real-time balances if you need them.** `/accounts/balance/get` is live, but it is billed per call. Swap it into `syncAccounts` in `sync.js`.
- **Back up the database and keep `ENCRYPTION_KEY` safe.** Losing the key means reconnecting every bank.

## Ideas for next steps

- Monthly budgets per category, with alerts when you pass them.
- Re-categorize transactions and remember your rules.
- Flag new subscriptions or price increases (compare `last_amount` with `average_amount`).
- Scheduled background sync, for example a daily cron that calls `syncAll()`.
