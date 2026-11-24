package com.carebridge;

import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.time.*;
import java.util.*;

record HistoryEdit(@NotNull LocalDate eventDate, @NotBlank @Size(max = 120) String title,
                   @NotBlank @Size(max = 2000) String details) {}
record HistoryView(Long id, LocalDate eventDate, String title, String details) {
    static HistoryView of(History item) {
        return new HistoryView(item.id, item.eventDate, item.title, item.details);
    }
}
record ReportView(Long id, String filename, LocalDateTime uploadedAt, String patient, List<Long> sharedWith) {}
record ReportFile(String filename, String mediaType, byte[] bytes) {}

@Service
@Transactional(readOnly = true)
class Records {
    private final EntityManager database;

    Records(EntityManager database) {
        this.database = database;
    }

    List<HistoryView> history(Long patientId) {
        return database.createQuery("""
            from History h where h.patientId = :patient order by h.eventDate desc, h.id desc
            """, History.class).setParameter("patient", patientId).getResultList().stream().map(HistoryView::of).toList();
    }

    @Transactional
    public HistoryView saveHistory(Long patientId, Long id, HistoryEdit input) {
        if (input.eventDate().isAfter(LocalDate.now(Visits.CLINIC_ZONE)) || input.eventDate().getYear() < 1900) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a past or current date from 1900 onward.");
        }
        History item = id == null ? new History() : ownedHistory(patientId, id);
        item.patientId = patientId;
        item.eventDate = input.eventDate();
        item.title = input.title().strip();
        item.details = input.details().strip();
        if (id == null) {
            database.persist(item);
        }
        return HistoryView.of(item);
    }

    @Transactional
    public void deleteHistory(Long patientId, Long id) {
        database.remove(ownedHistory(patientId, id));
    }

    List<ReportView> reports(Identity viewer) {
        boolean patient = viewer.role() == Role.PATIENT;
        String filter = patient ? "r.patientId = :viewer" :
            "exists (select s.id from ReportShare s where s.reportId = r.id and s.doctorId = :viewer)";
        List<Object[]> entries = database.createQuery("""
            select r.id, r.filename, r.uploadedAt, u.name from Report r, Account u
                where u.id = r.patientId and %s order by r.uploadedAt desc, r.id desc
            """.formatted(filter), Object[].class).setParameter("viewer", viewer.id()).getResultList();

        Map<Long, List<Long>> shares = new HashMap<>();
        if (patient) {
            database.createQuery("""
                select s.reportId, s.doctorId from ReportShare s, Report r
                    where r.id = s.reportId and r.patientId = :patient order by s.doctorId
                """, Object[].class).setParameter("patient", viewer.id()).getResultList().forEach(row ->
                    shares.computeIfAbsent((Long) row[0], id -> new ArrayList<>()).add((Long) row[1]));
        }
        return entries.stream().map(row -> new ReportView((Long) row[0], (String) row[1],
            (LocalDateTime) row[2], (String) row[3], shares.getOrDefault(row[0], List.of()))).toList();
    }

    @Transactional
    public ReportView upload(Identity user, MultipartFile file) {
        if (file.isEmpty() || file.getSize() > 3 * 1024 * 1024) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a nonempty file up to 3 MB.");
        }

        String originalName = Optional.ofNullable(file.getOriginalFilename()).orElse("");
        String name = originalName.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("\\p{Cntrl}", "").strip();
        if (name.isBlank() || name.length() > 180) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use a filename of up to 180 characters.");
        }

        String lowerName = name.toLowerCase(Locale.ROOT);
        if (!lowerName.endsWith(".pdf") && !lowerName.endsWith(".txt")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload a text-based PDF or UTF-8 TXT file.");
        }
        boolean pdf = lowerName.endsWith(".pdf");
        byte[] bytes;
        String extracted;
        try {
            bytes = file.getBytes();
            extracted = pdf ? readPdf(bytes) : StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
        } catch (IOException | IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The report could not be read. Use a readable PDF or UTF-8 TXT file.");
        }
        extracted = extracted.replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", "").strip();
        if (extracted.isBlank() || extracted.length() > 20000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Use a selectable-text report with 1–20,000 characters. Scans require a text copy.");
        }

        Report report = new Report();
        report.patientId = user.id();
        report.uploadedAt = LocalDateTime.now(Visits.CLINIC_ZONE);
        report.filename = name;
        report.mediaType = pdf ? "application/pdf" : "text/plain";
        report.content = bytes;
        report.extractedText = extracted;
        database.persist(report);
        return new ReportView(report.id, name, report.uploadedAt, user.name(), List.of());
    }

    ReportFile download(Identity user, Long id) {
        Report report = accessible(user, id);
        return new ReportFile(report.filename, report.mediaType, report.content);
    }

    String reportText(Identity user, Long id) {
        return accessible(user, id).extractedText;
    }

    @Transactional
    public void share(Long patientId, Long id, Long doctorId, boolean grant) {
        lockOwnedReport(patientId, id);
        var existing = database.createQuery("""
            from ReportShare s where s.reportId = :report and s.doctorId = :doctor
            """, ReportShare.class).setParameter("report", id).setParameter("doctor", doctorId).getResultList();
        if (!grant) {
            existing.forEach(database::remove);
            return;
        }

        boolean booked = database.createQuery("""
            select count(v) from Visit v where v.patient.id = :patient and v.doctor.id = :doctor
                and v.status <> :cancelled
            """, Long.class).setParameter("patient", patientId).setParameter("doctor", doctorId)
            .setParameter("cancelled", Status.CANCELLED).getSingleResult() > 0;
        if (!booked) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Book an appointment with this doctor before sharing.");
        }
        if (existing.isEmpty()) {
            ReportShare share = new ReportShare();
            share.reportId = id;
            share.doctorId = doctorId;
            database.persist(share);
        }
    }

    @Transactional
    public void deleteReport(Long patientId, Long id) {
        Report report = lockOwnedReport(patientId, id);
        database.createQuery("delete from ReportShare s where s.reportId = :id").setParameter("id", id).executeUpdate();
        database.remove(report);
    }

    private History ownedHistory(Long patient, Long id) {
        History item = database.find(History.class, id);
        if (item == null || !patient.equals(item.patientId)) {
            throw Checks.missing();
        }
        return item;
    }

    private Report lockOwnedReport(Long patient, Long id) {
        Report report = database.find(Report.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (report == null || !patient.equals(report.patientId)) {
            throw Checks.missing();
        }
        return report;
    }

    private Report accessible(Identity viewer, Long id) {
        String filter = viewer.role() == Role.PATIENT ? "r.patientId = :viewer" :
            "exists (select s.id from ReportShare s where s.reportId = r.id and s.doctorId = :viewer)";
        return database.createQuery("from Report r where r.id = :id and " + filter, Report.class)
            .setParameter("id", id).setParameter("viewer", viewer.id()).getResultList().stream().findFirst()
            .orElseThrow(Checks::missing);
    }

    private String readPdf(byte[] bytes) throws IOException {
        try (var pdf = Loader.loadPDF(bytes)) {
            if (pdf.isEncrypted() || pdf.getNumberOfPages() > 20) {
                throw new IOException("Unsupported PDF.");
            }
            return new PDFTextStripper().getText(pdf);
        }
    }
}

@RestController
@RequestMapping("/api")
class RecordController {
    private final Records records;

    RecordController(Records records) {
        this.records = records;
    }

    @GetMapping("/patient/history")
    List<HistoryView> history(@AuthenticationPrincipal LoginUser user) {
        return records.history(user.identity.id());
    }

    @PostMapping("/patient/history")
    @ResponseStatus(HttpStatus.CREATED)
    HistoryView add(@AuthenticationPrincipal LoginUser user, @Valid @RequestBody HistoryEdit input) {
        return records.saveHistory(user.identity.id(), null, input);
    }

    @PutMapping("/patient/history/{id}")
    HistoryView update(@AuthenticationPrincipal LoginUser user, @PathVariable Long id, @Valid @RequestBody HistoryEdit input) {
        return records.saveHistory(user.identity.id(), id, input);
    }

    @DeleteMapping("/patient/history/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal LoginUser user, @PathVariable Long id) {
        records.deleteHistory(user.identity.id(), id);
    }

    @GetMapping("/reports")
    List<ReportView> reports(@AuthenticationPrincipal LoginUser user) {
        return records.reports(user.identity);
    }

    @PostMapping("/patient/reports")
    @ResponseStatus(HttpStatus.CREATED)
    ReportView upload(@AuthenticationPrincipal LoginUser user, @RequestParam MultipartFile file) {
        return records.upload(user.identity, file);
    }

    @GetMapping("/reports/{id}/download")
    ResponseEntity<byte[]> download(@AuthenticationPrincipal LoginUser user, @PathVariable Long id) {
        ReportFile file = records.download(user.identity, id);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename(file.filename(), StandardCharsets.UTF_8).build().toString())
            .body(file.bytes());
    }

    @PutMapping("/patient/reports/{id}/shares/{doctorId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void share(@AuthenticationPrincipal LoginUser user, @PathVariable Long id, @PathVariable Long doctorId) {
        records.share(user.identity.id(), id, doctorId, true);
    }

    @DeleteMapping("/patient/reports/{id}/shares/{doctorId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unshare(@AuthenticationPrincipal LoginUser user, @PathVariable Long id, @PathVariable Long doctorId) {
        records.share(user.identity.id(), id, doctorId, false);
    }

    @DeleteMapping("/patient/reports/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void removeReport(@AuthenticationPrincipal LoginUser user, @PathVariable Long id) {
        records.deleteReport(user.identity.id(), id);
    }
}
