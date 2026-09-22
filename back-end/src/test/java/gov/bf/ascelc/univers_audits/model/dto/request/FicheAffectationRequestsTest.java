package gov.bf.ascelc.univers_audits.model.dto.request;

import gov.bf.ascelc.univers_audits.enums.DecisionCgeAffectation;
import gov.bf.ascelc.univers_audits.enums.EtatAvancementAffectation;
import gov.bf.ascelc.univers_audits.enums.TypeDesignation;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class FicheAffectationRequestsTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void createRequest_rejetteDecisionCgeManquante() {
        FicheAffectationCreateRequest request = FicheAffectationCreateRequest.builder().build();
        Set<ConstraintViolation<FicheAffectationCreateRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
    }

    @Test
    void affectationRequest_rejetteTypeDesignationManquant() {
        FicheAffectationAffectationRequest request = FicheAffectationAffectationRequest.builder().build();
        Set<ConstraintViolation<FicheAffectationAffectationRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
    }

    @Test
    void suiviRequest_rejetteEtatAvancementManquant() {
        FicheAffectationSuiviRequest request = FicheAffectationSuiviRequest.builder().build();
        Set<ConstraintViolation<FicheAffectationSuiviRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
    }

    @Test
    void requests_acceptentDesValeursValides() {
        FicheAffectationCreateRequest create = FicheAffectationCreateRequest.builder()
                .decisionCge(DecisionCgeAffectation.AFFECTATION_DIRECTE_CGEA)
                .build();
        assertThat(validator.validate(create)).isEmpty();

        FicheAffectationAffectationRequest affectation = FicheAffectationAffectationRequest.builder()
                .typeDesignation(TypeDesignation.BRPD)
                .build();
        assertThat(validator.validate(affectation)).isEmpty();

        FicheAffectationSuiviRequest suivi = FicheAffectationSuiviRequest.builder()
                .etatAvancement(EtatAvancementAffectation.EN_COURS)
                .build();
        assertThat(validator.validate(suivi)).isEmpty();
    }
}
