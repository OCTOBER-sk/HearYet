package com.hearyet.app.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hearyet.app.core.ui.designsystem.NextIcons
import com.hearyet.app.core.model.SessionState

@Composable
fun SessionStateLadder(
    currentState: SessionState,
    modifier: Modifier = Modifier,
) {
    val currentIndex = currentState.ladderIndex
    val states = listOf(
        "Idle",
        "Waiting for guests",
        "Waiting for media",
        "Connecting…",
        "Syncing clock…",
        "In sync",
        "In sync",
        "Connection issue",
        "Session ended",
    )

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        states.forEachIndexed { index, label ->
            val isCurrent = index == currentIndex
            val isComplete = index < currentIndex
            val color = when {
                isCurrent -> MaterialTheme.colorScheme.primary
                isComplete -> MaterialTheme.colorScheme.onSurface
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when {
                    isComplete -> Icon(
                        imageVector = NextIcons.Check,
                        contentDescription = "Complete",
                        modifier = Modifier.size(20.dp),
                        tint = color,
                    )
                    isCurrent -> Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(color, CircleShape),
                    )
                    else -> Spacer(Modifier.size(12.dp))
                }
                Spacer(Modifier.width(Spacing.md))
                Text(
                    text = label,
                    color = color,
                    style = if (isCurrent) {
                        MaterialTheme.typography.titleLarge
                    } else {
                        MaterialTheme.typography.bodyLarge
                    },
                )
            }
        }
    }
}

private val SessionState.ladderIndex: Int
    get() = when (this) {
        SessionState.Idle -> 0
        SessionState.Advertising -> 1
        SessionState.WaitingForMedia -> 2
        SessionState.Discovering -> 3
        SessionState.ClockSyncing -> 4
        is SessionState.Connected -> 5
        is SessionState.Playing -> 6
        is SessionState.Paused -> 6
        is SessionState.Error -> 7
        SessionState.Ended -> 8
    }

@Preview
@Composable
private fun SessionStateLadderPreview() {
    SessionStateLadder(currentState = SessionState.ClockSyncing)
}
