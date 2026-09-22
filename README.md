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

## Environment variables

The root `.env` file is exclusively for local Docker Compose infrastructure. Future backend service secrets, including GitHub OAuth and LLM keys, will be documented in a separate backend environment template and never committed.
