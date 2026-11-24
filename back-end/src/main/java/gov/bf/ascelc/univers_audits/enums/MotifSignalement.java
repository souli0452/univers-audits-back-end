package gov.bf.ascelc.univers_audits.enums;

import java.util.Optional;

/**
 * Motif choisi par le citoyen : l'une des allégations officielles (table type_infraction), ou « Autre » avec un texte
 * libre. Le citoyen voit le nom officiel et, une fois son choix fait, une phrase qui l'explique en mots simples.
 * L'agent confirme ou corrige l'allégation à l'étude d'opportunité : seul son choix entre dans les statistiques.
 * Le nom de chaque valeur est le code de l'allégation (sauf AUTRE, qui n'en a pas).
 */
public enum MotifSignalement {
    CONFLIT_INTERET("Conflit d’intérêt",
            "Une personne décide d’une affaire dans laquelle elle a un intérêt personnel."),
    BLANCHIMENT_CAPITAUX("Blanchiment de capitaux",
            "De l’argent d’origine illégale est caché ou « nettoyé » (comptes, achats, sociétés)."),
    FAVORITISME("Favoritisme",
            "Un marché, un poste ou un avantage donné à un proche, sans respecter les règles."),
    ENRICHISSEMENT_ILLICITE("Enrichissement illicite",
            "Quelqu’un vit bien au-dessus de ses moyens, sans pouvoir l’expliquer."),
    ABUS_DE_FONCTION("Abus de fonction",
            "Un responsable utilise son pouvoir pour son intérêt ou pour nuire."),
    DEMANDE_INTERVENTION_DETTES("Demande d’intervention pour règlement de dettes",
            "Vous demandez l’aide de l’ASCE-LC pour récupérer une somme qu’on vous doit."),
    FRAUDE("Fraude",
            "Fausse déclaration ou fausse facture pour tromper l’administration."),
    CONCUSSION("Concussion",
            "Un agent public réclame une somme qui n’est pas due."),
    DELIT_APPARENCE("Délit d’apparence",
            "Des faits qui laissent croire à une faute, même sans preuve directe."),
    CONFLIT_FONCIER("Conflit foncier",
            "Litige sur un terrain : double vente, attribution d’une parcelle."),
    CORRUPTION("Corruption",
            "On demande ou on offre de l’argent ou un cadeau pour un service ou pour fermer les yeux."),
    DETOURNEMENT_MALVERSATIONS("Détournement et malversations financières",
            "De l’argent ou des biens publics ont disparu ou sont mal utilisés."),
    STELLIONAT("Stellionat",
            "Vente ou hypothèque d’un bien qui n’appartient pas au vendeur, ou déjà cédé à un autre."),
    DYSFONCTIONNEMENT("Dysfonctionnement",
            "Un service ne fonctionne pas comme il le devrait : lenteurs, règles non appliquées."),
    FAUX_ET_USAGE_DE_FAUX("Faux et usage de faux",
            "Faux document, fausse signature, ou utilisation d’un faux."),
    AUTRE("Autre",
            "Aucune de ces allégations ne convient : décrivez les faits en quelques mots.");

    private final String libelle;
    private final String explication;

    MotifSignalement(String libelle, String explication) {
        this.libelle = libelle;
        this.explication = explication;
    }

    /** Nom officiel de l'allégation, tel que le citoyen le lit. */
    public String libelle() {
        return libelle;
    }

    /** Une phrase en mots simples, affichée quand le citoyen choisit ce motif. */
    public String explication() {
        return explication;
    }

    /** Code (table type_infraction) de l'allégation officielle correspondante ; vide pour « Autre ». */
    public Optional<String> allegation() {
        return this == AUTRE ? Optional.empty() : Optional.of(name());
    }
}
