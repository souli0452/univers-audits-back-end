package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.request.ChangePasswordRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.UpdateProfileRequest;
import gov.bf.ascelc.univers_audits.service.EmailService;
import gov.bf.ascelc.univers_audits.service.KeycloakAdminService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
    private final EmailService         emailService;

    @PutMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> updateProfile(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateProfileRequest req) {

        keycloakAdminService.updateUserProfile(
                jwt.getSubject(),
                req.firstName(),
                req.lastName(),
                req.email());
        return ResponseEntity.ok(Map.of("message", "Profil mis à jour"));
    }

    @PutMapping("/password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> changePassword(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ChangePasswordRequest req) {

        if (!req.newPassword().equals(req.confirmPassword())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message",
                            "Les mots de passe ne correspondent pas"));
        }

        String username = jwt.getClaimAsString("preferred_username");
        if (!keycloakAdminService.verifyCurrentPassword(username, req.currentPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Mot de passe actuel incorrect"));
        }

        keycloakAdminService.changePassword(jwt.getSubject(), req.newPassword());

        String email = jwt.getClaimAsString("email");
        String given = jwt.getClaimAsString("given_name");
        String family = jwt.getClaimAsString("family_name");
        String fullName = ((given != null ? given : "")
                + " " + (family != null ? family : "")).trim();
        emailService.sendPasswordChangedConfirmation(email, fullName);

        return ResponseEntity.ok(Map.of("message", "Mot de passe modifié"));
    }
}