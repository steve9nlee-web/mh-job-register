# MH Job Register — Role-Based Android Apps

One Android codebase that builds **four separate APKs**, one per role. Every
app follows the same WhatsApp → billing workflow, but each APK only shows the
steps and information that role needs.

| APK | Who installs it | What they see |
|---|---|---|
| **MH Job Register Admin** (blue) | You / office admin | Everything: full job register, AI-flag review, status updates, rate card matching, customer billing, cleaner & repairer payment summaries, invoice/payment tracking, follow-up list |
| **MH Job Register Cleaner** (teal) | Cleaning contractors | Only cleaning jobs, status update + remarks, their own pay. **No customer prices** |
| **MH Job Register Repairer** (orange) | Repair contractors (plumbing, electrical, aircon, general) | Only repair jobs, status update + remarks, their own pay. **No customer prices** |
| **MH Job Register Initiator** (purple) | Whoever posts the daily WhatsApp message | Paste WhatsApp message → AI conversion into register rows, job status tracking, pending follow-up list. **No money information at all** |

All four install side by side on one phone (different application IDs), so you
can test them together.

## Workflow coverage

```
WhatsApp Message        → Initiator / Admin: paste into "New Job"
AI Conversion           → On-device parser: normalize dates, find unit number,
                          classify category (typo-tolerant), detect pending/completed
Job Register            → Shared spreadsheet (Google Sheets via Apps Script)
Human Review / AI Flags → Admin "Review" tab: rows with missing info are flagged
Status Update           → Admin + contractors on their own jobs
Rate Card Matching      → Admin "Billing" tab: one tap fills customer charge + payable
Customer Billing        → Admin only
Cleaner/Repairer Payable→ Admin sees both summaries; each contractor sees only their own
Invoice & Payment       → Admin marks invoiced / paid; contractor sees "Paid" status
Pending Job Follow-up   → Admin + Initiator: jobs not ready for billing, oldest first
```

The per-role visibility rules live in one file:
[`app/src/main/java/com/jobregister/app/RoleConfig.kt`](app/src/main/java/com/jobregister/app/RoleConfig.kt).

## Getting the APKs

Every push to this branch runs the **Build role APKs** GitHub Actions workflow,
which compiles all four APKs.

1. Open the repo on GitHub → **Actions** tab → latest "Build role APKs" run.
2. Download the artifacts: `JobRegister-Admin`, `JobRegister-Cleaner`,
   `JobRegister-Repairer`, `JobRegister-Initiator`.
3. Copy each APK to the right person's phone and install (allow "install from
   unknown sources"). The APKs are debug-signed — fine for internal use, but
   replace the signing config in `app/build.gradle.kts` before any Play Store
   upload.

To build locally instead (needs Android SDK + JDK 17):

```
gradle assembleRelease        # all four
gradle assembleAdminRelease   # just one flavor
```

## Connecting all apps to one Job Register spreadsheet

**The APKs ship pre-configured**: the sync server URL and sync key are built
in (see `DEFAULT_SYNC_URL` / `DEFAULT_SYNC_KEY` in `app/build.gradle.kts`), so
a freshly installed app syncs to the shared Job Register with zero setup.
Settings can override both per phone. The Apps Script backend checks the key
(`SYNC_KEY` in `backend/Code.gs`) — keep the two values in sync.

If you ever need to re-deploy the backend from scratch:

1. Open your Job Register spreadsheet in Google Sheets.
2. Extensions → Apps Script → paste [`backend/Code.gs`](backend/Code.gs) → save.
3. Deploy → New deployment → Web app → Execute as **Me**, access **Anyone with
   the link** → copy the URL.
4. In each app: **Settings → Job Register sync URL** → paste → *Save & sync*.

Every status change, new job, review fix, and billing update is then pushed to
the sheet, and the sync button pulls the latest rows on any phone.

## Alternative backend: n8n

Prefer n8n over Apps Script? The [`n8n/`](n8n/) folder has three importable
workflows: the same jobs API served by n8n webhooks, an AI intake flow that
converts WhatsApp messages with Claude, and a daily pending-job reminder
email. Setup guide: [`n8n/README.md`](n8n/README.md).

## Project layout

```
app/                     Android app (Kotlin + Jetpack Compose)
  build.gradle.kts       4 product flavors: admin / cleaner / repairer / initiator
  src/main/java/com/jobregister/app/
    RoleConfig.kt        per-role visibility rules (the information boundary)
    ai/MessageParser.kt  WhatsApp text → job row (the "AI conversion" step)
    data/                local store + Google Sheets sync
    ui/                  screens; each gated through RoleConfig
backend/Code.gs          Google Apps Script: spreadsheet ↔ JSON API
n8n/                     alternative backend as importable n8n workflows
.github/workflows/       CI that builds the 4 APKs on every push
```
