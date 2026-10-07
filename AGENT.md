# 🤖 Guide de Développement & Consignes pour Agents IA — NoosTV

Ce document définit les règles impératives, les consignes d'architecture et les protocoles de vérification pour tout agent IA (ou développeur) intervenant sur le projet **NoosTV**.

---

## 🚨 RÈGLE FONDAMENTALE N°1 : GESTION DES BRANCHES GIT

> [!CAUTION]
> **IL EST FORMELLEMENT INTERDIT DE COMMITER OU DE POUSSER DIRECTEMENT SUR LA BRANCHE `stable`.**
> 
> Tout le travail de développement, de correction de bugs, de refactoring et d'ajout de fonctionnalités **doit être réalisé EXCLUSIVEMENT sur la branche `testing`**.
>
> La seule et unique exception est lorsque **l'utilisateur demande explicitement** dans son message de promouvoir / fusionner la version sur `stable` (ex. : *"Pousse la testing vers la stable"*, *"Publie en version stable"*).

### Protocole de travail standard :
1. Vérifier la branche active avant toute intervention :
   ```bash
   git branch --show-current   # Doit impérativement être 'testing'
   ```
2. Si vous n'êtes pas sur `testing`, basculez dessus :
   ```bash
   git checkout testing
   git pull origin testing
   ```
3. Commiter vos modifications avec des messages sémantiques clairs (`feat(...)`, `fix(...)`, `refactor(...)`).
4. Pousser uniquement vers `testing` :
   ```bash
   git push origin testing
   ```

---

## 🛠️ Environnement & Commandes de Build

L'environnement de développement nécessite le **JDK 21** pour compiler correctement le projet Gradle / Kotlin.

### 1. Variables d'environnement requises
Toujours exporter `JAVA_HOME` vers JDK 21 avant toute commande Gradle :
```bash
export JAVA_HOME=/home/chomiam/.local/share/jdk-21 && export PATH=$JAVA_HOME/bin:$PATH
```

### 2. Compilation & Assemblage
* **Compilation Kotlin (rapide pour valider la syntaxe et les types)** :
  ```bash
  export JAVA_HOME=/home/chomiam/.local/share/jdk-21 && export PATH=$JAVA_HOME/bin:$PATH && ./gradlew compileDebugKotlin
  ```
* **Génération complète de l'APK Debug** :
  ```bash
  export JAVA_HOME=/home/chomiam/.local/share/jdk-21 && export PATH=$JAVA_HOME/bin:$PATH && ./gradlew assembleDebug
  ```
  L'APK généré se situe dans : `app/build/outputs/apk/debug/app-debug.apk`

---

## 📺 Déploiement & Validation sur Matériel Réel (Android TV)

L'utilisateur dispose d'une Android TV connectée en Wi-Fi / ADB sur le réseau local (`192.168.1.16:5555`).

### 1. Vérification de la connexion ADB
```bash
/home/chomiam/Android/Sdk/platform-tools/adb devices
# Si la TV est déconnectée :
/home/chomiam/Android/Sdk/platform-tools/adb connect 192.168.1.16:5555
```

### 2. Installation de l'APK
```bash
/home/chomiam/Android/Sdk/platform-tools/adb -s 192.168.1.16:5555 install -r app/build/outputs/apk/debug/app-debug.apk
```

### 3. Démarrage de l'application
```bash
/home/chomiam/Android/Sdk/platform-tools/adb -s 192.168.1.16:5555 shell am start -n io.noostv/.MainActivity
```

### 4. Piège des captures d'écran vidéo (Surfaces matérielles)
> [!WARNING]
> Sur Android TV, les puces de décodage matériel (Amlogic, MediaTek) utilisent des surfaces overlay DRM/hardware sécurisées.
> `screencap -p` renverra une image noire pour la zone vidéo d'ExoPlayer.
> **Pour valider l'interface utilisateur, utilisez TOUJOURS `uiautomator dump`** :
> ```bash
> adb -s 192.168.1.16:5555 shell uiautomator dump /sdcard/dump.xml
> adb -s 192.168.1.16:5555 shell cat /sdcard/dump.xml
> ```
> Cela permet de vérifier la présence exacte des textes, des boutons, de la pastille de résolution et de l'état `focused="true"`.

### 5. Inspection des logs ciblés
```bash
adb -s 192.168.1.16:5555 logcat -d -s PlayerEngine,NoosPlayer,SoundEffectManager,AndroidRuntime | tail -n 50
```

---

## 🔐 SÉCURITÉ : AUCUN SECRET DANS L'APK

> [!WARNING]
> **Il est FORMELLEMENT INTERDIT d'embarquer un identifiant ou un secret dans l'APK compilé.**
> Cela inclut les identifiants IPTV / Xtream Codes (serveur, `username`, `password`) ET tout token GitHub / PAT.

### Règles impératives
1. **Aucun `buildConfigField` ni `resValue` pour un secret** dans `app/build.gradle.kts`. Ne jamais injecter de token depuis `local.properties`, une variable d'environnement ou une ressource embarquée.
2. **Aucun secret en dur** dans `gradle.properties`, `local.properties` versionné, `app/src/main/assets` ou `app/src/main/res`.
3. **Ne jamais commiter de fichier contenant un secret** (`local.properties`, `.env`, keystores, PAT). Vérifier avant tout commit :
   ```bash
   git ls-files | grep -iE 'secret|token|pass|cred|\.env|keystore|\.jks'
   ```
4. **Les identifiants IPTV / Xtream sont chiffrés au runtime** (AES-256-GCM, Android Keystore via `CryptoManager` / `SessionManager`). Ils ne doivent jamais exister en clair dans le code ni dans l'APK.
5. **Le jeton GitHub (OTA dépôts privés) est saisi par l'utilisateur dans les Réglages** (composable `GithubTokenField`, champ « JETON GITHUB (DÉPÔT PRIVÉ) » sur mobile et TV) et stocké chiffré via `SessionManager.githubToken`. L'OTA lit uniquement `sessionManager.githubToken`, jamais `BuildConfig`.

### Vérification post-build
Après chaque `assembleDebug`, contrôler l'absence de secrets dans l'APK :
```bash
cd /tmp && rm -rf apkx && mkdir apkx && cd apkx
unzip -o /root/noostv/app/build/outputs/apk/debug/app-debug.apk 'classes*.dex'
strings -n 6 classes*.dex | grep -iE 'ghp_|github_pat_|player_api|username=|password='
```
Le résultat ne doit contenir que des **formats d'URL** (`player_api`, `username=`, `password=`), **aucun** vrai secret ni host:port Xtream.

---

## 🏗️ Architecture Clé du Codebase

* **`MainActivity.kt`** : Point d'entrée de l'application, instanciation des managers racine, capture globale des événements clavier D-Pad (`dispatchKeyEvent`) pour les retours sonores Apple TV, et routage des écrans (`CurrentScreen`).
* **`io.noostv.core.player.PlayerEngine`** : Moteur Media3 ExoPlayer singleton. Gère les flux, l'accélération matérielle, l'extraction de pistes vidéo/audio/sous-titres, les statistiques en temps réel (`PlaybackStats`) et la bascule entre mode prévisualisation (720p/2.5Mbps) et plein écran.
* **`io.noostv.core.audio.SoundEffectManager`** : Gestionnaire audio `SoundPool` à latence zéro pour les retours sonores tvOS. Rate-limiting intégré (45ms).
* **`io.noostv.core.storage.SessionManager`** & **`CryptoManager`** : Gestionnaire de préférences et de sessions avec chiffrement matériel AES-256-GCM (Android KeyStore). Stocke la session active, l'historique multi-comptes, la langue de l'application et les préférences audio.
* **`io.noostv.ui.tv.*`** : Composables dédiés à Android TV (D-pad focus management, halos lumineux, animations d'échelle, split-view direct TV).
* **`io.noostv.ui.player.NoosPlayerScreen`** : Lecteur vidéo unifié. Barre supérieure épurée (titre, badges dynamiques, horloge), contrôles OSD placés dans la **barre inférieure** sous Play/Pause, et dialogue modale multi-onglets (Infos, Vitesse, Qualité, Audio & Subs).

---

## ✅ Checklist Obligatoire pour tout Agent

Avant de finaliser votre intervention et de répondre à l'utilisateur :
- [ ] Le code a été vérifié sur la branche **`testing`**.
- [ ] La compilation Kotlin est réussie sans aucune régression (`./gradlew compileDebugKotlin`).
- [ ] L'APK a été assemblé (`./gradlew assembleDebug`).
- [ ] Si la TV est connectée (`adb devices`), l'APK a été installé et testé.
- [ ] Les commits ont été poussés sur `origin/testing` (et jamais sur `stable` sans instruction explicite).
