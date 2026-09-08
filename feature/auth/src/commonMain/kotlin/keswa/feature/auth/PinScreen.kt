package keswa.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import keswa.domain.auth.AppUser
import keswa.feature.auth.PinContract.Intent
import keswa.feature.auth.PinContract.State
import keswa.feature.auth.generated.resources.Res
import keswa.feature.auth.generated.resources.back
import keswa.feature.auth.generated.resources.enter_pin
import keswa.feature.auth.generated.resources.error_auth_invalid_pin
import keswa.feature.auth.generated.resources.error_auth_locked_out
import keswa.feature.auth.generated.resources.error_auth_no_such_user
import keswa.feature.auth.generated.resources.error_auth_no_users_configured
import keswa.feature.auth.generated.resources.error_common_unexpected
import keswa.feature.auth.generated.resources.select_user
import org.jetbrains.compose.resources.stringResource

@Composable
fun PinScreen(state: State, onIntent: (Intent) -> Unit) {
    Surface(modifier = Modifier.fillMaxSize()) {
        when {
            state.isLoadingUsers -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            state.selectedUser == null -> UserPicker(state.users, onIntent)
            else -> PinPad(state, onIntent)
        }
    }
}

@Composable
private fun UserPicker(users: List<AppUser>, onIntent: (Intent) -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text(stringResource(Res.string.select_user), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(users, key = { it.id }) { user ->
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { onIntent(Intent.UserSelected(user.id)) },
                    tonalElevation = 1.dp,
                ) {
                    Text(user.fullName, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun PinPad(state: State, onIntent: (Intent) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(state.selectedUser?.fullName.orEmpty(), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(Res.string.enter_pin), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(PinContract.PIN_LENGTH) { index ->
                val filled = index < state.pin.length
                Box(
                    Modifier.size(16.dp).clip(CircleShape)
                        .background(if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                )
            }
        }

        state.error?.let { key ->
            Spacer(Modifier.height(8.dp))
            Text(errorMessage(key), color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(24.dp))

        if (state.isUnlocking) {
            CircularProgressIndicator()
        } else {
            DigitGrid(onDigit = { onIntent(Intent.DigitPressed(it)) }, onBackspace = { onIntent(Intent.BackspacePressed) })
        }

        Spacer(Modifier.height(16.dp))
        Button(onClick = { onIntent(Intent.BackToUserPicker) }) { Text(stringResource(Res.string.back)) }
    }
}

@Composable
private fun DigitGrid(onDigit: (Char) -> Unit, onBackspace: () -> Unit) {
    val rows = listOf("123", "456", "789", " 0⌫")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (char in row) {
                    when (char) {
                        ' ' -> Spacer(Modifier.size(64.dp))
                        '⌫' -> Button(onClick = onBackspace, modifier = Modifier.size(64.dp)) { Text("⌫") }
                        else -> Button(onClick = { onDigit(char) }, modifier = Modifier.size(64.dp)) { Text(char.toString()) }
                    }
                }
            }
        }
    }
}

@Composable
private fun errorMessage(key: keswa.core.common.ErrorKey): String = when (key.value) {
    "error.auth.invalid_pin" -> stringResource(Res.string.error_auth_invalid_pin)
    "error.auth.locked_out" -> stringResource(Res.string.error_auth_locked_out)
    "error.auth.no_such_user" -> stringResource(Res.string.error_auth_no_such_user)
    "error.auth.no_users_configured" -> stringResource(Res.string.error_auth_no_users_configured)
    else -> stringResource(Res.string.error_common_unexpected)
}
