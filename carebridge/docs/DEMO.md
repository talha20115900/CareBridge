# CareBridge final demonstration

## What to have open

- Patient and doctor accounts in separate browser sessions, both using the live HTTPS URL.
- A fictional doctor profile with a recognizable hospital name.
- The included synthetic PDF, an open weekday slot, and the AI demo code.
- Repository tab with README and ER diagram, plus the imported Postman collection.

## Five-minute demo

| Time | Show | Explain |
| --- | --- | --- |
| 0:00 | Patient overview and searchable doctor directory | Patients can manage appointments, reports, and history in one workspace. |
| 0:40 | Book a slot, then refresh the doctor dashboard | MySQL unique keys protect the doctor and patient schedules, including when two requests arrive together. |
| 1:25 | Add a medical-history entry and upload the synthetic PDF | The original file and extracted text are stored with a patient owner. |
| 2:05 | Click **Summarize** and compare the returned excerpts against the PDF | The Spring backend asks the model to select actual source lines and verifies the returned IDs. Numeric values come from the source. |
| 2:50 | Run the information, terminology, and visit preparation tools | Preparation uses the booked specialty and time. Medical education uses curated cards and definitions. |
| 3:35 | Share the report with the booked doctor, download it, then revoke access | The report owner controls each doctor’s access, and the backend checks access on download. |
| 4:20 | Show the ER diagram, Postman collection, and live health endpoint | Seven related tables, repeatable API tests, and one HTTPS address for React and Spring APIs. |

## Checks before presenting

- A patient cannot alter somebody else’s history or appointment.
- A doctor cannot open an unshared report or a revoked report URL.
- Duplicate or simultaneous booking produces one successful reservation for the slot.
- A cancelled appointment becomes available to book again.
- The four AI tools return usable results with your real provider key.
- Every report, name, and account used in the demo is fictional.
- The repository and live URL open on another device.

## Submission fields

- Repository URL: your GitHub repository URL.
- Live application URL: your deployed HTTPS hostname.
- Database diagram: `docs/ER-diagram.pdf`. The editable Mermaid source is in `docs/ER-diagram.md`.
- Postman collection: `docs/CareBridge.postman_collection.json`.
- Setup and documentation: `README.md` and `docs/SETUP-AND-DEPLOY.md`.
- Demonstration: follow the sequence above for a live presentation or record the same sequence if the event asks for a video.

## Interview explanation

“I built a Spring Boot and React healthcare workspace on MySQL with two roles. The main workflow is booking an appointment, uploading a report, and sharing it with the doctor. I handled booking collisions with MySQL unique constraints so that concurrent requests still produce a single valid reservation. React never calls the LLM directly. Spring verifies the patient’s access and uses the model to select source excerpts or curated information. This gives the patient something to discuss with the doctor while keeping the system out of diagnosis and prescribing.”
