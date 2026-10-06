# Plan de Tests Unitaires et Procédures de Validation NoosTV

Ce document détaille le plan de test unitaire, la matrice de validation et les procédures d'exécution pour certifier la robustesse et la conformité du lecteur IPTV NoosTV sur Android TV (Xiaomi Mi Box, Google TV Streamer 4K) et Mobiles/Tablettes.

---

## 1. Périmètre et Stratégie de Test

Les tests ciblent les composants critiques sans dépendance matérielle obligatoire, garantissant une exécution rapide en intégration continue (CI/CD) et en développement local :

1. **Détection Matérielle Adaptative (`DeviceDetector`)** : Garantit le basculement automatique entre l'interface 10-foot D-Pad (Leanback) et l'interface tactile Mobile.
2. **Gestionnaire de Droits SaaS (`EntitlementManager`)** : Garantit la protection des flux 4K HDR, du Replay EPG, et de la limitation d'écrans simultanés (1 en Free, 4 en VIP).
3. **Analyseur de Playlists M3U/M3U8 (`M3UParser`)** : Garantit l'extraction correcte des chaînes, logos, catégories et la détection automatique des flux 4K/HDR et codecs AV1/HEVC.
4. **Analyseur EPG XMLTV (`XmlTvParser`)** : Garantit le traitement des flux de programmes, le parsing des dates et le calcul temps réel de la progression du direct.
5. **Moteur de Recherche Universel (`UniversalSearchEngine`)** : Garantit la recherche multi-contenus tolérante aux accents et insensible à la casse.

---

## 2. Matrice de Couverture des Tests Unitaires

| Classe de Test | Méthode de Test | Scénario & Assertions | Statut |
| :--- | :--- | :--- | :---: |
| **`DeviceDetectorTest`** | `when system has leanback feature should detect TELEVISION` | Simule `FEATURE_LEANBACK` activé (Mi Box / Google TV Streamer). Vérifie `deviceType == TELEVISION` et `isTv == true`. |  **SUCCÈS** |
| **`DeviceDetectorTest`** | `when screen width is 400dp should detect PHONE` | Simule un smartphone (largeur 400dp). Vérifie `deviceType == PHONE`. |  **SUCCÈS** |
| **`DeviceDetectorTest`** | `when screen width is 800dp should detect TABLET` | Simule une tablette (largeur 800dp). Vérifie `deviceType == TABLET`. |  **SUCCÈS** |
| **`EntitlementManagerTest`** | `default user should be on FREE tier and denied 4K HDR and multi-screens` | Vérifie que le profil gratuit refuse l'accès aux fonctionnalités VIP (HDR/4K, Replay) et bloque le 2ème écran. |  **SUCCÈS** |
| **`EntitlementManagerTest`** | `upgradeToPremium should unlock 4K HDR and allow up to 4 screens` | Vérifie le déblocage immédiat de toutes les fonctionnalités VIP et l'autorisation de 4 écrans simultanés. |  **SUCCÈS** |
| **`EntitlementManagerTest`** | `expired subscription should be denied all features` | Vérifie qu'un compte dont la date d'expiration est passée est rétrogradé sans accès aux flux 4K. |  **SUCCÈS** |
| **`M3UParserTest`** | `parse valid M3U playlist should extract channels with metadata` | Valide l'extraction de `tvg-id`, `tvg-logo`, `group-title`, résolution 1080p et 4K UHD. |  **SUCCÈS** |
| **`M3UParserTest`** | `parse playlist with AV1 codec tag should identify AV1 video codec` | Vérifie la détection et la priorisation du codec matériel de nouvelle génération `AV1`. |  **SUCCÈS** |
| **`M3UParserTest`** | `parse empty or malformed playlist should not crash` | Vérifie la résilience du lecteur face à une playlist corrompue ou vide. |  **SUCCÈS** |
| **`XmlTvParserTest`** | `parse valid XMLTV stream should extract EPG programs and dates` | Valide la désérialisation XML standard, l'extraction des titres, descriptions et conversion horaire en millisecondes epoch. |  **SUCCÈS** |
| **`XmlTvParserTest`** | `epgProgram progressFraction should calculate correct percentage` | Vérifie que le ratio d'avancement du direct renvoie exactement 0.0f au début, 0.5f à mi-chemin, et 1.0f à la fin. |  **SUCCÈS** |
| **`UniversalSearchEngineTest`** | `search should be accent-insensitive and match accented words` | Vérifie que chercher `cinema` trouve `Canal+ Cinéma HD` et `amelie` trouve `Le Fabuleux Destin d'Amélie Poulain`. |  **SUCCÈS** |
| **`UniversalSearchEngineTest`** | `search with empty query should return empty result` | Vérifie qu'une chaîne de recherche vide ne consomme pas de ressources et renvoie une liste vide propre. |  **SUCCÈS** |

---

## 3. Commandes d'Exécution

Pour relancer l'ensemble de la suite de tests unitaires :

```bash
cd /home/chomiam/Projets/noostv
./gradlew testDebugUnitTest
```

Le rapport visuel interactif au format HTML est généré automatiquement ici :
`file:///home/chomiam/Projets/noostv/app/build/reports/tests/testDebugUnitTest/index.html`

Pour recompiler l'APK installable :
```bash
./gradlew assembleDebug
```
L'APK prêt à l'emploi est produit dans :
`/home/chomiam/Projets/noostv/app/build/outputs/apk/debug/app-debug.apk`

---

## 4. Guide de Test sur Boîtiers Physiques & Émulateur Waydroid

### 4.1 Déploiement sur Xiaomi Mi Box / Google TV Streamer 4K
1. Activez le **Mode Développeur** sur la box : *Paramètres > Système > À propos > Cliquez 7 fois sur "Build OS"*.
2. Activez le **Débogage Réseau (ADB)** dans *Options développeur*.
3. Depuis votre terminal Pop!_OS :
   ```bash
   adb connect 192.168.1.XX:5555
   adb install -r /home/chomiam/Projets/noostv/app/build/outputs/apk/debug/app-debug.apk
   ```
4. L'application apparaît instantanément dans la rangée d'applications Android TV / Google TV avec sa bannière personnalisée 16:9.

### 4.2 Installation et Test via Waydroid (Pop!_OS 24.04 Wayland)
Pop!_OS 24.04 tourne nativement sous Wayland (`wayland-1`), ce qui est idéal pour Waydroid.
Pour installer et initialiser Waydroid sur votre système (nécessite les privilèges sudo) :
```bash
sudo apt update
sudo apt install -y curl ca-certificates
export DISTRO="noble"
curl -s https://repo.waydro.id | sudo bash
sudo apt install -y waydroid

# Initialisation de l'image Android
sudo waydroid init

# Démarrage du conteneur et du serveur graphique
sudo systemctl start waydroid-container
waydroid session start &

# Installation de NoosTV dans Waydroid
adb -s emulator-5554 install /home/chomiam/Projets/noostv/app/build/outputs/apk/debug/app-debug.apk
```
