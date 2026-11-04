Version 1 of Development
========================

# WHIM cloud deployment scaffold

This package includes the original WHIM application and a Railway/Docker-compatible Node.js + PostgreSQL API scaffold.

## Important status

- The existing browser application is preserved and still stores its inventory, user accounts, and session in browser `localStorage`.
- The API in this folder can store/retrieve JSON inventory snapshots in PostgreSQL, protected by a server-side bearer token.
- **The existing WHIM interface has not yet been wired to this API.** Merely deploying this scaffold will not synchronize records between devices or make the browser-local login cloud authentication.
- Do not use a shared API token in a public browser app. Before production integration, add per-user authentication/authorization and tenant isolation, then wire WHIM's load/save workflow to the API.

## Deploy API on Railway

1. Create a Railway project and add a PostgreSQL database.
2. Deploy this folder as a separate service (or deploy the repository and set the service root directory to `cloud-server`).
3. Add environment variables to the API service:
   - `DATABASE_URL`: Railway PostgreSQL connection URL (use the reference variable from Railway if available).
   - `WHIM_API_TOKEN`: a long random secret generated privately. Do not share it with customers or put it in frontend JavaScript.
4. Deploy and verify `https://YOUR-API-DOMAIN/health` returns `{"status":"ok","service":"whim-cloud-api"}`.
5. Keep the existing app deployed separately as a static site or web service. HTTPS is required.

## API endpoints

- `GET /health` — health check.
- `GET /api/inventory/:tenantId` — fetch a saved inventory snapshot.
- `PUT /api/inventory/:tenantId` — save `{ "payload": { ... } }`.

Both inventory endpoints require `Authorization: Bearer <WHIM_API_TOKEN>`. This is intended for server-side integration/testing only; do not call these endpoints directly from a public browser with the shared token.

## Production work still required

- Implement user sign-in on the server (secure password hashing, sessions/cookies or short-lived tokens).
- Enforce tenant authorization server-side; never trust a tenant ID supplied by an unauthenticated client.
- Integrate cloud load/save with WHIM's `loadState()` and `saveState()` and handle offline/error/conflict states.
- Migrate browser-local accounts/inventory deliberately; provide export/backup and restore.
- Add backups, database access controls, rate limiting, audit logging, and restore tests.
- Test licensing, inventory operations, multi-user access, mobile behavior, and concurrent edits before live use.

## Current data safety

Keep the original ZIP as a backup. The WHIM owner unlock key is not included. Never publish `WHIM_Owner_Unlock_Key.json` or upload it to a public repository.
