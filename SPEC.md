# MH Job Register — workflow specification

This file is the **source of truth for what the system does**, independent of
how it is built. Two separate implementations are being trialled against it:

- **Track A — Android apps** (this repo's `app/` + `backend/Code.gs`)
- **Track B — n8n** (`n8n/`, to be built)

Neither track may change this document to match its own convenience. If the
behaviour should change, change it here first, then in both tracks.

---

## 1. The people and what each one may do

| Role | Purpose | Sees | Can do |
|---|---|---|---|
| **Initiator** | Raises work from the ground (site staff) | All jobs, no money | Create a job, view status and photos |
| **Admin** | Runs the business | Everything | Create, approve, review, price, invoice, manage customers |
| **Cleaner** | Does cleaning work | Approved cleaning jobs only | Start, photograph, complete; own pay |
| **Repairer** | Does aircond / plumbing / pest / general work | Approved repair jobs only | Start, photograph, complete; own pay |

Hard rules:

- A contractor **never** sees a job that has not been approved.
- A contractor **never** sees customer billing, only their own payable.
- The initiator **cannot** change a job's status. Status only ever comes from
  a contractor or the admin.

## 2. The flow, end to end

```
Initiator raises job ──► Awaiting approval ──► Admin approves ──► Pending
                                                                    │
                                            contractor takes before photo
                                                                    ▼
                                                              In Progress
                                            contractor takes after photo
                                                                    ▼
                                                               Completed
                                                                    │
                                              Admin prices ──► Invoiced ──► Paid
```

1. **Raise.** Initiator picks Apartment → Unit → Service → Room → notes, and
   takes a photo. The job is created as `AWAITING_APPROVAL`. The photo uploads
   with the job; there is no separate upload step and no gallery picker.
2. **Approve.** Only the admin. On approval the job becomes `PENDING`,
   `approvedBy` and `approvedAt` are stamped, and it becomes visible to the
   trade that matches its category. An admin raising a job approves it in the
   same act.
3. **Start.** The contractor must take a *before* photo. Only then can they
   press Start work, which sets `IN_PROGRESS` and stamps `startedAt`.
4. **Finish.** The contractor must take an *after* photo. Only then does
   "Mark done" enable, setting `COMPLETED`, `completedAt` and `updatedBy`.
   A job cannot be closed without both photos.
5. **Bill.** Admin sets customer charge and contractor payable (rate card or
   by hand), then marks invoiced and paid.
6. **Chase.** Anything not completed appears in the Follow-up view, oldest
   first.

## 3. Statuses

| Status | Meaning | Set by |
|---|---|---|
| `AWAITING_APPROVAL` | Raised, not yet released | System, on creation by a non-admin |
| `PENDING` | Approved, waiting for a contractor | Admin approval |
| `IN_PROGRESS` | Contractor has started, before photo exists | Contractor |
| `WAITING` | Held up (parts, access) | Admin / contractor |
| `COMPLETED` | Done, after photo exists | Contractor |
| `NOT_COMPLETED` | Attempted, not finished | Admin / contractor |

"Needs follow-up" = anything except `COMPLETED` (and, for chasing purposes,
excluding jobs still awaiting approval is acceptable but not required).

## 4. Categories and colours

| Category | Trade | Card colour |
|---|---|---|
| `CLEANING`, `DEEP_CLEANING` | Cleaner | Light red |
| `AIRCON` | Repairer | Light blue |
| `PEST_CONTROL` | Repairer | Light green |
| `PLUMBING` | Repairer | Light yellow |
| `ELECTRICAL`, `GENERAL_REPAIR`, `UNKNOWN` | Repairer | Neutral grey |

Category is derived from the chosen service name: contains "clean" → CLEANING,
"air" → AIRCON, "pest" → PEST_CONTROL, "plumb" → PLUMBING, else GENERAL_REPAIR.

## 5. Units and apartments

Format `PREFIX-Floor-Unit`, e.g. `L-19-11`, `OV-26-10`. Real-world text may
also write `R13-08` without the first dash; parsing must accept both.

| Code | Apartment |
|---|---|
| L | Luminari |
| OV | Ocean View |
| PV | Park View |
| R | Rubica |
| SA | Sea View |
| TA / TB | Woodsbury Block A / B |
| WA / WB | Wellesley Block A / B |

Units are added only by the admin, in the customer database. No free typing of
unit numbers when raising a job.

## 6. Services

Alphabetical, held in the `Services` tab with a details column shown as
"tap for price & details":

1. AirCond Chemical Overhaul — RM 180.00 per unit
2. AirCond Inspection & Repairs — priced per job
3. AirCond Normal Service — RM 90.00 per unit
4. Cleaning Set A — 1 room 35 / 2 rooms 70 / 3 rooms 105 / yard 35 / whole house 120
5. Cleaning Set B — 1 room 50 / 2 rooms 90 / 3 rooms 120 / whole house 140
6. Cleaning Set C — whole house 170 (vacant unit deep clean)
7. General
8. Pest Control
9. Plumbing

Room selection (`Room 1` / `Room 2` / `Room 3` / `Room All` / not applicable)
accompanies the service, because the cleaning sets are priced by room count.

## 7. Data model

### `JobRegister` tab — one row per job

`id`, `date`, `unit`, `category`, `description`, `status`, `needsReview`,
`reviewReason`, `remarks`, `customerCharge`, `contractorPayable`, `invoiced`,
`paid`, `createdBy`, `rawMessage`, `rooms`, `updatedBy`, `startedAt`,
`completedAt`, `approvedBy`, `approvedAt`

Job ids are `J-` + 8 uppercase hex characters. Implementations must add new
columns at the end and tolerate rows written before a column existed.

### `Photos` tab — one row per photo

`jobId`, `filename`, `driveUrl`, `fileId`, `uploadedAt`, `kind`

`kind` is `job` (raised with the job), `before`, or `after`.

### Customer database tabs

- `Apartments`: `code`, `name`
- `Services`: `service`, `details`
- `Customers`: `apartment`, `unit`, `service`, `customerName`, `phone`, `remarks`

## 8. Photos

- **Camera only.** Gallery uploads are not evidence of today's work.
- Every image is downscaled (max 1600px) and JPEG-compressed before upload.
- Every image is **stamped into the picture** with job number, BEFORE/AFTER
  where applicable, and `yyyy-MM-dd HH:mm`.
- Filenames: `J-XXXXXXXX_20260911_210500.jpg`, with `_BEFORE_`/`_AFTER_` for
  work photos.
- Two Drive folders, deliberately separate:
  - Job intake photos → `1TZZteVqmOYNGy1LtaNSN_HmMmipqAvoa`
  - Before/after work photos → `1N-wODATUjCGzemtrEFLXErI-gAltRPXu`
- Everyone who can open a job sees all of its photos, each labelled.
- Photos are served back through the backend, so a phone never needs its own
  Drive access, and only files inside those two folders may be served.

## 9. Notifications

Each role hears only what it needs:

| Event | Admin | Initiator | Contractor |
|---|---|---|---|
| Job raised (awaiting approval) | "New job needs approval" | — | — |
| Job approved | status line | "Approved — unit" | "New job for you" |
| Started / completed / other status | yes | yes | — (they set it) |

The first sync after an install is silent, so a new phone does not announce the
entire back catalogue.

## 10. Sync contract (Track A's backend, and the bar for Track B)

Authenticated by a shared key on the query string: `?key=MH-SYNC-2026`.

- `GET /exec?key=…` → `{ jobs, customers, apartments, services, serviceInfo, photos }`
- `GET /exec?key=…&photo=<fileId>` → `{ fileId, filename, data }` (base64 JPEG)
- `POST /exec?key=…` with a job object → upsert that row by `id`
- `POST /exec?key=…` with `{type:"photo", jobId, kind, filename, data}` → store
  the photo in the folder for that kind and log it
- `POST /exec?key=…` with `{type:"customer"|"apartment"|"service", …}` → edit
  the customer database

## 11. Non-functional expectations

- Works on cheap Android phones held by non-technical staff.
- Survives a poor connection: the phone keeps working and syncs later.
- The spreadsheet stays readable and editable by a human at all times.
- No one needs a Google account to use the system; only the owner's account
  runs the backend.
