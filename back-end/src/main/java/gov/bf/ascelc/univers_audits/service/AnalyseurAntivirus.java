package gov.bf.ascelc.univers_audits.service;

/** Analyse antivirus d'une pièce jointe avant son enregistrement. */
public interface AnalyseurAntivirus {

    enum Verdict {
        SAIN,
        INFECTE,
        /** Antivirus coupé, injoignable ou en erreur : la pièce n'a pas été contrôlée. */
        NON_ANALYSE
    }

    Verdict analyser(byte[] octets);
}
