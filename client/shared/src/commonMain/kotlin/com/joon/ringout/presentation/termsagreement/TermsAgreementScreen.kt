package com.joon.ringout.presentation.termsagreement

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.presentation.termsagreement.component.AllTermsAgreementRow
import com.joon.ringout.presentation.termsagreement.component.TermAgreementRow
import com.joon.ringout.presentation.termsagreement.component.TermsStartButton

@Composable
internal fun TermsAgreementScreen(
    uiState: TermsAgreementUiState,
    onAllAgreementChange: (Boolean) -> Unit,
    onTermAgreementChange: (TermId, Boolean) -> Unit,
    onTermDetailClick: (TermId) -> Unit,
    onStartClick: () -> Unit,
    modifier: Modifier = Modifier,
    startEnabled: Boolean = true,
    errorMessage: String? = null,
    secondaryAction: @Composable ColumnScope.() -> Unit = {},
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
                            text = uiState.title,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(36.dp))
                    }
                    when (uiState.contentState) {
                        TermsAgreementContentState.Agreement -> {
                            if (uiState.terms.any { !it.isReadOnly }) {
                                item {
                                    AllTermsAgreementRow(
                                        checked = uiState.isAllAgreed,
                                        onCheckedChange = onAllAgreementChange,
                                        enabled = startEnabled,
                                    )
                                }
                                item { Spacer(modifier = Modifier.height(12.dp)) }
                            }
                            items(
                                items = uiState.terms,
                                key = { term -> term.id.value },
                            ) { term ->
                                TermAgreementRow(
                                    term = term,
                                    onAgreedChange = { agreed -> onTermAgreementChange(term.id, agreed) },
                                    onDetailClick = { onTermDetailClick(term.id) },
                                    enabled = startEnabled,
                                )
                            }
                        }
                        TermsAgreementContentState.Loading -> item {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                CircularProgressIndicator()
                                Text(
                                    text = "약관 동의 상태를 확인하고 있어요.",
                                    modifier = Modifier.padding(top = 16.dp),
                                )
                            }
                        }
                        TermsAgreementContentState.Error -> Unit
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                errorMessage?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                    )
                }
                if (uiState.contentState != TermsAgreementContentState.Loading) {
                    TermsStartButton(
                        enabled = startEnabled && (
                            uiState.contentState == TermsAgreementContentState.Error || uiState.canStart
                        ),
                        onClick = onStartClick,
                        label = uiState.actionLabel,
                    )
                }
                secondaryAction()
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
        TermsAgreementScreen(
            uiState = uiState,
            onAllAgreementChange = {},
            onTermAgreementChange = { _, _ -> },
            onTermDetailClick = {},
            onStartClick = {},
        )
    }
}

@Preview(name = "재동의 일부 완료", widthDp = 360, heightDp = 800)
@Composable
private fun TermsAgreementReagreementPreview() {
    TermsAgreementScreenPreview(
        themeMode = ThemeMode.Light,
        uiState = TermsAgreementUiState(
            terms = listOf(
                TermAgreementItem(TermId.Service, "서비스 이용약관", true, version = "2026-09-30"),
                TermAgreementItem(TermId.Privacy, "개인정보 처리방침", true,
                    isAgreed = true, isReadOnly = true, version = "2026-08-13"),
            ),
            title = "최신 약관에 동의해 주세요",
            actionLabel = "동의하고 계속하기",
        ),
    )
}

@Preview(name = "약관 확인 중", widthDp = 360, heightDp = 640)
@Composable
private fun TermsAgreementLoadingPreview() {
    TermsAgreementScreenPreview(
        ThemeMode.Dark,
        TermsAgreementUiState(terms = emptyList(), contentState = TermsAgreementContentState.Loading),
    )
}

@Preview(name = "약관 조회 오류", widthDp = 360, heightDp = 640)
@Composable
private fun TermsAgreementErrorPreview() {
    RingoutTheme {
        TermsAgreementScreen(
            uiState = TermsAgreementUiState(
                terms = emptyList(),
                title = "최신 약관에 동의해 주세요",
                actionLabel = "다시 확인하기",
                contentState = TermsAgreementContentState.Error,
            ),
            onAllAgreementChange = {},
            onTermAgreementChange = { _, _ -> },
            onTermDetailClick = {},
            onStartClick = {},
            errorMessage = "약관 동의 상태를 확인하지 못했어요. 다시 시도해 주세요.",
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
