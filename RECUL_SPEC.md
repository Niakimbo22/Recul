# Recul — spec

App Android perso (Nico + 1-2 amis), installée par APK. Bloque ou freine certaines apps (TikTok…).

## Principes
- Simple, belle, aérée : noir profond, un seul accent ambre, cercle qui respire.
- Textes courts. Le détail est caché.
- Rien ne sort du téléphone : seul le nom de l'app ouverte est utilisé.

## Fonctions (v0.1)
| Quoi | Comment |
|---|---|
| Limite par jour | 5 min → 4 h par app (temps calculé via UsageStatsManager) |
| Quand c'est fini | « +5 min » (1 à 5 fois/jour) ou « Bloqué jusqu'à demain » (aucun contournement) |
| Verrou à l'ouverture | Épreuve à chaque ouverture ; on peut revenir dans l'app pendant 1 min sans la refaire |
| Épreuves | Respiration (3 cycles 4 s / 5 s), Attente (10-120 s), Code (propre à chaque app, haché) |
| Stats | 7 derniers jours, moyenne/jour, classement des apps |
| Code de Recul | Protège l'ouverture de Recul lui-même |
| Mise à jour | Bouton dans Réglages → dernière Release GitHub |

## Plus tard
- Mode IA gratuite : « tu viens faire quoi sur TikTok ? » avant d'ouvrir.
- Empêcher la désinstallation (Device Admin).

## Technique
- Kotlin + Jetpack Compose, minSdk 29, targetSdk 35.
- `ReculAccessibilityService` : écoute `TYPE_WINDOW_STATE_CHANGED`, décide (limite / verrou) et ouvre `BlockActivity` par-dessus. Vérifie aussi toutes les 15 s l'app au premier plan (limite atteinte en cours d'usage).
- `Usage` : temps de premier plan depuis minuit (événements RESUMED/PAUSED/STOPPED, écran éteint).
- `Rules` : règles en JSON dans SharedPreferences ; +5 min remis à zéro chaque jour.
- `Updater` : API GitHub Releases, téléchargement, installation via FileProvider.

## Build / CI
- Push sur `main` → GitHub Actions compile, signe (secrets `SIGNING_*`) et publie la Release `v0.1.<run>`.
- Secrets : `SIGNING_KEYSTORE_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`.

## Premier lancement (APK installé à la main)
1. Accès « Temps d'utilisation ».
2. Accessibilité › Recul · Bloqueur. Si grisé : Infos de l'appli › ⋮ › Autoriser les paramètres restreints.
