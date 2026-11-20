package gov.bf.ascelc.univers_audits.shared.utils;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Une photo prise au téléphone révèle son auteur : modèle d'appareil, date, position GPS, logiciel, vignette.
 * Ces tests fabriquent de vraies images contenant des marqueurs reconnaissables et vérifient qu'ils disparaissent
 * sans abîmer l'image.
 */
class AssainisseurPieceJointeTest {

    private static final String SECRET = "SECRET-AUTEUR-GPS";

    // ── fabrication d'images ─────────────────────────────────────────────

    private static BufferedImage image() {
        BufferedImage img = new BufferedImage(16, 12, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 12; y++) {
                img.setRGB(x, y, (x * 15) << 16 | (y * 20) << 8 | ((x + y) * 7));
            }
        }
        return img;
    }

    private static byte[] encoder(String format) throws IOException {
        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        ImageIO.write(image(), format, sortie);
        return sortie.toByteArray();
    }

    private static boolean contient(byte[] octets, String texte) {
        return new String(octets, StandardCharsets.ISO_8859_1).contains(texte);
    }

    private static byte[] concat(byte[]... morceaux) {
        ByteArrayOutputStream s = new ByteArrayOutputStream();
        for (byte[] m : morceaux) { s.writeBytes(m); }
        return s.toByteArray();
    }

    /** Segment JPEG : marqueur FF xx, longueur sur 2 octets (elle compte ses propres 2 octets), puis la charge. */
    private static byte[] segmentJpeg(int marqueur, byte[] charge) {
        ByteBuffer b = ByteBuffer.allocate(4 + charge.length);
        b.put((byte) 0xFF).put((byte) marqueur).putShort((short) (charge.length + 2)).put(charge);
        return b.array();
    }

    /** Bloc EXIF (little-endian) : l'orientation, et le texte secret dans la marque « Make » de l'appareil. */
    private static byte[] exif(int orientation, String secret) {
        byte[] texte = (secret + "\0").getBytes(StandardCharsets.ISO_8859_1);
        int tailleIfd = 2 + 2 * 12 + 4;
        ByteBuffer t = ByteBuffer.allocate(8 + tailleIfd + texte.length).order(ByteOrder.LITTLE_ENDIAN);
        t.put((byte) 'I').put((byte) 'I').putShort((short) 42).putInt(8);
        t.putShort((short) 2);
        t.putShort((short) 0x010F).putShort((short) 2).putInt(texte.length).putInt(8 + tailleIfd); // Make → texte
        t.putShort((short) 0x0112).putShort((short) 3).putInt(1).putShort((short) orientation).putShort((short) 0);
        t.putInt(0);
        t.put(texte);
        return concat("Exif\0\0".getBytes(StandardCharsets.ISO_8859_1), t.array());
    }

    /** Insère des segments juste après le SOI (FF D8) d'un JPEG existant. */
    private static byte[] jpegAvec(byte[] jpeg, byte[]... segments) {
        return concat(new byte[]{jpeg[0], jpeg[1]}, concat(segments), java.util.Arrays.copyOfRange(jpeg, 2, jpeg.length));
    }

    private static byte[] jpegAvecExif(int orientation) throws IOException {
        return jpegAvec(encoder("jpg"), segmentJpeg(0xE1, exif(orientation, SECRET)));
    }

    private static byte[] chunkPng(String type, byte[] donnees) {
        byte[] t = type.getBytes(StandardCharsets.ISO_8859_1);
        CRC32 crc = new CRC32();
        crc.update(t); crc.update(donnees);
        ByteBuffer b = ByteBuffer.allocate(12 + donnees.length);
        b.putInt(donnees.length).put(t).put(donnees).putInt((int) crc.getValue());
        return b.array();
    }

    /** Insère des chunks PNG juste après IHDR (8 octets de signature + 25 octets de IHDR). */
    private static byte[] pngAvec(byte[] png, byte[]... chunks) {
        return concat(java.util.Arrays.copyOfRange(png, 0, 33), concat(chunks),
                java.util.Arrays.copyOfRange(png, 33, png.length));
    }

    private static void assertMemeImage(byte[] avant, byte[] apres) throws IOException {
        BufferedImage a = ImageIO.read(new ByteArrayInputStream(avant));
        BufferedImage b = ImageIO.read(new ByteArrayInputStream(apres));
        assertThat(b).as("l'image doit rester lisible").isNotNull();
        assertThat(b.getWidth()).isEqualTo(a.getWidth());
        assertThat(b.getHeight()).isEqualTo(a.getHeight());
        for (int x = 0; x < a.getWidth(); x++) {
            for (int y = 0; y < a.getHeight(); y++) {
                assertThat(b.getRGB(x, y)).as("pixel %d,%d", x, y).isEqualTo(a.getRGB(x, y));
            }
        }
    }

    // ── JPEG ─────────────────────────────────────────────────────────────

    @Test
    void jpeg_retireLesInformationsDeLAppareilEtLaPosition() throws IOException {
        byte[] original = jpegAvecExif(1);
        assertThat(contient(original, SECRET)).as("le fichier de départ contient bien le secret").isTrue();

        byte[] propre = AssainisseurPieceJointe.retirerMetadonnees("image/jpeg", original);

        assertThat(contient(propre, SECRET)).isFalse();
        assertThat(contient(propre, "Exif")).as("aucun bloc EXIF quand l'orientation est normale").isFalse();
        assertMemeImage(original, propre);
    }

    @Test
    void jpeg_garde_uniquement_lOrientation_pourQueLaPhotoNeSoitPasCouchee() throws IOException {
        byte[] original = jpegAvecExif(6);   // photo prise en portrait : pixels en paysage + orientation 6

        byte[] propre = AssainisseurPieceJointe.retirerMetadonnees("image/jpeg", original);

        assertThat(contient(propre, SECRET)).isFalse();
        assertThat(AssainisseurPieceJointe.orientationJpeg(propre)).isEqualTo(6);
        assertMemeImage(original, propre);
    }

    @Test
    void jpeg_retireCommentairesEtMetadonneesPhotoshopIptc() throws IOException {
        byte[] original = jpegAvec(encoder("jpg"),
                segmentJpeg(0xFE, ("commentaire " + SECRET).getBytes(StandardCharsets.ISO_8859_1)),
                segmentJpeg(0xED, ("Photoshop 3.0\0" + SECRET).getBytes(StandardCharsets.ISO_8859_1)),
                segmentJpeg(0xE1, ("http://ns.adobe.com/xap/1.0/\0<x:xmpmeta>" + SECRET).getBytes(StandardCharsets.ISO_8859_1)));

        byte[] propre = AssainisseurPieceJointe.retirerMetadonnees("image/jpeg", original);

        assertThat(contient(propre, SECRET)).isFalse();
        assertMemeImage(original, propre);
    }

    @Test
    void jpeg_retireLesDonneesAjouteesApresLaFinDeLImage() throws IOException {
        // Certaines photos « animées » portent une vidéo ou une seconde image collée après la fin du JPEG.
        byte[] original = concat(jpegAvecExif(1), ("APRES-LA-FIN " + SECRET).getBytes(StandardCharsets.ISO_8859_1));

        byte[] propre = AssainisseurPieceJointe.retirerMetadonnees("image/jpeg", original);

        assertThat(contient(propre, SECRET)).isFalse();
        assertThat(contient(propre, "APRES-LA-FIN")).isFalse();
        assertMemeImage(original, propre);
    }

    @Test
    void jpeg_sansMetadonnees_resteIdentiquementLisible() throws IOException {
        byte[] original = encoder("jpg");

        byte[] propre = AssainisseurPieceJointe.retirerMetadonnees("image/jpeg", original);

        assertMemeImage(original, propre);
    }

    @Test
    void jpeg_accepteAussiLeTypeImageJpg() throws IOException {
        byte[] propre = AssainisseurPieceJointe.retirerMetadonnees("image/jpg", jpegAvecExif(1));

        assertThat(contient(propre, SECRET)).isFalse();
    }

    @Test
    void jpeg_illisible_estRefuse() {
        assertThatThrownBy(() -> AssainisseurPieceJointe.retirerMetadonnees("image/jpeg", "ce n'est pas une image".getBytes()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AssainisseurPieceJointe.retirerMetadonnees("image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── PNG ──────────────────────────────────────────────────────────────

    @Test
    void png_retireTexteDateEtExif() throws IOException {
        byte[] original = pngAvec(encoder("png"),
                chunkPng("tEXt", ("Author\0" + SECRET).getBytes(StandardCharsets.ISO_8859_1)),
                chunkPng("iTXt", ("XML:com.adobe.xmp\0\0\0\0\0" + SECRET).getBytes(StandardCharsets.ISO_8859_1)),
                chunkPng("zTXt", ("Comment\0\0" + SECRET).getBytes(StandardCharsets.ISO_8859_1)),
                chunkPng("eXIf", exif(1, SECRET)),
                chunkPng("tIME", new byte[]{0x07, (byte) 0xE9, 3, 15, 14, 30, 0}));
        assertThat(contient(original, SECRET)).isTrue();

        byte[] propre = AssainisseurPieceJointe.retirerMetadonnees("image/png", original);

        assertThat(contient(propre, SECRET)).isFalse();
        assertThat(contient(propre, "tIME")).isFalse();
        assertThat(contient(propre, "eXIf")).isFalse();
        assertMemeImage(original, propre);
    }

    @Test
    void png_retireLesDonneesAjouteesApresIEND() throws IOException {
        byte[] original = concat(encoder("png"), ("APRES-IEND " + SECRET).getBytes(StandardCharsets.ISO_8859_1));

        byte[] propre = AssainisseurPieceJointe.retirerMetadonnees("image/png", original);

        assertThat(contient(propre, SECRET)).isFalse();
        assertMemeImage(original, propre);
    }

    @Test
    void png_gardeLesBlocsUtilesALAffichage() throws IOException {
        byte[] original = pngAvec(encoder("png"), chunkPng("gAMA", new byte[]{0, 1, (byte) 0x86, (byte) 0xA0}));

        byte[] propre = AssainisseurPieceJointe.retirerMetadonnees("image/png", original);

        assertThat(contient(propre, "gAMA")).isTrue();
        assertThat(contient(propre, "IDAT")).isTrue();
        assertMemeImage(original, propre);
    }

    @Test
    void png_illisible_estRefuse() {
        assertThatThrownBy(() -> AssainisseurPieceJointe.retirerMetadonnees("image/png", "pas un png".getBytes()))
                .isInstanceOf(IllegalArgumentException.class);
        byte[] tronque = java.util.Arrays.copyOf(concat(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}, new byte[]{0, 0, 0, 99, 'I', 'H', 'D', 'R'}), 16);
        assertThatThrownBy(() -> AssainisseurPieceJointe.retirerMetadonnees("image/png", tronque))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── autres types ─────────────────────────────────────────────────────

    @Test
    void autresTypes_sontRenduxTelsQuels() {
        byte[] pdf = "%PDF-1.4 contenu".getBytes(StandardCharsets.ISO_8859_1);

        assertThat(AssainisseurPieceJointe.retirerMetadonnees("application/pdf", pdf)).isSameAs(pdf);
        assertThat(AssainisseurPieceJointe.retirerMetadonnees("audio/webm", pdf)).isSameAs(pdf);
    }

    @Test
    void seulsJpegEtPngSontAssainissables() {
        assertThat(AssainisseurPieceJointe.estImageAssainissable("image/jpeg")).isTrue();
        assertThat(AssainisseurPieceJointe.estImageAssainissable("image/jpg")).isTrue();
        assertThat(AssainisseurPieceJointe.estImageAssainissable("image/png")).isTrue();
        assertThat(AssainisseurPieceJointe.estImageAssainissable("image/gif")).isFalse();
        assertThat(AssainisseurPieceJointe.estImageAssainissable("image/webp")).isFalse();
        assertThat(AssainisseurPieceJointe.estImageAssainissable("application/pdf")).isFalse();
    }

    @Test
    void estImage_reconnaitTousLesTypesImage() {
        assertThat(AssainisseurPieceJointe.estImage("image/webp")).isTrue();
        assertThat(AssainisseurPieceJointe.estImage("application/pdf")).isFalse();
    }
}
