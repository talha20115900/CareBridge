package com.carebridge;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

enum Role { PATIENT, DOCTOR }
enum Status { BOOKED, CANCELLED, COMPLETED }

@Entity
@Table(name = "users")
class Account {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    String name;
    String email;
    String passwordHash;
    @Enumerated(EnumType.STRING) Role role;
}

@Entity
@Table(name = "doctors")
class Doctor {
    @Id Long id;
    @OneToOne(fetch = FetchType.LAZY) @MapsId @JoinColumn(name = "id") Account account;
    String specialty;
    String hospital;
    String bio;
}

@Entity
@Table(name = "patients")
class Patient {
    @Id Long id;
    @OneToOne(fetch = FetchType.LAZY) @MapsId @JoinColumn(name = "id") Account account;
}

@Entity
@Table(name = "appointments")
class Visit {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "doctor_id") Doctor doctor;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "patient_id") Patient patient;
    LocalDateTime startsAt;
    LocalDateTime reservedAt;
    String reason;
    @Enumerated(EnumType.STRING) Status status;
}

@Entity
@Table(name = "medical_history")
class History {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    Long patientId;
    LocalDate eventDate;
    String title;
    String details;
}

@Entity
@Table(name = "medical_reports")
class Report {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    Long patientId;
    LocalDateTime uploadedAt;
    String filename;
    String mediaType;
    @Lob byte[] content;
    @Column(columnDefinition = "MEDIUMTEXT")
    @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.LONGVARCHAR)
    String extractedText;
}

@Entity
@Table(name = "report_shares")
class ReportShare {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    Long reportId;
    Long doctorId;
}
