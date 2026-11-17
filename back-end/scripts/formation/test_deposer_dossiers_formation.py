"""Tests du script de dépôt de dossiers de formation. À lancer dans ce dossier :  python -m unittest -v"""
import json
import os
import random
import re
import tempfile
import threading
import unittest
from http.server import BaseHTTPRequestHandler, HTTPServer

import deposer_dossiers_formation as d

ARBRE = {"code": "BF", "libelle": "Burkina Faso", "regions": [
    {"id": "r-kad", "libelle": "Kadiogo", "zone": "KADIOGO", "provinces": [
        {"id": "p-kad", "libelle": "Kadiogo", "communes": [{"id": "c-ouaga", "libelle": "Ouagadougou", "categorie": "STATUT_PARTICULIER"}]}]},
    {"id": "r-gui", "libelle": "Guiriko", "zone": "GUIRIKO", "provinces": [
        {"id": "p-houet", "libelle": "Houet", "communes": [{"id": "c-bobo", "libelle": "Bobo-Dioulasso", "categorie": "STATUT_PARTICULIER"}]}]},
    {"id": "r-nak", "libelle": "Nakambé", "zone": "AUTRES_REGIONS", "provinces": [
        {"id": "p-bou", "libelle": "Boulgou", "communes": [{"id": "c-tenko", "libelle": "Tenkodogo", "categorie": "PLEIN"}]}]},
]}


class ApiSimulee(BaseHTTPRequestHandler):
    """Petit serveur qui imite l'API : géographie, dépôt, et un 429 au début si demandé."""
    refus_restants = 0
    depots = []

    def log_message(self, *a):
        pass

    def _envoyer(self, code, corps, entetes=None):
        octets = json.dumps(corps).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        for k, v in (entetes or {}).items():
            self.send_header(k, v)
        self.send_header("Content-Length", str(len(octets)))
        self.end_headers()
        self.wfile.write(octets)

    def do_GET(self):
        self._envoyer(200, ARBRE) if self.path == "/public/geographie" else self._envoyer(404, {})

    def do_POST(self):
        corps = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
        if ApiSimulee.refus_restants > 0:
            ApiSimulee.refus_restants -= 1
            return self._envoyer(429, {"message": "trop de demandes"}, {"Retry-After": "1"})
        ApiSimulee.depots.append(corps)
        self._envoyer(201, {"accessCode": f"CODE{len(ApiSimulee.depots):04d}"})


class TestsDepots(unittest.TestCase):
    def setUp(self):
        self.communes = d.aplatir_geographie(ARBRE)

    def depots(self, n=200, graine=1):
        rng = random.Random(graine)
        return [d.fabriquer_depot(rng, ARBRE, self.communes, i) for i in range(1, n + 1)]

    def test_un_depot_anonyme_ne_porte_aucune_identite_ni_commune(self):
        anonymes = [x for x in self.depots() if x["anonymous"]]
        self.assertGreater(len(anonymes), 10)
        for x in anonymes:
            self.assertEqual(x["declarantData"]["typeDeclarant"], "ANONYMOUS")
            for champ in ("firstName", "lastName", "email", "phoneNumber"):
                self.assertNotIn(champ, x["declarantData"])
            self.assertNotIn("origineProvinceId", x)
            self.assertNotIn("origineCommuneId", x)
            self.assertEqual(x["quality"], "TEMOIN")

    def test_un_depot_nominatif_utilise_une_fausse_identite_sans_adresse_reelle(self):
        nominatifs = [x for x in self.depots() if not x["anonymous"]]
        self.assertGreater(len(nominatifs), 100)
        for x in nominatifs:
            decl = x["declarantData"]
            self.assertTrue(decl["email"].endswith("@formation.invalid"), decl["email"])
            self.assertLessEqual(len(decl["phoneNumber"]), 20)
            self.assertTrue(decl["phoneNumber"].startswith("+226 00 00"))
            self.assertFalse(decl["notificationsAccepted"])

    def test_chaque_dossier_est_marque_formation_et_respecte_les_limites_du_serveur(self):
        for x in self.depots():
            self.assertTrue(x["object"].startswith("[FORMATION] "))
            self.assertLessEqual(len(x["object"]), 500)
            self.assertLessEqual(len(x["description"]), 10000)
            if "estimatedLoss" in x:
                self.assertGreater(x["estimatedLoss"], 0)

    def test_les_origines_couvrent_les_zones_et_les_identifiants_existent(self):
        ids_regions = {r["id"] for r in ARBRE["regions"]}
        zones, etranger, sans = set(), 0, 0
        for x in self.depots():
            if "paysOrigine" in x:
                etranger += 1
                self.assertNotIn("origineRegionId", x)
            elif "origineRegionId" in x:
                self.assertIn(x["origineRegionId"], ids_regions)
                zones.add(x["origineRegionId"])
            else:
                sans += 1
        self.assertEqual(zones, ids_regions)
        self.assertGreater(etranger, 0)
        self.assertGreater(sans, 0)

    def test_les_memes_parametres_donnent_les_memes_dossiers(self):
        self.assertEqual(self.depots(30, graine=7), self.depots(30, graine=7))
        self.assertNotEqual(self.depots(30, graine=7), self.depots(30, graine=8))


class TestsSql(unittest.TestCase):
    def sql(self, n=300):
        return d.ajustements_sql([f"AB{i:06d}" for i in range(n)], random.Random(5))

    def test_le_fichier_est_une_transaction_qui_ne_touche_que_les_dossiers_de_formation(self):
        sql = self.sql()
        self.assertTrue(sql.rstrip().endswith("COMMIT;"))
        updates = [l for l in sql.splitlines() if l.startswith("UPDATE")]
        self.assertEqual(len(updates), 300)
        for u in updates:
            self.assertIn("WHERE access_code = ", u)
            self.assertIn("AND object LIKE '[FORMATION]%'", u)

    def test_seules_des_valeurs_connues_sont_ecrites_et_la_plateforme_va_avec_les_reseaux_sociaux(self):
        canaux = {"WEB_FORM", "IN_PERSON", "PAPER_FORM", "POSTAL_MAIL", "EMAIL", "PHONE", "SOCIAL_MEDIA"}
        natures = {"PLAINTE_DENONCIATION", "DEMANDE_INTERVENTION"}
        vues = set()
        for u in [l for l in self.sql().splitlines() if l.startswith("UPDATE")]:
            canal = re.search(r"submission_mode = '(\w+)'", u).group(1)
            self.assertIn(canal, canaux)
            vues.add(canal)
            nature = re.search(r"\btype = '(\w+)'", u)
            if nature:
                self.assertIn(nature.group(1), natures)
            plate = re.search(r"social_platform = (NULL|'(\w+)')", u)
            if canal == "SOCIAL_MEDIA":
                self.assertIn(plate.group(2), {"WHATSAPP", "MESSENGER"})
            else:
                self.assertEqual(plate.group(1), "NULL")
        self.assertEqual(vues, canaux)
        self.assertIn("PLAINTE_DENONCIATION", self.sql())
        self.assertIn("DEMANDE_INTERVENTION", self.sql())

    def test_un_code_de_suivi_inattendu_est_refuse_pour_ne_jamais_injecter_de_sql(self):
        with self.assertRaises(ValueError):
            d.ajustements_sql(["ABC'; DROP TABLE dossier; --"], random.Random(1))


class TestsApi(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.serveur = HTTPServer(("127.0.0.1", 0), ApiSimulee)
        threading.Thread(target=cls.serveur.serve_forever, daemon=True).start()
        cls.api = f"http://127.0.0.1:{cls.serveur.server_port}"

    @classmethod
    def tearDownClass(cls):
        cls.serveur.shutdown()

    def setUp(self):
        ApiSimulee.refus_restants = 0
        ApiSimulee.depots = []
        self.dossier = tempfile.mkdtemp()
        self.sortie = os.path.join(self.dossier, "ajustements.sql")

    def test_depose_le_nombre_demande_et_ecrit_le_fichier_sql(self):
        code = d.main(["--api", self.api, "--nombre", "6", "--sortie", self.sortie], attente=lambda s: None)
        self.assertEqual(code, 0)
        self.assertEqual(len(ApiSimulee.depots), 6)
        sql = open(self.sortie, encoding="utf-8").read()
        for i in range(1, 7):
            self.assertIn(f"'CODE{i:04d}'", sql)

    def test_attend_la_duree_indiquee_par_le_serveur_apres_un_refus_429_puis_reussit(self):
        ApiSimulee.refus_restants = 2
        attentes = []
        code = d.main(["--api", self.api, "--nombre", "1", "--sortie", self.sortie], attente=attentes.append)
        self.assertEqual(code, 0)
        self.assertEqual(attentes, [3, 3])          # Retry-After 1 s + 2 s de marge, deux fois
        self.assertEqual(len(ApiSimulee.depots), 1)

    def test_a_blanc_n_envoie_rien_et_n_ecrit_aucun_fichier(self):
        code = d.main(["--api", self.api, "--nombre", "5", "--a-blanc", "--sortie", self.sortie],
                      attente=lambda s: None)
        self.assertEqual(code, 0)
        self.assertEqual(ApiSimulee.depots, [])
        self.assertFalse(os.path.exists(self.sortie))

    def test_un_site_injoignable_est_signale_clairement(self):
        with self.assertRaises(d.ErreurApi):
            d.appeler("http://127.0.0.1:9", "/public/geographie", attente=lambda s: None)


if __name__ == "__main__":
    unittest.main()
