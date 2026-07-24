package gov.bf.ascelc.univers_audits.shared.exceptions;

// Le code HTTP (404) est fixé par GlobalExceptionHandler, qui intercepte
// explicitement ce type — @ResponseStatus ici ne serait jamais consulté.
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
