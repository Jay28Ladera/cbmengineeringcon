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
