package gov.bf.ascelc.univers_audits.service;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Le type d'une pièce jointe est annoncé par l'expéditeur : on vérifie que le contenu ne le contredit pas (guide ANSSI,
 * R41). Programmes, scripts et pages web sont refusés quel que soit le type annoncé ; les images et les PDF doivent
 * porter leur signature. L'audio et la vidéo ne sont pas contraints davantage : les téléphones produisent trop de
 * variantes, et un enregistrement refusé serait un témoignage perdu.
 * Ce n'est pas un antivirus : un document bureautique piégé n'est pas détecté.
 */
final class ControleContenuFichier {

    private static final Pattern PAGE_OU_SCRIPT =
            Pattern.compile("^(<!doctype\\s+html|<html|<head|<body|<script|<svg|<\\?php|<\\?xml[\\s\\S]*<svg)");

    private ControleContenuFichier() {
    }

    /**
     * @param agent agent connecté : une page web enregistrée en texte est alors une preuve à conserver telle quelle
     *              (elle est servie comme texte, en téléchargement, et n'est jamais interprétée).
     */
    static boolean conforme(String type, byte[] octets, boolean agent) {
        boolean pageAcceptee = agent && type.startsWith("text/");
        if (estUnProgrammeOuUnScript(octets) || (!pageAcceptee && estUnePage(octets))) {
            return false;
        }
        return switch (type) {
            case "image/jpeg", "image/jpg" -> commence(octets, 0, 0xFF, 0xD8, 0xFF);
            case "image/png" -> commence(octets, 0, 0x89, 'P', 'N', 'G');
            case "image/gif" -> commence(octets, 0, 'G', 'I', 'F', '8');
            case "image/webp" -> commence(octets, 0, 'R', 'I', 'F', 'F') && commence(octets, 8, 'W', 'E', 'B', 'P');
            // La norme PDF tolère quelques octets avant la signature
            case "application/pdf" -> debut(octets, 1024).contains("%PDF");
            default -> true;
        };
    }

    private static boolean estUnProgrammeOuUnScript(byte[] o) {
        // « MZ » (Windows) : seulement s'il s'agit de données binaires, un texte peut commencer par ces initiales
        boolean windows = commence(o, 0, 'M', 'Z') && debut(o, 64).indexOf('\0') >= 0;
        return windows
                || commence(o, 0, 0x7F, 'E', 'L', 'F')
                || commence(o, 0, '#', '!', '/')
                || commence(o, 0, 0xCA, 0xFE, 0xBA, 0xBE)
                || commence(o, 0, 0xCF, 0xFA, 0xED, 0xFE);
    }

    private static boolean estUnePage(byte[] o) {
        String texte = new String(o, 0, Math.min(o.length, 512), StandardCharsets.UTF_8)
                .replace("﻿", "")
                .stripLeading()
                .toLowerCase(Locale.ROOT);
        return PAGE_OU_SCRIPT.matcher(texte).find();
    }

    private static String debut(byte[] o, int longueur) {
        return new String(o, 0, Math.min(o.length, longueur), StandardCharsets.ISO_8859_1);
    }

    private static boolean commence(byte[] o, int position, int... signature) {
        if (o.length < position + signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((o[position + i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }
}
