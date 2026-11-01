# Recul — spec

App Android perso (Nico + 1-2 amis), installée par APK. Bloque ou freine certaines apps (TikTok…).

## Principes
- Simple, belle, aérée : noir profond, un seul accent ambre, cercle qui respire.
- Textes courts. Le détail est caché.
- Rien ne sort du téléphone : seul le nom de l'app ouverte est utilisé.

## Fonctions (v0.1.4)
| Onglet | Quoi |
|---|---|
| Limites | Par app : pause à l'ouverture (Attente 2-60 s, défaut 5 s, ou 1-5 respirations) + limite/jour (5 min → 4 h) ; fin = « +5 min » (1-5/jour) ou « fini jusqu'à demain » |
| Codes | Rubrique à part : un code (haché) demandé à chaque ouverture d'une app ; 1 min pour y revenir sans le retaper |
| Stats | 7 jours, moyenne/jour, pauses du jour |

Pause façon Ascent : le compte à rebours démarre tout de suite, « Tu viens faire quoi ? » (💬 / 🎯 / 🌀), compteur d'ouvertures du jour, « Je ferme » en bouton principal. Après la pause, Recul ouvre l'app directement.

Ordre : limite atteinte > code > pause. Le code passé vaut aussi pour la pause.

Mise à jour auto : Recul vérifie la dernière Release (toutes les 6 h via le service, toutes les heures app ouverte) et l'installe via PackageInstaller (sans confirmation sur Android 12+ si possible). « Installer un fichier APK » dans Réglages : Recul installe lui-même un APK choisi (évite le blocage Play Protect des gestionnaires de fichiers).

Bug corrigé (v0.1.4) : l'app bloquée (ex. TikTok qui finit de se lancer) repassait devant l'écran de pause → Recul relançait l'écran et le compte à rebours repartait de zéro. Maintenant l'écran est juste remis devant, le temps est calculé sur l'horloge et continue.

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
- Push sur `main` → GitHub Actions compile, signe (secrets `SIGNING_*`) et publie la Release `v0.1.<run+10>`.
- Secrets : `SIGNING_KEYSTORE_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`.

## Premier lancement (APK installé à la main)
1. Accès « Temps d'utilisation ».
2. Accessibilité › Recul · Bloqueur. Si grisé : Infos de l'appli › ⋮ › Autoriser les paramètres restreints.
