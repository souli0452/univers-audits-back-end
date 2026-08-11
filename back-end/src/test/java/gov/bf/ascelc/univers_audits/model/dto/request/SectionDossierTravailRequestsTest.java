package gov.bf.ascelc.univers_audits.model.dto.request;

import gov.bf.ascelc.univers_audits.enums.OrganisationDetail;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le controleur SectionDossierTravailController n'a pas d'infrastructure
 * MockMvc/@WebMvcTest dans ce projet (aucun autre controleur n'en dispose) ;
 * on verifie donc directement, au niveau du DTO, que les annotations
 * jakarta.validation empechent les corps de requete vides/blancs qui
 * causaient auparavant des sections DETAIL sans libelle et des ecrasements
 * silencieux de organisationDetail.
 */
class SectionDossierTravailRequestsTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    void sectionDetailCreateRequest_rejetteLibelleManquant() {
        SectionDetailCreateRequest request = new SectionDetailCreateRequest();

        Set<ConstraintViolation<SectionDetailCreateRequest>> violations =
                validator.validate(request);

        assertThat(violations).isNotEmpty();
        assertThat(violations)
                .anyMatch(v -> v.getPropertyPath().toString().equals("libelle"));
    }

    @Test
    void sectionDetailCreateRequest_rejetteLibelleBlanc() {
        SectionDetailCreateRequest request = new SectionDetailCreateRequest();
        request.setLibelle("   ");

        Set<ConstraintViolation<SectionDetailCreateRequest>> violations =
                validator.validate(request);

        assertThat(violations).isNotEmpty();
    }

    @Test
    void sectionDetailCreateRequest_accepteLibelleRenseigne() {
        SectionDetailCreateRequest request = new SectionDetailCreateRequest();
        request.setLibelle("Site A");

        Set<ConstraintViolation<SectionDetailCreateRequest>> violations =
                validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void organisationDetailRequest_rejetteOrganisationDetailManquante() {
        OrganisationDetailRequest request = new OrganisationDetailRequest();

        Set<ConstraintViolation<OrganisationDetailRequest>> violations =
                validator.validate(request);

        assertThat(violations).isNotEmpty();
        assertThat(violations)
                .anyMatch(v -> v.getPropertyPath().toString().equals("organisationDetail"));
    }

    @Test
    void organisationDetailRequest_accepteValeurRenseignee() {
        OrganisationDetailRequest request = new OrganisationDetailRequest();
        request.setOrganisationDetail(OrganisationDetail.PAR_SITE);

        Set<ConstraintViolation<OrganisationDetailRequest>> violations =
                validator.validate(request);

        assertThat(violations).isEmpty();
    }
}
