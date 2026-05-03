package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.service.KeycloakAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final KeycloakAdminService keycloakAdminService;

    /** Modifier prénom / nom / email */
    @PutMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> updateProfile(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody UpdateProfileRequest req) {

        String keycloakId = jwt.getSubject();
        keycloakAdminService.updateUserProfile(
                keycloakId, req.firstName(), req.lastName(), req.email()
        );
        return ResponseEntity.ok(Map.of("message", "Profil mis à jour"));
    }

    /** Changer le mot de passe */
    @PutMapping("/password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> changePassword(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody ChangePasswordRequest req) {

        if (!req.newPassword().equals(req.confirmPassword())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Les mots de passe ne correspondent pas"));
        }
        if (req.newPassword().length() < 8) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Le mot de passe doit contenir au moins 8 caractères"));
        }

        String keycloakId = jwt.getSubject();
        keycloakAdminService.changePassword(keycloakId, req.newPassword());
        return ResponseEntity.ok(Map.of("message", "Mot de passe modifié"));
    }

    public record UpdateProfileRequest(
            String firstName,
            String lastName,
            String email
    ) {}

    public record ChangePasswordRequest(
            String newPassword,
            String confirmPassword
    ) {}
}