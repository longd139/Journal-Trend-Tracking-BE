# Admin Notification System — Implementation Plan

## Context

The SCITRACK application already has a user-specific notification system (`NOTIFICATION` table, REST API at `/api/v1/notifications`, SSE streaming, FE bell + full page). However, there is **no admin-specific notification infrastructure** — no broadcast events for admins, no email delivery, no user report/feedback system, and several `NotificationType` enum values (`SYSTEM`, `TREND_ALERT`) are defined but never triggered.

The user wants:
1. **Admin notifications** for: User events (new registrations, reports), System/Sync events (completed/failed, errors), Content events (new papers, trending keywords)
2. **In-app + Email** delivery
3. **Full CRUD** management (filter by type, mark read/unread, delete, paginate)
4. **User Report feature**: Users can report PDFs/files uploaded by admin → triggers admin notification

---

## Design Decisions

### 1. Broadcast Pattern (Single `NOTIFICATION` table)

**Decision**: Extend the existing `NOTIFICATION` table — add new `NotificationType` values. When an admin-relevant event occurs, create one `Notification` row per admin user.

**Why**:
- Reuses existing entity, repository, service, controller, SSE infrastructure
- Per-admin read status comes naturally (each admin has their own row)
- No new table, no schema migration complexity
- Existing FE bell/notification page work without modification

**Trade-off**: More rows (N admins × M events), but admin count is small.

### 2. Email via Spring Mail

**Decision**: Use `JavaMailSender` (SMTP config already in `application.properties`) with simple HTML email body.

**Why**: SMTP config already present; no need for Thymeleaf templates for simple notification emails.

### 3. New `USER_REPORT` Table for User Reports

**Decision**: Create a new `USER_REPORT` entity/table (separate from `PDF_REQUEST` which handles a different workflow — admin fulfilling PDF access).

---

## Implementation Steps

### Phase 1 — Backend: Extend Notification System (Foundation)

#### 1.1 Extend `NotificationType` enum
**File**: `Journal-Trend-Tracking-BE/src/main/java/com/sra/journal_tracking/entity/jpa/NotificationType.java`

Add new values:
```java
NEW_USER,         // New user registration
USER_REPORT,      // User submitted a report
SYNC_COMPLETED,   // Data sync completed successfully
SYNC_FAILED,      // Data sync failed
SYSTEM_ALERT,     // System-level alert (errors, warnings)
CONTENT_ALERT,    // Content-related alert (trending keyword spike, etc.)
```

#### 1.2 Add admin-specific query methods to `NotificationRepository`
**File**: `Journal-Trend-Tracking-BE/src/main/java/com/sra/journal_tracking/repository/jpa/NotificationRepository.java`

Add:
```java
// Filter by type for admin notification dashboard
Page<Notification> findByUser_UserIdAndTypeOrderByCreatedAtDesc(UUID userId, NotificationType type, Pageable pageable);

// Count unread by type (for admin badge breakdown)
long countByUser_UserIdAndIsReadFalseAndType(UUID userId, NotificationType type);
```

#### 1.3 Create `AdminNotificationService`
**File**: `Journal-Trend-Tracking-BE/src/main/java/com/sra/journal_tracking/service/AdminNotificationService.java` (interface)
**File**: `Journal-Trend-Tracking-BE/src/main/java/com/sra/journal_tracking/service/impl/AdminNotificationServiceImpl.java`

Responsibilities:
- `broadcastToAdmins(NotificationType type, String title, String message, ...)` — queries all admin users via `UserRepository.findByRole_RoleName("admin")`, creates one `Notification` per admin, saves, publishes SSE events
- `getAdminNotifications(email, page, size, typeFilter)` — paginated with optional type filter
- Uses `@Transactional` + `NotificationEventPublisher` for real-time SSE push to each admin

#### 1.4 Extend `NotificationController` (add type filter)
**File**: `Journal-Trend-Tracking-BE/src/main/java/com/sra/journal_tracking/controller/NotificationController.java`

Add optional `?type=new_user` query parameter to `GET /api/v1/notifications`:
```java
@RequestParam(required = false) String type  // filter by NotificationType
```

### Phase 2 — Backend: Email Infrastructure

#### 2.1 Create `EmailService`
**File**: `Journal-Trend-Tracking-BE/src/main/java/com/sra/journal_tracking/service/EmailService.java`

- `sendAdminNotification(String toEmail, String subject, String body)` — sends HTML email via `JavaMailSender`
- Uses `@Async` for non-blocking send
- Simple inline HTML (no Thymeleaf templates needed initially — keeps it simple)

#### 2.2 Wire email into `AdminNotificationService`
In `AdminNotificationServiceImpl.broadcastToAdmins()`:
- After creating notification rows for each admin
- For each admin with `isActive = true`, also call `EmailService.sendAdminNotification()`
- Email sending failures should NOT roll back notification creation (use try-catch, log error)

#### 2.3 Email configuration verification
**File**: `Journal-Trend-Tracking-BE/src/main/resources/application.properties`

Verify SMTP settings are correct and add any missing properties:
```properties
spring.mail.host=${MAIL_HOST:smtp.gmail.com}
spring.mail.port=${MAIL_PORT:587}
spring.mail.username=${MAIL_USERNAME:}
spring.mail.password=${MAIL_PASSWORD:}
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true
```

### Phase 3 — Backend: Notification Triggers (Integration Points)

#### 3.1 New user registration → `NEW_USER` notification
**File**: `Journal-Trend-Tracking-BE/src/main/java/com/sra/journal_tracking/service/impl/AuthServiceImpl.java`

After successful user registration (`register()` method):
```java
adminNotificationService.broadcastToAdmins(
    NotificationType.NEW_USER,
    "New User Registration",
    "User " + user.getFullName() + " (" + user.getEmail() + ") has registered."
);
```

#### 3.2 Sync completed → `SYNC_COMPLETED` / `SYNC_FAILED` notification
**File**: `Journal-Trend-Tracking-BE/src/main/java/com/sra/journal_tracking/service/impl/DataSyncServiceImpl.java`

In sync completion/failure paths:
```java
// On success:
adminNotificationService.broadcastToAdmins(
    NotificationType.SYNC_COMPLETED,
    "Data Sync Completed",
    "Sync from " + source + " completed. " + papersInserted + " papers inserted."
);

// On failure:
adminNotificationService.broadcastToAdmins(
    NotificationType.SYNC_FAILED,
    "Data Sync Failed",
    "Sync from " + source + " failed: " + errorMessage
);
```

#### 3.3 Upgrade request → `SYSTEM_ALERT` notification
**File**: `Journal-Trend-Tracking-BE/src/main/java/com/sra/journal_tracking/controller/AdminUpgradeController.java`

After a user submits an upgrade request:
```java
adminNotificationService.broadcastToAdmins(
    NotificationType.SYSTEM_ALERT,
    "New Upgrade Request",
    "User " + userEmail + " has requested to upgrade to Researcher."
);
```

#### 3.4 Scheduled trend detection → `CONTENT_ALERT`
**File**: `Journal-Trend-Tracking-BE/src/main/java/com/sra/journal_tracking/service/ScheduledDataSyncService.java`

After daily sync, if new trending keywords detected with significant spike:
```java
adminNotificationService.broadcastToAdmins(
    NotificationType.CONTENT_ALERT,
    "Trending Keyword Alert",
    "Keyword '" + keyword + "' has spiked " + growthRate + "% this week."
);
```

### Phase 4 — Backend: User Report Feature

#### 4.1 Database schema
**File**: `Journal-Trend-Tracking-BE/schema.sql` (append)

```sql
CREATE TABLE USER_REPORT (
    ReportID UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),
    UserID UNIQUEIDENTIFIER NOT NULL FOREIGN KEY REFERENCES [USER](UserID),
    ReportType NVARCHAR(50) NOT NULL,       -- 'PDF_ISSUE', 'CONTENT_ERROR', 'OTHER'
    TargetType NVARCHAR(50),                -- 'PAPER', 'PDF', 'JOURNAL'
    TargetID UNIQUEIDENTIFIER,              -- FK to the reported entity
    Title NVARCHAR(300) NOT NULL,
    Description NVARCHAR(MAX),
    Status NVARCHAR(20) NOT NULL DEFAULT 'pending',  -- pending, reviewed, resolved, dismissed
    AdminNote NVARCHAR(MAX),
    ResolvedByAdminID UNIQUEIDENTIFIER FOREIGN KEY REFERENCES [USER](UserID),
    CreatedAt DATETIME2 NOT NULL DEFAULT GETDATE(),
    ResolvedAt DATETIME2
);
```

#### 4.2 Entity
**File**: `Journal-Trend-Tracking-BE/src/main/java/com/sra/journal_tracking/entity/jpa/UserReport.java`

Follow existing entity pattern: `@Entity`, `@Table(name = "USER_REPORT")`, Lombok, UUID PK, `@ManyToOne(fetch = LAZY)` to User, `@PrePersist`.

#### 4.3 Enum + Converter
**Files**:
- `.../entity/jpa/ReportType.java` — PDF_ISSUE, CONTENT_ERROR, OTHER
- `.../entity/jpa/ReportStatus.java` — PENDING, REVIEWED, RESOLVED, DISMISSED
- `.../entity/jpa/ReportTypeConverter.java`
- `.../entity/jpa/ReportStatusConverter.java`

#### 4.4 Repository
**File**: `Journal-Trend-Tracking-BE/src/main/java/com/sra/journal_tracking/repository/jpa/UserReportRepository.java`

```java
Page<UserReport> findByStatusOrderByCreatedAtDesc(ReportStatus status, Pageable pageable);
Page<UserReport> findByUser_UserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
long countByStatus(ReportStatus status);
```

#### 4.5 Service
**Files**:
- `.../service/UserReportService.java` (interface)
- `.../service/impl/UserReportServiceImpl.java`

Methods:
- `createReport(email, CreateReportRequest)` — user submits a report → triggers `USER_REPORT` notification to admins via `AdminNotificationService`
- `getMyReports(email, page, size)` — user views their own reports
- `getAllReports(status, page, size)` — admin views all reports (filtered by status)
- `updateReportStatus(reportId, status, adminNote, adminEmail)` — admin resolves/dismisses
- Map to `UserReportResponse` DTO

#### 4.6 Controller — User-facing
**File**: `Journal-Trend-Tracking-BE/src/main/java/com/sra/journal_tracking/controller/UserReportController.java`

```java
@RestController
@RequestMapping("/api/v1/reports")
@SecurityRequirement(name = "Bearer Authentication")
```
- `POST /` — Create report (any authenticated user)
- `GET /my` — User's own reports (paged)

#### 4.7 Controller — Admin-facing
**File**: `Journal-Trend-Tracking-BE/src/main/java/com/sra/journal_tracking/controller/AdminReportController.java`

```java
@RestController
@RequestMapping("/api/admin/reports")
@PreAuthorize("hasRole('ADMIN')")
```
- `GET /` — List all reports (`?status=`, paged)
- `GET /{id}` — Report detail
- `PUT /{id}/status` — Update status (reviewed/resolved/dismissed)

#### 4.8 DTOs
**Files**:
- `.../dto/report/CreateReportRequest.java` — reportType, targetType, targetId, title, description
- `.../dto/report/UserReportResponse.java` — all report fields + reporter name/email, resolver name
- `.../dto/report/UpdateReportStatusRequest.java` — status, adminNote

### Phase 5 — Frontend: Admin Notifications Page

#### 5.1 Create `AdminNotificationsPage`
**File**: `Journal-Trend-Tracking-FE/src/features/admin/AdminNotificationsPage.jsx`

A full admin notification dashboard following existing admin page patterns:
- **Filter bar**: Type filter pills (ALL / NEW_USER / USER_REPORT / SYNC_COMPLETED / SYNC_FAILED / SYSTEM_ALERT / CONTENT_ALERT)
- **Table/list**: Notification cards grouped by date, with type icon + color
- **Actions**: Mark read, mark all read, delete, bulk delete
- **Pagination**: 15 per page
- **Auto-refresh toggle**: Poll every 60s (like AdminOverviewPage)
- **Empty state**: "No admin notifications"
- Uses Framer Motion for animations
- Uses `useTranslation('admin')` + `useTranslation('common')`

#### 5.2 Extend `admin/api.js`
**File**: `Journal-Trend-Tracking-FE/src/features/admin/api.js`

Add admin notification API methods:
```javascript
getAdminNotifications({ page, size, type }) // GET /api/v1/notifications?type=
getUnreadCountByType()                      // GET /api/v1/notifications/unread-count (existing)
```

#### 5.3 Update router
**File**: `Journal-Trend-Tracking-FE/src/app/router.jsx`

Add new lazy import:
```javascript
const AdminNotificationsPage = lazy(() => import('../features/admin/AdminNotificationsPage.jsx'));
```

Add route (inside admin children):
```javascript
{
  path: 'admin-notifications',
  element: (
    <ProtectedRoute allowedRoles={['admin']}>
      <Suspense fallback={<FallbackLoading />}>
        <AdminNotificationsPage />
      </Suspense>
    </ProtectedRoute>
  ),
},
```

#### 5.4 Update sidebar navigation
**File**: `Journal-Trend-Tracking-FE/src/shared/layouts/MainLayout.jsx`

Add "Admin Notifications" to the admin sidebar nav items (between "Notifications" and "Audit Logs").

### Phase 6 — Frontend: User Report Feature

#### 6.1 Create `ReportForm` component
**File**: `Journal-Trend-Tracking-FE/src/features/reports/ReportForm.jsx`

A modal/dialog for users to submit reports:
- Dropdown: Report type (PDF Issue / Content Error / Other)
- Target: auto-filled if navigated from a paper/PDF page
- Title + Description text fields
- Submit button → calls `POST /api/v1/reports`
- Success toast via `sonner`

#### 6.2 Create user-facing `MyReportsPage`
**File**: `Journal-Trend-Tracking-FE/src/features/reports/MyReportsPage.jsx`

- List user's submitted reports
- Status badges (pending/reviewed/resolved/dismissed)
- Admin response/note display

#### 6.3 Create admin-facing `AdminReportsPage`
**File**: `Journal-Trend-Tracking-FE/src/features/admin/AdminReportsPage.jsx`

- Table of all reports with status filter
- Click row → detail slide-over (shadcn Sheet pattern, like AdminAuditLogPage)
- Actions: Mark reviewed, Resolve, Dismiss (with admin note)

#### 6.4 Add report button to paper detail / PDF view
Add "Report Issue" button on paper detail page and PDF request view, opening the `ReportForm` dialog.

#### 6.5 Update router for new pages
Add routes:
- `/:roleName/my-reports` → `MyReportsPage` (researcher, academic_user)
- `/:roleName/admin/reports` → `AdminReportsPage` (admin)

### Phase 7 — Frontend: i18n

#### 7.1 Add admin notification strings
**File**: `Journal-Trend-Tracking-FE/src/i18n/locales/en/admin.json`

```json
"adminNotifications": {
  "title": "Admin Notifications",
  "description": "System events, user reports, and sync status alerts",
  "filterAll": "All",
  "filterNewUser": "New Users",
  "filterReport": "Reports",
  "filterSyncCompleted": "Sync OK",
  "filterSyncFailed": "Sync Failed",
  "filterSystem": "System",
  "filterContent": "Content",
  "emptyTitle": "No admin notifications",
  "emptyDesc": "Everything is running smoothly."
},
"reports": {
  "title": "User Reports",
  "description": "Manage content reports and feedback from users",
  "filterAll": "All",
  "filterPending": "Pending",
  "filterReviewed": "Reviewed",
  "filterResolved": "Resolved",
  "filterDismissed": "Dismissed",
  "detail": "Report Detail",
  "reporter": "Reporter",
  "type": "Type",
  "target": "Target",
  "adminNote": "Admin Note",
  "resolve": "Resolve",
  "dismiss": "Dismiss",
  "markReviewed": "Mark Reviewed"
}
```

#### 7.2 Add Vietnamese translations
**File**: `Journal-Trend-Tracking-FE/src/i18n/locales/vi/admin.json`

Mirror all new keys with Vietnamese translations.

#### 7.3 Add report-related strings
**File**: `Journal-Trend-Tracking-FE/src/i18n/locales/en/common.json`

```json
"report": {
  "title": "Report an Issue",
  "submitReport": "Submit Report",
  "reportType": "Report Type",
  "description": "Description",
  "pdfIssue": "PDF Issue",
  "contentError": "Content Error",
  "other": "Other",
  "submitted": "Report submitted successfully",
  "submitting": "Submitting..."
}
```

---

## Files to Modify (Summary)

### Backend (BE)
| Action | File |
|--------|------|
| **Modify** | `entity/jpa/NotificationType.java` — add 6 new enum values |
| **Modify** | `repository/jpa/NotificationRepository.java` — add type-filter queries |
| **Create** | `service/AdminNotificationService.java` — interface |
| **Create** | `service/impl/AdminNotificationServiceImpl.java` — broadcast + filter logic |
| **Create** | `service/EmailService.java` — email sending |
| **Modify** | `controller/NotificationController.java` — add `?type=` param |
| **Modify** | `service/impl/AuthServiceImpl.java` — trigger NEW_USER |
| **Modify** | `service/impl/DataSyncServiceImpl.java` — trigger SYNC_COMPLETED/FAILED |
| **Modify** | `service/ScheduledDataSyncService.java` — trigger CONTENT_ALERT |
| **Modify** | `controller/AdminUpgradeController.java` — trigger SYSTEM_ALERT |
| **Create** | `entity/jpa/UserReport.java` — JPA entity |
| **Create** | `entity/jpa/ReportType.java` — enum |
| **Create** | `entity/jpa/ReportStatus.java` — enum |
| **Create** | `entity/jpa/ReportTypeConverter.java` |
| **Create** | `entity/jpa/ReportStatusConverter.java` |
| **Create** | `repository/jpa/UserReportRepository.java` |
| **Create** | `service/UserReportService.java` — interface |
| **Create** | `service/impl/UserReportServiceImpl.java` |
| **Create** | `controller/UserReportController.java` — user-facing |
| **Create** | `controller/AdminReportController.java` — admin-facing |
| **Create** | `dto/report/CreateReportRequest.java` |
| **Create** | `dto/report/UserReportResponse.java` |
| **Create** | `dto/report/UpdateReportStatusRequest.java` |
| **Modify** | `schema.sql` — add USER_REPORT table DDL |
| **Modify** | `application.properties` — verify SMTP config |

### Frontend (FE)
| Action | File |
|--------|------|
| **Create** | `features/admin/AdminNotificationsPage.jsx` — admin notification dashboard |
| **Modify** | `features/admin/api.js` — add admin notification API methods |
| **Create** | `features/reports/ReportForm.jsx` — report submission dialog |
| **Create** | `features/reports/MyReportsPage.jsx` — user's reports list |
| **Create** | `features/reports/api.js` — report API methods |
| **Create** | `features/admin/AdminReportsPage.jsx` — admin report management |
| **Modify** | `app/router.jsx` — add new routes |
| **Modify** | `shared/layouts/MainLayout.jsx` — add sidebar nav item |
| **Modify** | `i18n/locales/en/admin.json` — add admin notification + report strings |
| **Modify** | `i18n/locales/vi/admin.json` — Vietnamese translations |
| **Modify** | `i18n/locales/en/common.json` — add report strings |
| **Modify** | `i18n/locales/vi/common.json` — Vietnamese translations |

---

## Verification Plan

### Backend
1. **Start SQL Server**: `docker compose up -d` in BE directory
2. **Run the app**: `mvn spring-boot:run`
3. **Test via Swagger**: `http://localhost:8080/swagger-ui/index.html`
   - Register a new user → verify `NEW_USER` notification created for admin
   - Login as admin → `GET /api/v1/notifications` → see new user notification
   - Trigger manual sync → verify `SYNC_COMPLETED` notification
   - Create a report via `POST /api/v1/reports` → verify `USER_REPORT` notification
   - Filter notifications by type → verify `?type=new_user` works
4. **Test email**: Check admin email inbox for notification emails (if SMTP configured)

### Frontend
1. **Start FE**: `npm run dev` in FE directory
2. **Login as admin** → navigate to `/admin/admin-notifications` → see notification dashboard
3. **Verify type filters** work correctly
4. **Verify mark read/delete/bulk delete** actions
5. **Login as researcher** → submit a report from paper detail/page
6. **Login as admin** → navigate to `/admin/reports` → see submitted report → resolve it

### Integration
- New user registers → admin sees notification in bell + email + admin notifications page
- Sync completes/fails → admin notified
- User submits report → admin notified → admin resolves → user sees resolution
