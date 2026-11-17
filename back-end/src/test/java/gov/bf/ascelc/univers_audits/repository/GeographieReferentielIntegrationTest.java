package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.enums.RegionOrigine;
import gov.bf.ascelc.univers_audits.model.dto.response.GeographieResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.GeographieResponse.Commune;
import gov.bf.ascelc.univers_audits.model.dto.response.GeographieResponse.Province;
import gov.bf.ascelc.univers_audits.model.dto.response.GeographieResponse.Region;
import gov.bf.ascelc.univers_audits.service.GeographieService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le référentiel chargé en base est celui du Ministère de l'Administration territoriale et de la Mobilité :
 * 17 régions, 47 provinces, 351 communes (149 de plein exercice, 200 de moyen exercice, 2 à statut particulier).
 */
@SpringBootTest
class GeographieReferentielIntegrationTest {

    @Autowired private GeographieService service;

    private GeographieResponse arbre() {
        return service.arbre();
    }

    private static Region region(GeographieResponse a, String libelle) {
        return a.regions().stream().filter(r -> r.libelle().equals(libelle)).findFirst().orElseThrow();
    }

    private static Province province(GeographieResponse a, String libelle) {
        return a.regions().stream().flatMap(r -> r.provinces().stream())
                .filter(p -> p.libelle().equals(libelle)).findFirst().orElseThrow();
    }

    private static List<Commune> communes(GeographieResponse a) {
        return a.regions().stream().flatMap(r -> r.provinces().stream()).flatMap(p -> p.communes().stream()).toList();
    }

    @Test
    void leBurkinaFasoCompteLesRegionsProvincesEtCommunesDuDocumentOfficiel() {
        GeographieResponse a = arbre();

        assertThat(a.libelle()).isEqualTo("Burkina Faso");
        assertThat(a.regions()).hasSize(17);
        assertThat(a.regions().stream().mapToInt(r -> r.provinces().size()).sum()).isEqualTo(47);
        assertThat(communes(a)).hasSize(351);
        assertThat(communes(a).stream().filter(c -> c.categorie().equals("PLEIN")).count()).isEqualTo(149);
        assertThat(communes(a).stream().filter(c -> c.categorie().equals("MOYEN")).count()).isEqualTo(200);
        assertThat(communes(a).stream().filter(c -> c.categorie().equals("STATUT_PARTICULIER")).map(Commune::libelle))
                .containsExactlyInAnyOrder("Ouagadougou", "Bobo-Dioulasso");
    }

    @Test
    void lesDixSeptRegionsPortentLeursNomsActuels() {
        assertThat(arbre().regions()).extracting(Region::libelle).containsExactly(
                "Bankui", "Djôrô", "Goulmou", "Guiriko", "Kadiogo", "Koulsé", "Liptako", "Nakambé", "Nando",
                "Nazinon", "Oubri", "Sirba", "Soum", "Sourou", "Tannounyan", "Tapoa", "Yaadga");
    }

    @Test
    void chaqueRegionAUneZonePublieeEtSeuleLesDeuxPlusGrandesSontIsolees() {
        GeographieResponse a = arbre();

        assertThat(region(a, "Kadiogo").zone()).isEqualTo(RegionOrigine.KADIOGO.name());
        assertThat(region(a, "Guiriko").zone()).isEqualTo(RegionOrigine.GUIRIKO.name());
        assertThat(a.regions().stream().filter(r -> r.zone().equals(RegionOrigine.AUTRES_REGIONS.name())).count())
                .isEqualTo(15);
        assertThat(a.regions()).allSatisfy(r -> assertThat(r.provinces()).isNotEmpty());
    }

    @Test
    void lesEffectifsDeCommunesParProvinceCorrespondentALaRealite() {
        GeographieResponse a = arbre();

        assertThat(province(a, "Houet").communes()).hasSize(13);
        assertThat(province(a, "Kénédougou").communes()).hasSize(13);
        assertThat(province(a, "Boulkiemdé").communes()).hasSize(15);
        assertThat(province(a, "Kadiogo").communes()).hasSize(7);
        assertThat(province(a, "Nayala").communes()).hasSize(6);
        assertThat(province(a, "Komondjari").communes()).hasSize(3);
    }

    @Test
    void uneCommuneEstRattacheeASaBonneProvince() {
        GeographieResponse a = arbre();

        assertThat(province(a, "Koulpélogo").communes()).extracting(Commune::libelle).contains("Yargatenga");
        assertThat(province(a, "Kourittenga").communes()).extracting(Commune::libelle).doesNotContain("Yargatenga");
        assertThat(province(a, "Kénédougou").communes()).extracting(Commune::libelle).contains("Orodara");
        assertThat(province(a, "Houet").communes()).extracting(Commune::libelle).contains("Bobo-Dioulasso", "Léna");
        assertThat(region(a, "Liptako").provinces()).extracting(Province::libelle)
                .containsExactly("Oudalan", "Séno", "Yahga");
    }

    @Test
    void lesNomsIdentiquesDansDeuxProvincesRestentDistincts() {
        GeographieResponse a = arbre();

        // « Thiou » existe dans le Boulkiemdé et dans le Yatenga ; « Boussouma » dans le Boulgou et le Sandbondtenga.
        assertThat(province(a, "Boulkiemdé").communes()).extracting(Commune::libelle).contains("Thiou");
        assertThat(province(a, "Yatenga").communes()).extracting(Commune::libelle).contains("Thiou");
        assertThat(communes(a).stream().map(Commune::id).distinct().count()).isEqualTo(351);
    }

    @Test
    void lesListesSontTrieesParOrdreAlphabetiqueSansTenirCompteDesAccents() {
        GeographieResponse a = arbre();

        assertThat(province(a, "Kadiogo").communes()).extracting(Commune::libelle).containsExactly(
                "Komki-Ipala", "Komsilga", "Koubri", "Ouagadougou", "Pabré", "Saaba", "Tanghin-Dassouri");
    }
}
