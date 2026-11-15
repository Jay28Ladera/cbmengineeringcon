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
