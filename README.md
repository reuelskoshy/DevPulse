# DevPulse

DevPulse is an AI-powered developer intelligence platform for engineering teams.

For how the system fits together (components, request flow and design decisions), see [ARCHITECTURE.md](ARCHITECTURE.md).

[![DevPulse product walkthrough](docs/demo.gif)](docs/demo.mp4)

A 75-second walkthrough of the live demo: team view, personal dashboard, AI insight and weekly digest. The preview above plays at 2x speed. Click it for the full-quality video ([docs/demo.mp4](docs/demo.mp4)).

## Live demo

**Demo URL:** `TODO: add the Vercel URL here after deploying` (see [Deploy](#deploy)).

Click **Try the live demo** to sign in as the manager of a read-only sample team. You don't need an account or a GitHub connection. You can explore the dashboard and the team activity view. Anything that would change data, such as syncing, generating insights, or editing the team, is blocked. To connect your own GitHub, create a free account.

The demo backend runs on a free instance that sleeps when idle. The first request after a quiet spell can take up to about a minute.

## Prerequisites

- Java 21
- Maven 3.9+
- Node.js 22+ and npm
- Docker Desktop

## Run locally

1. Copy `.env.example` to `.env` and set the MySQL root password.
2. Start infrastructure: `docker compose up -d mysql redis`.
3. Copy `backend/.env.example` to a private local environment configuration and replace its password and JWT secret. Configure those values in the IntelliJ run configuration for `DevPulseApplication`.
4. Start the backend from IntelliJ by opening `backend/pom.xml`, selecting a Java 21 SDK, and running `DevPulseApplication`.
5. In another terminal, run `npm install` and `npm run dev` from `frontend`.

Endpoints:

- API health: `http://localhost:8080/api/v1/health`
- Actuator health: `http://localhost:8080/actuator/health`
- Web app: `http://localhost:5173`

## Database Access

Connect to MySQL using MySQL Workbench or the Docker CLI with the host/port/database/username above and the root password you set in your local `.env` (never commit it):
- Host: localhost
- Port: 3306
- Database: db_DevPulse
- Username: root

```bash
docker exec -it devpulse-mysql-1 mysql -uroot -p db_DevPulse
```

## Authentication endpoints

- `POST /api/v1/auth/register` — `{ "name": "Dev User", "email": "dev@example.com", "password": "at-least-12-characters" }`
- `POST /api/v1/auth/login` — returns an access token and user DTO. The user DTO includes `demo: true|false`.
- `POST /api/v1/auth/demo` — signs in as the demo manager of the read-only sample team. Returns 404 unless `DEMO_ENABLED=true`. In a demo session, every request other than `GET`/`HEAD`/`OPTIONS` gets 403, except requests under `/api/v1/auth/`.
- `GET /api/v1/users/me` — requires `Authorization: Bearer <access-token>`

## Rate limiting

A handful of sensitive or costly endpoints are throttled with an in-memory token bucket per caller, refilled steadily rather than reset all at once at a window boundary:

| Endpoint | Limit | Keyed by |
| --- | --- | --- |
| `POST /api/v1/auth/login` | 10 / 5 min | IP address |
| `POST /api/v1/auth/register` | 5 / hour | IP address |
| `POST /api/v1/auth/demo` | 20 / min | IP address |
| `POST /api/v1/integrations/github/sync` | 6 / min | user |
| `POST /api/v1/insights/generate` | 5 / hour | user |
| `POST /api/v1/insights/team/generate` | 5 / hour | user |
| `POST /api/v1/digest/test` | 3 / hour | user |

A throttled request gets `429 Too Many Requests` with a `Retry-After` header (seconds) and a friendly `message`. This runs on a single backend instance; a multi-instance deployment would need a shared store instead of the in-memory buckets.

## Team management endpoints

Backed by the `dp_user` table. Roles are `ADMIN`, `MANAGER`, `MEMBER`; `MANAGER` sees/manages users whose `parent` points at them, `ADMIN` sees/manages everyone, `MEMBER` sees only themselves. New registrations default to `MEMBER` with no manager, except emails listed in `ADMIN_EMAILS`, which start as `ADMIN`.

Admins manage roles, managers, and active status on the **People** page (`/app/people`). The API enforces these rules:

- You can't change your own role or deactivate yourself, so the last admin can't lock themselves out.
- A manager must have the `MANAGER` or `ADMIN` role.
- Reporting lines can't loop: nobody reports to themselves or to one of their own reports.
- A manager who still has reports can't be made a `MEMBER`.

Role changes take effect at the user's next sign-in, because the role is carried in the access token.

- `GET /api/v1/team-members` — list scoped to your role
- `GET /api/v1/team-members/{id}` — self, admin, or that user's manager
- `PUT /api/v1/team-members/{id}` — edit profile fields (name/phone/address/location); self, admin, or that user's manager
- `PATCH /api/v1/team-members/{id}/status` — activate/deactivate; admin, or that user's manager
- `PATCH /api/v1/team-members/{id}/role` — reassign role/manager; admin only

## Connect GitHub

1. Create an OAuth App at [GitHub Developer Settings](https://github.com/settings/developers). For local development, set its authorization callback URL to `http://localhost:8080/api/v1/integrations/github/callback`.
2. Add the Client ID and Client Secret to `backend/.env` as `GITHUB_CLIENT_ID` and `GITHUB_CLIENT_SECRET`, then configure the same values as environment variables in the IntelliJ run configuration. Keep them private.
3. Sign in to DevPulse, then call `POST /api/v1/integrations/github/authorize` with the access token. Open the returned `authorizationUrl` in a browser and approve GitHub access.
4. GitHub redirects back to the API, which links the GitHub account, then back to the frontend dashboard. Confirm the result with `GET /api/v1/integrations/github` using the same access token — it now also returns `repoCount`, `commitCount`, and `lastSyncedAt`.

The connection requests `read:user` and `repo` scopes. The access token is encrypted at rest (see `TOKEN_ENCRYPTION_KEY` below).

## GitHub sync

- `POST /api/v1/integrations/github/sync` — pulls every repo the connected account can access and the account's own commits from the last 14 days (90 on first sync), deduplicated and safe to re-run.
- **Automatic sync.** Every `AUTO_SYNC_CHECK_INTERVAL` (default 15 minutes), the API re-syncs connected, active, non-demo accounts whose last sync is older than `AUTO_SYNC_MAX_AGE` (default 6 hours), at most 25 per run. Accounts that have never synced go first, then the stalest. If one account fails, for example because its token was revoked, the API logs it, skips it, and retries it on a later run. Set `AUTO_SYNC_ENABLED=false` to turn this off.
- **Pull requests.** Each sync also fetches, through GitHub's search API, the PRs the account authored and the PRs it reviewed (someone else's), updated since the last complete PR sync (90 days back the first time). For a reviewed PR, the review time is that account's first submitted review. A PR with only review comments falls back to the PR's last update. At most 60 review lookups run per sync. If there are more, the rest come on the next sync, and the PR window doesn't move forward until a sync gets through everything. If PR fetching fails (for example, the search rate limit is hit), the commit sync still succeeds and PRs catch up next time. PRs in private repos require the token's `repo` scope, just like commits.

## AI insights

- `POST /api/v1/insights/generate` — reviews the last 14 days of synced activity (commits, your pull requests, and reviews you gave) via the Gemini API. Requires `GEMINI_API_KEY` to be set; each call is a deliberate user action, not automatic.
- `GET /api/v1/insights/latest` — cheap read of the most recently generated insight (404 if none yet).

An insight has a headline, an overview (`summary`), and a `details` object:

- `facts` — measured by DevPulse, not the model: commits, active days, longest streak, busiest weekday, weekend commits, PRs opened / merged / still open, median hours to merge, reviews given, and top repositories. Days are counted in UTC.
- `highlights` (up to 4), `patterns` and `suggestions` (up to 3 each) — written by Gemini as schema-constrained JSON from those facts, commit messages and PR titles. The prompt treats commit messages and PR titles as data, never as instructions. Each field is trimmed to a fixed length. If the model returns something that isn't JSON, its text becomes the summary and the facts are still kept.

Insights generated before this change have `details: null` and show just the summary.

## Team activity

- `GET /api/v1/team/activity?days=14` — commit activity for everyone you can see, over the last `days` calendar days in UTC, ending today. `days` accepts 1–90 and defaults to 14; any other value returns 400.
  - Team totals: members, connected members, active members, commits, repos touched, and pull request stats.
  - A zero-filled daily team series.
  - One entry per member: GitHub connection and login, last sync time, commits, active days, last commit time, top 3 repos, a daily series, and pull request stats.
  - Pull request stats (`pullRequests`): PRs opened and merged in the range, PRs still open, reviews given in the range, and `medianHoursToMerge` (open to merge, for PRs merged in the range; `null` if none were merged).
  - Members are sorted by commits, then by name.

Who sees whom follows the team roles. `ADMIN` sees everyone. `MANAGER` sees themselves and their direct reports (users whose `parent` is them). `MEMBER` sees only themselves. Demo users and real users never see each other. No tokens, phone numbers, or addresses are returned.

## Weekly digest

Once a week, DevPulse emails each user a short digest covering the last 7 days, compared with the 7 days before:

- **Personal:** your commits, active days, merged PRs, reviews given, and your most active repo.
- **Team:** for anyone who can see more than themselves (managers and admins). Includes team commits, active members, merged and open PRs, median time to merge, and the top 3 contributors. Also lists connected teammates with no commits that week.

Delivery works like this:

- **When it goes out.** The API checks hourly for the latest round (`DIGEST_DAY_OF_WEEK` at `DIGEST_HOUR_UTC` UTC, default Monday 08:00). A round is still sent up to 2 days late, so a sleeping instance catches up when it wakes.
- **Who gets it.** Only accounts that existed before the round and haven't received it yet.
- **No duplicates.** Each send is claimed atomically first, so several instances never email the same person twice. A failed send releases the claim and is retried on the next check.
- **What's skipped.** Weeks with nothing to report aren't emailed. Demo accounts never get email.

Endpoints and settings:

- `GET /api/v1/digest/weekly` returns this week's digest as JSON (the Settings page shows it as a preview).
- `GET` / `PUT /api/v1/digest/preferences` with `{"weeklyDigestEnabled": false}` opts you out. The same switch is on the **Settings** page (`/app/settings`), where you can also edit your name, phone and location. The response's `emailDelivery` field says whether this server can send email at all.
- `POST /api/v1/digest/test` emails this week's digest to you only, right away, even if it's empty. This is the **Send me a test** button on Settings. It doesn't count as that week's digest, and it's limited to once a minute. Demo sessions can't use it.

Email needs SMTP settings. Without `MAIL_HOST` and `MAIL_FROM`, nothing is sent and everything else keeps working; `/actuator/health` doesn't depend on the mail server.

## Continuous integration

`.github/workflows/ci.yml` runs on every push and pull request to `master`:

- the backend tests, with `mvn -B test` on Temurin 21;
- the frontend lint and production build, with `npm ci`, `npm run lint` and `npm run build` on Node 22.

The backend tests need no database.

## Environment variables

The root `.env` file is exclusively for local Docker Compose infrastructure. Backend secrets live in `backend/.env` (copied from `backend/.env.example`, never committed):

- `TOKEN_ENCRYPTION_KEY` — base64, 32 bytes, encrypts the GitHub access token at rest. Changing it invalidates previously-connected accounts; reconnect GitHub afterward.
- `GEMINI_API_KEY` / `GEMINI_MODEL` — used to generate AI insights (free tier via [aistudio.google.com](https://aistudio.google.com), no card required).
- `GEMINI_FALLBACK_MODEL` — optional, default `gemini-3.5-flash-lite`. Busy or rate-limited requests (429/5xx) are retried twice on `GEMINI_MODEL`, then once on this model. Set it blank to turn the fallback off.
- `DEMO_ENABLED` — `true` seeds the read-only sample team and turns on **Try the live demo** (`POST /api/v1/auth/demo`). Defaults to `false`.
- `ADMIN_EMAILS` — comma-separated emails that are always `ADMIN`. They're promoted at startup if the account exists, or at registration otherwise. This is how the first admin is created. Demo accounts are never promoted.
- `AUTO_SYNC_ENABLED` / `AUTO_SYNC_MAX_AGE` / `AUTO_SYNC_CHECK_INTERVAL` — background GitHub sync (see [GitHub sync](#github-sync)). The durations are ISO-8601 values, for example `PT6H`.
- `MAIL_HOST` / `MAIL_PORT` (default 587) / `MAIL_USERNAME` / `MAIL_PASSWORD` / `MAIL_FROM`: the SMTP server and sender address for the weekly digest. Any SMTP provider works (Resend, Brevo, SendGrid, Gmail with an app password). `MAIL_SMTP_AUTH` and `MAIL_STARTTLS` default to `true`.
- `DIGEST_ENABLED` / `DIGEST_DAY_OF_WEEK` / `DIGEST_HOUR_UTC`: the weekly digest schedule (see [Weekly digest](#weekly-digest)). The defaults are `true`, `MONDAY` and `8`.
- `PORT` — the port the API listens on, set by hosting platforms. Falls back to `BACKEND_PORT`, then 8080.

## Deploy

The live demo runs on free tiers:

- The API runs on [Render](https://render.com) as a Docker service.
- MySQL 8 can be any hosted provider, for example [Aiven](https://aiven.io)'s free plan.
- The web app runs on [Vercel](https://vercel.com).

The repo includes `backend/Dockerfile`, `render.yaml`, and `frontend/vercel.json` for this setup.

1. **Database.** Create a hosted MySQL 8 database. Note its host, port, database name, username, and password. Aiven's defaults are database `defaultdb` and user `avnadmin`. Build the JDBC URL. Hosted MySQL usually requires TLS:

   ```
   jdbc:mysql://<host>:<port>/<database>?sslMode=REQUIRED&serverTimezone=UTC
   ```

   Flyway creates the schema when the API first starts.

2. **Secrets.** Generate two values locally and never commit them. Each must decode to 32 bytes:

   ```bash
   openssl rand -base64 32   # JWT_SECRET
   openssl rand -base64 32   # TOKEN_ENCRYPTION_KEY
   ```

3. **Backend on Render.** Choose **New → Blueprint** and connect this repo. Render reads `render.yaml`, which defines one free Docker web service. The service is built from `backend/Dockerfile`, health-checked at `/api/v1/health`, and runs with `DEMO_ENABLED=true`. Fill in the prompted variables:
   - `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` come from step 1.
   - `JWT_SECRET` and `TOKEN_ENCRYPTION_KEY` come from step 2.
   - `FRONTEND_ORIGIN` is the Vercel URL. If you don't have it yet, enter a placeholder and fix it in step 5.
   - `ADMIN_EMAILS` is the email you'll sign up with, so your account becomes the first admin.
   - The `GITHUB_*` variables and `GEMINI_API_KEY` are optional (see step 6). Leave them blank for a demo-only deploy; the API then reports those features as not configured.

   Render only prompts for these when the Blueprint is first created. To change them later, use the service's **Environment** tab. Once the service is live, `https://<render-service>.onrender.com/api/v1/health` should return `"status": "UP"`.

4. **Frontend on Vercel.** Import the repo with these settings:
   - Root directory: `frontend`
   - Framework preset: Vite
   - Build command: `npm run build`
   - Output directory: `dist`
   - Environment variable: `VITE_API_URL=https://<render-service>.onrender.com/api/v1`

   Vite bakes `VITE_API_URL` into the build, so redeploy after changing it. `vercel.json` rewrites every path to `index.html`, so deep links like `/app/dashboard` work on refresh.

5. **CORS.** On Render, set `FRONTEND_ORIGIN` to the exact Vercel production URL: scheme and host, no trailing slash, for example `https://<your-project>.vercel.app`. Then redeploy. The API allows exactly one origin, so Vercel preview URLs can't call it.

6. **Optional: GitHub and AI insights for real accounts.** A GitHub OAuth App has a single callback URL, so create a separate one for production. Set its callback to `https://<render-service>.onrender.com/api/v1/integrations/github/callback`. Then set these on Render:
   - `GITHUB_CLIENT_ID` and `GITHUB_CLIENT_SECRET` from the new OAuth App.
   - `GITHUB_REDIRECT_URI`: the same callback URL.
   - `GITHUB_FRONTEND_SUCCESS_URL=https://<your-project>.vercel.app/app/dashboard`.
   - `GEMINI_API_KEY`, to turn on insights.
   - `MAIL_HOST`, `MAIL_USERNAME`, `MAIL_PASSWORD` and `MAIL_FROM`, to send the weekly digest.

7. **Cold starts.** Render's free instance sleeps after 15 minutes without traffic. The next request wakes it, which takes roughly 30–60 seconds, sometimes longer while the JVM starts. The first **Try the live demo** click after a quiet spell can be slow. The instance has 512 MB of RAM, and `JAVA_OPTS` in `render.yaml` is sized for that. The heap is a percentage of container memory, so it grows with a bigger plan. On a bigger plan, you can also drop `-XX:TieredStopAtLevel=1`. While the instance sleeps, automatic GitHub sync doesn't run either; it catches up on the first check after the instance wakes.

Finally, put the Vercel URL in [Live demo](#live-demo) above.

To try the image locally, build it and point it at your local MySQL through `host.docker.internal` (inside a container, `localhost` is the container itself). The `-e` flag overrides `DATABASE_URL` from `backend/.env` without editing the file; `--add-host` makes `host.docker.internal` resolve on Linux too:

```bash
docker build -t devpulse-api ./backend
docker run --rm -p 8080:8080 --env-file backend/.env \
  -e DATABASE_URL='jdbc:mysql://host.docker.internal:3306/db_DevPulse?serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true' \
  --add-host=host.docker.internal:host-gateway devpulse-api
```
