#!/usr/bin/env python3
"""Dépose des dossiers FICTIFS pour la formation, par la route publique de dépôt (comme le ferait un citoyen).

Chaque dossier porte la mention « [FORMATION] » dans son objet, une fausse identité (adresse en
@formation.invalid : aucun e-mail réel possible) ou aucune (dépôt anonyme), et une origine géographique tirée
du référentiel officiel. Un fichier SQL d'ajustements est écrit à la fin : il donne à une partie des dossiers
les natures (plainte-dénonciation, demande d'intervention), canaux (écrit, mail, téléphone, WhatsApp,
Messenger…) et types d'institution que seul un agent peut choisir, pour que les chiffres publiés aient de quoi
s'afficher en formation. Rien d'autre n'est modifié : les dossiers restent « Soumis », à traiter par les stagiaires.

Usage (depuis n'importe quel poste qui joint le site) :
    python3 deposer_dossiers_formation.py --nombre 20
    python3 deposer_dossiers_formation.py --api http://localhost:8081/api/v1 --nombre 5      (essai local)
    python3 deposer_dossiers_formation.py --a-blanc --nombre 10                               (n'envoie rien)

Puis, sur le serveur, appliquer le fichier SQL produit :
    docker exec -i asce_postgres psql -U <utilisateur> -d <base> < ajustements-formation.sql

Le serveur limite les dépôts (5 par tranche de 10 minutes et par adresse) : le script attend quand il le faut,
vingt dossiers prennent donc une demi-heure environ. Il peut être interrompu (Ctrl+C) : le fichier SQL couvre
les dossiers déjà déposés.
"""
import argparse
import json
import random
import re
import sys
import time
import urllib.error
import urllib.request

API_PAR_DEFAUT = "https://denoncer.asce-lc.bf/api/v1"
DOMAINE_MAIL = "formation.invalid"
MARQUE = "[FORMATION]"

PRENOMS = ["Awa", "Fatimata", "Mariam", "Salamata", "Aminata", "Pascaline", "Odette", "Roukiatou", "Habibou",
           "Salif", "Ousmane", "Ibrahim", "Adama", "Issa", "Rasmané", "Wendyam", "Boukary", "Hamidou", "Yacouba",
           "Alassane", "Moussa", "Dieudonné", "Inoussa", "Seydou"]
NOMS = ["Ouédraogo", "Sawadogo", "Kaboré", "Traoré", "Compaoré", "Zongo", "Ilboudo", "Nikiéma", "Tapsoba", "Sana",
        "Barry", "Diallo", "Ouattara", "Konaté", "Coulibaly", "Zerbo", "Bationo", "Savadogo", "Belem", "Yaméogo"]
PAYS_ETRANGERS = ["Côte d'Ivoire", "France", "Ghana", "Mali", "Niger", "Togo", "Canada"]

# (objet, description, lieu, montant mini, montant maxi) — situations génériques, sans institution réelle citée
SCENARIOS = [
    ("Marché public attribué sans mise en concurrence",
     "Un marché de fournitures aurait été attribué à une entreprise proche d'un responsable, sans appel d'offres.",
     "Direction des marchés d'un ministère", 15_000_000, 400_000_000),
    ("Recrutement irrégulier dans un établissement public",
     "Des candidats auraient été retenus sans concours, au profit de proches de la direction.",
     "Service du personnel d'un établissement public", 0, 0),
    ("Détournement de carburant d'un projet",
     "Des bons de carburant destinés aux véhicules d'un projet seraient revendus par un chauffeur et son chef de parc.",
     "Parc automobile d'un projet de développement", 2_000_000, 25_000_000),
    ("Perception illégale de frais dans une mairie",
     "Des frais non prévus par les textes seraient exigés pour la délivrance d'actes d'état civil.",
     "Service d'état civil d'une mairie", 100_000, 3_000_000),
    ("Surfacturation de travaux de voirie",
     "Les travaux facturés seraient supérieurs aux travaux réellement exécutés sur un tronçon de route.",
     "Chantier de voirie communale", 50_000_000, 900_000_000),
    ("Enrichissement inexpliqué d'un responsable",
     "Un responsable aurait acquis plusieurs biens immobiliers sans rapport avec son salaire.",
     "Direction d'une société d'État", 80_000_000, 1_500_000_000),
    ("Abus de fonction pour un règlement de dette",
     "Un agent public aurait utilisé son autorité pour obtenir le paiement d'une dette privée.",
     "Services déconcentrés d'une région", 500_000, 6_000_000),
    ("Faux et usage de faux dans un dossier de subvention",
     "Des pièces justificatives falsifiées auraient été produites pour obtenir une subvention.",
     "Fonds de financement", 5_000_000, 120_000_000),
    ("Conflit foncier lié à un lotissement",
     "Des parcelles auraient été attribuées deux fois, avec la complicité d'un agent du service foncier.",
     "Service du cadastre d'une commune", 3_000_000, 60_000_000),
    ("Dysfonctionnement grave d'un service de santé",
     "Des médicaments destinés aux patients seraient détournés avant leur distribution.",
     "Centre de santé d'une province", 1_000_000, 15_000_000),
    ("Favoritisme dans l'attribution de bourses",
     "Des bourses auraient été attribuées à des élèves proches de membres de la commission.",
     "Direction régionale de l'éducation", 0, 0),
    ("Fraude sur des cotisations et prestations",
     "Des prestations seraient versées à des personnes fictives.",
     "Caisse locale d'un organisme public", 4_000_000, 70_000_000),
]
PERIODES = ["Janvier 2026", "Février 2026", "Mars 2026", "Avril 2026", "Mai 2026", "Juin 2026", "Juillet 2026",
            "Août 2026", "Septembre 2026", "Octobre 2026", "Depuis plusieurs mois"]
QUALITES = ["TEMOIN", "TEMOIN", "VICTIME", "REPRESENTANT_VICTIME"]

# Profils appliqués après le dépôt (poids, nature | None = inchangée, canal, plateforme | None)
PROFILS = [
    (30, None,                   "WEB_FORM",     None),
    (12, None,                   "IN_PERSON",    None),
    (12, None,                   "PAPER_FORM",   None),
    (8,  None,                   "POSTAL_MAIL",  None),
    (7,  None,                   "EMAIL",        None),
    (4,  None,                   "PHONE",        None),
    (10, None,                   "SOCIAL_MEDIA", "WHATSAPP"),
    (4,  None,                   "SOCIAL_MEDIA", "MESSENGER"),
    (6,  "PLAINTE_DENONCIATION", "IN_PERSON",    None),
    (7,  "DEMANDE_INTERVENTION", "PAPER_FORM",   None),
]
TYPOLOGIES = ["MINISTERES_ADMINISTRATIONS_CENTRALES", "MINISTERES_ADMINISTRATIONS_CENTRALES", "ETABLISSEMENTS_PUBLICS",
              "SOCIETES_D_ETAT", "SOCIETES_D_ETAT", "PROJETS_PROGRAMMES", "FONDS_FINANCEMENT",
              "COLLECTIVITES_TERRITORIALES", "COLLECTIVITES_TERRITORIALES", "AUTRES"]


class ErreurApi(Exception):
    pass


def appeler(api, chemin, corps=None, attente=time.sleep):
    """GET (corps absent) ou POST JSON ; attend et recommence quand le serveur limite le débit (429)."""
    for tentative in range(1, 30):
        requete = urllib.request.Request(
            api + chemin, method="POST" if corps is not None else "GET",
            data=json.dumps(corps).encode("utf-8") if corps is not None else None,
            headers={"Content-Type": "application/json", "Accept": "application/json",
                     "User-Agent": "formation-asce-lc/1.0"})
        try:
            with urllib.request.urlopen(requete, timeout=60) as reponse:
                return json.loads(reponse.read().decode("utf-8"))
        except urllib.error.HTTPError as e:
            texte = e.read().decode("utf-8", "replace")[:300]
            if e.code == 429:
                delai = min(max(int(e.headers.get("Retry-After", "60")), 1), 900) + 2
                print(f"    limite de débit atteinte : attente de {delai} s…", flush=True)
                attente(delai)
                continue
            raise ErreurApi(f"HTTP {e.code} sur {chemin} : {texte}")
        except urllib.error.URLError as e:
            raise ErreurApi(f"site injoignable ({e.reason})")
    raise ErreurApi("trop d'attentes successives (limite de débit)")


def aplatir_geographie(arbre):
    """Liste de (région, province, commune) du référentiel officiel."""
    return [(r, p, c) for r in arbre["regions"] for p in r["provinces"] for c in p["communes"]]


def choisir_origine(rng, arbre, communes, anonyme):
    """Origine : Kadiogo, Guiriko, autres régions, diaspora ou non précisée, dans des proportions réalistes."""
    tirage = rng.random()
    if tirage < 0.12:
        return {}
    if tirage < 0.15:
        return {"paysOrigine": rng.choice(PAYS_ETRANGERS)}
    if tirage < 0.55:
        zone = "KADIOGO"
    elif tirage < 0.67:
        zone = "GUIRIKO"
    else:
        zone = "AUTRES_REGIONS"
    candidates = [t for t in communes if t[0]["zone"] == zone]
    region, province, commune = rng.choice(candidates)
    origine = {"origineRegionId": region["id"]}
    if not anonyme:     # le serveur ne garde que la région pour un déclarant anonyme : on s'y conforme
        origine["origineProvinceId"] = province["id"]
        origine["origineCommuneId"] = commune["id"]
    return origine


def fabriquer_depot(rng, arbre, communes, numero):
    """Une demande de dépôt public complète (identité fictive ou anonyme)."""
    objet, description, lieu, mini, maxi = rng.choice(SCENARIOS)
    anonyme = rng.random() < 0.20
    origine = choisir_origine(rng, arbre, communes, anonyme)
    prenom, nom = rng.choice(PRENOMS), rng.choice(NOMS)
    declarant = {"typeDeclarant": "ANONYMOUS" if anonyme else "CITIZEN", "anonymous": anonyme,
                 "dataProcessingConsent": True, "notificationsAccepted": False,
                 "protectionRequested": False, "protectionAcknowledged": False}
    if not anonyme:
        identifiant = f"formation{numero:03d}"
        declarant.update({"firstName": prenom, "lastName": nom, "email": f"{identifiant}@{DOMAINE_MAIL}",
                          "phoneNumber": f"+226 00 00 {numero // 100:02d} {numero % 100:02d}"})
    depot = {"type": "DENUNCIATION", "quality": "TEMOIN" if anonyme else rng.choice(QUALITES),
             "submissionMode": "WEB_FORM", "anonymous": anonyme,
             "object": f"{MARQUE} {objet}",
             "description": f"{description} Dossier fictif créé pour la formation des agents.",
             "incidentLocation": lieu, "incidentPeriod": rng.choice(PERIODES), "declarantData": declarant}
    if maxi > 0:
        depot["estimatedLoss"] = rng.randrange(mini, maxi, 100_000)
    depot.update(origine)
    return depot


def choisir_profil(rng):
    total = sum(p[0] for p in PROFILS)
    point = rng.uniform(0, total)
    cumul = 0
    for poids, nature, canal, plateforme in PROFILS:
        cumul += poids
        if point <= cumul:
            return nature, canal, plateforme
    return PROFILS[0][1:]


def ajustements_sql(codes, rng):
    """Fichier SQL qui donne aux dossiers déposés les natures, canaux et types d'institution choisis par un agent."""
    lignes = ["-- Ajustements des dossiers de FORMATION (générés par deposer_dossiers_formation.py).",
              "-- Ne modifie que les dossiers listés, par leur code de suivi. À appliquer une seule fois.",
              "BEGIN;"]
    for code in codes:
        if not re.fullmatch(r"[A-Z0-9]{4,12}", str(code)):
            raise ValueError(f"code de suivi inattendu : {code!r}")
        nature, canal, plateforme = choisir_profil(rng)
        champs = [f"submission_mode = '{canal}'",
                  f"social_platform = {repr_sql(plateforme)}",
                  f"typologie_institutionnelle = {repr_sql(rng.choice(TYPOLOGIES) if rng.random() < 0.85 else None)}"]
        if nature:
            champs.insert(0, f"type = '{nature}'")
        lignes.append(f"UPDATE dossier SET {', '.join(champs)} WHERE access_code = '{code}' "
                      f"AND object LIKE '{MARQUE}%';")
    lignes.append("COMMIT;")
    return "\n".join(lignes) + "\n"


def repr_sql(valeur):
    return "NULL" if valeur is None else "'" + valeur.replace("'", "''") + "'"


def main(argv=None, attente=time.sleep):
    parser = argparse.ArgumentParser(description="Dépose des dossiers fictifs pour la formation.")
    parser.add_argument("--api", default=API_PAR_DEFAUT, help=f"adresse de l'API (défaut : {API_PAR_DEFAUT})")
    parser.add_argument("--nombre", type=int, default=20, help="nombre de dossiers à déposer (défaut : 20)")
    parser.add_argument("--graine", type=int, default=2026, help="graine du hasard (mêmes dossiers à chaque fois)")
    parser.add_argument("--sortie", default="ajustements-formation.sql", help="fichier SQL produit")
    parser.add_argument("--a-blanc", action="store_true", help="n'envoie rien, affiche seulement ce qui serait déposé")
    args = parser.parse_args(argv)
    if not 1 <= args.nombre <= 200:
        parser.error("--nombre doit être compris entre 1 et 200")

    rng = random.Random(args.graine)
    print(f"Lecture du référentiel géographique sur {args.api} …")
    arbre = appeler(args.api, "/public/geographie", attente=attente)
    communes = aplatir_geographie(arbre)
    print(f"  {len(arbre['regions'])} régions, {len(communes)} communes.")

    codes, echecs = [], 0
    try:
        for numero in range(1, args.nombre + 1):
            depot = fabriquer_depot(rng, arbre, communes, numero)
            if args.a_blanc:
                print(f"  [{numero:>3}] (à blanc) {depot['object'][:60]} — "
                      f"{'anonyme' if depot['anonymous'] else depot['declarantData']['lastName']}")
                continue
            try:
                reponse = appeler(args.api, "/dossiers/public/submit", depot, attente=attente)
                codes.append(reponse["accessCode"])
                print(f"  [{numero:>3}/{args.nombre}] déposé : {reponse['accessCode']}  "
                      f"{'(anonyme)' if depot['anonymous'] else ''}", flush=True)
            except ErreurApi as e:
                echecs += 1
                print(f"  [{numero:>3}/{args.nombre}] ÉCHEC : {e}", file=sys.stderr, flush=True)
                if echecs >= 3:
                    print("Trois échecs : arrêt.", file=sys.stderr)
                    break
    except KeyboardInterrupt:
        print("\nInterrompu : le fichier SQL couvre les dossiers déjà déposés.")

    if codes:
        with open(args.sortie, "w", encoding="utf-8", newline="\n") as f:
            f.write(ajustements_sql(codes, random.Random(args.graine + 1)))
        print(f"\n{len(codes)} dossier(s) déposé(s). Fichier SQL : {args.sortie}")
        print("Appliquez-le sur le serveur : docker exec -i asce_postgres psql -U <utilisateur> -d <base> "
              f"< {args.sortie}")
    return 0 if echecs == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
