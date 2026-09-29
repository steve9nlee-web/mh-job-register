# MH Job Register — where the build stands

**Version 2.6, live and in daily use.** This is the wrap-up of the Android
track: what runs, what it is built from, what it costs to operate, and how to
pick the work up in a fresh conversation.

Companion files: `SPEC.md` is what the system does, `CLAUDE.md` is how to work
on it, `docs/TRACK-COMPARISON.md` is the n8n trial.

---

## 1. It works, end to end — verified in the live sheet

Real jobs have run the whole loop, with the register showing every step:

| Job | Unit | Raised by | Approved | Started | Completed | Photos |
|---|---|---|---|---|---|---|
| J-DC17F2DB | OV-26-10 | Initiator | ADMIN 08:49 | CLEANER 08:52 | 08:53 | 1 job, 2 before, 1 after |
| J-9B082870 | OV-26-10 | Initiator | ADMIN 08:54 | CLEANER 08:56 | 09:00 | 1 job, 6 before, 12 after |
| J-FA9C071D | L-19-11 | Initiator | ADMIN | CLEANER 10:06 | in progress | 1 job, 3 before |

The `Photos` tab holds 44 images across the three kinds, each stamped and filed
in the right Drive folder. The `Services` tab carries all nine services in
alphabetical order with the correct Set A/B/C prices, and the two legacy rows
("Cleaning", "AirCond Service") were migrated away automatically.

Everything in `SPEC.md` is implemented. Nothing is waiting to be deployed.

## 2. The flow that runs

**Initiator** picks Apartment → Unit → Service → Room, adds notes, takes as
many photos as the job needs, and presses Create job. A "Job description &
pricing" button under Create shows the unit, room, notes and the full price and
scope. The job is written as `AWAITING_APPROVAL` and is invisible to every
contractor.

**Admin** sees "New job needs approval", opens it from the **To approve**
filter, and presses "Approve — send to Cleaning". That releases it to the
matching trade and stamps `approvedBy` / `approvedAt`. An admin raising a job
approves it in the same act.

**Cleaner / Repairer** gets "New job for you" and works down one screen:

1. Before photo — nothing else unlocks without it.
2. Status row — **Start** (stamps `startedAt`), **Held up** and **Not done**,
   the last two refusing to save without a typed reason.
3. After photo, then **Complete job**, which turns green the moment the after
   photo lands and stamps `completedAt` and `updatedBy`.

**Admin** then prices the job (rate card or by hand) and marks invoiced and
paid. Anything unfinished sits in the **Follow-up** filter, oldest first.

Everyone on a job sees its photos in three named groups — from the initiator,
before work, after work — with empty groups still showing their heading.

## 3. What it is built from

| Piece | What it is | Who owns it |
|---|---|---|
| 3 Android apps | Kotlin + Jetpack Compose from one codebase (`app/`): Admin, Initiator, and one Contractor app where a staff code picks Cleaner or Repairer | This repo |
| Backend | Google Apps Script web app (`backend/Code.gs`) over the spreadsheet | Steven's Google account |
| Database | Google Sheet "MH Contractors Database", 6 tabs (incl. `Staff` codes) | Steven's Google account |
| Photo storage | Two Google Drive folders, intake and work photos kept apart | Steven's Google account |
| Build | GitHub Actions, three APKs per push to `main` | This repo |
| Notifications | Android WorkManager polling, per-role rules | In the apps |

Running cost today: nothing. Google Sheets, Drive and Apps Script are free at
this volume, GitHub Actions is free for this repo, and there is no server.

## 4. The keys to the kingdom

| Thing | Value |
|---|---|
| Repo | `steve9nlee-web/mh-job-register`, branch `main` |
| Spreadsheet | `11htg51uUpOA2nH2xmR3apRA7nN-7Dn8HSy_KDn4Bl7k` ("MH Contractors Database") |
| Web app URL | `https://script.google.com/macros/s/AKfycbw4D6mscZV0q_a-gWCzvfa0rr-OV9HOes43pWkJ_S2bJprx-ZwqP8MxLvMTBMoZp6n9/exec` |
| Sync key | `MH-SYNC-2026` (in `Code.gs` and `app/build.gradle.kts`) |
| Job photos folder | `1TZZteVqmOYNGy1LtaNSN_HmMmipqAvoa` |
| Work photos folder | `1N-wODATUjCGzemtrEFLXErI-gAltRPXu` |
| APKs | GitHub → Actions → newest green run → Artifacts |

The URL and key are compiled into every APK, so a phone syncs with no setup.
Both can still be overridden per phone in Settings.

## 5. The two operations that matter

**Ship an app change.** Push to `main`; Actions builds three APKs in about three
minutes; download from the run page and install. Bump `versionCode` and
`versionName` first so the phones can be told apart.

**Ship a backend change.** Paste `backend/Code.gs` into Extensions → Apps
Script, Ctrl+S, then **Deploy → Manage deployments → pencil → Version: New
version → Deploy**. Never "New deployment" — it mints a new URL and every
installed phone goes dead. If the change touches a new Google service, run
`setupCustomerSheets` once and approve the permission screen.

Data-only changes — prices, scope text, apartments, units, services — need
neither. Edit the sheet; the phones pick it up on the next sync.

## 6. Known rough edges

- **Timestamps are inconsistent in the sheet.** Some read `2026-09-12 8:53`,
  others `2026-09-12T00:52:00.000Z`, because Sheets sometimes parses the string
  into a date. Cosmetic in the apps; ugly in the spreadsheet.
- **The first three Photos rows are shifted one column**, written before the
  `fileId` column existed. Harmless history.
- **Jobs raised before the approval gate** sit at `PENDING` with no approver.
- **Notifications are polled, not pushed.** Instant in the foreground, up to
  15 minutes when the app is closed. Real push needs Firebase Cloud Messaging
  and a `google-services.json` from a free Firebase project.
- **Approval releases a job to a trade, not to a person.** Every cleaner sees
  every approved cleaning job.
- **Rates ignore the room count.** The rate card is per category, so a 1-room
  and a 3-room Set A price the same until it is taught the room bands.
- **Release APKs are debug-signed.** Fine for sideloading, not for Play Store.

## 6a. Added in v2.7

- **Paste WhatsApp** on the New Job screen (Initiator and Admin): copied
  messages become one draft job per unit, checked against registered units
  and the Services list, with photos per draft, then raised through the
  normal approval gate and notifications.
- **One Contractor app** replaces the Cleaner and Repairer APKs. Each person
  signs in with a code from the `Staff` tab; the server holds a code to its
  trade's approved jobs, hides customer prices, and only accepts status
  fields and before/after photos from it. The shared sync key still gives
  the Admin and Initiator apps full access, as before.

## 7. The obvious next moves

1. Assign a job to a named contractor, not just a trade.
2. Price by room band, so Set A with Room 2 bills RM 70 by itself.
3. Firebase Cloud Messaging for genuinely instant notifications.
4. Invoice output — a monthly statement per apartment from the register.
5. Build Track B in n8n and run the trial in `docs/TRACK-COMPARISON.md`.

## 8. Starting a fresh conversation

Point a new session at `steve9nlee-web/mh-job-register` and open with something
like:

> This repo runs a property maintenance job system: three Android apps over a
> Google Sheet, live and in daily use at v2.6. Read CLAUDE.md, SPEC.md and
> HANDOVER.md first. I want to change <X>. Follow the existing build and
> deployment rules, and tell me plainly what I need to do on my side.

Anything that changes the workflow itself should land in `SPEC.md` in the same
breath as the code, so the n8n comparison stays honest.
