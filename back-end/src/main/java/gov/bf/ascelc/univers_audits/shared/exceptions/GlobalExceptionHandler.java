package gov.bf.ascelc.univers_audits.shared.exceptions;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            ResourceNotFoundException ex) {
        log.warn("Ressource introuvable : {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(
                        HttpStatus.NOT_FOUND.value(),
                        "NOT_FOUND",
                        ex.getMessage()));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(
            BusinessException ex) {
        log.warn("Règle métier violée : {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(
                        HttpStatus.BAD_REQUEST.value(),
                        "BUSINESS_ERROR",
                        ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex,
            WebRequest request) {

        Map<String, String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        FieldError::getDefaultMessage,
                        (existing, replacement) -> existing
                ));

        log.warn("Validation errors: {}", errors);

        ErrorResponse response = ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                "VALIDATION_ERROR",
                "Données invalides — vérifiez les champs"
        );

        response.setValidationErrors(errors);
        response.setPath(request.getDescription(false).replace("uri=", ""));

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(
            ObjectOptimisticLockingFailureException ex) {

        log.warn("Conflit de version détecté : {}", ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(
                        HttpStatus.CONFLICT.value(),
                        "VERSION_CONFLICT",
                        "Ce dossier a été modifié par un autre "
                                + "agent. Veuillez recharger la page "
                                + "et recommencer."));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException ex) {
        log.warn("Accès refusé : {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of(
                        HttpStatus.FORBIDDEN.value(),
                        "ACCESS_DENIED",
                        "Vous n'avez pas les droits nécessaires "
                                + "pour cette action"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(
            Exception ex) {
        log.error("Erreur interne inattendue : {}", ex.getMessage(), ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(
                        HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "INTERNAL_ERROR",
                        "Une erreur interne s'est produite. "
                                + "Contactez l'administrateur DDIC."));
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class ErrorResponse {
        private int status;
        private String code;
        private String message;
        private String timestamp;
        private String path;
        private Map<String, String> validationErrors;

        public static ErrorResponse of(int status, String code, String message) {
            ErrorResponse r = new ErrorResponse();
            r.setStatus(status);
            r.setCode(code);
            r.setMessage(message);
            r.setTimestamp(Instant.now().toString());
            return r;
        }

        public static ErrorResponse of(int status, String code, String message, String path) {
            ErrorResponse r = of(status, code, message);
            r.setPath(path);
            return r;
        }
    }
}