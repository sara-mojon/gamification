package es.masorange.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.springframework.lang.NonNull;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@Component
public class SlackAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(SlackAuthenticationFilter.class);

    @Value("${slack.signing-secret:}")
    private String slackSigningSecret;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        // 1. Solo actuamos si la ruta es de Slack
        if (!request.getRequestURI().startsWith("/api/slack")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Si es una ruta de Slack pero el microservicio no tiene configurado el
        // secreto, bloqueamos por seguridad
        if (slackSigningSecret == null || slackSigningSecret.isEmpty()) {
            log.error("Falta configurar slack.signing-secret en el application.properties");
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Configuración de Slack incompleta");
            return;
        }

        // 2. Extraer cabeceras de seguridad
        String signature = request.getHeader("X-Slack-Signature");
        String timestamp = request.getHeader("X-Slack-Request-Timestamp");

        if (signature == null || timestamp == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Faltan cabeceras de validación de Slack");
            return;
        }

        // 3. Envolvemos la request con la clase CachedBodyHttpServletRequest para poder
        // leer el cuerpo y conservar los parámetros
        CachedBodyHttpServletRequest wrappedRequest = new CachedBodyHttpServletRequest(request);
        String rawPayload = new String(wrappedRequest.getCachedBody(), StandardCharsets.UTF_8);

        // 4. USAMOS LA LÓGICA DE VALIDACIÓN INTERNA
        if (!isValidSlackRequest(signature, timestamp, rawPayload)) {
            log.error("⚠️ ATENCIÓN: Se ha bloqueado una petición en {} que fingía ser de Slack.", request.getRequestURI());
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Firma de Slack inválida");
            return;
        }

        // 5. ¡Firma válida! Dejamos pasar la petición
        filterChain.doFilter(wrappedRequest, response);
    }

    private boolean isValidSlackRequest(String slackSignature, String timestamp, String rawBody) {
        long timeTime = Long.parseLong(timestamp);
        long currentTime = System.currentTimeMillis() / 1000;
        long diferenciaSegundos = Math.abs(currentTime - timeTime);

        if (diferenciaSegundos > 300) {
            log.warn("Validación Slack fallida: Posible ataque de repetición. Timestamp expirado hace {} segundos",
                    diferenciaSegundos);
            return false;
        }

        String sigBaseString = "v0:" + timestamp + ":" + rawBody;

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(slackSigningSecret.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256");
            mac.init(secretKeySpec);

            byte[] hash = mac.doFinal(sigBaseString.getBytes(StandardCharsets.UTF_8));
            String mySignature = "v0=" + HexFormat.of().formatHex(hash);
            boolean isValid = mySignature.equals(slackSignature);

            if (!isValid) {
                log.warn("Validación Slack fallida: Las firmas no coinciden.");
            } else {
                log.info("Firma de Slack validada correctamente");
            }

            return isValid;

        } catch (Exception e) {
            log.error("Error crítico al calcular la firma de Slack: {}", e.getMessage(), e);
            return false;
        }
    }
}