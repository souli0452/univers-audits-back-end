# Antivirus des pièces jointes — à lancer sur `rpd`, dans `/opt/asce/deploy`

Ce qui change : un conteneur ClamAV analyse chaque pièce jointe à l'envoi. Fichier infecté : refusé. Antivirus arrêté :
pièce acceptée et marquée « Non analysé par l'antivirus ». Une migration de base (colonne ajoutée). Mémoire : environ
1 Go, davantage pendant un rechargement des signatures.

## 1. Sauvegarde et images de secours

```
cd /opt/asce/deploy
bash scripts/backup.sh
D=$(date +%F-%H%M); echo $D
docker tag asce-lc-backend:latest asce-lc-backend:avant-$D
docker tag asce-lc-proxy:latest asce-lc-proxy:avant-$D
free -h
```

## 2. Démarrer l'antivirus seul

```
git -C sources/back fetch -q origin
git -C sources/back reset -q --hard origin/feature/workflow-denociation-asce-fix
test -e docker-compose.override.yml && echo "ARRET : un fichier override existe deja"
cp sources/back/back-end/docs/deploiement/2026-11-26/docker-compose.override.yml .
docker compose config --quiet && echo CONFIGURATION OK
docker compose up -d clamav
```

Le premier démarrage télécharge l'image et les signatures (plusieurs minutes). Attendre puis :

```
docker exec asce_clamav clamdscan --ping 1
docker logs asce_clamav 2>&1 | grep -iE "error|database" | tail -5
```

Attendu : `PONG`. **Sinon ne pas continuer** (les envois resteraient « non analysés »).

## 3. Déployer

```
bash scripts/deploy.sh
```

## 4. Contrôles

```
docker exec asce_backend printenv ANTIVIRUS_HOST
docker stats --no-stream --format '{{.Name}} {{.MemUsage}}' asce_clamav
```

Attendu : `clamav`. Puis, dans le navigateur : joindre un PDF ordinaire à un dossier « TEST » ; aucune mention
« Non analysé » ne doit apparaître sous ce fichier. La mention apparaît sous les pièces anciennes, c'est normal.

Vérifier que le refus fonctionne, avec le fichier témoin inoffensif EICAR, **sans l'écrire sur un poste de travail** :

```
docker exec asce_clamav sh -c 'printf "%s%s" "X5O!P%@AP[4\\PZX54(P^)7CC)7}\$" "EICAR-STANDARD-ANTIVIRUS-TEST-FILE!\$H+H*" | clamdscan -'
```

Attendu : `FOUND`.

## Retour arrière

```
rm docker-compose.override.yml
docker rm -f asce_clamav
docker tag asce-lc-backend:avant-<date> asce-lc-backend:latest
docker tag asce-lc-proxy:avant-<date> asce-lc-proxy:latest
docker compose up -d backend proxy
```

La colonne ajoutée en base peut rester : l'ancienne version l'ignore.
