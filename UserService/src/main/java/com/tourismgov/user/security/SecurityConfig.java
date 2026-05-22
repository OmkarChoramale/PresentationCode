package com.tourismgov.user.security;
 
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
 
@Configuration
@EnableWebSecurity
public class SecurityConfig {
 
    private static final String ADMIN = "ADMIN";
    private static final String MANAGER = "MANAGER";
    private static final String AUDITOR = "AUDITOR";
    private static final String COMPLIANCE = "COMPLIANCE";
 
    private final JwtAuthenticationFilter jwtAuthFilter;
 
    public SecurityConfig(JwtAuthenticationFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }
 
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // ==========================================
                // 1. AUTHENTICATION ENDPOINTS (/tourismgov/v1/auth)
                // ==========================================
                .requestMatchers(HttpMethod.POST, "/tourismgov/v1/auth/login", "/tourismgov/v1/auth/register").permitAll()
                .requestMatchers(HttpMethod.PUT, "/tourismgov/v1/auth/password/reset").permitAll()
                .requestMatchers(HttpMethod.PUT, "/tourismgov/v1/auth/password/update").authenticated()
 
                // ==========================================
                // 2. USER MANAGEMENT ENDPOINTS (/tourismgov/user/users)
                // ==========================================
                .requestMatchers(HttpMethod.POST, "/tourismgov/v1/users").permitAll()

                // ✅ SECURITY REMOVED: Anyone (any role or microservice) can now call GET /tourismgov/v1/users
                .requestMatchers(HttpMethod.GET, "/tourismgov/v1/users").permitAll()
                
                .requestMatchers(HttpMethod.POST, "/tourismgov/v1/audit-logs").permitAll()
 
                // Admin Approval and Deletion
                .requestMatchers(HttpMethod.PUT, "/tourismgov/v1/users/*/approve").hasRole(ADMIN)
                .requestMatchers(HttpMethod.DELETE, "/tourismgov/v1/users/*").hasRole(ADMIN)
 
                // ==========================================
                // 3. AUDIT LOG ENDPOINTS (/tourismgov/user/audit-logs)
                // ==========================================
                .requestMatchers(HttpMethod.GET, "/tourismgov/v1/audit-logs").hasAnyRole(ADMIN, AUDITOR, COMPLIANCE)
                .requestMatchers(HttpMethod.GET, "/tourismgov/v1/audit-logs/user/*").hasAnyRole(ADMIN, AUDITOR, COMPLIANCE)
                .requestMatchers(HttpMethod.GET, "/tourismgov/v1/audit-logs/action/*").hasAnyRole(ADMIN, AUDITOR, COMPLIANCE)
                .requestMatchers(HttpMethod.GET, "/tourismgov/v1/audit-logs/dates").hasAnyRole(ADMIN, AUDITOR, COMPLIANCE)
 
                // Fallback: Any other request must be authenticated
                .anyRequest().authenticated()
            );
 
        http.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
 
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
 
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}