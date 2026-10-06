<div align="center">

<img src="docs/logo.png" alt="NoosTV Logo" width="180" />

# NoosTV

**Lecteur IPTV / OTT moderne, fluide et immersif pour Android TV & Android Mobile**

[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.22-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Android](https://img.shields.io/badge/Android-8.0%2B%20(API%2026%2B)-3DDC84?logo=android&logoColor=white)](https://developer.android.com/)
[![Compose TV](https://img.shields.io/badge/Jetpack%20Compose-Android%20TV%20%26%20M3-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Media3](https://img.shields.io/badge/Media3%20ExoPlayer-1.2.1-FF6F00?logo=youtube&logoColor=white)](https://developer.android.com/media/media3)
[![GitHub Releases](https://img.shields.io/badge/Release-v1.0.1%20%2F%20v1.1.0--beta.1-00E5FF)](https://github.com/Chomiam/noostv/releases)

---

</div>

## 📌 Présentation

**NoosTV** est une application Android moderne conçue sur mesure pour offrir une expérience de streaming de premier plan sur **téléviseurs connectés, box Android TV (Nvidia Shield, Xiaomi Mi Box, Chromecast avec Google TV)** ainsi que sur **smartphones et tablettes Android**.

Développée intégralement en **Kotlin** et **Jetpack Compose for TV**, NoosTV intègre les standards visuels les plus modernes : thème sombre OLED épuré, angles arrondis prononcés, halos lumineux de sélection télécommande D-Pad, miniatures cinématographiques avec défilement de texte automatique (*marquee*), et un mode de prévisualisation live split sur un quart d'écran.

---

## ✨ Fonctionnalités Principales

### 📺 1. TV en Direct avec Marquee & Backdrops Cinématographiques
- **Grille de chaînes interactive** à 4 colonnes avec logos vectorisés ou haute résolution.
- **Défilement automatique du titre (*Marquee Text*)** : le nom du programme en cours défile en continu sur la carte pour une lisibilité parfaite même sur les titres longs.
- **Illustration contextuelle en arrière-plan** : affichage d'une image immersive adaptée au type de chaîne ou de programme (stade de football/sport, plateau de journal télévisé, salle de cinéma, documentaire nature/espace, animation).
- **Indicateurs en direct** : badge vibrant `DIRECT`, résolution (`1080p`, `4K UHD`, `HDR`) et jauge de progression horaire en temps réel.

<div align="center">
  <img src="docs/screenshots/tv_channels_marquee.png" alt="Grille TV avec Marquee et Backdrops" width="85%" />
  <p><em>Grille TV avec jaquettes thématiques, texte défilant et badges de direct</em></p>
</div>

---

### 🔲 2. Prévisualisation Live 1/4 d'Écran & Guide TV Dédié
- **Split View immersive** déclenchée au clic sur n'importe quelle chaîne :
  - **Colonne de gauche** : zapping rapide dans la liste des chaînes avec indication de la chaîne active.
  - **Zone supérieure droite (1/4 d'écran)** : lecteur vidéo ExoPlayer diffusant le flux live en direct. Un clic ou appui sur `OK` passe instantanément en mode plein écran sans coupure de flux.
  - **Zone inférieure droite** : **Guide TV de la chaîne sélectionnée** avec le programme actuel (« EN CE MOMENT »), la jauge d'avancement, le résumé complet et les émissions suivantes (« À SUIVRE ») avec statut Replay.
- **Navigation télécommande intuitive** : retour direct du plein écran vers la prévisualisation, puis de la prévisualisation vers la grille complète.

<div align="center">
  <img src="docs/screenshots/tv_channel_preview_quadrant.png" alt="Prévisualisation 1/4 d'écran et Guide TV" width="85%" />
  <p><em>Prévisualisation live 1/4 d'écran avec flux en direct et guide TV de la chaîne en dessous</em></p>
</div>

---

### 📅 3. Guide TV Interactif (EPG Complet)
- Vue tabulaire horizontale et chronologique pour visualiser les programmes par chaîne et par tranche horaire.
- Mise en évidence immédiate des programmes en diffusion en direct et du contenu disponible en replay.
- Synchronisation continue avec l'API EPG Xtream Codes et synthèse dynamique locale en mode déconnecté.

<div align="center">
  <img src="docs/screenshots/interactive_tv_guide.png" alt="Guide TV Interactif" width="85%" />
  <p><em>Guide TV interactif avec grille horaire et statut en temps réel</em></p>
</div>

---

### 🎬 4. Catalogue Films & Séries (VOD)
- **Onglets dédiés distincts** pour les **Films** et les **Séries**.
- **Format d'affiche vertical 2:3** respectant les proportions des affiches officielles de cinéma sans rognage.
- **Filtres par catégories** : Action, Comédie, Thriller, Science-Fiction, Documentaires, etc.
- **Détails riches** : note IMDb, date de sortie, durée, genre, résumé, et navigation par saisons/épisodes pour les séries.

<div align="center">
  <img src="docs/screenshots/movies_catalog.png" alt="Catalogue Films VOD" width="45%" />
  &nbsp;&nbsp;
  <img src="docs/screenshots/series_catalog.png" alt="Catalogue Séries TV" width="45%" />
  <p><em>Catalogues Films et Séries au ratio cinématographique 2:3</em></p>
</div>

---

### ⚙️ 5. Paramètres & Mises à Jour OTA GitHub (Stable / Testing)
- **Sélecteur de canal de mise à jour** : permet de basculer en un clic entre la branche **Stable** (production recommandée) et la branche **Testing** (bêta avec fonctionnalités en avant-première).
- **Vérification OTA intégrée** : interroge l'API GitHub Releases et affiche le numéro de version, les notes de version (*changelog*) et permet le téléchargement direct de l'APK.
- **Gestionnaire de compte** : statut de connexion, affichage de l'URL du serveur actif et déconnexion sécurisée.

<div align="center">
  <img src="docs/screenshots/settings_ota_updates.png" alt="Écran des Paramètres et Mises à Jour" width="85%" />
  <p><em>Menu Paramètres avec sélecteur de canal Stable / Testing et vérification OTA</em></p>
</div>

---

### 🔑 6. Connexion Xtream Codes & Mode Démo
- Écran de connexion ergonomique supportant la télécommande TV et le clavier virtuel :
  - **URL du serveur** (HTTP / HTTPS)
  - **Identifiant**
  - **Mot de passe**
- **Mode Démo intégré** : permet de tester l'intégralité de l'application (flux live, 4K HDR, films, séries, EPG) sans identifiants externes.

<div align="center">
  <img src="docs/screenshots/login_screen.png" alt="Écran de Connexion" width="70%" />
  <p><em>Écran d'authentification Xtream Codes</em></p>
</div>

---

## 🛠️ Stack Technique & Architecture

| Composant | Technologie | Description |
|---|---|---|
| **Langage** | [Kotlin 1.9](https://kotlinlang.org/) | 100% Kotlin moderne avec Coroutines & Flow |
| **Interface TV** | [Compose for TV 1.0](https://developer.android.com/jetpack/compose) | Composables TV optimisés pour le focus D-Pad |
| **Design System** | [Material 3](https://m3.material.io/) | Thème sombre OLED, palettes cyan & néon, arrondis 20dp |
| **Lecteur Vidéo** | [AndroidX Media3 ExoPlayer 1.2.1](https://developer.android.com/media/media3) | Support HLS, DASH, TS, MP4, décodage HEVC / 4K / HDR |
| **Réseau & API** | [Ktor Client 2.3](https://ktor.io/) & [Kotlinx Serialization](https://github.com/Kotlin/kotlinx.serialization) | Client HTTP asynchrone pour l'API Xtream Codes |
| **Images** | [Coil 2.5](https://coil-kt.github.io/coil/) | Chargement d'images asynchrone avec cache disque/RAM |
| **Stockage** | [EncryptedSharedPreferences](https://developer.android.com/reference/androidx/security/crypto/EncryptedSharedPreferences) | Chiffrement matériel des identifiants et tokens de session |

---

## 📥 Téléchargement & Installation

### Option 1 — Téléchargement des APKs
Téléchargez directement le fichier `.apk` depuis la page des [Releases GitHub](https://github.com/Chomiam/noostv/releases) :
- **Canal Stable** : `noostv-v1.0.1.apk`
- **Canal Testing** : `noostv-v1.1.0-beta.1.apk`

### Option 2 — Installation via ADB
```bash
# Installation sur une TV ou Box connectée en réseau
adb connect <IP_DE_VOTRE_BOX>:5555
adb install -r noostv-v1.0.1.apk

# Lancement direct
adb shell am start -n io.noostv/.MainActivity
```

---

## 🔨 Compilation depuis les Sources

### Prérequis
- **JDK 17** ou supérieur
- **Android SDK** (Build-Tools `34.0.0`, Platform `android-34`)
- **Gradle 8.2+** (inclus via Gradle Wrapper)

### Commandes
```bash
# Cloner le dépôt
git clone https://github.com/Chomiam/noostv.git
cd noostv

# Compiler l'APK Debug
./gradlew assembleDebug

# Compiler l'APK Release optimisé
./gradlew assembleRelease

# Déployer directement sur un appareil connecté
./gradlew installDebug
```

---

## 📄 Licence & Crédits

- Projet développé pour l'écosystème **Noos**.
- Tous droits réservés &copy; 2026 **NoosTV**.
