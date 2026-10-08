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

## 🚨 RÈGLE FONDAMENTALE N°2 : SÉCURITÉ DU FOCUS COMPOSE SUR ANDROID TV

> [!CAUTION]
> ### 🛑 INTERDICTION STRICTE DE `focusProperties { up = dynamicRequester }` SUR LES LISTES DYNAMIQUES / LAZY
> Dans Jetpack Compose Android TV, lier un `FocusRequester` dynamique (appartenant à un item de `LazyVerticalGrid`, `LazyColumn` ou composant externe) dans un bloc `.focusProperties { up = ...; down = ... }` provoque **UN CRASH INSTANTANÉ DE L'APPLICATION** :
> `java.lang.IllegalStateException: FocusRequester is not initialized`
> dès que l'élément ciblé se retrouve hors-champ ou non encore recomposé !
>
> **Règle absolue :**
> 1. Ne JAMAIS utiliser `focusProperties { up = ..., down = ... }` pour naviguer vers ou depuis des éléments de listes virtuelles (`LazyVerticalGrid`, `LazyColumn`).
> 2. Gérer TOUJOURS la navigation D-Pad via `.onPreviewKeyEvent { event -> when (event.key) { Key.DirectionUp -> ... true } }`.
> 3. Scroller d'abord vers l'item si nécessaire (`gridState.scrollToItem(index)`), attendre un délai minimal (`delay(60)`), et TOUJOURS envelopper les appels de focus dans un bloc sécurisé :
>    ```kotlin
>    runCatching { targetFocusRequester?.requestFocus() }
>    ```


---

## 🛠️ Environnement & Commandes de Build

> [!CAUTION]
> ### ☕ RÈGLE IMPÉRATIVE JAVA : UTILISATION EXCLUSIVE DU JDK 21 LTS
> **IL EST FORMELLEMENT INTERDIT DE COMPILER AVEC UNE ANCIENNE VERSION DE JAVA (JDK 17, JDK 11 OU INFÉRIEURE).**
> Toute compilation (Debug ou Release) doit s'effectuer **EXCLUSIVEMENT** avec le JDK 21 installé à :
> `/home/chomiam/.local/share/jdk-21`
> 
> Avant toute invocation de `./gradlew`, il est obligatoire d'exporter les variables :
> ```bash
> export JAVA_HOME=/home/chomiam/.local/share/jdk-21 && export PATH=$JAVA_HOME/bin:$PATH
> ```

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

## 🛡️ SÉCURITÉ APPLICATIVE & DÉFENSE EN PROFONDEUR (ANTI-IA & ANTI-REVERSE)

> [!WARNING]
> **NoosTV intègre une architecture de défense en profondeur contre l'analyse automatisée par IA, le reverse-engineering et l'extraction de secrets.**

### 1. Règle Zéro Secret dans le code et les builds
* **Aucun `buildConfigField` ni `resValue` pour un secret** dans `app/build.gradle.kts`. Ne jamais injecter de token depuis `local.properties`, une variable d'environnement ou une ressource embarquée.
* **Aucun secret en dur** dans `gradle.properties`, `local.properties` versionné, `app/src/main/assets` ou `app/src/main/res`.
* **Ne jamais commiter de fichier contenant un secret** (`local.properties`, `.env`, keystores, PAT). Vérifier avant tout commit :
  ```bash
  git ls-files | grep -iE 'secret|token|pass|cred|\.env|keystore|\.jks'
  ```
* **Chiffrement matériel au repos** : Les identifiants IPTV / Xtream sont chiffrés en **AES-256-GCM** adossé au TEE / Android KeyStore matériel via [`CryptoManager`](file:///home/chomiam/Projets/noostv/app/src/main/java/io/noostv/core/security/CryptoManager.kt) et [`SessionManager`](file:///home/chomiam/Projets/noostv/app/src/main/java/io/noostv/core/storage/SessionManager.kt). Les indexations de catalogues volumineux sont également chiffrées localement via [`EncryptedCatalogStore`](file:///home/chomiam/Projets/noostv/app/src/main/java/io/noostv/data/cache/EncryptedCatalogStore.kt).

### 2. Obfuscation & Minification R8 Obligatoires en Release
* Dans `app/build.gradle.kts`, `isMinifyEnabled = true` et `isShrinkResources = true` sont **obligatoires** pour les variantes Release.
* Toutes les classes métier sensibles (`core.security`, `core.storage`, `data.api`) sont obfusquées et renommées par R8.
* Les modèles de données Gson (`io.noostv.data.model.**`), Jetpack Compose et Media3/ExoPlayer sont verrouillés via [`app/proguard-rules.pro`](file:///home/chomiam/Projets/noostv/app/proguard-rules.pro) pour éviter tout crash de sérialisation.

### 3. Protection contre l'Extraction Physique (Anti-ADB dump)
* Dans `AndroidManifest.xml`, `android:allowBackup="false"` est formellement exigé.
* Cela empêche toute extraction brute des bases locales, des sessions chiffrées et des caches de streaming via la commande `adb backup` lors d'un accès physique à la box TV.

### 4. Cloisonnement Réseau & HTTPS Strict Ciblée
* `AndroidManifest.xml` référence [`res/xml/network_security_config.xml`](file:///home/chomiam/Projets/noostv/app/src/main/res/xml/network_security_config.xml) :
  * **HTTPS strict** imposé sur les domaines de mise à jour, métadonnées et GitHub (`api.github.com`, `themoviedb.org`, `tmdb.org`).
  * Trafic clair (`cleartext`) limité exclusivement aux flux vidéo bruts (`.ts`, `.m3u8`) pour préserver la compatibilité des serveurs IPTV hérités sans exposer les canaux d'administration.

### 5. Détection d'Instrumentation Dynamique & Anti-Frida
* Le composant [`AppIntegrityChecker`](file:///home/chomiam/Projets/noostv/app/src/main/java/io/noostv/core/security/AppIntegrityChecker.kt) est appelé à l'initialisation de l'application :
  * Détecte l'injection d'agents de rétro-ingénierie et de hooking IA (`frida-agent.so`, `gadget.so`, `xposed`) dans `/proc/self/maps`.
  * Sonde le port TCP local par défaut (27042) utilisé par les serveurs Frida.
  * Détecte la présence d'un débogueur attaché en production (`Debug.isDebuggerConnected()`).
  * Signale toute tentative d'altération sans bloquer violemment les box TV équipées de firmwares constructeurs personnalisés.

### 6. Assainissement des Données Sensibles (Anti-Leak)
* Toute manipulation d'URL de flux ou d'appel API passe par [`XtreamCodesClient.sanitizeUrl()`](file:///home/chomiam/Projets/noostv/app/src/main/java/io/noostv/data/api/XtreamCodesClient.kt) pour remplacer automatiquement les mots de passe et jetons (`password=***`, `/live/user/***/id.ts`) avant toute journalisation ou inclusion dans une trace d'exception.
* Configuration d'OkHttp avec `ConnectionSpec.MODERN_TLS` et normalisation du `User-Agent` (`NoosTV/1.2.6 (Android TV; ExoPlayer)`) pour éliminer le fingerprinting d'outils automatisés.

### 7. Contrôle d'Intégrité des Mises à Jour OTA
* Dans [`UpdateManager.installApk()`](file:///home/chomiam/Projets/noostv/app/src/main/java/io/noostv/core/update/UpdateManager.kt), avant de transmettre l'archive à l'installateur système Android :
  * Validation de la structure du paquet via `PackageManager.getPackageArchiveInfo()`.
  * Contrôle strict que le `packageName` correspond exactement à `io.noostv` et suppression immédiate du fichier si l'archive est corrompue ou altérée par une interception réseau.

### 8. Vérification post-build
Après chaque `assembleDebug`, contrôler l'absence de secrets résiduels dans l'APK :
```bash
cd /tmp && rm -rf apkx && mkdir apkx && cd apkx
unzip -o /home/chomiam/Projets/noostv/app/build/outputs/apk/debug/app-debug.apk 'classes*.dex'
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
