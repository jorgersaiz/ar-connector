package com.vendor.arconnector.web;

import com.vendor.arconnector.domain.AppUser;
import com.vendor.arconnector.domain.Person;
import com.vendor.arconnector.domain.UserRole;
import com.vendor.arconnector.repository.AppUserRepository;
import com.vendor.arconnector.repository.PersonRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/** Gestión de usuarios del panel (solo managers). */
@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('MANAGER')")
public class UserController {

    public record UserDto(Long id, String email, String displayName, UserRole role, Long personId, boolean active) {
        static UserDto from(AppUser u) {
            return new UserDto(u.getId(), u.getEmail(), u.getDisplayName(), u.getRole(), u.personId(), u.isActive());
        }
    }

    public record CreateUserDto(@NotBlank @Email String email, @NotBlank @Size(min = 8) String password,
                                @NotBlank String displayName, @NotNull UserRole role, Long personId) {
    }

    public record UpdateUserDto(@NotBlank @Email String email, @NotBlank String displayName, @NotNull UserRole role,
                                Long personId, Boolean active, @Size(min = 8) String password) {
    }

    private final AppUserRepository userRepository;
    private final PersonRepository personRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public UserController(AppUserRepository userRepository, PersonRepository personRepository,
                          PasswordEncoder passwordEncoder, Clock clock) {
        this.userRepository = userRepository;
        this.personRepository = personRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @GetMapping
    public List<UserDto> list() {
        return userRepository.findAllWithPerson().stream().map(UserDto::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public UserDto create(@Valid @RequestBody CreateUserDto dto) {
        userRepository.findByEmailWithPerson(AppUser.normalizeEmail(dto.email())).ifPresent(u -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un usuario con ese email");
        });
        AppUser user = new AppUser(dto.email(), passwordEncoder.encode(dto.password()), dto.displayName().trim(),
                dto.role(), person(dto.personId()));
        return UserDto.from(userRepository.save(user));
    }

    @PutMapping("/{id}")
    @Transactional
    public UserDto update(@PathVariable Long id, @Valid @RequestBody UpdateUserDto dto) {
        AppUser user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado: " + id));
        userRepository.findByEmailWithPerson(AppUser.normalizeEmail(dto.email()))
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe otro usuario con ese email");
                });
        user.setEmail(dto.email());
        user.setDisplayName(dto.displayName().trim());
        user.setRole(dto.role());
        user.setPerson(person(dto.personId()));
        if (dto.active() != null) {
            user.setActive(dto.active());
        }
        if (dto.password() != null && !dto.password().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(dto.password()));
        }
        user.setUpdatedAt(Instant.now(clock));
        return UserDto.from(userRepository.save(user));
    }

    private Person person(Long personId) {
        if (personId == null) {
            return null;
        }
        return personRepository.findById(personId)
                .orElseThrow(() -> new IllegalArgumentException("Persona no encontrada: " + personId));
    }
}
