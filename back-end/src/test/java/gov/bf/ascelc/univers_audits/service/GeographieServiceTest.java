package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.enums.RegionOrigine;
import gov.bf.ascelc.univers_audits.model.entity.GeoCommune;
import gov.bf.ascelc.univers_audits.model.entity.GeoProvince;
import gov.bf.ascelc.univers_audits.model.entity.GeoRegion;
import gov.bf.ascelc.univers_audits.repository.GeoCommuneRepository;
import gov.bf.ascelc.univers_audits.repository.GeoProvinceRepository;
import gov.bf.ascelc.univers_audits.repository.GeoRegionRepository;
import gov.bf.ascelc.univers_audits.service.GeographieService.OrigineGeographique;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/** Origine géographique d'un dossier : Pays → Région → Province → Commune, et zone publiée qui en découle. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GeographieServiceTest {

    @Mock private GeoRegionRepository regions;
    @Mock private GeoProvinceRepository provinces;
    @Mock private GeoCommuneRepository communes;

    private GeographieService service;

    private GeoRegion kadiogo, guiriko, nakambe;
    private GeoProvince provinceKadiogo, houet, koulpelogo;
    private GeoCommune ouagadougou, bobo, yargatenga;

    @BeforeEach
    void setUp() {
        service = new GeographieService(regions, provinces, communes);
        kadiogo = region("Kadiogo", RegionOrigine.KADIOGO);
        guiriko = region("Guiriko", RegionOrigine.GUIRIKO);
        nakambe = region("Nakambé", RegionOrigine.AUTRES_REGIONS);
        provinceKadiogo = province("Kadiogo", kadiogo);
        houet = province("Houet", guiriko);
        koulpelogo = province("Koulpélogo", nakambe);
        ouagadougou = commune("Ouagadougou", provinceKadiogo);
        bobo = commune("Bobo-Dioulasso", houet);
        yargatenga = commune("Yargatenga", koulpelogo);
    }

    private GeoRegion region(String libelle, RegionOrigine zone) {
        GeoRegion r = GeoRegion.builder().id(UUID.randomUUID()).code(libelle.toUpperCase()).libelle(libelle)
                .zonePublication(zone).build();
        when(regions.findById(r.getId())).thenReturn(Optional.of(r));
        return r;
    }

    private GeoProvince province(String libelle, GeoRegion region) {
        GeoProvince p = GeoProvince.builder().id(UUID.randomUUID()).code(libelle.toUpperCase()).libelle(libelle)
                .region(region).build();
        when(provinces.findById(p.getId())).thenReturn(Optional.of(p));
        return p;
    }

    private GeoCommune commune(String libelle, GeoProvince province) {
        GeoCommune c = GeoCommune.builder().id(UUID.randomUUID()).code(libelle.toUpperCase()).libelle(libelle)
                .categorie("PLEIN").province(province).build();
        when(communes.findById(c.getId())).thenReturn(Optional.of(c));
        return c;
    }

    @Test
    void sansAucuneIndicationLOrigineResteVide() {
        OrigineGeographique o = service.resoudreOrigine(null, null, null, null, false);

        assertThat(o.pays()).isNull();
        assertThat(o.region()).isNull();
        assertThat(o.province()).isNull();
        assertThat(o.commune()).isNull();
        assertThat(o.zone()).isNull();
    }

    @Test
    void uneRegionSeuleDonneLeBurkinaFasoEtLaZonePubliee() {
        OrigineGeographique o = service.resoudreOrigine(null, nakambe.getId(), null, null, false);

        assertThat(o.pays()).isEqualTo("Burkina Faso");
        assertThat(o.region()).isEqualTo(nakambe);
        assertThat(o.zone()).isEqualTo(RegionOrigine.AUTRES_REGIONS);
    }

    @Test
    void laZonePublieeDuKadiogoEtDuGuirikoEstCelleDeLeurRegion() {
        assertThat(service.resoudreOrigine(null, kadiogo.getId(), null, null, false).zone())
                .isEqualTo(RegionOrigine.KADIOGO);
        assertThat(service.resoudreOrigine(null, guiriko.getId(), null, null, false).zone())
                .isEqualTo(RegionOrigine.GUIRIKO);
    }

    @Test
    void uneCommuneSeuleRetrouveSaProvinceEtSaRegion() {
        OrigineGeographique o = service.resoudreOrigine(null, null, null, bobo.getId(), false);

        assertThat(o.commune()).isEqualTo(bobo);
        assertThat(o.province()).isEqualTo(houet);
        assertThat(o.region()).isEqualTo(guiriko);
        assertThat(o.zone()).isEqualTo(RegionOrigine.GUIRIKO);
    }

    @Test
    void uneProvinceSeuleRetrouveSaRegion() {
        OrigineGeographique o = service.resoudreOrigine(null, null, koulpelogo.getId(), null, false);

        assertThat(o.province()).isEqualTo(koulpelogo);
        assertThat(o.region()).isEqualTo(nakambe);
        assertThat(o.commune()).isNull();
    }

    @Test
    void laChaineCompleteCoherenteEstAcceptee() {
        OrigineGeographique o = service.resoudreOrigine("Burkina Faso", nakambe.getId(), koulpelogo.getId(),
                yargatenga.getId(), false);

        assertThat(o.commune()).isEqualTo(yargatenga);
        assertThat(o.zone()).isEqualTo(RegionOrigine.AUTRES_REGIONS);
    }

    @Test
    void uneProvinceQuiNEstPasDeLaRegionIndiqueeEstRefusee() {
        assertThatThrownBy(() -> service.resoudreOrigine(null, kadiogo.getId(), houet.getId(), null, false))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void uneCommuneQuiNEstPasDeLaProvinceIndiqueeEstRefusee() {
        assertThatThrownBy(() -> service.resoudreOrigine(null, null, houet.getId(), yargatenga.getId(), false))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void uneCommuneQuiNEstPasDeLaRegionIndiqueeEstRefusee() {
        assertThatThrownBy(() -> service.resoudreOrigine(null, kadiogo.getId(), null, yargatenga.getId(), false))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void unIdentifiantInconnuEstRefuse() {
        UUID inconnu = UUID.randomUUID();
        when(regions.findById(inconnu)).thenReturn(Optional.empty());
        when(provinces.findById(inconnu)).thenReturn(Optional.empty());
        when(communes.findById(inconnu)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resoudreOrigine(null, inconnu, null, null, false))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.resoudreOrigine(null, null, inconnu, null, false))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.resoudreOrigine(null, null, null, inconnu, false))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void unPaysEtrangerDonneLaDiasporaEtEffaceLaGeographieDuBurkina() {
        OrigineGeographique o = service.resoudreOrigine("Côte d'Ivoire", kadiogo.getId(), houet.getId(),
                bobo.getId(), false);

        assertThat(o.pays()).isEqualTo("Côte d'Ivoire");
        assertThat(o.zone()).isEqualTo(RegionOrigine.DIASPORA);
        assertThat(o.region()).isNull();
        assertThat(o.province()).isNull();
        assertThat(o.commune()).isNull();
    }

    @Test
    void leBurkinaFasoSEcritLibrementSansTenirCompteDeLaCasseNiDesEspaces() {
        assertThat(service.resoudreOrigine("  burkina FASO ", kadiogo.getId(), null, null, false).zone())
                .isEqualTo(RegionOrigine.KADIOGO);
    }

    @Test
    void unPaysSansRegionEtSansEtrangerResteSansZone() {
        OrigineGeographique o = service.resoudreOrigine("Burkina Faso", null, null, null, false);

        assertThat(o.pays()).isEqualTo("Burkina Faso");
        assertThat(o.zone()).isNull();
    }

    @Test
    void pourUnDeclarantAnonymeSeulLaRegionEstConserveePourLeNePasIdentifier() {
        OrigineGeographique o = service.resoudreOrigine(null, null, koulpelogo.getId(), yargatenga.getId(), true);

        assertThat(o.region()).isEqualTo(nakambe);
        assertThat(o.province()).isNull();
        assertThat(o.commune()).isNull();
        assertThat(o.zone()).isEqualTo(RegionOrigine.AUTRES_REGIONS);
    }
}
