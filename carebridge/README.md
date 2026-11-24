# CareBridge

CareBridge is a two-role healthcare workspace for the VCube Hack Quest. A patient can find a doctor by hospital or specialty, book a visit, keep a medical timeline, upload a report, and selectively share it. A doctor can update their profile, manage appointments, and download reports shared with them.

**Start here:** [Windows setup, development, Postman, and cloud deployment](docs/SETUP-AND-DEPLOY.md)  
**Submission:** [ER diagram PDF](docs/ER-diagram.pdf) · [ER diagram source](docs/ER-diagram.md) · [Postman collection](docs/CareBridge.postman_collection.json) · [Demo and judging checklist](docs/DEMO.md)  
**Demo assets:** [Synthetic PDF](docs/demo-report.pdf) · [Synthetic text report](docs/demo-report.txt)

## Requirements coverage

| Brief requirement | Implementation |
| --- | --- |
| Patient and doctor registration | Role-based signup and session login. Doctor signup requires an invitation code. |
| Doctor profile management | Doctors can update specialty, hospital, and bio. Patients can search these details. |
| Appointment booking | Date-specific slots, separate role views, cancellation, and doctor completion after the start time. Separate MySQL unique keys prevent simultaneous bookings for the same doctor or patient. |
| Medical history | Patient-only timeline with create, list, edit, and delete. |
| Medical report upload | Selectable-text PDF and UTF-8 TXT. Upload, download, list, deletion, and explicit per-doctor sharing. |
| Doctor dashboard | Upcoming, completed, and shared report counts, appointment filters, and next-visit details. |
| Medical Report Summarizer | The Spring backend extracts text, sends candidate lines after consent, and returns validated selections from the actual uploaded report. |
| Healthcare Information Assistant | The LLM prioritizes curated education cards for lab reports, referrals, and follow-ups. |
| Medical Terminology Explainer | The app displays curated definitions and uses the LLM to select an approved follow-up explanation. |
| Appointment Preparation Suggestions | The LLM ranks a checklist using the specialty and time from the authenticated patient’s upcoming booking. |
| Minimum five connected tables | Seven tables with foreign keys, indexes, and Flyway-managed migrations. |
| Stack | Java 21, Spring Boot, Spring Data JPA, Spring Security, REST, MySQL 8.4, React, and a compatible LLM API accessed through Spring Boot. |
| Submission package | Source, README, ER diagram, Postman collection, synthetic data, demo script, and Docker Compose deployment configuration. |

## Run in five minutes after prerequisites are installed

1. Extract the ZIP and open the `carebridge` folder.
2. Install and start Docker Desktop. In PowerShell, run `Copy-Item .env.example .env`.
3. Open `.env`, fill the database passwords and doctor invitation code, and supply an LLM API key to enable AI.
4. Run `docker compose -f compose.yml -f compose.local.yml up --build -d`.
5. Open [http://localhost:8080](http://localhost:8080). Register a synthetic doctor first, then a patient in a separate private window.

## How the app is organized

| Location | What to edit |
| --- | --- |
| `frontend/src/main.jsx` | Login, dashboard, directory, booking, history, report sharing, and assistant UI. |
| `frontend/src/styles.css` | Layout, colors, breakpoints, and form styling. |
| `backend/src/main/java/com/carebridge/Accounts.java` | Accounts, registration, role assignment, and login identity. |
| `backend/src/main/java/com/carebridge/Security.java` | Spring Security, CSRF, access rules, and sign-out. |
| `backend/src/main/java/com/carebridge/Visits.java` | Doctor profiles, available slots, bookings, and status changes. |
| `backend/src/main/java/com/carebridge/Records.java` | Medical history and report storage, ownership, and sharing. |
| `backend/src/main/java/com/carebridge/Assistant.java` | LLM integration, curated content, request gating, and output validation. |
| `backend/src/main/resources/db/migration/` | MySQL schema migrations. |
| `Dockerfile`, `compose*.yml`, `Caddyfile` | Build, service wiring, persistent storage, and HTTPS deployment. |

The deployment serves the React frontend and Spring REST API from the same URL. Spring uses a session cookie and CSRF token for writes. MySQL stays on the internal Docker network in the deployed configuration. AI responses are assembled from approved cards or selected source excerpts, with the provider returning IDs only. Use synthetic data across the app and enable provider spending limits for a public demo.

## Scope

The directory stores a hospital name on each doctor profile. Scheduling uses a shared Monday–Friday, 09:00–17:00 IST timetable, 30-minute slots, and a 30-day booking window. Report access is explicit and revocable, and appointment cancellation releases the slot without revoking an existing report share. OCR, hospital staff accounts, and configurable clinician schedules are outside this build. The public deployment configuration is intended for a hackathon demonstration using fictional accounts and reports.

## Educational references

The curated content uses plain-language definitions and report-reading concepts from [MedlinePlus Lab Results](https://medlineplus.gov/lab-tests/how-to-understand-your-lab-results/), [MedlinePlus Laboratory Tests](https://medlineplus.gov/laboratorytests.html), the [NCI MRI definition](https://www.cancer.gov/publications/dictionaries/cancer-terms/def/mri), and the [NCI biopsy definition](https://www.cancer.gov/publications/dictionaries/cancer-terms/def/biopsy).

## Verification

The integration suite was run against H2 and MySQL 8.4.9. It covers registration and session access, profile updates, role restrictions, appointment collisions and cancellation, history edits, report sharing and revocation, and all four AI modes with a local mock provider. An HTTP smoke test also exercised the built React asset delivery, real PDF upload and extraction, exact file download, and report revocation against MySQL. A Chromium test at desktop and mobile widths verified registration, profile editing, booking, medical history, PDF upload, AI summary, sharing, and revocation. Local, development, and cloud Compose configurations passed Docker Compose configuration validation. The cloud deployment and live LLM requests require your own cloud host and provider credentials.
