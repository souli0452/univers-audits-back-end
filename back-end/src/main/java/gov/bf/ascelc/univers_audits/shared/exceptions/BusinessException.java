package gov.bf.ascelc.univers_audits.shared.exceptions;

// Le code HTTP (400) est fixé par GlobalExceptionHandler, qui intercepte
// explicitement ce type — @ResponseStatus ici ne serait jamais consulté.
public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}