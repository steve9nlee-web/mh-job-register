# Track B — the Job Register under n8n

> **Status: early, and behind the spec.** These three workflows were written
> against the original 15-column sheet, before approval, rooms, work photos,
> timestamps and notifications existed. Read `../SPEC.md` for what the workflow
> actually has to do now, and `../CLAUDE.md` for how this track is meant to
> stay separate from the Android apps — including using its **own copy** of the
> spreadsheet and Drive folders so the trial data never mixes.

Three importable workflows replace / upgrade the Apps Script backend. The four
APKs keep working unchanged — they just point at an n8n URL instead.

| File | What it does |
|---|---|
| `jobregister-api.json` | The API the 4 apps talk to: `GET` returns all jobs, `POST` upserts one row in the Google Sheet. This is the drop-in replacement for `backend/Code.gs`. |
| `whatsapp-ai-intake.json` | POST a WhatsApp message to it → Claude extracts the job rows (dates, units, categories, status, review flags) → rows are appended to the sheet. |
| `followup-reminder.json` | Every day at 09:00, emails you a list of jobs still Pending / Waiting / Not Completed after 2 days. |

## 1. Prepare the spreadsheet

Create (or reuse) a Google Sheet with a tab named **JobRegister** whose first
row is exactly these headers:

```
id  date  unit  category  description  status  needsReview  reviewReason  remarks  customerCharge  contractorPayable  invoiced  paid  createdBy  rawMessage
```

(If you already ran the Apps Script backend, that tab already exists — reuse
it. The workflows are pre-configured for the "MH Contractors Database"
spreadsheet.)

## 2. Import the workflows

In n8n: **Workflows → Import from File** for each of the three JSON files.

## 3. Set credentials & placeholders

- **Google Sheets**: open each Google Sheets node → select/create a
  *Google Sheets OAuth2* credential. The nodes are already pointed at the
  "MH Contractors Database" spreadsheet
  (ID `11htg51uUpOA2nH2xmR3apRA7nN-7Dn8HSy_KDn4Bl7k`); change the Document ID
  only if you switch to a different spreadsheet.
- **Claude (intake workflow only)**: open the *AI Convert (Claude)* node →
  create a **Header Auth** credential with name `x-api-key` and value = your
  Anthropic API key (get one at console.anthropic.com). The workflow uses the
  `claude-opus-5` model.
- **Gmail (reminder workflow only)**: open *Email Reminder* → connect a Gmail
  credential and replace `REPLACE_WITH_YOUR_EMAIL` with your address.
- **Secret path**: in both webhook workflows, edit the webhook nodes and change
  `SECRET-CHANGE-ME` in the path to your own random string (same value in the
  GET and POST nodes of `jobregister-api`). This is what keeps strangers out
  of your billing data, since the apps don't send auth headers.

## 4. Activate & connect the apps

Activate the workflows (toggle top-right). The production URL of the API is:

```
https://<your-n8n-host>/webhook/jobregister/<your-secret>
```

Paste that URL into **Settings → Job Register sync URL** in each of the four
apps. Done — every sync pulls from the sheet, every change pushes to it.

Quick test from a terminal:

```bash
curl https://<host>/webhook/jobregister/<secret>          # -> {"jobs":[...]}

curl -X POST https://<host>/webhook/jobregister-intake/<secret> \
  -H 'Content-Type: application/json' \
  -d '{"message":"12/8 unit A-12-03 cleaning done\nB-05-11 sink leaking, plumber tmr","sender":"Steve"}'
```

## Notes

- Your n8n must be reachable from the contractors' phones — n8n Cloud or a
  small VPS works; an instance on your laptop won't.
- The intake webhook is also the natural place to attach n8n's **WhatsApp
  Business Cloud trigger** later: point the trigger's message text at the same
  Claude → Sheets chain and the daily group message flows in with no
  copy-pasting at all.
- The reminder workflow's 2-day threshold is the `DAYS_BEFORE_REMINDER`
  constant at the top of its *Find Overdue Jobs* code node.
- Apps Script (`backend/Code.gs`) and n8n can't both be "the" backend — pick
  one URL for the apps. Both write the same sheet layout, so switching later
  is just changing the URL in Settings.
