package com.jtexpress.bevest.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jtexpress.bevest.R
import com.jtexpress.bevest.ui.common.ErrorNote
import com.jtexpress.bevest.ui.common.PrimaryButton
import com.jtexpress.bevest.ui.common.ReflectiveBand
import com.jtexpress.bevest.ui.theme.BevestIcons
import com.jtexpress.bevest.ui.theme.EyebrowStyle
import com.jtexpress.bevest.ui.theme.LocalStatusPalette
import com.jtexpress.bevest.ui.theme.Radius
import com.jtexpress.bevest.ui.theme.Spacing

@Composable
fun LoginScreen(
    onForgotPassword: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var passwordVisible by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = Spacing.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(Spacing.xxl))

        Image(
            painter = painterResource(R.drawable.bevest_logo),
            contentDescription = "BeVest — monitor, protect, save lives",
            modifier = Modifier.size(180.dp),
        )

        // The product's promise, set as an eyebrow between the two reflective bands —
        // the same pairing that frames content on every screen behind this one, so the
        // sign-in page already looks like the app it opens.
        Spacer(Modifier.height(Spacing.lg))
        ReflectiveBand(
            thickness = 2.dp,
            emphasis = 0.8f,
            modifier = Modifier.widthIn(max = 420.dp),
        )
        Text(
            "MONITOR · PROTECT · SAVE LIVES",
            style = EyebrowStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = Spacing.md),
        )
        ReflectiveBand(
            thickness = 2.dp,
            emphasis = 0.8f,
            modifier = Modifier.widthIn(max = 420.dp),
        )

        Spacer(Modifier.height(Spacing.xl))

        OutlinedTextField(
            value = state.email,
            onValueChange = viewModel::onEmailChange,
            label = { Text("Email") },
            leadingIcon = { Icon(Icons.Outlined.Email, contentDescription = null) },
            singleLine = true,
            isError = state.emailError != null,
            supportingText = state.emailError?.let { { Text(it) } },
            shape = RoundedCornerShape(Radius.md),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            ),
            keyboardActions = KeyboardActions(onNext = { focus.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) }),
            modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp),
        )

        Spacer(Modifier.height(Spacing.sm))

        OutlinedTextField(
            value = state.password,
            onValueChange = viewModel::onPasswordChange,
            label = { Text("Password") },
            leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = if (passwordVisible) "Hide password" else "Show password",
                    )
                }
            },
            singleLine = true,
            isError = state.passwordError != null,
            supportingText = state.passwordError?.let { { Text(it) } },
            shape = RoundedCornerShape(Radius.md),
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = {
                keyboard?.hide()
                viewModel.submit()
            }),
            modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp),
        )

        AnimatedVisibility(visible = state.formError != null) {
            ErrorNote(
                message = state.formError.orEmpty(),
                modifier = Modifier.widthIn(max = 420.dp).padding(top = Spacing.md),
            )
        }

        Spacer(Modifier.height(Spacing.lg))

        PrimaryButton(
            text = "Sign in",
            onClick = {
                keyboard?.hide()
                viewModel.submit()
            },
            loading = state.submitting,
            loadingText = "Signing in…",
            modifier = Modifier.widthIn(max = 420.dp),
        )

        TextButton(
            onClick = onForgotPassword,
            modifier = Modifier.heightIn(min = Spacing.touchTarget),
        ) {
            Text("Forgot password?")
        }

        Spacer(Modifier.height(Spacing.xxl))
    }
}

@Composable
fun ForgotPasswordScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ForgotPasswordViewModel = hiltViewModel(),
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value

    Column(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = Spacing.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(72.dp)
                .padding(bottom = Spacing.md),
        ) {
            Image(
                painter = painterResource(R.drawable.bevest_mark),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Text("Reset password", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(Spacing.sm))

        if (state.sent) {
            Text(
                "If an account exists for ${state.email}, a reset link is on its way.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.lg))
            PrimaryButton(
                text = "Back to sign in",
                onClick = onBack,
                modifier = Modifier.widthIn(max = 420.dp),
            )
        } else {
            Text(
                "Enter your account email and we'll send a reset link.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.lg))
            OutlinedTextField(
                value = state.email,
                onValueChange = viewModel::onEmailChange,
                label = { Text("Email") },
                leadingIcon = { Icon(Icons.Outlined.Email, contentDescription = null) },
                singleLine = true,
                isError = state.emailError != null,
                supportingText = state.emailError?.let { { Text(it) } },
                shape = RoundedCornerShape(Radius.md),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp),
            )
            state.formError?.let {
                ErrorNote(
                    message = it,
                    modifier = Modifier.widthIn(max = 420.dp).padding(top = Spacing.sm),
                )
            }
            Spacer(Modifier.height(Spacing.lg))
            PrimaryButton(
                text = "Send reset link",
                onClick = viewModel::submit,
                loading = state.submitting,
                loadingText = "Sending…",
                modifier = Modifier.widthIn(max = 420.dp),
            )
            TextButton(onClick = onBack, modifier = Modifier.heightIn(min = Spacing.touchTarget)) {
                Text("Back")
            }
        }
    }
}
