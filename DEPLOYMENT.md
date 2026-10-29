# Manual deployment guide

A hands-on guide to deploy this project **by hand**, using web dashboards only — no cloud CLI is
required for the primary path. The stack:

| Piece | Runs on | Notes |
| --- | --- | --- |
| PostgreSQL | [Neon](https://neon.tech) (or Supabase) | managed Postgres, SSL required |
| Backend API | [Render](https://render.com) (or Fly.io) | built from `backend/Dockerfile` |
| Frontend SPA | [Vercel](https://vercel.com) | built from `frontend/`, `vercel.json` adds SPA rewrites |

```
Browser ──https──► Vercel (React SPA, VITE_API_BASE_URL)
   │                     │  fetch with credentials (cross-site cookie)
   │                     ▼
   └────────────► Render (Spring Boot API, /api, /auth, /oauth2, /admin, /parent)
                         │  JDBC over TLS
                         ▼
                    Neon PostgreSQL (Flyway migrates on startup)
```

Nothing here automates the deploy for you: every step is performed in a provider dashboard. Do not
run deploy commands from this repository.

Environment variable names, purposes and example values are catalogued in
[ENVIRONMENT.md](./ENVIRONMENT.md).

---

## 0. Prerequisites

- The repository pushed to GitHub/GitLab (Render and Vercel deploy from a Git repo).
- Three free accounts: Neon, Render, Vercel. (Google Cloud account for the Google sign-in client.)
- The values you will collect as you go: a database password, a `JWT_SECRET`, a
  `NOTICE_SIGNING_SECRET`, a Google client id/secret, and the final frontend URL.

Generate the two random secrets now and keep them somewhere safe:

```bash
openssl rand -base64 48   # JWT_SECRET
openssl rand -base64 48   # NOTICE_SIGNING_SECRET
```

> **Deploy the backend first**, then the frontend, then come back and set the Google redirect URI and
> CORS to the final frontend URL. You will know the two URLs after steps 1 and 3.

---

## 1. Database — Neon (primary)

1. Sign in at <https://neon.tech> and **Create project**. Pick a region close to where the backend
   will run (e.g. the same continent as Render/Fly).
2. Neon creates a database and a role, and shows a **connection string** like:

   ```
   postgresql://school_owner:AbC123...@ep-cool-name-123456.us-east-2.aws.neon.tech/neondb?sslmode=require
   ```

3. Turn it into a JDBC URL for the backend by prefixing `jdbc:` and keeping `?sslmode=require`:

   ```
   DB_URL=jdbc:postgresql://ep-cool-name-123456.us-east-2.aws.neon.tech/neondb?sslmode=require
   DB_USERNAME=school_owner
   DB_PASSWORD=AbC123...            # the password from the connection string
   ```

   Neon also offers a **pooled** endpoint whose host contains `-pooler` (e.g.
   `ep-cool-name-123456-pooler.us-east-2.aws.neon.tech`). Either works; the direct host is simplest
   for Flyway. If you use the pooled host, keep the same URL shape.

4. Leave the database empty — the backend creates the schema (see step 5).

### Alternative: Supabase

1. <https://supabase.com> → **New project** (choose and save a database password).
2. **Project Settings → Database → Connection string**. Use the **Session pooler** (port `5432`) or
   **Direct connection** and append `?sslmode=require`:

   ```
   DB_URL=jdbc:postgresql://db.<project-ref>.supabase.co:5432/postgres?sslmode=require
   DB_USERNAME=postgres
   DB_PASSWORD=<the database password you set>
   ```

---

## 2. Backend — Render (primary)

1. <https://dashboard.render.com> → **New → Web Service** → connect your repository.
2. Settings:
   - **Root Directory**: `backend`
   - **Runtime**: `Docker` (Render detects `backend/Dockerfile`)
   - **Instance type**: Free (see the free-tier caveats in step 10)
   - **Health Check Path**: `/actuator/health`
3. **Environment** → add these variables (see ENVIRONMENT.md for the full list):

   | Key | Value |
   | --- | --- |
   | `SPRING_PROFILES_ACTIVE` | `prod` |
   | `DB_URL` | your JDBC URL from step 1 |
   | `DB_USERNAME` | your DB user |
   | `DB_PASSWORD` | your DB password |
   | `JWT_SECRET` | the generated secret (≥32 bytes) |
   | `NOTICE_SIGNING_SECRET` | the second generated secret |
   | `GOOGLE_CLIENT_ID` | from step 4 (you can fill this after step 4 and redeploy) |
   | `GOOGLE_CLIENT_SECRET` | from step 4 |
   | `CORS_ALLOWED_ORIGINS` | `https://<your-app>.vercel.app` (set after step 3) |
   | `FRONTEND_BASE_URL` | `https://<your-app>.vercel.app` (set after step 3) |
   | `AUTH_COOKIE_SECURE` | `true` |
   | `AUTH_COOKIE_SAME_SITE` | `None` |

   > Render injects `PORT` automatically; the app reads it
   > (`server.port: ${PORT:...}`), so do not set `PORT` yourself.

4. **Create Web Service**. Render builds the image and starts the container. The first request after
   an idle period is slow on the free tier (see step 10).
5. Backend URL: `https://<service-name>.onrender.com`.

### Alternative: Fly.io

Fly.io has no dashboard-only flow — it needs the `flyctl` CLI (optional path). In `backend/`:

```bash
fly launch --no-deploy          # detects the Dockerfile; set a name and region
fly secrets set SPRING_PROFILES_ACTIVE=prod DB_URL=... DB_USERNAME=... DB_PASSWORD=... \
  JWT_SECRET=... NOTICE_SIGNING_SECRET=... GOOGLE_CLIENT_ID=... GOOGLE_CLIENT_SECRET=... \
  CORS_ALLOWED_ORIGINS=https://<your-app>.vercel.app FRONTEND_BASE_URL=https://<your-app>.vercel.app
fly deploy
```

Set `internal_port = 8080` and `[env] PORT = "8080"` in `fly.toml` (Fly terminates TLS and forwards
`X-Forwarded-Proto`; `server.forward-headers-strategy=framework` makes redirects use `https`).

---

## 3. Frontend — Vercel

1. <https://vercel.com> → **Add New → Project** → import your repository.
2. **Root Directory**: `frontend`. Vercel detects Vite; `frontend/vercel.json` sets the build command,
   the `dist` output directory and the SPA rewrite (all routes fall through to `index.html`).
3. **Environment Variables** → add:

   | Key | Value |
   | --- | --- |
   | `VITE_API_BASE_URL` | `https://<service-name>.onrender.com` (the backend URL from step 2) |

4. **Deploy**. Vercel gives you `https://<project>.vercel.app`.
5. Back in Render, set `CORS_ALLOWED_ORIGINS` and `FRONTEND_BASE_URL` to this exact origin and
   **redeploy** the backend.

> Vite bakes `VITE_API_BASE_URL` in at **build time** — changing it requires a new Vercel deployment.

---

## 4. Google OAuth for production

1. <https://console.cloud.google.com/apis/credentials> → **Create credentials → OAuth client ID** →
   **Web application**.
2. **Authorised redirect URIs** — add the backend callback (registration id `google`):

   ```
   https://<service-name>.onrender.com/login/oauth2/code/google
   ```

   For local development keep `http://localhost:8080/login/oauth2/code/google` as a second URI.
   (Authorised JavaScript origins are not required for this server-side flow, but you may add the
   frontend origin.)
3. Copy the **client id** and **client secret** into the backend's `GOOGLE_CLIENT_ID` /
   `GOOGLE_CLIENT_SECRET` and redeploy.
4. After sign-in Google returns to the backend, which then redirects the browser to
   `FRONTEND_BASE_URL + /auth/callback`. There is **no auto-signup** — the email must already exist
   as an active user (step 6).

---

## 5. First Flyway migration

Nothing manual: with `SPRING_PROFILES_ACTIVE=prod`, `spring.flyway.enabled=true` runs every migration
in `backend/src/main/resources/db/migration` **at startup**, before Hibernate validates the schema
(`ddl-auto=validate`). A bad schema stops the app from serving.

Verify in the Render logs (Flyway logs at INFO):

```
Migrating schema "public" to version "1 - users and roles"
...
Successfully applied N migrations to schema "public"
```

To confirm from the database side, run in the Neon **SQL Editor**:

```sql
select version, description, success from flyway_schema_history order by installed_rank;
select count(*) from users;
```

> Flyway is **forward-only**. Never edit a migration that has already been applied in production —
> add a new `V{n}__...sql` instead.

---

## 6. Create the first admin user

There is no admin UI and no signup, so bootstrap the first account with SQL (Neon SQL Editor):

```sql
INSERT INTO users (email, phone, name, active)
VALUES ('head@yourschool.org', '+919876543210', 'Head Teacher', TRUE)
RETURNING id;

-- use the id returned above
INSERT INTO user_roles (user_id, role) VALUES (<id>, 'ADMIN');
```

- `email` is used by Google sign-in; `phone` is used by OTP login. Fill whichever you will use (at
  least one is needed).
- With `OTP_SENDER=console`, the OTP is written to the Render logs (masked phone). To log in by OTP
  in production, either read the code from the logs once or set a real `OTP_SENDER` provider.
- After signing in as the admin you can add teachers/parents and link them from the admin UI.

---

## 7. Smoke-test checklist

Run through this after every deploy:

- [ ] `GET https://<backend>/api/health` → `200` with `"status":"UP"`.
- [ ] `GET https://<backend>/actuator/health` → `{"status":"UP"}`.
- [ ] `GET https://<backend>/admin/users` without a session → `401` (auth is enforced).
- [ ] From the frontend origin, a preflight `OPTIONS` to the backend returns the CORS headers
      (`Access-Control-Allow-Origin: https://<frontend>`), and a call from any other origin is
      **not** allowed.
- [ ] Load the frontend; it reaches the API (no CORS/network errors in the browser console).
- [ ] Sign in with the admin phone via OTP (code from the logs) — the session cookie is set and `/me`
      returns the admin with role `ADMIN`.
- [ ] Sign in with Google for the admin email — lands on `/auth/callback` signed in.
- [ ] Create a class and a student; mark attendance for today; the roster saves.
- [ ] Link a parent to the student and confirm the parent dashboard shows the child's attendance.
- [ ] Upload a notice attachment and open the signed URL; regenerate a student's QR token.
- [ ] Reload a deep link (e.g. `https://<frontend>/admin/students`) — it serves the SPA (not a 404).

---

## 8. Rollback

**Backend (Render)** — Dashboard → your service → **Deploys** → pick the last good deploy → **Redeploy**
(or “Rollback” where offered). Alternatively deploy a previous Git commit.

**Frontend (Vercel)** — Dashboard → **Deployments** → previous deployment → **⋯ → Promote to
Production** (instant rollback).

**Database (Neon)** — use **Branches / Restore** to reset a branch to an earlier point in time, or
restore into a new branch and repoint `DB_URL`. Supabase exposes scheduled backups +
point-in-time recovery on paid plans.

**Schema** — Flyway cannot “un-apply” a migration. To undo a change, ship a new forward migration
that reverses it; do not delete rows from `flyway_schema_history`.

Always roll the backend forward/backward **first** if a release included a schema change, so the DB
and the running code stay compatible.

---

## 9. Configuration recap (the variables that matter most)

| Where | Variable | Value |
| --- | --- | --- |
| Backend | `SPRING_PROFILES_ACTIVE` | `prod` |
| Backend | `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | from step 1 |
| Backend | `JWT_SECRET` / `NOTICE_SIGNING_SECRET` | generated secrets |
| Backend | `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | from step 4 |
| Backend | `CORS_ALLOWED_ORIGINS` / `FRONTEND_BASE_URL` | the Vercel origin |
| Backend | `AUTH_COOKIE_SECURE` / `AUTH_COOKIE_SAME_SITE` | `true` / `None` |
| Frontend | `VITE_API_BASE_URL` | the Render backend URL |

---

## 10. Free-tier limits and caveats

Free tiers change often — **check each provider's current pricing page** before relying on these.

- **Render free web service**: spins down after a period of inactivity and cold-starts on the next
  request (the first request can take roughly a minute). It also has a monthly instance-hour budget
  and **no persistent disk** on the free plan — files written at runtime are lost on redeploy/restart.
- **Neon free tier**: the compute **autosuspends** when idle and resumes on the next connection
  (small delay on the first query). There are storage and compute-hour limits and a cap on branches.
- **Supabase free tier**: projects are **paused** after a period of inactivity and must be resumed
  from the dashboard; storage/egress are limited.
- **Fly.io**: the small shared machines are usage-billed and can suspend; verify the current
  allowance.
- **Vercel Hobby**: free for personal/non-commercial use, with bandwidth and build limits; commercial
  use may require a paid plan.

Because of the **ephemeral filesystem**, notice attachments and ID-card photos stored under
`./uploads` on Render/Fly are not durable. For real use, attach a persistent disk (paid) or move that
storage to object storage. The **rate limiter is in-memory**, so limits reset on restart and are
per-instance.

---

## 11. Troubleshooting

| Symptom | Likely cause / fix |
| --- | --- |
| App exits immediately at startup | A required variable is missing — the prod profile has no default for `DB_URL`, `DB_PASSWORD`, `JWT_SECRET`, `NOTICE_SIGNING_SECRET`, `GOOGLE_CLIENT_ID/SECRET`, `CORS_ALLOWED_ORIGINS`, `FRONTEND_BASE_URL`. Check the Render logs. |
| `JWT_SECRET must be at least 32 bytes` | Generate a longer value (`openssl rand -base64 48`). |
| CORS error in the browser | `CORS_ALLOWED_ORIGINS` must be the **exact** frontend origin (scheme + host, no trailing slash). |
| Signed out on every request / cookie not sent | SPA and API are on different sites: set `AUTH_COOKIE_SAME_SITE=None` and `AUTH_COOKIE_SECURE=true`. |
| Redirect after Google sign-in goes to localhost | `FRONTEND_BASE_URL` still points at `http://localhost:5173`. |
| `redirect_uri_mismatch` from Google | The authorised redirect URI must be exactly `https://<backend>/login/oauth2/code/google`. |
| `relation "..." does not exist` | Flyway did not run — confirm `SPRING_PROFILES_ACTIVE=prod` and check the logs for migration errors. |
| DB connection refused / SSL error | Include `?sslmode=require` in `DB_URL`; use the host from the provider's connection string. |
| Deep link returns 404 on refresh | `frontend/vercel.json` (SPA rewrite) is missing or the Vercel Root Directory is not `frontend`. |
