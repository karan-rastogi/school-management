# Environment variables

Every value the project reads, with its purpose, an example and whether it is required. **No real
secrets appear here** — replace every placeholder with your own value.

The backend activates the production profile with `SPRING_PROFILES_ACTIVE=prod`, which loads
`backend/src/main/resources/application-prod.yml`. In that profile a **required** variable has no
default, so the application fails fast at startup if it is missing. Optional variables fall back to
the value shown in the *Example / default* column below (they also have defaults in
`application.yml` for local development).

Durations use Spring's format: ISO-8601 (`PT15M`, `P30D`) or simple (`15m`, `30d`).

Generate secrets with, for example:

```bash
openssl rand -base64 48
```

---

## Backend — required in production

| Variable | Purpose | Example / default | Required |
| --- | --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | Selects the production profile | `prod` | Yes (to run in prod) |
| `DB_URL` | JDBC URL of the PostgreSQL database | `jdbc:postgresql://ep-xxx.aws.neon.tech/school?sslmode=require` | Yes |
| `DB_USERNAME` | Database user | `school_app` | Yes |
| `DB_PASSWORD` | Database password | *(secret — generated)* | Yes |
| `JWT_SECRET` | HMAC key that signs the access tokens. **At least 32 bytes** or startup fails | *(secret — 48+ random chars)* | Yes |
| `NOTICE_SIGNING_SECRET` | HMAC key for notice attachment signed URLs | *(secret — 48+ random chars)* | Yes |
| `GOOGLE_CLIENT_ID` | Google OAuth 2.0 client id | `1234567890-abc.apps.googleusercontent.com` | Yes |
| `GOOGLE_CLIENT_SECRET` | Google OAuth 2.0 client secret | *(secret)* | Yes |
| `CORS_ALLOWED_ORIGINS` | Origin(s) allowed to call the API, comma-separated | `https://school.vercel.app` | Yes |
| `FRONTEND_BASE_URL` | Where the browser is redirected after Google sign-in | `https://school.vercel.app` | Yes |

## Backend — optional (with defaults)

| Variable | Purpose | Example / default | Required |
| --- | --- | --- | --- |
| `PORT` | HTTP port the app listens on (injected by Render/Fly.io) | `8080` | No |
| `SERVER_PORT` | Alternative to `PORT` for local runs | `8080` | No |
| `APP_TIMEZONE` | School-local zone for business dates | `Asia/Kolkata` | No |
| `DB_POOL_MAX_SIZE` | Hikari maximum pool size | `10` | No |
| `DB_POOL_MIN_IDLE` | Hikari minimum idle connections | `2` | No |
| `JWT_ISSUER` | `iss` claim of the access token | `school-management` | No |
| `JWT_ACCESS_TOKEN_TTL` | Access-token lifetime | `PT15M` | No |
| `JWT_REFRESH_TOKEN_TTL` | Refresh-token lifetime | `P30D` | No |
| `AUTH_COOKIE_SECURE` | Sets the `Secure` cookie flag (keep `true` in prod) | `true` | No |
| `AUTH_COOKIE_SAME_SITE` | Cookie `SameSite` policy. Use `None` when the SPA and API are on different sites | `None` (prod) / `Lax` (local) | No |
| `OTP_SENDER` | OTP delivery: `console` (dev) or `sms` (provider stub) | `console` | No |
| `OTP_CONSOLE_EXPOSE_CODE` | Dev only: print the OTP code to the log. Never `true` in prod | `false` | No |
| `OTP_TTL` | How long a code stays valid | `PT5M` | No |
| `OTP_LENGTH` | Number of digits in a code (4–10) | `6` | No |
| `OTP_MAX_ATTEMPTS` | Wrong guesses allowed per code | `5` | No |
| `OTP_RATE_PHONE_REQUESTS` | OTP requests per phone per window | `3` | No |
| `OTP_RATE_PHONE_VERIFIES` | OTP verifications per phone per window | `10` | No |
| `OTP_RATE_IP_REQUESTS` | OTP requests per client address per window | `10` | No |
| `OTP_RATE_IP_VERIFIES` | OTP verifications per client address per window | `30` | No |
| `OTP_RATE_WINDOW` | Window used by the OTP rate limits | `PT15M` | No |
| `AUTH_RATE_OAUTH_STARTS_PER_IP` | Google sign-in starts per client address per window | `30` | No |
| `AUTH_RATE_WINDOW` | Window used by the OAuth rate limit | `PT15M` | No |
| `NOTICE_SIGNED_URL_TTL` | Lifetime of a notice-attachment signed URL | `PT15M` | No |
| `NOTICE_MAX_ATTACHMENT_BYTES` | Max notice attachment size (bytes) | `10485760` (10 MB) | No |
| `NOTICE_ALLOWED_CONTENT_TYPES` | Allowed attachment content types | `application/pdf,image/png,image/jpeg,image/webp,text/plain` | No |
| `NOTICE_STORAGE_DIR` | Directory for stored attachments | `./uploads/notice-attachments` | No |
| `NOTICE_MAX_ATTACHMENT_SIZE` | Servlet multipart max file size | `10MB` | No |
| `NOTICE_MAX_REQUEST_SIZE` | Servlet multipart max request size | `12MB` | No |
| `ID_CARD_SCHOOL_NAME` | School name printed on ID cards | `School` | No |
| `ID_CARD_PHOTO_DIR` | Directory read for student photos | `./uploads/photos` | No |
| `ID_CARD_CARD_WIDTH_MM` / `ID_CARD_CARD_HEIGHT_MM` | Card size in millimetres | `85.6` / `54` | No |
| `ID_CARD_COLUMNS` / `ID_CARD_ROWS` | Grid on the class sheet | `2` / `4` | No |
| `ID_CARD_MARGIN_MM` / `ID_CARD_GUTTER_MM` | Sheet margins and gaps in millimetres | `12` / `8` | No |
| `ATTENDANCE_PRESENT_WEIGHT` | Weight of PRESENT | `1.0` | No |
| `ATTENDANCE_LATE_WEIGHT` | Weight of LATE | `1.0` | No |
| `ATTENDANCE_HALF_DAY_WEIGHT` | Weight of HALF_DAY | `0.5` | No |
| `ATTENDANCE_ABSENT_WEIGHT` | Weight of ABSENT | `0.0` | No |
| `ATTENDANCE_EXCUSED_EXCLUDED` | Remove EXCUSED from the denominator | `true` | No |
| `ATTENDANCE_WARNING_THRESHOLD` | Percentage below which a warning is shown | `75.0` | No |

## Backend — local only (docker compose)

These are used by `docker-compose.yml` for the local PostgreSQL container; they are **not** needed
in production (managed Postgres provides its own).

| Variable | Purpose | Example / default | Required |
| --- | --- | --- | --- |
| `POSTGRES_DB` | Local database name | `school` | No |
| `POSTGRES_USER` | Local database user | `school` | No |
| `POSTGRES_PASSWORD` | Local database password | `school` | No |
| `POSTGRES_PORT` | Host port for the local DB | `5433` | No |

---

## Frontend

Vite inlines `VITE_*` variables **at build time**, so set them in the hosting provider's build
environment (Vercel → Project → Settings → Environment Variables) and redeploy after changing them.
They are not secrets — anything prefixed `VITE_` ends up in the browser bundle.

| Variable | Purpose | Example / default | Required |
| --- | --- | --- | --- |
| `VITE_API_BASE_URL` | Absolute base URL of the backend API | `https://school-api.onrender.com` | Yes (for a build that talks to a real API) |

If `VITE_API_BASE_URL` is unset the frontend falls back to `http://localhost:8080`
(`frontend/src/lib/api.ts`).

---

## Notes

- **Secrets are never committed.** `.env` is git-ignored; `.env.example` holds placeholders only.
  Keep real values in the hosting provider's environment/secret settings.
- `JWT_SECRET` shorter than 32 bytes makes the application refuse to start (HS256 requirement).
- `AUTH_COOKIE_SECURE` and `AUTH_COOKIE_SAME_SITE` together decide whether the session cookie is
  accepted cross-site. `SameSite=None` requires `Secure=true`, which is the case over https.
- Uploaded files (`NOTICE_STORAGE_DIR`, `ID_CARD_PHOTO_DIR`) live on the container filesystem, which
  is ephemeral on most free hosting tiers — see `DEPLOYMENT.md`.
