package gov.bf.ascelc.univers_audits.shared.utils;

/**
 * Coordonnées institutionnelles ASCE-LC — source unique, alignée sur le
 * formulaire papier officiel (référence du 2026-07-30). Les valeurs
 * précédemment codées en dur dans PdfExportService et EmailService étaient
 * incorrectes et divergentes entre elles ; celle-ci fait foi.
 */
public final class AsceLcInstitutionalInfo {

    private AsceLcInstitutionalInfo() {}

    public static final String ADDRESS =
            "01 BP 617 Ouagadougou 01 BF – Ouaga 2000 – Avenue Pascal ZAGRE";
    public static final String PHONE = "(00226) 25 37 40 56";
    public static final String EMAIL_INFO = "info@asce-lc.bf";
    public static final String EMAIL_CONTACT = "contact@asce-lc.bf";
    public static final String WEBSITE = "www.asce-lc.bf";
    public static final String NUMERO_VERT = "80 00 11 02";
    public static final String SLOGAN =
            "Au nom de notre intégrité, combattons la corruption !";
}
