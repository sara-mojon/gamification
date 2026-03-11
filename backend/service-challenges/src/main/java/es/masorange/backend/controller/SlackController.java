package es.masorange.backend.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/slack")
public class SlackController {

    // ==========================================================
    // 1. EVENTOS DE SLACK - Para el challenge y archivos
    // ==========================================================
    @PostMapping(value = "/events", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> handleEvents(@RequestBody Map<String, Object> payload) {

        System.out.println("Payload de Slack (Eventos): " + payload);

        // REGLA DE ORO: Responder al challenge
        if ("url_verification".equals(payload.get("type"))) {
            Map<String, Object> response = new HashMap<>();
            response.put("challenge", payload.get("challenge"));
            return ResponseEntity.ok(response);
        }

        // Aquí procesaremos los archivos subidos (solucion.py) más adelante
        return ResponseEntity.ok().build();
    }

    // ==========================================================
    // 2. COMANDOS DE SLACK - Para /reto, /rank, etc.
    // ==========================================================
    @PostMapping(value = "/commands", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public Map<String, Object> handleCommands(
            @RequestParam("command") String command,
            @RequestParam("user_name") String userName,
            @RequestParam(value = "text", defaultValue = "") String text) {

        System.out
                .println("Comando recibido: " + command + " ejecutado por @" + userName + " con texto: '" + text + "'");

        Map<String, Object> response = new HashMap<>();

        switch (command) {
            case "/reto":
                String tituloReto = "Contar vocales";
                String urlReto = "http://localhost:5173/entrenar/4";
                String mensajeReto = "¡Hola @" + userName + "! Aquí tienes un reto rápido para calentar:\n\n" +
                        "👉 *" + tituloReto + "* (Fácil)\n" +
                        "💻 Resuélvelo aquí: " + urlReto;

                response.put("response_type", "ephemeral");
                response.put("text", mensajeReto);
                break;

            case "/rank":
                // 1. MOCKEAMOS LOS DATOS: Aquí en el futuro harás:
                // Usuario user = usuarioRepository.findByUsername(userName);
                // Y calcularás su posición consultando a la BBDD.
                int posicionMock = 7;
                int puntosMock = 1250;
                int retosMock = 42;

                String mensajeRank = "🏆 *Tu perfil de Codewars* 🏆\n" +
                        "👤 *Jugador:* @" + userName + "\n" +
                        "🏅 *Clasificación Global:* #" + posicionMock + "\n" +
                        "⭐ *Puntos de honor:* " + puntosMock + " px\n" +
                        "💻 *Katas resueltas:* " + retosMock;

                response.put("response_type", "ephemeral");
                response.put("text", mensajeRank);
                break;

            case "/duelo":
                String oponente = text.trim();

                if (oponente.isEmpty() || !oponente.startsWith("@")) {
                    response.put("response_type", "ephemeral");
                    response.put("text", "⚠️ *¡Error!* Para lanzar un duelo debes etiquetar a tu oponente.\n" +
                            "💡 *Ejemplo de uso:* `/duelo @nombre_usuario`");
                    break;
                }

                if (oponente.equals("@" + userName)) {
                    response.put("response_type", "ephemeral");
                    response.put("text", "🤡 No puedes batirte en duelo contigo mismo. ¡Busca un rival de verdad!");
                    break;
                }

                String mensajeDuelo = "🔥 *¡NUEVO DESAFÍO EN LA ARENA!* 🔥\n\n" +
                        "El desarrollador @" + userName + " ha lanzado el guante a " + oponente + ".\n" +
                        "⚔️ *" + oponente
                        + "*, la afrenta es pública. ¿Aceptas el duelo de código para defender tu honor?";

                response.put("response_type", "in_channel");
                response.put("text", mensajeDuelo);
                break;

            case "/hint":
                response.put("response_type", "ephemeral");
                response.put("text", "🤖 Conectando con la IA para tu pista...");
                break;

            default:
                response.put("response_type", "ephemeral");
                response.put("text", "❌ Comando no reconocido.");
                break;
        }

        return response;
    }
}