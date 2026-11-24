CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(190) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    role VARCHAR(12) NOT NULL
);
CREATE TABLE doctors (
    id BIGINT PRIMARY KEY,
    specialty VARCHAR(100) NOT NULL,
    hospital VARCHAR(120) NOT NULL,
    bio VARCHAR(1000) NOT NULL,
    FOREIGN KEY (id) REFERENCES users(id)
);
CREATE TABLE patients (
    id BIGINT PRIMARY KEY,
    FOREIGN KEY (id) REFERENCES users(id)
);
CREATE TABLE appointments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    doctor_id BIGINT NOT NULL,
    patient_id BIGINT NOT NULL,
    starts_at DATETIME(6) NOT NULL,
    reserved_at DATETIME(6),
    reason VARCHAR(400) NOT NULL,
    status VARCHAR(16) NOT NULL,
    FOREIGN KEY (doctor_id) REFERENCES doctors(id),
    FOREIGN KEY (patient_id) REFERENCES patients(id),
    UNIQUE (doctor_id, reserved_at),
    UNIQUE (patient_id, reserved_at),
    INDEX idx_patient_visits (patient_id, starts_at),
    INDEX idx_doctor_visits (doctor_id, starts_at)
);
CREATE TABLE medical_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    patient_id BIGINT NOT NULL,
    event_date DATE NOT NULL,
    title VARCHAR(120) NOT NULL,
    details VARCHAR(2000) NOT NULL,
    FOREIGN KEY (patient_id) REFERENCES patients(id),
    INDEX idx_history_date (patient_id, event_date)
);
CREATE TABLE medical_reports (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    patient_id BIGINT NOT NULL,
    uploaded_at DATETIME(6) NOT NULL,
    filename VARCHAR(180) NOT NULL,
    media_type VARCHAR(40) NOT NULL,
    content LONGBLOB NOT NULL,
    extracted_text MEDIUMTEXT NOT NULL,
    FOREIGN KEY (patient_id) REFERENCES patients(id),
    INDEX idx_report_date (patient_id, uploaded_at)
);
CREATE TABLE report_shares (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    report_id BIGINT NOT NULL,
    doctor_id BIGINT NOT NULL,
    UNIQUE (report_id, doctor_id),
    FOREIGN KEY (report_id) REFERENCES medical_reports(id),
    FOREIGN KEY (doctor_id) REFERENCES doctors(id),
    INDEX idx_shared_doctor (doctor_id)
);
