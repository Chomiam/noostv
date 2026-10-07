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
