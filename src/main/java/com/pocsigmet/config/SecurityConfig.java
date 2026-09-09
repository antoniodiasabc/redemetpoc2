package com.pocsigmet.config;

import com.pocsigmet.mongo.UserDocument;
import com.pocsigmet.mongo.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final UserRepository userRepository;
    private final LoginHandler loginHandler;
    private final LoginAttemptService loginAttemptService;

    public SecurityConfig(UserRepository userRepository, LoginHandler loginHandler,
                          LoginAttemptService loginAttemptService) {
        this.userRepository = userRepository;
        this.loginHandler = loginHandler;
        this.loginAttemptService = loginAttemptService;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            UserDocument u = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado: " + username));
            return User.withUsername(u.getUsername())
                .password(u.getPasswordHash())
                .roles(u.getRole())
                .build();
        };
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // A05 — Security headers
            .headers(headers -> headers
                .frameOptions(f -> f.deny())
                .contentTypeOptions(c -> {})
                .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .maxAgeInSeconds(31536000))
                .referrerPolicy(r -> r
                    .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .contentSecurityPolicy(csp -> csp
                    .policyDirectives("default-src 'self'; script-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net https://cesium.com; style-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net https://getbootstrap.com https://maxcdn.bootstrapcdn.com; img-src 'self' data: blob:; connect-src 'self'; worker-src blob:; font-src 'self' https://cdn.jsdelivr.net https://maxcdn.bootstrapcdn.com"))
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/api/auth/register").permitAll()
                .requestMatchers("/actuator/**").authenticated()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .successHandler(loginHandler)
                .failureHandler(loginHandler)
                .permitAll()
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/login")
                .permitAll()
            )
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/api/**", "/sigmet", "/create_sigmet", "/realcada_hsv", "/hsv_optimized", "/hsv_polygons")
            );
        return http.build();
    }
}
