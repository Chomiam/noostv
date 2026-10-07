package io.noostv.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import io.noostv.data.model.Channel
import io.noostv.data.model.EpgProgram
import io.noostv.ui.theme.CardBorderUnfocused
import io.noostv.ui.theme.DarkOledBackground
import io.noostv.ui.theme.NoosCyan
import io.noostv.ui.theme.SurfaceDark
import io.noostv.ui.theme.TextPrimary
import io.noostv.ui.theme.TextSecondary
import io.noostv.ui.theme.TextTertiary
import io.noostv.ui.tv.EpgProvider

/**
 * Guide TV compact affiché sous le lecteur lorsque le téléphone est en mode vertical.
 *
 * Affiche la liste des chaînes (logotype + numéro + nom), le programme en cours
 * (titre, plage horaire, barre de progression) et permet de changer de chaîne d'un tap.
 * Défilement automatique sur la chaîne actuellement à l'antenne.
 */
@Composable
fun MobileTvGuidePanel(
    channels: List<Channel>,
    epgPrograms: List<EpgProgram>,
    activeChannel: Channel?,
    onSelectChannel: (Channel) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedCategory by remember { mutableStateOf("Toutes") }

    val categories = remember(channels) {
        listOf("Toutes") + channels.map { it.categoryName }.distinct().sorted()
    }

    val visibleChannels = remember(channels, selectedCategory) {
        val base = if (selectedCategory == "Toutes") channels
        else channels.filter { it.categoryName.equals(selectedCategory, ignoreCase = true) }
        // Mise en avant de la chaîne active en tête de liste si elle n'est pas dans le filtre
        base.take(200)
    }

    val listState = rememberLazyListState()

    LaunchedEffect(activeChannel?.id, selectedCategory) {
        val idx = visibleChannels.indexOfFirst { it.id == activeChannel?.id }
        if (idx > 0) {
            runCatching { listState.scrollToItem(idx) }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkOledBackground)
            .border(1.dp, CardBorderUnfocused)
    ) {
        // ------------------ EN-TÊTE ------------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.LiveTv,
                contentDescription = null,
                tint = NoosCyan,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "GUIDE TV",
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "${visibleChannels.size} chaînes",
                color = TextTertiary,
                fontSize = 11.sp
            )
        }

        // ------------------ FILTRES PAR CATÉGORIE ------------------
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(categories) { category ->
                val isSelected = category == selectedCategory
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (isSelected) NoosCyan.copy(alpha = 0.18f) else SurfaceDark)
                        .border(
                            width = 1.dp,
                            color = if (isSelected) NoosCyan else CardBorderUnfocused,
                            shape = RoundedCornerShape(50)
                        )
                        .clickable { selectedCategory = category }
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = category,
                        color = if (isSelected) NoosCyan else TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ------------------ LISTE DES CHAÎNES + PROGRAMME EN COURS ------------------
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(visibleChannels, key = { it.id }) { channel ->
                GuideChannelRow(
                    channel = channel,
                    program = remember(channel.id, epgPrograms) {
                        EpgProvider.getCurrentProgram(channel, epgPrograms)
                    },
                    isActive = channel.id == activeChannel?.id,
                    onClick = { onSelectChannel(channel) }
                )
            }
        }
    }
}

@Composable
private fun GuideChannelRow(
    channel: Channel,
    program: EpgProgram,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isActive) NoosCyan.copy(alpha = 0.10f) else SurfaceDark)
            .border(
                width = if (isActive) 1.5.dp else 1.dp,
                color = if (isActive) NoosCyan else CardBorderUnfocused,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Logotype de la chaîne
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(Color(0xFF0F1320)),
            contentAlignment = Alignment.Center
        ) {
            if (!channel.logoUrl.isNullOrBlank()) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                        .data(channel.logoUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = channel.name,
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(3.dp)
                )
            } else {
                Text(
                    text = channel.num?.toString() ?: "TV",
                    color = NoosCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (channel.num != null) {
                    Text(
                        text = channel.num.toString(),
                        color = TextTertiary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = channel.name,
                    color = if (isActive) NoosCyan else TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isActive) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "● EN COURS",
                        color = NoosCyan,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = program.title,
                color = TextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = program.timeSlotFormatted,
                    color = TextTertiary,
                    fontSize = 9.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                LinearProgressIndicator(
                    progress = { program.progressFraction() },
                    color = if (isActive) NoosCyan else TextTertiary,
                    trackColor = Color(0xFF1B2234),
                    modifier = Modifier
                        .weight(1f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                )
            }
        }
    }
}