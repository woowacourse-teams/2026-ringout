package com.joon.ringout.presentation.destination

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.joon.ringout.RingoutTheme
import com.joon.ringout.presentation.destination.components.DestinationNicknameDialog
import kotlin.math.abs

data class DestinationSelection(
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
)

val DefaultDestinationSelection = DestinationSelection(
    name = "집 앞에 수원천",
    address = "경기 수원시 팔달구 정조로 893",
    latitude = 37.2875205998,
    longitude = 127.0146478075,
)

@Composable
fun DestinationMapScreen(
    initialSelection: DestinationSelection,
    onBackClick: () -> Unit,
    onConfirmClick: (DestinationSelection) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selection by remember(initialSelection) { mutableStateOf(initialSelection) }
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
    var pendingSelection by remember { mutableStateOf<DestinationSelection?>(null) }

    PlatformBackHandler(onBack = {
        when {
            pendingSelection != null -> pendingSelection = null
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

    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        DestinationMapLayout(
            selection = selection,
            isResolvingAddress = isResolvingAddress,
            mapError = mapError,
            onBackClick = onBackClick,
            isSearchOpen = isSearchOpen,
            searchQuery = searchQuery,
            searchResults = searchResults,
            isSearching = isSearching,
            searchError = searchError,
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
                isSearchOpen = false
                searchResults = emptyList()
                searchError = null
            },
            onConfirmClick = { pendingSelection = selection },
            modifier = Modifier.fillMaxSize(),
            mapContent = { mapModifier ->
                PlatformDestinationMap(
                    initialLatitude = initialSelection.latitude,
                    initialLongitude = initialSelection.longitude,
                    cameraTarget = cameraTarget,
                    onCameraMoveStarted = { isResolvingAddress = true },
                    onCameraIdle = { latitude, longitude, placeName, address ->
                        val searchedTarget = cameraTarget?.takeIf {
                            abs(it.latitude - latitude) < 0.00001 &&
                                abs(it.longitude - longitude) < 0.00001
                        }
                        selection = searchedTarget ?: DestinationSelection(
                            name = placeName?.takeIf(String::isNotBlank) ?: "선택한 위치",
                            address = address?.takeIf(String::isNotBlank)
                                ?: "주소를 확인할 수 없는 위치",
                            latitude = latitude,
                            longitude = longitude,
                        )
                        cameraTarget = null
                        isResolvingAddress = false
                    },
                    onMapError = { error ->
                        mapError = error
                        isResolvingAddress = false
                    },
                    modifier = mapModifier,
                )
            },
        )

        pendingSelection?.let { selectedDestination ->
            DestinationNicknameDialog(
                address = selectedDestination.address,
                onDismissRequest = { pendingSelection = null },
                onSave = { nickname ->
                    pendingSelection = null
                    onConfirmClick(
                        selectedDestination.copy(name = nickname),
                    )
                },
                modifier = Modifier.zIndex(2f),
            )
        }
    }
}

@Composable
private fun DestinationMapLayout(
    selection: DestinationSelection,
    isResolvingAddress: Boolean,
    mapError: String?,
    onBackClick: () -> Unit,
    isSearchOpen: Boolean,
    searchQuery: String,
    searchResults: List<DestinationSelection>,
    isSearching: Boolean,
    searchError: String?,
    onSearchQueryChange: (String) -> Unit,
    onSearchClick: () -> Unit,
    onSearchClose: () -> Unit,
    onSearchSubmit: () -> Unit,
    onSearchResultClick: (DestinationSelection) -> Unit,
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
                        text = "카카오 플랫폼 설정을 확인해 주세요.",
                        color = SecondaryText,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    )
                }
            }
        }

        FloatingMapHeader(
            onBackClick = onBackClick,
            onSearchClick = onSearchClick,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 16.dp),
        )

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
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .zIndex(1f)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            )
        }

        if (mapError == null) {
            AddressBubble(
                title = if (isResolvingAddress) "주소를 찾는 중..." else selection.name,
                address = if (isResolvingAddress) "지도를 움직여 위치를 선택하세요" else selection.address,
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

        ConfirmDestinationButton(
            enabled = !isResolvingAddress && mapError == null,
            onClick = onConfirmClick,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 18.dp),
        )
    }
}

@Composable
private fun FloatingMapHeader(
    onBackClick: () -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(12.dp, RoundedCornerShape(24.dp))
            .background(Color(0xF2FFFFFF), RoundedCornerShape(24.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .background(Pale, RoundedCornerShape(18.dp))
                .clickable(onClick = onBackClick),
            contentAlignment = Alignment.Center,
        ) {
            BackIcon()
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "목적지 설정",
                color = PrimaryText,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                ),
            )
            Text(
                text = "지도를 움직여 도착할 위치를 선택하세요",
                color = SecondaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            )
        }
        Box(
            modifier = Modifier
                .size(50.dp)
                .background(Orange, RoundedCornerShape(18.dp))
                .clickable(onClick = onSearchClick),
            contentAlignment = Alignment.Center,
        ) {
            SearchIcon()
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

        if (isSearching || error != null || results.isNotEmpty()) {
            when {
                isSearching -> SearchStatusText("검색 중...")
                error != null -> SearchStatusText(error)
                else -> LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                ) {
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
private fun ConfirmDestinationButton(
    enabled: Boolean,
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
            text = "이 위치를 목적지로 설정",
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

@Preview
@Composable
private fun DestinationMapScreenPreview() {
    RingoutTheme {
        DestinationMapLayout(
            selection = DefaultDestinationSelection,
            isResolvingAddress = false,
            mapError = null,
            onBackClick = {},
            isSearchOpen = false,
            searchQuery = "",
            searchResults = emptyList(),
            isSearching = false,
            searchError = null,
            onSearchQueryChange = {},
            onSearchClick = {},
            onSearchClose = {},
            onSearchSubmit = {},
            onSearchResultClick = {},
            onConfirmClick = {},
            mapContent = { mapModifier -> Box(mapModifier.background(MapFallback)) },
        )
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
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}
