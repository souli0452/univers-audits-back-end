package gov.bf.ascelc.univers_audits.shared.exceptions;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Gestionnaire global des exceptions
 * Capture TOUTES les exceptions de l'application et retourne des réponses HTTP appropriées
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
}
