# 0067. Deploy the frontend through GitHub Actions to S3 and CloudFront

## Status

Accepted.

## Context

The frontend was uploaded to S3 manually. The user requested automated
deployment from the team repository's main branch and removal of files from
previous deployments. The backend already uses GitHub Actions with AWS OIDC.

## Decision

- Use the repository-root workflow
  [frontend-cicd.yml](../../../.github/workflows/frontend-cicd.yml).
- On pull requests targeting main, run frontend checks only. On main pushes
  and manual runs of main in 2026-KW-HACKATHON/23_Business-and-Computer, deploy
  after checks pass. Automatic triggers cover frontend and workflow changes.
- Use Node 22, `npm ci`, `npm run check`, and `npm run check:docs`. The existing
  completion gate builds the app; upload its output as an artifact and deploy
  that same artifact without rebuilding.
- Inject `VITE_BACKEND_API_BASE_URL` before building. Fail when required build
  or deployment variables are absent, or the downloaded entry page is empty.
- Authenticate only the deployment job through OIDC with
  arn:aws:iam::975049927748:role/gakkum-frontend-deploy. Its trust policy uses
  audience `sts.amazonaws.com` and the exact main-branch subject:

  ```text
  repo:2026-KW-HACKATHON@329474081/23_Business-and-Computer@1372523659:ref:refs/heads/main
  ```

  Do not add a GitHub Environment without updating this subject policy.
- Deploy to bucket `gakkum-front-975049927748-ap-northeast-1-an` in
  `ap-northeast-1` and refresh CloudFront distribution `E43UPLVE5MISO`.
- Upload hashed assets with a one-year immutable cache header, other static
  files with a 60-second cache header, then publish the entry page with
  revalidation required. Finally sync the complete build with `--delete` to
  remove obsolete files. The bucket is dedicated to frontend build output.
- Invalidate all CloudFront paths and wait for completion before reporting
  deployment success.
- Cancel obsolete PR checks, but serialize main workflow runs without
  cancelling an active deployment.

## Rationale

Reuse the repository's existing AWS authentication and CI conventions. Separate
checks from AWS permissions and deploy the checked artifact. Publishing HTML
after the new assets reduces the chance of references to files not yet uploaded.
Removing obsolete files follows the user's explicit deployment policy.

## Alternatives Considered

- Retaining previous hashed assets: rejected by the user. A browser still using
  an older build can fail when loading an asset deleted by the next deployment.
- Long-lived AWS access keys: unnecessary because OIDC is already configured.
- Building again in the deployment job: unnecessary; transfer the checked build.

## Agent Guidance

- Repository variables are `AWS_REGION`, `FRONTEND_AWS_ROLE_ARN`,
  `FRONTEND_S3_BUCKET`, `CLOUDFRONT_DISTRIBUTION_ID`, and
  `VITE_BACKEND_API_BASE_URL`; the backend URL is public build configuration.
- IAM requires bucket listing, object reading/writing/deletion, and
  `cloudfront:CreateInvalidation` / `cloudfront:GetInvalidation` on the target
  distribution. Keep permissions scoped to these resources.
- Keep deletion after publishing the entry page and after validating the build.
  Never empty the bucket before uploading the new build. There is no automatic
  rollback if S3 synchronization or invalidation fails.
- Existing CloudFront HTTPS, cache policies, SPA fallback, and backend allowed
  origins are infrastructure prerequisites; this workflow does not modify them.
- Check the first GitHub run for successful OIDC authentication, uploads,
  obsolete-file deletion, and invalidation. Manually check direct navigation
  and refresh on /login and /cookie and verify social login on the deployed site.
