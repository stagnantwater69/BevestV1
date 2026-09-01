package com.jtexpress.bevest.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jtexpress.bevest.domain.model.User
import com.jtexpress.bevest.ui.theme.Spacing

/**
 * Top-right entry to the profile, identical for every role so the app reads as one
 * product. Shows the signed-in person's initials rather than a generic glyph — it is
 * *your* account, and that is faster to recognise than an icon.
 */
@Composable
fun ProfileAction(
    user: User,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .padding(end = Spacing.sm)
            .size(Spacing.touchTarget)
            .semantics { contentDescription = "Profile and settings" }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        WorkerAvatar(
            name = user.fullName.ifBlank { user.email },
            photoUrl = null,
            size = 34.dp,
        )
    }
}
