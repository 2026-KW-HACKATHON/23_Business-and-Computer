# 골목인턴 frontend

React 19, TypeScript, and Vite frontend for 골목인턴. The current screen is the Vite starter UI; product flows and backend API calls have not been added yet. The Spring Boot backend is in `../backend` and has separate instructions in `../backend/AGENTS.md`.

## Local commands

```sh
npm ci
npm run dev
npm run check
```

`npm run check` is the normal completion gate. It checks for tracked local/generated files, runs ESLint, builds with TypeScript and Vite, and checks where each notification type leads (`npm run test:notifications`). GitHub Actions runs it and `npm run check:docs` for frontend pull requests targeting main. There is no automated browser test yet. Changes involving user flows should also be checked in the browser; API changes require a running backend and a contract check against its implementation.

## Deployment

[Frontend CI/CD](../.github/workflows/frontend-cicd.yml) deploys frontend changes
on main in the team repository, after checks pass. It uses Node 22 and AWS OIDC,
uploads the checked build to S3, publishes the entry page, removes obsolete
files with `--delete`, and waits for CloudFront cache invalidation. It can also
be run manually from the GitHub Actions tab with main selected.

Configure repository Actions Variables:

| Variable | Value |
| --- | --- |
| `AWS_REGION` | `ap-northeast-1` |
| `FRONTEND_AWS_ROLE_ARN` | arn:aws:iam::975049927748:role/gakkum-frontend-deploy |
| `FRONTEND_S3_BUCKET` | `gakkum-front-975049927748-ap-northeast-1-an` |
| `CLOUDFRONT_DISTRIBUTION_ID` | `E43UPLVE5MISO` |
| `VITE_BACKEND_API_BASE_URL` | `https://gakkum-api.hubspacekw.com` |

The bucket must contain only frontend build output. Deployment permissions,
cleanup policy, and first-run checks are recorded in
[ADR 0067](docs/decisions/0067-frontend-s3-cloudfront-cicd.md).

See `AGENTS.md` for coding-agent work boundaries and verification expectations.
