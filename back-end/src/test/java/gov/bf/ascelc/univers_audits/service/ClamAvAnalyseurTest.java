package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.service.AnalyseurAntivirus.Verdict;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/** Dialogue avec ClamAV (commande INSTREAM), essayé contre un faux serveur. */
class ClamAvAnalyseurTest {

    private ServerSocket serveur;
    private final CompletableFuture<byte[]> recu = new CompletableFuture<>();
    private final CompletableFuture<String> commande = new CompletableFuture<>();

    private int fauxClamd(String reponse) throws IOException {
        serveur = new ServerSocket(0);
        Thread t = new Thread(() -> {
            try (Socket s = serveur.accept(); DataInputStream in = new DataInputStream(s.getInputStream())) {
                StringBuilder c = new StringBuilder();
                for (int b = in.read(); b > 0; b = in.read()) c.append((char) b);
                commande.complete(c.toString());
                ByteArrayOutputStream contenu = new ByteArrayOutputStream();
                for (int n = in.readInt(); n > 0; n = in.readInt()) contenu.write(in.readNBytes(n));
                recu.complete(contenu.toByteArray());
                s.getOutputStream().write((reponse + "\0").getBytes(StandardCharsets.US_ASCII));
            } catch (IOException ignore) {
                // le test échouera sur les valeurs attendues
            }
        });
        t.setDaemon(true);
        t.start();
        return serveur.getLocalPort();
    }

    @AfterEach
    void fermer() throws IOException {
        if (serveur != null) serveur.close();
    }

    @Test
    void unFichierSainEstSignaleSain_etToutSonContenuAEteEnvoye() throws Exception {
        byte[] fichier = new byte[200_000];
        for (int i = 0; i < fichier.length; i++) fichier[i] = (byte) i;
        int port = fauxClamd("stream: OK");

        Verdict v = new ClamAvAnalyseur("localhost", port, 5_000).analyser(fichier);

        assertThat(v).isEqualTo(Verdict.SAIN);
        assertThat(commande.get()).isEqualTo("zINSTREAM");
        assertThat(recu.get()).isEqualTo(fichier);
    }

    @Test
    void unVirusTrouveEstSignaleInfecte() throws Exception {
        int port = fauxClamd("stream: Eicar-Test-Signature FOUND");

        assertThat(new ClamAvAnalyseur("localhost", port, 5_000).analyser(new byte[]{1})).isEqualTo(Verdict.INFECTE);
    }

    @Test
    void uneErreurDeClamAvNeVautPasAnalyse() throws Exception {
        int port = fauxClamd("INSTREAM size limit exceeded. ERROR");

        assertThat(new ClamAvAnalyseur("localhost", port, 5_000).analyser(new byte[]{1})).isEqualTo(Verdict.NON_ANALYSE);
    }

    @Test
    void unAntivirusInjoignableNeBloquePasLEnvoi() throws Exception {
        int portFerme;
        try (ServerSocket s = new ServerSocket(0)) {
            portFerme = s.getLocalPort();
        }

        assertThat(new ClamAvAnalyseur("localhost", portFerme, 1_000).analyser(new byte[]{1}))
                .isEqualTo(Verdict.NON_ANALYSE);
    }

    @Test
    void sansAdresseConfigureeRienNEstAnalyse() {
        assertThat(new ClamAvAnalyseur("", 3310, 1_000).analyser(new byte[]{1})).isEqualTo(Verdict.NON_ANALYSE);
    }
}
