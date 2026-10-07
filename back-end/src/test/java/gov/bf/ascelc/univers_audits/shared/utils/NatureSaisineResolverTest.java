package gov.bf.ascelc.univers_audits.shared.utils;

import gov.bf.ascelc.univers_audits.enums.QualiteDeclarant;
import gov.bf.ascelc.univers_audits.enums.TypeDeclarant;
import gov.bf.ascelc.univers_audits.enums.TypeSaisine;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NatureSaisineResolverTest {

    private final NatureSaisineResolver resolver = new NatureSaisineResolver();

    @Test
    void citizenVictimeNotAnonymous_returnsPlainte() {
        TypeSaisine result = resolver.resolve(
                TypeDeclarant.CITIZEN, QualiteDeclarant.VICTIME, false);
        assertThat(result).isEqualTo(TypeSaisine.PLAINTE);
    }

    @Test
    void citizenRepresentantVictimeNotAnonymous_returnsPlainte() {
        TypeSaisine result = resolver.resolve(
                TypeDeclarant.CITIZEN, QualiteDeclarant.REPRESENTANT_VICTIME, false);
        assertThat(result).isEqualTo(TypeSaisine.PLAINTE);
    }

    @Test
    void citizenTemoinNotAnonymous_returnsDenonciation() {
        TypeSaisine result = resolver.resolve(
                TypeDeclarant.CITIZEN, QualiteDeclarant.TEMOIN, false);
        assertThat(result).isEqualTo(TypeSaisine.DENONCIATION);
    }

    @Test
    void citizenTemoinAnonymous_returnsDenonciation() {
        TypeSaisine result = resolver.resolve(
                TypeDeclarant.CITIZEN, QualiteDeclarant.TEMOIN, true);
        assertThat(result).isEqualTo(TypeSaisine.DENONCIATION);
    }

    @Test
    void companyVictime_returnsPlainte() {
        TypeSaisine result = resolver.resolve(
                TypeDeclarant.COMPANY, QualiteDeclarant.VICTIME, false);
        assertThat(result).isEqualTo(TypeSaisine.PLAINTE);
    }

    @Test
    void associationTemoin_returnsDenonciation() {
        TypeSaisine result = resolver.resolve(
                TypeDeclarant.ASSOCIATION, QualiteDeclarant.TEMOIN, false);
        assertThat(result).isEqualTo(TypeSaisine.DENONCIATION);
    }

    @Test
    void anonymousDeclarantTemoin_returnsDenonciation() {
        TypeSaisine result = resolver.resolve(
                TypeDeclarant.ANONYMOUS, QualiteDeclarant.TEMOIN, true);
        assertThat(result).isEqualTo(TypeSaisine.DENONCIATION);
    }

    @Test
    void anonymousDeclarantVictime_isTreatedAsDenonciation() {
        assertThat(resolver.resolve(TypeDeclarant.ANONYMOUS, QualiteDeclarant.VICTIME, true))
                .isEqualTo(TypeSaisine.DENONCIATION);
    }

    @Test
    void anonymousDeclarantRepresentantVictime_isTreatedAsDenonciation() {
        assertThat(resolver.resolve(TypeDeclarant.ANONYMOUS, QualiteDeclarant.REPRESENTANT_VICTIME, true))
                .isEqualTo(TypeSaisine.DENONCIATION);
    }

    @Test
    void victimeButAnonymousFlagTrue_isTreatedAsDenonciation() {
        assertThat(resolver.resolve(TypeDeclarant.CITIZEN, QualiteDeclarant.VICTIME, true))
                .isEqualTo(TypeSaisine.DENONCIATION);
    }

    @Test
    void representantVictimeButAnonymousFlagTrue_isTreatedAsDenonciation() {
        assertThat(resolver.resolve(TypeDeclarant.COMPANY, QualiteDeclarant.REPRESENTANT_VICTIME, true))
                .isEqualTo(TypeSaisine.DENONCIATION);
    }

    @Test
    void victimeIdentified_stillRequiresNamedComplaint() {
        assertThat(resolver.resolve(TypeDeclarant.CITIZEN, QualiteDeclarant.VICTIME, false))
                .isEqualTo(TypeSaisine.PLAINTE);
    }

    @Test
    void citizenWithoutQuality_throws() {
        assertThatThrownBy(() -> resolver.resolve(
                TypeDeclarant.CITIZEN, null, false))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void publicAuthority_qualityIgnored_returnsSignalement() {
        assertThat(resolver.resolve(TypeDeclarant.PUBLIC_AUTHORITY, null, false))
                .isEqualTo(TypeSaisine.SIGNALEMENT);
        assertThat(resolver.resolve(
                TypeDeclarant.PUBLIC_AUTHORITY, QualiteDeclarant.VICTIME, false))
                .isEqualTo(TypeSaisine.SIGNALEMENT);
    }

    @Test
    void asceSelfReferral_qualityIgnored_returnsAutoSaisine() {
        assertThat(resolver.resolve(TypeDeclarant.ASCE_SELF_REFERRAL, null, false))
                .isEqualTo(TypeSaisine.AUTO_SAISINE);
    }

    @Test
    void typeDeclarantNull_throws() {
        assertThatThrownBy(() -> resolver.resolve(null, QualiteDeclarant.TEMOIN, false))
                .isInstanceOf(BusinessException.class);
    }
}
