package io.noostv.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.noostv.ui.theme.*

@Composable
fun HdrBadge(modifier: Modifier = Modifier, text: String = "HDR") {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0x338B5CF6))
            .border(0.8.dp, Color(0x668B5CF6), RoundedCornerShape(50))
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            color = Color(0xFFD8B4FE),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun ResolutionBadge(modifier: Modifier = Modifier, resolution: String = "4K") {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0x223888FF))
            .border(0.8.dp, Color(0x443888FF), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = resolution,
            color = NoosCyan,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun LiveIndicatorBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0x33FF3B30))
            .border(0.8.dp, Color(0x66FF3B30), RoundedCornerShape(50))
            .padding(horizontal = 7.dp, vertical = 2.5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(RedLive)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "DIRECT",
            color = Color(0xFFFF5252),
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
fun PremiumVipBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFFFFD54F), Color(0xFFFFA000))
                )
            )
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Text(
            text = "VIP PREMIUM",
            color = Color(0xFF1A1200),
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.5.sp
        )
    }
}
