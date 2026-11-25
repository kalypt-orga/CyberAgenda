package fr.cyberagenda.feature.settings

import androidx.compose.foundation.layout.Arrangement
import fr.cyberagenda.core.ui.scrolledFromTop
import fr.cyberagenda.core.ui.ScrollDivider
import androidx.compose.foundation.lazy.rememberLazyListState
import fr.cyberagenda.core.ui.overscrollIfScrollable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.cyberagenda.core.ui.AgendaTheme
import fr.cyberagenda.core.ui.Mono
import fr.cyberagenda.core.ui.SectionHeader

private data class Term(val word: String, val definition: String)

private val glossary = listOf(
    Term("CTF", "« Capture The Flag » : compétition où l'on résout des défis de sécurité pour trouver des « flags » (des chaînes de texte secrètes). Souvent en équipe, souvent en ligne, ouverte à tous niveaux."),
    Term("Flag", "La réponse à un défi, par exemple FLAG{b13n_j0u3}. On la soumet sur la plateforme pour marquer des points."),
    Term("Jeopardy", "Format de CTF le plus courant : un tableau de défis classés par catégorie et par difficulté. Idéal pour débuter, on choisit ce qu'on veut tenter."),
    Term("Attaque-défense", "Format de CTF où chaque équipe protège ses services tout en attaquant ceux des autres. Plutôt pour les équipes expérimentées."),
    Term("Web", "Catégorie de défis sur les sites et applications web : injections, failles d'authentification, mauvaises configurations…"),
    Term("Crypto", "Catégorie de défis de cryptographie : retrouver un message chiffré en exploitant une faiblesse."),
    Term("Forensics", "Investigation numérique : analyser un fichier, une capture réseau ou une image disque pour retrouver des traces."),
    Term("Reverse", "Rétro-ingénierie : comprendre le fonctionnement d'un programme sans son code source."),
    Term("Pwn", "Exploitation de programmes vulnérables (débordements de mémoire, etc.) pour en prendre le contrôle."),
    Term("OSINT", "Renseignement en sources ouvertes : retrouver des informations publiques (photos, réseaux sociaux, cartes…)."),
    Term("Writeup", "Compte rendu qui explique comment un défi a été résolu. Lire des writeups est l'une des meilleures façons d'apprendre."),
    Term("Wargame", "Plateforme d'entraînement permanente (Root-Me, picoCTF…) : des défis à faire à son rythme, sans compétition."),
    Term("Rump session", "Série de présentations très courtes (quelques minutes) en fin de conférence, souvent détendues et accessibles."),
    Term("CFP", "« Call For Papers » : appel à présentations d'une conférence. Tout le monde peut proposer un sujet, y compris les débutants."),
    Term("BSides", "Conférences communautaires locales, peu chères ou gratuites, à l'ambiance accueillante."),
    Term("Café vie privée", "Rencontre grand public pour apprendre à protéger ses données : navigateur, messagerie, mots de passe. Aussi appelé « chiffrofête » ou « cryptoparty »."),
    // Indice de l'easter egg (7 appuis sur la version dans les paramètres).
    Term("Easter egg", "Surprise cachée par les développeurs dans un logiciel ou un jeu. Les habitués d'Android le savent, on aime les easter eggs quelle que soit la version ! (^_~)"),
)

@Composable
fun GlossaryRoute(onBack: () -> Unit) {
    InfoScaffold("lexique", onBack) {
        item {
            Intro("Les mots qu'on croise dans les événements cyber. Personne ne les connaît tous au début : n'hésitez pas à demander sur place.")
        }
        items(glossary, key = { it.word }) { t ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(t.word, style = Mono.label, color = MaterialTheme.colorScheme.primary)
                Text(t.definition, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun AboutRoute(onBack: () -> Unit, versionName: String) {
    InfoScaffold("confidentialité", onBack) {
        item { Intro("CyberAgenda $versionName — aide les curieux à trouver des événements cyber en France.") }

        item { SectionHeader("vos données") }
        bullets(
            "Aucun compte, aucune publicité, aucun outil de mesure d'audience ni traceur.",
            "Aucune donnée personnelle n'est collectée ni envoyée. Favoris et réglages restent sur ce téléphone et sont exclus des sauvegardes.",
            "La recherche de ville et le placement des événements sont calculés hors ligne : le texte que vous tapez ne quitte pas l'appareil. En revanche, afficher une zone de la carte télécharge ses tuiles depuis OpenFreeMap, qui voit donc la zone consultée (comme tout site de cartes).",
            "Les tuiles déjà affichées sont gardées en cache sur ce téléphone pour éviter de les retélécharger ; effacer les données de l'application les supprime.",
            "Les dernières recherches (agenda et villes de la carte) sont mémorisées sur ce téléphone uniquement, pour être reprises d'un toucher : la croix retire une entrée, et « Effacer l'historique de recherche » dans les paramètres les supprime toutes.",
            "Les rappels sont planifiés localement. Aucune permission de localisation n'est demandée.",
            "L'application contacte uniquement les sources d'événements listées dans les paramètres et le fond de carte. Comme tout site web, ces services voient l'adresse IP du téléphone.",
            "Proposer un événement ouvre la page GitHub du projet dans votre navigateur (ou votre messagerie) : rien n'est envoyé sans votre action, et une proposition faite sur GitHub est publique. Partager un événement ouvre l'application de votre choix.",
        )

        item { SectionHeader("sécurité") }
        bullets(
            "Connexions chiffrées uniquement (HTTPS), avec épinglage des certificats des sources.",
            "Toutes les données reçues sont revérifiées : liens https uniquement, textes nettoyés, aucun contenu web exécuté.",
            "La sélection CyberAgenda est signée numériquement : une version modifiée est refusée.",
            "Avant d'ouvrir un site externe, l'application affiche son adresse exacte.",
        )

        item { SectionHeader("données et crédits") }
        bullets(
            "CTFtime.org — calendrier mondial des CTF.",
            "Agenda du Libre (agendadulibre.org) — événements du logiciel libre.",
            "Cybersecurity Events FR — liste communautaire d'Alexandre Dubois (GPL-3.0).",
            "confs.tech — données de conférences (MIT).",
            "OpenAgenda — événements publiés par leurs organisateurs, le plus souvent sous Licence Ouverte 2.0 ; la source est citée sur chaque fiche.",
            "Fond de carte OpenFreeMap ; données © contributeurs OpenStreetMap (ODbL).",
            "Communes : geo.api.gouv.fr (Etalab, Licence Ouverte 2.0).",
        )

        item { SectionHeader("logiciels libres") }
        bullets(
            "CyberAgenda © 2026 Kalypt — code source publié sous licence MIT.",
            "MapLibre Native (BSD-2-Clause).",
            "Kotlin, AndroidX, Jetpack Compose, Room, WorkManager, Hilt, OkHttp, Tink (Apache 2.0).",
            "Polices IBM Plex Sans, JetBrains Mono, Caveat, Playfair Display et Press Start 2P (SIL Open Font License 1.1).",
        )
    }
}

@Composable
private fun InfoScaffold(title: String, onBack: () -> Unit, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") }
            Text(title, style = Mono.label, color = AgendaTheme.colors.muted)
        }
        val list = rememberLazyListState()
        ScrollDivider(scrolledFromTop(list))
        LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(bottom = 32.dp), overscrollEffect = overscrollIfScrollable(list), content = content)
    }
}

@Composable
private fun Intro(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
}

private fun androidx.compose.foundation.lazy.LazyListScope.bullets(vararg lines: String) {
    items(lines.toList()) { line ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
            Text("·  ", style = Mono.label, color = MaterialTheme.colorScheme.primary)
            Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
