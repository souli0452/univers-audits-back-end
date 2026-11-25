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
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            java.time.format.DateTimeParseException.class})
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

    /**
     * Corps envoyé avec un type de contenu que la route n'accepte pas : erreur du client (415), pas une erreur interne.
     * Sans ce gestionnaire, la réponse générale renvoyait un 500 et écrivait une trace complète dans les journaux.
     */
    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedMediaType(
            org.springframework.web.HttpMediaTypeNotSupportedException ex) {
        log.warn("Type de contenu non pris en charge : {}", ex.getContentType());
        return ResponseEntity
                .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ErrorResponse.of(
                        HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(),
                        "UNSUPPORTED_MEDIA_TYPE",
                        "Type de contenu non pris en charge par cette ressource"));
    }

    /** Fichier au-delà de la taille autorisée : erreur du client (413), avec un message compréhensible. */
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleUploadTooLarge(
            org.springframework.web.multipart.MaxUploadSizeExceededException ex) {
        log.warn("Envoi trop volumineux : {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ErrorResponse.of(
                        HttpStatus.PAYLOAD_TOO_LARGE.value(),
                        "PAYLOAD_TOO_LARGE",
                        "Fichier trop volumineux"));
    }

    /** Envoi de fichiers mal formé (pas au format multipart, partie attendue absente) : erreur du client (400). */
    @ExceptionHandler({
            org.springframework.web.multipart.MultipartException.class,
            org.springframework.web.multipart.support.MissingServletRequestPartException.class})
    public ResponseEntity<ErrorResponse> handleMalformedUpload(Exception ex) {
        log.warn("Envoi de fichiers invalide : {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(
                        HttpStatus.BAD_REQUEST.value(),
                        "INVALID_REQUEST",
                        "Requête invalide — vérifiez les données envoyées"));
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

    /**
     * Refus de la base : une donnée invalide (classe SQL 22 : texte trop long, caractère nul…) ou une valeur déjà
     * utilisée est une erreur du client ; tout le reste demeure une erreur interne. Le détail de la base n'est jamais renvoyé.
     */
    @ExceptionHandler(org.springframework.dao.DataAccessException.class)
    public ResponseEntity<ErrorResponse> handleDatabase(org.springframework.dao.DataAccessException ex) {
        String etat = null;
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof java.sql.SQLException sql && sql.getSQLState() != null) {
                etat = sql.getSQLState();
            }
        }
        if (etat != null && etat.startsWith("22")) {
            return handleBadRequest(ex);
        }
        if ("23505".equals(etat)) {
            return handleConflict(new ConflictException("Cette valeur est déjà utilisée"));
        }
        return handleGeneral(ex);
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