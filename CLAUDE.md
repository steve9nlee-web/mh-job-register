# MH Job Register — project guide

Property maintenance job management for MH Contractors: a job is raised for an
apartment unit, approved, done by a contractor with photo evidence, then billed.

**Read `SPEC.md` first.** It defines the workflow both implementations must
satisfy, and it is the arbiter when the two disagree.

## Two tracks, deliberately separate

Steven is running a trial: the same workflow built twice, to find out which is
better to live with. Do not merge them, and do not let one quietly become a
dependency of the other.

| | Track A — Android apps | Track B — n8n |
|---|---|---|
| Status | Built and in use | To be built |
| Front end | 4 Android APKs (Admin, Cleaner, Repairer, Initiator) | n8n forms / chat front door — still to be decided |
| Logic | Kotlin in `app/` | n8n workflows in `n8n/` |
| Backend | Apps Script `backend/Code.gs` | n8n HTTP + Google Sheets nodes |
| Data | "MH Contractors Database" sheet + 2 Drive folders | **Its own copy** — see below |

**Keep the trial clean.** Track B must not write to the live spreadsheet or the
live Drive folders. Give it a duplicate spreadsheet and duplicate folders, so
the two systems can run the same week's work side by side without corrupting
each other's data or each other's measurements. `docs/TRACK-COMPARISON.md`
holds the trial plan and the scorecard.

When work is requested, ask yourself which track it belongs to. A change to the
workflow itself belongs in `SPEC.md` and then in both.

## Track A: how to build and ship

- **Never run a local Gradle build.** `dl.google.com` is blocked in this
  environment, so the Android SDK cannot resolve. GitHub Actions is the
  compiler: push to `main`, and `.github/workflows/build-apks.yml` produces
  four APKs in about three minutes. Check the run, read the failure log when it
  is red, fix, push again.
- Before pushing Kotlin, check what can be checked without a compiler: brace
  and paren balance, that every new enum value is handled by the `when`s that
  list statuses or categories by name, and that new symbols are imported.
- Bump `versionCode` and `versionName` in `app/build.gradle.kts` for every
  user-visible change, so Steven can tell the phones apart.
- APKs are downloaded from the Actions run page (GitHub login required); the
  artifacts cannot be fetched from this session.

## Track A: how the backend is deployed

`backend/Code.gs` is pasted into the spreadsheet's Apps Script editor by hand.
The deployment discipline matters more than it looks:

- Update with **Deploy → Manage deployments → pencil → Version: New version**.
  "New deployment" mints a different URL and silently breaks every installed
  phone — this has happened three times.
- Access must be **"Anyone"**, not "Anyone with Google account", or the apps
  receive a login page instead of JSON.
- After any change that touches a new Google service, the script must be
  re-authorised: run `setupCustomerSheets` from the editor once and approve.
- The live URL is baked into the APKs as `DEFAULT_SYNC_URL`.
- `script.google.com` is blocked here, so the deployed backend cannot be tested
  from this session. Verify by simulating it (see below) or by reading the
  spreadsheet through the Google Drive connector.

## Verifying without the real thing

`Code.gs` is plain JavaScript, so it can be exercised offline: stub
`SpreadsheetApp`, `DriveApp`, `Utilities`, `ContentService` and run the real
functions against a fake sheet shaped like the live one. This has caught
header-migration and folder-routing bugs before they reached Steven. Do it for
any change to the sheet layout or the photo pipeline.

## Working with Steven

He is not a programmer. He reads results, not diffs.

- Say what changed in his terms — what he will see on the phone, what he must
  do in Google Sheets — and give the APK link every time.
- Always restate the outstanding Apps Script deploy steps; they are the usual
  reason a change "didn't work".
- When something needs his Google account (a Firebase project, a Drive folder,
  a permission), say exactly what to click and what to send back.
