# DevPulse

DevPulse is an AI-powered developer intelligence platform for engineering teams.

## Prerequisites

- Java 21
- Maven 3.9+
- Node.js 22+ and npm
- Docker Desktop

## Run locally

1. Copy `.env.example` to `.env` and replace the local PostgreSQL password.
2. Start infrastructure: `docker compose up -d postgres redis`.
3. Copy `backend/.env.example` to a private local environment configuration and replace its password and JWT secret. Configure those values in the IntelliJ run configuration for `DevPulseApplication`.
4. Start the backend from IntelliJ by opening `backend/pom.xml`, selecting a Java 21 SDK, and running `DevPulseApplication`.
5. In another terminal, run `npm install` and `npm run dev` from `frontend`.

Endpoints:

- API health: `http://localhost:8080/api/v1/health`
- Actuator health: `http://localhost:8080/actuator/health`
- Web app: `http://localhost:5173`

## Authentication endpoints

- `POST /api/v1/auth/register` — `{ "email": "dev@example.com", "password": "at-least-12-characters" }`
- `POST /api/v1/auth/login` — returns an access token and user DTO
- `GET /api/v1/users/me` — requires `Authorization: Bearer <access-token>`

## Connect GitHub

1. Create an OAuth App at [GitHub Developer Settings](https://github.com/settings/developers). For local development, set its authorization callback URL to `http://localhost:8080/api/v1/integrations/github/callback`.
2. Add the Client ID and Client Secret to `backend/.env` as `GITHUB_CLIENT_ID` and `GITHUB_CLIENT_SECRET`, then configure the same values as environment variables in the IntelliJ run configuration. Keep them private.
3. Sign in to DevPulse, then call `POST /api/v1/integrations/github/authorize` with the access token. Open the returned `authorizationUrl` in a browser and approve GitHub access.
4. GitHub redirects back to the API, which links the GitHub account. Confirm the result with `GET /api/v1/integrations/github` using the same access token.

The connection currently requests `read:user` and `repo` scopes so DevPulse can identify the account and, in the next backend milestone, read repositories and pull-request activity.

## Environment variables

The root `.env` file is exclusively for local Docker Compose infrastructure. Future backend service secrets, including GitHub OAuth and LLM keys, will be documented in a separate backend environment template and never committed.
