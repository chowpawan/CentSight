# CentSight

![Java](https://img.shields.io/badge/Java-21-E76F00?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-6DB33F?logo=springboot&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql&logoColor=white)
![React](https://img.shields.io/badge/React-19-61DAFB?logo=react&logoColor=black)
![AWS](https://img.shields.io/badge/AWS-App%20Runner-232F3E)
![Plaid](https://img.shields.io/badge/Plaid-API-111111)

A personal finance dashboard built on Plaid. It shows every bank and credit card balance, what you
owe versus what you have, spending by category and merchant for each month, and recurring charges.

Multi-user, installable on a phone, and deployable to AWS as a single container.

```
client/   React 19 + Vite. Mobile-first PWA, bundled into the jar at build time.
backend/  Java 21 + Spring Boot 3.5. REST API, Google sign-in, Plaid sync, Postgres.
infra/    Terraform: AWS App Runner + RDS Postgres + Secrets Manager + ECR.
server/   The original Node prototype, kept for reference. Superseded by backend/.
```

## How it works

Google signs you in, the backend mints a JWT, and the browser sends it as a bearer token.
Every table holding financial data carries a `user_id`, and every query filters on the user
resolved from that token — never from anything the client sends.

| Concern | Approach |
|---|---|
| Sign-in | Spring Security OAuth2 (Google) → HS512 JWT, 30-day expiry |
| Isolation | `user_id` on every row; `@Transactional` reads scoped by the authenticated user |
| Access tokens | Plaid tokens encrypted with AES-256-GCM before they touch the database |
| Secrets | AWS Secrets Manager, read by an IAM role scoped to one secret |
| Schema | Flyway migrations; Hibernate runs in `validate` mode |
| Money | `BigDecimal` and `NUMERIC(19,4)` end to end — no floats |

## Run it locally

You need Java 21, Node 22, Docker and a free [Plaid](https://dashboard.plaid.com/developers/keys)
account.

```bash
# 1. Postgres
docker run -d --name centsight-pg -p 5432:5432 \
  -e POSTGRES_DB=centsight -e POSTGRES_USER=centsight -e POSTGRES_PASSWORD=centsight \
  postgres:16-alpine

# 2. Backend
cd backend
export DATABASE_URL=jdbc:postgresql://localhost:5432/centsight
export DATABASE_USER=centsight DATABASE_PASSWORD=centsight
export PLAID_CLIENT_ID=... PLAID_SECRET=... PLAID_ENV=sandbox
export JWT_SECRET="$(openssl rand -base64 48)"
export ENCRYPTION_KEY="$(openssl rand -hex 32)"
export GOOGLE_CLIENT_ID=... GOOGLE_CLIENT_SECRET=...
export APP_URL=http://localhost:5173 CORS_ORIGINS=http://localhost:5173
mvn spring-boot:run

# 3. Frontend (second terminal)
cd client && npm install && npm run dev
```

Open http://localhost:5173, sign in with Google, then **Connect an account** and use
`user_good` / `pass_good`. Sandbox institutions such as "First Platypus Bank" include credit
cards with liability data.

For Google sign-in locally, add `http://localhost:8080/login/oauth2/code/google` as an
authorised redirect URI in the Google Cloud Console.

### Or run the whole thing as one container

```bash
docker build -t centsight .
docker run -p 8080:8080 --env-file .env centsight   # app and API both on :8080
```

## Deploy to AWS

One container serves the API and the React app, so there is a single HTTPS URL, no CORS and no
load balancer to pay for.

```
App Runner (HTTPS, autoscaling)  ->  VPC connector  ->  RDS Postgres (private subnets)
        |                                                        |
   Secrets Manager  <--- IAM role scoped to one secret ----------+
```

```bash
cd infra
cp terraform.tfvars.example terraform.tfvars   # fill in Plaid + Google credentials
terraform init
terraform apply                                # creates VPC, RDS, ECR, App Runner
./deploy.sh                                    # build, push to ECR, roll the service
```

`terraform output app_url` is the link to send your friends. Two things to do once:

1. Put `terraform output google_redirect_uri` into Google Cloud Console as an authorised
   redirect URI.
2. Optionally set `allowed_emails` in `terraform.tfvars` to a comma-separated guest list, so
   only the people you name can create an account.

Running cost is roughly **$20–30/month** — RDS `db.t4g.micro` is the bulk of it, and App Runner
bills per request plus a small always-on charge. `terraform destroy` removes everything
(RDS has deletion protection on, so turn that off first).

## On a phone

The app is a PWA: Android offers an **Install** button, and on iOS it is
**Share → Add to Home Screen**. It then opens full-screen with its own icon. A service worker
caches the shell so it starts instantly; account data is never cached and always comes from
the network.

The layout switches to a bottom tab bar under 860px, and the recurring-charges table reflows
into cards rather than scrolling sideways.

## API

Everything under `/api` requires `Authorization: Bearer <jwt>` and is scoped to that user.

| Method | Path | Returns |
|---|---|---|
| GET | `/api/me` | The signed-in user |
| POST | `/api/link/token` | Link token for Plaid Link |
| POST | `/api/link/exchange` | Saves a new connection and syncs it |
| POST | `/api/sync` | Refreshes every connection |
| GET | `/api/items` | Connected banks |
| DELETE | `/api/items/{id}` | Disconnects a bank and deletes its data |
| GET | `/api/summary` | Accounts, assets, debts, net worth |
| GET | `/api/months` | Months that have transactions |
| GET | `/api/spending?month=YYYY-MM` | Categories, top merchants, income, 6-month trend |
| GET | `/api/recurring` | Active recurring inflows and outflows |
| GET | `/api/transactions?limit=50` | Recent transactions |
| POST | `/api/webhook` | Plaid webhook receiver (public) |

**Spending** counts outgoing, posted transactions and excludes transfers, loan and card payments,
and income, so paying your credit card isn't counted twice.

**Recurring** per-month amounts convert each stream's frequency (weekly, every two weeks, yearly
and so on) into a monthly figure.

## Before switching to real banks

Everything above runs against Plaid **Sandbox**, where the data is fake. To read real accounts:

- **Apply for Plaid Production access.** You then hold other people's financial data and take on
  Plaid's compliance terms. Set `plaid_env = "production"` and use your production secret.
- **Verify webhooks** using the `Plaid-Verification` header —
  https://plaid.com/docs/api/webhooks/webhook-verification/
- **Keep `ENCRYPTION_KEY` safe and backed up.** Losing it means every bank must be reconnected.
- **Use the `allowed_emails` guest list** so the deployment isn't open to the internet.

## Tests

```bash
cd backend && mvn test
```

Covers the AES-256-GCM round trip (including tamper and wrong-key rejection) and JWT issuing,
expiry, and rejection of tokens whose claims were edited.
