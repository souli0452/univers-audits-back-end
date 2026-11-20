package gov.bf.ascelc.univers_audits.enums;

import java.util.Optional;

/**
 * Motif choisi par le citoyen, en mots simples (facultatif). Il renvoie à l'une des allégations officielles du
 * tableau publié (table type_infraction), que l'agent confirme ou corrige à l'étude d'opportunité : seul son choix
 * entre dans les statistiques. Les qualifications juridiques techniques (blanchiment, stellionat, concussion, délit
 * d'apparence, conflit d'intérêt, dettes) ne sont volontairement pas proposées au citoyen.
 */
public enum MotifSignalement {
    CORRUPTION("On m'a demandé de l'argent ou un cadeau",
            "Pour rendre un service, accélérer un dossier ou fermer les yeux.", "CORRUPTION"),
    DETOURNEMENT("De l'argent ou des biens publics ont disparu",
            "Caisse vidée, matériel emporté, dépenses sans justificatif.", "DETOURNEMENT_MALVERSATIONS"),
    FAVORITISME("Un marché ou un recrutement donné à un proche",
            "Sans concurrence ou sans respecter les règles.", "FAVORITISME"),
    FAUX("Un faux document ou une fausse signature",
            "Faux diplôme, fausse pièce, signature imitée.", "FAUX_ET_USAGE_DE_FAUX"),
    FRAUDE("Une fausse facture ou une fausse déclaration",
            "Chiffres truqués pour tromper l'administration.", "FRAUDE"),
    ENRICHISSEMENT("Quelqu'un vit bien au-dessus de ses moyens",
            "Maisons, voitures ou train de vie qui ne s'expliquent pas par son salaire.", "ENRICHISSEMENT_ILLICITE"),
    ABUS_DE_FONCTION("Un responsable abuse de son pouvoir",
            "Il utilise sa fonction pour son intérêt ou pour nuire.", "ABUS_DE_FONCTION"),
    FONCIER("Un terrain vendu deux fois, un litige sur une parcelle",
            "Conflit lié à un terrain ou à une attribution de parcelle.", "CONFLIT_FONCIER"),
    DYSFONCTIONNEMENT("Un service ne fonctionne pas comme il le devrait",
            "Lenteurs, absences, règles non appliquées.", "DYSFONCTIONNEMENT"),
    AUTRE("Autre chose, ou je ne sais pas",
            "L'agent qualifiera lui-même les faits.", null);

    private final String libelle;
    private final String explication;
    private final String allegation;

    MotifSignalement(String libelle, String explication, String allegation) {
        this.libelle = libelle;
        this.explication = explication;
        this.allegation = allegation;
    }

    /** Phrase en mots simples, telle que le citoyen la lit. */
    public String libelle() {
        return libelle;
    }

    /** Une ligne d'explication sous le libellé. */
    public String explication() {
        return explication;
    }

    /** Code (table type_infraction) de l'allégation officielle correspondante ; vide pour « Autre ». */
    public Optional<String> allegation() {
        return Optional.ofNullable(allegation);
    }
}
