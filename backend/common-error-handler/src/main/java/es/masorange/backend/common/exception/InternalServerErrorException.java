package es.masorange.backend.common.exception;

public class InternalServerErrorException extends RuntimeException {
    public InternalServerErrorException(String message) { super(message); }
}
