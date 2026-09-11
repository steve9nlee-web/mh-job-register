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

// The Job Register spreadsheet ("MH Contractors Database"). All app data is
// stored here. When this script is pasted inside that spreadsheet the ID is
// redundant but harmless; it also lets the script run as a standalone project.
var SPREADSHEET_ID = '11htg51uUpOA2nH2xmR3apRA7nN-7Dn8HSy_KDn4Bl7k';

// Shared sync key: every app sends ?key=... and it must match this value.
// Change it here AND in the apps' Settings (or app/build.gradle.kts default)
// together. Leave '' to accept requests without a key (not recommended).
var SYNC_KEY = 'MH-SYNC-2026';

var SHEET_NAME = 'JobRegister';
var HEADERS = ['id', 'date', 'unit', 'category', 'description', 'status',
  'needsReview', 'reviewReason', 'remarks', 'customerCharge',
  'contractorPayable', 'invoiced', 'paid', 'createdBy', 'rawMessage'];

function openSs_() {
  try {
    return SpreadsheetApp.openById(SPREADSHEET_ID);
  } catch (e) {
    return SpreadsheetApp.getActiveSpreadsheet();
  }
}

function getSheet_() {
  var ss = openSs_();
  var sheet = ss.getSheetByName(SHEET_NAME);
  if (!sheet) {
    sheet = ss.insertSheet(SHEET_NAME);
    sheet.appendRow(HEADERS);
    sheet.setFrozenRows(1);
  }
  return sheet;
}

function keyOk_(e) {
  return !SYNC_KEY || (e && e.parameter && e.parameter.key === SYNC_KEY);
}

function badKey_() {
  return ContentService.createTextOutput(JSON.stringify({ error: 'Invalid sync key' }))
    .setMimeType(ContentService.MimeType.JSON);
}

/** GET -> all jobs as JSON. */
function doGet(e) {
  if (!keyOk_(e)) return badKey_();
  // Auto-create the customer database tabs on first use.
  if (!openSs_().getSheetByName(CUSTOMER_SHEET)) setupCustomerSheets();
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
  var ss = openSs_();

  var customers = [];
  var cs = ss.getSheetByName(CUSTOMER_SHEET);
  if (cs) {
    var cv = cs.getDataRange().getValues();
    for (var ci = 1; ci < cv.length; ci++) {
      if (!cv[ci][1]) continue;
      customers.push({
        apartment: String(cv[ci][0] || ''),
        unit: String(cv[ci][1] || ''),
        service: String(cv[ci][2] || ''),
        customerName: String(cv[ci][3] || '')
      });
    }
  }

  var apartments = [];
  var ap = ss.getSheetByName(APARTMENT_SHEET);
  if (ap) {
    var av = ap.getDataRange().getValues();
    for (var ai = 1; ai < av.length; ai++) {
      if (!av[ai][0]) continue;
      apartments.push({ code: String(av[ai][0]), name: String(av[ai][1] || '') });
    }
  }

  var services = [];
  var serviceInfo = [];
  var sv = ss.getSheetByName(SERVICE_SHEET);
  if (sv) {
    ensureServicePackages_(sv);
    var svv = sv.getDataRange().getValues();
    for (var si = 1; si < svv.length; si++) {
      if (svv[si][0]) {
        services.push(String(svv[si][0]));
        serviceInfo.push({ name: String(svv[si][0]), details: String(svv[si][1] || '') });
      }
    }
  }

  return ContentService.createTextOutput(JSON.stringify({
    jobs: jobs,
    customers: customers,
    apartments: apartments,
    services: services,
    serviceInfo: serviceInfo
  })).setMimeType(ContentService.MimeType.JSON);
}

function ok_() {
  return ContentService.createTextOutput(JSON.stringify({ ok: true }))
    .setMimeType(ContentService.MimeType.JSON);
}

function findRow_(sheet, col, value) {
  var v = sheet.getDataRange().getValues();
  for (var r = 1; r < v.length; r++) {
    if (String(v[r][col]) === String(value)) return r + 1;
  }
  return -1;
}

function handleCustomer_(d) {
  if (!openSs_().getSheetByName(CUSTOMER_SHEET)) setupCustomerSheets();
  var sheet = openSs_().getSheetByName(CUSTOMER_SHEET);
  var row = findRow_(sheet, 1, d.unit); // match on unit column
  if (d.action === 'delete') {
    if (row > 0) sheet.deleteRow(row);
    return ok_();
  }
  var values = [String(d.apartment || ''), String(d.unit || ''),
    String(d.service || ''), String(d.customerName || ''),
    String(d.phone || ''), String(d.remarks || '')];
  if (row > 0) sheet.getRange(row, 1, 1, values.length).setValues([values]);
  else sheet.appendRow(values);
  return ok_();
}

function handleApartment_(d) {
  if (!openSs_().getSheetByName(APARTMENT_SHEET)) setupCustomerSheets();
  var sheet = openSs_().getSheetByName(APARTMENT_SHEET);
  var row = findRow_(sheet, 0, d.code);
  if (d.action === 'delete') {
    if (row > 0) sheet.deleteRow(row);
    return ok_();
  }
  if (row > 0) sheet.getRange(row, 1, 1, 2).setValues([[String(d.code), String(d.name || '')]]);
  else sheet.appendRow([String(d.code), String(d.name || '')]);
  return ok_();
}

function handleService_(d) {
  if (!openSs_().getSheetByName(SERVICE_SHEET)) setupCustomerSheets();
  var sheet = openSs_().getSheetByName(SERVICE_SHEET);
  var row = findRow_(sheet, 0, d.name);
  if (d.action === 'delete') {
    if (row > 0) sheet.deleteRow(row);
    return ok_();
  }
  if (row < 0) sheet.appendRow([String(d.name), String(d.details || '')]);
  else if (d.details) sheet.getRange(row, 2).setValue(String(d.details));
  return ok_();
}

// Keeps the Services tab as the service-package catalogue:
// - the five packages exist and the whole list is kept in alphabetical
//   order (AirCond Chemical Overhaul, AirCond Normal Service,
//   Cleaning Set A/B/C, General, Pest Control, ...)
// - legacy entries ('Cleaning', 'AirCond Service') are removed and any
//   customer rows using them are migrated to the matching package
// - a package's details text is only (re)written while the cell is empty
//   or still a placeholder, so edits made in the sheet are never clobbered
// No-ops once the tab is in the desired state.
function ensureServicePackages_(sheet) {
  if (String(sheet.getRange(1, 2).getValue()) !== 'details') {
    sheet.getRange(1, 2).setValue('details');
  }
  var PACKAGES = [
    ['Cleaning Set A', 'Set A \u2014 Occupied Unit (Routine Housekeeping Service)\n\nPrices:\n1 x Room: RM 35.00\n2 x Room: RM 70.00\n3 x Room: RM 105.00\nYard Cleaning: RM 35.00\nWhole House incl. common area (Living Hall, Dining Area, Kitchen & Toilet): RM 120.00\n\nScope of work:\n\u2022 Sweeping and vacuuming of all accessible floor areas\n\u2022 Damp mopping of floor finishes\n\u2022 Wipe down accessible furniture and surfaces\n\u2022 General dusting of fixtures and fittings\n\u2022 Final visual inspection upon completion'],
    ['Cleaning Set B', 'Set B \u2014 Occupied Unit (Enhanced Housekeeping Service)\n\nPrices:\n1 x Room: RM 50.00\n2 x Room: RM 90.00\n3 x Room: RM 120.00\nWhole House incl. common area (Living Hall, Dining Area, Kitchen & Toilet): RM 140.00\n\nScope of work:\n\u2022 Includes all services under Cleaning Set A, plus:\n\u2022 Replacement of bed sheets and pillowcases\n\u2022 Replacement of blanket or quilt covers\n\u2022 Laundry washing of bed linens, pillowcases and blankets/quilts\n\u2022 Detailed cleaning of high-touch surfaces\n\u2022 Additional spot cleaning where required'],
    ['Cleaning Set C', 'Set C \u2014 Vacant Unit (Move-Out / Move-In Deep Cleaning)\n\nPrices:\nWhole House incl. common area (Living Hall, Dining Area, Kitchen & Toilet): RM 170.00\n\nScope of work:\n\u2022 Includes all services under Cleaning Set B, plus:\n\u2022 Comprehensive deep cleaning of the entire premise\n\u2022 Detailed cleaning of kitchen cabinets, wardrobes and internal shelving\n\u2022 Removal of stains, dirt build-up and accumulated dust\n\u2022 Disposal of general waste and unwanted items\n\u2022 Collection and storage of tenant belongings identified as Lost & Found items for the retention period in accordance with Company SOP. Unclaimed items will be disposed of upon expiry of the retention period.'],
    ['AirCond Normal Service', 'Normal Air Conditioning Servicing\n\nPrice: RM 90.00 per unit\n\nScope of work:\n\u2022 Clean and wash reusable air filters\n\u2022 Clean the indoor unit front panel and external casing\n\u2022 Vacuum clean the evaporator coil surface\n\u2022 Flush and clear the condensate drain tray and drain pipe\n\u2022 Inspect the indoor blower fan operation\n\u2022 Inspect refrigerant pipe insulation for signs of deterioration\n\u2022 Inspect electrical wiring, terminals and connection points\n\u2022 Measure and record operating temperature (return air and supply air)\n\u2022 Test thermostat and remote controller functionality\n\u2022 Inspect for abnormal noise, vibration and water leakage\n\u2022 Perform final operational and cooling performance test'],
    ['AirCond Chemical Overhaul', 'Chemical Overhaul (Indoor and Outdoor units)\n\nPrice: RM 180.00 per unit\n\nScope of work:\n\u2022 Fully dismantle the indoor unit\n\u2022 Remove the evaporator coil and blower wheel\n\u2022 Perform chemical cleaning of the evaporator coil\n\u2022 Perform chemical cleaning of the blower wheel\n\u2022 Thoroughly clean the drain tray and condensate drain pipe\n\u2022 Clean all plastic covers, louvers and casing components\n\u2022 Chemically clean the outdoor condenser coil\n\u2022 Inspect condenser fan motor and compressor condition\n\u2022 Inspect capacitor, electrical terminals and printed circuit board (PCB)\n\u2022 Reassemble all components\n\u2022 Conduct drainage flow test\n\u2022 Perform refrigerant leakage inspection\n\u2022 Measure operating current and operating temperature\n\u2022 Conduct final cooling performance and functional testing']
  ];
  var LEGACY = { 'Cleaning': 'Cleaning Set A', 'AirCond Service': 'AirCond Normal Service' };

  var v = sheet.getDataRange().getValues();
  var rows = [];
  for (var r = 1; r < v.length; r++) {
    if (v[r][0]) rows.push([String(v[r][0]), String(v[r][1] || '')]);
  }
  var byName = {};
  rows.forEach(function (x) { byName[x[0]] = x[1]; });

  var hadLegacy = Object.keys(LEGACY).some(function (o) {
    return byName.hasOwnProperty(o);
  });

  var packNames = {};
  var packRows = PACKAGES.map(function (p) {
    packNames[p[0]] = true;
    var cur = byName[p[0]];
    var keep = cur && cur !== '' && cur.indexOf('Details to be added') !== 0;
    return [p[0], keep ? cur : p[1]];
  });

  var rest = rows.filter(function (x) {
    return !packNames[x[0]] && !LEGACY.hasOwnProperty(x[0]);
  });
  var desired = packRows.concat(rest).sort(function (a, b) {
    var an = a[0].toLowerCase(), bn = b[0].toLowerCase();
    return an < bn ? -1 : (an > bn ? 1 : 0);
  });

  var changed = hadLegacy || desired.length !== rows.length;
  if (!changed) {
    for (var i = 0; i < desired.length; i++) {
      if (rows[i][0] !== desired[i][0] || rows[i][1] !== desired[i][1]) {
        changed = true; break;
      }
    }
  }
  if (!changed) return;

  if (rows.length) sheet.getRange(2, 1, rows.length, 2).clearContent();
  sheet.getRange(2, 1, desired.length, 2).setValues(desired);

  if (hadLegacy) {
    var cs = openSs_().getSheetByName(CUSTOMER_SHEET);
    if (cs) {
      var cv = cs.getDataRange().getValues();
      for (var cr = 1; cr < cv.length; cr++) {
        var svc = String(cv[cr][2]);
        if (LEGACY.hasOwnProperty(svc)) {
          cs.getRange(cr + 1, 3).setValue(LEGACY[svc]);
        }
      }
    }
  }
}

/** POST -> job upsert (default) or customer/apartment/service change. */
function doPost(e) {
  if (!keyOk_(e)) return badKey_();
  var data = JSON.parse(e.postData.contents);
  if (data.type === 'customer') return handleCustomer_(data);
  if (data.type === 'apartment') return handleApartment_(data);
  if (data.type === 'service') return handleService_(data);
  var job = data;
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

// ---------------------------------------------------------------------------
// Customer database
//
// Run setupCustomerSheets() ONCE from the Apps Script editor (select it in
// the function dropdown, press Run). It creates three tabs:
//
//   Apartments  - the apartment list (code + name). Add or delete rows freely;
//                 the Customers dropdown follows this list automatically.
//   Services    - the service type list. Add or delete rows freely too.
//   Customers   - one row per customer unit: apartment (dropdown), unit,
//                 service (dropdown), customerName, phone, remarks.
//                 Add a row: right-click a row number > Insert row, fill it in.
//                 Delete a row: right-click > Delete row.
// ---------------------------------------------------------------------------

var CUSTOMER_SHEET = 'Customers';
var APARTMENT_SHEET = 'Apartments';
var SERVICE_SHEET = 'Services';

function setupCustomerSheets() {
  var ss = openSs_();

  var apts = ss.getSheetByName(APARTMENT_SHEET);
  if (!apts) {
    apts = ss.insertSheet(APARTMENT_SHEET);
    apts.appendRow(['code', 'name']);
    apts.appendRow(['L', 'Luminari']);
    apts.appendRow(['OV', 'Ocean View']);
    apts.setFrozenRows(1);
  }

  var svcs = ss.getSheetByName(SERVICE_SHEET);
  if (!svcs) {
    svcs = ss.insertSheet(SERVICE_SHEET);
    svcs.appendRow(['service']);
    ['Cleaning', 'AirCond Service', 'Pest Control', 'General'].forEach(function (v) {
      svcs.appendRow([v]);
    });
    svcs.setFrozenRows(1);
  }

  var cust = ss.getSheetByName(CUSTOMER_SHEET);
  if (!cust) {
    cust = ss.insertSheet(CUSTOMER_SHEET);
    cust.appendRow(['apartment', 'unit', 'service', 'customerName', 'phone', 'remarks']);
    cust.appendRow(['L', 'L-19-11', 'Cleaning', '', '', '']);
    cust.appendRow(['OV', 'OV-26-10', 'AirCond Service', '', '', '']);
    cust.setFrozenRows(1);
  }

  // Dropdowns applied to whole columns, so newly added rows inherit them and
  // adding/deleting entries in Apartments/Services updates every dropdown.
  var aptRule = SpreadsheetApp.newDataValidation()
    .requireValueInRange(apts.getRange('A2:A1000'), true)
    .setAllowInvalid(false)
    .build();
  cust.getRange('A2:A1000').setDataValidation(aptRule);

  var svcRule = SpreadsheetApp.newDataValidation()
    .requireValueInRange(svcs.getRange('A2:A1000'), true)
    .setAllowInvalid(false)
    .build();
  cust.getRange('C2:C1000').setDataValidation(svcRule);
}
