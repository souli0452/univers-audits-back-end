package gov.bf.ascelc.univers_audits.shared.utils;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;

/**
 * Retire d'une image les informations cachées qui peuvent identifier son auteur : modèle d'appareil, date,
 * position GPS, nom du logiciel, vignette, commentaires, données collées après la fin de l'image.
 *
 * <p>Le travail se fait sur les blocs du fichier, sans décoder ni recompresser l'image : les pixels restent
 * strictement identiques. Pour le JPEG, seule l'orientation est conservée (dans un bloc EXIF minimal) : sans elle,
 * une photo prise en portrait s'afficherait couchée.
 *
 * <p>Réservé au dépôt public (citoyen). Les pièces versées par un agent gardent leurs métadonnées, qui peuvent
 * faire partie de la preuve (date et lieu d'un constat).
 *
 * <p>Seuls JPEG et PNG sont traités. Les autres types sont rendus tels quels : l'appelant décide s'il les accepte.
 * Une image illisible est refusée ({@link IllegalArgumentException}) plutôt que stockée sans contrôle.
 */
public final class AssainisseurPieceJointe {

    private static final byte[] SIGNATURE_PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] ENTETE_EXIF = {'E', 'x', 'i', 'f', 0, 0};
    private static final byte[] ENTETE_ICC = "ICC_PROFILE\0".getBytes(StandardCharsets.ISO_8859_1);

    /** Blocs PNG nécessaires à l'affichage ; tout le reste (texte, date, EXIF, inconnus) est retiré. */
    private static final Set<String> BLOCS_PNG_GARDES = Set.of(
            "IHDR", "PLTE", "IDAT", "IEND", "tRNS", "gAMA", "cHRM", "sRGB", "iCCP", "sBIT", "bKGD", "pHYs",
            "acTL", "fcTL", "fdAT");

    private AssainisseurPieceJointe() {
    }

    public static boolean estImage(String mime) {
        return mime != null && mime.toLowerCase().startsWith("image/");
    }

    public static boolean estImageAssainissable(String mime) {
        return estJpeg(mime) || estPng(mime);
    }

    private static boolean estJpeg(String mime) {
        return mime != null && (mime.equalsIgnoreCase("image/jpeg") || mime.equalsIgnoreCase("image/jpg"));
    }

    private static boolean estPng(String mime) {
        return mime != null && mime.equalsIgnoreCase("image/png");
    }

    /** Rend l'image sans ses métadonnées ; les types autres que JPEG et PNG sont rendus tels quels. */
    public static byte[] retirerMetadonnees(String mime, byte[] octets) {
        if (estJpeg(mime)) { return assainirJpeg(octets); }
        if (estPng(mime)) { return assainirPng(octets); }
        return octets;
    }

    // ── JPEG ─────────────────────────────────────────────────────────────

    private static int u(byte b) {
        return b & 0xFF;
    }

    private static IllegalArgumentException illisible(String format) {
        return new IllegalArgumentException("Image " + format + " illisible");
    }

    private static byte[] assainirJpeg(byte[] in) {
        if (in.length < 4 || u(in[0]) != 0xFF || u(in[1]) != 0xD8) { throw illisible("JPEG"); }

        ByteArrayOutputStream app0 = new ByteArrayOutputStream();
        ByteArrayOutputStream reste = new ByteArrayOutputStream(in.length);
        int orientation = 1;
        boolean balayageVu = false;
        int pos = 2;

        while (!balayageVu) {
            if (pos + 2 > in.length || u(in[pos]) != 0xFF) { throw illisible("JPEG"); }
            int marqueur = u(in[pos + 1]);
            if (marqueur == 0xFF) { pos++; continue; }                         // octets de remplissage
            if (marqueur == 0xD9) { throw illisible("JPEG"); }                 // fin d'image sans aucun balayage
            if (marqueur == 0x01 || (marqueur >= 0xD0 && marqueur <= 0xD8)) {  // marqueurs sans contenu
                reste.write(in, pos, 2);
                pos += 2;
                continue;
            }
            if (pos + 4 > in.length) { throw illisible("JPEG"); }
            int longueur = (u(in[pos + 2]) << 8) | u(in[pos + 3]);
            if (longueur < 2 || pos + 2 + longueur > in.length) { throw illisible("JPEG"); }
            byte[] segment = Arrays.copyOfRange(in, pos, pos + 2 + longueur);

            if (marqueur == 0xDA) {                                            // début du balayage : l'image elle-même
                reste.write(segment, 0, segment.length);
                int fin = finDeImage(in, pos + 2 + longueur);
                if (fin < 0) { throw illisible("JPEG"); }
                reste.write(in, pos + 2 + longueur, fin - (pos + 2 + longueur));
                balayageVu = true;
            } else if (marqueur == 0xE0) {
                app0.write(segment, 0, segment.length);                         // JFIF : taille et proportions
            } else if (marqueur == 0xE1) {
                if (orientation == 1) { orientation = lireOrientation(segment); }
            } else if (marqueur == 0xE2 && commencePar(segment, 4, ENTETE_ICC)) {
                reste.write(segment, 0, segment.length);                         // profil de couleur : sans identité
            } else if ((marqueur >= 0xE1 && marqueur <= 0xEF) || marqueur == 0xFE) {
                // EXIF, XMP, IPTC, Photoshop, commentaires, constructeurs : retirés
            } else {
                reste.write(segment, 0, segment.length);                         // tables et structure de l'image
            }
            pos += 2 + longueur;
        }

        ByteArrayOutputStream sortie = new ByteArrayOutputStream(reste.size() + 64);
        sortie.write(0xFF);
        sortie.write(0xD8);
        sortie.writeBytes(app0.toByteArray());
        if (orientation != 1) { sortie.writeBytes(segmentOrientation(orientation)); }
        sortie.writeBytes(reste.toByteArray());
        return sortie.toByteArray();
    }

    /** Position juste après le premier vrai marqueur de fin d'image (FF D9), ou -1 s'il manque. */
    private static int finDeImage(byte[] in, int debut) {
        int i = debut;
        while (i + 1 < in.length) {
            if (u(in[i]) != 0xFF) { i++; continue; }
            int suivant = u(in[i + 1]);
            if (suivant == 0x00 || (suivant >= 0xD0 && suivant <= 0xD7)) { i += 2; continue; }
            if (suivant == 0xFF) { i++; continue; }
            if (suivant == 0xD9) { return i + 2; }
            if (i + 3 >= in.length) { return -1; }                              // autre segment (tables, balayage suivant)
            i += 2 + ((u(in[i + 2]) << 8) | u(in[i + 3]));
        }
        return -1;
    }

    private static boolean commencePar(byte[] octets, int decalage, byte[] debut) {
        if (octets.length < decalage + debut.length) { return false; }
        for (int i = 0; i < debut.length; i++) {
            if (octets[decalage + i] != debut[i]) { return false; }
        }
        return true;
    }

    /** Orientation (1 à 8) lue dans un segment APP1 EXIF complet (marqueur et longueur compris) ; 1 si absente. */
    private static int lireOrientation(byte[] segment) {
        if (!commencePar(segment, 4, ENTETE_EXIF)) { return 1; }
        int tiff = 4 + ENTETE_EXIF.length;
        int taille = segment.length - tiff;
        if (taille < 8) { return 1; }
        boolean petitBoutiste = segment[tiff] == 'I' && segment[tiff + 1] == 'I';
        boolean grandBoutiste = segment[tiff] == 'M' && segment[tiff + 1] == 'M';
        if (!petitBoutiste && !grandBoutiste) { return 1; }
        int decalageIfd = entier(segment, tiff + 4, 4, petitBoutiste);
        if (decalageIfd < 8 || decalageIfd + 2 > taille) { return 1; }
        int entrees = entier(segment, tiff + decalageIfd, 2, petitBoutiste);
        for (int e = 0; e < entrees; e++) {
            int p = tiff + decalageIfd + 2 + e * 12;
            if (p + 12 > segment.length) { return 1; }
            if (entier(segment, p, 2, petitBoutiste) == 0x0112) {
                int valeur = entier(segment, p + 8, 2, petitBoutiste);
                return valeur >= 1 && valeur <= 8 ? valeur : 1;
            }
        }
        return 1;
    }

    private static int entier(byte[] octets, int pos, int nombre, boolean petitBoutiste) {
        int valeur = 0;
        for (int i = 0; i < nombre; i++) {
            int octet = u(octets[pos + (petitBoutiste ? nombre - 1 - i : i)]);
            valeur = (valeur << 8) | octet;
        }
        return valeur;
    }

    /** Orientation d'un JPEG (1 si aucune) ; sert aux contrôles et à la lecture de l'image rendue. */
    public static int orientationJpeg(byte[] jpeg) {
        int pos = 2;
        while (pos + 4 <= jpeg.length && u(jpeg[pos]) == 0xFF) {
            int marqueur = u(jpeg[pos + 1]);
            if (marqueur == 0xDA || marqueur == 0xD9) { break; }
            int longueur = (u(jpeg[pos + 2]) << 8) | u(jpeg[pos + 3]);
            if (longueur < 2 || pos + 2 + longueur > jpeg.length) { break; }
            if (marqueur == 0xE1) {
                int o = lireOrientation(Arrays.copyOfRange(jpeg, pos, pos + 2 + longueur));
                if (o != 1) { return o; }
            }
            pos += 2 + longueur;
        }
        return 1;
    }

    /** Bloc EXIF réduit à l'orientation : « Exif », en-tête TIFF, une seule entrée. */
    private static byte[] segmentOrientation(int orientation) {
        byte[] tiff = {
                'I', 'I', 42, 0, 8, 0, 0, 0,                  // petit-boutiste, premier répertoire à l'octet 8
                1, 0,                                          // une entrée
                0x12, 0x01, 3, 0, 1, 0, 0, 0,                 // étiquette 0x0112 (orientation), SHORT, 1 valeur
                (byte) orientation, 0, 0, 0,
                0, 0, 0, 0                                     // pas de répertoire suivant
        };
        int longueur = 2 + ENTETE_EXIF.length + tiff.length;
        ByteArrayOutputStream s = new ByteArrayOutputStream();
        s.write(0xFF);
        s.write(0xE1);
        s.write(longueur >> 8);
        s.write(longueur & 0xFF);
        s.writeBytes(ENTETE_EXIF);
        s.writeBytes(tiff);
        return s.toByteArray();
    }

    // ── PNG ──────────────────────────────────────────────────────────────

    private static byte[] assainirPng(byte[] in) {
        if (in.length < SIGNATURE_PNG.length + 12 || !commencePar(in, 0, SIGNATURE_PNG)) { throw illisible("PNG"); }
        ByteArrayOutputStream sortie = new ByteArrayOutputStream(in.length);
        sortie.write(in, 0, SIGNATURE_PNG.length);

        int pos = SIGNATURE_PNG.length;
        boolean premier = true;
        while (pos + 12 <= in.length) {
            long longueur = ((long) u(in[pos]) << 24) | (u(in[pos + 1]) << 16) | (u(in[pos + 2]) << 8) | u(in[pos + 3]);
            if (longueur > in.length - pos - 12L) { throw illisible("PNG"); }
            String type = new String(in, pos + 4, 4, StandardCharsets.ISO_8859_1);
            int total = 12 + (int) longueur;
            if (premier && !type.equals("IHDR")) { throw illisible("PNG"); }
            premier = false;
            if (BLOCS_PNG_GARDES.contains(type)) { sortie.write(in, pos, total); }
            pos += total;
            if (type.equals("IEND")) { return sortie.toByteArray(); }          // tout ce qui suit est ignoré
        }
        throw illisible("PNG");
    }
}
