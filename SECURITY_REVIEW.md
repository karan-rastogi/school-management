# Security Review

Scope: the whole repository (`backend/` Spring Boot API, `frontend/` React SPA, migrations,
configuration). No deployment was performed. This document records what was checked, what was
changed, and the residual risks that remain.

Everything below is enforced **server-side**; the SPA's checks are never the gate.

## How to run the verification

```bash
cd backend && mvn test            # 32 test classes, incl. the new security/audit/rate-limit tests
cd frontend && npm run typecheck
```

---

## 1. Authorization on every endpoint

Method security is enabled (`@EnableMethodSecurity`). Every controller carries a class-level
`@PreAuthorize` (with one method-level override), and the filter chain now uses an explicit
allow-list with **`anyRequest().denyAll()`** as the default — so a new endpoint is unreachable until
it is deliberately listed *and* given a role check. Previously the chain ended in
`anyRequest().authenticated()`, which let any signed-in user reach any un-annotated route.

### Endpoint → required role

| Method | Path | Required role / access | Enforced by |
| --- | --- | --- | --- |
| GET | `/api/health` | public | filter chain permitAll |
| GET | `/actuator/health`, `/actuator/info` | public | filter chain permitAll (only these two are exposed) |
| GET | `/oauth2/authorization/google` | public (starts Google sign-in) | OAuth2 chain (order 1) |
| GET | `/login/oauth2/code/google` | public (provider callback) | OAuth2 chain (order 1) |
| POST | `/auth/otp/request` | public | filter chain permitAll |
| POST | `/auth/otp/verify` | public | filter chain permitAll |
| POST | `/auth/refresh` | public (needs the refresh cookie) | filter chain permitAll |
| POST | `/auth/logout` | public (clears the session) | filter chain permitAll |
| GET | `/notices/attachments/{id}` | public (valid HMAC signed URL) | filter chain permitAll + signature check |
| GET | `/me` | authenticated (any role) | filter chain `.authenticated()` |
| POST | `/me/active-role` | authenticated (only roles the user holds) | `AuthService.switchActiveRole` |
| GET | `/admin/users`, `/admin/users/options`, `/admin/users/{id}` | ADMIN | `@PreAuthorize("hasRole('ADMIN')")` |
| POST/PUT/DELETE | `/admin/users`, `/admin/users/{id}`, `/admin/users/{id}/roles` | ADMIN | class `@PreAuthorize` |
| GET/POST/PUT/DELETE | `/admin/students/**` (incl. `/{id}/qr-token`, `/{id}/enrollments/**`) | ADMIN | class `@PreAuthorize` |
| POST | `/admin/students/import` | ADMIN | class `@PreAuthorize` |
| GET | `/admin/students/{id}/parents`, `/admin/parents/{id}/students` | ADMIN | class `@PreAuthorize` |
| POST/DELETE | `/admin/parent-student-links`, `/admin/parent-student-links/{parent}/{student}` | ADMIN | class `@PreAuthorize` |
| GET/POST/PUT/DELETE | `/admin/academic-years/**` | ADMIN | class `@PreAuthorize` |
| GET/POST/PUT/DELETE | `/admin/classes/**` | ADMIN | class `@PreAuthorize` |
| GET/POST/PUT/DELETE | `/admin/teacher-assignments/**` | ADMIN | class `@PreAuthorize` |
| GET/POST/DELETE | `/admin/classes/{id}/substitute-permissions`, `/admin/substitute-permissions/{id}` | ADMIN | class `@PreAuthorize` |
| GET/PUT/DELETE | `/admin/calendar/**` | ADMIN | class `@PreAuthorize` |
| GET | `/admin/id-cards/students/{id}`, `/admin/id-cards/classes/{id}` | ADMIN | class `@PreAuthorize` |
| GET | `/attendance/classes`, `/classes/{classId}/attendance` | ADMIN, TEACHER | class `@PreAuthorize` + service ownership |
| PUT | `/classes/{classId}/attendance` | ADMIN, TEACHER | class `@PreAuthorize` + service ownership |
| POST | `/attendance/scan` | ADMIN, TEACHER | class `@PreAuthorize` + service ownership |
| GET | `/attendance/queries` | ADMIN, TEACHER | class `@PreAuthorize` + service scoping |
| POST | `/attendance/queries/{id}/resolve` | ADMIN, TEACHER (class teacher of that student) | class `@PreAuthorize` + `requireCanResolve` |
| GET | `/notices`, `/notices/assignable-classes` | ADMIN, TEACHER | class `@PreAuthorize` + service scoping |
| POST | `/notices` | ADMIN (GLOBAL) / TEACHER (own class only) | class `@PreAuthorize` + `NoticeService.create` |
| PUT | `/notices/{id}` | sender or ADMIN | class `@PreAuthorize` + `requireCanManage` |
| DELETE | `/notices/{id}` | sender or ADMIN (soft delete) | class `@PreAuthorize` + `requireCanManage` |
| POST/DELETE | `/notices/{id}/attachment` | sender or ADMIN | class `@PreAuthorize` + `requireCanManage` |
| POST | `/notices/{id}/holiday` | ADMIN | method `@PreAuthorize("hasRole('ADMIN')")` |
| GET | `/parent/children` | PARENT (own children) | class `@PreAuthorize` + `ParentStudentLinkService` |
| GET | `/parent/children/{studentId}/attendance` | PARENT (own child only) | class `@PreAuthorize` + `ParentAccessService.requireChild` |
| POST | `/parent/children/{studentId}/attendance-queries` | PARENT (own child only) | class `@PreAuthorize` + `ParentAccessService.requireChild` |
| GET | `/parent/notices` | PARENT (feed scoped to own children) | class `@PreAuthorize` + `NoticeService.parentFeed` |
| — | everything else | **denied** | `anyRequest().denyAll()` |

**Change:** `backend/src/main/java/com/school/config/SecurityConfig.java` — the `apiFilterChain`
now lists every reachable path and ends in `.anyRequest().denyAll()`. No un-annotated endpoint was
found; the allow-list is defence-in-depth so this can't regress silently.

## 2. IDOR / broken object-level authorization

Ownership is verified in the **service layer**, not only by role:

- **Parent → child.** `ParentAccessService.requireChild(parentUserId, studentId)` checks
  `parent_student_links` before any child data is returned, and is called by both the attendance and
  the attendance-query endpoints. The child list and the parent notice feed are derived from the
  links table (`listForParent`, `parentFeed`), so a parent can only ever see their own children.
- **Teacher → class.** `AttendanceService.requireCanMark` allows only an active *class-teacher*
  assignment or an active *substitute* permission for that class (date-scoped); `AttendanceService`
  also rejects entries for students not enrolled in the class on that date.
  `AttendanceQueryService` scopes the teacher's query list to their class-teacher students and
  `requireCanResolve` re-checks the student's current class. `NoticeService` derives the teacher's
  visible/assignable classes from their active assignments.
- **Notice edit/delete.** `requireCanManage` allows only the original sender or an admin, and a
  teacher can only manage a `CLASS` notice (never a `GLOBAL` one).

No IDOR hole was found; the existing ownership checks were verified and are covered by the
`ParentAttendanceApiIntegrationTest`, `AttendanceApiIntegrationTest` and `NoticeApiIntegrationTest`
403 assertions, which all still pass. The default-deny change in §1 makes a future un-scoped
endpoint unreachable rather than merely unauthorised.

## 3. Rate limiting

| Endpoint | Limit | Keyed by | Where |
| --- | --- | --- | --- |
| `/auth/otp/request` | `OTP_RATE_PHONE_REQUESTS` (3) | normalised phone | `RateLimiter` in `OtpService` |
| `/auth/otp/request` | `OTP_RATE_IP_REQUESTS` (10) | client address | `RateLimiter` in `OtpService` |
| `/auth/otp/verify` | `OTP_RATE_PHONE_VERIFIES` (10) | normalised phone | `RateLimiter` in `OtpService` |
| `/auth/otp/verify` | `OTP_RATE_IP_VERIFIES` (30) | client address | `RateLimiter` in `OtpService` |
| `/oauth2/authorization/**` (login) | `AUTH_RATE_OAUTH_STARTS_PER_IP` (30) | client address | **new** `AuthRateLimitFilter` |

**Change:** the OTP limiter was generalised to `com.school.common.ratelimit.RateLimiter`, and a new
`AuthRateLimitFilter` (registered for every request, scoped to the sign-in entry point) adds the
missing per-address limit on the OAuth login start. Exceeding a limit returns `429` with code
`RATE_LIMITED`; the filter renders the same JSON error shape as the controller advice.

## 4. Input validation & file uploads

- Every request DTO is validated (`@NotBlank`, `@Size`, `@Email`, `@Pattern`, `@NotNull`, `@Past`/
  `@PastOrPresent`, `@Min`, `@NotEmpty`) and controllers use `@Valid`; `GlobalExceptionHandler`
  turns failures into `400 VALIDATION_ERROR` with per-field messages. Enums are constrained by type.
- **Change:** `BulkAttendanceRequest.entries` gained `@Size(max = 2000)` so a single request cannot
  carry an unbounded roster.
- **Change:** `NoticeAttachmentStore` now checks the file's leading bytes against the declared
  content type (PDF, PNG, JPEG, WebP signatures) **in addition to** the existing size limit
  (`NOTICE_MAX_ATTACHMENT_BYTES`, 10 MB) and content-type allow-list
  (`NOTICE_ALLOWED_CONTENT_TYPES`). A file renamed to `.pdf` can no longer smuggle past the
  allow-list.
- `PhoneNumbers.normalize` canonicalises and validates phone input; paging is clamped
  (`PageRequests`, max size 100); CSV import is capped at 5 MB and each row's fields are validated.

## 5. Security headers, cookies and CORS

**Change:** `SecurityConfig` now configures response headers for both filter chains:

- `Strict-Transport-Security: max-age=31536000; includeSubDomains; preload` (sent on secure
  requests only, as required by browsers),
- `X-Content-Type-Options: nosniff`,
- `X-Frame-Options: DENY`,
- `Referrer-Policy: no-referrer`,
- `Content-Security-Policy: default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'`,
- `Permissions-Policy: geolocation=(), microphone=(), camera=()`.

**Cookies** (unchanged, verified): `access_token` and `refresh_token` are `HttpOnly`, `Secure`
(`AUTH_COOKIE_SECURE`, default true) and `SameSite=Lax` (`AUTH_COOKIE_SAME_SITE`); they carry a JWT
with no personal data / an opaque refresh token stored only as a SHA-256 hash.

**CORS:** limited to the configured `CORS_ALLOWED_ORIGINS` with `allow-credentials=true`; an
unknown origin is rejected (`HealthControllerIntegrationTest.corsRejectsUnknownOrigin`).

## 6. Audit logging

A new `audit_log` table (`V12__audit_log.sql`), entity, repository and `AuditService` record the
acting user id (from the security context), a machine action code, the entity type/id and an
optional short, PII-free detail. Wired in:

| Action code | Source |
| --- | --- |
| attendance edits | `attendance_audit_log` (pre-existing; who/when/old/new) |
| `USER_ROLES_CHANGED` | `UserAdminService.replaceRoles` (records `from=`/`to=` roles) |
| `USER_CREATED`, `USER_UPDATED`, `USER_DELETED` | `UserAdminService` |
| `QR_TOKEN_REGENERATED` | `StudentService.regenerateQrToken` |
| `NOTICE_CREATED`, `NOTICE_UPDATED`, `NOTICE_DELETED`, `NOTICE_ATTACHMENT_ADDED/REMOVED`, `NOTICE_HOLIDAY_CREATED` | `NoticeService` |
| `STUDENT_CREATED/UPDATED`, `ENROLLMENT_CREATED/REMOVED`, `STUDENT_IMPORT_COMMITTED` | `StudentService`, `StudentImportService` |
| `TEACHER_ASSIGNMENT_CREATED/UPDATED/DELETED` | `TeacherAssignmentService` |
| `SUBSTITUTE_PERMISSION_CREATED/DELETED` | `SubstitutePermissionService` |
| `ACADEMIC_YEAR_*`, `CLASS_*`, `CALENDAR_MARKED/CLEARED` | `AcademicYearService`, `SchoolClassService`, `SchoolCalendarService` |
| `PARENT_LINK_CREATED/REMOVED` | `ParentStudentLinkService` |

Audit rows are written in the same transaction as the action, so a successful mutation always has
its audit row. `AuditLogIntegrationTest` asserts role changes and QR-token regeneration are recorded
with the correct actor and entity.

## 7. No PII in logs, error messages or URLs

- **Change:** `ConsoleOtpSender` no longer writes the OTP code to the log. The phone is masked
  (`****3210`) and only the fact that a code was sent is logged. The plaintext code is written only
  when `app.auth.otp.console-expose-code=true` is set explicitly (documented as development-only).
- Verified: the JWT carries no email/phone/name; `AuthenticatedUser.toString()` prints only the id;
  logs record user ids, never names/emails/phones; `OAuth2LoginSuccessHandler` redirects with an
  opaque error code, never the address; `ApiError.path` is the request URI (no query string, so no
  data in URLs); `GlobalExceptionHandler` logs method + path only and returns a generic message for
  unexpected errors; the student-photo resolver never fetches remote URLs (no SSRF) and the ID-card
  QR encodes only the opaque token.

---

## Residual risks / not changed

These were reviewed and deliberately left; each needs an operator decision.

1. **Signed attachment URLs carry a token in the URL** (`/notices/attachments/{id}?expires=&signature=`).
   This is the intended grant for a public download; it is HMAC-SHA256 signed, short-lived
   (`NOTICE_SIGNED_URL_TTL`, default 15 min) and contains no personal data. Switching to an
   authenticated, non-URL scheme is a larger redesign.
2. **Rate limiting is per-instance and in-memory.** A multi-instance deployment should back
   `RateLimiter` with a shared store (e.g. Redis). The client address is `getRemoteAddr()`; behind a
   reverse proxy, configure the proxy / `ForwardedHeaderFilter` so the real client address is seen.
3. **Upload validation is size + allow-list + magic-byte prefix**, not deep content inspection /
   antivirus. Add an AV scan step if untrusted documents are a concern.
4. **CSRF** relies on `SameSite=Lax` cookies plus the strict CORS allow-list (CSRF tokens are
   disabled). Revisit if the SPA is ever served from a different site.
5. **Development defaults for secrets** (`JWT_SECRET`, `NOTICE_SIGNING_SECRET`) are insecure
   placeholders; production must override them via environment variables (the build fails fast if
   the JWT secret is shorter than 32 bytes).
6. **Student import report** echoes admin-supplied parent name/phone back to the same admin — a
   report of their own upload, not a cross-user disclosure.
