package ph.pusoalalay.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import ph.pusoalalay.app.R

/** Sign in or create a local account. Accounts and records exist only on this phone. */
@Composable
fun AuthScreen(hasAccounts: Boolean, busy: Boolean, error: Int?,
    onSignIn: (String, String) -> Unit, onSignUp: (String, String, String, String) -> Unit, onClearError: () -> Unit) {
    var creating by rememberSaveable { mutableStateOf(!hasAccounts) }
    var name by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    // Passwords are deliberately not saved across process death.
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val usernameHelp: (@Composable () -> Unit)? = if (creating) { { Text(stringResource(R.string.auth_username_hint)) } } else null
    val passwordHelp: (@Composable () -> Unit)? = if (creating) { { Text(stringResource(R.string.auth_password_hint)) } } else null
    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(Icons.Outlined.FavoriteBorder, null, tint = Mint, modifier = Modifier.padding(top = 16.dp).size(40.dp))
        Text("PusoAlalay", style = MaterialTheme.typography.titleLarge, color = Mint)
        Text(stringResource(if (creating) R.string.auth_create_title else R.string.auth_signin_title),
            style = MaterialTheme.typography.headlineLarge)
        Text(stringResource(if (creating) R.string.auth_create_body else R.string.auth_signin_body), color = Muted)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilterChip(!creating, { creating = false; onClearError() }, label = { Text(stringResource(R.string.auth_signin_tab)) }, enabled = !busy)
            FilterChip(creating, { creating = true; onClearError() }, label = { Text(stringResource(R.string.auth_create_tab)) }, enabled = !busy)
        }
        if (creating) OutlinedTextField(name, { name = it; onClearError() }, modifier = Modifier.fillMaxWidth().testTag("auth_name"),
            label = { Text(stringResource(R.string.auth_name)) }, singleLine = true, enabled = !busy)
        OutlinedTextField(username, { username = it; onClearError() }, modifier = Modifier.fillMaxWidth().testTag("auth_username"),
            label = { Text(stringResource(R.string.auth_username)) }, singleLine = true, enabled = !busy,
            supportingText = usernameHelp, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
        OutlinedTextField(password, { password = it; onClearError() }, modifier = Modifier.fillMaxWidth().testTag("auth_password"),
            label = { Text(stringResource(R.string.auth_password)) }, singleLine = true, enabled = !busy,
            supportingText = passwordHelp, visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
        if (creating) OutlinedTextField(confirm, { confirm = it; onClearError() }, modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.auth_confirm)) }, singleLine = true, enabled = !busy,
            visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
        error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
        Button(onClick = { if (creating) onSignUp(name, username, password, confirm) else onSignIn(username, password) },
            enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("auth_submit")) {
            Text(stringResource(when {
                busy -> R.string.auth_working
                creating -> R.string.auth_create_button
                else -> R.string.auth_signin_button
            }))
        }
        Text(stringResource(R.string.auth_local_note), style = MaterialTheme.typography.bodySmall, color = Muted)
    }
}
