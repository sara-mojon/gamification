package es.masorange.backend.controller;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import es.masorange.backend.model.Challenge;
import es.masorange.backend.repository.ChallengeRepository;
import es.masorange.backend.services.ChallengeService;
import es.masorange.backend.services.SlackIntegrationService;

@RestController
@RequestMapping("/api/slack")
public class SlackController {

    private final SlackIntegrationService slackService;
    private final ChallengeRepository challengeRepository;

    public SlackController(SlackIntegrationService slackService, ChallengeRepository challengeRepository) {
        this.slackService = slackService;
        this.challengeRepository = challengeRepository;
    }

    // ==========================================================
    // 1. EVENTOS DE SLACK - Para el challenge y archivos
    // ==========================================================
    @PostMapping(value = "/events", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> handleEvents(@RequestBody Map<String, Object> payload) {

        // 1. EL CHALLENGE
        if ("url_verification".equals(payload.get("type"))) {
            Map<String, Object> response = new HashMap<>();
            response.put("challenge", payload.get("challenge"));
            return ResponseEntity.ok(response);
        }

        // 2. RECIBIR MENSAJES DIRECTOS
        if ("event_callback".equals(payload.get("type"))) {
            Map<String, Object> event = (Map<String, Object>) payload.get("event");

            if (event != null && "message".equals(event.get("type")) && event.get("bot_id") == null) {
                String idUsuario = (String) event.get("user");

                // Usas tu método modificado para responderle por privado
                slackService.enviarMensajeASlack(idUsuario, "¡Hola! Estoy listo para procesar tus katas.");
            }
        }

        return ResponseEntity.ok().build();
    }

    // ==========================================================
    // 2. COMANDOS DE SLACK - Para /reto, /rank, etc.
    // ==========================================================
    @PostMapping(value = "/commands", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public Map<String, Object> handleCommands(
            @RequestParam("command") String command,
            @RequestParam("user_name") String userName,
            @RequestParam("user_id") String userId,
            @RequestParam(value = "text", defaultValue = "") String text) {

        System.out
                .println("Comando recibido: " + command + " ejecutado por @" + userName + " con texto: '" + text + "'");

        Map<String, Object> response = new HashMap<>();

        switch (command) {
            case "/reto":
                Optional<Challenge> retoOpt = challengeRepository.findRandomChallenge();

                if (retoOpt.isPresent()) {
                    Challenge reto = retoOpt.get();
                    // Construimos la URL que apunta al frontend
                    String urlReto = "http://localhost:5173/entrenar/" + reto.getId();

                    String mensajeReto = "¡Hola @" + userName + "! Aquí tienes un reto para hoy:\n\n" +
                            "🚀 *" + reto.getName() + "* (" + reto.getRank() + ")\n" +
                            "📝 " + reto.getDescription() + "\n\n" +
                            "💻 *Resuélvelo aquí:* " + urlReto;

                    response.put("response_type", "ephemeral");
                    response.put("text", mensajeReto);
                } else {
                    response.put("text", "No hay retos disponibles en este momento.");
                }
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
                String oponenteRaw = text.trim();

                // 1. Validamos que haya escrito la @ (ya sea en formato literal o ID)
                if (oponenteRaw.isEmpty() || (!oponenteRaw.startsWith("@") && !oponenteRaw.startsWith("<@"))) {
                    response.put("response_type", "ephemeral");
                    response.put("text", "⚠️ *¡Error!* Para lanzar un duelo debes etiquetar a tu oponente.\n" +
                            "💡 *Ejemplo de uso:* `/duelo @nombre_usuario`");
                    break;
                }

                String idOponente;
                String oponenteParaMensaje;

                // 2. Extraemos el destinatario dependiendo de cómo lo mande Slack
                if (oponenteRaw.startsWith("<@")) {
                    // Si viene con ID oculto: <@U12345678|mario>
                    int finId = oponenteRaw.indexOf("|");
                    if (finId == -1)
                        finId = oponenteRaw.indexOf(">");
                    idOponente = oponenteRaw.substring(2, finId);
                    oponenteParaMensaje = "<@" + idOponente + ">"; // Para que brille en azul
                } else {
                    // Si viene en texto plano: @mario
                    idOponente = oponenteRaw; // Usaremos "@mario" como destinatario
                    oponenteParaMensaje = oponenteRaw;
                }

                // 3. Validamos el autodesafío (comparando tanto por ID como por nombre)
                if (idOponente.equals(userId) || oponenteRaw.equals("@" + userName)) {
                    response.put("response_type", "ephemeral");
                    response.put("text", "🤡 No puedes batirte en duelo contigo mismo. ¡Busca un rival de verdad!");
                    break;
                }

                // 4. Preparamos los textos
                String mensajePrivadoOponente = "⚔️ *¡HAS SIDO DESAFIADO!* ⚔️\n" +
                        "El usuario <@" + userId + "> te ha retado a un duelo de código.\n" +
                        "¿Aceptas el reto? Prepara tu teclado...";

                String mensajeDueloPublico = "🔥 *¡NUEVO DESAFÍO EN LA ARENA!* 🔥\n\n" +
                        "El desarrollador <@" + userId + "> ha lanzado un desafío a " + oponenteParaMensaje + ".\n" +
                        "La afrenta es pública. ¡Que gane el mejor código!";

                // 5. Enviamos el mensaje directo
                slackService.enviarMensajeASlack(idOponente, mensajePrivadoOponente);

                // 6. Respondemos en el canal público
                response.put("response_type", "in_channel");
                response.put("text", mensajeDueloPublico);
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