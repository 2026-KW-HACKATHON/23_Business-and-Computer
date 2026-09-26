# 0004. KakaoPay redirect and approval callback

## Status

Accepted

## Context

The backend prepares a payment and returns separate PC and mobile KakaoPay URLs.
KakaoPay redirects the browser to frontend approval, cancel, or fail routes;
only approval includes `pg_token`. The backend keeps the `tid` for that order.

## Decision

- Payment initiation remains a feature API helper until a job payment screen exists.
  The helper sends the authenticated preparation request and redirects the browser
  to the PC or mobile URL selected from the browser user agent.
- **/payments/kakao/approval** reads `orderId` and `pg_token`, then sends an
  authenticated `POST /payments/{orderId}/approve`. The page only shows success
  after the backend returns `PAID`; it removes `pg_token` from the visible URL.
- Cancel and fail callbacks show their outcome without calling approval.
- Refresh or retry can repeat approval. The backend returns the stored result
  for an already paid order. A missing access token requires a new login and
  payment attempt; the callback does not weaken the authenticated API boundary.

## Verification

Run `npm run check` and manually check PC/mobile redirects, all callbacks,
refresh/retry, and missing authentication against a running backend.
