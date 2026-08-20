package com.joon.ringout.presentation.destination

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.joon.ringout.RingoutTheme
import com.joon.ringout.analytics.DestinationSelectionSource
import com.joon.ringout.domain.destination.SavedDestination
import com.joon.ringout.presentation.destination.components.DestinationManagementDialog
import com.joon.ringout.presentation.destination.components.DestinationNicknameDialog
import com.joon.ringout.presentation.destination.components.DestinationShortcutRow
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

@Composable
fun DestinationMapScreen(
    initialSelection: DestinationSelection,
    requestCurrentLocationOnStart: Boolean = false,
    isAuthenticated: Boolean = false,
    onEntered: () -> Unit = {},
    onBackClick: () -> Unit,
    onConfirmClick: (SavedDestination) -> Unit,
    onSavedDestinationConfirmClick: (SavedDestination) -> Unit,
    savedDestinations: List<SavedDestination> = emptyList(),
    onSavedDestinationRename: (Long, String) -> Unit = { _, _ -> },
    onSavedDestinationDeleteClick: (Long) -> Unit = {},
    onSavedDestinationSelected: (DestinationSelectionSource) -> Unit = {},
    isSaveInProgress: Boolean = false,
    isDestinationActionEnabled: Boolean = true,
    modifier: Modifier = Modifier,
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
    var isLocatingCurrentLocation by remember(initialSelection, requestCurrentLocationOnStart) {
        mutableStateOf(requestCurrentLocationOnStart)
    }
    var currentLocationError by remember { mutableStateOf<String?>(null) }
    var pendingSelection by remember { mutableStateOf<DestinationSelection?>(null) }
    var isDestinationManagementOpen by remember { mutableStateOf(false) }
    var nicknameEditingDestination by remember { mutableStateOf<SavedDestination?>(null) }
    var selectedSavedDestination by remember { mutableStateOf<SavedDestination?>(null) }

    LaunchedEffect(isAuthenticated) {
        if (isAuthenticated) onEntered()
    }

    LaunchedEffect(isDestinationActionEnabled) {
        if (!isDestinationActionEnabled) {
            pendingSelection = null
            nicknameEditingDestination = null
            isDestinationManagementOpen = false
        }
    }

    PlatformBackHandler(onBack = {
        when {
            nicknameEditingDestination != null -> nicknameEditingDestination = null
            pendingSelection != null -> pendingSelection = null
            isDestinationManagementOpen -> isDestinationManagementOpen = false
            isSearchOpen -> isSearchOpen = false
            else -> onBackClick()
        }
    })

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

    LaunchedEffect(savedDestinations) {
        val selectedId = selectedSavedDestination?.id ?: return@LaunchedEffect
        val refreshedDestination = savedDestinations.firstOrNull { it.id == selectedId }
        if (refreshedDestination == null) {
            selectedSavedDestination = null
        } else if (refreshedDestination != selectedSavedDestination) {
            selectedSavedDestination = refreshedDestination
            if (
                selection.hasSameCoordinates(
                    latitude = refreshedDestination.latitude,
                    longitude = refreshedDestination.longitude,
                )
            ) {
                selection = refreshedDestination.toDestinationSelection()
            }
        }
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
            savedDestinations = savedDestinations,
            isLocatingCurrentLocation = isLocatingCurrentLocation,
            isSaveInProgress = isSaveInProgress,
            isDestinationActionEnabled = isDestinationActionEnabled,
            currentLocationError = currentLocationError,
            onSearchQueryChange = {
                searchQuery = it
                searchError = null
            },
            onSearchClick = { isSearchOpen = true },
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
                selectedSavedDestination = null
                mapError = null
                currentLocationError = null
                isSearchOpen = false
                searchResults = emptyList()
                searchError = null
            },
            onCurrentLocationClick = {
                selectedSavedDestination = null
                currentLocationError = null
                isLocatingCurrentLocation = true
                currentLocationRequestId += 1
            },
            onDestinationManagementClick = {
                isSearchOpen = false
                isDestinationManagementOpen = true
            },
            onSavedDestinationClick = { savedDestination, source ->
                if (isDestinationActionEnabled) {
                    val savedSelection = savedDestination.toDestinationSelection()
                    selection = savedSelection
                    cameraTarget = savedSelection
                    selectedSavedDestination = savedDestination
                    onSavedDestinationSelected(source)
                    mapError = null
                    currentLocationError = null
                    isSearchOpen = false
                    searchResults = emptyList()
                    searchError = null
                }
            },
            onConfirmClick = {
                if (isDestinationActionEnabled) {
                    currentLocationCancellationId += 1
                    val savedDestination = savedDestinations.findAtLocation(selection)
                    if (savedDestination == null) {
                        pendingSelection = selection
                    } else {
                        onSavedDestinationConfirmClick(savedDestination)
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
            mapContent = { mapModifier ->
                PlatformDestinationMap(
                    initialLatitude = initialSelection.latitude,
                    initialLongitude = initialSelection.longitude,
                    cameraTarget = cameraTarget,
                    currentLocationRequestId = currentLocationRequestId,
                    currentLocationCancellationId = currentLocationCancellationId,
                    onCameraMoveStarted = {
                        currentLocationError = null
                        isCameraMoving = true
                        isResolvingAddress = true
                    },
                    onCameraSettled = { latitude, longitude ->
                        val settledSelection = destinationAtCameraPosition(
                            cameraTarget = cameraTarget,
                            latitude = latitude,
                            longitude = longitude,
                        )
                        val shouldResolveAddress = settledSelection.requiresAddressResolution()
                        selection = settledSelection
                        selectedSavedDestination = selectedSavedDestination?.takeIf { saved ->
                            saved.toDestinationSelection().hasSameCoordinates(
                                latitude = latitude,
                                longitude = longitude,
                            )
                        }
                        cameraTarget = null
                        isCameraMoving = false
                        isResolvingAddress = shouldResolveAddress
                    },
                    onAddressResolved = { latitude, longitude, placeName, address ->
                        if (
                            shouldApplyResolvedAddress(
                                isResolvingAddress = isResolvingAddress,
                                selection = selection,
                                cameraTarget = cameraTarget,
                                latitude = latitude,
                                longitude = longitude,
                            )
                        ) {
                            selection = selection.withResolvedAddress(
                                latitude = latitude,
                                longitude = longitude,
                                placeName = placeName,
                                address = address,
                            )
                            pendingSelection = pendingSelection?.withResolvedAddress(
                                latitude = latitude,
                                longitude = longitude,
                                placeName = placeName,
                                address = address,
                            )
                            isResolvingAddress = false
                        }
                    },
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

        if (isDestinationManagementOpen) {
            DestinationManagementDialog(
                destinations = savedDestinations,
                onDismissRequest = { isDestinationManagementOpen = false },
                onEditClick = { savedDestination ->
                    nicknameEditingDestination = savedDestination
                },
                onDeleteClick = { savedDestination ->
                    if (selectedSavedDestination?.id == savedDestination.id) {
                        selectedSavedDestination = null
                    }
                    onSavedDestinationDeleteClick(savedDestination.id)
                },
                modifier = Modifier.zIndex(2f),
            )
        }

        nicknameEditingDestination?.let { savedDestination ->
            DestinationNicknameDialog(
                address = savedDestination.address,
                initialNickname = savedDestination.name,
                onDismissRequest = { nicknameEditingDestination = null },
                onSave = { nickname ->
                    nicknameEditingDestination = null
                    onSavedDestinationRename(savedDestination.id, nickname)
                },
                modifier = Modifier.zIndex(3f),
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
private fun DestinationMapLayout(
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
    savedDestinations: List<SavedDestination>,
    isLocatingCurrentLocation: Boolean,
    isSaveInProgress: Boolean,
    isDestinationActionEnabled: Boolean,
    currentLocationError: String?,
    onSearchQueryChange: (String) -> Unit,
    onSearchClick: () -> Unit,
    onSearchClose: () -> Unit,
    onSearchSubmit: () -> Unit,
    onSearchResultClick: (DestinationSelection) -> Unit,
    onCurrentLocationClick: () -> Unit,
    onDestinationManagementClick: () -> Unit,
    onSavedDestinationClick: (SavedDestination, DestinationSelectionSource) -> Unit,
    onConfirmClick: () -> Unit,
    modifier: Modifier = Modifier,
    mapContent: @Composable (Modifier) -> Unit,
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

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(top = 16.dp),
        ) {
            FloatingMapHeader(
                onBackClick = onBackClick,
                onSearchClick = onSearchClick,
                modifier = Modifier.padding(horizontal = 10.5.dp),
            )
            if (!isSearchOpen) {
                DestinationShortcutRow(
                    destinations = savedDestinations,
                    onManagementClick = onDestinationManagementClick,
                    onDestinationClick = { savedDestination ->
                        onSavedDestinationClick(
                            savedDestination,
                            DestinationSelectionSource.MapShortcut,
                        )
                    },
                    modifier = Modifier.padding(
                        start = 3.dp,
                        top = 10.dp,
                        end = 17.dp,
                    ),
                    enabled = isDestinationActionEnabled,
                )
            }
        }

        if (isSearchOpen) {
            DestinationSearchPanel(
                query = searchQuery,
                results = searchResults,
                isSearching = isSearching,
                error = searchError,
                onQueryChange = onSearchQueryChange,
                onClose = onSearchClose,
                onSubmit = onSearchSubmit,
                onResultClick = onSearchResultClick,
                onDestinationManagementClick = onDestinationManagementClick,
                savedDestinations = savedDestinations,
                onSavedDestinationClick = { savedDestination ->
                    onSavedDestinationClick(
                        savedDestination,
                        DestinationSelectionSource.SearchShortcut,
                    )
                },
                isDestinationActionEnabled = isDestinationActionEnabled,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .zIndex(1f)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            )
        }

        if (mapError == null) {
            AddressBubble(
                title = when {
                    isCameraMoving -> "위치를 선택하는 중..."
                    isResolvingAddress -> "주소를 찾는 중..."
                    else -> selection.name
                },
                address = when {
                    isCameraMoving -> "지도를 움직여 위치를 선택하세요"
                    else -> selection.address
                },
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = (-110).dp),
            )
            CenterDestinationPin(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = (-29).dp),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 18.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            currentLocationError?.let {
                CurrentLocationErrorMessage(message = it)
            }
            CurrentLocationButton(
                enabled = canConfirmDestination(
                    isCameraMoving = isCameraMoving,
                    isLocatingCurrentLocation = isLocatingCurrentLocation,
                    mapError = mapError,
                    isSaveInProgress = isSaveInProgress,
                ),
                isLoading = isLocatingCurrentLocation,
                onClick = onCurrentLocationClick,
            )
            ConfirmDestinationButton(
                enabled = isDestinationActionEnabled &&
                    selection.isConfiguredDestination() &&
                    currentLocationError == null &&
                    canConfirmDestination(
                        isCameraMoving = isCameraMoving,
                        isLocatingCurrentLocation = isLocatingCurrentLocation,
                        mapError = mapError,
                        isSaveInProgress = isSaveInProgress,
                    ),
                isSaving = isSaveInProgress,
                onClick = onConfirmClick,
            )
        }
    }
}

@Composable
private fun FloatingMapHeader(
    onBackClick: () -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(15.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(62.dp)
            .background(Color.White, shape)
            .border(1.dp, HeaderBorder, shape)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clickable(
                    role = Role.Button,
                    onClickLabel = "이전 화면으로 이동",
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
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(MaterialTheme.colorScheme.primary, shape)
                .clickable(
                    role = Role.Button,
                    onClickLabel = "목적지 검색 열기",
                    onClick = onSearchClick,
                )
                .semantics { contentDescription = "검색" },
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
private fun DestinationSearchPanel(
    query: String,
    results: List<DestinationSelection>,
    isSearching: Boolean,
    error: String?,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    onSubmit: () -> Unit,
    onResultClick: (DestinationSelection) -> Unit,
    onDestinationManagementClick: () -> Unit,
    savedDestinations: List<SavedDestination>,
    onSavedDestinationClick: (SavedDestination) -> Unit,
    isDestinationActionEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(14.dp, RoundedCornerShape(24.dp))
            .background(Color.White, RoundedCornerShape(24.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .background(Pale, RoundedCornerShape(18.dp))
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                BackIcon()
            }
            TextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .focusRequester(focusRequester),
                placeholder = {
                    Text(
                        text = "장소 또는 주소 검색",
                        color = SecondaryText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Pale,
                    unfocusedContainerColor = Pale,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = PrimaryText,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .background(Orange, RoundedCornerShape(18.dp))
                    .clickable(onClick = onSubmit),
                contentAlignment = Alignment.Center,
            ) {
                SearchIcon()
            }
        }

        DestinationShortcutRow(
            destinations = savedDestinations,
            onManagementClick = onDestinationManagementClick,
            onDestinationClick = onSavedDestinationClick,
            enabled = isDestinationActionEnabled,
        )

        if (isSearching || error != null || results.isNotEmpty()) {
            when {
                isSearching -> SearchStatusText("검색 중...")
                error != null -> SearchStatusText(error)
                else -> LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
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
private fun AddressBubble(
    title: String,
    address: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .padding(horizontal = 32.dp)
            .shadow(12.dp, RoundedCornerShape(18.dp))
            .background(Color.White, RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MiniPinIcon()
        Column(
            modifier = Modifier.weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                color = PrimaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                ),
            )
            Text(
                text = address,
                color = SecondaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            )
        }
    }
}

@Composable
private fun CenterDestinationPin(modifier: Modifier = Modifier) {
    Canvas(modifier.size(54.dp, 80.dp)) {
        drawOval(
            color = Color(0x33000000),
            topLeft = Offset(size.width * .17f, size.height * .84f),
            size = Size(size.width * .66f, size.height * .12f),
        )

        val pin = Path().apply {
            moveTo(size.width / 2f, size.height * .86f)
            cubicTo(
                size.width * .42f,
                size.height * .72f,
                size.width * .12f,
                size.height * .52f,
                size.width * .12f,
                size.height * .32f,
            )
            cubicTo(
                size.width * .12f,
                size.height * .12f,
                size.width * .29f,
                size.height * .04f,
                size.width / 2f,
                size.height * .04f,
            )
            cubicTo(
                size.width * .71f,
                size.height * .04f,
                size.width * .88f,
                size.height * .12f,
                size.width * .88f,
                size.height * .32f,
            )
            cubicTo(
                size.width * .88f,
                size.height * .52f,
                size.width * .58f,
                size.height * .72f,
                size.width / 2f,
                size.height * .86f,
            )
            close()
        }
        drawPath(pin, Orange)
        drawPath(pin, Color.White, style = Stroke(width = 3.dp.toPx()))
        drawCircle(
            color = Color.White,
            radius = size.width * .16f,
            center = Offset(size.width / 2f, size.height * .3f),
        )
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
private fun BackIcon() = Canvas(Modifier.size(22.dp)) {
    val stroke = 1.8.dp.toPx()
    drawLine(PrimaryText, Offset(size.width * .65f, size.height * .2f), Offset(size.width * .35f, size.height * .5f), stroke, StrokeCap.Round)
    drawLine(PrimaryText, Offset(size.width * .35f, size.height * .5f), Offset(size.width * .65f, size.height * .8f), stroke, StrokeCap.Round)
}

@Composable
private fun SearchIcon() = Canvas(Modifier.size(22.dp)) {
    val stroke = 1.8.dp.toPx()
    drawCircle(Color.White, size.minDimension * .27f, Offset(size.width * .43f, size.height * .43f), style = Stroke(stroke))
    drawLine(Color.White, Offset(size.width * .63f, size.height * .63f), Offset(size.width * .82f, size.height * .82f), stroke, StrokeCap.Round)
}

@Composable
private fun MiniPinIcon() = Canvas(Modifier.size(22.dp)) {
    val stroke = 1.7.dp.toPx()
    drawCircle(Orange, size.minDimension * .22f, Offset(size.width / 2f, size.height * .4f), style = Stroke(stroke))
    drawLine(Orange, Offset(size.width / 2f, size.height * .62f), Offset(size.width / 2f, size.height * .88f), stroke, StrokeCap.Round)
}

private val PrimaryText = Color(0xFF161A17)
private val SecondaryText = Color(0xFF6E756F)
private val Pale = Color(0xFFF5F6F2)
private val MapFallback = Color(0xFFDCE8DF)
private val Orange = Color(0xFFFF6B2C)
private val DisabledButton = Color(0xFFB8BDB7)
private val HeaderBorder = Color(0xFFD0D0D0)
private val CurrentLocationIconColor = Color(0xFFF5F5F6)
private const val DisabledControlAlpha = 0.45f
private const val InitialCurrentLocationRequestId = 1

private val DestinationPreviewSavedDestinations = listOf(
    SavedDestination(
        id = 1L,
        name = "런닝",
        address = "서울 중구 세종대로 110",
        latitude = 37.5665851,
        longitude = 126.9782038,
    ),
    SavedDestination(
        id = 2L,
        name = "공부하러가야지",
        address = "서울 서초구 반포대로 201",
        latitude = 37.5001,
        longitude = 127.0001,
    ),
    SavedDestination(
        id = 3L,
        name = "헬스장",
        address = "서울 강남구 테헤란로 123",
        latitude = 37.5012,
        longitude = 127.0396,
    ),
)

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
            savedDestinations = DestinationPreviewSavedDestinations,
            isLocatingCurrentLocation = false,
            isSaveInProgress = false,
            isDestinationActionEnabled = true,
            currentLocationError = null,
            onSearchQueryChange = {},
            onSearchClick = {},
            onSearchClose = {},
            onSearchSubmit = {},
            onSearchResultClick = {},
            onCurrentLocationClick = {},
            onDestinationManagementClick = {},
            onSavedDestinationClick = { _, _ -> },
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
                onBackClick = {},
                onSearchClick = {},
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
private fun DestinationSearchPanelPreview() {
    RingoutTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MapFallback),
        ) {
            DestinationSearchPanel(
                query = "강남역",
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
                onQueryChange = {},
                onClose = {},
                onSubmit = {},
                onResultClick = {},
                onDestinationManagementClick = {},
                savedDestinations = DestinationPreviewSavedDestinations,
                onSavedDestinationClick = {},
                isDestinationActionEnabled = true,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}
