# LSI — Course + Ebook + Invoice + Communication implementation report (17 Sep 2026)

Everything below was built by **extending** the existing Spring Boot / static-site architecture. No
existing system (auth, Razorpay, enrollment, email, storage, admin panels) was replaced or duplicated.

## 1. Test results

| Suite | Result |
|---|---|
| `mvn test` before the change (baseline) | 173 tests, 0 failures |
| `mvn test` after the change | **207 tests, 0 failures, 0 errors** (H2, Flyway V1–V18) |
| Live check on port 8081 against local MySQL (`lsi_db`, V18 applied) | admin login → create ebook → upload PDF → activate → public catalogue → demo ebook purchase (student `LSI-2026-00010`, invoice `LSI-INV-2026-000001`) → duplicate purchase refused (409) → same email (upper-case) buys course → **same Student ID**, invoice `LSI-INV-2026-000002`, `newAccount=false` → admin invoice PDF / print / Excel / purchase PDF → communication preview + send (email + WhatsApp both recorded FAILED with honest reasons) → anonymous ebook download 401, admin token on student endpoint 403 → deactivate ebook keeps the purchase, delete refused (409) → dashboard counters |

New automated tests (34): `EbookPurchaseFlowTest` (11), `EbookAccessAndAdminTest` (5), `InvoiceTest` (4),
`CommunicationTest` (10), `ExportTest` (3), `EmailTemplateTest` (+1) plus the existing suites. Existing tests
that changed: `AccountSetupFlowTest` (purchase now uses the purchase-confirmation email, the setup link is
captured from it), `PaymentFlowTest` (duplicate-course message wording), `RazorpayGatewaySignatureTest`
(`AppProperties` gained the WhatsApp/ebook settings).

Scenarios from the specification covered by tests: new student course purchase, existing student course purchase,
duplicate course purchase, invalid payment, duplicate webhook, invoice creation, email failure, course access,
unauthorized course access, new student ebook purchase, existing student ebook purchase, duplicate ebook purchase,
ebook entitlement, protected ebook download, unauthorized ebook download, ebook invoice, course→ebook,
ebook→course, course+ebook same student, no duplicate Student ID, change course price (existing), change ebook
price, upload ebook, replace ebook PDF, activate/deactivate ebook, individual email, individual WhatsApp, bulk
email, bulk WhatsApp, both channels, failed delivery, retry, audit logging, print, PDF, Excel, filtered export.

## 2. Database migration

`backend/src/main/resources/db/migration/V18__ebooks_invoices_communications.sql` (V1–V17 untouched):

* `ebooks` — catalogue (code, title, author, descriptions, category, language, price/discounted price, cover
  path, **protected** PDF path/name/size, status DRAFT/ACTIVE/INACTIVE, display order, audit columns).
* `payments` — now the single purchase record for every product: `product_type` (COURSE/EBOOK, default COURSE),
  `ebook_id` FK, `course_id` made nullable; indexes on product and verified_at. Existing rows unchanged.
* `ebook_entitlements` — `unique(student_user_id, ebook_id)`, `unique(payment_id)`, status ACTIVE/REVOKED, source.
* `invoice_sequence` + `invoices` — one invoice per payment (`unique(payment_id)`, `unique(invoice_number)`),
  snapshot of student/product/amounts/discount/tax/total/method/transaction id, invoice + purchase dates, PDF path,
  emailed_at / email_count; indexes on student, email, product, dates, transaction id.
* `communication_logs` — channel, message type, student, recipient, subject, body, status PENDING/SENT/FAILED,
  provider, provider message id, error reason, attachment, invoice, product, batch ref, attempts, sent by;
  indexes on status, channel, student, created_at, batch, product.

## 3. Backend — files created

| Area | Files |
|---|---|
| Entities / enums | `entity/Ebook`, `EbookEntitlement`, `Invoice`, `InvoiceSequence`, `CommunicationLog`; enums `ProductType`, `EbookStatus`, `EntitlementStatus`, `CommunicationChannel`, `CommunicationStatus`, `CommunicationType` |
| Repositories | `EbookRepository`, `EbookEntitlementRepository`, `InvoiceRepository`, `InvoiceSequenceRepository`, `CommunicationLogRepository` |
| Services | `EbookService`, `EbookEntitlementService`, `InvoiceNumberService`, `InvoiceService`, `PurchaseCommunicationService`, `PurchaseFulfilment`, `CommunicationService`, `CommunicationSender`, `CommunicationDispatcher`, `LmsReportService` |
| Export | `export/PdfRenderer` (Thymeleaf → PDF, embedded Noto Sans for ₹), `ExportTable`, `ExcelExporter` (Apache POI .xlsx), `ReportExportService` |
| WhatsApp | `communication/WhatsAppProvider`, `WhatsAppDelivery`, `WhatsAppMessageService`, `MetaCloudWhatsAppProvider` (official Meta Cloud API, text + document) |
| Controllers | `PublicEbookController`, `admin/AdminEbookController`, `student/StudentLibraryController`, `automation/web/AutomationCommunicationController` |
| Config | `config/AsyncConfig` (single-worker executor for batched sends) |
| Templates | `templates/invoice/invoice.html` (the ONE invoice design), `templates/export/report.html` (branded report PDF/print), emails `course-purchase-confirmation.html`, `ebook-purchase-confirmation.html`, `existing-student-new-purchase.html`, `invoice-email.html`, `communication.html`, `purchase-fragments.html` |
| Branding | `resources/branding/lord-sai-logo.png` (copy of the existing blue `img/lord-sai-logo.png` — no new logo), `branding/fonts/NotoSans-{Regular,Bold}.ttf` + OFL licence |
| DTOs | `dto/ebook/EbookDtos`, `dto/invoice/InvoiceDtos`, `dto/communication/CommunicationDtos` |
| Tests | `ebook/EbookPurchaseFlowTest`, `ebook/EbookAccessAndAdminTest`, `invoice/InvoiceTest`, `communication/CommunicationTest`, `export/ExportTest` |

## 4. Backend — files modified

`pom.xml` (openhtmltopdf-pdfbox 1.1.28, poi-ooxml 5.3.0), `application.yml` (whatsapp / communication / ebook
size settings), `.env.example`, `AppProperties` (WhatsApp + ebook limit), `entity/Payment` (product type, ebook,
optional course, helpers), `PaymentService` (product-agnostic order/verify/webhook/demo, invoice + product data in
responses), `EnrollmentService.fulfilPayment` (adds invoice + purchase email; `resendSetupEmail` unchanged),
`FileStorageService` (EBOOK + INVOICE kinds, `storeBytes`, `readBytes`, `exists`, `safeFilename`),
`EmailService` / `SmtpEmailService` / `LoggingEmailService` / `EmailDelivery` (attachments, `sendPurchaseEmail`,
`sendMessage`, provider in the outcome), `PaymentDtos` / `GatewayDtos` (productType, ebookId, productName,
invoiceNumber), `AdminDtos` / `AdminService` (dashboard counters), `AcadReportService` (all on-screen filters,
new master-data reports), `AutomationLedgerController` (`/reports/{report}/{csv|xlsx|pdf}` + `/print`),
`application-test.yml`, `README.md`.

## 5. API endpoints added

Public: `GET /api/public/ebooks`, `GET /api/public/ebooks/{id}`; `POST /api/payments/create-order` and
`/demo-complete` accept `productType` + `ebookId` (courses unchanged).

Student (ROLE_STUDENT + ownership): `GET /api/student/ebooks`, `GET /api/student/ebooks/{id}`,
`GET /api/student/ebooks/{id}/download` (403 without entitlement, audited), `GET /api/student/invoices`,
`GET /api/student/invoices/{id}`, `GET /api/student/invoices/{id}/pdf` (owner-scoped lookup).

Main Admin: `GET/POST /api/admin/ebooks`, `GET/PUT/DELETE /api/admin/ebooks/{id}`, `PATCH …/price`,
`PATCH …/status`, `PUT /api/admin/ebooks/reorder`, `POST …/cover`, `POST …/pdf`, `GET …/pdf`,
`GET …/entitlements`, `GET /api/admin/students/{id}/ebooks`, `POST /api/admin/ebook-entitlements`,
`PATCH /api/admin/ebook-entitlements/{id}/status`.

Automation Admin + Main Admin (`/api/automation`, one set of endpoints for both panels):
`communications/status|counters|students|preview|send|attachments|batches/{ref}|history|history/{id}|history/{id}/retry|students/{id}/history`;
`invoices`, `invoices/{id}`, `invoices/{id}/pdf`, `invoices/{id}/print`, `invoices/{id}/resend-email`,
`invoices/{id}/whatsapp`; `reports/{report}` now also `purchases`, `invoices`, `communications`, `lms-students`,
`ebooks`, `batches`, `courses-master`, `payment-modes`, `session-types`, with `/csv`, `/xlsx`, `/pdf`, `/print`.

## 6. Purchase flow (course and ebook)

`create-order` reads the price from the database (course or ebook; client price ignored — tested), refuses a
second purchase of an owned product ("You already have access to this course/ebook…"). `verify` and the webhook
both route through `PaymentService.fulfil` → `EnrollmentService.fulfilPayment` (course) or
`EbookEntitlementService.fulfilPayment` (ebook): find-or-create ONE student by normalised email
(`UserService.findOrCreateStudent`, unchanged) → entitlement (DB-unique) → `InvoiceService.createForPayment`
(idempotent per payment, backend number `LSI-INV-YYYY-NNNNNN` from a row-locked sequence, PDF stored under
`uploads/invoices/`) → `PurchaseCommunicationService.sendPurchaseConfirmation` (invoice PDF attached; delivery
result recorded in `communication_logs`; failure never fails the purchase). Repeated verify/webhook calls create
nothing new (tested). Existing accounts receive "New Purchase Added To Your Existing Student Account".

## 7. Email

Templates reuse `email/layout.html`; Lord Sai blue (`#0B5FA5` / navy) accents, never Mutual Fund green.
Subjects: "Course Purchase Confirmed — Lord Sai Share Market Academy", "Ebook Purchase Confirmed — …",
"New Purchase Added To Your Existing Student Account — …". Each includes name, Student ID, product, price,
payment status/date, transaction id, invoice number, portal URL, login instructions, the secure one-time setup
link (new accounts only; existing password-setup mechanism reused, no passwords ever emailed), the access
condition text required by the specification, support contact and the invoice PDF attachment.

## 8. WhatsApp

`WhatsAppProvider` abstraction + `MetaCloudWhatsAppProvider` (official WhatsApp Business Cloud API: text and
document messages via media upload). Configured only from the environment; secrets never logged/returned.
When `WHATSAPP_PROVIDER=NONE` (default) every send is recorded as FAILED with "WhatsApp provider is not
configured." — nothing is ever reported as sent. Provider message id and failure reason are stored.
**Not tested against Meta** — no credentials were available (see §12).

## 9. Communication management (Automation Admin → Communication / Message History; Main Admin → Communication)

Students list with courses/ebooks and per-row Send Email / Send WhatsApp; filters product (all/course/ebook),
course, ebook, purchase (course/ebook/both/none), status, purchase-date range, search; select rows / select all /
send to all filtered; channel Email / WhatsApp / Both; subject, message, optional validated attachment; preview
"You are about to send this message to X students" with confirm; server queues one row per student+channel
(no duplicates), processes in batches (`COMM_BATCH_SIZE`, `COMM_BATCH_PAUSE_MS`) on a single background worker;
progress endpoint; history with channel/status/date/student/product filters, View, Retry (failed admin messages),
Resend invoice (failed purchase emails), Print / PDF / Excel. Audit actions: `BULK_COMMUNICATION_QUEUED`,
`INDIVIDUAL_COMMUNICATION_QUEUED`, `EMAIL_SENT/FAILED`, `WHATSAPP_SENT/FAILED`, `COMMUNICATION_RETRY`,
`INVOICE_GENERATED`, `INVOICE_RESENT/RESEND_FAILED`, `EBOOK_*`, `EBOOK_ENTITLEMENT_*`.

## 10. Frontend

* `store.html` — "Ebooks — Buy & Read in the Student Portal" section rendered from `/api/public/ebooks`; the shared
  purchase modal; `js/checkout.js` now handles COURSE and EBOOK through the same Razorpay/demo flow
  (`courses.html` modal made product-agnostic, shows invoice number on success).
* `student-dashboard.html` / `js/student-dashboard.js` — **My Courses** (renamed from My Learning), **My Ebooks**
  (cover, title, purchase date, in-portal PDF reader streamed with the bearer token + Student ID watermark),
  **My Invoices** (number, product, date, amount, status, View/Download), profile shows ebook record.
* `admin-dashboard.html` / `js/admin-dashboard.js` — **Ebooks** (add/edit/price/status/upload-replace PDF/cover/
  view/delete), **Invoices** (filters, view/download/print, resend email, WhatsApp, Print/PDF/Excel),
  **Communication** (channel status, history with retry, Send Message with attachment), student page shows
  ebooks (grant/revoke), invoice links, communication history, Send Email / Send WhatsApp; Payments show product
  type + invoice; dashboard cards for ebooks, entitlements, invoices, failed messages.
* `automation-admin.html` / `js/automation-admin.js` / `css/automation.css` — **Print / PDF / Excel** toolbar on
  every record section (dashboard list, Students, Payments, Receipts, Attendance sessions, Batches, Courses,
  Payment modes, Session types, Reports) respecting the on-screen filters; new **Purchase Reports**, **Invoices**,
  **Communication**, **Message History** views. Reports "Excel" is now a real .xlsx and "PDF" a server-rendered
  branded PDF with page numbers; "Print" opens the printer-friendly page (title, filters, generated time, table,
  record count — no sidebar/navigation).

## 11. Security summary

JWT filter already enforces valid token + active session + active account + usable role; new endpoints sit under
the existing role rules (`/api/admin/**` ADMIN, `/api/student/**` STUDENT, `/api/automation/**`
AUTOMATION_ADMIN/ADMIN). Ebook and invoice files live in `uploads/ebooks` and `uploads/invoices` and are only
streamed after the entitlement / owner check (owner-scoped repository lookups — Student A cannot open Student B's
invoice by changing the id; tested). Uploads validated by extension, content type, size; filenames sanitised;
paths resolved under the storage root. Prices, product ownership and invoice ownership are never taken from the
client. Excel/CSV cells neutralise formula injection. WhatsApp tokens only in the environment.

## 12. What is pending / needs configuration

1. **WhatsApp delivery** — implemented and unit-tested only for the "not configured" path and the request
   contract. To go live set `WHATSAPP_PROVIDER=META_CLOUD`, `WHATSAPP_ACCESS_TOKEN`, `WHATSAPP_PHONE_NUMBER_ID`,
   `WHATSAPP_BUSINESS_ACCOUNT_ID` (Meta for Developers → WhatsApp → API Setup). Meta only delivers free-form
   text/documents inside a 24-hour customer-service window; outside it an approved template is required (not
   implemented — the provider returns Meta's error verbatim so the history shows why).
2. **Real email delivery** — depends on the existing Email Settings / `MAIL_ENABLED`; the purchase emails were
   verified by template rendering and the log sink (the live check ran with mail disabled), not against an SMTP
   server.
3. **Razorpay live** — the gateway is mocked in tests and the live check used demo mode; the pipeline is the
   existing one with product type added.
4. The local MySQL now has V18 applied and the live-check data listed in the memory note (test ebook
   `LIVE-3C28E7`, student `LSI-2026-00010`); the user's own 8080 backend must be restarted to pick up the new code.
5. Not done: browser-level (UI) automation — JS was syntax-checked with esprima and every element id referenced by
   the scripts was verified to exist in its page; there is no headless browser run in this report.

## 13. Protected Course Content Hardening (19 Sep 2026)

### 13.1 Server-Side Authorization & Endpoint Security
* **Lesson Video Streaming (`/api/student/lessons/{id}/video`)**:
  - Enforces short-lived signed HMAC stream tickets generated per lesson (`/api/student/lessons/{id}/stream-ticket`).
  - Added cross-student session token verification: if an active caller presents a ticket issued to a different student user, access is immediately blocked with HTTP 403 and audited under `PROTECTION_UNAUTHORIZED_MEDIA_REQUEST`.
  - Streaming uses `VideoStreaming.partial` with chunked HTTP Range requests (`ResourceRegion`), never buffering complete video files in server memory.
* **Handout PDFs (`/api/student/lessons/{id}/material`)**:
  - Requires authenticated student JWT session + active course enrollment.
  - Header hardening: `Content-Disposition: inline`, `Cache-Control: private, no-store`, `Pragma: no-cache`, `Expires: 0`, `X-Content-Type-Options: nosniff`, `X-Frame-Options: SAMEORIGIN`.
* **Ebook Reading (`/api/student/ebooks/{id}/download` and `/api/student/ebooks/{id}/view`)**:
  - Enforces active student entitlement checks before streaming.
  - Header hardening: `Content-Disposition: inline`, `Cache-Control: private, no-store`, `Pragma: no-cache`, `Expires: 0`, `X-Frame-Options: SAMEORIGIN`.
* **Private Storage Unmapped from Web Root**:
  - Unmapped private disk storage: `./uploads/videos` and `./uploads/ebooks` have no static resource handlers; unauthorized or direct requests return HTTP 401/404.

### 13.2 Visual Cleanliness & Zero-Watermark Compliance
* **Strictly No Watermarks**:
  - Removed all video player background watermarks, tiling, SVG generators, and periodic positional shift timers.
  - Made handout canvas stamping (`LSI_Protect.stampCanvas`) a safe no-op so PDF handouts render with 100% clarity and zero watermark overlay.
  - Removed `#ebookReaderWatermark` from `student-dashboard.html` and cleaned up reader script assignments.
  - Updated client security notices and lockdown copy to eliminate any false references to watermarks.

### 13.3 Client-Side Mitigations & Browser Controls
* **Video Player Controls**:
  - Native `<video>` configured with `controlsList="nodownload noremoteplayback"`, `disablePictureInPicture`, `playsinline`, `preload="metadata"`.
  - Bound client-side listeners blocking `dragstart` and `contextmenu` events.
* **Ebook & Handout Viewers**:
  - Ebook reader iframe loads with `#toolbar=0&navpanes=0` via temporary object blob URLs revoked immediately upon closing the modal.
  - Handouts rendered to HTML5 canvas elements via PDF.js rather than embedding native browser PDF viewers with save/print toolbars.
* **Protection & Capture Interception**:
  - `student-protect.js` monitors window blur, visibility change, and common screen-capture/devtools shortcuts (`PrintScreen`, `Ctrl+S`, `Ctrl+P`, `Ctrl+Shift+I`), displaying a shield overlay while reporting throttled security events to `/api/student/protection-events`.
  - Honest security posture: Browser-level script controls cannot physically block OS-level window managers, kernel-level screen recording software, or external cameras; the platform relies on hardened server authorization, private storage, and short-lived scoped tickets.

### 13.4 Verification & Automated Test Results
* `com.lordsai.lsi.learning.ContentProtectionTest`: **9 tests passed** (including cross-student ticket denial & audit, video range chunk seeking with hardened headers, and unmapped upload URL protection).
* `com.lordsai.lsi.ebook.EbookAccessAndAdminTest`: **5 tests passed** (including `/view` endpoint and SAMEORIGIN frame options).
* `com.lordsai.lsi.ebook.EbookSimpleUploadTest`: **6 tests passed** (single-step upload, entitlement checks, and protected storage).
