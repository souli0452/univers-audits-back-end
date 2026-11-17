package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.enums.DossierStatus;
import gov.bf.ascelc.univers_audits.enums.RegionOrigine;
import gov.bf.ascelc.univers_audits.enums.SubmissionMode;
import gov.bf.ascelc.univers_audits.enums.TypeSaisine;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.model.entity.GeoCommune;
import gov.bf.ascelc.univers_audits.service.GeographieService;
import gov.bf.ascelc.univers_audits.service.GeographieService.OrigineGeographique;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/** Avec la vraie base : l'origine d'un dossier est résolue depuis le référentiel, enregistrée puis relue. */
@SpringBootTest
@Transactional
class DossierOrigineGeographiqueIntegrationTest {

    @Autowired private DossierRepository dossiers;
    @Autowired private GeoCommuneRepository communes;
    @Autowired private GeographieService geographie;
    @Autowired private EntityManager em;

    private GeoCommune commune(String libelle, String province) {
        return communes.findAll().stream()
                .filter(c -> c.getLibelle().equals(libelle) && c.getProvince().getLibelle().equals(province))
                .findFirst().orElseThrow();
    }

    private Dossier enregistrer(String code, OrigineGeographique o) {
        Dossier d = dossiers.saveAndFlush(Dossier.builder().accessCode(code).object("Origine de test")
                .type(TypeSaisine.DENONCIATION).submissionMode(SubmissionMode.WEB_FORM).status(DossierStatus.SOUMIS)
                .originePays(o.pays()).origineRegion(o.region()).origineProvince(o.province())
                .origineCommune(o.commune()).regionOrigine(o.zone()).build());
        em.clear();
        return dossiers.findById(d.getId()).orElseThrow();
    }

    @Test
    void uneCommuneEnregistreeRetrouveSaProvinceSaRegionEtSaZonePubliee() {
        GeoCommune yargatenga = commune("Yargatenga", "Koulpélogo");

        Dossier relu = enregistrer("GEO-1", geographie.resoudreOrigine(null, null, null, yargatenga.getId(), false));

        assertThat(relu.getOriginePays()).isEqualTo("Burkina Faso");
        assertThat(relu.getOrigineCommune().getLibelle()).isEqualTo("Yargatenga");
        assertThat(relu.getOrigineProvince().getLibelle()).isEqualTo("Koulpélogo");
        assertThat(relu.getOrigineRegion().getLibelle()).isEqualTo("Nakambé");
        assertThat(relu.getRegionOrigine()).isEqualTo(RegionOrigine.AUTRES_REGIONS);
    }

    @Test
    void uneCommuneDuKadiogoEstComptéeDansLaZoneKadiogo() {
        GeoCommune ouaga = commune("Ouagadougou", "Kadiogo");

        Dossier relu = enregistrer("GEO-2", geographie.resoudreOrigine(null, null, null, ouaga.getId(), false));

        assertThat(relu.getRegionOrigine()).isEqualTo(RegionOrigine.KADIOGO);
    }

    @Test
    void unDeclarantAnonymeNeGardeQueLaRegionEnBase() {
        GeoCommune bobo = commune("Bobo-Dioulasso", "Houet");

        Dossier relu = enregistrer("GEO-3", geographie.resoudreOrigine(null, null, null, bobo.getId(), true));

        assertThat(relu.getOrigineRegion().getLibelle()).isEqualTo("Guiriko");
        assertThat(relu.getOrigineProvince()).isNull();
        assertThat(relu.getOrigineCommune()).isNull();
        assertThat(relu.getRegionOrigine()).isEqualTo(RegionOrigine.GUIRIKO);
    }

    @Test
    void unPaysEtrangerEstEnregistreCommeDiasporaSansGeographieDuBurkina() {
        Dossier relu = enregistrer("GEO-4", geographie.resoudreOrigine("Côte d'Ivoire", null, null, null, false));

        assertThat(relu.getOriginePays()).isEqualTo("Côte d'Ivoire");
        assertThat(relu.getRegionOrigine()).isEqualTo(RegionOrigine.DIASPORA);
        assertThat(relu.getOrigineRegion()).isNull();
    }
}
