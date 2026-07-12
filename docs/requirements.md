# Requirements

> This file records the original V1 learning baseline. Current implemented capabilities and boundaries are documented in `ARCHITECTURE.md`, `CODEBASE.md`, and `docs/api.md`.

## V1 Scope

- Users can register and log in.
- Users can create, list, and disable platform API keys.
- Users can bind official provider API keys.
- Admins can configure platform provider keys.
- The gateway exposes one chat-completions style endpoint.
- The system records request logs, usage records, wallet transactions, and audit logs.
- Redis is used for basic rate limiting.

## Out Of Scope For This Stage

- Hosting third-party account passwords.
- Cookie or web-session reverse proxying.
- Multiple backend services.
- Real payment integration.
- Complex frontend dashboard.
