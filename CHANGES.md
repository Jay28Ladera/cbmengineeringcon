# CBM portal — fixes, speed and redesign (Nov 2026)

## Broken pages, links and modals (fixed)

| Problem | Cause | Fix |
|---|---|---|
| 35 of 41 sidebar links returned 404 (Purchasing, Inventory, Accounting, HR, Reports, Admin, Subcontractors, header "Settings") | No controllers for those modules; `/Admin/Users` pointed at a URL that didn't exist | `ModuleController` serves an "in preparation" page for each planned module. Users → `/users`, Settings → `/profile`, Quotation/Job Order listings → the real lists |
| Modals unstyled or not opening after clicking through the sidebar | Sidebar navigation swaps only `<main>`; each page kept its CSS and scripts in its own `<head>`, so they were missing | One stylesheet (`css/app.css`) and one set of scripts load on every page via `fragments/layout.html`. All modals live inside `<main>` and are driven by delegated scripts |
| User-management modals and the header dropdown misbehaved | Bootstrap was loaded twice (5.3.0 from a CDN + 5.3.3 webjar) | Bootstrap loads once; user modals are now native `<dialog>`s |
| Deleting a user did nothing (404) | Form posted to `POST /users/delete`, which didn't exist | Endpoint added; you cannot delete yourself |
| Edit user broke on names with apostrophes | Values were injected into an inline `onclick` | Values passed via `data-*` attributes |
| Public "Request a quote" form never reached the system | Only produced an email draft; `/inquiries/public` required login | Form posts to `/inquiries/public` (public, CSRF-protected, duplicate check, hidden spam-trap field). Requests appear in Clients |
| Landing page cut off halfway for new visitors | CSRF token created after the response had started | Token requested in `<head>` |
| Second "forgot password" request crashed | One-token-per-user constraint | Old token removed first |
| Password reset emails linked to sistersofmaryschool.online and used SMS branding | Copied from the school project | Links use the current site address (or `app.base-url`); CBM wording |
| Approving a user crashed if email sending failed | `EmailService` threw | Email failures are logged and reported, never block the action |
| Changing your username in My Profile logged you out | Session still held the old username | Session refreshed after saving |
| Profile page had no sidebar | Missing include | Uses the shared layout |
| Any authenticated user could open `/users` and approve accounts | No role rule | `/users/**` and `/Admin/**` are admin-only |
| Missing records showed a 500 error | `orElseThrow()` | Proper 404 page |

## Loading speed

- **No more 3 user queries per page**: header and sidebar details come from the signed-in session (`GlobalModelAttributes`).
- **No per-row queries on lists**: Quotations and Job Orders fetch client and line items in one query (`@EntityGraph`), plus `default_batch_fetch_size`. With the database on Neon (US East), each avoided round trip saves roughly 150–250 ms from the Philippines.
- **Sidebar navigation fetches only the page body** (`X-App-Navigation` header → `th:fragment="page"`): 3–5× smaller responses. Links are prefetched on hover, with a progress bar while loading.
- **gzip compression** on, and **one-year browser caching** for CSS/JS/fonts/images with content-hashed URLs, so changes still show immediately.
- **No CDN requests**: fonts and icons are served by the app (`static/vendor`).
- Logo is 21 KB (was 228 KB); ~9 MB of unused school images, CSS, JS and sounds removed.
- SQL logging off; Thymeleaf template cache on (DevTools still turns it off during development).

## Design

Navy/steel palette with CBM red as the accent, IBM Plex Sans with Barlow Condensed headings and IBM Plex Mono for document numbers and amounts. The dashboard shows live pipeline counts. Quotation and job order pages are print-ready documents with a workflow stepper and "next step" actions. Sign-in pages and the public site follow the same design.

## Still to do

- **Rotate the secrets in `application.properties`.** The Neon password, Cloudinary key/secret, Resend key and admin password are stored in the file and in git history. Move them to environment variables.
- `resend.from.email` is still `john@sistersofmaryschool.online`; set a CBM sender domain verified in Resend.
- Unused school DTOs in `dto/` can be deleted.

---

# Organizational chart (Nov 13, 2026)

**Human Resources → Organizational Chart** (`/organization-chart`), also linked from the dashboard.

- **Who sees what:** everyone signed in sees published positions. Admins (role `admin`) also see drafts and can add, edit, delete and arrange.
- **Drag boxes**, and the connector lines follow. Lines turn blue while you drag. If a box is placed beside or above its supervisor, the line curves.
- **Move with branch** (on by default) moves a supervisor together with everyone under them.
- **Saving the arrangement:** an admin's dragging saves automatically for everyone ("Layout saved"). Other users can drag to look around, but it isn't saved. **Reset layout** returns to the automatic arrangement.
- **Canvas:** drag empty space to pan. Ctrl/⌘ + scroll or pinch to zoom, or use the − / % / + buttons. **Fit** shows the whole chart. Alt + arrow keys nudge the selected box.
- **Department colours** appear on the top stripe, avatar, title pill and tint. The legend shows a count per department; click one to highlight it, and **Show all** to clear.
- **Large teams:** a supervisor with 4 or more reports who have nobody under them shows them as a vertical list. Any branch can be collapsed with the − / + button under its box.
- **Search** by name, position, department, site or trade; Enter jumps to the next match.
- **Staff profile** (click a box): profession/trade, PRC licence or TESDA certificate with expiry (an expired licence is flagged in red, with ⚠ on the box), site, email/phone links, date hired, reports-to, direct reports and dotted-line reports.
- **Add/edit dialog:** vacant positions, dotted-line "also reports to", login-account link (uses their profile photo), photo upload, sibling order, draft/published, and resigned with date. Resigned staff leave the chart and can be restored from **Resigned**.
- **Safety checks:** a position can't report to itself or to someone below it. Employee IDs must be unique. A position with people under it can't be deleted.

**New files:** `entity/OrganizationNode.java`, `entity/OrganizationReporting.java`, their repositories, `controller/OrganizationChartController.java`, `service/PhotoStorageService.java`, `templates/organization-chart.html`, `static/js/org-chart.js`.
**Changed:** `css/app.css` (chart styles; focused inputs stay white), `fragments/layout.html` (loads org-chart.js), `fragments/sidebar.html`, `home.html`.

The tables `organization_node` and `organization_reporting` are created automatically on startup (`ddl-auto=update`).

**Photos:** uploaded to Cloudinary (folder `cbm/org`, cropped to the face) when the Cloudinary keys are set. Render erases files saved to its disk on every deploy, so this is what keeps photos. Without Cloudinary they're saved to `uploads/org/`. Profile photos (My Profile) still save to the local disk, so they will disappear on Render redeploys.

---

# Staff accounts and department access (Nov 13, 2026)

## Login accounts from the org chart
- Click a person's box → **Create login account**. The account is created and linked to the box, and a one-time activation link is emailed to the address on the box. The username is suggested from the name (e.g. `mila.santos`).
- The person opens the link (`/activate`), can change the username, and sets a password. The link then stops working.
- If the email fails, the link is shown with a **Copy link** button so you can send it by Messenger or SMS. **Resend link** issues a fresh link and the old one stops working.
- The profile box shows the account state: no account / invited / active (with their access) / disabled. **Manage access** opens them on Users.
- **Users → Staff from org chart** lists everyone on the chart who has no active account yet, with Create account / Resend link.
- **Marking someone Resigned** disables their login and signs them out on their next click. This doesn't apply to your own account or the last admin.

## Access by department
- Modules: Inquiry, Implementation, Purchasing, Inventory, Accounting, Human Resources, Reports. Dashboard, Organizational Chart and My Profile are open to everyone; Administration is admins only.
- **Administration → Department Access** is a table where you tick each department's modules. Departments come from the org chart. Unsaved ones show suggested ticks marked *New*.
- **Users → Edit** (the Access section):
  - **Administrator:** full access. Making someone admin asks for confirmation. You can't remove your own admin rights or the last admin.
  - **Follow department** (default): the person gets their department's modules.
  - **Choose modules for this person:** your own selection, starting from the department's modules.
- **Enforcement:** the menu only shows allowed modules, and typing a blocked URL shows the "No access" page.
- **When changes apply:** access changes, making someone admin, and disabling all take effect on the person's next click. They don't need to sign in again.
- **Existing accounts not linked to anyone on the chart** keep access to all modules, so nobody gets locked out. The Users page flags them as "Not on chart". Link them from their box (Edit → Login account) to make department access apply.

New: `AccessModule`, `DepartmentAccess`, `UserAccess`, `StaffActivation` (entities + repositories), `AccessService`, `StaffAccountService`, `AccessInterceptor`, `PrincipalRefreshFilter`, `ActivationController`, `DepartmentAccessController`, templates `activate.html`, `department-access.html`, `fragments/invite-result.html`.
Changed: `UserController`, `OrganizationChartController`, `SecurityConfig`, `WebConfig`, `GlobalModelAttributes`, `OrganizationNodeRepository`, `user-management.html`, `organization-chart.html`, `home.html`, `login.html`, `fragments/sidebar.html`, `fragments/header.html`, `js/users.js`, `js/org-chart.js`, `css/app.css`, `application.properties` (`server.forward-headers-strategy`).
New database tables are created automatically on startup.

# HR payroll (Nov 13, 2026)

Built from the Excel payroll (`OCT_26_TO_NOV_10_2026_FOR_INPUT_HR.xlsx`): MASTER → DTR → DEDUCTIONS / RECEIVABLE → PAYLIST → PAYSLIP / FOR PRINTING. Everything is under **Human Resources** and needs the HR module (department access).

| Page | What it does |
|---|---|
| **Employees** `/HR/Employees` | Master list. Everyone named on the org chart is added automatically; name, position, department, employee ID and date hired follow their box, and resigned people become inactive. HR adds people not on the chart (site crews, drivers). If someone added in HR later appears on the chart with the same name or employee ID, the records are linked instead of duplicated. Per employee: payroll group (Cebu Office, Cebu Site, …), status code (R/PR/PB/PT/SC), daily or monthly rate, standard SSS / PhilHealth / Pag-IBIG and which cut-off takes them. Rate changes are logged with date and reason. |
| **Payroll Periods** `/HR/PayrollPeriod` | Cut-offs. "New period" creates the next one (26–10, then 11–25). Lock / unlock, change dates, delete. |
| **Time Sheets** `/HR/TimeSheets` | Same grid as the DTR sheet: REG / OT / L per day, Sundays and holidays highlighted, ND hours and remarks per employee (replaces the NOTES sheet). Live totals (days, OT, Sunday, lates, absences). Paste a block straight from Excel, arrow keys / Enter to move, "Fill 8 hrs" for empty working days, Ctrl+S to save, filter by group. Read-only once locked. |
| **Pay Sheets** `/HR/PaySheets` | The PAYLIST, grouped with subtotals. Click a row for the full breakdown and to add incentives, bonuses, 13th month, commission, SSS/PhilHealth/Pag-IBIG, cash advance and other lines. Print payslips (4 per A4, like the PAYSLIP sheet), signature sheet, Excel export. **Lock payroll** freezes every pay line so later rate or rule changes don't alter released payslips. |
| **Loans & Deductions** `/HR/Loans` | Loans, cash advances, sinking fund: total, amount per cut-off, start date. The installment shows on each pay sheet (not in a cut-off with no pay) and comes off the balance when the payroll is locked; unlocking puts it back. |
| **Holidays** `/HR/Holidays` | Regular (2X) and special (30%) holidays per year. |
| **Pay rules** `/HR/PayrollSettings` | The multipliers, grace period, hours per day, company name on payslips. |

## Formulas (same as PAYLIST)

- rate/hr = rate/day ÷ 8
- Daily-rate: basic = rate/day × days worked (REG hours ÷ 8, Sundays excluded). Monthly-rate: basic = monthly ÷ 2, less absent days × rate/day (rate/day = monthly × 12 ÷ 313 if none is set)
- OT = rate/hr × 1.25 × hours · Sunday = rate/day × 1.3 × days · Sunday OT = rate/hr × 1.3 × hours
- Special holiday worked = +30% of rate/day · regular holiday worked = +100% · night diff = rate/hr × 10% × hours
- Lates = minutes × (rate/hr ÷ 60) × 1.3
- Absences count on Monday–Saturday dates that aren't holidays

## Differences from the Excel file

- The late grace period is a setting (default **0**, the same as the Excel formula, which deducts every minute typed). Set it to 15 under Pay rules if HR will type the actual minutes late.
- Holidays are excluded from "absent" for monthly-rate staff (in Excel they counted as absences unless HR adjusted it).
- The Excel payslip had wrong cell references (e.g. one slip's absent count came from another employee's row, another showed the Sunday pay of the employee above). Payslips are now generated, so this can't happen.

## Files

- New: `entity/Employee, EmployeeRateChange, Holiday, PayrollPeriod, TimeEntry, TimesheetRow, PayAdjustment, DeductionPlan, PayrollSettings, PayrollResult`; matching repositories; `payroll/PayItem, PayLine, PayrollCalculator, PayrollService, PayrollExcelExporter`; `controller/HrEmployeeController, HrPayrollController`; templates `hr-*.html`; `static/js/payroll.js`.
- Changed: `ModuleController` (HR pages removed from "in preparation"), `fragments/sidebar.html`, `fragments/layout.html` (loads payroll.js), `css/app.css` (HR section at the end).
- Tables are created by Hibernate (`ddl-auto=update`), all prefixed `hr_`.

# Biometric devices and time logs (Nov 14, 2026)

The two "Soon" pages under Human Resources now work and feed the time sheet:
**machine → Time Logs → Time Sheet → Pay Sheet → payslips.**

## Biometric Device `/HR/BiometricDevice`
- ZKTeco and compatible machines push punches by themselves (ADMS / "Cloud Server" protocol, endpoint `/iclock/…`). On the machine: COMM. › Cloud Server Setting → server address = the portal's domain, port 443, HTTPS on, domain name on. The page shows the exact values.
- A new machine appears as **waiting for approval**. Nothing is accepted until HR approves it, so a stranger can't send fake punches. Its stored logs stay on the machine until then.
- Online/offline status, last check-in, last punch received, punches saved, users on the machine. Actions: edit, ask for all logs again, pause, remove.
- Names typed on the machine are captured so device IDs can be matched to employees.
- Older machines without HTTPS can't reach an HTTPS-only site such as Render. For those, use the file upload below.

## Time Logs `/HR/TimeLogs`
- **Daily attendance**: everyone's time-in and time-out for a day, punches, REG / OT / late, and status (Present, Late, No time-out, No logs, Not enrolled), with counts at the top.
- **All punches**: filter by dates and employee; shows where each came from (machine, uploaded file, manual, and who added it); remove a wrong punch.
- **Unmatched IDs**: device user IDs not linked to an employee yet, with the name from the machine and a suggestion. "Link by employee ID / name" does the obvious ones automatically.
- **Upload file**: `attlog.dat` from the machine's USB, or CSV / Excel exports from ZKTime, ZKBio or Attendance Management. Duplicates are skipped, so uploading the same file twice is safe.
- **Add punch**: for a forgotten time-out or work away from the machine. A reason is required, and the punch is marked as manual.
- **Send to time sheet**: writes REG / OT / late per day into an open payroll period, either filling only empty days or replacing days that have logs. Days with only one punch are skipped and counted so HR can fix them first. This is also on the time sheet as **From time logs**.

## How punches become hours (Pay rules › Work schedule)
- Default shift 8:00 AM to 5:00 PM, with a 12:00 to 1:00 break. All of it can be changed.
- First punch = in, last punch = out. Punches less than a minute apart count once.
- **Late** = minutes after the shift start (morning arrivals). Lates are charged separately, so they don't also reduce REG.
- **REG** = hours inside the shift, less the break, in half-hours, up to 8. Leaving early or a half-day reduces it.
- **OT** = time after the shift end in 30-minute blocks (6:40 PM out = 1.5 h). It can be turned off if OT must be approved first.

## Other
- Employees have a **Biometric ID** field (their user ID on the machine). It must be unique, and "0015" and "15" count as the same ID.
- The app now runs on Philippine time (`Asia/Manila`, override with the `APP_TIMEZONE` env var). Render's servers use UTC, so times shown (lock times, device check-ins) were 8 hours off before.
- Upload limit raised to 20 MB.
- New: `entity/BiometricDevice, AttendanceLog, BiometricUser`, `payroll/AttendanceService`, `controller/AdmsController, HrAttendanceController`, `hr-devices.html`, `hr-timelogs.html`. Changed: `Employee` (biometric_id), `PayrollSettings` (schedule), `SecurityConfig` (`/iclock/**` open to machines, no CSRF), sidebar, time sheet, pay rules, employees.

# Speed with a lot of data (Nov 14, 2026)

Tested with 150 employees, a year of biometric punches (~190,000), 23 payroll periods (~45,000 time-sheet days), and the database 50 ms away (like Render in Oregon and Neon in Ohio).

| | Before | After |
|---|---|---|
| HR pages (Employees, Time Sheets, Pay Sheets, Time Logs…) | 0.7–1.5 s | 0.2–0.8 s |
| Send logs to time sheet (~1,900 days) | 107 s | 1.4 s |
| Machine uploads 2,880 punches | 298 s | 1.0 s |

What changed:
- Time-sheet saves, "Send to time sheet", machine uploads, file imports and locking now write in **one batched statement** instead of one query per row. Only cells that actually changed are written.
- Each HR page runs its queries in one transaction (fewer round trips). The org chart sync uses 2 queries instead of one per person. Payroll Periods, Loans and Biometric Device count with one query instead of one per row.
- Machine uploads no longer load the existing punches into memory; duplicates are skipped by the database (`on conflict do nothing`).

**Biggest remaining factor: the distance between Render and Neon.** The database is in AWS us-east-2 (Ohio). If the Render service is in another region, every query adds ~50 ms or more. Put the Render web service in **Ohio (US East)**: pages then load in about 0.05–0.15 s. (Render's region is chosen when the service is created; to change it, create a new web service in Ohio from the same repo and move the domain.)

# Purchasing and inventory (Nov 14, 2026)

The "Soon" pages under Purchasing and Inventory now work and are connected:
**canvass → purchase order → stock receiving → materials stock → issuance to a job order → job order material cost.**

## Purchasing
- **Suppliers**: directory with contact, TIN, payment terms, what they supply, and how much was ordered from each.
- **Canvasses** (CV-2026-0001…): list the items, add the suppliers to compare, and type their unit prices in a grid. The lowest price wins each item unless you pick another. "Approve winners", then "Create purchase orders" makes one draft PO per winning supplier, with their items and prices. Reopen is possible until the POs are made.
- **Procurements** (PO-2026-0001…): draft → placed → partly received → received (or cancelled). For a job order or for warehouse stock. Items can be materials or free text. VAT/tax %, delivery address, terms. Printable PO with signature lines. "Back to draft" and "Cancel" are blocked once something was received.
- **Market prices**: every price seen is recorded automatically (canvass quotes, placed POs, deliveries), plus prices typed in by hand. The page shows the latest, lowest and highest price per item and who offered the lowest, with the full history per material.

## Inventory
- **Categories** and **Materials** (MAT-0001…): unit, category, reorder level, location, opening stock and cost. On-hand quantity and **moving-average cost** are computed from movements, never typed in. Materials at or below their reorder level are flagged. Each material has a stock card listing every movement with its running balance.
- **Stock Receiving** (RR-…): receive a PO in full or in part, with DR and invoice numbers. The PO's received quantities and status update. Free-text PO items are mapped to a material when received. Option: "Delivered to site: issue straight to the job order". Receipts without a PO are allowed. Voiding a receipt takes the stock back out, but is refused if the stock was already used.
- **Stock Issuance** (IS-…, new page): materials out of the warehouse, normally to a job order, at average cost. That cost is charged to the job order. It can't issue more than what's on hand (the material row is locked, so two people can't take the same stock). Voiding puts the stock back and removes the cost from the job.
- **Stock Adjustment** (ADJ-…): opening balance, recount, damaged, lost, returned from site (reduces that job's material cost), other. A note is required.
- **Tools & Equipment**: register by asset tag. Issue to an employee or anyone else, for a job order, with a due date. Return with the condition; "needs repair" sends the tool to maintenance. Overdue returns and maintenance due within 7 days are counted and flagged. Full history per tool.

## Job orders
The job order page has a **Purchases & materials** panel: material cost issued, PO amount placed, contract amount, tools on site, and the POs and issuances with links, plus "New purchase order" and "Issue materials" buttons.

## Rules
- Costing is moving average: a delivery re-averages what's on hand, while issues and write-offs leave at the current average.
- Deliveries go into stock. A job order is charged only when materials are issued to it (or received with "issue straight to the job order").
- Numbers run per year: CV-, PO-, RR-, IS-, ADJ-2026-0001.
- Access follows the existing departments: /Purchasing pages need Purchasing, /Inventory pages need Inventory.

New code: package `com.asmsi.admin_system.inventory` (entities, repositories, `InventoryService`, `PurchasingService`, controllers), templates `pur-*.html`, `inv-*.html`, `static/js/inventory.js`. Changed: sidebar (new Stock Issuance link, Soon removed), `ModuleController`, `JobOrderController` + `job-order-detail.html`, `app.css`, `layout.html`. Tables `pur_*` and `inv_*` are created automatically.

## Canvass: compare supplier quotes and pick the cheapest

- **Add supplier quote** form per supplier: pick an existing supplier or type a new one (added to the directory), their quote no., quote date, delivery days, valid-until, terms, and a price per item with a live total. Blank price = they don't carry it. **Edit** reopens the quote with the prices filled in.
- **Summary strip**: chosen total, cheapest possible, savings against the highest quotes, and the cheapest supplier who quoted every item (for one-supplier buying).
- **Supplier cards**: items quoted, their total, delivery, terms and how much is chosen from them. **Use for all** gives that supplier every item they quoted.
- **Comparison table**: each price shows the line amount, a **Cheapest** tag, **+x%** above the cheapest, and **Chosen**. Click a price to choose that supplier for the item. **Choose cheapest for every item** resets all picks.
- Items with no manual pick follow the cheapest automatically, so a new cheaper quote moves them.
- Saving a quote updates its market-price record instead of adding a duplicate.
- New columns on `pur_canvass_supplier`: `quote_ref`, `quote_date`, `delivery_days`, `valid_until` (added by Hibernate `ddl-auto=update`).

## Quotation costing and the job order material budget

**Quotations**
- Each line has a type: Material, Labor, Subcontract, Equipment or Other.
- A Material line can pick an inventory material. Description and unit fill in, and the line shows the latest supplier price, the lowest price in the last year, and the stock on hand with its average cost.
- Each line has an internal "Our cost" strip with unit cost and markup %. Markup sets the price; typing a price recalculates the markup. A default "Markup on cost" with "Apply to all lines" is saved on the quotation.
- The costing panel shows cost by type, total cost, selling price (after discount, before VAT), gross profit and margin, and warns when lines have no cost. It also appears read-only after the quote is sent.
- Cost, markup, type and the material picker are hidden when printing; the client sees description, qty, unit, price and amount only.
- New columns: `quotation_line_items.kind`, `material_id`, `unit_cost`; `quotations.markup_pct` (added by `ddl-auto=update`). Old lines read as type Other with no cost.

**Job orders**
- Purchases & materials now opens with **Material budget** (the quotation's material cost) vs **Materials issued**, with a progress bar that turns red when over budget.
- **Quoted materials** table per material: quoted qty, budget, issued (net of returns and voids), in stock, on order for this job (placed POs not yet received), and **to buy** = quoted − issued − in stock − on order. Free-text material lines show their full qty.
- **Start canvass** (Purchasing access) creates a draft canvass for the job order with the to-buy items and quantities, then opens it. If the job already has a canvass, it asks first. The job order lists its canvasses as chips.

## Labor priced as a crew; one-line forms with commas

- A **Labor** line's cost is **workers × days × rate per day** instead of a single unit cost. The line shows the man-days and the crew cost; markup and price work as before. Switching a line to Labor sets qty 1 lot if empty.
- **HR rate…** picks the average daily rate per position from active daily-paid employees in HR (e.g. "AC Technician · ₱640.17 avg of 30").
- Saving with only some of workers/days/rate filled is refused with a message naming the line. Leaving all three blank keeps the old single unit cost.
- The read-only costing table shows e.g. "3 worker(s) × 5 day(s) × ₱640.17/day = 15 man-days".
- New columns: `quotation_line_items.crew_size`, `work_days`, `daily_rate`.
- **Fix:** a form posting a single line split a description containing a comma into two values (Spring's String→List conversion), which misaligned the line columns. Affected one-line quotations, canvasses, POs, receipts and issues. `SingleValueListConverter` (registered in `WebConfig`) keeps a single value whole for `List<String>` parameters.

## Quotation approval (CEO signature) and client e-signing

**Flow:** Draft → **Submit for approval** → CEO **Approve** (or **Return for changes** with a note) → **Send to client** → client **accepts and signs** or **declines** online → Create job order.

- **Approvers:** Users › Edit › "Can approve quotations" (badge "Approver" in the list). Stored in table `quote_approver`. While nobody is ticked, administrators approve.
- **Signatures:** My profile › My signature: draw it (mouse, pen or finger) or upload a PNG/JPG. Table `user_signature`. Approving needs a saved signature; it is **copied onto the quotation** when approved, so a later change of signature does not alter past quotations.
- **Approval:** submitting emails the approvers; approving or returning emails the preparer. The approver's position comes from their org chart box (else "Authorized signatory"). Prices are locked from submit; "Return for changes" / "Back to draft" reopens them and clears the approval.
- **Send to client** (approved only): email with summary and a "View and sign the quotation" button, CC prefilled with the approver(s), reply-to = sender, valid 30 days (editable). If the email cannot be sent, nothing changes. "Mark as sent without email" + **Client link** (Copy / Preview) for Viber, Messenger or hand delivery.
- **Client page `/q/{token}`** (no login, random 32-character link, not indexed): the quotation with the CEO's signature, Print/PDF, **Accept and sign** (name, position, drawn signature or uploaded signed copy PDF/JPG/PNG ≤ 8 MB, consent box) or **Decline** with reason. Records time, IP and browser. Staff (sender + approvers) get an email. Expired or answered links can't be signed again; visits are counted ("Opened 3 times").
- Internal page: 5-step progress, client link box, accepted/declined details, link to the uploaded signed copy (`/Quotations/{id}/signed-copy`), and the signature block (CBM approver + client conforme) on screen and in print.
- Recording a client's answer by hand (Accepted / Rejected) still works for quotations answered by phone or on paper.
- New table `quotation_signoff`; new statuses `FOR_APPROVAL`, `APPROVED`.
- **Email settings:** `RESEND_API_KEY`, `RESEND_FROM`, `QUOTE_MAIL_FROM` environment variables (the properties file keeps the old values as fallback). Set `app.base-url` (or rely on the proxy headers) so links in emails point to the live site.

## Approvals page for the CEO

- **Approvals** in the sidebar (only for approvers), with a red count of quotations waiting. The count is read on full page loads only, in one query (`QuotationRepository.approvalCounts`).
- `/Approvals` lists every quotation waiting for approval, oldest first: number, project, client, who submitted it and when, **total, our cost, gross profit and margin**, warnings for lines without cost and for an earlier return note, and the items (type, qty, price, amount, cost, markup, labor crew) behind "Show the items".
- **Approve** and **Return for changes** (with a note) work right on the list and come back to it; "Open the full quotation" for the detailed view.
- "Your recent decisions" shows what this approver approved or returned and where each quotation is now.
- The approval-request email now opens the Approvals page.
- Non-approvers opening `/Approvals` are sent to the dashboard with an explanation.

## Approve & send to client

- The approver now has two buttons (on Approvals and on the quotation): **Approve** (the preparer sends it later, as before) and **Approve & send to client**.
- Approve & send opens a dialog prefilled with the client's email, the preparer on CC, the usual message and 30 days' validity; on confirm it approves (signature stamped) and emails the client the "View and sign" link in one step. The preparer gets no separate "approved" email, since they are on the CC.
- If the client email can't be sent, the approval still stands and the quotation stays Approved, with a message to use "Send to client" again. A missing client email is refused before anything is approved.
- New endpoint `POST /Quotations/{id}/approve-send`; dialog fragment `quote-doc :: approveSendDialog`.
- When an email to the client can't be sent, the message now gives the reason: "email is not set up here (RESEND_API_KEY is empty)" or Resend's own error with the sender address used (e.g. domain not verified, invalid recipient). The log line includes the sender too.

## Canvass sheet as on paper (form CBM-CD-006) with sign-off in the portal

**Numbering:** canvass numbers are now the plain running "Canvass form seq #", continuing the paper series from **2286** (setting `app.canvass.first-number`, env `APP_CANVASS_FIRSTNUMBER`). Older CV-2026-#### canvasses keep their numbers.

**New fields**
- Canvass: **MR #** (new-canvass form and the Details box of a draft), shown in the list, header, approvals and printout.
- Item: **Remarks** (REMARKS column).
- Per supplier quote: **VAT / Non-VAT**, **mode of payment** (Cash/Cheque), **terms** (on delivery, 30, 60, 90 days), **PDC**; per item **Brand** and **N/A** ("NOT AVAILABLE": no price, never chosen). The edit dialog refills all of these. POs made from the canvass carry MR #, payment and VAT in their notes.

**Sign-off** (replaces "Approve winners")
- **Submit for approval** checks every item has a price, fixes the chosen suppliers and locks the canvass.
- **Approved by** (Office Engineer/QS) → **Noted by** (Project/Operations Manager) → status APPROVED, purchase orders can be made → **Processed by** (Finance), signed when the payment is processed.
- Who signs each step: Users › Edit › "Signs canvass sheets" (three checkboxes; badge "Canvass: approves, notes…" in the list). While nobody is set for a step, administrators sign it.
- Each signer gets an email and sees the canvass on **Approvals** (chosen total, cheapest possible, savings, each item's chosen supplier, brand and price, "View canvass sheet"), and signs with their saved signature (copied onto the canvass). Sign, return and view live under `/Approvals/canvass/{id}/…` so Finance can sign without Purchasing access.
- **Return for changes** (before Noted) sends it back to draft with a note and clears the signatures; **Reopen** of an approved canvass without POs also clears them.
- The sidebar Approvals count includes canvasses waiting for your step.

**Printout** — "Print canvass sheet": landscape A4 in the company format: letterhead (Cebu/Manila addresses, contacts, emails, motto — edit in `pur-canvass-print.html`), DATE / MR # / PROJECT NAME / CANVASS FORM SEQ #, REMARKS · DESCRIPTION · QTY · UNIT, four suppliers per page (Brand · Unit cost · Amount, coloured headers), chosen prices highlighted, NOT AVAILABLE in red, TOTAL with VAT/NONVAT, Approved/Noted/Processed By with signatures and "via portal" date and time, mode of payment, terms (30/60/90 highlighted), PDC, and code CBM-CD-006. More than four suppliers continue on the next page.

New tables: `pur_canvass_signoff`, `pur_canvass_signer`; new columns on `pur_canvass` (mr_no), `pur_canvass_item` (remarks), `pur_canvass_quote` (brand, not_available), `pur_canvass_supplier` (vat_inclusive, payment_mode, term_days, pdc).
- Fix: Approvals › canvass card showed "—" for the chosen supplier (`supplierMap[w]` read "w" as a literal key; now `supplierMap.get(w)`).

# Accounting phase 1: ledger, supplier bills and payments (Nov 15, 2026)

A double-entry general ledger behind everything that moves money or stock. Every posting is checked (debits = credits) before it is saved; nothing is deleted, a void posts a reversing entry.

## The purchase-to-pay flow

1. **Canvass → PO** as before. The PO now carries the supplier's terms from the canvass: payment mode, term days (0 = *Pay before ordering, cash/COD*), VAT-inclusive prices, PDC.
2. **Pay-first PO** (terms 0): *Place order* stays disabled until Accounting pays. *Request payment* on the PO creates the bill for the ordered quantity.
3. **Term PO** (30/60/90 days): order → receive delivery → *Record bill* (bills what was received and not yet billed). Due date = bill date + terms.
4. **Payment voucher** (Accounting › Payments): Finance picks the supplier's open bills, part or full, chooses cash or cheque and the bank, and submits.
5. **CEO approves** on the Approvals page (or the voucher page) with the saved signature. *Return for changes* sends it back with a note.
6. **Finance releases** the voucher: cheque no./date, received by, supplier OR no. This posts the payment and updates the bills. A pay-first PO can now be placed.
7. **Void payment** after release (e.g. a bounced cheque) reverses the entry and reopens the bills.

## Taxes

- **VAT 12%**: bills split the amount into net and **Input VAT** (1300). The VAT setting follows the PO: VAT-inclusive, VAT added on top, or no VAT (non-VAT supplier). Bills without a PO choose it per bill.
- **Expanded withholding tax (EWT)**: set per supplier (1% goods, 2% services/contractors, 5% rentals, 10–15% professional fees, or none). It is withheld when paying, on the amount net of VAT, and goes to **EWT Payable** (2110). The supplier receives the net.
- Stock bought on VAT-inclusive prices is valued **net of VAT** in inventory (when `app.accounting.vat-registered=true`, the default).

## What posts automatically

| Event | Debit | Credit |
|---|---|---|
| Delivery received | 1200 Inventory | 2040 Goods received, not yet billed |
| Supplier bill for a PO | 2040 (net) + 1300 Input VAT | 2010 Accounts payable |
| Bill without a PO | Chosen expense account (net) + 1300 | 2010 |
| Payment released | 2010 | Bank/cash (net) + 2110 EWT payable |
| Materials issued to a job | 5010 Materials used (tagged with the job) | 1200 |
| Return from a job | 1200 | 5010 |
| Stock adjustment (damaged, lost, recount) | 6150 Inventory losses ⇄ 1200 | |
| Opening stock adjustment | 1200 | 3900 Opening balance equity |

Account 2040 nets to zero once every delivery is billed. A paid pay-first PO sits there as a debit (advance) until the goods arrive.

## New pages (Accounting menu)

- **Payables**: owed / overdue / due in 7 days, POs waiting to be billed, open and paid bills, *Enter bill* for non-PO expenses.
- **Payments**: vouchers in progress and released; check/cash voucher printout with amount in words, the accounting entry and the Prepared / Approved (signature) / Received by block.
- **Banks & Cash**: add bank accounts (each gets its own ledger account 1031, 1032…), balances, transfers and deposits.
- **Journal**: every entry with a link to its source document; manual entries with a live debit/credit balance check; void by reversal.
- **Chart of Accounts**: about 55 accounts for a contractor, editable; click an account for its ledger with running balance.
- **Trial Balance** as of any date.
- **Opening Balances**: bring the stock already on hand into the ledger in one click, and enter starting cash and bank balances.
- Client Billing, Subcon Billings and Admin Expenses are marked *Soon* (phases 2–3).

## Setup after deploying

1. Users › Edit the CEO → tick **Approves payment vouchers** (he needs a signature on My profile). Until someone is ticked, administrators can approve.
2. Accounting › Banks & Cash → add the company bank accounts.
3. Accounting › Opening Balances → bring in the inventory, then the starting cash and bank balances.
4. Purchasing › Suppliers → set each supplier's VAT status and EWT rate.
5. Unpaid supplier invoices from before go in with *Enter bill*.

## Files

- New package `accounting/`: `Account`, `JournalEntry`, `JournalLine`, `BankAccount`, `Bill`, `BillLine`, `PaymentVoucher`, `VoucherLine`, `PaymentApprover` (+ repositories), `Acct` (account codes), `ChartSetup` (seeds the chart), `Ledger`, `InventoryPosting`, `PayablesService`, `VoucherService`, `VoucherSheet`, `PayablesController`, `LedgerController`.
- Inventory: `StockEvent`, `PoPaymentGate`; `InventoryService` publishes stock events; `PurchaseOrder` gets terms and VAT fields; `PurchaseOrderLine.billedQty`; `Supplier` gets VAT and EWT.
- Templates `acc-*.html`, `fragments/acc.html`, `static/js/accounting.js`; changes to the Approvals page, Users, the sidebar, PO and supplier pages.
- Tables are created by Hibernate (`ddl-auto=update`); the chart of accounts is seeded on start-up.

# Accounting phase 2: client billing and collections (Nov 15, 2026)

Contractor-style billing from the job order, from downpayment to retention release, posted to the same ledger.

## Flow

1. **Billing terms per job** (Accounting › Client Billing › job): the contract amount (pre-filled from the accepted quotation), VAT, billing basis (% per quotation item, or one overall % for lump sum or milestones), downpayment % (default 30), retention % (default 10), the client's withholding (2% for contractors), payment terms, bill-to and the client's PO number. Raise the contract amount for change orders.
2. **Downpayment billing**: the downpayment % of the contract, billed before work starts.
3. **Progress billing**: enter each item's % accomplished to date. The system works out the work this period (to date − previous), the downpayment recoupment (same %; the last billing recoups whatever is left), the retention held and the amount due, live as you type.
4. **CEO approval** on the Approvals page (or the billing page) with his signature; *Return for changes* sends it back with a note. Approval issues and posts the billing.
5. **Send to client**: emails a link to a printable billing statement (no login). The page shows when the client opened it.
6. **Collection**: tick the billings paid and the amount settled. The tax the client withholds (CWT) defaults to 2% of the amount net of VAT and can be changed to match the BIR 2307. Cash received = amount settled − CWT. Record the cheque, CBM's OR number and whether the 2307 was received.
7. **Retention release** at the end bills the retention held.
8. Voids reverse the posting: a collection (e.g. bounced cheque) reopens its billings; a billing can be voided only while nothing was collected and no later progress billing exists.

## Postings

| Event | Debit | Credit |
|---|---|---|
| Downpayment billed | 1100 AR clients | 2200 Customer deposits (net) + 2100 Output VAT |
| Progress billing | 1100 AR (amount due) + 1110 Retention receivable + 2200 (downpayment recouped, net of VAT) | 4010 Contract revenue (net) + 2100 Output VAT |
| Retention release | 1100 | 1110 |
| Collection | Bank (cash) + 1310 Creditable withholding tax | 1100 |

Every line is tagged with the job order, for job profitability in phase 4. Checked on a ₱6,462,400 contract billed in full: revenue ₱5,770,000, output VAT ₱692,400, customer deposits and retention back to zero, trial balance in balance.

## Pages

- **Client Billing**: receivable, overdue, retention held, not yet billed; drafts; open billings with aging; every job with its billed and collected figures.
- **Job billing**: terms, totals, billings, collections, and the actions for the next billing.
- **Collections** with a reminder for missing BIR 2307s.
- **Reports › Billing Monitoring** and **Reports › Payment Listing** are now real reports.
- **Job order page**: a Billing & collections panel.
- **Users**: the *Approves payment vouchers and client billings* tick covers both. The Approvals count includes billings.

## Files

`accounting/`: `JobContract`, `ClientBilling`, `ClientBillingLine`, `ClientCollection`, `ClientCollectionLine` (+ repositories), `ClientBillingService`, `BillingSheet`, `ClientBillingController`, `PublicBillingController` (`/sb/{token}`, public in `SecurityConfig`), `BillingReportsController`. Templates `acc-billing*.html`, `acc-collection*.html`, `rpt-billing-monitoring.html`, `rpt-payment-listing.html`.

# Accounting phase 3: subcontractors, admin expenses, petty cash, payroll posting, remittances (Nov 15, 2026)

## Subcontractors and subcon billings

- **Implementation › Subcontractors**: a directory by trade with TIN, VAT, EWT (default 2%) and accreditation. Subcontractors are stored with the suppliers, so bills, payment vouchers, CEO approval and withholding work the same way.
- **Accounting › Subcon Billings**: a subcontract per job order (scope, amount, VAT, downpayment %, retention %).
- Billings against a subcontract:
  - **Downpayment (advance)**.
  - **Progress**: % accomplished to date, verified by the engineer.
  - **Retention release**.
- The progress billing works out the advance recoupment, the retention held and the amount due.
- *Post billing* charges the job and creates the bill in Payables, which is paid through a voucher.

| Event | Debit | Credit |
|---|---|---|
| Advance | 1400 Advances to subcontractors | 2020 AP subcontractors |
| Progress | 5030 Subcontract costs (job, net) + 1300 Input VAT | 1400 (recouped) + 2030 Retention payable + 2020 |
| Retention release | 2030 | 2020 |
| Payment (voucher) | 2020 | Bank + 2110 EWT |

## Admin expenses

- A month view of operating expenses (accounts 6xxx) by account, with that month's expense bills.
- **Monthly expenses** (rent, internet, utilities) are set up once (supplier, account, VAT, EWT, day of month). *Bill this month* creates the bill in Payables, and the amount can be changed for utilities.

## Petty cash

- Funds are the *Petty cash* accounts in Banks & Cash, now with a custodian and a fund amount.
- **Petty cash voucher**: posts at once (Dr expense, tagged to a job if needed / Cr fund).
- **Replenish**: lists the vouchers and advances since the last replenishment and posts the refill cheque (Dr fund / Cr bank).
- **Cash advance** to an employee: Dr 1410 / Cr fund or bank. **Liquidate** with receipts (expense and job per receipt) and cash returned. The unliquidated balance becomes a CASH_ADVANCE deduction in HR (all at once or per cut-off); payroll then credits 1410.

## Payroll posting

- **Accounting › Payroll** lists HR's payroll periods.
- For a **locked** period, choose where each employee's pay is charged: a job order (5020 Direct labor) or the office (6010). The choice is remembered.
- *Post* records:
  - debits: pay earned, by employee;
  - credits: SSS, PhilHealth and Pag-IBIG payables, loans and cash advances (1410), sinking fund (2170, a new account), other deductions (4090), and net pay to 2160 Salaries payable.
- *Release net pay* pays 2160 from a bank.
- HR cannot unlock a posted period until Accounting *Unposts* it.
- **Not included yet:** employer shares of SSS, PhilHealth and Pag-IBIG are not in the HR pay sheet; record them with a journal entry (6020).

## Remittances

What is owed now for:
- EWT, withholding on compensation and VAT (output less input);
- SSS, PhilHealth and Pag-IBIG.

*Remit* records the payment with period and reference (PRN, eFPS). The VAT remittance applies the input VAT first.

## Reports now live

Subcon Billing Listing, Admin Expense Listing, Project Salary Cost.

## Checked end to end

| What | Result |
|---|---|
| Subcontract ₱560,000 (DP 20%, retention 10%) billed in full | Subcontract cost ₱500,000; advances and retention back to zero |
| Petty cash fund | Back to ₱10,000 after replenishing ₱4,850 |
| Cash advance | ₱1,300 sent to payroll as a deduction plan |
| Payroll | Posted and released |
| EWT and VAT | Remitted |
| Trial balance | In balance |

Loans that employees had before accounting started will show as a credit in 1410 once payroll deducts them. Bring their balances in once with a journal entry: Dr 1410 / Cr 3900.

# Accounting phase 4: statements, job profitability, dashboard, closing (Nov 16, 2026)

## Accounting dashboard (Accounting › Dashboard, the new landing page)

- **Totals:** cash and bank by account, what clients owe (overdue and retention), what CBM owes (due in 7 days, taxes).
- **This month:** a short profit-and-loss summary against last month, plus the year-to-date net.
- **Jobs:** each job with its margin bar and status (on track, over budget, projected loss).
- **Due lists:** bills due within 7 days and overdue client billings.
- **Alerts:** items waiting for approval, approved payments to release, locked payrolls not yet posted, jobs at risk.

## Financial statements

- **Income statement:**
  - Revenue, cost of contracts, gross profit and margin, operating expenses, operating income, other income and net income.
  - Compared with the previous period (the previous months for whole months).
  - Presets: this month, last month, quarter, this year, last year. Each account opens its ledger.
- **Balance sheet** as of any date:
  - Assets and liabilities by group; equity with the net income not yet closed.
  - Shows whether it balances.
- **Cash flow:**
  - Opening, money in, money out and closing per bank or cash account.
  - Grouped by where the money came from and went (collections, payments to suppliers and subcontractors, salaries, remittances, petty cash).
  - Transfers between CBM's own accounts are left out.

## Job profitability (Reports › Project Profitability, Expense Breakdown, Monthly Summary)

- **Per job:** contract (net of VAT), % accomplished, revenue billed, and cost to date by kind (materials, labor, subcontract, equipment, other direct, site expenses). Then gross profit and margin.
- **Budget vs actual:** compared with the cost entered in the quotation's costing.
- **Committed costs:** POs ordered but not received, and subcontract work not yet billed.
- **Projection:**
  - Estimated cost at completion = cost so far ÷ % accomplished.
  - From it, the projected profit and margin.
  - Flags: *Over budget* (cost runs ahead of progress or past the budget) and *Projected loss*.
- **Job order page:** now shows revenue, cost, gross profit, budget used and projected profit, with a link to the details.
- **Expense breakdown:** costs per job and kind for a period.
- **Monthly summary:** revenue, cost and profit per month, overall and per job.

## Inventory reports

- **Inventory Summary:** stock on hand at average cost, reorder flags, and a check against account 1200 in the ledger.
- **Inventory Movement:** opening, in, out and closing per material for a period, with values.

## Closing (Accounting › Closing)

- **Close through a date:** nothing can be posted on or before it, whether from bills, payments, stock or journal entries. The message says why. *Reopen all periods* undoes it.
- **Year-end:** brings every income and expense account to zero with an entry dated December 31. The net goes to 3100 Retained Earnings, and the year is locked.
- **Rules:** a year can be closed only after it ends, and only in order. *Reopen* undoes the closing entry.
- The income statement leaves out closing entries, so a closed year still shows its figures.

All Reports menu items are now live.

# Phase 5: bank reconciliation and BIR outputs (Nov 16, 2026)

## Bank reconciliation (Accounting › Bank Reconciliation)

- **Start:** choose a bank account and enter the statement's ending date and balance. The opening is the last reconciled balance.
- **Tick** each book entry that appears on the statement. The cleared balance and the difference update as you tick, and *Finish* unlocks at zero.
- **Import statement (CSV):**
  - Download it from online banking, or save the Excel file as CSV.
  - The header row is detected automatically: a date column, plus either *Amount* or *Debit/Withdrawal* and *Credit/Deposit*. *Description* and *Check/Reference No.* are used when present.
  - Each line is matched to a book entry with the same amount within 7 days before to 60 days after (cheques clear late), using the cheque number when it is there. Matches are ticked.
- **Statement lines not in the books** are listed. Bank charges (6130) and interest (4090) are *Recorded* in one click: posted and ticked. Others can be ignored or entered in their own module, then *Match again*.
- **Result:** the outstanding cheques and deposits in transit, so that statement + in transit − outstanding = balance in the books.
- **Reconciled lines** stay cleared, so the next month starts from the right balance. The latest reconciliation can be reopened.

## BIR (Accounting › BIR Reports), by quarter

- **BIR 2307 for every supplier and subcontractor** CBM withheld tax from:
  - Taken from released payment vouchers.
  - Income payments by month (net of VAT) and tax withheld, with the ATC.
  - Printable on legal-size paper, one per payee or all at once.
  - *Edit* sets a payee's TIN, address, type (corporation or individual) and an ATC override.
- **ATC by rate:**

  | EWT rate | ATC |
  |---|---|
  | 1% goods | WC158 |
  | 2% services | WC160 |
  | 2% subcontractors | WC120 |
  | 5% rentals | WC100 |
  | 10% / 15% professional fees | WC010 / WC011 |

  Individuals use WI instead of WC. Suppliers can now carry a payee type and ATC (Purchasing › Suppliers).
- **1601-EQ summary** by ATC, and a **QAP CSV** in the alphalist column order.
- **VAT (2550Q):** output VAT, input VAT, sales and purchases per month, and the VAT payable.
- **Tax withheld by clients (SAWT):** per client with TIN (new *Client TIN* field in the billing terms), ATC and whether every 2307 was received. **SAWT CSV** export.
- **Company details:** CBM's registered name, TIN, address, RDO and signatory, printed on the 2307.

Have the accountant check the figures and the ATCs before filing. The CSVs are for the BIR alphalist data entry module.

# Phase 6: Admin Settings and Audit Trail (Nov 16, 2026)

Every sidebar module is now built; no page is marked "Soon".

## Admin › Settings

- **Company profile:**
  - Registered name, address, ZIP, RDO, TIN, phone, email, website, "cheques payable to", and the 2307 signatory.
  - Printed under the logo on quotations, purchase orders, payment vouchers, client billing statements and BIR 2307, and in the quotation email signature.
  - The same record as *Company details* on the BIR page.
- **Quotation defaults:**
  - VAT/tax %, terms and notes, and validity days.
  - New quotations start with these values. The validity is pre-filled when a quotation is sent.
- **Document numbering:**
  - Shows each series (PO, RR, IS, ADJ, JV, BL, DV, PB, CR, SC, SCB, PCV, PCR, CA, RM), the last number issued this year and the next one.
  - *Continue at* sets where this year continues, for when you move over from manual books.
  - It can't go below a number already issued, so there are no duplicates.
- **Email & system:**
  - Shows whether email is set up and the sender addresses, without showing keys, plus a *Send test* button.
  - Shows database size and version, photo storage (Cloudinary or local), uptime and memory.
  - Sets how long the audit trail is kept.

## Admin › Audit Trail

- **Recorded automatically:**
  - Every record created, changed or deleted anywhere in the portal: who, when, from which IP, and each field's value before and after.
  - Deleted records keep their full contents.
  - Sign-ins, failed sign-ins and sign-outs.
- **Grouped by save:** what is saved together appears together, e.g. a voided journal entry with its reversal. High-volume detail (journal lines, time sheet days, stock movements, payroll results, bank statement lines) is summarised as "12 created".
- **Filters:** date, user, module, type (created / changed / deleted / sign-ins / failed sign-ins) and search by number, name or value. Each line has *Open* and *History of this record*.
- **Reliability:** the trail is written in the same transaction as the change, so a failed save leaves no line. A problem writing the trail never blocks the save.
- **Notes:**
  - Changes made directly in the database (SQL) are not recorded.
  - Recording starts from this version.
  - Tables `sys_settings`, `sys_number_seed` and `sys_audit`, and the new `acc_company` columns, are created on startup.

# Phase 7: payroll taxes and contributions, fixed assets (Nov 16, 2026)

## Payroll: withholding tax and contributions

- **Withholding tax on compensation:** deducted automatically on each pay sheet using the BIR table (TRAIN, 2023 onward). The period's taxable pay is annualised.
  - Taxable pay = gross − 13th month / bonuses − lates and absences − employee SSS, PhilHealth and Pag-IBIG.
  - It appears as a *Withholding tax* line, and a typed-in amount replaces it for that cut-off.
  - Shown in the pay sheet's "Gov't & tax" column and in the Excel export (W/TAX column).
- **Employee tax status (HR › Employees):** *Withhold per BIR table*, *Minimum wage earner (exempt)* or *Don't withhold*.
- **New employee fields:** TIN, SSS / PhilHealth / Pag-IBIG numbers, birth date and address. The dialog also shows what the 2025 contribution tables give for the employee's rate.
- **Contribution tables (HR › Pay rules › "Use the contribution tables"):** used when an employee's SSS, PhilHealth or Pag-IBIG amount is blank. Fixed amounts still win.
  - SSS: 15% of the salary credit (₱5,000–35,000). The employee pays 5%; the employer pays 10% plus EC.
  - PhilHealth: 5% of the monthly basic (₱10,000–100,000), split equally.
  - Pag-IBIG: 2% each, up to ₱10,000.
  - Rates are in `payroll/Statutory.java`.
- **Employer shares** are worked out from the employee shares actually deducted and posted with the payroll:
  - Dr direct labor of the job (5020), or 6020 for office staff.
  - Cr SSS / PhilHealth / Pag-IBIG payable.
  - Withholding tax goes to 2120.
  - Shown per employee on the payroll posting page.
- **Accounting › Payroll Taxes:**
  - **Monthly:** the 1601-C figures (compensation, non-taxable, taxable, tax withheld), SSS / PhilHealth / Pag-IBIG employee and employer shares per employee, and a contributions CSV.
  - **Yearly:** alphalist of employees with tax due by the annual table, tax withheld, and what is still to withhold or refund. Alphalist CSV (1604-C) and a printable **BIR 2316** per employee.

## Fixed assets (Accounting › Fixed Assets)

- **Register:** code, category (construction equipment 1530, tools 1500, vehicles 1520, office 1540, building 1550), serial and plate no., custodian, location, project, cost, salvage value and useful life.
- **How it's in the books:**
  - *bought now*: Dr asset / Cr bank.
  - *already recorded*: through a bill or entry; no new entry.
  - *owned before*: opening balance, with depreciation to date.
- **Monthly depreciation:** straight-line, one entry a month, posted in order and undoable for the latest month.
  - Assets used on a project go to 5050, charged to that job; others go to 6140.
  - Cr accumulated depreciation (1510).
- **Schedule per asset:** posted and projected months, accumulated depreciation and book value.
- **Sell / dispose:** removes cost and depreciation, records the cash received, and books the gain (4095) or loss (6160). Can be undone.
- **New accounts:** 1530, 1540, 1550, 4095, 5050, 6160.

# Phase 8: go-live tools (Nov 16, 2026)

## Admin › Import & Backup

- **Import from Excel (.xlsx) or CSV** for:
  - suppliers and subcontractors;
  - materials, with opening stock;
  - employees;
  - opening account balances (trial balance).
- **How it works:**
  - Download the template, fill it in, then upload.
  - The header row is found automatically: columns can be in any order, and common spellings work ("Supplier Name", "UOM", "Daily rate", "Dr/Cr"…).
  - **Check first:** the preview marks each row New / Update / Skip / Problem with the reason. Nothing is saved until you confirm.
  - On confirm, all valid rows are saved in one transaction.
- **Matching existing records:**
  - Suppliers by name.
  - Materials by code, else name.
  - Employees by employee no., biometric ID or name. People on the org chart keep their name, position and department.
  - Blank cells leave existing values unchanged.
- **Opening stock** goes in at the given cost. The preview reminds you to bring it into the books in Accounting › Opening balances.
- **Opening balances** post one entry (source OPENING), with any difference to 3900. It warns if a trial-balance opening was posted before.
- **Each import** is recorded on the audit trail, and every record it creates or changes appears there too.
- **Backup:**
  - ZIP of every table as CSV (PostgreSQL COPY, one consistent snapshot), plus `restore.sql` and a README. The audit trail is optional.
  - Tested: restored into an empty database, every row count and the ledger matched.
  - Who downloaded it and when is shown and recorded.

## Admin › Access Review

- Everyone's module access in one grid, where it comes from (admin, department or custom), and what they approve or sign: quotations, payment vouchers and client billings, canvass steps.
- Last sign-in and failed sign-ins.
- **Flags:**
  - an approver who can't open the module they approve in;
  - an account with no modules;
  - no sign-in for 90+ days;
  - too many admins, or only one;
  - no payment or quotation approver set;
  - canvass steps without a signer;
  - sign-ups waiting for approval.
- CSV export.
