# 🧠 Retour d'Expérience & Pièges à Éviter — NoosTV (SKILLS.md)

Ce document récapitule les apprentissages critiques, les pièges techniques sournois et les règles d'architecture découverts lors du développement de **NoosTV**. Il a pour but d'éviter toute régression future par un agent IA ou un développeur.

---

## 🎯 Sommaire des Pièges Techniques
1. [La détection de résolution et le ratio CinemaScope (1920×800 vs 1080p)](#1-la-détection-de-résolution-et-le-ratio-cinemascope-1920800-vs-1080p)
2. [Le mode prévisualisation ExoPlayer et la fuite de métriques](#2-le-mode-prévisualisation-exoplayer-et-la-fuite-de-métriques)
3. [L'ergonomie de l'OSD et des boutons d'options sur Android TV](#3-lergonomie-de-losd-et-des-boutons-doptions-sur-android-tv)
4. [La gestion du focus D-Pad dans les catalogues et modales TV](#4-la-gestion-du-focus-d-pad-dans-les-catalogues-et-modales-tv)
5. [Les sons de navigation d'interface et le SoundPool sur TV](#5-les-sons-de-navigation-dinterface-et-le-soundpool-sur-tv)
6. [Le chiffrement matériel AES-256 et la sécurité des identifiants](#6-le-chiffrement-matériel-aes-256-et-la-sécurité-des-identifiants)
7. [La mémoire graphique (Coil & GPU Mali/Amlogic)](#7-la-mémoire-graphique-coil--gpu-maliamlogic)
8. [Optimisations Performances IPTV à Grande Échelle (28 000+ Chaînes & VOD)](#8-optimisations-performances-iptv-à-grande-échelle-28-000-chaînes--vod)
9. [Persistance des Favoris & Double Couche de Sauvegarde (SharedPreferences + Disque)](#9-persistance-des-favoris--double-couche-de-sauvegarde-sharedpreferences--disque)
10. [Mises à Jour OTA Publiques GitHub (S3 Redirect 400 & Absence de Token)](#10-mises-à-jour-ota-publiques-github-s3-redirect-400--absence-de-token)
11. [Sécurité Applicative, Défense Anti-IA et Durcissement Android](#11-sécurité-applicative-défense-anti-ia-et-durcissement-android)

---

## 1. La détection de résolution et le ratio CinemaScope (1920×800 vs 1080p)

### ⚠️ Le Piège
La grande majorité des films 1080p (VOD, MKV, MP4) sont encodés au format large cinéma (**2.35:1** ou **2.39:1** CinemaScope), avec les bandes noires supérieures et inférieures rognées dans l'encodage.
Leurs dimensions réelles sont donc :
* **1920 × 800**
* **1920 × 804**
* **1920 × 816**
* **1920 × 856**

Si votre algorithme teste uniquement la hauteur avec une condition stricte :
```kotlin
// ❌ PIÈGE MAJEUR : 800 est inférieur à 1080, mais supérieur à 720 !
val res = when {
    height >= 2160 -> "4K"
    height >= 1080 -> "1080p"
    height >= 720 -> "720p"    // <-- 800 tombe ICI par erreur !
    else -> "SD"
}
```
Le lecteur affichera alors **"720p"** sur la pastille et sur le bouton de qualité alors même que le flux est en Full HD 1080p !

### ✅ La Solution Robuste
Toujours combiner la largeur, la hauteur avec des seuils adaptés au format cinéma, et les labels de métadonnées :
```kotlin
// ✔️ SOLUTION UNIVERSELLE :
val effectiveWidth = selectedVideoTrack?.width?.takeIf { it > 0 } ?: playbackStats.width.takeIf { it > 0 }
val effectiveHeight = selectedVideoTrack?.height?.takeIf { it > 0 } ?: playbackStats.height.takeIf { it > 0 }

val displayRes = when {
    selectedVideoTrack?.label?.contains("4K", ignoreCase = true) == true ||
    (effectiveWidth != null && effectiveWidth >= 3200) ||
    (effectiveHeight != null && effectiveHeight >= 1800) -> "4K"

    selectedVideoTrack?.label?.contains("1080", ignoreCase = true) == true ||
    selectedVideoTrack?.label?.contains("FHD", ignoreCase = true) == true ||
    (effectiveWidth != null && effectiveWidth >= 1600) ||
    (effectiveHeight != null && effectiveHeight >= 800) -> "1080p"

    selectedVideoTrack?.label?.contains("720", ignoreCase = true) == true ||
    selectedVideoTrack?.label?.contains("HD", ignoreCase = true) == true ||
    (effectiveWidth != null && effectiveWidth >= 1100) ||
    (effectiveHeight != null && effectiveHeight >= 600) -> "720p"

    effectiveHeight != null && effectiveHeight in 360..599 -> "SD"
    resolution.contains("4K", ignoreCase = true) || resolution.contains("UHD", ignoreCase = true) -> "4K"
    resolution.contains("1080", ignoreCase = true) || resolution.contains("FHD", ignoreCase = true) -> "1080p"
    resolution.contains("720", ignoreCase = true) || resolution.contains("HD", ignoreCase = true) -> "720p"
    resolution.isNotBlank() -> resolution
    else -> "HD"
}
```

---

## 2. Le mode prévisualisation ExoPlayer et la fuite de métriques

### ⚠️ Le Piège
Pour permettre un zapping ultra-rapide et sans gel dans la vue divisée (`TvSplitChannelView`), `PlayerEngine.setPreviewMode(true)` restreint le `DefaultTrackSelector` :
* Résolution plafonnée à 1280×720
* Débit plafonné à 2.5 Mbps
* `setForceHighestSupportedBitrate(false)`

Si l'utilisateur lance ensuite un film, une série ou passe en plein écran sans déverrouiller explicitement le sélecteur de pistes, ExoPlayer conservera cette contrainte interne et downscalera silencieusement le flux en 720p !

De plus, lors d'un appel à `playStream(url, ...)`, ExoPlayer met quelques centaines de millisecondes à négocier le flux. Si vous ne purgez pas les StateFlow de pistes :
* `_availableVideoTracks`
* `_playbackStats`
Le nouvel écran continuera d'afficher les pistes et la résolution du flux précédent pendant le chargement.

### ✅ La Solution Robuste
1. Dans `PlayerEngine.playStream(...)`, purger systématiquement l'état précédent :
   ```kotlin
   _availableVideoTracks.value = emptyList()
   _availableAudioTracks.value = emptyList()
   _availableSubtitleTracks.value = emptyList()
   _playbackStats.value = PlaybackStats()
   ```
2. Dans `PlayerEngine.setPreviewMode(false)`, lever toutes les contraintes et purger les overrides vidéo :
   ```kotlin
   builder
       .clearVideoSizeConstraints()
       .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
       .setMaxVideoBitrate(Int.MAX_VALUE)
       .setForceHighestSupportedBitrate(true)
       .setExceedVideoConstraintsIfNecessary(true)
   ```
3. Dans `NoosPlayerScreen.kt`, forcer `playerEngine.setPreviewMode(false)` dès le `LaunchedEffect(Unit)` de démarrage.

---

## 3. L'ergonomie de l'OSD et des boutons d'options sur Android TV

### ⚠️ Le Piège
Sur une interface TV pilotée à la télécommande :
* Placer les boutons d'options (Infos, Vitesse, Qualité, Audio & Subs, Format) dans la barre supérieure de l'écran est une erreur d'ergonomie majeure.
* L'utilisateur se trouve par défaut sur le bouton central Play/Pause. Pour aller en haut, il doit remonter à travers la seekbar, risquant de faire des sauts de lecture involontaires.
* Un auto-masquage (`isOsdVisible = false`) actif pendant qu'une boîte de dialogue de réglages est ouverte ferme brutalement l'OSD sous les yeux de l'utilisateur.

### ✅ La Solution Robuste
1. **Contrôles OSD inférieurs** : positionner la rangée de boutons de réglages directement sous la ligne Play/Pause. Une seule pression sur la touche **Bas** de la télécommande donne accès aux options.
2. **Barre supérieure épurée** : réserver le haut au bouton retour, titre, badges d'état et horloge.
3. **Maintien de l'OSD** : lier le timer d'auto-masquage à l'état du dialogue :
   ```kotlin
   LaunchedEffect(isOsdVisible, showSettingsDialog) {
       if (isOsdVisible && !showSettingsDialog) {
           delay(100)
           runCatching { playPauseFocusRequester.requestFocus() }
           delay(4400)
           if (!showSettingsDialog) {
               isOsdVisible = false
           }
       }
   }
   ```

---

## 4. La gestion du focus D-Pad dans les catalogues et modales TV

### ⚠️ Le Piège
1. **Transition Sidebar ➔ Catalogue** : quand l'utilisateur est positionné sur l'onglet "Films" ou "Séries" dans la barre latérale gauche et clique sur la flèche Droite, le focus ne doit **JAMAIS** arriver sur la rangée des chips de catégories supérieures. L'utilisateur veut naviguer dans le catalogue ! S'il veut filtrer les catégories, il doit faire la démarche explicite de monter (Flèche Haut).
2. **Modales de détails** : sur TV, la croix de fermeture [X] en haut à droite doit avoir un FocusRequester explicite. Si l'utilisateur est sur la liste des épisodes ou les boutons d'action et appuie sur Haut, il doit atterrir directement sur la croix pour pouvoir fermer la modale sans être bloqué.
3. **Gel de l'arrière-plan** : quand une modale s'ouvre, elle doit capturer l'ensemble des key events et désactiver le focus des cartes sous-jacentes.

---

## 5. Les sons de navigation d'interface et le SoundPool sur TV

### ⚠️ Le Piège
1. `AudioManager.playSoundEffect(SoundEffectConstants.CLICK)` est **désactivé par défaut** sur de nombreuses box Android TV grand public (Xiaomi Mi Box, Chromecast avec Google TV) dans les réglages système (`Paramètres > Préférences relatives à l'appareil > Son > Sons du système = Désactivé`).
2. S'appuyer sur l'audio système produit un silence complet chez la majorité des utilisateurs.
3. Jouer un son non limité en cadence lors d'un appui prolongé sur une touche D-Pad crée un son strident ou saturé (*glitch audio*).

### ✅ La Solution Robuste
1. Utiliser un `SoundPool` dédié avec attributs sonores d'assistance :
   ```kotlin
   val audioAttributes = AudioAttributes.Builder()
       .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
       .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
       .build()
   val soundPool = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(audioAttributes).build()
   ```
2. Synthétiser des fichiers WAV PCM 44.1 kHz 16-bit courts et feutrés (~24 ms pour la navigation, ~42 ms pour la validation).
3. Intégrer un limiteur de cadence strict :
   ```kotlin
   private const val FOCUS_RATE_LIMIT_MS = 45L

   fun playFocus() {
       val now = SystemClock.uptimeMillis()
       if (now - lastFocusPlayTimeMs < FOCUS_RATE_LIMIT_MS) return
       lastFocusPlayTimeMs = now
       // Lecture du son
   }
   ```
4. Intercepter les touches dans `MainActivity.dispatchKeyEvent(event)` pour couvrir uniformément l'ensemble de l'application sans modifier chaque Composable individuellement.

---

## 6. Le chiffrement matériel AES-256 et la sécurité des identifiants

### ⚠️ Le Piège
Les identifiants IPTV (URL de serveur, utilisateur, mot de passe) ne doivent **JAMAIS** être écrits en texte brut dans les SharedPreferences ou affichés intégralement dans les logs.
Certaines box Android TV bas de gamme ne supportent pas `MasterKeys.AES256_GCM_SPEC` si leur Android KeyStore est altéré.

### ✅ La Solution Robuste
* Centraliser tout chiffrement dans [`CryptoManager`](file:///home/chomiam/Projets/noostv/app/src/main/java/io/noostv/core/security/CryptoManager.kt).
* Assurer un fallback sécurisé avec salt matériel et gestion d'erreurs silencieuse sans crash d'application.
* Dans les interfaces utilisateurs (Paramètres, historique de comptes), toujours masquer l'URL et les mots de passe (`http://serveur.com/*****` et `••••••••`).

---

## 7. La mémoire graphique (Coil & GPU Mali/Amlogic)

### ⚠️ Le Piège
Les catalogues IPTV contiennent des dizaines de milliers de films et séries.
Charger des images non redimensionnées en ARGB_8888 sur des chipsets Mali-G31 / Amlogic S905 entraîne immédiatement des erreurs `OutOfMemory` (OOM) ou des chutes massives de framerate lors du défilement.

### ✅ La Solution Robuste
* Plafonner le cache mémoire Coil à **100 Mo max**.
* Appliquer des dimensions maximales de rendu sur les posters (`size(width, height)` ou `precision(Precision.INEXACT)`).
* Toujours activer la pagination intégrale des catalogues VOD (50 à 100 éléments par page) plutôt que de charger 39 000 éléments dans un seul `LazyVerticalGrid`.

---

## 8. Optimisations Performances IPTV à Grande Échelle (28 000+ Chaînes & VOD)

### ⚠️ Les Pièges
1. **`response.body?.string()` avec Gson** : Sur un fournisseur avec 28 000 chaînes, charger tout le corps JSON dans une `String` alloue 30 à 60 Mo de caractères UTF-16, puis des centaines de milliers d'objets `Map.Node`, déclenchant des pauses GC de 1 à 3 secondes.
2. **Scan linéaire EPG $O(N)$** : Dans un `LazyColumn`, appeler `epgPrograms.firstOrNull { it.channelId == id }` exécute 28 000 itérations par chaîne visible lors du défilement.
3. **`SubcomposeAsyncImage` avec animations continues** : Avoir 24 `LinearProgressIndicator` animés indéfiniment dans les cartes de la grille surcharge le Choreographer Compose.

### ✅ La Solution Robuste
1. **Streaming JSON OkHttp & Gson** : Toujours utiliser `response.body?.charStream().use { reader -> gson.fromJson(reader, type) }`. Le flux est lu par blocs de 8 Ko directement depuis le socket réseau.
2. **Indexation EPG en mémoire $O(1)$** : Grouper les programmes par `channelId` dans un `HashMap` dès la réception. La recherche devient un accès instantané en $O(1)$.
3. **`AsyncImage` + `RGB_565` + Downsampling** :
   * Configurer `bitmapConfig(Bitmap.Config.RGB_565)` dans `NoosApplication` (divise par 2 la RAM des bitmaps sans perte visible sur TV).
   * Spécifier `.size(width = 300, height = 450)` dans `ImageRequest.Builder`.
   * Fournir systématiquement `key = { it.id }` et `contentType` dans les `LazyColumn` et `LazyVerticalGrid`.
4. **`android:largeHeap="true"`** : Impératif dans `AndroidManifest.xml` pour les box Android TV dotées de 1.5 Go / 2 Go de RAM afin de supporter de volumineux catalogues IPTV.

---

## 9. Persistance des Favoris & Double Couche de Sauvegarde (SharedPreferences + Disque)

### ⚠️ Les Pièges
1. **Écriture asynchrone `apply()` perdue lors des kills / mises à jour** : `SharedPreferences.apply()` stocke d'abord en mémoire RAM et planifie une écriture différée sur disque. Si l'application est fermée immédiatement, killée par l'OS ou écrasée par une mise à jour d'APK, l'écriture différée peut être abandonnée.
2. **`logout()` avec `prefs.edit().clear()`** : Appeler `.clear()` effaçait `profiles_json` et l'ensemble des favoris de chaînes, films et séries lors de la déconnexion ou du changement de compte IPTV.
3. **Absence de sauvegarde miroir privée** : Si les SharedPreferences sont altérées lors d'une mise à jour du système Android ou d'un paquet corrompu, toutes les données étaient irrémédiablement perdues.

### ✅ La Solution Robuste
* **Commit synchrone** : Toujours utiliser `prefs.edit().putString(...).commit()` pour la persistance des profils et favoris afin de garantir l'écriture physique immédiate sur disque.
* **Fichier miroir atomique sur stockage interne privé** :
  À chaque modification des profils ou favoris, écrire une copie miroir dans `context.filesDir/noostv_profiles_backup.json` (avec écriture dans un `.tmp` puis renommage atomique).
  Au démarrage (`getProfiles()`), si SharedPreferences est vide ou corrompu, restaurer automatiquement depuis ce fichier de sauvegarde.
* **`logout()` ciblé** : `logout()` ne doit supprimer que les identifiants actifs (`is_logged_in = false`, `server_url`, `username`, `password`) et ne doit **JAMAIS** supprimer `profiles_json` ou `saved_accounts`.
* **Reactivité Compose** : Les collections de favoris doivent être exposées ou observées sous forme d'états Compose (`mutableStateOf`) afin que tout ajout ou retrait de favori se répercute instantanément à l'écran sans dépendre d'une navigation externe.

---

## 10. Mises à Jour OTA Publiques GitHub (S3 Redirect 400 & Absence de Token)

### ⚠️ Les Pièges
1. **Rejet HTTP 400 par AWS S3 sur redirection** :
   Lorsqu'un asset de release GitHub est téléchargé via son URL publique `browser_download_url` (`https://github.com/Owner/Repo/releases/download/...`), GitHub répond par une redirection HTTP 302 vers un bucket Amazon S3 de stockage d'artefacts.
   Si la requête OkHttp d'origine contenait un en-tête `Authorization: Bearer <token>`, OkHttp transmet cet en-tête à AWS S3 lors de la redirection. Or, AWS S3 rejette formellement toute requête avec signature invalide/inattendue en renvoyant une erreur **HTTP 400 Bad Request** !
2. **Exigence de jeton inutile sur les dépôts publics** :
   Demander un Personal Access Token GitHub aux utilisateurs finaux dans l'interface des paramètres est inutile et anxiogène pour un projet open-source public. L'API Releases publique de GitHub (`https://api.github.com/repos/Owner/Repo/releases`) est accessible directement et sans authentification.

### ✅ La Solution Robuste
* Supprimer tout champ de saisie de token GitHub de l'interface utilisateur.
* Dans [`UpdateManager`](file:///home/chomiam/Projets/noostv/app/src/main/java/io/noostv/core/update/UpdateManager.kt) :
  * Privilégier `browser_download_url` pour le téléchargement public direct de l'APK.
  * Ne transmettre l'en-tête `Authorization` que si l'URL pointe spécifiquement vers `api.github.com` et si un token a été explicitement fourni :
    ```kotlin
    if (downloadUrl.contains("api.github.com") && token.isNotBlank()) {
        reqBuilder.header("Authorization", "Bearer $token")
    }
    ```
  * Cela permet un téléchargement OTA 100% public, ultra-rapide et sans aucune friction utilisateur.

---

## 11. Sécurité Applicative, Défense Anti-IA et Durcissement Android

### ⚠️ Les Pièges
1. **`android:allowBackup="true"`** : Permet l'extraction physique complète des SharedPreferences, profils, bases de données et clés privées via `adb backup` dès qu'un câble USB ou un débogage réseau est activé.
2. **`isMinifyEnabled = false` en Release** : Laisse l'APK 100% lisible pour les outils de décompilation automatisée par IA (JADX, Ghidra), exposant l'architecture interne, les formats d'API, les modèles de données et les routines de sécurité.
3. **`android:usesCleartextTraffic="true"` global sans restriction** : Permet à un attaquant en Wi-Fi partagé de réaliser un Man-in-the-Middle (MitM) sur les vérifications de versions OTA, les métadonnées TMDb et les requêtes applicatives.
4. **Blocage aveugle du HTTP clair** : Si `cleartextTrafficPermitted="false"` est appliqué à l'ensemble de l'application sans discernement, de nombreux flux IPTV légitimes (`.ts`, `.m3u8` servis sur les ports 80/8080/8000 par des fournisseurs historiques) refuseront catégoriquement de démarrer dans ExoPlayer.
5. **Fuite des identifiants dans les traces d'erreurs et logs** : Les URLs Xtream Codes contiennent par défaut `?username=...&password=...` ou `/live/user/pass/id.ts`. Toute exception réseau non filtrée imprimée dans Logcat ou affichée à l'écran expose le mot de passe en clair.
6. **Absence de contrôle sur l'APK téléchargé en OTA** : Lancer l'Intent d'installation sur n'importe quel fichier sans vérifier son `packageName` ni sa structure permettrait l'installation d'une application tierce malveillante en cas d'empoisonnement DNS ou d'interception réseau.

### ✅ La Solution Robuste
1. **Défense en oignon et verrouillage système** :
   * Déclarer formellement `android:allowBackup="false"` dans `AndroidManifest.xml`.
   * Définir un [`res/xml/network_security_config.xml`](file:///home/chomiam/Projets/noostv/app/src/main/res/xml/network_security_config.xml) qui impose du HTTPS strict sur les domaines sensibles (`api.github.com`, `themoviedb.org`), tout en conservant une `base-config` autorisant le HTTP pour la rétro-compatibilité indispensable des flux IPTV bruts.
2. **Obfuscation et Shrinking R8 intégrale** :
   * Activer `isMinifyEnabled = true` et `isShrinkResources = true` dans `buildTypes.release`.
   * Verrouiller les modèles Gson (`io.noostv.data.model.**`), Jetpack Compose et Media3 dans `proguard-rules.pro`, tout en laissant R8 obfusquer agressivement `core.security`, `core.storage` et `data.api`.
   * Supprimer les attributs de debug et renommer les sources (`-renamesourcefileattribute SourceFile`).
   * Réduction directe de 59% de l'empreinte binaire de l'APK (de 22 Mo à 9 Mo).
3. **Module d'intégrité & Détection Anti-Frida ([`AppIntegrityChecker`](file:///home/chomiam/Projets/noostv/app/src/main/java/io/noostv/core/security/AppIntegrityChecker.kt))** :
   * Détecter les bibliothèques d'instrumentation dynamique en mémoire (`/proc/self/maps` contenant `frida-agent.so`, `gadget.so`, `xposed`).
   * Sonder le port TCP local 27042 (Frida server) avec un timeout ultracourt (< 30ms).
   * Détecter les débogueurs actifs en production via `Debug.isDebuggerConnected()`.
   * Adopter une stratégie non bloquante sur Android TV : notifier et dégrader la persistance plutôt que de crasher sauvagement sur des firmwares de box TV constructeurs exotiques.
4. **Nettoyage automatique des URLs ([`XtreamCodesClient.sanitizeUrl`](file:///home/chomiam/Projets/noostv/app/src/main/java/io/noostv/data/api/XtreamCodesClient.kt))** :
   * Masquer systématiquement les mots de passe et tokens de streaming : `password=***` et `/(live|movie|series)/user/***/id.ext`.
   * Standardiser le `User-Agent` (`NoosTV/1.2.6 (Android TV; ExoPlayer)`) et forcer `ConnectionSpec.MODERN_TLS`.
5. **Validation cryptographique et structurelle de l'OTA ([`UpdateManager.installApk`](file:///home/chomiam/Projets/noostv/app/src/main/java/io/noostv/core/update/UpdateManager.kt))** :
   * Avant d'invoquer `Intent.ACTION_VIEW`, analyser le binaire avec `PackageManager.getPackageArchiveInfo()`.
   * Rejeter et détruire le fichier immédiatement si `archiveInfo == null`, si la taille est inférieure à 100 Ko ou si `archiveInfo.packageName != context.packageName`.
