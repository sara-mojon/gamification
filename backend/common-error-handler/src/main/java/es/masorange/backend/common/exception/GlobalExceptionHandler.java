package es.masorange.backend.common.exception;

import es.masorange.backend.common.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ====================================== //
    // ERRORES DEL CLIENTE (4xx) - Nivel WARN //
    // ====================================== //

    // 1. Petición incorrecta o datos inválidos (400)
    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(BadRequestException ex, WebRequest request) {
        log.warn("Petición incorrecta (400): {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    // 2. Errores de validación de Bean Validation (@Valid) (400)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        log.warn("Error de validación (400): Se recibieron datos de entrada inválidos.");

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(f -> errors.put(f.getField(), f.getDefaultMessage()));

        ErrorResponse body = ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.BAD_REQUEST.value())
            .error("Validation Error")
            .message("Existen errores en los datos enviados")
            .validationErrors(errors)
            .build();
        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    // 3. Recurso no encontrado (404)
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException ex, WebRequest request) {
        log.warn("Recurso no encontrado (404): {}", ex.getMessage());
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    // 4. Conflicto de estado o reglas de negocio (409)
    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException ex, WebRequest request) {
        log.warn("Conflicto detectado (409): {}", ex.getMessage());
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    // ======================================== //
    // ERRORES DEL SERVIDOR (5xx) - Nivel ERROR //
    // ======================================== //

    // 5. Error explícito del servidor (500)
    @ExceptionHandler(InternalServerErrorException.class)
    public ResponseEntity<ErrorResponse> handleInternalServerError(InternalServerErrorException ex, WebRequest request) {
        log.error("Error interno controlado (500): {}", ex.getMessage());
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), request);
    }

    // 6. Error de comunicación entre microservicios (502)
    @ExceptionHandler(ServiceCommunicationException.class)
    public ResponseEntity<ErrorResponse> handleServiceCommunication(ServiceCommunicationException ex, WebRequest request) {
        log.error("Fallo de comunicación externa (502): {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_GATEWAY, ex.getMessage(), request);
    }

    // 7. Error genérico inesperado (500) - Fallback
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGlobal(Exception ex, WebRequest request) {
        // MUY IMPORTANTE: Aquí pasamos 'ex' como segundo parámetro para imprimir el stack trace completo
        log.error("Error crítico inesperado en el servidor (500): ", ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Ha ocurrido un error inesperado en el servidor.", request);
    }

    private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status, String msg, WebRequest req) {
        ErrorResponse res = ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(status.value())
            .error(status.getReasonPhrase())
            .message(msg)
            .path(req.getDescription(false).replace("uri=", ""))
            .build();
        return new ResponseEntity<>(res, status);
    }

}