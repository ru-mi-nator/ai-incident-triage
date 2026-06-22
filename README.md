# AI Incident Triage Portal

A full-stack MVP for submitting, assigning, AI-assisted triaging, and resolving software incidents with human-reviewed final outcomes.

## Core workflow

```text
Login
→ Incident list
→ Create incident
→ Incident details
→ Assign to me
→ Generate AI analysis
→ Resolve incident
→ Final resolved details
```

Support Engineers create and monitor incidents. Developers self-assign open incidents, optionally request advisory AI analysis, and record the authoritative final resolution.

## Tech stack

- Java 17, Spring Boot, Spring Security, JWT, Spring Data JPA, Flyway
- PostgreSQL 16
- Spring AI with OpenAI
- Angular 19, Angular Material, SCSS
- Maven, npm, Karma, Jasmine

## Architecture

The Angular single-page application calls relative `/api` endpoints through the local development proxy. Spring Boot owns authentication, authorization, incident lifecycle rules, synchronous AI orchestration, and persistence. PostgreSQL stores users, incidents, advisory AI analysis, and final human-entered resolution data.

```text
Angular UI → /api → Spring Boot → PostgreSQL
                         └──────→ OpenAI (when configured)
```

## Roles and lifecycle

| Role | Responsibilities |
| --- | --- |
| Support Engineer | Sign in, create incidents, view incidents, and request AI analysis for their own open incidents |
| Developer | Sign in, view incidents, self-assign open incidents, analyze assigned incidents, and resolve their own assigned incidents |

```text
OPEN → IN_PROGRESS → RESOLVED
```

Incidents cannot currently be edited, deleted, or reopened through the application.

## Main features

- JWT authentication with protected, role-aware routes
- Paginated incident list with preserved page context
- Validated incident creation with optional error logs
- Incident details with safe loading, empty, and error states
- Developer self-assignment
- Advisory AI category, priority, root-cause, and resolution suggestions
- Human-entered final category, priority, root cause, and resolution
- Responsive Angular Material interface
- Race protection for route changes and duplicate actions

## AI behaviour

AI analysis is synchronous and advisory. The assigned developer remains responsible for the final resolution. Automated tests use a mocked project-owned AI client.

Live OpenAI verification is pending and requires a valid local `OPENAI_API_KEY`. When no usable provider configuration is available, the API returns a safe unavailable response and leaves the incident unchanged. A local provider such as Ollama may be considered as a future enhancement; it is not implemented.

## Local prerequisites

- Java 17 or later
- Node.js 20 and npm 10
- Docker Desktop with Docker Compose

## Environment setup

Copy the example file and replace its placeholder values:

```powershell
Copy-Item .env.example .env
```

Required variables:

| Variable | Purpose |
| --- | --- |
| `POSTGRES_DB` | Database name |
| `POSTGRES_HOST` | Database host |
| `POSTGRES_USER` | Database user |
| `POSTGRES_PASSWORD` | Database password |
| `POSTGRES_PORT` | Host PostgreSQL port |
| `JWT_SECRET` | Base64-encoded JWT secret of at least 32 random bytes |
| `OPENAI_API_KEY` | Optional for live AI analysis |
| `OPENAI_MODEL` | OpenAI model name |

Do not commit `.env` or real secrets.

## Run locally

Start PostgreSQL from the repository root:

```powershell
docker compose up -d
docker compose ps
```

Load `.env` and start the backend:

```powershell
Get-Content .env | ForEach-Object {
    if ($_ -match '^\s*([^#][^=]*)=(.*)$') {
        [Environment]::SetEnvironmentVariable(
            $Matches[1].Trim(),
            $Matches[2],
            'Process'
        )
    }
}

Set-Location backend
.\mvnw.cmd spring-boot:run
```

In a separate PowerShell window, start Angular:

```powershell
Set-Location frontend
npm install
npm start
```

Open `http://localhost:4200/login`. The Angular proxy forwards `/api` requests to `http://localhost:8080`.

## Demo credentials

| Role | Username | Password |
| --- | --- | --- |
| Support Engineer | `support1` | `Support@123` |
| Support Engineer | `support2` | `Support@123` |
| Developer | `developer1` | `Developer@123` |
| Developer | `developer2` | `Developer@123` |

These accounts are for local demonstration only.

## Testing

Backend:

```powershell
Set-Location backend
.\mvnw.cmd test
```

Frontend:

```powershell
Set-Location frontend
npm test -- --watch=false --browsers=ChromeHeadless
npm run build
```

## API summary

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `POST` | `/api/auth/login` | Authenticate and issue a JWT |
| `GET` | `/api/incidents` | List incidents with pagination |
| `POST` | `/api/incidents` | Create an incident |
| `GET` | `/api/incidents/{id}` | Get incident details |
| `POST` | `/api/incidents/{id}/assign-to-me` | Assign an open incident to the current developer |
| `POST` | `/api/incidents/{id}/analyze` | Generate synchronous advisory AI analysis |
| `POST` | `/api/incidents/{id}/resolve` | Record the final resolution |

See [API Design](docs/api-design.md), [Database Design](docs/database-design.md), and [Product Requirements](docs/product-requirements.md).

## Current limitations

- Live OpenAI provider verification is pending.
- AI analysis runs synchronously.
- No incident editing, deletion, reopening, search, filters, dashboards, charts, notifications, or uploads.
- No registration, refresh tokens, Swagger, circuit breaker, or production deployment setup.
- This repository is an MVP and does not claim production readiness.

## Future enhancements

- Optional local AI provider support such as Ollama
- Asynchronous AI analysis and resilience controls
- Search, filtering, richer operational views, notifications, and deployment automation

The core MVP workflow is complete.
