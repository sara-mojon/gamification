package es.masorange.backend.common.exception;

public class ConflictException extends RuntimeException {
    public ConflictException(String message) { super(message); }
}