# Track A (apps) vs Track B (n8n) — trial plan

The same workflow, `SPEC.md`, built twice. This file decides what "better"
means before either side is scored, so the answer is not just whichever was
finished most recently.

## Ground rules

1. **Separate data.** Track B gets its own copy of the spreadsheet and its own
   two Drive folders. Nothing in the trial writes to the live sheet.
2. **Same week, same jobs.** Run both tracks over the same real work for a full
   week: the same units, the same services, the same staff.
3. **Same people.** The cleaner who tests Track A tests Track B. Their opinion
   is evidence, not decoration — they are the ones who use it daily.
4. **Record as you go.** Fill the scorecard during the week, not from memory
   afterwards.

## What Track B still has to answer

Track A gets these for free from being an app; n8n has to solve them somehow,
and how well it does is much of the comparison:

- **The front door.** n8n has form triggers and chat integrations, but no
  installable per-role app. Likely shape: an n8n form per role, opened from a
  bookmark, or a WhatsApp/Telegram conversation.
- **Camera and stamped photos.** The apps take the photo, shrink it, burn the
  job number and timestamp into it, and file it. In n8n this must happen
  server-side from an uploaded file, and the "camera only, no gallery" rule is
  hard to enforce in a browser form.
- **Working offline.** The apps keep working with no signal and sync later. A
  browser form does not.
- **Push to the phone.** n8n can send WhatsApp or email instantly, which may
  well beat the apps' 15-minute background check.
- **Who is using it.** The apps know their role because the role is compiled
  in. n8n needs a login, a per-role link, or a trusted phone number.

## Scorecard

Score 1–5 per row for each track, and write the reason. The reason matters more
than the number.

| What | Why it matters | Track A | Track B |
|---|---|---|---|
| Speed to raise a job on site | Done dozens of times a day | | |
| Contractor can finish a job unaided | They are not office staff | | |
| Photo evidence is reliable | It is the proof of work | | |
| Works on bad signal | Basements, lifts, older phones | | |
| Notification speed | Drives response time | | |
| Admin approval and billing | Steven's own daily work | | |
| Cost per month | Hosting, n8n licence, phones | | |
| Who can change it later | Can it be edited without a developer | | |
| What breaks when it breaks | Blast radius and recovery | | |
| Effort to add the next feature | The real long-run cost | | |

## The decision

At the end of the week, write the outcome here: which track is adopted, which
parts of the loser are worth stealing, and what is retired. A hybrid is a
legitimate answer — for example the apps for the contractors, n8n for
notifications and reporting.
