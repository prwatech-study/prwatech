# Legacy → Freemium backfill

Pre-freemium accounts have `planTier: null` and unlimited AI access. To align them with the product (50 queries, 70 with referral), run this backfill **once** after deploying the freemium API changes.

## Who gets migrated

| Included (default in Admin UI) | Excluded |
|----------|----------|
| `planTier == null` | Already `FREEMIUM`, `PAID`, `ENTERPRISE` |
| Inactive (optional checkbox) | `ADMIN`, `OWNER` |
| No phone (optional checkbox) | — |

Query params: `includeInactive`, `allowMissingPhone` (both default `false` on API; Admin Settings enables them by default).

Referral (`referredBy`) is preserved; limit becomes **70** and modules stay at the freemium set.

## 1. Dry-run (recommended first)

```bash
export BASE_URL="https://your-production-api"
export OWNER_TOKEN="<owner-jwt>"
chmod +x scripts/backfill-legacy-freemium.sh
./scripts/backfill-legacy-freemium.sh
```

Or curl:

```http
POST /skillama/api/admin/maintenance/backfill-legacy-to-freemium?dryRun=true
Authorization: Bearer <OWNER_JWT>
```

Response includes counts and email lists for migrated / skipped users.

## 2. Apply

```bash
./scripts/backfill-legacy-freemium.sh --apply
```

```http
POST /skillama/api/admin/maintenance/backfill-legacy-to-freemium?dryRun=false
```

## 3. Normalize existing FREEMIUM rows (optional)

If some users already have `FREEMIUM` but wrong limits:

```http
POST /skillama/api/admin/maintenance/normalize-freemium-limits
```

## Per-user migration (learner OTP)

Learners can still self-serve via:

- `POST /skillama/users/migrate/freemium/otp/send`
- `POST /skillama/users/migrate/freemium/confirm`

Use backfill for bulk; use OTP when phone must be collected from the user.
