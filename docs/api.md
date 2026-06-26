# API Design

## Auth

```text
POST /api/auth/register
POST /api/auth/login
POST /api/auth/refresh
```

## User

```text
GET /api/users/me
```

## API Key

```text
POST  /api/api-keys
GET   /api/api-keys
PATCH /api/api-keys/{id}/disable
```

## Model

```text
GET /api/models
```

## Request Log

```text
GET /api/request-logs
```

## Gateway

```text
POST /v1/chat/completions
```

