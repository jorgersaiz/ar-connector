package com.vendor.arconnector.security;

import com.vendor.arconnector.domain.AppUser;
import com.vendor.arconnector.domain.UserRole;
import com.vendor.arconnector.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Crea el primer usuario MANAGER si la tabla app_user está vacía, con las
 * credenciales de auth.bootstrap.* (variables ADMIN_EMAIL / ADMIN_PASSWORD).
 * Solo actúa una vez; después los usuarios se gestionan desde el panel.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AppUserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;

    public AdminBootstrap(AppUserRepository repository, PasswordEncoder passwordEncoder,
                          @Value("${auth.bootstrap.email:}") String email,
                          @Value("${auth.bootstrap.password:}") String password) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (repository.count() > 0) {
            return;
        }
        if (email.isBlank() || password.isBlank()) {
            log.warn("No hay usuarios del panel y no se ha configurado auth.bootstrap.email/password: nadie podrá entrar");
            return;
        }
        repository.save(new AppUser(email, passwordEncoder.encode(password), "Administrador", UserRole.MANAGER, null));
        log.warn("Creado el usuario MANAGER inicial {} con la contraseña de arranque. Cámbiala desde el panel.", email);
    }
}
