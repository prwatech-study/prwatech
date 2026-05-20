# Skillama backend — feature verification

Base path: `/skillama` (e.g. `https://prwatech.xyz/skillama`).

Last verified against codebase: **2026-05-15**.

## Legend

| Status | Meaning |
|--------|---------|
| ✅ | Implemented in this repo |
| ⚠️ | Implemented; confirm deployed to production |
| 🔧 | Fixed in repo this session; deploy required |

---

## Auth & registration

| Requirement | Endpoint | Status |
|-------------|----------|--------|
| Email OTP send | `POST /users/otp/email/send` | ✅ |
| Email OTP verify | `POST /users/otp/email/verify` | ⚠️ Deploy |
| Freemium register (phone required) | `POST /users/register/freemium` | ✅ |
| Login with OTP | `POST /users/login/otp` | ✅ |
| Classic register | `POST /users/register` | ✅ |

---

## Freemium

| Requirement | Endpoint | Status |
|-------------|----------|--------|
| Get freemium status | `GET /user-profile/freemium` | ✅ |
| Consume query credit | `POST /user-profile/freemium/consume-query` | ✅ |
| Referral apply | `POST /user-profile/freemium/referral` | ✅ |
| Admin set plan tier | `PUT /api/admin/users/{userId}/plan` | ✅ |

---

## LMS access & progress

| Requirement | Endpoint | Status |
|-------------|----------|--------|
| Access control per course | `GET /user-profile/access-control?courseId=` | ✅ |
| Mark lecture complete | `POST /user-profile/lectures/{lectureId}/complete` | ✅ |
| **Per-course progress %** (no leak across courses) | Included in access-control `progressSummary` | 🔧 `UserProfileService.buildProgressSummary` filters by `courseId` |
| Chat track | `POST /user-profile/chat/track` | ✅ |

---

## Curriculum

| Requirement | Endpoint | Status |
|-------------|----------|--------|
| Submodule `enabled` flag | Admin curriculum CRUD | ✅ |
| Admin sees disabled topics | `GET /courses/{id}/curriculum?forAdmin=true` | ✅ |
| Learners hide disabled | `CourseService` filters when `forAdmin=false` | ✅ |
| Learners receive `scriptText` for narration/TTS | `copySubmoduleForLearner` copies field | ✅ |

---

## Reviews

| Requirement | Endpoint | Status |
|-------------|----------|--------|
| Submit review | `POST /reviews` | ✅ |
| List reviews | `GET /reviews?courseId=&scope=` | 🔧 Optional `scope` filter added |

---

## Sales / marketing

| Requirement | Endpoint | Status |
|-------------|----------|--------|
| Sales interest (consent) | `POST /leads/sales-interest` | ✅ |
| Admin list leads | `GET /api/admin/leads/sales-interest?page=&size=` | 🔧 Added |

---

## Admin

| Requirement | Endpoint | Status |
|-------------|----------|--------|
| Users list with date/phone filters | `GET /api/admin/users` | ✅ |
| User profile | `GET /api/admin/users/{id}/profile` | ✅ |
| User activity | `GET /api/admin/users/{id}/activity` | ✅ |
| User progress | `GET /api/admin/users/{id}/progress` | ✅ |

---

## Deploy checklist

1. Build: `./gradlew build` (or your CI pipeline).
2. Deploy to `prwatech.xyz`.
3. Smoke-test:
   - `POST .../users/otp/email/send` (was 404 on old deploy)
   - `GET .../user-profile/access-control?courseId=X` — new course should show `completionPercentage: 0`
   - `GET .../api/admin/leads/sales-interest` (admin JWT)
4. Update `skillama-lms/FRONTEND_API_INTEGRATION.md` checklist after production matches this doc.

---

## Files changed (this session)

- `UserProfileService.java` — course-scoped `buildProgressSummary`
- `CourseService.java` — learner `scriptText` included for TTS (prior: stripped)
- `ReviewRepository/Service/Controller` — `scope` query param (prior)
- `SalesLeadService.java` — `listLeads`
- `AdminController.java` — `GET /api/admin/leads/sales-interest`
