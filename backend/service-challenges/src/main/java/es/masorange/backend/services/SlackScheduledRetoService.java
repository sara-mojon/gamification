package es.masorange.backend.services;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

@Service
public class SlackScheduledRetoService {

    private final String slackBotToken = "xoxb-tu-token-de-bot-aqui";
    private final String canalId = "C123456789";

    @Scheduled(cron = "0 0 10 */2 * *") // Cada 48h a las 10:00 (o @Scheduled(fixedRate = 10000) para probar)
    public void dispararRetoCada48h() {

        // Datos mockeados del reto (luego los sacarás de tu base de datos)
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

        enviarMensajeASlack(mensaje);
    }

    private void enviarMensajeASlack(String texto) {
        RestTemplate restTemplate = new RestTemplate();
        String url = "https://slack.com/api/chat.postMessage";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(slackBotToken);

        String textoLimpio = texto.replace("\"", "\\\"").replace("\n", "\\n");
        String payload = String.format("{\"channel\":\"%s\",\"text\":\"%s\"}", canalId, textoLimpio);

        HttpEntity<String> request = new HttpEntity<>(payload, headers);

        try {
            restTemplate.postForEntity(url, request, String.class);
            System.out.println("Reto con opciones enviado a Slack.");
        } catch (Exception e) {
            System.err.println("Error enviando el reto: " + e.getMessage());
        }
    }
}