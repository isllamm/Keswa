package com.alsoug.keswa

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alsoug.keswa.core.platform.IPlatformProvider
import org.koin.compose.koinInject

/**
 * Phase 0 placeholder. It resolves [IPlatformProvider] through Koin deliberately — rendering the
 * platform name proves the DI graph is wired end to end, which a static "hello" would not.
 */
@Composable
fun App(platform: IPlatformProvider = koinInject()) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Keswa", style = MaterialTheme.typography.headlineMedium)
                Text("كسوة", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${platform.platformName} · ${platform.platformVersion}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
