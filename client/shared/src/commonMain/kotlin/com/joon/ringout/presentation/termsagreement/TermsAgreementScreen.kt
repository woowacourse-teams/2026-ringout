package com.joon.ringout.presentation.termsagreement

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.presentation.termsagreement.component.AllTermsAgreementRow
import com.joon.ringout.presentation.termsagreement.component.TermAgreementRow
import com.joon.ringout.presentation.termsagreement.component.TermsStartButton

@Composable
fun TermsAgreementScreen(
    onStart: () -> Unit,
    onTermDetailClick: (TermId) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TermsAgreementViewModel = viewModel { TermsAgreementViewModel() },
) {
    TermsAgreementScreenContent(
        uiState = viewModel.uiState,
        onAllAgreementChange = viewModel::setAllAgreed,
        onTermAgreementChange = viewModel::setTermAgreed,
        onTermDetailClick = onTermDetailClick,
        onStartClick = {
            if (viewModel.requestStart() == TermsAgreementAdvance.Complete) {
                onStart()
            }
        },
        modifier = modifier,
    )
}

@Composable
internal fun TermsAgreementScreenContent(
    uiState: TermsAgreementUiState,
    onAllAgreementChange: (Boolean) -> Unit,
    onTermAgreementChange: (TermId, Boolean) -> Unit,
    onTermDetailClick: (TermId) -> Unit,
    onStartClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(start = 24.dp, top = 28.dp, end = 24.dp, bottom = 16.dp),
            ) {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 24.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    item {
                        Text(
                            text = "서비스 이용을 위해 약관에 동의해주세요",
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(36.dp))
                    }
                    item {
                        AllTermsAgreementRow(
                            checked = uiState.isAllAgreed,
                            onCheckedChange = onAllAgreementChange,
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    items(
                        items = uiState.terms,
                        key = { term -> term.id.value },
                    ) { term ->
                        TermAgreementRow(
                            term = term,
                            onAgreedChange = { agreed ->
                                onTermAgreementChange(term.id, agreed)
                            },
                            onDetailClick = { onTermDetailClick(term.id) },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                TermsStartButton(
                    enabled = uiState.canStart,
                    onClick = onStartClick,
                    label = "시작하기",
                )
            }
        }
    }
}

@Preview(name = "Dark terms initial", widthDp = 402, heightDp = 941)
@Composable
private fun DarkTermsInitialPreview() {
    TermsAgreementScreenPreview(ThemeMode.Dark, TermsAgreementUiState())
}

@Preview(name = "Light terms initial", widthDp = 402, heightDp = 941)
@Composable
private fun LightTermsInitialPreview() {
    TermsAgreementScreenPreview(ThemeMode.Light, TermsAgreementUiState())
}

@Preview(name = "Dark terms all agreed", widthDp = 402, heightDp = 941)
@Composable
private fun DarkTermsAllAgreedPreview() {
    TermsAgreementScreenPreview(
        themeMode = ThemeMode.Dark,
        uiState = TermsAgreementUiState(defaultTerms.map { it.copy(isAgreed = true) }),
    )
}

@Preview(name = "Light terms all agreed", widthDp = 402, heightDp = 941)
@Composable
private fun LightTermsAllAgreedPreview() {
    TermsAgreementScreenPreview(
        themeMode = ThemeMode.Light,
        uiState = TermsAgreementUiState(defaultTerms.map { it.copy(isAgreed = true) }),
    )
}

@Preview(name = "Dark terms partially agreed", widthDp = 402, heightDp = 941)
@Composable
private fun DarkTermsPartiallyAgreedPreview() {
    TermsAgreementScreenPreview(
        themeMode = ThemeMode.Dark,
        uiState = TermsAgreementUiState(
            defaultTerms.mapIndexed { index, term -> term.copy(isAgreed = index == 0) },
        ),
    )
}

@Preview(name = "Light terms partially agreed", widthDp = 402, heightDp = 941)
@Composable
private fun LightTermsPartiallyAgreedPreview() {
    TermsAgreementScreenPreview(
        themeMode = ThemeMode.Light,
        uiState = TermsAgreementUiState(
            defaultTerms.mapIndexed { index, term -> term.copy(isAgreed = index == 0) },
        ),
    )
}

@Preview(name = "Small terms", widthDp = 360, heightDp = 800)
@Composable
private fun SmallTermsAgreementPreview() {
    TermsAgreementScreenPreview(ThemeMode.Dark, TermsAgreementUiState())
}

@Preview(name = "Large terms", widthDp = 430, heightDp = 932)
@Composable
private fun LargeTermsAgreementPreview() {
    TermsAgreementScreenPreview(
        themeMode = ThemeMode.Light,
        uiState = TermsAgreementUiState(defaultTerms.map { it.copy(isAgreed = true) }),
    )
}

@Preview(name = "One term centered", widthDp = 402, heightDp = 941)
@Composable
private fun OneTermAgreementPreview() {
    TermsAgreementScreenPreview(
        themeMode = ThemeMode.Light,
        uiState = TermsAgreementUiState(previewTerms(count = 1)),
    )
}

@Preview(name = "Five terms centered", widthDp = 402, heightDp = 941)
@Composable
private fun FiveTermsAgreementPreview() {
    TermsAgreementScreenPreview(
        themeMode = ThemeMode.Dark,
        uiState = TermsAgreementUiState(previewTerms(count = 5)),
    )
}

@Preview(name = "Ten terms scrollable", widthDp = 360, heightDp = 800)
@Composable
private fun TenTermsAgreementPreview() {
    TermsAgreementScreenPreview(
        themeMode = ThemeMode.Light,
        uiState = TermsAgreementUiState(previewTerms(count = 10)),
    )
}

@Composable
private fun TermsAgreementScreenPreview(
    themeMode: ThemeMode,
    uiState: TermsAgreementUiState,
) {
    RingoutTheme(themeMode = themeMode) {
        TermsAgreementScreenContent(
            uiState = uiState,
            onAllAgreementChange = {},
            onTermAgreementChange = { _, _ -> },
            onTermDetailClick = {},
            onStartClick = {},
        )
    }
}

private fun previewTerms(count: Int): List<TermAgreementItem> =
    List(count) { index ->
        TermAgreementItem(
            id = TermId("preview-$index"),
            title = "약관 ${index + 1} 동의",
            isRequired = index < 2,
        )
    }
