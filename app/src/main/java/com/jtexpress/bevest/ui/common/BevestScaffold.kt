package com.jtexpress.bevest.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jtexpress.bevest.R
import com.jtexpress.bevest.ui.theme.EyebrowStyle

/**
 * Standard screen chrome. A screen either has a back arrow (a detail) or the BeVest mark
 * (a root tab) — so the user always knows whether they are deep in a flow.
 */
@Composable
fun BevestScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    showBrandMark: Boolean = onBack == null,
    actions: @Composable (RowScope.() -> Unit) = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            val hairline = MaterialTheme.colorScheme.outlineVariant
            TopAppBar(
                modifier = Modifier.drawBehind {
                    drawLine(
                        color = hairline,
                        start = androidx.compose.ui.geometry.Offset(0f, size.height),
                        end = androidx.compose.ui.geometry.Offset(size.width, size.height),
                        strokeWidth = 1f,
                    )
                },
                title = {
                    Column {
                        if (subtitle != null) {
                            Text(
                                subtitle.uppercase(),
                                style = EyebrowStyle,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Text(
                            title,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    when {
                        onBack != null -> IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                        showBrandMark -> Image(
                            painter = painterResource(R.drawable.bevest_mark),
                            contentDescription = null,
                            modifier = Modifier.padding(start = 12.dp).size(32.dp),
                        )
                    }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        floatingActionButton = floatingActionButton,
    ) { padding ->
        // The app draws edge to edge, so the window no longer resizes for the keyboard.
        // Give every screen IME padding here rather than in each form.
        Box(Modifier.fillMaxSize().imePadding()) {
            content(padding)
        }
    }
}
