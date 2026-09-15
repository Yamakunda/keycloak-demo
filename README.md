# Keycloak OAuth2/OIDC Demo Suite

A collection of small demo apps showing different ways to integrate with
**Keycloak** for authentication/authorization: a pure-frontend Authorization
Code flow, a backend-mediated flow, a confidential-client OAuth flow, an
ASP.NET Core Razor Pages OIDC integration, native Android passkey/biometric
apps, and a custom Keycloak SPI that adds **QR cross-device login** as a
selectable option right on Keycloak's own login page. Everything runs
against a single shared Keycloak instance (realm `test`) via Docker Compose.

## Services

| Service | Path | Port | Stack | Auth pattern | Keycloak client |
|---|---|---|---|---|---|
| Keycloak | — | `8080` | Keycloak 26.1.4 | Identity provider (realm `test`, auto-imported) + custom QR login SPI | — |
| bookstore-fe | [bookstore-fe/](bookstore-fe/) | `3002` | React (TS) | Authorization Code flow, token exchange **in the browser** | `test-client` |
| bookstore-api | [bookstore-api/](bookstore-api/) | `3003` | Express | Verifies tokens via **introspection** | `test-client` |
| bookstore-fe-2 | [bookstore-fe-2/](bookstore-fe-2/) | `3012` | React (TS) | SPA calling a backend for token exchange | `test-client-2` |
| bookstore-api-2 | [bookstore-api-2/](bookstore-api-2/) | `3013` | Express | Exchanges auth code for tokens **server-side** | `test-client-2` |
| bookstore-fe-oauth | [bookstore-fe-oauth/](bookstore-fe-oauth/) | `3022` | React (TS) | Same pattern as `-2`, parallel stack | `test-client-oauth` |
| bookstore-api-oauth | [bookstore-api-oauth/](bookstore-api-oauth/) | `3023` | Express | Same pattern as `-2`, parallel stack | `test-client-oauth` |
| razor-demo | [razor-demo/](razor-demo/) | `5100` | ASP.NET Core Razor Pages | `AddOpenIdConnect` (Authorization Code + PKCE), server-side session cookie | `razor-demo-client` |
| bookstore-saml-fe | [bookstore-saml-fe/](bookstore-saml-fe/) | `3032` | React (TS) | SPA calling a backend that holds the SAML session (httpOnly cookie) | `bookstore-saml` |
| bookstore-saml-api | [bookstore-saml-api/](bookstore-saml-api/) | `3033` | Express | **SAML 2.0** Service Provider (passport-saml), roles via assertion attribute | `bookstore-saml` |
| bookstore-api-mobile | [bookstore-api-mobile/](bookstore-api-mobile/) | `3043` | Express | Verifies JWT via JWKS for the native Android apps | `passwordless-demo` / `biometric-demo` |
| bookstore-android | [bookstore-android/](bookstore-android/) | — | Android (Kotlin, Compose) | **Passkey/WebAuthn** passwordless login via Chrome Custom Tabs + AppAuth, not in `docker-compose.yml` | `passwordless-demo` |
| bookstore-android-biometric | [bookstore-android-biometric/](bookstore-android-biometric/) | — | Android (Kotlin, Compose) | Login once via Custom Tab, then **local biometric unlock** (refresh_token encrypted in Android Keystore); also scans the Keycloak login QR to approve cross-device sign-in, not in `docker-compose.yml` | `biometric-demo` |
| bookstore-api-qr | [bookstore-api-qr/](bookstore-api-qr/) | `3053` | Express | Legacy Node.js QR cross-device backend (pre-SPI), holds `qr-login-confidential` client secret | `qr-login-confidential` |
| bookstore-fe-qr | [bookstore-fe-qr/](bookstore-fe-qr/) | `3052` | React (TS) | Legacy QR page that talks to `bookstore-api-qr` (`/qr/*`), draws its own QR code | — (calls `bookstore-api-qr`) |
| bookstore-fe-qr-2 | [bookstore-fe-qr-2/](bookstore-fe-qr-2/) | `3060` | React (TS) | **Real public OAuth client** (Authorization Code + PKCE) that redirects straight to Keycloak's own login page, used to test the QR SPI's "Try another way" option | `test-qr-web-2` |
| keycloak-spi-qr-login | [keycloak-spi-qr-login/](keycloak-spi-qr-login/) | — | Java (Keycloak SPI, Maven) | Custom `RealmResourceProvider` (`/realms/{realm}/qr-login/*`) + `Authenticator` that adds "Login with QR code" as an alternative step on Keycloak's built-in login flow | — (runs inside Keycloak) |
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
- **Android apps**: both native apps open a Chrome Custom Tab (not a WebView — Google has blocked OAuth login in WebViews since 2016) to run Authorization Code + PKCE against Keycloak, then call `bookstore-api-mobile` for book data. `bookstore-android` adds passwordless WebAuthn/passkey login; `bookstore-android-biometric` adds local biometric unlock (Android Keystore-encrypted refresh token) and can scan a QR code shown on any device's Keycloak login page to approve that login using its own already-valid access token.
- **QR cross-device login — two parallel implementations**:
  - *Legacy (`bookstore-api-qr` + `bookstore-fe-qr`)*: a standalone Node.js/React stack outside Keycloak. The web page draws its own QR, the phone app calls `bookstore-api-qr`'s `/qr/*` REST routes, which hold the `qr-login-confidential` client secret and mint tokens via the standard token endpoint.
  - *Current (`keycloak-spi-qr-login` + `bookstore-fe-qr-2`)*: a custom SPI running **inside** Keycloak itself. It adds a "Login with QR code" `Authenticator` as an `ALTERNATIVE` step alongside the username/password form, surfaced via Keycloak's built-in "Try another way" link — so **any** OAuth client using the default login page gets QR login for free, no separate page needed. The phone app calls `/realms/{realm}/qr-login/approve` directly (using Keycloak's internal `TokenManager` to mint real tokens), and `bookstore-fe-qr-2` is just a plain OAuth test client used to exercise this flow end-to-end.
  - The Android app currently calls the SPI route (`/qr-login/approve`); the legacy Node.js stack is kept for reference but no longer wired to the app.

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
keycloak-spi-qr-login/     # custom Keycloak SPI: QR login as an alternative on the built-in login page
bookstore-fe/               bookstore-api/            # pure-frontend OAuth demo (port 3002/3003)
bookstore-fe-2/              bookstore-api-2/          # backend-mediated OAuth demo (port 3012/3013)
bookstore-fe-oauth/          bookstore-api-oauth/      # parallel OAuth demo stack (port 3022/3023)
razor-demo/                 # ASP.NET Core Razor Pages OIDC demo (port 5100)
bookstore-saml-fe/           bookstore-saml-api/       # SAML 2.0 demo, Keycloak as IdP (port 3032/3033)
bookstore-api-mobile/       # JWKS-verifying API shared by the native Android apps (port 3043)
bookstore-android/          # Android passkey/WebAuthn passwordless demo, not in docker-compose
bookstore-android-biometric/ # Android biometric-unlock + QR-scan-to-approve demo, not in docker-compose
bookstore-api-qr/            bookstore-fe-qr/          # legacy Node.js/React QR cross-device login (port 3053/3052)
bookstore-fe-qr-2/          # plain OAuth test client for the QR SPI's "Try another way" flow (port 3060)
test-keycloak-fe/           # standalone scratch app, not in docker-compose
```

> Note: some per-service `README.md` files were copy-pasted between the
> `-2`/`-oauth` variants and may reference the wrong port/client — trust
> each service's `.env.example` and `docker-compose.yml` over its README
> for exact ports and client IDs.
