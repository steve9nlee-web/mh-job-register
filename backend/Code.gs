/**
 * Job Register backend — Google Apps Script web app.
 *
 * This turns your existing Job Register spreadsheet into the shared database
 * for all four APKs (Admin / Cleaner / Repairer / Initiator).
 *
 * SETUP
 * 1. Open your Job Register spreadsheet in Google Sheets.
 * 2. Extensions -> Apps Script, paste this whole file, save.
 * 3. Deploy -> New deployment -> type "Web app".
 *      Execute as: Me
 *      Who has access: Anyone with the link
 * 4. Copy the web app URL and paste it into Settings -> "Job Register sync URL"
 *    in each app.
 *
 * The script creates/uses a sheet tab named "JobRegister" with these columns:
 * id | date | unit | category | description | status | needsReview |
 * reviewReason | remarks | customerCharge | contractorPayable | invoiced |
 * paid | createdBy | rawMessage
 */

var SHEET_NAME = 'JobRegister';
var HEADERS = ['id', 'date', 'unit', 'category', 'description', 'status',
  'needsReview', 'reviewReason', 'remarks', 'customerCharge',
  'contractorPayable', 'invoiced', 'paid', 'createdBy', 'rawMessage'];

function getSheet_() {
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  var sheet = ss.getSheetByName(SHEET_NAME);
  if (!sheet) {
    sheet = ss.insertSheet(SHEET_NAME);
    sheet.appendRow(HEADERS);
    sheet.setFrozenRows(1);
  }
  return sheet;
}

/** GET -> all jobs as JSON. */
function doGet() {
  var sheet = getSheet_();
  var values = sheet.getDataRange().getValues();
  var jobs = [];
  for (var r = 1; r < values.length; r++) {
    var row = values[r];
    if (!row[0]) continue;
    var job = {};
    for (var c = 0; c < HEADERS.length; c++) {
      job[HEADERS[c]] = row[c];
    }
    job.needsReview = row[6] === true || row[6] === 'TRUE' || row[6] === 'true';
    job.invoiced = row[11] === true || row[11] === 'TRUE' || row[11] === 'true';
    job.paid = row[12] === true || row[12] === 'TRUE' || row[12] === 'true';
    job.date = formatDate_(row[1]);
    job.customerCharge = row[9] === '' ? null : Number(row[9]);
    job.contractorPayable = row[10] === '' ? null : Number(row[10]);
    jobs.push(job);
  }
  return ContentService.createTextOutput(JSON.stringify({ jobs: jobs }))
    .setMimeType(ContentService.MimeType.JSON);
}

/** POST {job} -> upsert one row by id. */
function doPost(e) {
  var job = JSON.parse(e.postData.contents);
  var sheet = getSheet_();
  var values = sheet.getDataRange().getValues();
  var rowIndex = -1;
  for (var r = 1; r < values.length; r++) {
    if (values[r][0] === job.id) { rowIndex = r + 1; break; }
  }
  var row = HEADERS.map(function (h) {
    var v = job[h];
    return v === null || v === undefined ? '' : v;
  });
  if (rowIndex === -1) {
    sheet.appendRow(row);
  } else {
    sheet.getRange(rowIndex, 1, 1, HEADERS.length).setValues([row]);
  }
  return ContentService.createTextOutput(JSON.stringify({ ok: true }))
    .setMimeType(ContentService.MimeType.JSON);
}

function formatDate_(v) {
  if (Object.prototype.toString.call(v) === '[object Date]') {
    return Utilities.formatDate(v, Session.getScriptTimeZone(), 'yyyy-MM-dd');
  }
  return String(v);
}
