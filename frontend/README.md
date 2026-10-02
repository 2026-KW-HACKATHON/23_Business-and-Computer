# 가꿈 frontend

React 19, TypeScript, and Vite frontend for 가꿈. The current screen is the Vite starter UI; product flows and backend API calls have not been added yet. The Spring Boot backend is in `../backend` and has separate instructions in `../backend/AGENTS.md`.

## Local commands

```sh
npm ci
npm run dev
npm run check
```

`npm run check` is the normal completion gate. It checks for tracked local/generated files, runs ESLint, and builds with TypeScript and Vite. There is no automated browser test or frontend CI workflow yet. Changes involving user flows should also be checked in the browser; API changes require a running backend and a contract check against its implementation.

See `AGENTS.md` for coding-agent work boundaries and verification expectations.
