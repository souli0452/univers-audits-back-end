package gov.bf.ascelc.univers_audits.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

/** Le contenu d'une pièce jointe doit être cohérent avec le type annoncé par l'expéditeur (guide ANSSI, R41). */
class ControleContenuFichierTest {

    private static byte[] hexa(String h) {
        return HexFormat.of().parseHex(h);
    }

    private static byte[] texte(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @ParameterizedTest
    @CsvSource({
            "image/jpeg,      ffd8ffe000104a46",
            "image/png,       89504e470d0a1a0a",
            "image/gif,       4749463839610100",
            "image/webp,      524946461a00000057454250",
            "application/pdf, 255044462d312e37",
            "application/msword, d0cf11e0a1b11ae1",
            "application/msword, 7b5c72746631",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document, 504b030414000600",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,       504b030414000600",
            "audio/mpeg,      4944330400000000",
            "audio/webm,      1a45dfa39f428681",
            "audio/mp4,       0000001c667479704d344120",
            "video/mp4,       000000186674797069736f6d"
    })
    void unFichierConformeEstAccepte(String type, String debut) {
        assertThat(ControleContenuFichier.conforme(type, hexa(debut), false)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"application/pdf", "image/png", "audio/mpeg", "video/mp4", "text/plain", "application/msword"})
    void unProgrammeRenommeEstRefuseQuelQueSoitLeTypeAnnonce(String type) {
        byte[] programmeWindows = hexa("4d5a90000300000004000000ffff0000b800000000000000");
        byte[] programmeLinux = hexa("7f454c4602010100");

        assertThat(ControleContenuFichier.conforme(type, programmeWindows, false)).isFalse();
        assertThat(ControleContenuFichier.conforme(type, programmeLinux, false)).isFalse();
        assertThat(ControleContenuFichier.conforme(type, texte("#!/bin/sh\nrm -rf /tmp/x\n"), false)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "<!DOCTYPE html><html><body></body></html>",
            "  \n<HTML><head></head></HTML>",
            "<script>alert(1)</script>",
            "<svg xmlns=\"http://www.w3.org/2000/svg\" onload=\"alert(1)\"/>",
            "<?xml version=\"1.0\"?>\n<svg onload=\"alert(1)\"/>",
            "<?php system($_GET['c']); ?>"
    })
    void unePageOuUnScriptEstRefuseMemeAnnonceCommeTexte(String contenu) {
        assertThat(ControleContenuFichier.conforme("text/plain", texte(contenu), false)).isFalse();
        assertThat(ControleContenuFichier.conforme("audio/webm", texte("﻿" + contenu), false)).isFalse();
    }

    @Test
    void uneImageOuUnPdfDoitAvoirLaBonneSignature() {
        assertThat(ControleContenuFichier.conforme("application/pdf", texte("bonjour"), false)).isFalse();
        assertThat(ControleContenuFichier.conforme("image/png", hexa("ffd8ffe000104a46"), false)).isFalse();
        assertThat(ControleContenuFichier.conforme("image/jpeg", new byte[0], false)).isFalse();
    }

    @Test
    void unTexteOrdinaireEstAccepteMemeSIlCommenceParDesInitiales() {
        assertThat(ControleContenuFichier.conforme("text/plain", texte("MZ a signalé les faits le 3 mars."), false)).isTrue();
        assertThat(ControleContenuFichier.conforme("text/csv", texte("nom;montant\nx;10\n"), false)).isTrue();
    }
}
