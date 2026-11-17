package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.enums.RegionOrigine;
import gov.bf.ascelc.univers_audits.model.dto.response.GeographieResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.GeographieResponse.Commune;
import gov.bf.ascelc.univers_audits.model.dto.response.GeographieResponse.Province;
import gov.bf.ascelc.univers_audits.model.dto.response.GeographieResponse.Region;
import gov.bf.ascelc.univers_audits.model.entity.GeoCommune;
import gov.bf.ascelc.univers_audits.model.entity.GeoProvince;
import gov.bf.ascelc.univers_audits.model.entity.GeoRegion;
import gov.bf.ascelc.univers_audits.repository.GeoCommuneRepository;
import gov.bf.ascelc.univers_audits.repository.GeoProvinceRepository;
import gov.bf.ascelc.univers_audits.repository.GeoRegionRepository;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Référentiel géographique (Pays → Région → Province → Commune) et origine géographique d'un dossier.
 * Le référentiel ne change qu'avec une migration : l'arbre est construit une fois puis gardé en mémoire.
 */
@Service
@RequiredArgsConstructor
public class GeographieService {

    public static final String BURKINA_FASO = "Burkina Faso";
    private static final String CODE_BURKINA_FASO = "BF";

    private final GeoRegionRepository regions;
    private final GeoProvinceRepository provinces;
    private final GeoCommuneRepository communes;

    private volatile GeographieResponse arbreEnMemoire;

    /** Origine retenue pour un dossier : le pays, la localité la plus fine connue, et la zone publiée qui en découle. */
    public record OrigineGeographique(String pays, GeoRegion region, GeoProvince province, GeoCommune commune,
                                      RegionOrigine zone) {

        public static OrigineGeographique aucune() {
            return new OrigineGeographique(null, null, null, null, null);
        }
    }

    @Transactional(readOnly = true)
    public GeographieResponse arbre() {
        GeographieResponse en = arbreEnMemoire;
        if (en == null) {
            en = construireArbre();
            arbreEnMemoire = en;
        }
        return en;
    }

    private GeographieResponse construireArbre() {
        Collator ordre = Collator.getInstance(Locale.FRENCH);
        ordre.setStrength(Collator.PRIMARY);
        Comparator<String> parNom = ordre::compare;

        Map<UUID, List<GeoCommune>> communesParProvince = communes.findAll().stream()
                .collect(Collectors.groupingBy(c -> c.getProvince().getId()));
        Map<UUID, List<GeoProvince>> provincesParRegion = provinces.findAll().stream()
                .collect(Collectors.groupingBy(p -> p.getRegion().getId()));

        List<Region> lesRegions = regions.findAll().stream()
                .sorted(Comparator.comparing(GeoRegion::getLibelle, parNom))
                .map(r -> new Region(r.getId(), r.getLibelle(), r.getZonePublication().name(),
                        provincesParRegion.getOrDefault(r.getId(), List.of()).stream()
                                .sorted(Comparator.comparing(GeoProvince::getLibelle, parNom))
                                .map(p -> new Province(p.getId(), p.getLibelle(),
                                        communesParProvince.getOrDefault(p.getId(), List.of()).stream()
                                                .sorted(Comparator.comparing(GeoCommune::getLibelle, parNom))
                                                .map(c -> new Commune(c.getId(), c.getLibelle(), c.getCategorie()))
                                                .toList()))
                                .toList()))
                .toList();
        return new GeographieResponse(CODE_BURKINA_FASO, BURKINA_FASO, lesRegions);
    }

    /**
     * Vérifie l'origine indiquée et en déduit la chaîne complète et la zone publiée.
     * <ul>
     *   <li>Un pays autre que le Burkina Faso : diaspora ; la géographie du Burkina est alors ignorée.</li>
     *   <li>Sinon, la commune donne sa province et sa région, la province donne sa région ; toute incohérence est refusée.</li>
     *   <li>Pour un déclarant anonyme, seule la région est conservée : une commune pourrait l'identifier.</li>
     * </ul>
     */
    @Transactional(readOnly = true)
    public OrigineGeographique resoudreOrigine(String pays, UUID regionId, UUID provinceId, UUID communeId,
                                               boolean anonyme) {
        String paysSaisi = pays == null ? "" : pays.trim();
        boolean burkina = paysSaisi.equalsIgnoreCase(BURKINA_FASO);
        if (!paysSaisi.isEmpty() && !burkina) {
            return new OrigineGeographique(paysSaisi, null, null, null, RegionOrigine.DIASPORA);
        }

        GeoCommune commune = communeId == null ? null : trouver(communes.findById(communeId).orElse(null), "commune");
        GeoProvince province = provinceId == null ? null : trouver(provinces.findById(provinceId).orElse(null), "province");
        GeoRegion region = regionId == null ? null : trouver(regions.findById(regionId).orElse(null), "région");

        if (commune != null) {
            if (province != null && !province.getId().equals(commune.getProvince().getId())) {
                throw incoherence();
            }
            province = commune.getProvince();
        }
        if (province != null) {
            if (region != null && !region.getId().equals(province.getRegion().getId())) {
                throw incoherence();
            }
            region = province.getRegion();
        }
        if (anonyme) {
            province = null;
            commune = null;
        }
        if (region == null && !burkina) {
            return OrigineGeographique.aucune();
        }
        return new OrigineGeographique(BURKINA_FASO, region, province, commune,
                region == null ? null : region.getZonePublication());
    }

    private static <T> T trouver(T trouve, String niveau) {
        if (trouve == null) {
            throw new BusinessException("Localité inconnue : cette " + niveau + " n'existe pas dans le référentiel.");
        }
        return trouve;
    }

    private static BusinessException incoherence() {
        return new BusinessException("L'origine géographique est incohérente : la commune, la province et la région "
                + "indiquées ne se correspondent pas.");
    }
}
