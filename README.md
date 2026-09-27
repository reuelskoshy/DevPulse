# DevPulse

DevPulse is an AI-powered developer intelligence platform for engineering teams.

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

## AI insights

- `POST /api/v1/insights/generate` — summarizes the last 14 days of synced commit activity into a short plain-language insight via the Gemini API. Requires `GEMINI_API_KEY` to be set; each call is a deliberate user action, not automatic.
- `GET /api/v1/insights/latest` — cheap read of the most recently generated insight (404 if none yet).

## Team activity

- `GET /api/v1/team/activity?days=14` — commit activity for everyone you can see, over the last `days` calendar days in UTC, ending today. `days` accepts 1–90 and defaults to 14; any other value returns 400.
  - Team totals: members, connected members, active members, commits, and repos touched.
  - A zero-filled daily team series.
  - One entry per member: GitHub connection and login, last sync time, commits, active days, last commit time, top 3 repos, and a daily series.
  - Members are sorted by commits, then by name.

Who sees whom follows the team roles. `ADMIN` sees everyone. `MANAGER` sees themselves and their direct reports (users whose `parent` is them). `MEMBER` sees only themselves. Demo users and real users never see each other. No tokens, phone numbers, or addresses are returned.

## Continuous integration

`.github/workflows/ci.yml` runs on every push and pull request to `master`:

- the backend tests, with `mvn -B test` on Temurin 21;
- the frontend lint and production build, with `npm ci`, `npm run lint` and `npm run build` on Node 22.

The backend tests need no database.

## Environment variables

The root `.env` file is exclusively for local Docker Compose infrastructure. Backend secrets live in `backend/.env` (copied from `backend/.env.example`, never committed):

- `TOKEN_ENCRYPTION_KEY` — base64, 32 bytes, encrypts the GitHub access token at rest. Changing it invalidates previously-connected accounts; reconnect GitHub afterward.
- `GEMINI_API_KEY` / `GEMINI_MODEL` — used to generate AI insights (free tier via [aistudio.google.com](https://aistudio.google.com), no card required).
- `DEMO_ENABLED` — `true` seeds the read-only sample team and turns on **Try the live demo** (`POST /api/v1/auth/demo`). Defaults to `false`.
- `ADMIN_EMAILS` — comma-separated emails that are always `ADMIN`. They're promoted at startup if the account exists, or at registration otherwise. This is how the first admin is created. Demo accounts are never promoted.
- `AUTO_SYNC_ENABLED` / `AUTO_SYNC_MAX_AGE` / `AUTO_SYNC_CHECK_INTERVAL` — background GitHub sync (see [GitHub sync](#github-sync)). The durations are ISO-8601 values, for example `PT6H`.
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

7. **Cold starts.** Render's free instance sleeps after 15 minutes without traffic. The next request wakes it, which takes roughly 30–60 seconds, sometimes longer while the JVM starts. The first **Try the live demo** click after a quiet spell can be slow. The instance has 512 MB of RAM, and `JAVA_OPTS` in `render.yaml` is sized for that. The heap is a percentage of container memory, so it grows with a bigger plan. On a bigger plan, you can also drop `-XX:TieredStopAtLevel=1`. While the instance sleeps, automatic GitHub sync doesn't run either; it catches up on the first check after the instance wakes.

Finally, put the Vercel URL in [Live demo](#live-demo) above.

To try the image locally, build it and point it at your local MySQL through `host.docker.internal` (inside a container, `localhost` is the container itself). The `-e` flag overrides `DATABASE_URL` from `backend/.env` without editing the file; `--add-host` makes `host.docker.internal` resolve on Linux too:

```bash
docker build -t devpulse-api ./backend
docker run --rm -p 8080:8080 --env-file backend/.env \
  -e DATABASE_URL='jdbc:mysql://host.docker.internal:3306/db_DevPulse?serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true' \
  --add-host=host.docker.internal:host-gateway devpulse-api
```
