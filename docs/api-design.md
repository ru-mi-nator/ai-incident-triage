# API Design

## Overview

The AI Incident Triage Portal API is designed as a REST API secured with JWT authentication. The MVP uses one access token, seeded users, and role-based authorization for support engineer and developer workflows.

All endpoints are planned under the `/api` base path.

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

## Metadata

The metadata API prevents Angular from duplicating backend enum definitions.

| Method | Endpoint | Auth required | Roles | Purpose |
| --- | --- | --- | --- | --- |
| `GET` | `/api/metadata` | Yes | `SUPPORT_ENGINEER`, `DEVELOPER` | Return backend enum values used by forms and filters |

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
| `PUT` | `/api/incidents/{id}` | Yes | Creator `SUPPORT_ENGINEER` | Update eligible incident intake fields |
| `POST` | `/api/incidents/{id}/analyze` | Yes | Eligible creator or assigned developer | Trigger AI analysis |
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
- Use pagination.
- Default page size is 10.
- Maximum page size is 50.
- Default sorting is newest first.
- Response should contain summary objects rather than full incident details.
- Display priority should use final priority when available.
- Otherwise use AI-suggested priority.
- Otherwise return `null`.

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

### Get Incident Details

```http
GET /api/incidents/{id}
```

Returns one combined response containing incident details, creator, assigned developer, AI analysis, developer review, and timestamps. The database ID remains numeric, while the UI can display it in a readable format such as `INC-0042`.

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
  "creator": {
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
    "generatedAt": "2026-06-17T10:45:00"
  },
  "developerReview": {
    "finalCategory": null,
    "finalPriority": null,
    "actualRootCause": null,
    "actualResolution": null
  },
  "createdAt": "2026-06-17T10:30:00",
  "updatedAt": "2026-06-17T10:50:00",
  "assignedAt": "2026-06-17T10:50:00",
  "resolvedAt": null
}
```

### Update Incident

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

- The creator may analyse their own `OPEN` incident.
- The assigned developer may analyse their own `IN_PROGRESS` incident.
- Only one analysis is allowed.
- Resolved incidents cannot be analysed.
- Return the updated combined incident response.

AI response validation limits:

- `probableRootCause`: maximum 2,000 characters
- `suggestedResolution`: maximum 3,000 characters

### Assign Incident

```http
POST /api/incidents/{id}/assign-to-me
```

No request body is required.

Rules:

- Only `DEVELOPER` may call it.
- The incident must be `OPEN`.
- The incident must not already be assigned.
- Assign the authenticated developer.
- Set `assignedAt`.
- Change status to `IN_PROGRESS`.
- Return the updated incident response.

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
- The incident must be `IN_PROGRESS`.
- The authenticated developer must be the assigned developer.
- All four fields are mandatory.
- Set status to `RESOLVED`.
- Set `resolvedAt`.
- Return the updated combined incident response.

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
| `UpdateIncidentRequest` | Editable incident intake fields |
| `ResolveIncidentRequest` | Final developer resolution fields |
| `IncidentDetailsResponse` | Combined incident details view |
| `IncidentSummaryResponse` | Paginated incident list item |
| `AiAnalysisResponse` | Read-only AI triage output |
| `MetadataResponse` | Enum metadata for frontend forms |
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
| `AI_ANALYSIS_ALREADY_EXISTS` | `409` | A second AI analysis was requested |
| `INVALID_STATUS_TRANSITION` | `409` | Requested transition violates lifecycle rules |
| `USER_NOT_AUTHORIZED` | `403` | Authenticated user cannot perform the action |
| `INCIDENT_CANNOT_BE_EDITED` | `409` | Incident is not eligible for updates |
| `INCIDENT_ALREADY_RESOLVED` | `409` | Requested action is not allowed after resolution |
| `AI_PROVIDER_FAILURE` | `502` | AI provider call failed |
| `INVALID_AI_RESPONSE` | `502` | AI response failed structured validation |
