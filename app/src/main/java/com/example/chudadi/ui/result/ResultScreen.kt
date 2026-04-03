package com.example.chudadi.ui.result

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.chudadi.R
import com.example.chudadi.model.game.snapshot.MatchUiState
import com.example.chudadi.ui.ComposeTestTags

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    uiState: MatchUiState,
    onRestartMatch: () -> Unit,
    onExitToHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag(ComposeTestTags.RESULT_SCREEN),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.result_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.result_winner, uiState.resultSummary?.winnerName ?: "-"),
                style = MaterialTheme.typography.headlineSmall,
            )
            uiState.resultSummary?.rankingLines.orEmpty().forEach { line ->
                Text(text = line, style = MaterialTheme.typography.bodyLarge)
            }
            Button(
                modifier = Modifier.testTag(ComposeTestTags.RESTART_BUTTON),
                onClick = onRestartMatch,
            ) {
                Text(
                    text = stringResource(R.string.restart_match),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Button(
                modifier = Modifier.testTag(ComposeTestTags.EXIT_BUTTON),
                onClick = onExitToHome,
            ) {
                Text(
                    text = stringResource(R.string.exit_to_home),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}
