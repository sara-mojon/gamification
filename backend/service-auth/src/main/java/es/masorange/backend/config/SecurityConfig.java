package es.masorange.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

        private final JwtAuthenticationFilter jwtAuthenticationFilter;

        public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
                this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        }

        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
                return http
                                // 1. Desactivamos CSRF (necesario para APIs REST, si no bloqueará los POST)
                                .csrf(csrf -> csrf.disable())

                                // 2. Configuración de rutas
                                .authorizeHttpRequests(auth -> auth
                                                .requestMatchers("/auth/login", "/auth/signin", "/auth/users")
                                                .permitAll()
                                                .anyRequest().authenticated() // El resto requiere autenticación
                                )

                                // 3. Hacemos que la sesión sea STATELESS (sin estado)
                                // Esto es vital para APIs con JWT: no se guardan cookies de sesión en el
                                // servidor
                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                                // 4. NO añadimos .formLogin(), así evitamos que genere la página HTML por
                                // defecto

                                // Añadimos nuestro filtro ANTES del filtro estándar de usuario/pass
                                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                                .build();
        }
}