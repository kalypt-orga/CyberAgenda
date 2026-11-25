# CyberAgenda — documentation technique

Architecture, sources, sécurité et commandes de build. Pour la présentation de l'application,
voir le [README](../README.md).

Application Android (Kotlin / Jetpack Compose), 100 % locale : pas de compte, pas de backend,
pas de traqueur.

## Structure

```
app/                 Application, activité, navigation, manifeste, politique réseau
core/model           Modèle (Event, SourceMeta) + validation/assainissement communs
core/network         OkHttp durci, HttpFetcher (taille bornée), SafeUrl
core/database        Room : cache des événements, favoris, état de synchro
core/data            EventSource (contrat), SourceRegistry, préférences, EventRepository
core/ui              Design system « encre & signal » (thème, polices, EventCard…)
core/formats         Analyseurs défensifs : iCalendar, HTML → texte brut
core/geo             Géocodage hors ligne (table des communes embarquée)
source/ctftime       CTF en ligne + CTF sur place en France (API CTFtime)
source/agendadulibre Ateliers / cafés vie privée grand public (API Agenda du Libre)
source/frcyberevents Salons et conférences cyber FR (iCal GitHub, A. Dubois)
source/confstech     Conférences sécurité en ligne / en France (JSON confs.tech)
source/curated       Sélection CyberAgenda : événements vérifiés à la main, signés Ed25519 (en travaux)
source/openagenda    Événements cyber d'OpenAgenda (clé publique oa_pk_ : cyberagenda.openagendaKey ;
                     recherche transverse si ouverte, sinon liste d'agendas choisis)
feature/events       Liste groupée par mois, filtres, détail
feature/calendar     Vue mensuelle
feature/map          Carte MapLibre + OpenFreeMap (sans clé, sans traceur)
feature/settings     Paramètres (thème, rappels, sources) + formulaire « Proposer un événement »
feature/reminders    Rappels des favoris (WorkManager, notifications)
```

## Fonctionnalités

- Agenda groupé par mois, filtres, recherche ; événements récurrents regroupés (« +7 dates ») ;
  CTF en ligne regroupés par semaine (carte repliable) ; horizon de 3 mois, « Voir plus loin » pour la suite.
- Doublons entre sources fusionnés (même jour + titres équivalents) ; la source la plus fiable fait foi.
- Calendrier : points pour les événements d'un jour, barres pour ceux sur plusieurs jours.
- Carte : recherche de ville hors ligne, rayon 10–100 km, libellés en français.
- Favoris, rappels (la veille / 1 h avant), partage.
- Ajout à l'agenda : application au choix (sélecteur Android) ou fichier .ics (Proton, Outlook,
  Thunderbird…) ; export de tous les favoris en .ics depuis les paramètres.
- Itinéraire vers l'adresse complète avec l'application de cartographie au choix (Maps, Waze…).
- Tarif quand il est connu (sélection, mention explicite « gratuit » / « entrée libre »).
- Proposer un événement : formulaire validé (dates, heures et tarif facultatifs) → **issue GitHub
  pré-remplie** dans le navigateur (modèle `.github/ISSUE_TEMPLATE/proposer-evenement.yml`, chaque champ
  pré-rempli par son identifiant), avec l'entrée JSON prête pour la sélection signée. Secours sans compte
  GitHub : e-mail pré-rempli. Réglages dans `gradle.properties` : `cyberagenda.proposeRepo` (propriétaire/dépôt)
  et `cyberagenda.proposeEmail` (adresse **publique** ; vide = pas de lien e-mail). Rien n'est envoyé par l'app.
- Thème clair / sombre / auto.

## Design

Identité « encre & signal » : fond encre (sombre) ou papier (clair), un seul accent ambre pour
ce qui est actionnable, une couleur par type d'événement en petites touches, IBM Plex Sans pour
lire et JetBrains Mono pour les métadonnées. Pas de couleurs dynamiques Material You.
Polices embarquées (OFL, voir `core/ui/src/main/assets/licenses`).

## Ajouter une source

1. Créer `source/<nom>/` (copier `source/ctftime/build.gradle.kts`) et l'inclure dans `settings.gradle.kts`.
2. Implémenter `EventSource` (identifiants préfixés par `meta.id + ":"`).
3. L'enregistrer : `@Binds @IntoSet abstract fun bind(s: MaSource): EventSource`.
4. Déclarer son domaine + pins dans `app/src/main/res/xml/network_security_config.xml`.
5. Ajouter `implementation(project(":source:<nom>"))` dans `app/build.gradle.kts`.

Elle apparaît automatiquement dans le menu « Sources » (activée par défaut).

## Sélection signée (source « Sélection CyberAgenda »)

Pour couvrir les événements sans flux exploitable (associations, conférences régionales) :

1. Éditer `source/curated/src/main/assets/curated/events.json` (dates `AAAA-MM-JJ` ou ISO avec fuseau,
   `kind` : CTF / CONFERENCE / MEETUP / WORKSHOP / OTHER, `format` : ONSITE / ONLINE / HYBRID).
   Incrémenter `generatedAt` à chaque publication.
2. Signer : `tools/sign-curated.sh` (produit `events.json.sig` et vérifie la signature).
3. Optionnel — mise à jour sans republier l'app : héberger `events.json` et `events.json.sig`
   (ex. dépôt GitHub) et renseigner `cyberagenda.curatedUrl=...` dans `gradle.properties`.
   L'app n'accepte la version distante que si la signature est valide et qu'elle n'est pas
   plus ancienne que la version embarquée (anti-rejeu).

La clé privée est dans `signing/private-ed25519.pem` (ignorée par git) : **chiffrez-la et sauvegardez-la
hors du dépôt** (voir l'en-tête de `tools/sign-curated.sh` ; `CYBERAGENDA_SIGNING_KEY` pour un autre emplacement).
Si elle est perdue, générer une nouvelle paire et mettre à jour `CuratedSource.PUBLIC_KEY`.

## Choix de sécurité (OWASP MASVS)

| Domaine | Mesure |
|---|---|
| Réseau | HTTPS seul, TLS restreint, pinning SPKI (avec secours + expiration), CA utilisateur refusées en release, pas de redirection vers http |
| Intégrité | Sélection signée Ed25519 (Tink), anti-rejeu par date de génération, contenu modifié = rejeté |
| Données entrantes | Chaque événement revalidé quelle que soit la source : URL https sans identifiants, longueurs bornées, caractères bidi/contrôle supprimés, dates et coordonnées cohérentes, une source ne peut écrire que dans son espace, réponses limitées à 2 Mo |
| Affichage | Texte brut uniquement, aucune WebView ; domaine (punycode) affiché avant d'ouvrir un lien |
| Stockage | Base privée non chiffrée (données publiques uniquement), sauvegardes cloud et transfert désactivés |
| Vie privée | Géocodage hors ligne (aucun lieu envoyé à un tiers), aucune permission de localisation (celles ajoutées par MapLibre sont retirées du manifeste), polices embarquées |
| Fichiers | `.ics` partagés via un FileProvider non exporté, limité à cache/ics, lecture seule accordée à l'app choisie |
| Plateforme | Permissions : INTERNET, ACCESS_NETWORK_STATE, POST_NOTIFICATIONS (demandée seulement à l'activation des rappels), RECEIVE_BOOT_COMPLETED / WAKE_LOCK (WorkManager). Localisation et FOREGROUND_SERVICE retirées. Pas d'alarme exacte. Notification → PendingIntent explicite et immuable ; l'extra reçu par l'activité est validé, un seul composant exporté, pas de deep link, anti-tapjacking, ajout à l'agenda par Intent (aucune permission calendrier) |
| Build | R8 (minify + shrink), logs supprimés en release, checksum du wrapper Gradle vérifié |

## Commandes

```bash
./gradlew assembleDebug            # APK debug (fr.cyberagenda.debug)
./gradlew testDebugUnitTest        # tests unitaires
./gradlew assembleRelease          # APK release obfusqué (non signé)
```

Release signée : `signing/release.jks` + `signing/keystore.properties` (ignorés par git, mot de
passe aléatoire). **Sauvegardez le dossier `signing/` hors du dépôt, chiffré** : sans cette clé, impossible
de publier une mise à jour installable par-dessus la version existante. Pour sortir les clés du projet :
`CYBERAGENDA_SIGNING_DIR=/chemin/vers/dossier` ; pour retirer les mots de passe du fichier :
`CYBERAGENDA_STORE_PASSWORD` et `CYBERAGENDA_KEY_PASSWORD`. Pour une publication sur le Play Store,
activer **Play App Signing** (Google conserve la clé de signature ; la vôtre devient une clé d'import révocable).

`./gradlew assembleRelease` produit un APK par architecture dans `app/build/outputs/apk/release/`
(`arm64-v8a` pour la quasi-totalité des téléphones récents, ~16 Mo) et un APK universel.

### Installer sur un téléphone Xiaomi / HyperOS
- Par USB : Options pour les développeurs → « Débogage USB » **et** « Installer via USB »
  (compte Xiaomi requis), puis `adb install app/build/outputs/apk/release/app-arm64-v8a-release.apk`.
- Sans USB : copier l'APK sur le téléphone et l'ouvrir depuis le gestionnaire de fichiers
  (autoriser l'installation d'applications inconnues pour cette app uniquement).
- Pour des rappels fiables : Paramètres de l'app → Économiseur de batterie → « Pas de restrictions »,
  et activer « Démarrage automatique ».

## Mettre à jour les pins

```bash
openssl s_client -connect ctftime.org:443 -servername ctftime.org -showcerts </dev/null
# puis pour chaque certificat de la chaîne :
openssl x509 -pubkey -noout | openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | base64
```
Domaines épinglés : ctftime.org, www.agendadulibre.org, alexandredubois.github.io,
raw.githubusercontent.com, *.openfreemap.org. Pins vérifiés le 2026-11-23, expiration fixée au 2027-12-31.
