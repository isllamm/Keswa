package com.alsoug.keswa.features.settings.presentation.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alsoug.keswa.core.designsystem.KeswaTheme
import com.alsoug.keswa.core.designsystem.ScreenHeader
import com.alsoug.keswa.core.designsystem.resolve
import com.alsoug.keswa.core.domain.model.ShopSettings
import com.alsoug.keswa.core.printing.MonoBitmap
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val strings = KeswaTheme.strings

    LaunchedEffect(Unit) { viewModel.onEvent(SettingsUiEvent.Load) }
    LaunchedEffect(Unit) {
        viewModel.navigation.collect { if (it is SettingsNavigation.Back) onBack() }
    }
    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is SettingsUiEffect.ShowMessage -> onMessage(effect.message.resolve(strings))
                is SettingsUiEffect.ShowError -> onMessage(effect.message.resolve(strings))
            }
        }
    }

    SettingsContent(state = state, onEvent = viewModel::onEvent, modifier = modifier)
}

@Composable
internal fun SettingsContent(
    state: SettingsUiState,
    onEvent: (SettingsUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = state.settings
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.isLoading || state.isBusy) LinearProgressIndicator(Modifier.fillMaxWidth())

        ScreenHeader(
            title = KeswaTheme.strings.settings,
            subtitle = KeswaTheme.strings.settingsSubtitle,
            onBack = { onEvent(SettingsUiEvent.Back) },
        )

        Section(
            title = KeswaTheme.strings.receiptPrinter,
            subtitle = KeswaTheme.strings.receiptPrinterNote,
        ) {
            AddressRow(
                host = settings.receiptHost,
                port = settings.receiptPort,
                onHost = { onEvent(SettingsUiEvent.Edit(settings.copy(receiptHost = it))) },
                onPort = { onEvent(SettingsUiEvent.Edit(settings.copy(receiptPort = it))) },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PaperChip("80 mm", MonoBitmap.WIDTH_80MM, settings, onEvent)
                PaperChip("58 mm", MonoBitmap.WIDTH_58MM, settings, onEvent)
            }
            OutlinedButton(
                enabled = state.canTestReceipt,
                onClick = { onEvent(SettingsUiEvent.TestReceipt) },
            ) { Text(KeswaTheme.strings.testPrint) }
        }

        Section(
            title = KeswaTheme.strings.labelPrinter,
            subtitle = KeswaTheme.strings.labelPrinterNote,
        ) {
            AddressRow(
                host = settings.labelHost,
                port = settings.labelPort,
                onHost = { onEvent(SettingsUiEvent.Edit(settings.copy(labelHost = it))) },
                onPort = { onEvent(SettingsUiEvent.Edit(settings.copy(labelPort = it))) },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(KeswaTheme.strings.widthMm, settings.labelWidthMm) {
                    onEvent(SettingsUiEvent.Edit(settings.copy(labelWidthMm = it)))
                }
                NumberField(KeswaTheme.strings.heightMm, settings.labelHeightMm) {
                    onEvent(SettingsUiEvent.Edit(settings.copy(labelHeightMm = it)))
                }
                NumberField(KeswaTheme.strings.gapMm, settings.labelGapMm) {
                    onEvent(SettingsUiEvent.Edit(settings.copy(labelGapMm = it)))
                }
            }
            OutlinedButton(
                enabled = state.canTestLabel,
                onClick = { onEvent(SettingsUiEvent.TestLabel) },
            ) { Text(KeswaTheme.strings.testLabel) }
        }

        Section(
            title = KeswaTheme.strings.barcodeScanner,
            subtitle = KeswaTheme.strings.barcodeScannerNote,
        ) {
            NumberField(KeswaTheme.strings.maxGapMs, settings.scanMaxGapMillis.toInt()) {
                onEvent(SettingsUiEvent.Edit(settings.copy(scanMaxGapMillis = it.toLong())))
            }
        }

        Button(onClick = { onEvent(SettingsUiEvent.Save) }) { Text(KeswaTheme.strings.save) }
    }
}

@Composable
private fun Section(title: String, subtitle: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            content()
        }
    }
}

@Composable
private fun AddressRow(host: String, port: Int, onHost: (String) -> Unit, onPort: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = host,
            onValueChange = onHost,
            label = { Text(KeswaTheme.strings.host) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = port.toString(),
            onValueChange = { text -> text.filter(Char::isDigit).toIntOrNull()?.let(onPort) },
            label = { Text(KeswaTheme.strings.port) },
            singleLine = true,
            modifier = Modifier.width(110.dp),
        )
    }
}

@Composable
private fun NumberField(label: String, value: Int, onChange: (Int) -> Unit) {
    OutlinedTextField(
        value = value.toString(),
        onValueChange = { text -> text.filter(Char::isDigit).toIntOrNull()?.let(onChange) },
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.width(120.dp),
    )
}

@Composable
private fun PaperChip(
    label: String,
    dots: Int,
    settings: ShopSettings,
    onEvent: (SettingsUiEvent) -> Unit,
) {
    FilterChip(
        selected = settings.paperWidthDots == dots,
        onClick = { onEvent(SettingsUiEvent.Edit(settings.copy(paperWidthDots = dots))) },
        label = { Text(label) },
    )
}

@Preview
@Composable
private fun SettingsConfiguredPreview() {
    KeswaTheme {
        SettingsContent(
            state = SettingsUiState(
                settings = ShopSettings(
                    receiptHost = "192.168.1.50",
                    labelHost = "192.168.1.51",
                ),
            ),
            onEvent = {},
        )
    }
}

@Preview
@Composable
private fun SettingsFreshInstallPreview() {
    KeswaTheme { SettingsContent(state = SettingsUiState(), onEvent = {}) }
}

@Preview
@Composable
private fun SettingsPrintingPreview() {
    KeswaTheme {
        SettingsContent(
            state = SettingsUiState(isBusy = true, settings = ShopSettings(receiptHost = "10.0.0.5")),
            onEvent = {},
        )
    }
}
