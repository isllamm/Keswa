package com.alsoug.keswa.features.auth.presentation.screens.signin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alsoug.keswa.core.designsystem.KeswaTheme
import com.alsoug.keswa.core.designsystem.resolve
import com.alsoug.keswa.core.domain.model.User
import com.alsoug.keswa.core.domain.model.UserRole
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun SignInScreen(
    viewModel: SignInViewModel,
    onSignedIn: () -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val strings = KeswaTheme.strings

    LaunchedEffect(Unit) { viewModel.onEvent(SignInUiEvent.Load) }
    LaunchedEffect(Unit) {
        viewModel.navigation.collect { if (it is SignInNavigation.SignedIn) onSignedIn() }
    }
    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is SignInUiEffect.ShowError -> onMessage(effect.message.resolve(strings))
                is SignInUiEffect.ShowMessage -> onMessage(effect.message.resolve(strings))
            }
        }
    }

    SignInContent(state = state, onEvent = viewModel::onEvent, modifier = modifier)
}

@Composable
internal fun SignInContent(
    state: SignInUiState,
    onEvent: (SignInUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Card(modifier = Modifier.widthIn(max = 400.dp).padding(20.dp)) {
            Column(
                modifier = Modifier.padding(24.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (state.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(KeswaTheme.strings.appName, style = MaterialTheme.typography.headlineSmall)
                    Text("كسوة", style = MaterialTheme.typography.titleMedium)
                }

                when {
                    state.recoveryCode != null -> RecoveryCodePanel(state.recoveryCode, onEvent)
                    state.mustChangeFor != null -> ReplaceSecretPanel(state.mustChangeFor, onEvent)
                    state.needsSetup -> FirstRunPanel(onEvent)
                    else -> SignInPanel(state, onEvent)
                }
            }
        }
    }
}

@Composable
private fun SignInPanel(state: SignInUiState, onEvent: (SignInUiEvent) -> Unit) {
    if (state.sellers.isNotEmpty()) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = state.mode == SignInMode.SELLER,
                onClick = { onEvent(SignInUiEvent.ModeChanged(SignInMode.SELLER)) },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
            ) { Text(KeswaTheme.strings.seller) }
            SegmentedButton(
                selected = state.mode == SignInMode.ADMIN,
                onClick = { onEvent(SignInUiEvent.ModeChanged(SignInMode.ADMIN)) },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
            ) { Text(KeswaTheme.strings.admin) }
        }
    }

    if (state.isLocked) {
        Text(
            KeswaTheme.strings.lockedTryShortly,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
    }

    when (state.mode) {
        SignInMode.SELLER -> SellerPinPanel(state, onEvent)
        SignInMode.ADMIN -> AdminPasswordPanel(state, onEvent)
    }
}

@Composable
private fun SellerPinPanel(state: SignInUiState, onEvent: (SignInUiEvent) -> Unit) {
    Text(KeswaTheme.strings.whoIsOnTheTill, style = MaterialTheme.typography.labelMedium)
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        state.sellers.take(3).forEach { seller ->
            AssistChip(
                onClick = { onEvent(SignInUiEvent.SellerSelected(seller.id)) },
                label = { Text(seller.displayName) },
            )
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(SignInUiState.PIN_LENGTH) { index ->
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .background(
                        if (index < state.pin.length) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        CircleShape,
                    )
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
            )
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("123", "456", "789").forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { digit -> Key(digit.toString()) { onEvent(SignInUiEvent.PinDigit(digit)) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.width(72.dp))
            Key("0") { onEvent(SignInUiEvent.PinDigit('0')) }
            Key("⌫") { onEvent(SignInUiEvent.PinBackspace) }
        }
    }
}

@Composable
private fun Key(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.width(72.dp)) {
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun AdminPasswordPanel(state: SignInUiState, onEvent: (SignInUiEvent) -> Unit) {
    OutlinedTextField(
        value = state.username,
        onValueChange = { onEvent(SignInUiEvent.UsernameChanged(it)) },
        label = { Text(KeswaTheme.strings.username) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = state.password,
        onValueChange = { onEvent(SignInUiEvent.PasswordChanged(it)) },
        label = { Text(KeswaTheme.strings.password) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions.Default,
        modifier = Modifier.fillMaxWidth(),
    )
    Button(
        enabled = state.canSubmitPassword,
        onClick = { onEvent(SignInUiEvent.SubmitPassword) },
        modifier = Modifier.fillMaxWidth(),
    ) { Text(KeswaTheme.strings.signIn) }
}

@Composable
private fun FirstRunPanel(onEvent: (SignInUiEvent) -> Unit) {
    var username by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Text(KeswaTheme.strings.createOwnerAccount, style = MaterialTheme.typography.titleMedium)
    Text(
        KeswaTheme.strings.newInstallationNobody,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    OutlinedTextField(
        value = displayName,
        onValueChange = { displayName = it },
        label = { Text(KeswaTheme.strings.yourName) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = username,
        onValueChange = { username = it },
        label = { Text(KeswaTheme.strings.username) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        label = { Text(KeswaTheme.strings.passwordAtLeastEight) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )
    Button(
        enabled = username.isNotBlank() && displayName.isNotBlank() && password.length >= 8,
        onClick = { onEvent(SignInUiEvent.Bootstrap(username, displayName, password)) },
        modifier = Modifier.fillMaxWidth(),
    ) { Text(KeswaTheme.strings.createAccount) }
}

@Composable
private fun RecoveryCodePanel(code: String, onEvent: (SignInUiEvent) -> Unit) {
    Text(KeswaTheme.strings.writeThisDown, style = MaterialTheme.typography.titleMedium)
    Text(
        KeswaTheme.strings.recoveryOnlyWayBack,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Text(
        code,
        style = MaterialTheme.typography.headlineSmall,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.padding(vertical = 8.dp),
    )
    Button(
        onClick = { onEvent(SignInUiEvent.DismissRecoveryCode) },
        modifier = Modifier.fillMaxWidth(),
    ) { Text(KeswaTheme.strings.iHaveWrittenItDown) }
}

@Composable
private fun ReplaceSecretPanel(user: User, onEvent: (SignInUiEvent) -> Unit) {
    var secret by remember { mutableStateOf("") }
    val minimum = if (user.role == UserRole.SELLER) 4 else 8

    Text(KeswaTheme.strings.chooseNewSecret(isPin = user.role == UserRole.SELLER))
    Text(
        KeswaTheme.strings.detailsResetByAdmin,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    OutlinedTextField(
        value = secret,
        onValueChange = { secret = it },
        label = { Text(KeswaTheme.strings.newSecretAtLeast(minimum)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )
    Button(
        enabled = secret.length >= minimum,
        onClick = { onEvent(SignInUiEvent.ReplaceSecret(secret)) },
        modifier = Modifier.fillMaxWidth(),
    ) { Text(KeswaTheme.strings.saveAndContinue) }
}

private val previewSellers = listOf(
    User("u1", "mona", "Mona Adel", "منى عادل", UserRole.SELLER),
    User("u2", "tarek", "Tarek Nabil", "طارق نبيل", UserRole.SELLER),
)

@Preview
@Composable
private fun SignInSellerPreview() {
    KeswaTheme {
        SignInContent(
            state = SignInUiState(sellers = previewSellers, selectedSellerId = "u1", pin = "12"),
            onEvent = {},
        )
    }
}

@Preview
@Composable
private fun SignInAdminPreview() {
    KeswaTheme {
        SignInContent(
            state = SignInUiState(
                sellers = previewSellers,
                mode = SignInMode.ADMIN,
                username = "hala",
                password = "secret",
            ),
            onEvent = {},
        )
    }
}

@Preview
@Composable
private fun SignInFirstRunPreview() {
    KeswaTheme { SignInContent(state = SignInUiState(needsSetup = true), onEvent = {}) }
}

@Preview
@Composable
private fun SignInRecoveryCodePreview() {
    KeswaTheme {
        SignInContent(state = SignInUiState(recoveryCode = "KSW-BDFH-JKLM-NPQR"), onEvent = {})
    }
}

@Preview
@Composable
private fun SignInLockedPreview() {
    KeswaTheme {
        SignInContent(
            state = SignInUiState(
                sellers = previewSellers,
                selectedSellerId = "u1",
                lockedUntilMillis = 1L,
            ),
            onEvent = {},
        )
    }
}
