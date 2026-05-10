package es.masorange.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

        private final SlackAuthenticationFilter slackAuthenticationFilter;

        public SecurityConfig(SlackAuthenticationFilter slackAuthenticationFilter) {
                this.slackAuthenticationFilter = slackAuthenticationFilter;
        }

        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
                return http
                                .cors(Customizer.withDefaults())
                                .csrf(csrf -> csrf.disable())
                                .authorizeHttpRequests(auth -> auth
                                                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                                                .requestMatchers("/actuator/**").permitAll()
                                                .requestMatchers("/api/slack/**").permitAll()
                                                .requestMatchers("/api/users/ranking/**").permitAll()
                                                .requestMatchers("/api/users/slack/**").permitAll()
                                                .requestMatchers("/api/users/*/add-points").permitAll()
                                                .requestMatchers("/api/gamification/award").permitAll()
                                                .requestMatchers(HttpMethod.POST, "/api/users/link-slack").permitAll()
                                                .requestMatchers(HttpMethod.GET, "/api/users/streaks/at-risk")
                                                .permitAll()
                                                .anyRequest().authenticated())
                                .oauth2ResourceServer(oauth2 -> oauth2
                                                .jwt(jwt -> jwt.jwtAuthenticationConverter(
                                                                jwtAuthenticationConverter())))
                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                                .addFilterBefore(slackAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                                .build();
        }

        @Bean
        public CorsConfigurationSource corsConfigurationSource() {
                CorsConfiguration configuration = new CorsConfiguration();
                configuration.setAllowedOrigins(Arrays.asList(
                                "http://localhost:5173",
                                "https://frontend.sara.local",
                                "https://app.saramg.org",
                                "https://api.saramg.org"));
                configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
                configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "Accept"));
                configuration.setAllowCredentials(true);

                UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
                source.registerCorsConfiguration("/**", configuration);
                return source;
        }

        @Bean
        public JwtAuthenticationConverter jwtAuthenticationConverter() {
                JwtAuthenticationConverter converter = new JwtAuthenticationConverter();

                converter.setJwtGrantedAuthoritiesConverter(jwt -> {
                        // Buscamos el bloque "realm_access" en el token de Keycloak
                        Map<String, Object> realmAccess = jwt.getClaim("realm_access");

                        if (realmAccess == null || realmAccess.isEmpty()) {
                                return Collections.emptyList();
                        }

                        // Extraemos la lista de "roles"
                        @SuppressWarnings("unchecked")
                        List<String> roles = (List<String>) realmAccess.get("roles");

                        return roles.stream()
                                        .map(roleName -> {
                                                if (roleName.startsWith("ROLE_")) {
                                                        return new SimpleGrantedAuthority(roleName);
                                                } else {
                                                        return new SimpleGrantedAuthority("ROLE_" + roleName);
                                                }
                                        })
                                        .collect(Collectors.toList());
                });

                return converter;
        }
}