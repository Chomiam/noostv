package io.noostv.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.noostv.core.storage.SessionManager
import io.noostv.data.model.UserProfile
import io.noostv.ui.theme.*

/**
 * Bouton Profil moderne glassy pour le header supérieur (remplace l'ancienne pastille 4K HDR).
 * Affiche l'icône de profil Netflix/Prime et la couleur personnalisée.
 */
@Composable
fun TvProfileButton(
    profile: UserProfile,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.08f else 1.0f, label = "prof_scale")

    Box(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(50))
            .background(
                if (isFocused) Color(0xFF283652) else GlassPill
            )
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) NoosCyan else GlassBorder,
                shape = RoundedCornerShape(50)
            )
            .clickable { onClick() }
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .padding(horizontal = 12.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Pastille Avatar avec couleur et icône du profil (style Netflix / Prime)
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color(profile.avatarColorHex)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = UserProfile.getAvatarIconVector(profile.avatarIcon),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
            }

            Text(
                text = profile.name,
                color = if (isFocused) Color.White else TextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )

            Icon(
                imageVector = Icons.Default.SwitchAccount,
                contentDescription = "Changer de profil",
                tint = if (isFocused) NoosCyan else TextSecondary,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

/**
 * Modale de gestion multi-profils au design Glassy Frosted Blur (style Apple / Google TV / Netflix).
 * Permet de basculer, créer, renommer et personnaliser les profils avec couleurs et logos.
 */
@Composable
fun TvProfileModal(
    sessionManager: SessionManager,
    onDismiss: () -> Unit,
    onProfileChanged: (UserProfile) -> Unit
) {
    BackHandler { onDismiss() }

    var profiles by remember { mutableStateOf(sessionManager.getProfiles()) }
    var activeProfileId by remember { mutableStateOf(sessionManager.getActiveProfileId()) }

    // Mode formulaire : null = liste de profils, non-null = création ou édition
    var isFormOpen by remember { mutableStateOf(false) }
    var editingProfile by remember { mutableStateOf<UserProfile?>(null) }

    var profileNameInput by remember { mutableStateOf("") }
    var selectedColorHex by remember { mutableStateOf(UserProfile.AVATAR_COLORS.first()) }
    var selectedIconId by remember { mutableStateOf("Person") }

    val closeFocusRequester = remember { FocusRequester() }
    val firstCardRequester = remember { FocusRequester() }
    val createBtnRequester = remember { FocusRequester() }
    val nameFieldRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { firstCardRequester.requestFocus() }
    }

    LaunchedEffect(isFormOpen) {
        if (isFormOpen) {
            runCatching { nameFieldRequester.requestFocus() }
        } else {
            runCatching { createBtnRequester.requestFocus() }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xCC070A12)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(840.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(GlassCardGradient)
                    .border(1.5.dp, GlassBorderGradient, RoundedCornerShape(28.dp))
                    .padding(28.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header Modale
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(NoosGradient),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = when {
                                        editingProfile != null -> Icons.Default.Edit
                                        isFormOpen -> Icons.Default.PersonAdd
                                        else -> Icons.Default.Group
                                    },
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = when {
                                        editingProfile != null -> "Modifier le profil"
                                        isFormOpen -> "Créer un nouveau profil"
                                        else -> "Profils Utilisateur"
                                    },
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = when {
                                        editingProfile != null -> "Renommez le profil et personnalisez son logo & couleur"
                                        isFormOpen -> "Configurez le nom, la couleur et le logo style Netflix / Prime"
                                        else -> "Chaque profil conserve ses propres favoris et filtres de catégories"
                                    },
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                if (isFormOpen) {
                                    isFormOpen = false
                                    editingProfile = null
                                } else {
                                    onDismiss()
                                }
                            },
                            modifier = Modifier
                                .focusRequester(closeFocusRequester)
                                .onPreviewKeyEvent { keyEvent ->
                                    if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionDown) {
                                        if (isFormOpen) nameFieldRequester.requestFocus() else firstCardRequester.requestFocus()
                                        true
                                    } else false
                                }
                                .clip(CircleShape)
                                .background(GlassSurface)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Fermer", tint = Color.White)
                        }
                    }

                    HorizontalDivider(color = GlassBorder, thickness = 1.dp)

                    if (!isFormOpen) {
                        // Grille horizontale des profils existants
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            profiles.forEachIndexed { index, profile ->
                                val isActive = profile.id == activeProfileId
                                var cardFocused by remember { mutableStateOf(false) }

                                val cardModifier = Modifier
                                    .weight(1f)
                                    .then(if (index == 0) Modifier.focusRequester(firstCardRequester) else Modifier)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(
                                        when {
                                            cardFocused -> Color(0xFF2B3A5A)
                                            isActive -> Color(0xFF1E283F)
                                            else -> GlassCard
                                        }
                                    )
                                    .border(
                                        width = if (cardFocused) 2.5.dp else if (isActive) 1.5.dp else 1.dp,
                                        color = if (cardFocused) NoosCyan else if (isActive) Color(profile.avatarColorHex) else GlassBorder,
                                        shape = RoundedCornerShape(20.dp)
                                    )
                                    .onPreviewKeyEvent { keyEvent ->
                                        if (keyEvent.type == KeyEventType.KeyDown) {
                                            when (keyEvent.key) {
                                                Key.DirectionDown -> {
                                                    createBtnRequester.requestFocus()
                                                    true
                                                }
                                                Key.DirectionUp -> {
                                                    closeFocusRequester.requestFocus()
                                                    true
                                                }
                                                else -> false
                                            }
                                        } else false
                                    }
                                    .onFocusChanged { cardFocused = it.isFocused }
                                    .focusable()
                                    .padding(16.dp)

                                Box(
                                    modifier = cardModifier,
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        // Section Clic pour Basculer sur le profil
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .clickable {
                                                    sessionManager.setActiveProfileId(profile.id)
                                                    activeProfileId = profile.id
                                                    onProfileChanged(profile)
                                                    onDismiss()
                                                },
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            // Avatar avec icône style Netflix / Prime Video
                                            Box(
                                                modifier = Modifier
                                                    .size(56.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(profile.avatarColorHex))
                                                    .border(2.dp, Color.White.copy(alpha = 0.5f), CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = UserProfile.getAvatarIconVector(profile.avatarIcon),
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(32.dp)
                                                )
                                            }

                                            Text(
                                                text = profile.name,
                                                color = if (cardFocused) Color.White else TextPrimary,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold
                                            )

                                            if (isActive) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(50))
                                                        .background(NoosBlue.copy(alpha = 0.25f))
                                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = "ACTIF",
                                                        color = NoosCyan,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Black
                                                    )
                                                }
                                            } else {
                                                Text(
                                                    text = "Basculer",
                                                    color = TextSecondary,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }

                                        // Actions sur le profil : Renommer / Éditer et Supprimer
                                        Row(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(50))
                                                .background(GlassPill)
                                                .padding(horizontal = 6.dp, vertical = 2.dp),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Bouton Renommer / Modifier avec libellé explicite
                                            var isEditBtnFocused by remember { mutableStateOf(false) }
                                            Button(
                                                onClick = {
                                                    editingProfile = profile
                                                    profileNameInput = profile.name
                                                    selectedColorHex = profile.avatarColorHex
                                                    selectedIconId = profile.avatarIcon
                                                    isFormOpen = true
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (isEditBtnFocused) NoosCyan else Color(0xFF283652)
                                                ),
                                                shape = RoundedCornerShape(50),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                modifier = Modifier
                                                    .onFocusChanged { isEditBtnFocused = it.isFocused }
                                                    .focusable()
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Edit,
                                                    contentDescription = null,
                                                    tint = if (isEditBtnFocused) Color.Black else NoosCyan,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "Modifier",
                                                    color = if (isEditBtnFocused) Color.Black else Color.White,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }

                                            // Bouton Supprimer si plus d'un profil et profil non actif
                                            if (profiles.size > 1 && !isActive) {
                                                IconButton(
                                                    onClick = {
                                                        sessionManager.deleteProfile(profile.id)
                                                        profiles = sessionManager.getProfiles()
                                                    },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "Supprimer le profil",
                                                        tint = RedLive.copy(alpha = 0.85f),
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Bouton Créer un Nouveau Profil
                        var isBtnFocused by remember { mutableStateOf(false) }
                        Button(
                            onClick = {
                                editingProfile = null
                                profileNameInput = ""
                                selectedColorHex = UserProfile.AVATAR_COLORS[profiles.size % UserProfile.AVATAR_COLORS.size]
                                selectedIconId = "Person"
                                isFormOpen = true
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isBtnFocused) Color(0xFF283652) else GlassSurfaceElevated
                            ),
                            shape = RoundedCornerShape(50),
                            border = androidx.compose.foundation.BorderStroke(
                                width = if (isBtnFocused) 2.dp else 1.dp,
                                color = if (isBtnFocused) NoosCyan else GlassBorder
                            ),
                            modifier = Modifier
                                .fillMaxWidth(0.5f)
                                .focusRequester(createBtnRequester)
                                .onFocusChanged { isBtnFocused = it.isFocused }
                                .onPreviewKeyEvent { keyEvent ->
                                    if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionUp) {
                                        firstCardRequester.requestFocus()
                                        true
                                    } else false
                                }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = NoosCyan)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Créer un nouveau profil", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        // Formulaire de Création / Édition Glassy
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(GlassSurfaceElevated)
                                .border(1.dp, GlassBorder, RoundedCornerShape(20.dp))
                                .padding(18.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    text = if (editingProfile != null) "Renommer le profil" else "Nouveau profil",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                OutlinedTextField(
                                    value = profileNameInput,
                                    onValueChange = { profileNameInput = it },
                                    placeholder = { Text("Ex: Salon, Cinéma, Enfants...", color = TextTertiary) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(nameFieldRequester),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = NoosCyan,
                                        unfocusedBorderColor = GlassBorder,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    )
                                )

                                // Suggestions rapides de noms
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Suggestions :", color = TextSecondary, fontSize = 11.sp)
                                    listOf("Enfants", "Famille", "Chambre", "Invité", "Cinéma").forEach { preset ->
                                        var isSuggFocused by remember { mutableStateOf(false) }
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(50))
                                                .background(if (isSuggFocused) Color(0xFF283652) else GlassPill)
                                                .border(1.dp, if (isSuggFocused) NoosCyan else GlassBorder, RoundedCornerShape(50))
                                                .onFocusChanged { isSuggFocused = it.isFocused }
                                                .focusable()
                                                .clickable { profileNameInput = preset }
                                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = preset,
                                                color = if (isSuggFocused) Color.White else NoosCyan,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }

                                // Sélecteur de logos / icônes de profil (style Netflix / Prime)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Logo :", color = TextSecondary, fontSize = 11.sp)
                                    UserProfile.AVATAR_ICONS.forEach { opt ->
                                        val isChosen = opt.id == selectedIconId
                                        var isIconFocused by remember { mutableStateOf(false) }
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(
                                                    when {
                                                        isChosen -> Color(selectedColorHex).copy(alpha = 0.45f)
                                                        isIconFocused -> Color(0xFF283652)
                                                        else -> GlassPill
                                                    }
                                                )
                                                .border(
                                                    width = if (isIconFocused) 2.5.dp else if (isChosen) 2.dp else 1.dp,
                                                    color = if (isIconFocused) FocusGlow else if (isChosen) Color(selectedColorHex) else GlassBorder,
                                                    shape = RoundedCornerShape(10.dp)
                                                )
                                                .onFocusChanged { isIconFocused = it.isFocused }
                                                .focusable()
                                                .clickable { selectedIconId = opt.id },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = opt.icon,
                                                contentDescription = opt.name,
                                                tint = if (isChosen) Color.White else TextSecondary,
                                                modifier = Modifier.size(19.dp)
                                            )
                                        }
                                    }
                                }

                                // Sélecteur de couleur
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Couleur :", color = TextSecondary, fontSize = 11.sp)
                                    UserProfile.AVATAR_COLORS.forEach { colorHex ->
                                        val isChosen = colorHex == selectedColorHex
                                        var isColorFocused by remember { mutableStateOf(false) }
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(Color(colorHex))
                                                .border(
                                                    width = if (isColorFocused) 3.5.dp else if (isChosen) 3.dp else 1.dp,
                                                    color = if (isColorFocused) FocusGlow else if (isChosen) Color.White else Color.Transparent,
                                                    shape = CircleShape
                                                )
                                                .onFocusChanged { isColorFocused = it.isFocused }
                                                .focusable()
                                                .clickable { selectedColorHex = colorHex }
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    var isCancelFocused by remember { mutableStateOf(false) }
                                    TextButton(
                                        onClick = {
                                            isFormOpen = false
                                            editingProfile = null
                                        },
                                        modifier = Modifier
                                            .onFocusChanged { isCancelFocused = it.isFocused }
                                            .border(
                                                width = if (isCancelFocused) 1.5.dp else 0.dp,
                                                color = if (isCancelFocused) NoosCyan else Color.Transparent,
                                                shape = RoundedCornerShape(50)
                                            )
                                    ) {
                                        Text("Annuler", color = if (isCancelFocused) Color.White else TextSecondary)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    var isValFocused by remember { mutableStateOf(false) }
                                    Button(
                                        onClick = {
                                            if (profileNameInput.isNotBlank()) {
                                                if (editingProfile != null) {
                                                    // Modification d'un profil existant
                                                    val updated = sessionManager.updateProfile(
                                                        id = editingProfile!!.id,
                                                        name = profileNameInput.trim(),
                                                        colorHex = selectedColorHex,
                                                        icon = selectedIconId
                                                    )
                                                    profiles = sessionManager.getProfiles()
                                                    if (updated != null && updated.id == activeProfileId) {
                                                        onProfileChanged(updated)
                                                    }
                                                    isFormOpen = false
                                                    editingProfile = null
                                                } else {
                                                    // Création d'un nouveau profil
                                                    val created = sessionManager.createProfile(
                                                        name = profileNameInput.trim(),
                                                        colorHex = selectedColorHex,
                                                        icon = selectedIconId
                                                    )
                                                    profiles = sessionManager.getProfiles()
                                                    activeProfileId = created.id
                                                    onProfileChanged(created)
                                                    isFormOpen = false
                                                    onDismiss()
                                                }
                                            }
                                        },
                                        enabled = profileNameInput.isNotBlank(),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isValFocused) Color(0xFF1D72E8) else NoosBlue
                                        ),
                                        modifier = Modifier
                                            .onFocusChanged { isValFocused = it.isFocused }
                                            .border(
                                                width = if (isValFocused) 2.dp else 0.dp,
                                                color = if (isValFocused) FocusGlow else Color.Transparent,
                                                shape = RoundedCornerShape(50)
                                            ),
                                        shape = RoundedCornerShape(50)
                                    ) {
                                        Text(
                                            text = if (editingProfile != null) "Enregistrer" else "Valider et Activer",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
