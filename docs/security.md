# Security Notes

## Authentication Types

- Web account authentication: username/password plus JWT.
- Gateway authentication: platform API Key in request headers.
- Provider authentication: official Provider Key stored encrypted and used only server-side.

## Logging Rules

- Do not log `Authorization`.
- Do not log platform API Key.
- Do not log Provider Key.
- Do not log passwords.
- Mask sensitive values when they must appear in diagnostic messages.

