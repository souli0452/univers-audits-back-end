package gov.bf.ascelc.univers_audits.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Envoie le fichier à ClamAV (commande INSTREAM : blocs précédés de leur taille, terminés par un bloc vide). Rien n'est
 * écrit sur disque pour l'analyse. Une panne de l'antivirus ne bloque jamais l'envoi : un témoignage ne doit pas être
 * perdu ; la pièce est alors marquée « non analysée ».
 */
@Slf4j
@Component
public class ClamAvAnalyseur implements AnalyseurAntivirus {

    private static final int BLOC = 64 * 1024;

    private final String hote;
    private final int port;
    private final int delaiMs;

    public ClamAvAnalyseur(@Value("${antivirus.host:}") String hote,
                           @Value("${antivirus.port:3310}") int port,
                           @Value("${antivirus.timeout-ms:60000}") int delaiMs) {
        this.hote = hote;
        this.port = port;
        this.delaiMs = delaiMs;
    }

    @Override
    public Verdict analyser(byte[] octets) {
        if (hote == null || hote.isBlank()) {
            return Verdict.NON_ANALYSE;
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(hote, port), Math.min(delaiMs, 5_000));
            socket.setSoTimeout(delaiMs);
            DataOutputStream sortie = new DataOutputStream(socket.getOutputStream());
            sortie.write("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII));
            for (int debut = 0; debut < octets.length; debut += BLOC) {
                int taille = Math.min(BLOC, octets.length - debut);
                sortie.writeInt(taille);
                sortie.write(octets, debut, taille);
            }
            sortie.writeInt(0);
            sortie.flush();

            String reponse = new String(socket.getInputStream().readAllBytes(), StandardCharsets.US_ASCII)
                    .replace("\0", "").trim();
            if (reponse.endsWith("FOUND")) {
                log.warn("Antivirus : fichier refusé ({})", reponse);
                return Verdict.INFECTE;
            }
            if (reponse.endsWith("OK")) {
                return Verdict.SAIN;
            }
            log.error("Antivirus : réponse inattendue « {} », pièce non analysée", reponse);
        } catch (IOException e) {
            log.error("Antivirus injoignable ({}), pièce non analysée", e.getMessage());
        }
        return Verdict.NON_ANALYSE;
    }
}
