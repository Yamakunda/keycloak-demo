# bookstore-saml-fe

React (TypeScript) frontend for the **SAML 2.0** bookstore demo. Pairs with
[bookstore-saml-api](../bookstore-saml-api/) the same way the other `-fe`/`-api`
couples in this repo do.

## How auth works here

The SPA never touches SAML or Keycloak directly — SAML has no browser-side
artifact like an OAuth token. Instead:

1. "Login" is a **full-page redirect** to `bookstore-saml-api`'s `/login`,
   which starts the SAML dance with Keycloak.
2. Keycloak POSTs the signed assertion to the API's ACS endpoint; the API
   starts a session and sets an **httpOnly session cookie** on
   `localhost:3033`, then redirects the browser back here (`localhost:3032`).
3. The SPA calls the API with `fetch(..., { credentials: "include" })`. The
   cookie lives in the browser but is scoped to the API origin and invisible
   to JavaScript (httpOnly) — the FE never stores or reads any credential.
4. Roles come from `GET /api/me` (extracted from the SAML assertion server-side):
   `book-admin` sees add/delete controls, everyone else is read-only.

`localhost:3032` → `localhost:3033` is same-site (port doesn't matter for
SameSite), so the default `SameSite=Lax` cookie is sent on API calls without
needing `SameSite=None`/HTTPS.

## Run

```bash
# via docker compose (from repo root) — builds FE + API
docker compose up -d --build bookstore-saml-fe bookstore-saml-api

# or locally
cp .env.example .env
npm install
npm start   # dev server on http://localhost:3032
```

Open http://localhost:3032 and log in with `test` / `123456`.
