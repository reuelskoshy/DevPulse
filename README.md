# DevPulse

DevPulse is an AI-powered developer intelligence platform for engineering teams.

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
- `POST /api/v1/auth/login` — returns an access token and user DTO
- `GET /api/v1/users/me` — requires `Authorization: Bearer <access-token>`

## Team management endpoints

Backed by the `dp_user` table. Roles are `ADMIN`, `MANAGER`, `MEMBER`; `MANAGER` sees/manages users whose `parent` points at them, `ADMIN` sees/manages everyone, `MEMBER` sees only themselves. New registrations default to `MEMBER` with no manager.

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

- `POST /api/v1/integrations/github/sync` — pulls every repo the connected account can access and the account's own commits from the last 14 days (90 on first sync), deduplicated and safe to re-run. No automatic/background sync — this is an on-demand action.

## AI insights

- `POST /api/v1/insights/generate` — summarizes the last 14 days of synced commit activity into a short plain-language insight via the Anthropic API. Requires `ANTHROPIC_API_KEY` to be set; costs an API call each time, so it's a deliberate user action, not automatic.
- `GET /api/v1/insights/latest` — cheap read of the most recently generated insight (404 if none yet).

## Environment variables

The root `.env` file is exclusively for local Docker Compose infrastructure. Backend secrets live in `backend/.env` (copied from `backend/.env.example`, never committed):

- `TOKEN_ENCRYPTION_KEY` — base64, 32 bytes, encrypts the GitHub access token at rest. Changing it invalidates previously-connected accounts; reconnect GitHub afterward.
- `ANTHROPIC_API_KEY` / `ANTHROPIC_MODEL` — used to generate AI insights.
