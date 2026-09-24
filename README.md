# Lord Sai Share Market Classes

Full-stack platform: the existing static website (HTML/CSS/JS) plus a **Spring Boot + MySQL** backend
that powers course sales (demo or Razorpay), student accounts, a secure video learning portal, admin-managed
success stories, and a master admin dashboard. There are exactly **two application roles: ADMIN and STUDENT**.

---

## 1. Architecture

```
Browser (static site: index.html, courses.html, student-*.html, admin-dashboard.html ...)
   │  fetch() with Bearer JWT            js/api-config.js  →  API base URL
   ▼
Spring Boot REST API  (backend/, port 8080)
   ├── /api/public/**     course + ebook catalogue, site content, success stories, approved reviews (+ review submission)
   ├── /api/auth/**       login, logout, password setup/reset       (no auth for login/reset)
   ├── /api/payments/**   mode / demo-complete / Razorpay order / verify / webhook  (no auth; verified server-side)
   ├── /api/student/**    courses, lessons, video, progress, journal, doubts, ebooks, invoices, exams, certificates (ROLE_STUDENT)
   ├── /api/admin/**      everything incl. ebooks, exams, exam applications/results, certificates, payment gateway & success stories (ROLE_ADMIN)
   └── /api/automation/** office ledgers + email/WhatsApp automation (own students only), purchase reports, invoices, exports (AUTOMATION_ADMIN, ADMIN)
   ▼
MySQL 8/9 (schema managed by Flyway)      Local disk: uploads/{videos,pdfs,images}
```

**Technologies:** Java 21 · Spring Boot 3.5 · Spring Security (stateless JWT) · Spring Data JPA / Hibernate ·
Flyway · MySQL Connector/J · Bean Validation · BCrypt · JJWT · Razorpay Java SDK · Spring Mail + Thymeleaf ·
Maven · JUnit 5 / MockMvc (H2 for tests).

Package layout: `com.lordsai.lsi.{config, controller, service, repository, entity, dto, security, payment, email, exception, util}`.

---

## 2. Prerequisites

| Tool | Version |
|---|---|
| Java JDK | 21 |
| Maven | 3.9+ |
| MySQL Server | 8.0+ (9.x tested) |
| Any static file server for the frontend | e.g. VS Code Live Server (port 5500), `python -m http.server`, nginx |

---

## 3. Database setup (once)

Run as MySQL root (Workbench or CLI):

```bash
mysql -u root -p < backend/db/setup-mysql.sql
```

Edit the password inside that file first (or change it afterwards with `ALTER USER`). Tables are created
automatically by Flyway migrations (`backend/src/main/resources/db/migration/V1..V12`) the first time the backend
starts — do **not** create tables by hand.

---

## 4. Environment variables

Copy the template and fill in real values:

```bash
cd backend
cp .env.example .env
```

| Variable | Purpose |
|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | MySQL connection (user from step 3) |
| `JWT_SECRET` | ≥ 32 random chars. `openssl rand -base64 48` |
| `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET` | Razorpay Dashboard → Settings → API Keys (`rzp_test_*` for testing) |
| `RAZORPAY_WEBHOOK_SECRET` | Razorpay Dashboard → Webhooks (URL: `https://<api-host>/api/payments/webhook`, events: `payment.captured`, `payment.failed`, `refund.processed`) |
| `MAIL_ENABLED` | `false` = emails are written to the log (great for local testing); `true` = send via SMTP |
| `MAIL_HOST/PORT/USERNAME/PASSWORD/FROM` | SMTP account (Gmail: use an App Password) |
| `PUBLIC_BASE_URL` | Where the **static site** is served (used to build links in emails), e.g. `http://localhost:5500` |
| `CORS_ALLOWED_ORIGINS` | Comma-separated origins of the static site. No wildcards. |
| `STORAGE_BASE_PATH` | Folder for uploaded videos/PDFs/images/ebooks/invoices (default `./uploads`) |
| `MAX_EBOOK_SIZE_MB` | Upload limit for ebook PDFs (default 100) |
| `WHATSAPP_PROVIDER`, `WHATSAPP_ACCESS_TOKEN`, `WHATSAPP_PHONE_NUMBER_ID`, `WHATSAPP_BUSINESS_ACCOUNT_ID` | Optional fallback only. WhatsApp is normally configured once in **Main Admin → Settings → WhatsApp Settings** (token encrypted in `whatsapp_config`, verified with Meta before it is enabled); that configuration takes precedence over these variables. Neither set → "WhatsApp is not configured." |
| `COMM_BATCH_SIZE`, `COMM_BATCH_PAUSE_MS` | Bulk communication batching (default 25 messages, 2 s pause) |
| `BOOTSTRAP_ADMIN_EMAIL`, `BOOTSTRAP_ADMIN_PASSWORD` | Creates the first admin on first start (see §7) |

Secrets are never committed: `backend/.env` is git-ignored and `application.yml` only references
`${ENV_VARS}`.

---

## 5. Run the backend

```bash
cd backend
mvn clean compile          # compile
mvn test                   # 207 tests, runs on in-memory H2 — no MySQL needed
mvn spring-boot:run        # starts on http://localhost:8080
```

Loading `.env`: Spring does not read `.env` files by itself. Either export the variables in your shell,
set them in your IDE run configuration, or on Windows PowerShell:

```powershell
Get-Content backend\.env | Where-Object { $_ -match '^\s*[^#]' } | ForEach-Object { $k,$v = $_ -split '=',2; [Environment]::SetEnvironmentVariable($k.Trim(), $v.Trim()) }
cd backend; mvn spring-boot:run
```

Health check: `GET http://localhost:8080/api/health` → `{"success":true,"data":{"status":"UP"}}`

---

## 6. Run the frontend

Serve the project root as static files. With VS Code **Live Server** it will be `http://localhost:5500`.
`js/api-config.js` automatically targets `http://localhost:8080/api` when the page is on localhost;
in production it uses `/api` on the same domain (put the API behind a reverse proxy) or set
`window.LSI_API_BASE_OVERRIDE` before `api-config.js` loads.

Key pages:

| Page | Purpose |
|---|---|
| `courses.html` | Public course page with **Buy Course** → Razorpay checkout |
| `store.html` | Learning store; **Ebooks — Buy Online** section → the same Razorpay checkout (`js/checkout.js`) |
| `student-login.html` | Login with Student ID or email; forgot-password |
| `set-password.html?token=…` | First-time password setup / reset (link comes by email) |
| `student-dashboard.html` | My Courses, My Ebooks (in-portal reader), My Invoices (PDF download), **My Exams** (apply after course completion, scheduled MCQ exam, results), **Certificates** (PDF after passing), handouts, trade journal, doubt desk |
| `learn.html?course=ID` | Video learning page with progress tracking |
| `stories.html` | Public Success Stories (Students / Teachers / Parents filter) — loaded from the API |
| `testimonials.html` | Testimonials + **Add Your Review** (star rating, optional photo); approved reviews load per website |
| `admin-dashboard.html` | Master admin dashboard |

---

## 7. First administrator

There is no admin password in the code or database seed. On first start, if **no ADMIN exists** and both
`BOOTSTRAP_ADMIN_EMAIL` and `BOOTSTRAP_ADMIN_PASSWORD` (≥ 12 chars) are set, the account is created and an
audit entry is written. Log in at `student-login.html` with that email → you are redirected to
`admin-dashboard.html`. **Change the password from Settings and remove the two variables.**

---

## 8. Purchase flow (courses and ebooks)

```
courses.html / store.html  →  Buy  →  Name / Email / Mobile
   → POST /api/payments/create-order      (productType COURSE|EBOOK; price read from DB; Razorpay order; Payment=CREATED)
   → Razorpay Checkout in the browser
   → POST /api/payments/verify            (HMAC-SHA256 signature verified server-side)
        ├─ invalid  → Payment=FAILED, nothing else happens
        └─ valid    → Payment=SUCCESS
                      find-or-create ONE Student (PENDING_SETUP, ID LSI-YYYY-NNNNN) — reused for every later purchase
                      course → Enrollment (unique per student+course) | ebook → EbookEntitlement (unique per student+ebook)
                      Invoice LSI-INV-YYYY-NNNNNN (one per payment) + Lord Sai PDF stored under uploads/invoices
                      email (invoice PDF attached): course/ebook confirmation with the one-time setup link,
                        or "New Purchase Added To Your Existing Student Account" for an existing account
                      every email/WhatsApp attempt is recorded in communication_logs (invoice resend from Invoices)
   → success screen with Student ID, invoice number and "Go to Student Portal"
POST /api/payments/webhook   (Razorpay → server) fulfils the order even if the browser closed; idempotent
                              (no duplicate student, entitlement, invoice or email).
```

Ebook PDFs and invoice PDFs are never public: `/api/student/ebooks/{id}/download` and
`/api/student/invoices/{id}/pdf` need the bearer token, an active STUDENT account and the caller's own
entitlement / invoice (otherwise 403). A purchase email cannot fail a purchase; delivery failures are logged
and can be re-sent (Invoices → Resend Email).

Protections: price cannot be sent by the client; duplicate verify calls return the same result without
creating anything; an email already enrolled cannot start a second checkout; amount mismatch in the webhook
marks the payment FAILED.

---

## 9. Login & single-device sessions

`POST /api/auth/login` accepts `{identifier: <email | student ID>, password}`. On success the server creates a
`user_sessions` row and returns a JWT whose `jti` is that session. Every request re-checks that the session
is still active and that the user's `token_version` matches, so:

* A **student** who is already logged in elsewhere gets **409** *"This account is already logged in on another
  device. Please logout from the other device first."*
* Logout, admin force-logout, password change, or account deactivation invalidates the token immediately.
* Access tokens expire after `JWT_ACCESS_TOKEN_MINUTES` (default 120); `POST /api/auth/refresh` rotates them.
* 5 failed logins lock the identifier for 15 minutes; sensitive public endpoints are IP rate-limited.

---

## 10. File uploads & video security

Videos/PDFs are stored under `STORAGE_BASE_PATH` with random names; only relative paths are in MySQL.
`/api/student/lessons/{id}/video` is served through the backend with HTTP Range support and requires a
short-lived, lesson-scoped signed ticket (`/stream-ticket`) — issued only after the enrollment check passes,
and re-checked on every chunk. Upload limits: videos `MAX_VIDEO_SIZE_MB` (512), PDFs `MAX_DOCUMENT_SIZE_MB` (25);
type is validated by extension **and** content type.

---

## 11. Admin portal features

Dashboard stats & recent activity · global search (name / Student ID / email / course / payment ID) ·
Students (create, edit, activate/deactivate, force-logout, send password link, delete, view courses/payments/
sessions/journal) · Courses (add, edit, rename, price, activate/deactivate, delete, reorder) · Modules & Lessons
(add, rename, reorder, activate, delete, upload video / PDF) · Enrollments (list, manual enroll — marked
ADMIN_MANUAL, change status) · Payments (all transactions with mode DEMO / RAZORPAY and Razorpay IDs, filters) ·
Payment Gateway (Razorpay credentials, validate, enable/disable/remove — secrets encrypted, never shown again) ·
Success Stories (add, edit, upload/replace/remove video, thumbnail, category Students / Teachers / Parents,
publish/unpublish, reorder, delete) · Testimonials (visitor reviews: pending / approved / declined, accept or
decline with confirmation, details with photo, delete; plus admin-authored testimonials: add, edit, photo
upload/replace/remove, visibility; pending count on the dashboard and sidebar) ·
Ebooks (add, edit, price, cover, PDF upload/replace, activate/deactivate, delete when unpurchased, manual
access grants) · Invoices (filter, view/download/print PDF, resend email, WhatsApp, Print/PDF/Excel export) ·
**Exam Applications** (students who completed every lesson of a course apply; approve / reject / schedule the exam window with
date, start and end time, reschedule, cancel, view attempts) · **Exams** (per course: title, instructions, total marks, passing
marks, maximum attempts, optional time limit; MCQ questions with exactly four options and one correct answer — questions must add
up to the total marks before activation) · **Exam Results** (every submitted attempt with score, pass/fail and certificate) ·
**Certificates** (issued certificates + certificate design: the built-in default Lord Sai certificate is always available; upload a
JPG/PNG background, preview, activate, or switch back to the default — already issued certificates never change) ·
Trade journal review & Doubt desk · Mutual Fund Homepage Slider (add/replace/reorder/activate-deactivate/delete the
images shown in the existing Mutual Fund homepage hero carousel — Share Market has no equivalent) ·
Audit logs · Change password.

Courses also support thumbnail upload and removing a lesson's video / PDF. The dashboard shows which payment
mode is live (**DEMO PAYMENT ● ACTIVE** or **RAZORPAY PAYMENT ● ACTIVE**) with demo / Razorpay / failed counts.

**Roles.** Only ADMIN and STUDENT can sign in. The former Teacher role was retired in migration `V7`
(legacy TEACHER accounts are disabled, their tables dropped); "Teachers" now exists only as a Success Story
category. Admin performs every course, video and mentoring operation.

---

## 12. Production deployment notes

* Run behind HTTPS (nginx/Caddy). Proxy `/api/` to `localhost:8080`; serve the static site from the same host
  so cookies/CORS are simple. Set `CORS_ALLOWED_ORIGINS` and `PUBLIC_BASE_URL` to the real domain.
* Use live Razorpay keys and register the webhook URL.
* Set `MAIL_ENABLED=true` with a real SMTP account.
* Keep `uploads/` on persistent storage and back it up with the database.
* Schema changes go in new Flyway files `V22__…sql` (V1–V21 exist); Hibernate runs in `validate` mode and refuses to start on drift.
* Change the bootstrap admin password on first login and unset `BOOTSTRAP_ADMIN_*`.

---

## 13. Tests

`mvn test` — 85 tests covering: login (valid/invalid/disabled/locked/retired-role), duplicate-device rejection,
logout and password-reset token invalidation, role isolation (student vs admin endpoints, no teacher surface),
payment mode switching (demo ↔ Razorpay, no demo fallback during a Razorpay outage), Razorpay signature
verification, forged/duplicate/webhook payment handling, price-from-DB, duplicate enrollment prevention, course
CRUD, thumbnail upload, lesson video/PDF removal, curriculum reordering, progress calculation, video ticket
forgery and cross-student access, success stories (publish gating, category filter, streaming, RBAC), visitor reviews (pending → approve/decline
→ public visibility per website, validation, HTML stripping, duplicate/rate protection, RBAC, Share Market /
Mutual Fund isolation), Mutual Fund homepage slider (seeded migration, CRUD, activate/deactivate, reorder,
replace image, RBAC), email templates.

---

## 14. Course exam & certificate workflow

```
every active lesson completed (existing lesson_progress rule -> enrollment COMPLETED)
   -> Student Panel > My Exams > Apply for Exam            POST /api/student/exams/apply
   -> Main Admin > Exam Applications (PENDING)             approve / reject / schedule
   -> Main Admin > Exams: create exam, total & passing marks, max attempts, MCQ questions, activate
   -> schedule = exam + date + start/end time (IST)        POST /api/admin/exam-applications/{id}/schedule
   -> Student Panel shows the schedule; Start Exam only inside the window (server clock)
   -> POST /api/student/exams/applications/{id}/start     paper without correct options
   -> POST /api/student/exams/attempts/{id}/submit        backend scores, PASS if score >= passing marks
   -> PASS: certificate LSI-CERT-YYYY-NNNNNN issued (default Lord Sai design or the active uploaded design)
      FAIL: attempt again while attempts remain (limit enforced by a DB unique constraint)
   -> GET /api/student/certificates/{id}/pdf              owner-only, bearer-authenticated
```

Emails (application received, scheduled/rescheduled, result, certificate) go through the existing email
configuration and are recorded in `communication_logs` (type `EXAM_NOTIFICATION`); delivery is never required for exam access.

---

## Legacy note

The original static site instructions still apply for the public pages (`python3 -m http.server 8000`,
Mutual Fund mode at `home.html?mode=mutual-fund`). The old demo logins (`student123` etc.) have been removed;
all authentication now goes through the backend.
