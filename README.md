# Keycloak OAuth2/OIDC Demo Suite

A collection of small demo apps showing different ways to integrate with
**Keycloak** for authentication/authorization: a pure-frontend Authorization
Code flow, a backend-mediated flow, a confidential-client OAuth flow, and an
ASP.NET Core Razor Pages OIDC integration. Everything runs against a single
shared Keycloak instance (realm `test`) via Docker Compose.

## Services

| Service | Path | Port | Stack | Auth pattern | Keycloak client |
|---|---|---|---|---|---|
| Keycloak | — | `8080` | Keycloak 26.1.4 | Identity provider (realm `test`, auto-imported) | — |
| bookstore-fe | [bookstore-fe/](bookstore-fe/) | `3002` | React (TS) | Authorization Code flow, token exchange **in the browser** | `test-client` |
| bookstore-api | [bookstore-api/](bookstore-api/) | `3003` | Express | Verifies tokens via **introspection** | `test-client` |
| bookstore-fe-2 | [bookstore-fe-2/](bookstore-fe-2/) | `3012` | React (TS) | SPA calling a backend for token exchange | `test-client-2` |
| bookstore-api-2 | [bookstore-api-2/](bookstore-api-2/) | `3013` | Express | Exchanges auth code for tokens **server-side** | `test-client-2` |
| bookstore-fe-oauth | [bookstore-fe-oauth/](bookstore-fe-oauth/) | `3022` | React (TS) | Same pattern as `-2`, parallel stack | `test-client-oauth` |
| bookstore-api-oauth | [bookstore-api-oauth/](bookstore-api-oauth/) | `3023` | Express | Same pattern as `-2`, parallel stack | `test-client-oauth` |
| razor-demo | [razor-demo/](razor-demo/) | `5100` | ASP.NET Core Razor Pages | `AddOpenIdConnect` (Authorization Code + PKCE), server-side session cookie | `razor-demo-client` |
| bookstore-saml-fe | [bookstore-saml-fe/](bookstore-saml-fe/) | `3032` | React (TS) | SPA calling a backend that holds the SAML session (httpOnly cookie) | `bookstore-saml` |
| bookstore-saml-api | [bookstore-saml-api/](bookstore-saml-api/) | `3033` | Express | **SAML 2.0** Service Provider (passport-saml), roles via assertion attribute | `bookstore-saml` |
| test-keycloak-fe | [test-keycloak-fe/](test-keycloak-fe/) | — | React (TS) | Standalone scratch/test app, not wired into `docker-compose.yml` | — |

Each bookstore pair (`-fe`/`-api`, `-fe-2`/`-api-2`, `-fe-oauth`/`-api-oauth`)
is a full demo of a books CRUD app protected by Keycloak, with its own
Keycloak client so the stacks can run side by side without colliding.

## Architecture

- **Realm config**: [keycloak-config/test-realm.json](keycloak-config/test-realm.json) is imported automatically on Keycloak startup (`start-dev --import-realm`). It defines the `test` realm and all clients listed above.
- **Networking**: all containers share the `keycloak-net` bridge network. Backends and the Razor app add `extra_hosts: localhost:host-gateway` so that token issuer/audience checks against `http://localhost:8080` match what the browser sees.
- **Secrets/config**: each service reads its Keycloak URL, realm, client ID/secret, and port from its own `.env` (see each folder's `.env.example`) — nothing is hardcoded in `docker-compose.yml`.
- **Token storage**: the pure-frontend demo (`bookstore-fe`) stores the access token in a browser cookie and calls the API directly; the `-2`/`-oauth` pairs exchange the code for tokens on the backend instead.
- **SAML**: the `bookstore-saml-fe`/`-api` pair uses SAML 2.0 instead of OAuth — no tokens, the backend holds a session after Keycloak posts a signed assertion. Step-by-step Keycloak SAML client configuration (required vs optional settings) is documented in [bookstore-saml-api/README.md](bookstore-saml-api/README.md#các-bước-cấu-hình-keycloak).

## Running everything

```bash
# from repo root
docker compose up -d --build
```

This starts Keycloak plus every wired-up service. Bring up a subset with
`docker compose up -d --build <service-name>` (Keycloak will be started
automatically as a dependency).

Default Keycloak admin console: `http://localhost:8080` (admin/admin).

To run a single app outside Docker, `cd` into its folder, copy `.env.example`
to `.env`, fill in the client secret, and follow that folder's own README.

## Repo layout

```
docker-compose.yml         # orchestrates Keycloak + all demo services
keycloak-config/           # realm export auto-imported into Keycloak
bookstore-fe/               bookstore-api/            # pure-frontend OAuth demo (port 3002/3003)
bookstore-fe-2/              bookstore-api-2/          # backend-mediated OAuth demo (port 3012/3013)
bookstore-fe-oauth/          bookstore-api-oauth/      # parallel OAuth demo stack (port 3022/3023)
razor-demo/                 # ASP.NET Core Razor Pages OIDC demo (port 5100)
bookstore-saml-fe/           bookstore-saml-api/       # SAML 2.0 demo, Keycloak as IdP (port 3032/3033)
test-keycloak-fe/           # standalone scratch app, not in docker-compose
```

> Note: some per-service `README.md` files were copy-pasted between the
> `-2`/`-oauth` variants and may reference the wrong port/client — trust
> each service's `.env.example` and `docker-compose.yml` over its README
> for exact ports and client IDs.
