package com.carebridge;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import jakarta.persistence.EntityManager;

interface AccountRepository extends JpaRepository<Account, Long> {
    java.util.Optional<Account> findByEmail(String email);
}

record Identity(Long id, String name, String email, Role role) {
    static Identity of(Account account) {
        return new Identity(account.id, account.name, account.email, account.role);
    }
}

class LoginUser extends User {
    final Identity identity;

    LoginUser(Account account) {
        super(account.email, account.passwordHash,
            java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + account.role)));
        identity = Identity.of(account);
    }
}

record Signup(
    @NotBlank @Size(max = 100) String name,
    @NotBlank @Email @Size(max = 190) String email,
    @NotBlank @Size(min = 10, max = 72) String password,
    @NotNull Role role,
    @Size(max = 256) String doctorCode,
    @Size(max = 100) String specialty,
    @Size(max = 120) String hospital
) {}

@Service
class Accounts implements UserDetailsService {
    private final AccountRepository repository;
    private final EntityManager database;
    private final PasswordEncoder passwords;
    private final String doctorCode;

    Accounts(AccountRepository repository, EntityManager database, PasswordEncoder passwords,
             @Value("${carebridge.doctor-code}") String doctorCode) {
        this.repository = repository;
        this.database = database;
        this.passwords = passwords;
        this.doctorCode = doctorCode;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        return repository.findByEmail(email.strip().toLowerCase(Locale.ROOT))
            .map(LoginUser::new)
            .orElseThrow(() -> new UsernameNotFoundException("Invalid sign-in"));
    }

    @Transactional
    public Identity register(Signup input) {
        if (input.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password exceeds 72 UTF-8 bytes.");
        }

        Account account = new Account();
        account.name = input.name().strip();
        account.email = input.email().strip().toLowerCase(Locale.ROOT);
        account.passwordHash = passwords.encode(input.password());
        account.role = input.role();

        if (account.role == Role.DOCTOR) {
            boolean validCode = !doctorCode.isBlank() && input.doctorCode() != null
                && java.security.MessageDigest.isEqual(doctorCode.getBytes(StandardCharsets.UTF_8),
                    input.doctorCode().getBytes(StandardCharsets.UTF_8));
            if (!validCode) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "A valid doctor invitation is required.");
            }
            Doctor doctor = new Doctor();
            doctor.account = repository.saveAndFlush(account);
            doctor.specialty = Checks.required(input.specialty(), "Specialty");
            doctor.hospital = Checks.required(input.hospital(), "Hospital");
            doctor.bio = "";
            database.persist(doctor);
        } else {
            Patient patient = new Patient();
            patient.account = repository.saveAndFlush(account);
            database.persist(patient);
        }
        return Identity.of(account);
    }
}

@RestController
@RequestMapping("/api")
class AccountController {
    private final Accounts accounts;

    AccountController(Accounts accounts) {
        this.accounts = accounts;
    }

    @GetMapping("/health")
    java.util.Map<String, String> health() {
        return java.util.Map.of("status", "up");
    }

    @GetMapping("/auth/csrf")
    CsrfToken csrf(CsrfToken token) {
        token.getToken();
        return token;
    }

    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    Identity register(@Valid @RequestBody Signup input) {
        return accounts.register(input);
    }

    @GetMapping("/auth/me")
    Identity me(@AuthenticationPrincipal LoginUser user) {
        return user.identity;
    }
}
