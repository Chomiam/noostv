package io.noostv.core.localization

/**
 * Interface définissant l'ensemble des chaînes localisées de l'application NoosTV.
 */
interface AppStrings {
    // Navigation
    val navLiveTv: String
    val navMovies: String
    val navSeries: String
    val navEpg: String
    val navFavorites: String
    val navFilters: String
    val navSettings: String

    // Settings & Updates
    val settingsTitle: String
    val settingsSubtitle: String
    val githubChannelTitle: String
    val stableBranchTitle: String
    val stableBranchDesc: String
    val testingBranchTitle: String
    val testingBranchDesc: String
    val checkUpdates: String
    val checkAgain: String
    val checkingUpdates: String
    val noUpdateAvailable: String
    val updateAvailable: String
    val downloadAndInstall: String
    val installUpdate: String
    val downloadingUpdate: String
    val installerReady: String
    val reopenInstaller: String
    val retry: String
    val activeBadge: String
    val channelTestingBadge: String
    val channelStableBadge: String
    val publishedOn: String
    val size: String

    // Language Section
    val languageSectionTitle: String
    val languageSectionSubtitle: String
    val selectLanguage: String

    // Subscription & System
    val subscriptionSectionTitle: String
    val iptvSession: String
    val server: String
    val username: String
    val logout: String
    val systemDiag: String
    val device: String
    val screen: String
    val system: String
    val network: String
    val version: String

    // Profiles
    val profilesTitle: String
    val manageProfiles: String
    val selectProfile: String
    val addProfile: String
    val editProfile: String
    val profileName: String
    val chooseAvatar: String
    val chooseColor: String
    val save: String
    val cancel: String
    val delete: String
    val cannotDeleteLastProfile: String
    val activeProfile: String

    // Favorites
    val favoritesTitle: String
    val favoritesSubtitle: String
    val noFavorites: String
    val noFavoritesHint: String
    val pinnedChannels: String
    val pinnedMovies: String
    val pinnedSeries: String
    val removeFromFavorites: String
    val addToFavorites: String

    // Filters
    val filtersTitle: String
    val filtersSubtitle: String
    val movieCategories: String
    val seriesCategories: String
    val hiddenCount: String
    val allVisible: String
    val hideCategory: String
    val showCategory: String

    // Search & Player & Dialogs
    val searchPlaceholder: String
    val searchTitle: String
    val noResults: String
    val logoutConfirmTitle: String
    val logoutConfirmMsg: String
    val confirm: String
    val synopsis: String
    val episode: String
    val season: String
    val audioTracks: String
    val subtitles: String
    val upgradePremium: String
    val exitConfirmTitle: String
    val exitConfirmMsg: String
    val exitQuitButton: String
    val pressBackAgainToExit: String

    companion object {
        fun get(language: AppLanguage): AppStrings = when (language) {
            AppLanguage.FRENCH -> FrenchStrings
            AppLanguage.ENGLISH -> EnglishStrings
            AppLanguage.SPANISH -> SpanishStrings
            AppLanguage.GERMAN -> GermanStrings
            AppLanguage.ITALIAN -> ItalianStrings
            AppLanguage.ARABIC -> ArabicStrings
            AppLanguage.PORTUGUESE -> PortugueseStrings
        }
    }
}

object FrenchStrings : AppStrings {
    override val navLiveTv = "Direct TV"
    override val navMovies = "Films"
    override val navSeries = "Séries"
    override val navEpg = "Guide TV"
    override val navFavorites = "Favoris"
    override val navFilters = "Filtres"
    override val navSettings = "Paramètres"

    override val settingsTitle = "Paramètres & Mises à Jour"
    override val settingsSubtitle = "Gestion des canaux GitHub, langue et diagnostic système"
    override val githubChannelTitle = "CANAL DE DISTRIBUTION GITHUB"
    override val stableBranchTitle = "Branche Stable (Recommandé)"
    override val stableBranchDesc = "Versions officielles éprouvées, stabilité maximale pour un usage quotidien."
    override val testingBranchTitle = "Branche Testing (Expérimental)"
    override val testingBranchDesc = "Mises à jour préliminaires et nouvelles fonctionnalités en avant-première."
    override val checkUpdates = "Vérifier les mises à jour"
    override val checkAgain = "Vérifier à nouveau"
    override val checkingUpdates = "Recherche de nouvelles versions..."
    override val noUpdateAvailable = "Aucune mise à jour disponible. Vous utilisez la version la plus récente."
    override val updateAvailable = "Mise à jour disponible :"
    override val downloadAndInstall = "Télécharger & Installer"
    override val installUpdate = "Installer la mise à jour"
    override val downloadingUpdate = "Téléchargement de la mise à jour..."
    override val installerReady = "APK prêt ! L'installateur Android a été ouvert."
    override val reopenInstaller = "Réouvrir l'installateur"
    override val retry = "Réessayer"
    override val activeBadge = "ACTIF"
    override val channelTestingBadge = "Canal testing"
    override val channelStableBadge = "Canal stable"
    override val publishedOn = "Publiée le"
    override val size = "Taille :"

    override val languageSectionTitle = "LANGUE DE L'INTERFACE"
    override val languageSectionSubtitle = "Personnaliser la langue d'affichage de NoosTV"
    override val selectLanguage = "Choisir la langue"

    override val subscriptionSectionTitle = "ABONNEMENT IPTV & MATÉRIEL"
    override val iptvSession = "Session IPTV"
    override val server = "Serveur :"
    override val username = "Identifiant :"
    override val logout = "Se déconnecter"
    override val systemDiag = "Diagnostic Système"
    override val device = "Appareil :"
    override val screen = "Écran :"
    override val system = "Système :"
    override val network = "Réseau :"
    override val version = "Version"

    override val profilesTitle = "Profils NoosTV"
    override val manageProfiles = "Gérer les profils"
    override val selectProfile = "Choisir un profil"
    override val addProfile = "Ajouter un profil"
    override val editProfile = "Éditer le profil"
    override val profileName = "Nom du profil"
    override val chooseAvatar = "Choisir un avatar"
    override val chooseColor = "Choisir une couleur"
    override val save = "Enregistrer"
    override val cancel = "Annuler"
    override val delete = "Supprimer"
    override val cannotDeleteLastProfile = "Impossible de supprimer le dernier profil."
    override val activeProfile = "Profil actif"

    override val favoritesTitle = "Mes Programmes Favoris"
    override val favoritesSubtitle = "Chaînes, films et séries épinglés pour le profil"
    override val noFavorites = "Aucun favori pour le moment"
    override val noFavoritesHint = "Épinglez vos programmes favoris pour les retrouver facilement ici."
    override val pinnedChannels = "Chaînes en direct"
    override val pinnedMovies = "Films"
    override val pinnedSeries = "Séries"
    override val removeFromFavorites = "Retirer des favoris"
    override val addToFavorites = "Ajouter aux favoris"

    override val filtersTitle = "Filtres de Catégories"
    override val filtersSubtitle = "Personnalisez les catégories visibles pour le profil"
    override val movieCategories = "Catégories Films"
    override val seriesCategories = "Catégories Séries"
    override val hiddenCount = "masquée(s)"
    override val allVisible = "Toutes visibles"
    override val hideCategory = "Masquer"
    override val showCategory = "Afficher"

    override val searchPlaceholder = "Rechercher une chaîne, un film, une série..."
    override val searchTitle = "Recherche Rapide"
    override val noResults = "Aucun résultat trouvé"
    override val logoutConfirmTitle = "Se déconnecter ?"
    override val logoutConfirmMsg = "Êtes-vous sûr de vouloir vous déconnecter de votre compte IPTV ?"
    override val confirm = "Confirmer"
    override val synopsis = "Synopsis"
    override val episode = "Épisode"
    override val season = "Saison"
    override val audioTracks = "Pistes audio"
    override val subtitles = "Sous-titres"
    override val upgradePremium = "Passer à NoosTV Premium"
    override val exitConfirmTitle = "Quitter NoosTV ?"
    override val exitConfirmMsg = "Voulez-vous vraiment fermer complètement l'application ?"
    override val exitQuitButton = "Quitter"
    override val pressBackAgainToExit = "Appuyez de nouveau sur Retour pour quitter"
}

object EnglishStrings : AppStrings {
    override val navLiveTv = "Live TV"
    override val navMovies = "Movies"
    override val navSeries = "Series"
    override val navEpg = "TV Guide"
    override val navFavorites = "Favorites"
    override val navFilters = "Filters"
    override val navSettings = "Settings"

    override val settingsTitle = "Settings & Updates"
    override val settingsSubtitle = "GitHub release channels, language and system diagnostics"
    override val githubChannelTitle = "GITHUB RELEASE CHANNEL"
    override val stableBranchTitle = "Stable Branch (Recommended)"
    override val stableBranchDesc = "Proven official releases, maximum stability for everyday use."
    override val testingBranchTitle = "Testing Branch (Experimental)"
    override val testingBranchDesc = "Early preview updates and latest features ahead of release."
    override val checkUpdates = "Check for updates"
    override val checkAgain = "Check again"
    override val checkingUpdates = "Checking for new versions..."
    override val noUpdateAvailable = "No update available. You are using the latest version."
    override val updateAvailable = "Update available:"
    override val downloadAndInstall = "Download & Install"
    override val installUpdate = "Install update"
    override val downloadingUpdate = "Downloading update..."
    override val installerReady = "APK ready! Android installer has been opened."
    override val reopenInstaller = "Reopen installer"
    override val retry = "Retry"
    override val activeBadge = "ACTIVE"
    override val channelTestingBadge = "Testing channel"
    override val channelStableBadge = "Stable channel"
    override val publishedOn = "Published on"
    override val size = "Size:"

    override val languageSectionTitle = "INTERFACE LANGUAGE"
    override val languageSectionSubtitle = "Customize NoosTV display language"
    override val selectLanguage = "Select language"

    override val subscriptionSectionTitle = "IPTV SUBSCRIPTION & HARDWARE"
    override val iptvSession = "IPTV Session"
    override val server = "Server:"
    override val username = "Username:"
    override val logout = "Log out"
    override val systemDiag = "System Diagnostics"
    override val device = "Device:"
    override val screen = "Screen:"
    override val system = "System:"
    override val network = "Network:"
    override val version = "Version"

    override val profilesTitle = "NoosTV Profiles"
    override val manageProfiles = "Manage profiles"
    override val selectProfile = "Select a profile"
    override val addProfile = "Add a profile"
    override val editProfile = "Edit profile"
    override val profileName = "Profile name"
    override val chooseAvatar = "Choose an avatar"
    override val chooseColor = "Choose a color"
    override val save = "Save"
    override val cancel = "Cancel"
    override val delete = "Delete"
    override val cannotDeleteLastProfile = "Cannot delete the last profile."
    override val activeProfile = "Active profile"

    override val favoritesTitle = "My Favorite Programs"
    override val favoritesSubtitle = "Pinned channels, movies and series for this profile"
    override val noFavorites = "No favorites yet"
    override val noFavoritesHint = "Pin your favorite programs to easily find them here."
    override val pinnedChannels = "Live Channels"
    override val pinnedMovies = "Movies"
    override val pinnedSeries = "Series"
    override val removeFromFavorites = "Remove from favorites"
    override val addToFavorites = "Add to favorites"

    override val filtersTitle = "Category Filters"
    override val filtersSubtitle = "Customize visible categories for this profile"
    override val movieCategories = "Movie Categories"
    override val seriesCategories = "Series Categories"
    override val hiddenCount = "hidden"
    override val allVisible = "All visible"
    override val hideCategory = "Hide"
    override val showCategory = "Show"

    override val searchPlaceholder = "Search channel, movie, series..."
    override val searchTitle = "Quick Search"
    override val noResults = "No results found"
    override val logoutConfirmTitle = "Log out?"
    override val logoutConfirmMsg = "Are you sure you want to log out of your IPTV account?"
    override val confirm = "Confirm"
    override val synopsis = "Synopsis"
    override val episode = "Episode"
    override val season = "Season"
    override val audioTracks = "Audio tracks"
    override val subtitles = "Subtitles"
    override val upgradePremium = "Upgrade to NoosTV Premium"
    override val exitConfirmTitle = "Exit NoosTV?"
    override val exitConfirmMsg = "Are you sure you want to completely close the application?"
    override val exitQuitButton = "Exit"
    override val pressBackAgainToExit = "Press Back again to exit"
}

object SpanishStrings : AppStrings {
    override val navLiveTv = "TV en vivo"
    override val navMovies = "Películas"
    override val navSeries = "Series"
    override val navEpg = "Guía TV"
    override val navFavorites = "Favoritos"
    override val navFilters = "Filtros"
    override val navSettings = "Ajustes"

    override val settingsTitle = "Ajustes y Actualizaciones"
    override val settingsSubtitle = "Canales GitHub, idioma y diagnóstico del sistema"
    override val githubChannelTitle = "CANAL DE DISTRIBUCIÓN GITHUB"
    override val stableBranchTitle = "Rama Estable (Recomendado)"
    override val stableBranchDesc = "Versiones oficiales probadas, máxima estabilidad para uso diario."
    override val testingBranchTitle = "Rama Testing (Experimental)"
    override val testingBranchDesc = "Actualizaciones preliminares y nuevas funciones antes del lanzamiento."
    override val checkUpdates = "Buscar actualizaciones"
    override val checkAgain = "Buscar de nuevo"
    override val checkingUpdates = "Buscando nuevas versiones..."
    override val noUpdateAvailable = "No hay actualizaciones. Estás usando la versión más reciente."
    override val updateAvailable = "Actualización disponible:"
    override val downloadAndInstall = "Descargar e Instalar"
    override val installUpdate = "Instalar actualización"
    override val downloadingUpdate = "Descargando actualización..."
    override val installerReady = "¡APK listo! Se abrió el instalador de Android."
    override val reopenInstaller = "Reabrir instalador"
    override val retry = "Reintentar"
    override val activeBadge = "ACTIVO"
    override val channelTestingBadge = "Canal testing"
    override val channelStableBadge = "Canal estable"
    override val publishedOn = "Publicado el"
    override val size = "Tamaño:"

    override val languageSectionTitle = "IDIOMA DE LA INTERFAZ"
    override val languageSectionSubtitle = "Personalizar el idioma de visualización de NoosTV"
    override val selectLanguage = "Seleccionar idioma"

    override val subscriptionSectionTitle = "SUSCRIPCIÓN IPTV Y HARDWARE"
    override val iptvSession = "Sesión IPTV"
    override val server = "Servidor:"
    override val username = "Usuario:"
    override val logout = "Cerrar sesión"
    override val systemDiag = "Diagnóstico del Sistema"
    override val device = "Dispositivo:"
    override val screen = "Pantalla:"
    override val system = "Sistema:"
    override val network = "Red:"
    override val version = "Versión"

    override val profilesTitle = "Perfiles NoosTV"
    override val manageProfiles = "Administrar perfiles"
    override val selectProfile = "Seleccionar un perfil"
    override val addProfile = "Añadir un perfil"
    override val editProfile = "Editar perfil"
    override val profileName = "Nombre del perfil"
    override val chooseAvatar = "Elegir un avatar"
    override val chooseColor = "Elegir un color"
    override val save = "Guardar"
    override val cancel = "Cancelar"
    override val delete = "Eliminar"
    override val cannotDeleteLastProfile = "No se puede eliminar el último perfil."
    override val activeProfile = "Perfil activo"

    override val favoritesTitle = "Mis Programas Favoritos"
    override val favoritesSubtitle = "Canales, películas y series fijados para este perfil"
    override val noFavorites = "No hay favoritos por ahora"
    override val noFavoritesHint = "Fije sus programas favoritos para encontrarlos fácilmente aquí."
    override val pinnedChannels = "Canales en directo"
    override val pinnedMovies = "Películas"
    override val pinnedSeries = "Series"
    override val removeFromFavorites = "Quitar de favoritos"
    override val addToFavorites = "Añadir a favoritos"

    override val filtersTitle = "Filtros de Categorías"
    override val filtersSubtitle = "Personaliza las categorías visibles para este perfil"
    override val movieCategories = "Categorías Películas"
    override val seriesCategories = "Categorías Series"
    override val hiddenCount = "oculta(s)"
    override val allVisible = "Todas visibles"
    override val hideCategory = "Ocultar"
    override val showCategory = "Mostrar"

    override val searchPlaceholder = "Buscar canal, película, serie..."
    override val searchTitle = "Búsqueda Rápida"
    override val noResults = "No se encontraron resultados"
    override val logoutConfirmTitle = "¿Cerrar sesión?"
    override val logoutConfirmMsg = "¿Estás seguro de que deseas cerrar sesión de tu cuenta IPTV?"
    override val confirm = "Confirmar"
    override val synopsis = "Sinopsis"
    override val episode = "Episodio"
    override val season = "Temporada"
    override val audioTracks = "Pistas de audio"
    override val subtitles = "Subtítulos"
    override val upgradePremium = "Pasar a NoosTV Premium"
    override val exitConfirmTitle = "¿Salir de NoosTV?"
    override val exitConfirmMsg = "¿Está seguro de que desea cerrar completamente la aplicación?"
    override val exitQuitButton = "Salir"
    override val pressBackAgainToExit = "Presione Atrás de nuevo para salir"
}

object GermanStrings : AppStrings {
    override val navLiveTv = "Live-TV"
    override val navMovies = "Filme"
    override val navSeries = "Serien"
    override val navEpg = "TV-Programm"
    override val navFavorites = "Favoriten"
    override val navFilters = "Filter"
    override val navSettings = "Einstellungen"

    override val settingsTitle = "Einstellungen & Updates"
    override val settingsSubtitle = "GitHub-Release-Kanäle, Sprache und Systemdiagnose"
    override val githubChannelTitle = "GITHUB-RELEASE-KANAL"
    override val stableBranchTitle = "Stabiler Kanal (Empfohlen)"
    override val stableBranchDesc = "Offizielle bewährte Versionen, maximale Stabilität im Alltag."
    override val testingBranchTitle = "Testing-Kanal (Experimentell)"
    override val testingBranchDesc = "Vorab-Updates und neueste Funktionen vor dem offiziellen Release."
    override val checkUpdates = "Nach Updates suchen"
    override val checkAgain = "Erneut prüfen"
    override val checkingUpdates = "Suche nach Versionen auf GitHub..."
    override val noUpdateAvailable = "Kein Update verfügbar. Sie nutzen die neueste Version."
    override val updateAvailable = "Update verfügbar:"
    override val downloadAndInstall = "Herunterladen & Installieren"
    override val installUpdate = "Update installieren"
    override val downloadingUpdate = "Update wird heruntergeladen..."
    override val installerReady = "APK bereit! Android-Installationsprogramm geöffnet."
    override val reopenInstaller = "Installer erneut öffnen"
    override val retry = "Wiederholen"
    override val activeBadge = "AKTIV"
    override val channelTestingBadge = "Testing-Kanal"
    override val channelStableBadge = "Stabiler Kanal"
    override val publishedOn = "Veröffentlicht am"
    override val size = "Größe:"

    override val languageSectionTitle = "OBERFLÄCHENSPRACHE"
    override val languageSectionSubtitle = "NoosTV-Anzeigesprache anpassen"
    override val selectLanguage = "Sprache auswählen"

    override val subscriptionSectionTitle = "IPTV-ABONNEMENT & HARDWARE"
    override val iptvSession = "IPTV-Sitzung"
    override val server = "Server:"
    override val username = "Benutzername:"
    override val logout = "Abmelden"
    override val systemDiag = "Systemdiagnose"
    override val device = "Gerät:"
    override val screen = "Bildschirm:"
    override val system = "System:"
    override val network = "Netzwerk:"
    override val version = "Version"

    override val profilesTitle = "NoosTV-Profile"
    override val manageProfiles = "Profile verwalten"
    override val selectProfile = "Profil auswählen"
    override val addProfile = "Profil hinzufügen"
    override val editProfile = "Profil bearbeiten"
    override val profileName = "Profilname"
    override val chooseAvatar = "Avatar wählen"
    override val chooseColor = "Farbe wählen"
    override val save = "Speichern"
    override val cancel = "Abbrechen"
    override val delete = "Löschen"
    override val cannotDeleteLastProfile = "Das letzte Profil kann nicht gelöscht werden."
    override val activeProfile = "Aktives Profil"

    override val favoritesTitle = "Meine Favoriten"
    override val favoritesSubtitle = "Angeheftete Kanäle, Filme und Serien für dieses Profil"
    override val noFavorites = "Noch keine Favoriten"
    override val noFavoritesHint = "Heften Sie Ihre Lieblingssendungen an, um sie hier wiederzufinden."
    override val pinnedChannels = "Live-Sender"
    override val pinnedMovies = "Filme"
    override val pinnedSeries = "Serien"
    override val removeFromFavorites = "Aus Favoriten entfernen"
    override val addToFavorites = "Zu Favoriten hinzufügen"

    override val filtersTitle = "Kategoriefilter"
    override val filtersSubtitle = "Sichtbare Kategorien für dieses Profil anpassen"
    override val movieCategories = "Filmkategorien"
    override val seriesCategories = "Serienkategorien"
    override val hiddenCount = "ausgeblendet"
    override val allVisible = "Alle sichtbar"
    override val hideCategory = "Ausblenden"
    override val showCategory = "Einblenden"

    override val searchPlaceholder = "Kanal, Film, Serie suchen..."
    override val searchTitle = "Schnellsuche"
    override val noResults = "Keine Ergebnisse gefunden"
    override val logoutConfirmTitle = "Abmelden?"
    override val logoutConfirmMsg = "Möchten Sie sich wirklich von Ihrem IPTV-Konto abmelden?"
    override val confirm = "Bestätigen"
    override val synopsis = "Handlung"
    override val episode = "Folge"
    override val season = "Staffel"
    override val audioTracks = "Tonspuren"
    override val subtitles = "Untertitel"
    override val upgradePremium = "Auf NoosTV Premium upgraden"
    override val exitConfirmTitle = "NoosTV beenden?"
    override val exitConfirmMsg = "Möchten Sie die Anwendung wirklich vollständig beenden?"
    override val exitQuitButton = "Beenden"
    override val pressBackAgainToExit = "Drücken Sie erneut Zurück zum Beenden"
}

object ItalianStrings : AppStrings {
    override val navLiveTv = "TV in diretta"
    override val navMovies = "Film"
    override val navSeries = "Serie TV"
    override val navEpg = "Guida TV"
    override val navFavorites = "Preferiti"
    override val navFilters = "Filtri"
    override val navSettings = "Impostazioni"

    override val settingsTitle = "Impostazioni e Aggiornamenti"
    override val settingsSubtitle = "Canali GitHub, lingua e diagnostica di sistema"
    override val githubChannelTitle = "CANALE DI DISTRIBUZIONE GITHUB"
    override val stableBranchTitle = "Canale Stabile (Consigliato)"
    override val stableBranchDesc = "Versioni ufficiali collaudate, massima stabilità per l'uso quotidiano."
    override val testingBranchTitle = "Canale Testing (Sperimentale)"
    override val testingBranchDesc = "Anteprime e nuove funzionalità prima del rilascio ufficiale."
    override val checkUpdates = "Verifica aggiornamenti"
    override val checkAgain = "Verifica di nuovo"
    override val checkingUpdates = "Ricerca nuove versioni su GitHub..."
    override val noUpdateAvailable = "Nessun aggiornamento disponibile. Stai usando la versione più recente."
    override val updateAvailable = "Aggiornamento disponibile:"
    override val downloadAndInstall = "Scarica e Installa"
    override val installUpdate = "Installa aggiornamento"
    override val downloadingUpdate = "Download aggiornamento in corso..."
    override val installerReady = "APK pronto! Il programma di installazione Android è stato aperto."
    override val reopenInstaller = "Riapri installer"
    override val retry = "Riprova"
    override val activeBadge = "ATTIVO"
    override val channelTestingBadge = "Canale testing"
    override val channelStableBadge = "Canale stabile"
    override val publishedOn = "Pubblicata il"
    override val size = "Dimensioni:"

    override val languageSectionTitle = "LINGUA DELL'INTERFACCIA"
    override val languageSectionSubtitle = "Personalizza la lingua di NoosTV"
    override val selectLanguage = "Seleziona lingua"

    override val subscriptionSectionTitle = "ABBONAMENTO IPTV E HARDWARE"
    override val iptvSession = "Sessione IPTV"
    override val server = "Server:"
    override val username = "Nome utente:"
    override val logout = "Disconnetti"
    override val systemDiag = "Diagnostica di Sistema"
    override val device = "Dispositivo:"
    override val screen = "Schermo:"
    override val system = "Sistema:"
    override val network = "Rete:"
    override val version = "Versione"

    override val profilesTitle = "Profili NoosTV"
    override val manageProfiles = "Gestisci profili"
    override val selectProfile = "Seleziona un profilo"
    override val addProfile = "Aggiungi un profilo"
    override val editProfile = "Modifica profilo"
    override val profileName = "Nome del profilo"
    override val chooseAvatar = "Scegli un avatar"
    override val chooseColor = "Scegli un colore"
    override val save = "Salva"
    override val cancel = "Annulla"
    override val delete = "Elimina"
    override val cannotDeleteLastProfile = "Impossibile eliminare l'ultimo profilo."
    override val activeProfile = "Profilo attivo"

    override val favoritesTitle = "I miei programmi preferiti"
    override val favoritesSubtitle = "Canali, film e serie aggiunti ai preferiti"
    override val noFavorites = "Nessun preferito per ora"
    override val noFavoritesHint = "Aggiungi i tuoi programmi preferiti per ritrovarli facilmente qui."
    override val pinnedChannels = "Canali in diretta"
    override val pinnedMovies = "Film"
    override val pinnedSeries = "Serie TV"
    override val removeFromFavorites = "Rimuovi dai preferiti"
    override val addToFavorites = "Aggiungi ai preferiti"

    override val filtersTitle = "Filtri Categorie"
    override val filtersSubtitle = "Personalizza le categorie visibili per questo profilo"
    override val movieCategories = "Categorie Film"
    override val seriesCategories = "Categorie Serie"
    override val hiddenCount = "nascosta/e"
    override val allVisible = "Tutte visibili"
    override val hideCategory = "Nascondi"
    override val showCategory = "Mostra"

    override val searchPlaceholder = "Cerca canale, film, serie..."
    override val searchTitle = "Ricerca Rapida"
    override val noResults = "Nessun risultato trovato"
    override val logoutConfirmTitle = "Disconnettersi?"
    override val logoutConfirmMsg = "Sei sicuro di volerti disconnettere dal tuo account IPTV?"
    override val confirm = "Conferma"
    override val synopsis = "Sinossi"
    override val episode = "Episodio"
    override val season = "Stagione"
    override val audioTracks = "Tracce audio"
    override val subtitles = "Sottotitoli"
    override val upgradePremium = "Passa a NoosTV Premium"
    override val exitConfirmTitle = "Uscire da NoosTV?"
    override val exitConfirmMsg = "Sei sicuro di voler chiudere completamente l'applicazione?"
    override val exitQuitButton = "Esci"
    override val pressBackAgainToExit = "Premi di nuovo Indietro per uscire"
}

object ArabicStrings : AppStrings {
    override val navLiveTv = "البث المباشر"
    override val navMovies = "أفلام"
    override val navSeries = "مسلسلات"
    override val navEpg = "دليل التلفزيون"
    override val navFavorites = "المفضلة"
    override val navFilters = "تصفية"
    override val navSettings = "الإعدادات"

    override val settingsTitle = "الإعدادات والتحديثات"
    override val settingsSubtitle = "إدارة قنوات GitHub واللغة وتشخيص النظام"
    override val githubChannelTitle = "قناة توزيع GITHUB"
    override val stableBranchTitle = "القناة المستقرة (مستحسن)"
    override val stableBranchDesc = "إصدارات رسمية مجربة، أقصى درجات الاستقرار للاستخدام اليومي."
    override val testingBranchTitle = "قناة التجريب (تجريبي)"
    override val testingBranchDesc = "تحديثات أولية وأحدث الميزات قبل الإصدار الرسمي."
    override val checkUpdates = "التحقق من التحديثات"
    override val checkAgain = "تحقق مجدداً"
    override val checkingUpdates = "جاري البحث عن إصدارات جديدة..."
    override val noUpdateAvailable = "لا يوجد أي تحديث متاح. أنت تستخدم أحدث إصدار."
    override val updateAvailable = "تحديث متوفر:"
    override val downloadAndInstall = "تنزيل وتثبيت"
    override val installUpdate = "تثبيت التحديث"
    override val downloadingUpdate = "جاري تنزيل التحديث..."
    override val installerReady = "حزمة APK جاهزة! تم فتح مثبت Android."
    override val reopenInstaller = "إعادة فتح المثبت"
    override val retry = "إعادة المحاولة"
    override val activeBadge = "نشط"
    override val channelTestingBadge = "قناة التجريب"
    override val channelStableBadge = "القناة المستقرة"
    override val publishedOn = "تاريخ النشر"
    override val size = "الحجم:"

    override val languageSectionTitle = "لغة الواجهة"
    override val languageSectionSubtitle = "تخصيص لغة عرض تطبيق NoosTV"
    override val selectLanguage = "اختر اللغة"

    override val subscriptionSectionTitle = "اشتراك IPTV والأجهزة"
    override val iptvSession = "جلسة IPTV"
    override val server = "الخادم:"
    override val username = "اسم المستخدم:"
    override val logout = "تسجيل الخروج"
    override val systemDiag = "تشخيص النظام"
    override val device = "الجهاز:"
    override val screen = "الشاشة:"
    override val system = "النظام:"
    override val network = "الشبكة:"
    override val version = "الإصدار"

    override val profilesTitle = "ملفات NoosTV الشخصية"
    override val manageProfiles = "إدارة الملفات الشخصية"
    override val selectProfile = "اختر ملفاً شخصياً"
    override val addProfile = "إضافة ملف شخصي"
    override val editProfile = "تعديل الملف الشخصي"
    override val profileName = "اسم الملف الشخصي"
    override val chooseAvatar = "اختر صورة شخصية"
    override val chooseColor = "اختر لوناً"
    override val save = "حفظ"
    override val cancel = "إلغاء"
    override val delete = "حذف"
    override val cannotDeleteLastProfile = "لا يمكن حذف آخر ملف شخصي."
    override val activeProfile = "الملف النشط"

    override val favoritesTitle = "برامجي المفضلة"
    override val favoritesSubtitle = "القنوات والأفلام والمسلسلات المثبتة لهذا الملف"
    override val noFavorites = "لا توجد مفضلات حتى الآن"
    override val noFavoritesHint = "قم بتثبيت برامجك المفضلة لتجدها هنا بسهولة."
    override val pinnedChannels = "قنوات البث المباشر"
    override val pinnedMovies = "أفلام"
    override val pinnedSeries = "مسلسلات"
    override val removeFromFavorites = "إزالة من المفضلة"
    override val addToFavorites = "إضافة إلى المفضلة"

    override val filtersTitle = "تصفية الفئات"
    override val filtersSubtitle = "تخصيص الفئات المرئية لهذا الملف الشخصي"
    override val movieCategories = "فئات الأفلام"
    override val seriesCategories = "فئات المسلسلات"
    override val hiddenCount = "مخفية"
    override val allVisible = "الكل مرئي"
    override val hideCategory = "إخفاء"
    override val showCategory = "إظهار"

    override val searchPlaceholder = "ابحث عن قناة، فيلم، مسلسل..."
    override val searchTitle = "بحث سريع"
    override val noResults = "لم يتم العثور على أي نتائج"
    override val logoutConfirmTitle = "تسجيل الخروج؟"
    override val logoutConfirmMsg = "هل أنت متأكد من رغبتك في تسجيل الخروج من حساب IPTV الخاص بك؟"
    override val confirm = "تأكيد"
    override val synopsis = "نبذة"
    override val episode = "حلقة"
    override val season = "موسم"
    override val audioTracks = "المسارات الصوتية"
    override val subtitles = "الترجمة"
    override val upgradePremium = "الترقية إلى NoosTV Premium"
    override val exitConfirmTitle = "الخروج من NoosTV؟"
    override val exitConfirmMsg = "هل أنت متأكد من رغبتك في إغلاق التطبيق تمامًا؟"
    override val exitQuitButton = "خروج"
    override val pressBackAgainToExit = "اضغط على رجوع مرة أخرى للخروج"
}

object PortugueseStrings : AppStrings {
    override val navLiveTv = "TV em Direto"
    override val navMovies = "Filmes"
    override val navSeries = "Séries"
    override val navEpg = "Guia TV"
    override val navFavorites = "Favoritos"
    override val navFilters = "Filtros"
    override val navSettings = "Definições"

    override val settingsTitle = "Definições & Atualizações"
    override val settingsSubtitle = "Canais GitHub, idioma e diagnóstico do sistema"
    override val githubChannelTitle = "CANAL DE DISTRIBUIÇÃO GITHUB"
    override val stableBranchTitle = "Canal Estável (Recomendado)"
    override val stableBranchDesc = "Versões oficiais testadas, máxima estabilidade para o dia a dia."
    override val testingBranchTitle = "Canal de Testes (Experimental)"
    override val testingBranchDesc = "Pré-visualizações e novas funcionalidades antes do lançamento."
    override val checkUpdates = "Verificar atualizações"
    override val checkAgain = "Verificar novamente"
    override val checkingUpdates = "A procurar novas versões no GitHub..."
    override val noUpdateAvailable = "Nenhuma atualização disponível. Está a utilizar a versão mais recente."
    override val updateAvailable = "Atualização disponível:"
    override val downloadAndInstall = "Descarregar & Instalar"
    override val installUpdate = "Instalar atualização"
    override val downloadingUpdate = "A descarregar a atualização..."
    override val installerReady = "APK pronto! O instalador do Android foi aberto."
    override val reopenInstaller = "Reabrir instalador"
    override val retry = "Tentar novamente"
    override val activeBadge = "ATIVO"
    override val channelTestingBadge = "Canal de testes"
    override val channelStableBadge = "Canal estável"
    override val publishedOn = "Publicado em"
    override val size = "Tamanho:"

    override val languageSectionTitle = "IDIOMA DA INTERFACE"
    override val languageSectionSubtitle = "Personalizar o idioma de visualização do NoosTV"
    override val selectLanguage = "Selecionar idioma"

    override val subscriptionSectionTitle = "SUBSCRIÇÃO IPTV & HARDWARE"
    override val iptvSession = "Sessão IPTV"
    override val server = "Servidor:"
    override val username = "Utilizador:"
    override val logout = "Terminar sessão"
    override val systemDiag = "Diagnóstico do Sistema"
    override val device = "Dispositivo:"
    override val screen = "Ecrã:"
    override val system = "Sistema:"
    override val network = "Rede:"
    override val version = "Versão"

    override val profilesTitle = "Perfis NoosTV"
    override val manageProfiles = "Gerir perfis"
    override val selectProfile = "Selecionar um perfil"
    override val addProfile = "Adicionar um perfil"
    override val editProfile = "Editar perfil"
    override val profileName = "Nome do perfil"
    override val chooseAvatar = "Escolher um avatar"
    override val chooseColor = "Escolher uma cor"
    override val save = "Guardar"
    override val cancel = "Cancelar"
    override val delete = "Eliminar"
    override val cannotDeleteLastProfile = "Não é possível eliminar o último perfil."
    override val activeProfile = "Perfil ativo"

    override val favoritesTitle = "Os meus programas favoritos"
    override val favoritesSubtitle = "Canais, filmes e séries fixados para este perfil"
    override val noFavorites = "Nenhum favorito por agora"
    override val noFavoritesHint = "Fixe os seus programas favoritos para os encontrar facilmente aqui."
    override val pinnedChannels = "Canais em direto"
    override val pinnedMovies = "Filmes"
    override val pinnedSeries = "Séries"
    override val removeFromFavorites = "Remover dos favoritos"
    override val addToFavorites = "Adicionar aos favoritos"

    override val filtersTitle = "Filtros de Categorias"
    override val filtersSubtitle = "Personalize as categorias visíveis para este perfil"
    override val movieCategories = "Categorias de Filmes"
    override val seriesCategories = "Categorias de Séries"
    override val hiddenCount = "oculta(s)"
    override val allVisible = "Todas visíveis"
    override val hideCategory = "Ocultar"
    override val showCategory = "Mostrar"

    override val searchPlaceholder = "Pesquisar canal, filme, série..."
    override val searchTitle = "Pesquisa Rápida"
    override val noResults = "Nenhum resultado encontrado"
    override val logoutConfirmTitle = "Terminar sessão?"
    override val logoutConfirmMsg = "Tem a certeza de que pretende terminar sessão da sua conta IPTV?"
    override val confirm = "Confirmar"
    override val synopsis = "Sinopse"
    override val episode = "Episódio"
    override val season = "Temporada"
    override val audioTracks = "Faixas de áudio"
    override val subtitles = "Legendas"
    override val upgradePremium = "Atualizar para NoosTV Premium"
    override val exitConfirmTitle = "Sair do NoosTV?"
    override val exitConfirmMsg = "Tem certeza de que deseja fechar completamente a aplicação?"
    override val exitQuitButton = "Sair"
    override val pressBackAgainToExit = "Pressione Voltar novamente para sair"
}
