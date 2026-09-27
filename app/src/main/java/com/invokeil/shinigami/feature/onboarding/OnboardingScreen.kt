package com.invokeil.shinigami.feature.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.invokeil.shinigami.R
import com.invokeil.shinigami.core.ui.components.ShiniButton
import com.invokeil.shinigami.core.ui.components.ShiniCard
import com.invokeil.shinigami.core.ui.components.ShiniOrb
import com.invokeil.shinigami.core.ui.components.OrbState
import kotlinx.coroutines.launch

data class OnboardingPage(
    val icon: ImageVector?,
    val titleRes: Int,
    val bodyRes: Int,
)

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val pages = listOf(
        OnboardingPage(null, R.string.ob_welcome_title, R.string.ob_welcome_body),
        OnboardingPage(Icons.Rounded.SmartToy, R.string.ob_capability_title, R.string.ob_capability_body),
        OnboardingPage(Icons.Rounded.Lock, R.string.ob_privacy_title, R.string.ob_privacy_body),
        OnboardingPage(Icons.Rounded.VpnKey, R.string.ob_choice_title, R.string.ob_choice_offline_body),
        OnboardingPage(Icons.Rounded.Mic, R.string.ob_voice_title, R.string.ob_voice_body),
        OnboardingPage(Icons.Rounded.Verified, R.string.ob_finish_title, R.string.ob_finish_body),
    )
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Spacer(Modifier.height(28.dp))

        AnimatedContent(
            targetState = pagerState.currentPage,
            transitionSpec = {
                (slideInHorizontally { it / 3 } + fadeIn(tween(250)))
                    .togetherWith(slideOutHorizontally { -it / 3 } + fadeOut(tween(200)))
            },
            label = "orb",
        ) { page ->
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (page == 0) {
                    ShiniOrb(
                        state = OrbState.IDLE,
                        size = 120.dp,
                        onClick = null,
                        contentDescriptionText = "Shini",
                    )
                } else {
                    Icon(
                        pages[page].icon ?: Icons.Rounded.SmartToy,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(88.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(34.dp))

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
        ) { page ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top,
                modifier = Modifier.fillMaxSize(),
            ) {
                Text(
                    stringResource(pages[page].titleRes),
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    stringResource(pages[page].bodyRes),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                if (page == 3) {
                    Spacer(Modifier.height(22.dp))
                    ShiniCard {
                        Column(Modifier.padding(18.dp)) {
                            Text(
                                stringResource(R.string.ob_choice_offline_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.ob_choice_offline_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                stringResource(R.string.ob_choice_provider_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.ob_choice_provider_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        // Dots
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            repeat(pages.size) { i ->
                Box(
                    Modifier
                        .size(if (pagerState.currentPage == i) 22.dp else 7.dp, 7.dp)
                        .then(
                            Modifier,
                        )
                        .background(
                            color = if (pagerState.currentPage == i) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline,
                            shape = MaterialTheme.shapes.small,
                        ),
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (pagerState.currentPage > 0) {
                ShiniButton(
                    stringResource(R.string.ob_back),
                    onClick = {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                    },
                )
                Spacer(Modifier.width(12.dp))
            }
            Box(Modifier.weight(1f))
            ShiniButton(
                if (pagerState.currentPage == pages.size - 1) stringResource(R.string.ob_get_started)
                else stringResource(R.string.ob_next),
                onClick = {
                    if (pagerState.currentPage == pages.size - 1) {
                        viewModel.completeOnboarding()
                        onFinished()
                    } else {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }
                },
            )
        }
        Spacer(Modifier.height(18.dp))
    }
}
