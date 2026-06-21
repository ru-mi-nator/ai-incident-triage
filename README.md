# AI Incident Triage Portal

AI Incident Triage Portal is a full-stack application that enables software support teams to submit incidents and receive AI-generated category, priority, probable root-cause, and resolution recommendations. Developers can review, accept, or override the AI suggestions before recording the final resolution, preserving a human-in-the-loop decision process.

## Core Product Capabilities

- Implemented incident intake for support engineers with application, environment, description, and optional error logs.
- Implemented paginated incident summary listing for support engineers and developers.
- Implemented full incident-details retrieval for support engineers and developers.
- Implemented developer self-assignment for open, unassigned incidents.
- Implemented synchronous AI-assisted triage that produces structured category, priority, probable root cause, and suggested resolution output.
- Implemented assigned-developer resolution with authoritative human-entered category, priority, root cause, and resolution.
- Designed separate persistence of original AI analysis and final human resolution decisions.
- Implemented the MVP incident lifecycle from `OPEN` to `IN_PROGRESS` to `RESOLVED`.
- Designed role-based access for `SUPPORT_ENGINEER` and `DEVELOPER` users.

## Human-in-the-Loop AI Workflow

AI recommendations support, but do not replace, developer judgment. Synchronous analysis is stored as read-only advisory triage guidance, while the assigned developer remains responsible for final category, priority, root cause, and resolution decisions.

## Planned Technology Stack

- Java 17
- Spring Boot
- Spring Web
- Spring Security
- JWT authentication
- Spring Data JPA
- PostgreSQL
- Spring AI
- OpenAI
- Angular 19
- Angular Material
- SCSS

## MVP Status

The backend MVP is complete. It implements seven workflows: login, incident creation, incident listing, incident details, developer self-assignment, advisory AI analysis, and assigned-developer resolution. The Angular application foundation and authentication flow are implemented; incident UI and live OpenAI provider verification are still pending.

## Repository Structure

```text
ai-incident-triage/
|-- backend/
|-- frontend/
|-- docs/
|   |-- product-requirements.md
|   |-- database-design.md
|   `-- api-design.md
|-- README.md
`-- .gitignore
```

## Design Documentation

- [Product Requirements](docs/product-requirements.md)
- [Database Design](docs/database-design.md)
- [API Design](docs/api-design.md)

## Local PostgreSQL Setup

Copy the example environment file:

```powershell
Copy-Item .env.example .env
```

Start PostgreSQL:

```powershell
docker compose up -d
```

Check container status:

```powershell
docker compose ps
```

Stop PostgreSQL without deleting data:

```powershell
docker compose down
```

For a full database reset, stop PostgreSQL and delete the database volume:

```powershell
docker compose down -v
```

This command intentionally deletes the PostgreSQL volume and should only be used when a full local database reset is needed.

## Local demo credentials

These credentials are for local/demo use only:

| Role | Username | Password |
| --- | --- | --- |
| Support Engineer | `support1` | `Support@123` |
| Support Engineer | `support2` | `Support@123` |
| Developer | `developer1` | `Developer@123` |
| Developer | `developer2` | `Developer@123` |

Registration and user management are intentionally not part of the MVP. Only BCrypt password hashes are stored in PostgreSQL. These credentials must not be used for a real production deployment.

## Frontend development

The frontend uses Node.js `20.12.2`, npm `10.5.0`, Angular `19.2.x`, standalone components, Angular Material, and Karma/Jasmine.

Start PostgreSQL and the Spring Boot backend on port `8080` before starting the frontend. Then run:

```powershell
Set-Location frontend
npm install
npm start
```

Open `http://localhost:4200`. The `npm start` command loads `proxy.conf.json`, which forwards relative `/api` requests to `http://localhost:8080`; no browser CORS configuration is required for local development.

The current frontend slice includes login, session restoration through `sessionStorage`, bearer-token interception, protected routing, a reusable authenticated shell, logout, and a minimal dashboard. Incident listing, creation, details, assignment, AI analysis, resolution, filters, pagination, dashboards, and charts remain pending.

The login screen shows the seeded demo usernames `support1` and `developer1`. Passwords are not prefilled or stored.

## Implementation Note

The repository currently includes the Spring Boot backend foundation, local PostgreSQL Docker setup, Flyway schema and seeded demo users, JPA entities, Spring Data repositories, JWT authentication, incident creation, paginated incident summary listing, full incident-details retrieval, developer self-assignment, synchronous OpenAI-backed incident analysis, assigned-developer incident resolution, and the Angular authentication foundation. Automated analysis tests use a mocked project-owned AI client; live OpenAI verification remains pending until a valid local API key is available. Editing, metadata, filtering/search, deletion, asynchronous AI processing, and incident frontend features are still pending.
