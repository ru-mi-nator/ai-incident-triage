Create professional project design documentation for the **AI Incident Triage Portal**.

For this task, create documentation only. Do not generate Spring Boot, Angular, database migration, or implementation code.

Create these files:

* `docs/product-requirements.md`
* `docs/database-design.md`
* `docs/api-design.md`

Also update the project overview and planned features in the root `README.md` so that it is consistent with these documents.

## Product positioning

Present this as a serious, production-inspired AI-powered full-stack application suitable for a public GitHub portfolio and resume.

Use this product description:

> AI Incident Triage Portal is a full-stack platform that helps software support and engineering teams analyse incidents, assess priority, identify probable root causes, and generate resolution recommendations using AI.

The application follows a human-in-the-loop model:

* AI provides structured triage recommendations.
* Developers review the recommendations.
* Developers may accept or override the AI output.
* The final human decision remains separate from the original AI analysis.

Do not describe the project as:

* a personal learning project
* a beginner project
* a generic application
* a practice application
* a tutorial
* a demo-only application

Do not falsely claim that unfinished features are already implemented.

Use wording such as:

* planned
* designed
* proposed
* MVP scope
* intended workflow

when describing features that are not yet implemented.

## Product objectives

The platform is designed to:

* Reduce time spent on initial incident analysis.
* Generate consistent and structured AI triage results.
* Classify software incidents into predefined categories.
* Suggest incident priority.
* Identify probable technical root causes.
* Recommend possible resolution steps.
* Allow developers to review and override AI recommendations.
* Maintain both the original AI analysis and the final human resolution.
* Provide a clear workflow between support engineers and developers.

## Architecture scope

The MVP uses a modular monolithic architecture.

Planned technology stack:

* Java 17
* Spring Boot
* Spring Web
* Spring Security
* JWT authentication
* Spring Data JPA
* PostgreSQL
* Spring AI
* OpenAI
* Angular
* PrimeNG
* PrimeNG Aura theme
* SCSS

The following are outside the MVP scope:

* Microservices
* Kafka
* Redis
* Kubernetes
* Cloud deployment
* Distributed messaging
* User registration
* Password reset
* Refresh tokens
* File uploads
* Incident deletion
* Repeated AI analysis
* Advanced audit history
* RAG and vector databases

Mention RAG as a possible future enhancement, but do not include it in the MVP design.

## Users and roles

The application has two roles:

* `SUPPORT_ENGINEER`
* `DEVELOPER`

Users will initially be seeded in the database.

### Support Engineer capabilities

A Support Engineer can:

* Log in.
* View all incidents.
* View incident details.
* Create an incident.
* Edit only incidents they created.
* Edit an incident only while it is `OPEN` and has no AI analysis.
* Trigger AI analysis for an incident they created while it is `OPEN`.
* View the AI analysis.
* Track assignment and resolution status.
* View the final developer resolution.

### Developer capabilities

A Developer can:

* Log in.
* View all incidents.
* View incident details.
* Assign an unassigned `OPEN` incident to themselves.
* Trigger AI analysis for an assigned `IN_PROGRESS` incident if no analysis exists.
* Review the AI-generated category, priority, probable root cause, and suggested resolution.
* Accept or override AI suggestions.
* Enter the final category and priority.
* Enter the actual root cause and actual resolution.
* Resolve only incidents assigned to them.

## Incident lifecycle

The supported statuses are:

* `OPEN`
* `IN_PROGRESS`
* `RESOLVED`

The allowed transition is:

```text
OPEN -> IN_PROGRESS -> RESOLVED
```

Rules:

* New incidents start as `OPEN`.
* Assigning an incident changes it to `IN_PROGRESS`.
* Only the assigned developer may resolve it.
* Resolving requires final category, final priority, actual root cause, and actual resolution.
* Direct transition from `OPEN` to `RESOLVED` is not allowed.
* Resolved incidents cannot be edited or analysed.

## Incident creation fields

A Support Engineer enters:

* `title`
* `description`
* `applicationName`
* `environment`
* `errorLogs`

`errorLogs` is optional plain text.

Validation limits:

* Title: maximum 150 characters
* Description: maximum 2,000 characters
* Error logs: maximum 10,000 characters
* Actual root cause: maximum 2,000 characters
* Actual resolution: maximum 3,000 characters
* AI probable root cause: maximum 2,000 characters
* AI suggested resolution: maximum 3,000 characters

## Application names

Use a fixed backend enum:

* `AUTH_SERVICE`
* `PAYMENT_SERVICE`
* `ORDER_SERVICE`
* `NOTIFICATION_SERVICE`
* `REPORTING_SERVICE`

## Environments

Use a fixed backend enum:

* `DEV`
* `QA`
* `UAT`
* `PROD`

## Incident categories

Use a fixed backend enum:

* `API`
* `DATABASE`
* `AUTHENTICATION`
* `DEPLOYMENT`
* `PERFORMANCE`
* `NETWORK`
* `INTEGRATION`
* `CONFIGURATION`
* `OTHER`

## Priorities

Use a fixed backend enum:

* `LOW`
* `MEDIUM`
* `HIGH`
* `CRITICAL`

## AI analysis

Each incident can have zero or one AI analysis.

AI analysis is manually triggered.

In the MVP, the AI request is synchronous.

The AI returns structured output containing:

* Suggested category
* Suggested priority
* Probable root cause
* Suggested resolution
* Model name
* Generated timestamp

Rules:

* AI analysis is optional.
* Failure or absence of AI analysis must not prevent assignment or resolution.
* Only one AI analysis is allowed per incident.
* The AI result remains read-only after generation.
* The AI result must be stored separately from the developer’s final decision.
* The creator may analyse their own `OPEN` incident.
* The assigned developer may analyse their own `IN_PROGRESS` incident.
* Resolved incidents cannot be analysed.

Describe the AI workflow as:

```text
Incident details
    ->
Spring Boot AI service
    ->
Spring AI
    ->
OpenAI model
    ->
Structured triage response
    ->
Validation and persistence
    ->
Angular display
```

Explain that the backend should request structured output rather than relying on unstructured free-form text.

## Developer review

Developer review fields are stored inside the incident:

* Assigned developer
* Assigned timestamp
* Final category
* Final priority
* Actual root cause
* Actual resolution
* Resolved timestamp

The Angular interface will provide a button named:

```text
Use AI Suggestions
```

This button copies:

* Suggested category into final category
* Suggested priority into final priority
* Probable root cause into actual root cause
* Suggested resolution into actual resolution

The developer can edit these values before resolving the incident.

This is a frontend convenience feature and does not require a separate backend endpoint.

## Database

Database: PostgreSQL

Use these tables:

* `users`
* `incidents`
* `ai_analyses`

Use auto-generated `BIGINT` primary keys.

Store enums as readable strings rather than numeric values.

### users table

Fields:

* `id`
* `name`
* `username`
* `password`
* `role`

Constraints:

* `id` is the primary key.
* `username` is unique and not null.
* `password` is not null and stores a BCrypt hash.
* `role` is not null.

Suggested column sizes:

* `name`: `VARCHAR(100)`
* `username`: `VARCHAR(50)`
* `password`: `VARCHAR(255)`
* `role`: `VARCHAR(30)`

### incidents table

Fields:

* `id`
* `title`
* `description`
* `application_name`
* `environment`
* `error_logs`
* `status`
* `created_by_id`
* `assigned_developer_id`
* `final_category`
* `final_priority`
* `actual_root_cause`
* `actual_resolution`
* `created_at`
* `updated_at`
* `assigned_at`
* `resolved_at`

Relationships:

* `created_by_id` references `users.id`
* `assigned_developer_id` references `users.id`

Rules:

* `created_by_id` is mandatory.
* `assigned_developer_id` is nullable.
* Developer review fields are nullable until resolution.
* Assignment and resolution timestamps are nullable until those actions occur.
* Do not cascade deletion from users to incidents.

### ai_analyses table

Fields:

* `id`
* `incident_id`
* `suggested_category`
* `suggested_priority`
* `probable_root_cause`
* `suggested_resolution`
* `model_name`
* `generated_at`

Constraints:

* `incident_id` references `incidents.id`.
* `incident_id` is unique.
* An AI analysis cannot exist without an incident.
* Cascade deletion from an incident to its AI analysis.
* Each incident can have at most one AI analysis.

### Indexes

Include:

* Unique index on `users.username`
* Unique index on `ai_analyses.incident_id`
* Index on `incidents.status`
* Index on `incidents.created_by_id`
* Index on `incidents.assigned_developer_id`
* Index on `incidents.created_at`

Include a Mermaid entity-relationship diagram in `docs/database-design.md`.

## Authentication

Endpoint:

```http
POST /api/auth/login
```

Request example:

```json
{
  "username": "support1",
  "password": "password"
}
```

Response example:

```json
{
  "accessToken": "jwt-token",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "user": {
    "id": 1,
    "name": "Support User",
    "username": "support1",
    "role": "SUPPORT_ENGINEER"
  }
}
```

Use one JWT access token.

The MVP does not include:

* Refresh tokens
* Token revocation
* Registration
* Password reset
* User management
* Logout endpoint

The frontend logout action may clear the locally stored access token.

## Metadata API

Endpoint:

```http
GET /api/metadata
```

It returns backend enum values for:

* Applications
* Environments
* Categories
* Priorities

Example:

```json
{
  "applications": [
    "AUTH_SERVICE",
    "PAYMENT_SERVICE",
    "ORDER_SERVICE",
    "NOTIFICATION_SERVICE",
    "REPORTING_SERVICE"
  ],
  "environments": [
    "DEV",
    "QA",
    "UAT",
    "PROD"
  ],
  "categories": [
    "API",
    "DATABASE",
    "AUTHENTICATION",
    "DEPLOYMENT",
    "PERFORMANCE",
    "NETWORK",
    "INTEGRATION",
    "CONFIGURATION",
    "OTHER"
  ],
  "priorities": [
    "LOW",
    "MEDIUM",
    "HIGH",
    "CRITICAL"
  ]
}
```

Explain that this prevents Angular from duplicating backend enum definitions.

## Incident APIs

Document these endpoints:

### Create incident

```http
POST /api/incidents
```

Only `SUPPORT_ENGINEER` may call it.

Request example:

```json
{
  "title": "Login API returning 500",
  "description": "Users are unable to log in after the latest deployment.",
  "applicationName": "AUTH_SERVICE",
  "environment": "PROD",
  "errorLogs": "NullPointerException at AuthenticationService.java:84"
}
```

The backend automatically sets:

* Status to `OPEN`
* Creator from the authenticated user
* Created timestamp
* Updated timestamp

### List incidents

```http
GET /api/incidents?page=0&size=10&sort=createdAt,desc
```

Requirements:

* Both roles can view all incidents.
* Use pagination.
* Default page size: 10.
* Maximum page size: 50.
* Default sorting: newest first.
* Response should contain summary objects rather than full incident details.
* Display priority should use final priority when available.
* Otherwise use AI-suggested priority.
* Otherwise return null.

Example response structure:

```json
{
  "content": [
    {
      "id": 42,
      "displayId": "INC-0042",
      "title": "Login API returning 500",
      "applicationName": "AUTH_SERVICE",
      "environment": "PROD",
      "status": "OPEN",
      "priority": "HIGH",
      "assignedDeveloperName": null,
      "createdAt": "2026-06-17T10:30:00"
    }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 27,
  "totalPages": 3,
  "first": true,
  "last": false
}
```

### Get incident details

```http
GET /api/incidents/{id}
```

Return one combined response containing:

* Incident details
* Creator
* Assigned developer
* AI analysis
* Developer review
* Timestamps

The database ID remains numeric.

The UI displays it in a readable format such as:

```text
INC-0042
```

### Update incident

```http
PUT /api/incidents/{id}
```

Use a dedicated `UpdateIncidentRequest`.

Editable fields:

* Title
* Description
* Application name
* Environment
* Error logs

Rules:

* Only the Support Engineer who created the incident may edit it.
* Incident must be `OPEN`.
* Incident must not already have an AI analysis.
* System-managed fields must never be accepted from the client.

Explain why dedicated request DTOs are used instead of exposing JPA entities.

### Analyse incident

```http
POST /api/incidents/{id}/analyze
```

No request body is required.

Rules:

* The creator may analyse their own `OPEN` incident.
* The assigned developer may analyse their own `IN_PROGRESS` incident.
* Only one analysis is allowed.
* Resolved incidents cannot be analysed.
* Return the updated combined incident response.

### Assign incident

```http
POST /api/incidents/{id}/assign-to-me
```

No request body is required.

Rules:

* Only `DEVELOPER` may call it.
* The incident must be `OPEN`.
* The incident must not already be assigned.
* Assign the authenticated developer.
* Set `assignedAt`.
* Change status to `IN_PROGRESS`.
* Return the updated incident response.

### Resolve incident

```http
POST /api/incidents/{id}/resolve
```

Request example:

```json
{
  "finalCategory": "AUTHENTICATION",
  "finalPriority": "HIGH",
  "actualRootCause": "An invalid authentication configuration was deployed.",
  "actualResolution": "Corrected the configuration and restarted the authentication service."
}
```

Rules:

* Only `DEVELOPER` may call it.
* The incident must be `IN_PROGRESS`.
* The authenticated developer must be the assigned developer.
* All four fields are mandatory.
* Set status to `RESOLVED`.
* Set `resolvedAt`.
* Return the updated combined incident response.

## DTO design

Document that the API should use purpose-specific DTOs, including examples such as:

* `LoginRequest`
* `LoginResponse`
* `CreateIncidentRequest`
* `UpdateIncidentRequest`
* `ResolveIncidentRequest`
* `IncidentDetailsResponse`
* `IncidentSummaryResponse`
* `AiAnalysisResponse`
* `MetadataResponse`
* `ApiErrorResponse`

Do not expose JPA entities directly through REST endpoints.

Explain that DTOs:

* Protect system-managed fields.
* Prevent unintended updates.
* Decouple API contracts from persistence models.
* Support request-specific validation.
* Avoid circular relationship serialization.

## Error handling

Use one consistent API error format containing:

* Timestamp
* HTTP status
* Error code
* Message
* Request path

Example:

```json
{
  "timestamp": "2026-06-17T18:30:00",
  "status": 409,
  "errorCode": "INCIDENT_ALREADY_ASSIGNED",
  "message": "Incident is already assigned to another developer",
  "path": "/api/incidents/42/assign-to-me"
}
```

Validation errors should also return field-specific messages.

Example:

```json
{
  "timestamp": "2026-06-17T18:30:00",
  "status": 400,
  "errorCode": "VALIDATION_FAILED",
  "message": "Request validation failed",
  "path": "/api/incidents",
  "fieldErrors": {
    "title": "Title must not be blank",
    "description": "Description must not exceed 2000 characters"
  }
}
```

Document business errors including:

* Incident not found
* Incident already assigned
* AI analysis already exists
* Invalid status transition
* User not authorized
* Incident cannot be edited
* Incident already resolved
* AI provider failure
* Invalid AI response

## Frontend

Technology:

* Angular
* PrimeNG
* PrimeNG Aura theme
* SCSS
* PrimeIcons or Lucide icons

Screens:

* Login
* Incident list
* Create incident
* Incident details

Layout:

* Clean top navigation bar
* No sidebar in the MVP
* App name on the left
* Logged-in user, role, and logout action on the right
* Centered main content area
* Support Engineers see a `Create Incident` button
* Developers do not see the `Create Incident` button

Incident list:

* Use a PrimeNG table.
* Include pagination.
* Display incident ID, title, application, environment, status, priority, assigned developer, and created date.
* Use tags or badges for status, environment, and priority.
* Keep filters, search, and advanced sorting as future enhancements.

Create incident page:

* Title
* Description
* Application dropdown
* Environment dropdown
* Error logs textarea
* Client-side validation
* Redirect to the incident-details page after successful creation

Incident-details page:

Show everything on one page in three clearly separated sections:

1. Original incident details
2. AI analysis
3. Developer review and resolution

Do not use tabs in the MVP.

Use clean PrimeNG panels, bordered sections, or surface containers rather than visually heavy card layouts everywhere.

## Repository structure

Document this repository structure:

```text
ai-incident-triage/
├── backend/
├── frontend/
├── docs/
│   ├── product-requirements.md
│   ├── database-design.md
│   └── api-design.md
├── README.md
└── .gitignore
```

## README update

Update the root `README.md` with:

* Project name
* Professional AI-focused overview
* Core product capabilities
* Human-in-the-loop AI workflow
* Planned technology stack
* MVP status
* Repository structure
* Link to each design document
* Clear statement that implementation is currently in progress

Use this overview:

> AI Incident Triage Portal is a full-stack application that enables software support teams to submit incidents and receive AI-generated category, priority, probable root-cause, and resolution recommendations. Developers can review, accept, or override the AI suggestions before recording the final resolution, preserving a human-in-the-loop decision process.

Do not include exaggerated claims such as percentage improvements unless supported by actual measurements.

## Documentation expectations

* Keep the writing professional and suitable for a public GitHub repository and resume portfolio.
* Position the platform as an AI-powered engineering productivity and incident-management application.
* Emphasize structured AI output and human review.
* Include tables where they improve readability.
* Include a Mermaid workflow diagram in `product-requirements.md`.
* Include a Mermaid entity-relationship diagram in `database-design.md`.
* Include endpoint tables and sample JSON in `api-design.md`.
* Document authorization and business rules clearly.
* Keep terminology consistent across all files.
* Do not generate application code.
* Do not initialize Spring Boot or Angular.
* Do not install dependencies.
* Do not commit or push changes.

After finishing:

* Show the files created or updated.
* Briefly summarize each document.
* Show the final documentation tree.
* List any assumptions made.
