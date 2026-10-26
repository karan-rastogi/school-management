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

| Method | Path                 | Access        |
| ------ | -------------------- | ------------- |
| `GET`  | `/api/health`        | public        |
| `GET`  | `/actuator/health`   | public        |
| `GET`  | `/actuator/info`     | public        |
| —      | everything else      | authenticated |

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
- **Dates are school-local (`Asia/Kolkata`)** and stored as SQL `DATE`,
  never as UTC timestamps. A `Clock` bean bound to `app.timezone` is the
  single source of "now" for business logic.
- **Every attendance change** is written to an audit log (who, when, old
  value, new value).
- **No personal data** in URLs, logs or QR codes.
- Layered code: controller → service → repository, with DTOs and validation.
- Secrets come from environment variables.

## Tests

```bash
cd backend  && mvn test          # unit, MockMvc slice, and PostgreSQL integration tests
cd frontend && npm run typecheck # tsc --noEmit
```

`PersistenceMappingTest` starts a throwaway PostgreSQL container via
Testcontainers, runs every Flyway migration, lets Hibernate validate all
entities against the result, and asserts the key constraints (daily attendance
uniqueness, the notice scope/class rule, database-generated QR tokens). It is
skipped automatically when Docker is unavailable.
