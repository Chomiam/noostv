package io.noostv.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.noostv.ui.theme.*

@Composable
fun HdrBadge(modifier: Modifier = Modifier, text: String = "HDR") {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(PurpleHdr)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun ResolutionBadge(modifier: Modifier = Modifier, resolution: String = "4K") {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(NeonCyan.copy(alpha = 0.2f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = resolution,
            color = NeonCyan,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun LiveIndicatorBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(RedLive)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "DIRECT",
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
fun PremiumVipBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(GoldVip)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = "VIP PREMIUM",
            color = Color.Black,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black
        )
    }
}
