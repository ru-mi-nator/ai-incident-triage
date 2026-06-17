# AI Incident Triage Portal

AI Incident Triage Portal is a full-stack application that enables software support teams to submit incidents and receive AI-generated category, priority, probable root-cause, and resolution recommendations. Developers can review, accept, or override the AI suggestions before recording the final resolution, preserving a human-in-the-loop decision process.

## Core Product Capabilities

- Planned incident intake for support engineers with application, environment, description, and optional error logs.
- Designed AI-assisted triage that produces structured category, priority, probable root cause, and suggested resolution output.
- Planned human-in-the-loop developer review with the ability to accept or override AI recommendations.
- Designed separate persistence of original AI analysis and final human resolution decisions.
- Planned MVP incident lifecycle from `OPEN` to `IN_PROGRESS` to `RESOLVED`.
- Designed role-based access for `SUPPORT_ENGINEER` and `DEVELOPER` users.

## Human-in-the-Loop AI Workflow

The platform is designed so AI recommendations support, but do not replace, developer judgment. AI analysis is stored as read-only triage guidance, while the assigned developer records the final category, priority, actual root cause, and actual resolution.

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
- Angular
- PrimeNG
- PrimeNG Aura theme
- SCSS

## MVP Status

Design phase. Implementation is currently in progress, and features described in this repository are planned or designed unless explicitly marked otherwise.

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

## Implementation Note

Application code has not yet been generated in this repository. The current documentation defines the MVP scope, data model, API contracts, and intended user workflows.
