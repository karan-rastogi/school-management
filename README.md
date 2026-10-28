# School Management System

Management platform for a **single school** (no multi-tenancy). Administrators,
teachers and parents sign in; students are records only and never log in.

Monorepo:

```
backend/              Spring Boot 3 API (Java 21, Maven)
frontend/             React SPA (Vite, TypeScript, Tailwind CSS)
docker-compose.yml    Local PostgreSQL
.env.example          Template for environment variables
```

## Prerequisites

| Tool       | Version                          |
| ---------- | -------------------------------- |
| JDK        | 21                               |
| Maven      | 3.9+                             |
| Node.js    | 20.19+ or 22.12+ (Vite 8)        |
| Docker     | any recent version, with Compose |

## Configuration

Secrets and environment-specific values are **never** committed. Copy the
template and adjust:

```bash
cp .env.example .env                 # repository root
cp frontend/.env.example frontend/.env
```

The backend loads the root `.env` automatically (`spring.config.import`), so
`mvn spring-boot:run` picks up your local values. In deployed environments,
set the same names as real environment variables — OS environment variables
take precedence over the `.env` file.

| Variable                          | Default                                  | Used by        |
| --------------------------------- | ---------------------------------------- | -------------- |
| `POSTGRES_DB`                     | `school`                                 | docker compose |
| `POSTGRES_USER`                   | `school`                                 | docker compose |
| `POSTGRES_PASSWORD`               | `school`                                 | docker compose |
| `POSTGRES_PORT`                   | `5433`                                   | docker compose |
| `SERVER_PORT`                     | `8080`                                   | backend        |
| `DB_URL`                          | `jdbc:postgresql://localhost:5433/school`| backend        |
| `DB_USERNAME` / `DB_PASSWORD`     | `school`                                 | backend        |
| `DB_POOL_MAX_SIZE` / `DB_POOL_MIN_IDLE` | `10` / `2`                         | backend        |
| `APP_TIMEZONE`                    | `Asia/Kolkata`                           | backend        |
| `CORS_ALLOWED_ORIGINS`            | `http://localhost:5173`                  | backend        |
| `VITE_API_BASE_URL`               | `http://localhost:8080`                  | frontend       |

> The default host port for PostgreSQL is **5433**, because `5432` is commonly
> already occupied by a local PostgreSQL installation.

## Running locally

### 1. PostgreSQL

```bash
docker compose up -d postgres
docker compose ps            # wait for "healthy"
```

### 2. Backend

```bash
cd backend
mvn spring-boot:run
```

Serves <http://localhost:8080>. Flyway applies every migration in
`backend/src/main/resources/db/migration` on startup.

### 3. Frontend

```bash
cd frontend
npm install
npm run dev
```

Serves <http://localhost:5173>. The dev server talks to the backend directly
over CORS, so `CORS_ALLOWED_ORIGINS` must contain the dev-server origin.

> **Note:** if `NODE_ENV=production` is exported in your shell, `npm install`
> silently skips devDependencies (Vite, TypeScript, Tailwind). Use
> `npm install --include=dev` in that case.

### Production build

```bash
cd backend  && mvn clean package      # target/school-management-backend-0.0.1-SNAPSHOT.jar
cd frontend && npm run build          # dist/
```

## Endpoints

| Method | Path                          | Access                          |
| ------ | ----------------------------- | ------------------------------- |
| `GET`  | `/api/health`                 | public                          |
| `GET`  | `/actuator/health`, `/actuator/info` | public                   |
| `GET`  | `/oauth2/authorization/google` | public (starts the Google flow) |
| `POST` | `/auth/otp/request`           | public                          |
| `POST` | `/auth/otp/verify`            | public                          |
| `POST` | `/auth/refresh`               | public (needs the refresh cookie) |
| `POST` | `/auth/logout`                | public (clears the session)     |
| `GET`  | `/me`                         | authenticated                   |
| `POST` | `/me/active-role`             | authenticated                   |
| `GET`  | `/admin/users`                | `ADMIN` only                    |
| `GET`  | `/parent/children`            | `PARENT` only (own children)    |
| `GET`  | `/parent/children/{id}/attendance` | `PARENT` only (own children) |
| `POST` | `/parent/children/{id}/attendance-queries` | `PARENT` only (own children) |
| `GET`  | `/attendance/queries`         | `ADMIN` / `TEACHER`             |
| `POST` | `/attendance/queries/{id}/resolve` | `ADMIN` / `TEACHER`        |
| `GET`  | `/notices`, `/notices/assignable-classes` | `ADMIN` / `TEACHER`   |
| `POST` | `/notices`                    | `ADMIN` (global) / `TEACHER` (own class) |
| `PUT`  | `/notices/{id}`               | sender or `ADMIN`               |
| `DELETE` | `/notices/{id}`             | sender or `ADMIN` (soft delete) |
| `POST` | `/notices/{id}/attachment`    | sender or `ADMIN`               |
| `DELETE` | `/notices/{id}/attachment`  | sender or `ADMIN`               |
| `POST` | `/notices/{id}/holiday`       | `ADMIN` only                    |
| `GET`  | `/notices/attachments/{id}`   | public (valid signed URL)       |
| `GET`  | `/parent/notices`             | `PARENT` only                   |
| —      | everything else               | authenticated                   |

```bash
curl http://localhost:8080/api/health
```

```json
{
  "status": "UP",
  "application": "school-management-backend",
  "timeZone": "Asia/Kolkata",
  "timestamp": "2026-10-26T23:58:20.7459126+05:30"
}
```

## Authentication

### Signing in

**Google.** `GET /oauth2/authorization/google` runs the OAuth2 authorization-code
flow. On success the handler looks the address up in `users`:

- no such row → rejected, redirected to `/login?error=not_registered`
- row exists but `active = false` → `/login?error=account_disabled`
- otherwise → session cookies are set and the browser lands on `/auth/callback`

There is **no auto-signup**: an unknown address never creates a user. The redirect
only ever carries an opaque code — never the address.

**Phone OTP.** `POST /auth/otp/request` and `POST /auth/otp/verify`.

```bash
curl -i -X POST http://localhost:8080/auth/otp/request \
  -H 'Content-Type: application/json' -d '{"phone":"+919876543210"}'

curl -i -c cookies.txt -X POST http://localhost:8080/auth/otp/verify \
  -H 'Content-Type: application/json' -d '{"phone":"+919876543210","code":"123456"}'
```

OTP rules:

- Only a hash of the code is stored (BCrypt); the plaintext is never persisted.
- Codes expire after `app.auth.otp.ttl` (5 minutes) and are single-use.
- At most `app.auth.otp.max-attempts` (5) wrong guesses per code; the code is
  then refused even if the right value is finally supplied.
- Requests are rate limited **per phone** and **per client address**; verifications
  are too. Exceeding a limit returns `429` with code `RATE_LIMITED`.
- A code is only issued for a phone that belongs to an **active, pre-registered**
  user. The response is the same either way, so the endpoint cannot be used to
  discover which numbers are on file.

### Sessions

Both sign-in paths end the same way: an **access JWT** and an **opaque refresh
token** are placed in cookies.

| Cookie          | Contents                | Path   | Lifetime             |
| --------------- | ----------------------- | ------ | -------------------- |
| `access_token`  | HS256 JWT (`sub` = user id, `act` = active role) | `/` | `app.auth.jwt.access-token-ttl` (15 min) |
| `refresh_token` | 256-bit random, stored as a SHA-256 hash in `refresh_tokens` | `/auth` | `app.auth.jwt.refresh-token-ttl` (30 days) |

Both cookies are `HttpOnly`, `Secure` and `SameSite=Lax`. `Secure` still works on
`http://localhost`, which browsers treat as a trustworthy origin.

`POST /auth/refresh` rotates: the presented refresh token is revoked and a new one
is issued. Presenting an already-revoked token is treated as theft and revokes
every refresh token for that user. `POST /auth/logout` revokes the refresh token
and clears both cookies.

The JWT deliberately carries **no personal data** — no email, no phone, no name.
Authorities are loaded from `users` / `user_roles` on every request, so deactivating
an account or removing a role takes effect immediately rather than when the token
expires.

> **CSRF:** the session is cookie-based, so cross-site state-changing requests are
> blocked by `SameSite=Lax` (they never carry the cookie) plus the strict CORS
> origin allow-list. CSRF tokens are disabled on that basis; if you ever serve the
> SPA from a different site, add a double-submit token.

### Roles and the role switcher

A user may hold several roles. `GET /me` returns `roles` and the `activeRole`.
`POST /me/active-role` switches it — only to a role the user actually holds — and
re-issues the access cookie. The choice is remembered on the refresh-token row, so
it survives `POST /auth/refresh`; pass null (never choose) and it falls back to the
first role the account holds. All of a user's roles stay in `getAuthorities()`, so
switching changes which surface the UI shows, never what the server permits: an
admin who switches to "acting as TEACHER" can still open admin pages, because they
really are an admin.

Method security is enabled (`@EnableMethodSecurity`); `GET /admin/users` is guarded
with `@PreAuthorize("hasRole('ADMIN')")` and returns `403 FORBIDDEN` for anyone else.

### Creating the first user

There is no admin UI yet, so bootstrap the first account with SQL:

```sql
INSERT INTO users (email, phone, name, active)
VALUES ('head@school.test', '+919876543210', 'Head Teacher', TRUE)
RETURNING id;

INSERT INTO user_roles (user_id, role) VALUES (<id>, 'ADMIN');
```

With `OTP_SENDER=console` the code is printed to the backend log:

```
OTP (dev console sender) for ****3210 is 483920 and is valid for 5 minutes.
```

The phone is masked in logs; set `OTP_SENDER=sms` to route through an SMS provider
(the bundled `SmsOtpSender` is a stub and returns `501 NOT_IMPLEMENTED` until one
is wired up).

## Error format

Every error response — including `401` and `403` raised by Spring Security —
uses the same body:

```json
{
  "timestamp": "2026-10-26T18:26:16.563662100Z",
  "status": 405,
  "error": "Method Not Allowed",
  "code": "METHOD_NOT_ALLOWED",
  "message": "HTTP method is not supported for this resource",
  "path": "/api/health",
  "fieldErrors": []
}
```

`fieldErrors` is populated for validation failures:

```json
{
  "status": 400,
  "error": "Bad Request",
  "code": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "fieldErrors": [{ "field": "username", "message": "must not be blank" }]
}
```

Codes: `VALIDATION_ERROR`, `MALFORMED_REQUEST`, `NOT_FOUND`,
`METHOD_NOT_ALLOWED`, `UNAUTHENTICATED`, `FORBIDDEN`, `CONFLICT`,
`INTERNAL_ERROR`. Internal exception details are never returned to clients;
they are logged server-side.

Business code raises `ApiException` (status + code + message) and the
`@RestControllerAdvice` renders it, so there is one error shape everywhere.

## Database schema

The schema is owned by Flyway; JPA entities are validated against it on startup
(`spring.jpa.hibernate.ddl-auto: validate`), so migrations and entities can
never silently drift apart.

| Migration                             | Tables                                                              |
| ------------------------------------- | ------------------------------------------------------------------- |
| `V1__users_and_roles.sql`             | `users`, `user_roles`                                                |
| `V2__academic_years_and_classes.sql`  | `academic_years`, `classes`                                          |
| `V3__students_and_guardians.sql`      | `students`, `student_enrollments`, `parent_student_links`            |
| `V4__teacher_assignments_and_calendar.sql` | `teacher_assignments`, `school_calendar`                        |
| `V5__attendance.sql`                  | `attendance`, `attendance_audit_log`, `attendance_queries`           |
| `V6__notices_and_otp_codes.sql`       | `notices`, `otp_codes`                                               |
| `V7__refresh_tokens.sql`              | `refresh_tokens`                                                     |
| `V8__refresh_token_active_role.sql`   | adds `refresh_tokens.active_role`                                    |
| `V9__substitute_permissions.sql`      | `substitute_permissions`                                             |
| `V10__attendance_query_message.sql`   | adds `attendance_queries.message`                                    |
| `V11__notice_attachments.sql`         | adds notice attachment columns                                       |

Conventions:

- Surrogate keys are `BIGINT GENERATED BY DEFAULT AS IDENTITY`; pure link tables
  (`user_roles`, `parent_student_links`) use composite primary keys.
- Enumerations are `VARCHAR` + `CHECK` constraints, mapped with
  `@Enumerated(EnumType.STRING)`.
- Dates (`dob`, `admission_date`, `attendance.date`, `school_calendar.date`, …)
  are SQL `DATE` and map to `java.time.LocalDate`. Event timestamps are
  `TIMESTAMPTZ` and map to `java.time.OffsetDateTime`.
- `students.qr_token` is a `UUID` with `DEFAULT gen_random_uuid()`; the database
  owns the randomness, Hibernate reads it back after insert.
- Entity packages live in `com.school.<feature>.domain`, repositories in
  `com.school.<feature>.repository`.

## Project rules

These are enforced/observed throughout the codebase:

- **Authorization is server-side.** UI checks are never the gate.
- **Attendance is daily** — one record per student per date.
- **Attendance percentage** is `present-equivalent days / counted days`. The window runs
  from `max(admission_date, academic-year start)` to today; a day is counted when it is a
  working day (holidays excluded), attendance was actually taken, and the status is not
  excluded from the denominator. `PRESENT` and `LATE` count as a full day, `HALF_DAY` as
  half, `ABSENT` as nothing, and `EXCUSED` is removed from the denominator entirely. The
  weights and the warning threshold live in one place — `app.attendance` in
  `application.yml`, bound to `AttendancePolicy` — so a school can retune the rules without
  a code change.
- **Dates are school-local (`Asia/Kolkata`)** and stored as SQL `DATE`,
  never as UTC timestamps. A `Clock` bean bound to `app.timezone` is the
  single source of "now" for business logic.
- **Every attendance change** is written to an audit log (who, when, old
  value, new value).
- **No personal data** in URLs, logs or QR codes.
- **Notices** are `GLOBAL` or class-scoped. Admins post global notices; a teacher may only post a
  class notice to a class they are assigned to, and the `sender_label` is frozen from that
  assignment at send time (e.g. `Maths Teacher - Mrs Sharma`). Parents see global notices plus
  class notices for each child's class, labelled with the child's name. Editing stamps `edited_at`
  (shown as an “edited” badge); delete is a soft delete; `expires_at` hides a notice from the
  parent feed. An optional attachment is size- and type-limited and served through a short-lived
  HMAC-signed URL (`app.notices`). Admins can turn a global notice into a calendar `HOLIDAY`.
- Layered code: controller → service → repository, with DTOs and validation.
- Secrets come from environment variables.

## Tests

```bash
cd backend  && mvn test          # unit, MockMvc slice, and PostgreSQL integration tests
cd frontend && npm run typecheck # tsc --noEmit
```

The PostgreSQL integration tests (`AbstractPostgresTest` and its subclasses)
run against a throwaway instance started via Testcontainers when Docker is
available, and fall back to an embedded PostgreSQL otherwise — so they run on a
plain `mvn test` without Docker. They are skipped only if neither can be
started. `PersistenceMappingTest` runs every Flyway migration, lets Hibernate
validate all entities against the result, and asserts the key constraints
(daily attendance uniqueness, the notice scope/class rule, database-generated
QR tokens).

`AuthFlowIntegrationTest` drives the real HTTP surface: OTP login, cookie
attributes, `/me`, refresh rotation and reuse detection, logout, the role
switcher, and the admin-only endpoint. `OtpServiceTest` covers the OTP rules in
isolation (hashing, expiry, the five-attempt ceiling, single use, and the per-phone
and per-client-address rate limits), and `OAuth2LoginSuccessHandlerTest` covers
the "no auto-signup" rejection paths.
