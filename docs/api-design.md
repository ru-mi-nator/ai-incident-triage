# API Design

## Overview

The AI Incident Triage Portal API is designed as a REST API secured with JWT authentication. The MVP uses one access token, seeded users, and role-based authorization for support engineer and developer workflows.

Implemented and planned endpoints use the `/api` base path.

## Authentication

The MVP includes login only. It does not include refresh tokens, token revocation, registration, password reset, user management, or a logout endpoint. The frontend logout action may clear the locally stored access token.

| Method | Endpoint | Auth required | Roles | Purpose |
| --- | --- | --- | --- | --- |
| `POST` | `/api/auth/login` | No | Public | Authenticate a seeded user and return a JWT access token |

Request:

```json
{
  "username": "support1",
  "password": "password"
}
```

Response:

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

## Metadata (Planned / Deferred)

The metadata API is not implemented in the backend MVP. Its planned design prevents Angular from duplicating backend enum definitions.

| Method | Endpoint | Auth required | Roles | Purpose |
| --- | --- | --- | --- | --- |
| `GET` | `/api/metadata` | Yes | `SUPPORT_ENGINEER`, `DEVELOPER` | Planned: return backend enum values used by forms and filters |

Response:

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

## Incident Endpoints

| Method | Endpoint | Auth required | Roles | Purpose |
| --- | --- | --- | --- | --- |
| `POST` | `/api/incidents` | Yes | `SUPPORT_ENGINEER` | Create a new incident |
| `GET` | `/api/incidents?page=0&size=10&sort=createdAt,desc` | Yes | `SUPPORT_ENGINEER`, `DEVELOPER` | List incident summaries with pagination |
| `GET` | `/api/incidents/{id}` | Yes | `SUPPORT_ENGINEER`, `DEVELOPER` | Get combined incident details |
| `PUT` | `/api/incidents/{id}` | Yes | Creator `SUPPORT_ENGINEER` | Planned/deferred: update eligible incident intake fields |
| `POST` | `/api/incidents/{id}/analyze` | Yes | `SUPPORT_ENGINEER`, `DEVELOPER` subject to ownership and state rules | Trigger synchronous advisory AI analysis |
| `POST` | `/api/incidents/{id}/assign-to-me` | Yes | `DEVELOPER` | Assign an open incident to the authenticated developer |
| `POST` | `/api/incidents/{id}/resolve` | Yes | Assigned `DEVELOPER` | Resolve an assigned incident |

### Create Incident

```http
POST /api/incidents
```

Only `SUPPORT_ENGINEER` may call this endpoint.

Successful response: HTTP `201 Created` with the combined incident-details response.

Request:

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

- Status to `OPEN`
- Creator from the authenticated user
- Created timestamp
- Updated timestamp

Validation limits:

- `title`: maximum 150 characters
- `description`: maximum 2,000 characters
- `errorLogs`: maximum 10,000 characters

### List Incidents

```http
GET /api/incidents?page=0&size=10&sort=createdAt,desc
```

Requirements:

- Both roles can view all incidents.
- Defaults are `page=0`, `size=10`, and `sort=createdAt,desc`.
- `page` must be at least `0`.
- `size` must be between `1` and `50`.
- Sort direction must be `asc` or `desc`.
- Allowed sort fields are `id`, `createdAt`, `updatedAt`, `title`, `applicationName`, `environment`, and `status`.
- Unknown fields, nested properties, and invalid directions return HTTP `400`, `VALIDATION_FAILED`, and `fieldErrors.sort`.
- Invalid page or size values return HTTP `400`, `VALIDATION_FAILED`, and the corresponding field error.
- Summary objects do not contain descriptions or error logs.
- Display priority uses final priority when available, otherwise AI-suggested priority, otherwise `null`.
- Display IDs use at least four digits of padding, such as `INC-0042`; IDs above 9999 are not truncated.

Response:

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
      "assignedDeveloper": {
        "id": 3,
        "name": "Developer User",
        "username": "developer1",
        "role": "DEVELOPER"
      },
      "createdAt": "2026-06-21T10:30:00Z"
    }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 1,
  "totalPages": 1,
  "first": true,
  "last": true
}
```

### Get Incident Details

```http
GET /api/incidents/{id}
```

Both `SUPPORT_ENGINEER` and `DEVELOPER` may retrieve any incident. The response contains the complete intake, safe user summaries, assignment and resolution state, timestamps, and optional AI analysis. Unknown IDs return HTTP `404` with `INCIDENT_NOT_FOUND`.

Response:

```json
{
  "id": 42,
  "displayId": "INC-0042",
  "title": "Login API returning 500",
  "description": "Users are unable to log in after the latest deployment.",
  "applicationName": "AUTH_SERVICE",
  "environment": "PROD",
  "errorLogs": "NullPointerException at AuthenticationService.java:84",
  "status": "IN_PROGRESS",
  "createdBy": {
    "id": 1,
    "name": "Support User",
    "username": "support1",
    "role": "SUPPORT_ENGINEER"
  },
  "assignedDeveloper": {
    "id": 2,
    "name": "Developer User",
    "username": "developer1",
    "role": "DEVELOPER"
  },
  "aiAnalysis": {
    "suggestedCategory": "AUTHENTICATION",
    "suggestedPriority": "HIGH",
    "probableRootCause": "The authentication service is failing after a recent deployment.",
    "suggestedResolution": "Review the authentication deployment configuration and restart the service after correction.",
    "modelName": "openai-model",
    "generatedAt": "2026-06-17T10:45:00Z"
  },
  "assignedAt": "2026-06-17T10:50:00Z",
  "finalCategory": null,
  "finalPriority": null,
  "actualRootCause": null,
  "actualResolution": null,
  "resolvedAt": null,
  "createdAt": "2026-06-17T10:30:00Z",
  "updatedAt": "2026-06-17T10:50:00Z"
}
```

### Update Incident (Planned / Deferred)

This endpoint is not implemented in the backend MVP. The following notes preserve its future API design.

```http
PUT /api/incidents/{id}
```

Use a dedicated `UpdateIncidentRequest`.

Editable fields:

- Title
- Description
- Application name
- Environment
- Error logs

Request:

```json
{
  "title": "Login API returning 500",
  "description": "Users cannot log in after the latest deployment.",
  "applicationName": "AUTH_SERVICE",
  "environment": "PROD",
  "errorLogs": "NullPointerException at AuthenticationService.java:84"
}
```

Rules:

- Only the support engineer who created the incident may edit it.
- Incident must be `OPEN`.
- Incident must not already have an AI analysis.
- System-managed fields must never be accepted from the client.

Dedicated request DTOs are used instead of exposing JPA entities to protect system-managed fields, prevent unintended updates, decouple API contracts from persistence models, support request-specific validation, and avoid circular relationship serialization.

Validation limits:

- `title`: maximum 150 characters
- `description`: maximum 2,000 characters
- `errorLogs`: maximum 10,000 characters

Successful response: HTTP `200 OK` with the updated combined incident-details response.

### Analyse Incident

```http
POST /api/incidents/{id}/analyze
```

No request body is required.

Rules:

- The authenticated support engineer may analyse only an incident they created while it is unassigned and `OPEN`.
- The authenticated developer may analyse only an incident assigned to them while it is `IN_PROGRESS`.
- Only one analysis is allowed.
- Resolved incidents cannot be analysed.
- The caller identity comes only from the JWT and is revalidated against the current database user and role.
- The request cannot supply a user identity or AI output.
- The model receives only the incident title, description, application, environment, and optional error logs.
- The OpenAI call runs without holding a database transaction or lock. A short persistence transaction then pessimistically locks and revalidates the incident before saving.
- The existing unique incident-analysis constraint provides final duplicate protection.
- Return HTTP `200` with the full incident-details response containing `aiAnalysis`.
- AI output is advisory and requires human review; it does not update final incident decisions.
- AI analysis is optional and is not required for a later human resolution workflow.

AI response validation limits:

- All four structured fields are required.
- Category and priority must match the documented enums.
- `probableRootCause`: maximum 2,000 characters
- `suggestedResolution`: maximum 3,000 characters
- Markdown-wrapped, incomplete, or otherwise unusable output is rejected and not persisted.

Errors:

- Unknown incident: HTTP `404`, `INCIDENT_NOT_FOUND`.
- Existing analysis: HTTP `409`, `AI_ANALYSIS_ALREADY_EXISTS`.
- Ineligible incident state: HTTP `409`, `INCIDENT_NOT_ANALYZABLE`.
- Caller is not the creator or assigned developer: HTTP `403`, `ACCESS_DENIED`.
- Provider failure or unusable structured output: HTTP `503`, `AI_SERVICE_UNAVAILABLE`.

### Assign Incident

```http
POST /api/incidents/{id}/assign-to-me
```

No request body is required.

Rules:

- Only `DEVELOPER` may call it.
- The assigned developer is always taken from the authenticated JWT user's database record.
- The incident must be `OPEN`.
- The incident must not already be assigned.
- Assignment uses a pessimistic database write lock to prevent concurrent successful assignment.
- Assign the authenticated developer.
- Set `assignedAt`.
- Change status to `IN_PROGRESS`.
- Return the full updated incident-details response.
- If already assigned, return HTTP `409`, `INCIDENT_ALREADY_ASSIGNED`, and `Incident is already assigned`.
- If not open, return HTTP `409`, `INCIDENT_NOT_OPEN`, and `Only open incidents can be assigned`.

### Resolve Incident

```http
POST /api/incidents/{id}/resolve
```

Request:

```json
{
  "finalCategory": "AUTHENTICATION",
  "finalPriority": "HIGH",
  "actualRootCause": "An invalid authentication configuration was deployed.",
  "actualResolution": "Corrected the configuration and restarted the authentication service."
}
```

Rules:

- Only `DEVELOPER` may call it.
- The authenticated developer must be the assigned developer.
- The incident must be `IN_PROGRESS`.
- Caller identity is taken from the JWT `userId`, then reloaded from the database to verify the current role is still `DEVELOPER`.
- The incident is loaded with a pessimistic write lock so concurrent requests cannot both perform a valid transition.
- All four fields are mandatory.
- Category and priority must use the documented incident enums.
- Root cause and resolution must not be blank; surrounding whitespace is trimmed before persistence.
- Set status to `RESOLVED`.
- Set `resolvedAt`.
- AI analysis is optional. Existing AI analysis remains unchanged and is included in the response when present.
- AI output is advisory; the final human-entered category, priority, root cause, and resolution are authoritative.
- Return HTTP `200` with the full updated incident-details response.
- Unknown incidents return HTTP `404` with `INCIDENT_NOT_FOUND`.
- Incidents not assigned to the authenticated developer return HTTP `403` with `ACCESS_DENIED`.
- Incidents outside `IN_PROGRESS`, including already resolved incidents, return HTTP `409` with `INCIDENT_NOT_RESOLVABLE`.

Validation limits:

- `actualRootCause`: maximum 2,000 characters
- `actualResolution`: maximum 3,000 characters

## DTO Design

The API is designed to use purpose-specific DTOs and must not expose JPA entities directly through REST endpoints.

| DTO | Purpose |
| --- | --- |
| `LoginRequest` | Login credentials |
| `LoginResponse` | JWT and authenticated user summary |
| `CreateIncidentRequest` | Incident intake creation |
| `UpdateIncidentRequest` | Planned editable incident intake fields |
| `ResolveIncidentRequest` | Final developer resolution fields |
| `IncidentDetailsResponse` | Combined incident details view |
| `IncidentSummaryResponse` | Paginated incident list item |
| `AiAnalysisResponse` | Read-only AI triage output |
| `MetadataResponse` | Planned enum metadata for frontend forms |
| `ApiErrorResponse` | Consistent error response contract |

Request and response DTOs should apply the confirmed validation limits:

| Field | Limit |
| --- | --- |
| `title` | Maximum 150 characters |
| `description` | Maximum 2,000 characters |
| `errorLogs` | Maximum 10,000 characters |
| `actualRootCause` | Maximum 2,000 characters |
| `actualResolution` | Maximum 3,000 characters |
| `probableRootCause` | Maximum 2,000 characters |
| `suggestedResolution` | Maximum 3,000 characters |

DTOs:

- Protect system-managed fields.
- Prevent unintended updates.
- Decouple API contracts from persistence models.
- Support request-specific validation.
- Avoid circular relationship serialization.

## Error Handling

All API errors should use one consistent response format containing timestamp, HTTP status, error code, message, and request path.

Example business error:

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

Planned business errors:

| Error | Typical status | Description |
| --- | --- | --- |
| `INCIDENT_NOT_FOUND` | `404` | Incident ID does not exist |
| `INCIDENT_ALREADY_ASSIGNED` | `409` | Assignment requested for an already assigned incident |
| `INCIDENT_NOT_OPEN` | `409` | Assignment requested for an incident that is not open |
| `AI_ANALYSIS_ALREADY_EXISTS` | `409` | A second AI analysis was requested |
| `INCIDENT_NOT_ANALYZABLE` | `409` | Incident state is not eligible for AI analysis |
| `AI_SERVICE_UNAVAILABLE` | `503` | Provider failed or returned unusable structured output |
| `INCIDENT_NOT_RESOLVABLE` | `409` | Resolution requested for an incident outside its eligible state |
| `INVALID_STATUS_TRANSITION` | `409` | Requested transition violates lifecycle rules |
| `USER_NOT_AUTHORIZED` | `403` | Authenticated user cannot perform the action |
| `INCIDENT_CANNOT_BE_EDITED` | `409` | Incident is not eligible for updates |
| `INCIDENT_ALREADY_RESOLVED` | `409` | Requested action is not allowed after resolution |
