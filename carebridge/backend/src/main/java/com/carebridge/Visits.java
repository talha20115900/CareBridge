package com.carebridge;

import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;
import java.util.*;

record DoctorCard(Long id, String name, String specialty, String hospital, String bio) {
    static DoctorCard of(Doctor doctor) {
        return new DoctorCard(doctor.id, org.hibernate.Hibernate.unproxy(doctor.account, Account.class).name, doctor.specialty, doctor.hospital, doctor.bio);
    }
}
record DoctorEdit(@NotBlank @Size(max = 100) String specialty,
                  @NotBlank @Size(max = 120) String hospital,
                  @NotNull @Size(max = 1000) String bio) {}
record Booking(@NotNull Long doctorId, @NotNull LocalDateTime startsAt,
               @NotBlank @Size(max = 400) String reason) {}
record VisitView(Long id, Long doctorId, Long patientId, String doctorName, String specialty,
                 String hospital, String patientName, LocalDateTime startsAt, String reason, Status status) {
    static VisitView of(Visit visit) {
        Doctor doctor = org.hibernate.Hibernate.unproxy(visit.doctor, Doctor.class);
        Patient patient = org.hibernate.Hibernate.unproxy(visit.patient, Patient.class);
        Account doctorAccount = org.hibernate.Hibernate.unproxy(doctor.account, Account.class);
        Account patientAccount = org.hibernate.Hibernate.unproxy(patient.account, Account.class);
        return new VisitView(visit.id, doctor.id, patient.id, doctorAccount.name,
            doctor.specialty, doctor.hospital, patientAccount.name, visit.startsAt,
            visit.reason, visit.status);
    }
}

@Service
@Transactional(readOnly = true)
class Visits {
    static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Kolkata");
    private final EntityManager database;

    Visits(EntityManager database) {
        this.database = database;
    }

    List<DoctorCard> doctors() {
        return database.createQuery("select d from Doctor d join fetch d.account order by d.account.name", Doctor.class)
            .getResultList().stream().map(DoctorCard::of).toList();
    }

    DoctorCard profile(Long id) {
        return DoctorCard.of(doctor(id));
    }

    @Transactional
    public DoctorCard update(Long id, DoctorEdit edit) {
        Doctor doctor = doctor(id);
        doctor.specialty = edit.specialty().strip();
        doctor.hospital = edit.hospital().strip();
        doctor.bio = edit.bio().strip();
        return DoctorCard.of(doctor);
    }

    List<LocalDateTime> slots(Long doctorId, LocalDate day) {
        doctor(doctorId);
        checkDate(day);
        if (day.getDayOfWeek().getValue() >= 6) {
            return List.of();
        }

        Set<LocalDateTime> reserved = new HashSet<>(database.createQuery("""
            select v.reservedAt from Visit v where v.doctor.id = :doctor
                and v.reservedAt >= :start and v.reservedAt < :end
            """, LocalDateTime.class).setParameter("doctor", doctorId)
            .setParameter("start", day.atStartOfDay())
            .setParameter("end", day.plusDays(1).atStartOfDay()).getResultList());

        List<LocalDateTime> slots = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now(CLINIC_ZONE);
        for (LocalDateTime time = day.atTime(9, 0); time.isBefore(day.atTime(17, 0)); time = time.plusMinutes(30)) {
            if (time.isAfter(now) && !reserved.contains(time)) {
                slots.add(time);
            }
        }
        return slots;
    }

    @Transactional
    public VisitView book(Long patientId, Booking booking) {
        LocalDateTime time = booking.startsAt();
        checkDate(time.toLocalDate());
        if (!time.isAfter(LocalDateTime.now(CLINIC_ZONE)) || time.getDayOfWeek().getValue() >= 6
            || time.getHour() < 9 || time.getHour() >= 17 || time.getMinute() % 30 != 0
            || time.getSecond() != 0 || time.getNano() != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pick a future time from the available slots.");
        }

        Visit visit = new Visit();
        visit.patient = database.find(Patient.class, patientId);
        visit.doctor = doctor(booking.doctorId());
        visit.startsAt = time;
        visit.reservedAt = time;
        visit.reason = booking.reason().strip();
        visit.status = Status.BOOKED;
        database.persist(visit);
        return VisitView.of(visit);
    }

    List<VisitView> list(Identity user) {
        String owner = user.role() == Role.PATIENT ? "patient" : "doctor";
        return database.createQuery("""
            select v from Visit v join fetch v.doctor d join fetch d.account
                join fetch v.patient p join fetch p.account where v.%s.id = :id
                order by v.startsAt desc, v.id desc
            """.formatted(owner), Visit.class).setParameter("id", user.id())
            .getResultList().stream().map(VisitView::of).toList();
    }

    @Transactional
    public VisitView change(Identity user, Long id, Status status) {
        Visit visit = database.find(Visit.class, id, LockModeType.PESSIMISTIC_WRITE);
        checkOwner(user, visit);
        if (visit.status != Status.BOOKED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This appointment is already closed.");
        }

        LocalDateTime now = LocalDateTime.now(CLINIC_ZONE);
        if (status == Status.CANCELLED && visit.startsAt.isAfter(now)) {
            visit.status = status;
            visit.reservedAt = null;
        } else if (status == Status.COMPLETED && user.role() == Role.DOCTOR && !visit.startsAt.isAfter(now)) {
            visit.status = status;
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This appointment cannot be changed that way.");
        }
        return VisitView.of(visit);
    }

    VisitView byOwner(Identity user, Long id) {
        Visit visit = database.find(Visit.class, id);
        checkOwner(user, visit);
        return VisitView.of(visit);
    }

    private Doctor doctor(Long id) {
        Doctor doctor = database.find(Doctor.class, id);
        if (doctor == null) {
            throw Checks.missing();
        }
        return doctor;
    }

    private void checkOwner(Identity user, Visit visit) {
        if (visit == null) {
            throw Checks.missing();
        }
        Long owner = user.role() == Role.PATIENT
            ? referenceId(visit.patient)
            : referenceId(visit.doctor);
        if (!user.id().equals(owner)) {
            throw Checks.missing();
        }
    }

    private Long referenceId(Object entity) {
        return (Long) database.getEntityManagerFactory().getPersistenceUnitUtil().getIdentifier(entity);
    }

    private void checkDate(LocalDate day) {
        LocalDate today = LocalDate.now(CLINIC_ZONE);
        if (day.isBefore(today) || day.isAfter(today.plusDays(30))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a date within the next 30 days.");
        }
    }
}

@RestController
@RequestMapping("/api")
class VisitController {
    private final Visits visits;

    VisitController(Visits visits) {
        this.visits = visits;
    }

    @GetMapping("/doctors")
    List<DoctorCard> doctors() {
        return visits.doctors();
    }

    @GetMapping("/doctors/{id}/slots")
    List<LocalDateTime> slots(@PathVariable Long id, @RequestParam LocalDate day) {
        return visits.slots(id, day);
    }

    @GetMapping("/doctor/profile")
    DoctorCard profile(@AuthenticationPrincipal LoginUser user) {
        return visits.profile(user.identity.id());
    }

    @PutMapping("/doctor/profile")
    DoctorCard edit(@AuthenticationPrincipal LoginUser user, @Valid @RequestBody DoctorEdit edit) {
        return visits.update(user.identity.id(), edit);
    }

    @PostMapping("/patient/appointments")
    @ResponseStatus(HttpStatus.CREATED)
    VisitView book(@AuthenticationPrincipal LoginUser user, @Valid @RequestBody Booking booking) {
        return visits.book(user.identity.id(), booking);
    }

    @GetMapping("/appointments")
    List<VisitView> visits(@AuthenticationPrincipal LoginUser user) {
        return visits.list(user.identity);
    }

    @PatchMapping("/appointments/{id}")
    VisitView change(@AuthenticationPrincipal LoginUser user, @PathVariable Long id, @RequestParam Status status) {
        return visits.change(user.identity, id, status);
    }
}
