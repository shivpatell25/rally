@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.shiv.rally.presentation.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import com.shiv.rally.R
import com.shiv.rally.presentation.theme.AppleTvTheme
import kotlinx.coroutines.delay

private val onboardingShape = RoundedCornerShape(10.dp)

@Composable
fun OnboardingScreen(onContinue: () -> Unit) {
    val continueFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(180)
        runCatching { continueFocus.requestFocus() }
    }

    Box(Modifier.fillMaxSize().padding(horizontal = 54.dp, vertical = 34.dp)) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(R.drawable.rally_wordmark_color_ui),
                contentDescription = "Rally",
                modifier = Modifier.width(210.dp).height(72.dp)
            )
            Text(
                "Sports, kept simple.",
                color = Color.White,
                fontSize = 31.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-.4).sp
            )
            Spacer(Modifier.height(7.dp))
            Text(
                "Live games, verified sources, highlights, and the teams you follow—built for the biggest screen in your home.",
                color = AppleTvTheme.TextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(760.dp)
            )
            Spacer(Modifier.height(30.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                OnboardingCard("01", "Choose your sports", "Arrange leagues and favorite teams so Rally promotes the games that matter to you.", Modifier.weight(1f))
                OnboardingCard("02", "Connect your sources", "Add only the IPTV portals and Stremio addons you are authorized to use.", Modifier.weight(1f))
                OnboardingCard("03", "Watch your way", "Use automatic source selection, Game View, current highlights, and Multi-View from one remote-first interface.", Modifier.weight(1f))
            }
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = onContinue,
                modifier = Modifier.width(260.dp).focusRequester(continueFocus),
                shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                scale = ButtonDefaults.scale(scale = 1f, focusedScale = 1.035f),
                colors = ButtonDefaults.colors(
                    containerColor = AppleTvTheme.OffWhite,
                    focusedContainerColor = Color.White,
                    contentColor = AppleTvTheme.DeepNavy,
                    focusedContentColor = AppleTvTheme.DeepNavy
                ),
                border = ButtonDefaults.border(
                    border = Border(border = BorderStroke(1.dp, Color(0x50FFFFFF)), shape = RoundedCornerShape(8.dp)),
                    focusedBorder = Border(border = BorderStroke(2.dp, Color(0xFF6FCFFE)), shape = RoundedCornerShape(8.dp))
                )
            ) {
                Text("Continue to setup", fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 5.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text("Rally includes no television service or streams.", color = AppleTvTheme.TextTertiary, fontSize = 10.sp)
        }
    }
}

@Composable
private fun OnboardingCard(number: String, title: String, description: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .height(150.dp)
            .background(Color(0xA0121C27), onboardingShape)
            .border(1.dp, Color(0x365A7894), onboardingShape)
            .padding(18.dp)
    ) {
        Box(
            Modifier.size(30.dp).background(Color(0x202B9BC7), RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(number, color = Color(0xFF6FCFFE), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(11.dp))
        Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(5.dp))
        Text(description, color = AppleTvTheme.TextSecondary, fontSize = 10.5.sp, lineHeight = 15.sp)
    }
}
