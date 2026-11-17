package com.byteharvest.master.config;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Phase 1 of authentication: HTTP Basic for everything a user can reach.
 *
 * <ul>
 *   <li>Every endpoint needs the login from {@link AuthProperties}, except {@code POST /heartbeat}.</li>
 *   <li>{@code POST /heartbeat} stays open for now because the storage nodes cannot authenticate yet.
 *       Phase 2 (a shared node token) closes it.</li>
 *   <li>No sessions and no CSRF token: the API is stateless and every request carries its own credentials.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(AuthProperties.class)
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                // Uses the CORS mapping already declared in AppConfig.
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Error pages must not be hidden behind a 401, or every 400/404 would look like a login problem.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/heartbeat").permitAll()
                        .anyRequest().authenticated())
                // The usual Basic challenge header makes browsers open their own login dialog.
                // The UI has its own login form, so this answers 401 without that header.
                .httpBasic(basic -> basic.authenticationEntryPoint(
                        (request, response, exception) -> response.setStatus(HttpServletResponse.SC_UNAUTHORIZED)));
        return http.build();
    }

    @Bean
    UserDetailsService userDetailsService(AuthProperties properties, PasswordEncoder passwordEncoder) {
        return new InMemoryUserDetailsManager(
                User.withUsername(properties.username())
                        .password(passwordEncoder.encode(properties.password()))
                        .roles("USER")
                        .build());
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
