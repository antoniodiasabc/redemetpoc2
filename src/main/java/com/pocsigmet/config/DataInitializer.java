package com.pocsigmet.config;

import com.pocsigmet.mongo.UserDocument;
import com.pocsigmet.mongo.UserRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataInitializer {

    @Bean
    ApplicationRunner createAdminIfAbsent(UserRepository repo, PasswordEncoder encoder) {
        return args -> {
            try {
                if (repo.findByUsername("admin").isEmpty()) {
                    String pwd = System.getenv().getOrDefault("ADMIN_PASSWORD", "changeme");
                    repo.save(new UserDocument("admin", encoder.encode(pwd), "ADMIN"));
                }
            } catch (Exception e) {
                // outra instância já inseriu — ignora
            }
        };
    }
}
