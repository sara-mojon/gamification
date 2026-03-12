package es.masorange.backend.services;

import java.util.HashMap;
import java.util.Map;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

@Service
public class SlackIntegrationService {

    private final String slackBotToken = "xoxb-tu-token-de-bot-aqui";
    private final String canalId = "C123456789";

    @Scheduled(cron = "0 0 10 */2 * *")
    public void dispararRetoCada48h() {
        String tituloReto = "Validador de Sudokus";
        String dificultad = "Difícil";
        String descripcion = "Escribe un algoritmo eficiente que determine si un tablero de Sudoku de 9x9 actual es válido según las reglas clásicas.";
        String instrucciones = "Crea una función que reciba una matriz de 9x9. Devuelve 'true' si es válido o 'false' si no lo es.";
        String urlReto = "http://localhost:5173/entrenar/3";

        String mensaje = "¡NUEVO RETO GLOBAL DISPONIBLE!\n\n" +
                "*" + tituloReto + "* (" + dificultad + ")\n\n" +
                "*Descripción:*\n" + descripcion + "\n\n" +
                "*Instrucciones:*\n" + instrucciones + "\n\n" +
                "🛠️ *¿Cómo participar? Tienes dos opciones:*\n" +
                "1️⃣ *Desde Slack:* Responde a este mensaje subiendo tu archivo con la solución (ej. `solucion.py` o `solucion.java`).\n"
                +
                "2️⃣ *Desde la Web:* Entra en la plataforma y usa nuestro editor integrado: " + urlReto;

        enviarMensajeASlack(this.canalId, mensaje);
    }

    public void enviarMensajeASlack(String canalOIdUsuario, String texto) {
        RestTemplate restTemplate = new RestTemplate();
        String url = "https://slack.com/api/chat.postMessage";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(slackBotToken);

        Map<String, String> body = new HashMap<>();
        body.put("channel", canalOIdUsuario);
        body.put("text", texto);

        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

        try {
            restTemplate.postForEntity(url, request, String.class);
            System.out.println("Mensaje enviado a Slack (Destino: " + canalOIdUsuario + ")");
        } catch (Exception e) {
            System.err.println("Error enviando mensaje a Slack: " + e.getMessage());
        }
    }
}