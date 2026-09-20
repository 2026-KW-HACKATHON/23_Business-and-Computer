# 0003. Client routing and social-login token handling

## Status

Accepted

## Context

The first product flow is login. The backend uses Spring Security OAuth2: it
starts a provider flow at `/oauth2/authorization/<provider>`, and on success
sets an **HTTP-only** JWT cookie and redirects the browser to
`http://localhost:5173/cookie`. HTTP-only cookies cannot be read from JS, but
the rest of the app expects the JWT pair (access/refresh) in memory/storage the
way the normal (body-based) login returns them.

This needs (1) client-side routing for the login, cookie, and home routes, and
(2) a way to turn the cookie into a body token pair.

## Decision

- Add `react-router-dom` and route in `src/App.tsx`, wrapped by `BrowserRouter`
  in `src/main.tsx`. Routes: home (/), /login, /cookie, and a catch-all redirect
  to home.
- Social login is a full-page redirect to `socialLoginUrl(provider)`.
- On the /cookie route, `CookiePage` calls `POST /jwt/exchange` with
  `credentials: "include"` so the backend reads its HTTP-only cookie and returns
  `{ accessToken, refreshToken }` in the body; tokens are then stored and the
  user is sent home.
- All backend access goes through `src/api/client.ts` (`apiFetch`) and the auth
  feature's `src/features/auth/api`; the base URL comes from
  `VITE_BACKEND_API_BASE_URL`. Tokens live behind `src/features/auth` token
  storage (currently `localStorage`).

## Rationale

- Matches the backend's chosen cookie→body handoff without exposing the app to
  HTTP-only cookie limitations.
- Centralizing network + token access keeps the directory-boundary rules
  (`docs/conventions/directory-structure.md`): pages never `fetch` directly.
- `react-router-dom` is the standard router and supports React 19.

## Alternatives Considered

- Read the JWT cookie in JS: impossible when it is HTTP-only (the intended
  server config).
- Hand-rolled `pathname` routing to avoid a dependency: rejected; routing needs
  will grow and the router is the conventional, well-supported choice.

## Agent Guidance

- Do not call `fetch` from pages/components; add a function to
  `src/features/auth/api` or `src/api/client.ts`.
- `localStorage` token storage is a known tradeoff (XSS-exposed). If the token
  strategy changes (e.g. in-memory + silent refresh), supersede this record.
- Keep the /cookie route path in sync with the backend success-redirect URL.
