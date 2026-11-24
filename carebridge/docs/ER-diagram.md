# CareBridge database ER diagram

```mermaid
erDiagram
    USERS ||--o| DOCTORS : "doctor profile"
    USERS ||--o| PATIENTS : "patient profile"
    DOCTORS ||--o{ APPOINTMENTS : receives
    PATIENTS ||--o{ APPOINTMENTS : books
    PATIENTS ||--o{ MEDICAL_HISTORY : records
    PATIENTS ||--o{ MEDICAL_REPORTS : uploads
    MEDICAL_REPORTS ||--o{ REPORT_SHARES : grants
    DOCTORS ||--o{ REPORT_SHARES : receives

    USERS {
        bigint id PK
        varchar name
        varchar email UK
        varchar password_hash
        varchar role
    }
    DOCTORS {
        bigint id PK,FK
        varchar specialty
        varchar hospital
        varchar bio
    }
    PATIENTS {
        bigint id PK,FK
    }
    APPOINTMENTS {
        bigint id PK
        bigint doctor_id FK
        bigint patient_id FK
        datetime starts_at
        datetime reserved_at
        varchar reason
        varchar status
    }
    MEDICAL_HISTORY {
        bigint id PK
        bigint patient_id FK
        date event_date
        varchar title
        varchar details
    }
    MEDICAL_REPORTS {
        bigint id PK
        bigint patient_id FK
        datetime uploaded_at
        varchar filename
        varchar media_type
        longblob content
        mediumtext extracted_text
    }
    REPORT_SHARES {
        bigint id PK
        bigint report_id FK
        bigint doctor_id FK
    }
```

- A user has exactly one profile through the registration workflow. The profile shares the user ID.
- The clinic and all appointments use India Standard Time. `starts_at` records the original visit time. `reserved_at` is the same value until cancellation, when MySQL allows the old record to keep a NULL reservation.
- Separate unique constraints on `(doctor_id, reserved_at)` and `(patient_id, reserved_at)` arbitrate simultaneous bookings.
- The unique `(report_id, doctor_id)` pair represents explicit sharing. It remains valid until the patient revokes it or deletes the report. Cancellation alone leaves sharing in place.
- Flyway owns the schema. See [V1__carebridge.sql](../backend/src/main/resources/db/migration/V1__carebridge.sql).
