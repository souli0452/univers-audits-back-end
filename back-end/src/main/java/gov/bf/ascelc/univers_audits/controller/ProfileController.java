package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.request.ChangePasswordRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.UpdateProfileRequest;
import gov.bf.ascelc.univers_audits.service.KeycloakAdminService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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

        keycloakAdminService.changePassword(jwt.getSubject(), req.newPassword());
        return ResponseEntity.ok(Map.of("message", "Mot de passe modifié"));
    }
}