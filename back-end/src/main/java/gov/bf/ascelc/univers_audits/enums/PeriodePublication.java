package gov.bf.ascelc.univers_audits.enums;

import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;

/** Période sur laquelle la situation des plaintes et dénonciations est publiée : trimestre, semestre ou année. */
public enum PeriodePublication {
    T1(1, 3), T2(4, 6), T3(7, 9), T4(10, 12),
    S1(1, 6), S2(7, 12),
    ANNEE(1, 12);

    private final int premierMois;
    private final int dernierMois;

    PeriodePublication(int premierMois, int dernierMois) {
        this.premierMois = premierMois;
        this.dernierMois = dernierMois;
    }

    /** Lit le code reçu (sans tenir compte de la casse) ; refuse tout code inconnu. */
    public static PeriodePublication depuis(String code) {
        if (code != null) {
            for (PeriodePublication p : values()) {
                if (p.name().equalsIgnoreCase(code.trim())) {
                    return p;
                }
            }
        }
        throw new BusinessException("Période inconnue : choisissez T1 à T4, S1, S2 ou ANNEE.");
    }

    /** Premier instant de la période (inclus), à Ouagadougou. */
    public Instant debut(int annee, ZoneId zone) {
        return YearMonth.of(annee, premierMois).atDay(1).atStartOfDay(zone).toInstant();
    }

    /** Premier instant qui suit la période (exclu). */
    public Instant fin(int annee, ZoneId zone) {
        YearMonth suivant = YearMonth.of(annee, dernierMois).plusMonths(1);
        return suivant.atDay(1).atStartOfDay(zone).toInstant();
    }

    public String libelle(int annee) {
        return this == ANNEE ? "Année " + annee : name() + " " + annee;
    }
}
