#!/usr/bin/env bash
# Signe la sélection d'événements (Ed25519) après l'avoir validée.
# Usage : tools/sign-curated.sh [fichier.json]
# La clé privée n'est JAMAIS versionnée (signing/ et *.pem sont ignorés par git).
# Recommandé : la chiffrer (openssl demande alors la phrase de passe à chaque signature) et la
# garder hors du projet, via CYBERAGENDA_SIGNING_KEY=/chemin/vers/cle.pem :
#   openssl pkcs8 -topk8 -v2 aes-256-cbc -in private-ed25519.pem -out private-ed25519.enc.pem
set -euo pipefail
cd "$(dirname "$0")/.."
KEY="${CYBERAGENDA_SIGNING_KEY:-signing/private-ed25519.pem}"
FILE="${1:-source/curated/src/main/assets/curated/events.json}"

[ -f "$KEY" ] || { echo "Clé privée introuvable : $KEY" >&2; exit 1; }
python3 -I -m json.tool "$FILE" > /dev/null || { echo "JSON invalide : $FILE" >&2; exit 1; }

openssl pkeyutl -sign -inkey "$KEY" -rawin -in "$FILE" | base64 -w0 > "$FILE.sig"

# Vérification immédiate avec la clé publique dérivée
TMP=$(mktemp -d); trap 'rm -rf "$TMP"' EXIT
openssl pkey -in "$KEY" -pubout -out "$TMP/pub.pem"
base64 -d "$FILE.sig" > "$TMP/sig.bin"
openssl pkeyutl -verify -pubin -inkey "$TMP/pub.pem" -rawin -in "$FILE" -sigfile "$TMP/sig.bin"
echo "Clé publique (à reporter dans CuratedSource.PUBLIC_KEY si elle change) :"
openssl pkey -in "$KEY" -pubout -outform DER | tail -c 32 | base64 -w0; echo
