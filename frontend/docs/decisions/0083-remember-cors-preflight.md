# 0083. The browser remembers the API's CORS preflight for two hours

## Status

Accepted. The backend answers CORS preflight requests with
`Access-Control-Max-Age: 7200`, so the browser asks the API once per URL in
two hours instead of before nearly every call.

## Context

- The site (`gakkum.hubspacekw.com`) and the API
  (`gakkum-api.hubspacekw.com`) are different origins, and every API call
  sends `Authorization: Bearer …`. The browser therefore sends an `OPTIONS`
  preflight before the real request.
- The CORS configuration did not set a max age, so no
  `Access-Control-Max-Age` header was sent. Chrome then remembers a preflight
  for only 5 seconds, and per URL.
- Measured on the deployed site (student MY): a call after more than 5 idle
  seconds took 0.25–0.38 s, the same call within 5 seconds 0.13–0.16 s. The
  first calls of almost every screen paid this extra round trip.

## Decision

- `SecurityConfig.frontendCorsConfiguration` sets `maxAge` to 7200 seconds,
  the most Chrome accepts. It applies to every path, including the submission
  download path, and only to allowed origins (a rejected origin gets no CORS
  headers, as before).
- Allowed origins, methods and headers do not change.

## Rationale

- One header removes one round trip from the first calls of each screen
  across the whole app, with no frontend change.
- A preflight answer only says which origin, methods and headers are
  allowed; authentication is still checked on every real request.

## Alternatives Considered

- **Serve the API under the site's own domain (e.g. a CloudFront path)**:
  no preflight at all, but it changes the deployment and cookie setup.
- **A shorter max age**: keeps part of the extra round trips for no benefit,
  since the CORS policy rarely changes. When it does, a browser can keep the
  old answer for up to two hours.
