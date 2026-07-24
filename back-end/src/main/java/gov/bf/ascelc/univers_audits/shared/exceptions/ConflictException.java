package gov.bf.ascelc.univers_audits.shared.exceptions;

// Le code HTTP (409) est fixé par GlobalExceptionHandler, qui intercepte
// explicitement ce type — @ResponseStatus ici ne serait jamais consulté.
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}