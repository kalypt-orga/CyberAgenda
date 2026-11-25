pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // Interdit aux modules de déclarer leurs propres dépôts : la provenance des dépendances reste centralisée.
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "CyberAgenda"

include(":app")
include(":core:model")
include(":core:network")
include(":core:database")
include(":core:data")
include(":core:ui")
include(":core:formats")
include(":core:geo")
// Une source d'événements = un module. Pour en ajouter une, voir README.md.
include(":source:ctftime")
include(":source:agendadulibre")
include(":source:frcyberevents")
include(":source:confstech")
include(":source:curated")
include(":source:openagenda")
include(":feature:events")
include(":feature:settings")
include(":feature:calendar")
include(":feature:map")
include(":feature:reminders")
