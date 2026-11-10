package gov.bf.ascelc.univers_audits.shared.exceptions;

import gov.bf.ascelc.univers_audits.shared.exceptions.ConflictException;
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

    /** Route inexistante : sans ce handler, le catch-all la transformerait en 500. */
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(
            org.springframework.web.servlet.resource.NoResourceFoundException ex) {
        log.warn("Route introuvable : {}", ex.getResourcePath());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(
                        HttpStatus.NOT_FOUND.value(),
                        "NOT_FOUND",
                        "Ressource introuvable"));
    }

    /** Corps illisible, valeur d'énumération inconnue, paramètre manquant ou mal typé : erreur du client (400). */
    @ExceptionHandler({
            org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception ex) {
        log.warn("Requête invalide : {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(
                        HttpStatus.BAD_REQUEST.value(),
                        "INVALID_REQUEST",
                        "Requête invalide — vérifiez les données envoyées"));
    }

    /** Méthode HTTP non prévue sur cette route (405). */
    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(
            org.springframework.web.HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ErrorResponse.of(
                        HttpStatus.METHOD_NOT_ALLOWED.value(),
                        "METHOD_NOT_ALLOWED",
                        "Méthode non autorisée sur cette ressource"));
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


    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException ex) {
        log.warn("Conflit détecté : {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(
                        HttpStatus.CONFLICT.value(),
                        "CONFLICT",
                        ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex) {
        log.warn("Argument invalide : {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(
                        HttpStatus.BAD_REQUEST.value(),
                        "INVALID_ARGUMENT",
                        "Paramètre invalide"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex) {
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

        public static ErrorResponse of(int status, String code,
                                       String message, String path) {
            ErrorResponse r = of(status, code, message);
            r.setPath(path);
            return r;
        }
    }
}