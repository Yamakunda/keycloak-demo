# bookstore-saml-api

A minimal **SAML 2.0 Service Provider**: an Express backend that authenticates
against **Keycloak** acting as the SAML Identity Provider, keeps a server-side
session (httpOnly cookie), and exposes a JSON books API consumed by
[bookstore-saml-fe](../bookstore-saml-fe/) (port 3032).

Unlike the OAuth/OIDC bookstore demos in this repo, there are no tokens here —
Keycloak POSTs a signed XML assertion to this app's ACS endpoint. That's why
the SAML part must live on a backend: a pure SPA can never receive or verify
the assertion.

## Flow

1. Browser hits `GET /login` (full-page redirect from the FE) → app builds an
   `AuthnRequest` and redirects to Keycloak's SAML endpoint
   (`/realms/test/protocol/saml`).
2. User logs in on Keycloak (e.g. `test` / `123456`).
3. Keycloak POSTs a **signed SAMLResponse** to `POST /saml/acs`
   (Assertion Consumer Service).
4. The app verifies the signature against the realm's signing certificate
   (fetched automatically at startup from the IdP metadata at
   `/realms/test/protocol/saml/descriptor`), starts a session, sets the
   httpOnly cookie, and redirects the browser back to the FE (`:3032`).
5. The FE calls the JSON API with `credentials: "include"`; roles arrive as a
   `Role` attribute in the assertion (mapped by `saml-role-list-mapper`).

## Authorization

| Action | Requirement |
|---|---|
| `GET /api/me`, `GET /api/books` | any authenticated session |
| `POST /api/books` | realm role `book-admin` |
| `DELETE /api/books/:id` | realm role `book-admin` |

The realm import gives the `test` user the `book-admin` role. Create a second
user without the role to see the read-only view / 403.

## Endpoints

| Endpoint | Purpose |
|---|---|
| `GET /login` | Start SP-initiated SSO (browser navigation, not fetch) |
| `POST /saml/acs` | Assertion Consumer Service (Keycloak posts assertions here) |
| `GET /metadata` | SP metadata XML (importable into Keycloak) |
| `GET /logout` | SP-initiated Single Logout (sends `LogoutRequest` to Keycloak) |
| `GET/POST /saml/logout/callback` | Where Keycloak returns after logout |
| `GET /api/me` | Current user + roles + raw assertion attributes |
| `GET/POST/DELETE /api/books…` | Books CRUD (JSON) |

## Keycloak client

Defined in [keycloak-config/test-realm.json](../keycloak-config/test-realm.json):
client `bookstore-saml`, protocol `saml`, assertions + documents signed by the
IdP, client signature **off** (so the SP doesn't need its own keypair), NameID
format `username`, and a role-list mapper emitting a single `Role` attribute.

> The realm JSON is only imported when the realm doesn't exist yet. If your
> Keycloak container already ran before this client was added, either recreate
> it (`docker compose up -d --force-recreate keycloak` — re-imports the realm
> but wipes manual changes like LDAP federation) or create the client/roles by
> hand or via `kcadm.sh`.

## Run

```bash
# via docker compose (from repo root)
docker compose up -d --build bookstore-saml-api bookstore-saml-fe

# or locally
cp .env.example .env
npm install
npm start
```

API on http://localhost:3033, UI on http://localhost:3032 (`test` / `123456`).
