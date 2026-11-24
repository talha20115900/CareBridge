package com.carebridge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class WorkflowTest {
    private static final HttpServer PROVIDER = provider();

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        String mysql = System.getenv("TEST_MYSQL_URL");
        registry.add("spring.datasource.url", () -> mysql == null
            ? "jdbc:h2:mem:carebridge;MODE=MySQL;DB_CLOSE_DELAY=-1" : mysql);
        registry.add("spring.datasource.username", () -> mysql == null ? "sa" : System.getenv("TEST_MYSQL_USER"));
        registry.add("spring.datasource.password", () -> mysql == null ? "" : System.getenv("TEST_MYSQL_PASSWORD"));
        registry.add("carebridge.doctor-code", () -> "test-invite");
        registry.add("carebridge.ai.key", () -> "test-provider-key");
        registry.add("carebridge.ai.url", () -> "http://127.0.0.1:" + PROVIDER.getAddress().getPort() + "/chat");
        registry.add("AI_DEMO_CODE", () -> "ai-test");
    }

    @AfterAll
    static void stopProvider() {
        PROVIDER.stop(0);
    }

    private static HttpServer provider() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/chat", exchange -> {
                exchange.getRequestBody().readAllBytes();
                byte[] response = """
                    {"choices":[{"finish_reason":"stop","message":{"content":"{\\"ids\\":[0]}"}}]}
                    """.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                try (var body = exchange.getResponseBody()) {
                    body.write(response);
                }
            });
            server.start();
            return server;
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
    }

    @Test
    void clinicJourneyProtectsOwnershipAndImplementsAllAiTools() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/register").contentType("application/json").content("{}"))
            .andExpect(status().isForbidden());

        Actor doctor = account("Doctor", "DOCTOR");
        Actor patient = account("Patient", "PATIENT");
        Actor other = account("Other", "PATIENT");
        mvc.perform(get("/api/doctor/profile").session(patient.session())).andExpect(status().isForbidden());
        write(doctor, put("/api/doctor/profile"), Map.of("specialty", "General Medicine",
            "hospital", "Demo Hospital", "bio", "Fictional clinic profile.")).andExpect(status().isOk());
        mvc.perform(get("/api/doctors").session(patient.session())).andExpect(status().isOk())
            .andExpect(jsonPath("$[0].hospital").value("Demo Hospital"));

        LocalDate day = LocalDate.now(Visits.CLINIC_ZONE).plusDays(1);
        while (day.getDayOfWeek().getValue() >= 6) day = day.plusDays(1);
        String slot = result(mvc.perform(get("/api/doctors/{id}/slots", doctor.id())
            .param("day", day.toString()).session(patient.session())).andExpect(status().isOk())).get(0).asText();
        Map<String, Object> booking = Map.of("doctorId", doctor.id(), "startsAt", slot, "reason", "Demo follow-up");
        long visit = result(write(patient, post("/api/patient/appointments"), booking).andExpect(status().isCreated()))
            .get("id").asLong();
        write(other, post("/api/patient/appointments"), booking).andExpect(status().isConflict());
        write(other, patch("/api/appointments/{id}", visit).param("status", "CANCELLED"), null)
            .andExpect(status().isNotFound());
        mvc.perform(get("/api/appointments").session(doctor.session())).andExpect(status().isOk())
            .andExpect(jsonPath("$[0].patientName").value("Patient"));

        long history = result(write(patient, post("/api/patient/history"),
            Map.of("eventDate", "2025-01-02", "title", "Check-in", "details", "Demo entry."))
            .andExpect(status().isCreated())).get("id").asLong();
        write(other, delete("/api/patient/history/{id}", history), null).andExpect(status().isNotFound());
        write(patient, put("/api/patient/history/{id}", history),
            Map.of("eventDate", "2025-01-02", "title", "Updated check-in", "details", "Demo entry."))
            .andExpect(jsonPath("$.title").value("Updated check-in"));

        MockMultipartFile file = new MockMultipartFile("file", "demo.txt", "text/plain",
            "Hemoglobin 13.2 g/dL\nReference interval 12.0 to 16.0 g/dL".getBytes(StandardCharsets.UTF_8));
        long report = result(write(patient, multipart("/api/patient/reports").file(file), null)
            .andExpect(status().isCreated())).get("id").asLong();
        mvc.perform(get("/api/reports").session(patient.session())).andExpect(status().isOk())
            .andExpect(jsonPath("$[0].filename").value("demo.txt"));
        mvc.perform(get("/api/reports/{id}/download", report).session(doctor.session())).andExpect(status().isNotFound());
        write(patient, put("/api/patient/reports/{id}/shares/{doctor}", report, doctor.id()), null)
            .andExpect(status().isNoContent());
        mvc.perform(get("/api/reports").session(doctor.session())).andExpect(jsonPath("$[0].patient").value("Patient"));
        mvc.perform(get("/api/reports/{id}/download", report).session(doctor.session())).andExpect(status().isOk());
        mvc.perform(get("/api/reports/{id}/download", report).session(other.session())).andExpect(status().isNotFound());

        ai(patient, Map.of("feature", "SUMMARY", "referenceId", report, "demoCode", "ai-test"))
            .andExpect(status().isBadRequest());
        ai(patient, Map.of("feature", "SUMMARY", "referenceId", report, "consent", true, "demoCode", "ai-test"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items[0]").value("Hemoglobin 13.2 g/dL"));
        ai(other, Map.of("feature", "SUMMARY", "referenceId", report, "consent", true, "demoCode", "ai-test"))
            .andExpect(status().isNotFound());
        ai(patient, Map.of("feature", "INFORMATION", "topic", "Reading a lab report", "demoCode", "ai-test"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items[0]").isString());
        ai(patient, Map.of("feature", "TERMINOLOGY", "topic", "CBC", "demoCode", "ai-test"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items[0]").isString());
        ai(patient, Map.of("feature", "PREPARATION", "referenceId", visit, "demoCode", "ai-test"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items[0]").isString());

        write(patient, delete("/api/patient/reports/{id}/shares/{doctor}", report, doctor.id()), null)
            .andExpect(status().isNoContent());
        mvc.perform(get("/api/reports/{id}/download", report).session(doctor.session())).andExpect(status().isNotFound());
        write(patient, delete("/api/patient/reports/{id}", report), null).andExpect(status().isNoContent());
        write(patient, delete("/api/patient/history/{id}", history), null).andExpect(status().isNoContent());
        write(patient, patch("/api/appointments/{id}", visit).param("status", "CANCELLED"), null)
            .andExpect(status().isOk());

        Actor rival = account("Rival", "PATIENT");
        try (var pool = Executors.newFixedThreadPool(2)) {
            CountDownLatch start = new CountDownLatch(1);
            Future<Integer> a = pool.submit(() -> {
                start.await();
                return write(other, post("/api/patient/appointments"), booking).andReturn().getResponse().getStatus();
            });
            Future<Integer> b = pool.submit(() -> {
                start.await();
                return write(rival, post("/api/patient/appointments"), booking).andReturn().getResponse().getStatus();
            });
            start.countDown();
            assertEquals(java.util.Set.of(201, 409), java.util.Set.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS)));
        }

        write(patient, post("/api/auth/logout"), null).andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    private ResultActions ai(Actor actor, Map<String, Object> body) throws Exception {
        return write(actor, post("/api/ai"), body);
    }

    private Actor account(String name, String role) throws Exception {
        String email = UUID.randomUUID() + "@example.test";
        String password = "CareBridge-test-824";
        Actor guest = new Actor(0, new MockHttpSession());
        long id = result(write(guest, post("/api/auth/register"), Map.of("name", name, "email", email,
            "password", password, "role", role, "doctorCode", "test-invite", "specialty", "Family Medicine",
            "hospital", "Demo Hospital")).andExpect(status().isCreated())).get("id").asLong();
        HttpSession authenticated = write(guest, post("/api/auth/login").param("email", email)
            .param("password", password), null).andExpect(status().isNoContent()).andReturn().getRequest().getSession(false);
        return new Actor(id, (MockHttpSession) authenticated);
    }

    private ResultActions write(Actor actor, MockHttpServletRequestBuilder request, Object payload) throws Exception {
        JsonNode csrf = result(mvc.perform(get("/api/auth/csrf").session(actor.session())).andExpect(status().isOk()));
        request.session(actor.session()).header(csrf.get("headerName").asText(), csrf.get("token").asText());
        if (payload != null) request.contentType("application/json").content(json.writeValueAsString(payload));
        return mvc.perform(request);
    }

    private JsonNode result(ResultActions action) throws Exception {
        return json.readTree(action.andReturn().getResponse().getContentAsByteArray());
    }

    private record Actor(long id, MockHttpSession session) {}
}
