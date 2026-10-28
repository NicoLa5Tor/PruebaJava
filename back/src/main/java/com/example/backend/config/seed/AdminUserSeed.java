package com.example.backend.config.seed;

import com.example.backend.config.auth.AuthProperties;
import com.example.backend.model.user.User;
import com.example.backend.model.user.UserRole;
import com.example.backend.repository.user.UserRepository;
import java.util.Locale;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdminUserSeed implements ApplicationRunner {
    private final UserRepository userRepository;
    private final AuthProperties properties;

    public AdminUserSeed(UserRepository userRepository, AuthProperties properties) {
        this.userRepository = userRepository;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String adminEmail = properties.adminEmail();
        if (adminEmail == null || adminEmail.isBlank()) {
            return;
        }
        User admin = userRepository.findByEmailIgnoreCase(adminEmail).orElseGet(() -> newAdmin(adminEmail));
        admin.addRole(UserRole.ADMIN);
        userRepository.save(admin);
    }

    private User newAdmin(String adminEmail) {
        User user = new User();
        user.setEmail(adminEmail.trim().toLowerCase(Locale.ROOT));
        user.setDisplayName("Administrator");
        user.setEmailVerified(true);
        user.addRole(UserRole.ADMIN);
        return user;
    }
}
