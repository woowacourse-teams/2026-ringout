package com.joon.ringout.presentation.destination

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.joon.ringout.RingoutTheme
import com.joon.ringout.presentation.common.TextInputLengthRule
import com.joon.ringout.presentation.destination.component.DestinationSaveDialog
import com.joon.ringout.domain.destination.SavedDestination
import com.joon.ringout.presentation.destination.component.DestinationSearchScrollbar
import com.joon.ringout.presentation.destination.component.DestinationSearchScrollbarTouchWidth
import com.joon.ringout.presentation.destination.component.DestinationMapMarker
import com.joon.ringout.presentation.destination.component.DestinationNicknameDialog
import org.jetbrains.compose.resources.painterResource

data class DestinationSelection(
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
)

val DefaultDestinationSelection = DestinationSelection(
    name = "서울",
    address = "서울특별시 중구 세종대로 110",
    latitude = 37.5665851,
    longitude = 126.9782038,
)

private val DestinationSearchInputLengthRule = TextInputLengthRule()

@Composable
fun DestinationMapScreen(
    initialSelection: DestinationSelection,
    requestCurrentLocationOnStart: Boolean = false,
    onCurrentLocationResolved: (Double, Double) -> Unit = { _, _ -> },
    isAuthenticated: Boolean = false,
    onEntered: () -> Unit = {},
    onBackClick: () -> Unit,
    onConfirmClick: (SavedDestination) -> Unit,
    onConfirmWithoutSave: (DestinationSelection) -> Unit,
    isSaveInProgress: Boolean = false,
    isDestinationActionEnabled: Boolean = true,
    isBackHandlerEnabled: Boolean = true,
    modifier: Modifier = Modifier,
    bottomContent: (@Composable (DestinationSelection, Boolean) -> Unit)? = null,
    mapContent: (@Composable (Modifier) -> Unit)? = null,
) {
    var selection by remember(initialSelection) { mutableStateOf(initialSelection) }
    var isCameraMoving by remember { mutableStateOf(false) }
    var isResolvingAddress by remember { mutableStateOf(false) }
    var mapError by remember { mutableStateOf<String?>(null) }
    var isSearchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var submittedQuery by remember { mutableStateOf<String?>(null) }
    var searchRequestId by remember { mutableStateOf(0) }
    var searchResults by remember { mutableStateOf(emptyList<DestinationSelection>()) }
    var isSearching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var cameraTarget by remember { mutableStateOf<DestinationSelection?>(null) }
    var currentLocationRequestId by remember(initialSelection, requestCurrentLocationOnStart) {
        mutableStateOf(if (requestCurrentLocationOnStart) InitialCurrentLocationRequestId else 0)
    }
    var currentLocationCancellationId by remember { mutableStateOf(0) }
    var addressResolutionRequestId by remember { mutableStateOf(0) }
    var isLocatingCurrentLocation by remember(initialSelection, requestCurrentLocationOnStart) {
        mutableStateOf(requestCurrentLocationOnStart)
    }
    var currentLocationError by remember { mutableStateOf<String?>(null) }
    var pendingSelection by remember { mutableStateOf<DestinationSelection?>(null) }
    var pendingSaveDecision by remember { mutableStateOf<DestinationSelection?>(null) }

    LaunchedEffect(isAuthenticated) {
        if (isAuthenticated) onEntered()
    }

    LaunchedEffect(isDestinationActionEnabled) {
        if (!isDestinationActionEnabled) {
            pendingSelection = null
            pendingSaveDecision = null
        }
    }

    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = isBackHandlerEnabled && (
            pendingSelection != null || pendingSaveDecision != null || isSearchOpen
            ),
        onBackCompleted = {
            when {
                pendingSelection != null -> pendingSelection = null
                pendingSaveDecision != null -> pendingSaveDecision = null
                isSearchOpen -> isSearchOpen = false
                else -> onBackClick()
            }
        },
    )

    if (isSearchOpen && submittedQuery != null) {
        PlatformDestinationSearchEffect(
            query = submittedQuery,
            requestId = searchRequestId,
            onLoadingChange = { isSearching = it },
            onResults = {
                searchResults = it
                searchError = if (it.isEmpty()) "검색 결과가 없습니다." else null
            },
            onError = {
                searchResults = emptyList()
                searchError = it
            },
        )
    }

    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        DestinationMapLayout(
            selection = selection,
            isCameraMoving = isCameraMoving,
            isResolvingAddress = isResolvingAddress,
            mapError = mapError,
            onBackClick = onBackClick,
            isSearchOpen = isSearchOpen,
            searchQuery = searchQuery,
            searchResults = searchResults,
            isSearching = isSearching,
            searchError = searchError,
            isLocatingCurrentLocation = isLocatingCurrentLocation,
            isSaveInProgress = isSaveInProgress,
            isDestinationActionEnabled = isDestinationActionEnabled,
            currentLocationError = currentLocationError,
            onRetryAddressResolution = {
                if (!isCameraMoving && !isLocatingCurrentLocation && !isSaveInProgress) {
                    selection = selection.copy(address = ResolvingDestinationAddress)
                    isResolvingAddress = true
                    addressResolutionRequestId += 1
                }
            },
            onSearchQueryChange = {
                searchQuery = it
                searchError = null
            },
            onSearchClick = {
                searchQuery = ""
                submittedQuery = null
                searchResults = emptyList()
                isSearching = false
                searchError = null
                isSearchOpen = true
            },
            onSearchClose = { isSearchOpen = false },
            onSearchSubmit = {
                val query = searchQuery.trim()
                if (query.isNotEmpty()) {
                    searchError = null
                    submittedQuery = query
                    searchRequestId += 1
                }
            },
            onSearchResultClick = { result ->
                selection = result
                cameraTarget = result
                mapError = null
                currentLocationError = null
                isSearchOpen = false
                searchResults = emptyList()
                searchError = null
            },
            onCurrentLocationClick = {
                currentLocationError = null
                isLocatingCurrentLocation = true
                currentLocationRequestId += 1
            },
            onConfirmClick = {
                if (isDestinationActionEnabled) {
                    currentLocationCancellationId += 1
                    pendingSaveDecision = selection
                }
            },
            modifier = Modifier.fillMaxSize(),
            bottomContent = bottomContent,
            mapContent = { mapModifier ->
                if (mapContent != null) mapContent(mapModifier)
                else if (LocalInspectionMode.current) Box(mapModifier)
                else PlatformDestinationMap(
                    initialLatitude = initialSelection.latitude,
                    initialLongitude = initialSelection.longitude,
                    cameraTarget = cameraTarget,
                    currentLocationRequestId = currentLocationRequestId,
                    currentLocationCancellationId = currentLocationCancellationId,
                    addressResolutionRequestId = addressResolutionRequestId,
                    addressResolutionLatitude = selection.latitude,
                    addressResolutionLongitude = selection.longitude,
                    onCameraMoveStarted = {
                        currentLocationError = null
                        isCameraMoving = true
                        isResolvingAddress = true
                    },
                    onCameraSettled = { latitude, longitude ->
                        val settledSelection = destinationAtCameraPosition(
                            cameraTarget = cameraTarget ?: selection,
                            latitude = latitude,
                            longitude = longitude,
                        )
                        val shouldResolveAddress = settledSelection.requiresAddressResolution()
                        selection = settledSelection
                        cameraTarget = null
                        isCameraMoving = false
                        isResolvingAddress = shouldResolveAddress
                    },
                    onAddressResolved = { latitude, longitude, _, address ->
                        if (
                            shouldApplyResolvedAddress(
                                isResolvingAddress = isResolvingAddress,
                                selection = selection,
                                cameraTarget = cameraTarget,
                                latitude = latitude,
                                longitude = longitude,
                            )
                        ) {
                            val resolvedSelection = selection.withResolvedAddress(
                                latitude = latitude,
                                longitude = longitude,
                                address = address,
                            )
                            selection = resolvedSelection
                            pendingSelection = pendingSelection?.withResolvedAddress(
                                latitude = latitude,
                                longitude = longitude,
                                address = address,
                            )
                            pendingSaveDecision = pendingSaveDecision?.withResolvedAddress(
                                latitude = latitude,
                                longitude = longitude,
                                address = address,
                            )
                            isResolvingAddress = false
                        }
                    },
                    onCurrentLocationResolved = onCurrentLocationResolved,
                    onCurrentLocationLoadingChange = {
                        isLocatingCurrentLocation = it
                    },
                    onCurrentLocationError = { error ->
                        currentLocationError = error
                        isCameraMoving = false
                        isLocatingCurrentLocation = false
                    },
                    onMapError = { error ->
                        mapError = error
                        isCameraMoving = false
                        isResolvingAddress = false
                        isLocatingCurrentLocation = false
                    },
                    modifier = mapModifier,
                )
            },
        )

        pendingSaveDecision?.let { selectedDestination ->
            DestinationSaveDialog(
                onDismissRequest = { pendingSaveDecision = null },
                onSkipSave = {
                    if (pendingSaveDecision != null && isDestinationActionEnabled) {
                        pendingSaveDecision = null
                        onConfirmWithoutSave(selectedDestination)
                    }
                },
                onSave = {
                    if (pendingSaveDecision != null && isDestinationActionEnabled) {
                        pendingSaveDecision = null
                        pendingSelection = selectedDestination
                    }
                },
            )
        }

        pendingSelection?.let { selectedDestination ->
            DestinationNicknameDialog(
                address = selectedDestination.address,
                onDismissRequest = { pendingSelection = null },
                onSave = { nickname ->
                    pendingSelection = null
                    if (isDestinationActionEnabled) {
                        onConfirmClick(
                            selectedDestination
                                .withNicknameForSave(nickname)
                                .toSavedDestination(),
                        )
                    }
                },
                modifier = Modifier.zIndex(2f),
            )
        }
    }
}

@Composable
internal fun DestinationMapLayout(
    selection: DestinationSelection,
    isCameraMoving: Boolean,
    isResolvingAddress: Boolean,
    mapError: String?,
    onBackClick: () -> Unit,
    isSearchOpen: Boolean,
    searchQuery: String,
    searchResults: List<DestinationSelection>,
    isSearching: Boolean,
    searchError: String?,
    isLocatingCurrentLocation: Boolean,
    isSaveInProgress: Boolean,
    isDestinationActionEnabled: Boolean,
    currentLocationError: String?,
    onRetryAddressResolution: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchClick: () -> Unit,
    onSearchClose: () -> Unit,
    onSearchSubmit: () -> Unit,
    onSearchResultClick: (DestinationSelection) -> Unit,
    onCurrentLocationClick: () -> Unit,
    onConfirmClick: () -> Unit,
    modifier: Modifier = Modifier,
    mapContent: @Composable (Modifier) -> Unit,
    bottomContent: (@Composable (DestinationSelection, Boolean) -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MapFallback),
    ) {
        mapContent(Modifier.fillMaxSize())

        if (mapError != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xCCEAF0EA)),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 32.dp)
                        .shadow(12.dp, RoundedCornerShape(18.dp))
                        .background(Color.White, RoundedCornerShape(18.dp))
                        .padding(horizontal = 22.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "지도를 불러오지 못했습니다.",
                        color = PrimaryText,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                        ),
                    )
                    Text(
                        text = "지도 연결 상태와 API 설정을 확인해 주세요.",
                        color = SecondaryText,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    )
                }
            }
        }

        if (isSearchOpen) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable(
                        interactionSource = null,
                        indication = null,
                        onClickLabel = "검색 닫기",
                        onClick = onSearchClose,
                    )
                    .semantics { contentDescription = "검색창 바깥 영역" },
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .zIndex(1f)
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(top = 16.dp),
        ) {
            FloatingMapHeader(
                isSearchOpen = isSearchOpen,
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                onBackClick = if (isSearchOpen) onSearchClose else onBackClick,
                onSearchClick = onSearchClick,
                onSubmit = onSearchSubmit,
                modifier = Modifier.padding(horizontal = 10.5.dp),
            )
            if (isSearchOpen && (isSearching || searchError != null || searchResults.isNotEmpty())) {
                DestinationSearchResultsPanel(
                    results = searchResults,
                    isSearching = isSearching,
                    error = searchError,
                    onResultClick = onSearchResultClick,
                    modifier = Modifier.padding(horizontal = 10.5.dp, vertical = 8.dp),
                )
            }
        }

        if (mapError == null) {
            val address = when {
                isCameraMoving -> "지도를 움직여 위치를 선택하세요"
                isResolvingAddress -> ResolvingDestinationAddress
                else -> selection.address.ifBlank { UnavailableDestinationAddress }
            }
            DestinationMapMarker(
                address = address,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .then(if (bottomContent == null) Modifier.navigationBarsPadding() else Modifier)
                .padding(
                    start = if (bottomContent == null) 24.dp else 0.dp,
                    end = if (bottomContent == null) 24.dp else 0.dp,
                    bottom = if (bottomContent == null) 18.dp else 0.dp,
                ),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(if (bottomContent == null) 12.dp else 4.dp),
        ) {
            currentLocationError?.let {
                CurrentLocationErrorMessage(message = it)
            }
            CurrentLocationButton(
                modifier = if (bottomContent == null) Modifier else Modifier.padding(end = 17.dp),
                enabled = canConfirmDestination(
                    isCameraMoving = isCameraMoving,
                    isLocatingCurrentLocation = isLocatingCurrentLocation,
                    mapError = mapError,
                    isSaveInProgress = isSaveInProgress,
                ),
                isLoading = isLocatingCurrentLocation,
                onClick = onCurrentLocationClick,
            )
            if (bottomContent != null) {
                bottomContent(
                    selection,
                    isDestinationActionEnabled && !isResolvingAddress &&
                        selection.isConfiguredDestination() && currentLocationError == null &&
                        canConfirmDestination(
                            isCameraMoving,
                            isLocatingCurrentLocation,
                            mapError,
                            isSaveInProgress,
                            isAddressAvailable = selection.address.isNotBlank() &&
                                selection.address != UnavailableDestinationAddress &&
                                selection.address != ResolvingDestinationAddress,
                        ),
                )
            } else ConfirmDestinationButton(
                enabled = isDestinationActionEnabled &&
                    selection.isConfiguredDestination() &&
                    currentLocationError == null &&
                    canConfirmDestination(
                        isCameraMoving = isCameraMoving,
                        isLocatingCurrentLocation = isLocatingCurrentLocation,
                        mapError = mapError,
                        isSaveInProgress = isSaveInProgress,
                        isAddressAvailable = selection.address.isNotBlank() &&
                            selection.address != UnavailableDestinationAddress &&
                            selection.address != ResolvingDestinationAddress,
                    ),
                isSaving = isSaveInProgress,
                onClick = onConfirmClick,
            )
        }
    }
}

@Composable
private fun FloatingMapHeader(
    isSearchOpen: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onBackClick: () -> Unit,
    onSearchClick: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(15.dp)
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val isPreview = LocalInspectionMode.current

    LaunchedEffect(isSearchOpen) {
        if (!isPreview) {
            if (isSearchOpen) {
                focusRequester.requestFocus()
            } else {
                focusManager.clearFocus()
                keyboardController?.hide()
            }
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(62.dp)
            .background(Color.White, shape)
            .border(1.dp, HeaderBorder, shape)
            .clip(shape)
            .then(
                if (isSearchOpen) {
                    Modifier.pointerInput(Unit) { detectTapGestures(onTap = {}) }
                } else {
                    Modifier
                        .clickable(
                            role = Role.Button,
                            onClickLabel = "목적지 검색 열기",
                            onClick = onSearchClick,
                        )
                        .semantics { contentDescription = "검색" }
                },
            )
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clickable(
                    interactionSource = null,
                    indication = null,
                    role = Role.Button,
                    onClickLabel = if (isSearchOpen) "검색 닫기" else "이전 화면으로 이동",
                    onClick = onBackClick,
                )
                .semantics { contentDescription = "뒤로가기" },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(DestinationMapBackIconResource),
                contentDescription = null,
                modifier = Modifier.size(44.dp),
            )
        }
        if (isSearchOpen) {
            BasicTextField(
                value = query,
                onValueChange = { proposed ->
                    if (DestinationSearchInputLengthRule.allows(query, proposed)) {
                        onQueryChange(proposed)
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 8.dp)
                    .focusRequester(focusRequester)
                    .semantics { contentDescription = "장소 또는 주소 검색" },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = PrimaryText),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text(
                                text = "장소 또는 주소 검색",
                                color = SecondaryText,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        innerTextField()
                    }
                },
            )
        }
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(MaterialTheme.colorScheme.primary, shape)
                .then(
                    if (isSearchOpen) {
                        Modifier
                            .clickable(
                                interactionSource = null,
                                indication = null,
                                role = Role.Button,
                                onClickLabel = "목적지 검색 실행",
                                onClick = onSubmit,
                            )
                            .semantics { contentDescription = "검색" }
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(DestinationMapSearchIconResource),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun DestinationSearchResultsPanel(
    results: List<DestinationSelection>,
    isSearching: Boolean,
    error: String?,
    onResultClick: (DestinationSelection) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(14.dp, RoundedCornerShape(15.dp))
            .background(Color.White, RoundedCornerShape(15.dp))
            .pointerInput(Unit) { detectTapGestures(onTap = {}) }
            .padding(10.dp),
    ) {
        when {
            isSearching -> SearchStatusText("검색 중...")
            error != null -> SearchStatusText(error)
            else -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp),
            ) {
                val listState = rememberLazyListState()
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(end = DestinationSearchScrollbarTouchWidth),
                ) {
                    item {
                        Text(
                            text = "Powered by Google",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            color = SecondaryText,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    items(results) { result ->
                        SearchResultRow(result = result, onClick = { onResultClick(result) })
                    }
                }
                DestinationSearchScrollbar(
                    state = listState,
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
        }
    }
}

@Composable
private fun SearchStatusText(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        color = SecondaryText,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun SearchResultRow(
    result: DestinationSelection,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MiniPinIcon()
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = result.name,
                color = PrimaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold),
            )
            Text(
                text = result.address,
                color = SecondaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun CurrentLocationErrorMessage(
    message: String,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    Text(
        text = message,
        modifier = modifier
            .shadow(8.dp, shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = .94f), shape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.bodySmall.copy(
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        ),
    )
}

@Composable
private fun CurrentLocationButton(
    enabled: Boolean,
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(54.dp)
            .alpha(if (enabled || isLoading) 1f else DisabledControlAlpha)
            .background(MaterialTheme.colorScheme.primary, CircleShape)
            .semantics {
                contentDescription = "현재 위치로 이동"
                if (isLoading) {
                    stateDescription = "현재 위치 확인 중"
                }
            }
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = "현재 위치로 이동",
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = CurrentLocationIconColor,
                strokeWidth = 2.dp,
            )
        } else {
            Image(
                painter = painterResource(DestinationMapCurrentLocationIconResource),
                contentDescription = null,
                modifier = Modifier.size(30.dp),
            )
        }
    }
}

@Composable
private fun ConfirmDestinationButton(
    enabled: Boolean,
    isSaving: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .shadow(10.dp, RoundedCornerShape(16.dp))
            .background(if (enabled) Orange else DisabledButton, RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (isSaving) "목적지 저장 중..." else "이 위치를 목적지로 설정",
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

@Composable
private fun MiniPinIcon() = Canvas(Modifier.size(22.dp)) {
    val stroke = 1.7.dp.toPx()
    drawCircle(Orange, size.minDimension * .22f, Offset(size.width / 2f, size.height * .4f), style = Stroke(stroke))
    drawLine(Orange, Offset(size.width / 2f, size.height * .62f), Offset(size.width / 2f, size.height * .88f), stroke, StrokeCap.Round)
}

private val PrimaryText = Color(0xFF161A17)
private val SecondaryText = Color(0xFF6E756F)
private val MapFallback = Color(0xFFDCE8DF)
private val Orange = Color(0xFFFF6B2C)
private val DisabledButton = Color(0xFFB8BDB7)
private val HeaderBorder = Color(0xFFD0D0D0)
private val CurrentLocationIconColor = Color(0xFFF5F5F6)
private const val DisabledControlAlpha = 0.45f
private const val InitialCurrentLocationRequestId = 1

@Preview
@Composable
private fun DestinationMapScreenPreview() {
    RingoutTheme {
        DestinationMapLayout(
            selection = DefaultDestinationSelection,
            isCameraMoving = false,
            isResolvingAddress = false,
            mapError = null,
            onBackClick = {},
            isSearchOpen = false,
            searchQuery = "",
            searchResults = emptyList(),
            isSearching = false,
            searchError = null,
            isLocatingCurrentLocation = false,
            isSaveInProgress = false,
            isDestinationActionEnabled = true,
            currentLocationError = null,
            onRetryAddressResolution = {},
            onSearchQueryChange = {},
            onSearchClick = {},
            onSearchClose = {},
            onSearchSubmit = {},
            onSearchResultClick = {},
            onCurrentLocationClick = {},
            onConfirmClick = {},
            mapContent = { mapModifier -> Box(mapModifier.background(MapFallback)) },
        )
    }
}

@Preview
@Composable
private fun DestinationMapScreenSearchPreview() {
    RingoutTheme {
        DestinationMapLayout(
            selection = DefaultDestinationSelection,
            isCameraMoving = false,
            isResolvingAddress = false,
            mapError = null,
            onBackClick = {},
            isSearchOpen = true,
            searchQuery = "서울",
            searchResults = listOf(DefaultDestinationSelection),
            isSearching = false,
            searchError = null,
            isLocatingCurrentLocation = false,
            isSaveInProgress = false,
            isDestinationActionEnabled = true,
            currentLocationError = null,
            onRetryAddressResolution = {},
            onSearchQueryChange = {},
            onSearchClick = {},
            onSearchClose = {},
            onSearchSubmit = {},
            onSearchResultClick = {},
            onCurrentLocationClick = {},
            onConfirmClick = {},
            mapContent = { mapModifier -> Box(mapModifier.background(MapFallback)) },
        )
    }
}

@Preview(widthDp = 402, heightDp = 82)
@Composable
private fun FloatingMapHeaderPreview() {
    RingoutTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MapFallback)
                .padding(horizontal = 10.5.dp, vertical = 10.dp),
        ) {
            FloatingMapHeader(
                isSearchOpen = false,
                query = "",
                onQueryChange = {},
                onBackClick = {},
                onSearchClick = {},
                onSubmit = {},
            )
        }
    }
}

@Preview(widthDp = 402, heightDp = 82)
@Composable
private fun FloatingMapHeaderSearchPreview() {
    RingoutTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MapFallback)
                .padding(horizontal = 10.5.dp, vertical = 10.dp),
        ) {
            FloatingMapHeader(
                isSearchOpen = true,
                query = "강남역",
                onQueryChange = {},
                onBackClick = {},
                onSearchClick = {},
                onSubmit = {},
            )
        }
    }
}

@Preview(widthDp = 86, heightDp = 86)
@Composable
private fun CurrentLocationButtonPreview() {
    RingoutTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MapFallback),
            contentAlignment = Alignment.Center,
        ) {
            CurrentLocationButton(
                enabled = true,
                isLoading = false,
                onClick = {},
            )
        }
    }
}

@Preview
@Composable
private fun DestinationSearchResultsPanelPreview() {
    RingoutTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MapFallback),
        ) {
            DestinationSearchResultsPanel(
                results = listOf(
                    DefaultDestinationSelection,
                    DestinationSelection(
                        name = "강남역",
                        address = "서울 강남구 역삼동 858",
                        latitude = 37.497175,
                        longitude = 127.027926,
                    ),
                ),
                isSearching = false,
                error = null,
                onResultClick = {},
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}
