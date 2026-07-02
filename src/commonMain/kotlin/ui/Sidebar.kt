package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search

private data class SidebarListRow(
    val sectionTitle: String? = null,
    val objectIndex: Int? = null,
    val textureIndex: Int? = null,
    val title: String,
    val subtitle: String,
    val type: String = ""
)

// Тонкий скроллбар-индикатор поверх LazyColumn (правый край, прижат к контенту).
// Не показывается, если весь контент помещается на экран целиком. Бегунок можно
// перетаскивать курсором — реальная зона захвата шире (10dp), чем видимая полоска (4dp).
@Composable
private fun BoxScope.VerticalListScrollbar(listState: LazyListState) {
    val layoutInfo = listState.layoutInfo
    val totalItems = layoutInfo.totalItemsCount
    val visibleItems = layoutInfo.visibleItemsInfo.size
    if (totalItems == 0 || visibleItems == 0 || totalItems <= visibleItems) return

    val thumbFraction = (visibleItems.toFloat() / totalItems.toFloat()).coerceIn(0.06f, 1f)
    val scrollableRange = (totalItems - visibleItems).coerceAtLeast(1)
    val progress = (listState.firstVisibleItemIndex.toFloat() / scrollableRange.toFloat()).coerceIn(0f, 1f)

    val coroutineScope = rememberCoroutineScope()

    BoxWithConstraints(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .fillMaxHeight()
            .padding(vertical = 2.dp)
            .width(10.dp)
    ) {
        val trackHeightPx = constraints.maxHeight.toFloat()
        val trackHeight = maxHeight
        val thumbHeight = trackHeight * thumbFraction
        val thumbOffset = (trackHeight - thumbHeight) * progress
        val thumbHeightPx = trackHeightPx * thumbFraction
        val draggableRangePx = (trackHeightPx - thumbHeightPx).coerceAtLeast(1f)

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxHeight()
                .width(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.Black.copy(alpha = 0.05f))
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = thumbOffset)
                .height(thumbHeight)
                .fillMaxWidth()
                .padding(horizontal = 3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.Black.copy(alpha = 0.22f))
                .pointerInput(totalItems, visibleItems, draggableRangePx) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val deltaFraction = dragAmount.y / draggableRangePx
                        val newIndex = (listState.firstVisibleItemIndex + deltaFraction * scrollableRange)
                            .roundToInt()
                            .coerceIn(0, scrollableRange)
                        coroutineScope.launch { listState.scrollToItem(newIndex) }
                    }
                }
        )
    }
}

@Composable
fun GlassSidebar(
    openedTab: OpenedTab,
    onObjectSelected: (Int) -> Unit,
    onTextureSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var activeBottomTab by remember { mutableStateOf("Objects") }
    var searchQuery by remember { mutableStateOf("") }

    var sortColumn by remember { mutableStateOf("Id") }
    var sortAscending by remember { mutableStateOf(true) }

    val columnWidths = remember { mutableStateListOf(1.2f, 2.4f, 1.3f) }
    var activeSearchColumn by remember { mutableStateOf<String?>(null) }
    var idQuery by remember { mutableStateOf("") }
    var nameQuery by remember { mutableStateOf("") }
    var typeQuery by remember { mutableStateOf("") }

    val objectsListState = rememberLazyListState()
    val texturesListState = rememberLazyListState()

    val normalizedSearch = searchQuery.trim().lowercase()

    LaunchedEffect(activeBottomTab) {
        if (activeBottomTab != "Objects") {
            activeSearchColumn = null
        }
    }

    val filteredAndSortedObjects = remember(openedTab.objects, sortColumn, sortAscending, idQuery, nameQuery, typeQuery, normalizedSearch) {
        openedTab.objects.mapIndexed { index, obj -> index to obj }
            .filter { (_, obj) ->
                val searchText = listOf(obj.id.toString(), obj.name, obj.type).joinToString(" ").lowercase()
                val matchesSearch = normalizedSearch.isEmpty() || searchText.contains(normalizedSearch)
                val matchesId = idQuery.isEmpty() || obj.id.toString().contains(idQuery)
                val matchesName = nameQuery.isEmpty() || obj.name.contains(nameQuery, ignoreCase = true)
                val matchesType = typeQuery.isEmpty() || obj.type.contains(typeQuery, ignoreCase = true)
                matchesSearch && matchesId && matchesName && matchesType
            }
            .sortedWith { (i1, o1), (i2, o2) ->
                val res = when (sortColumn) {
                    "Id" -> o1.id.compareTo(o2.id)
                    "Name" -> o1.name.compareTo(o2.name)
                    else -> o1.type.compareTo(o2.type)
                }
                if (sortAscending) res else -res
            }
    }

    val filteredTextures = remember(openedTab.textures, normalizedSearch) {
        openedTab.textures.filter { tex ->
            val textureText = listOf(tex.index.toString(), tex.format, tex.width.toString(), tex.height.toString(), "${tex.width}x${tex.height}").joinToString(" ").lowercase()
            normalizedSearch.isEmpty() || textureText.contains(normalizedSearch)
        }
    }

    val objectRows = remember(filteredAndSortedObjects) {
        filteredAndSortedObjects.map { (index, obj) ->
            SidebarListRow(
                objectIndex = index,
                title = obj.name.ifEmpty { "${obj.type} ${obj.id}" },
                subtitle = "#${obj.id}",
                type = obj.type
            )
        }
    }

    GlassBox(modifier = modifier, alpha = 0.45f, cornerRadius = 16, contentPadding = 8.dp) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Objects: ${openedTab.objects.size} • Textures: ${openedTab.textures.size} • v${openedTab.containerVersion}",
                    color = Color(0xFF475569),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (activeBottomTab == "Objects") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                        .background(Color.Black.copy(alpha = 0.05f), RoundedCornerShape(10.dp))
                        .padding(vertical = 4.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf("Id", "Name", "Type").forEachIndexed { index, colName ->

                        val interactionSource = remember { MutableInteractionSource() }
                        val isHovered by interactionSource.collectIsHoveredAsState()

                        Box(
                            modifier = Modifier
                                .weight(columnWidths[index])
                                .fillMaxHeight(),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .hoverable(interactionSource)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        if (sortColumn == colName) {
                                            sortAscending = !sortAscending
                                        } else {
                                            sortColumn = colName
                                            sortAscending = true
                                        }
                                    }
                                    .padding(horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    (if (sortColumn == colName)
                                        if (sortAscending) "▲ " else "▼ "
                                    else "") + colName,
                                    color = Color(0xFF1E293B),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f).offset(y = (-2.5).dp)
                                )

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .clickable {
                                            activeSearchColumn =
                                                if (activeSearchColumn == colName) null else colName
                                        }
                                        .padding(0.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Search,
                                        contentDescription = "Search",
                                        tint = Color(0xFF3B82F6),
                                        modifier = Modifier
                                            .size(14.dp)
                                    )
                                }
                            }
                        }

                        if (index < 2) {
                            Box(
                                modifier = Modifier
                                    .size(width = 10.dp, height = 24.dp)
                                    .pointerInput(index) {
                                        detectDragGestures { _, dragAmount ->
                                            val delta = (dragAmount.x / 120f).coerceIn(-0.7f, 0.7f)
                                            columnWidths[index] =
                                                (columnWidths[index] + delta).coerceIn(0.9f, 4.8f)
                                            columnWidths[index + 1] =
                                                (columnWidths[index + 1] - delta).coerceIn(0.9f, 4.8f)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    modifier = Modifier
                                        .background(
                                            Color.Black.copy(alpha = 0.06f),
                                            RoundedCornerShape(6.dp)
                                        )
                                        .padding(horizontal = 3.dp, vertical = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    repeat(3) {
                                        Box(
                                            modifier = Modifier
                                                .size(2.dp)
                                                .background(Color.Black.copy(alpha = 1f), CircleShape)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 6.dp)) {
                if (activeBottomTab == "Objects") {
                    if (objectRows.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Нет объектов по запросу",
                                color = Color(0xFF64748B),
                                fontSize = 12.sp
                            )
                        }
                    } else {
                        LazyColumn(
                            state = objectsListState,
                            modifier = Modifier.fillMaxSize().padding(end = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(0.dp)
                        ) {
                            itemsIndexed(objectRows) { _, row ->
                                if (row.sectionTitle != null) {
                                    Text(
                                        text = row.sectionTitle,
                                        color = Color(0xFF0F172A),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(top = 2.dp, bottom = 2.dp)
                                    )
                                } else if (row.objectIndex != null) {
                                    val obj = openedTab.objects[row.objectIndex]
                                    val isSelected =
                                        row.objectIndex == openedTab.activeObjectIndex
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isSelected) Color(0xFFE0F2FE).copy(
                                                    alpha = 0.55f
                                                ) else Color.Transparent
                                            )
                                            .border(
                                                1.dp,
                                                if (isSelected) Color(0xFFBAE6FD) else Color.Transparent,
                                                RoundedCornerShape(8.dp)
                                            )
                                            .padding(vertical = 6.dp, horizontal = 6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier.weight(columnWidths[0])
                                                .clip(RoundedCornerShape(6.dp))
                                                .clickable { onObjectSelected(row.objectIndex) }
                                                .padding(vertical = 3.dp, horizontal = 2.dp),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            Text(
                                                "${obj.id}",
                                                color = Color(0xFF475569),
                                                fontSize = 12.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Box(
                                            modifier = Modifier.weight(columnWidths[1])
                                                .clip(RoundedCornerShape(6.dp))
                                                .clickable { onObjectSelected(row.objectIndex) }
                                                .padding(vertical = 3.dp, horizontal = 2.dp),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            Text(
                                                obj.name.ifEmpty { "" },
                                                color = Color(0xFF475569),
                                                fontSize = 12.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Box(
                                            modifier = Modifier.weight(columnWidths[2])
                                                .clip(RoundedCornerShape(6.dp))
                                                .clickable { onObjectSelected(row.objectIndex) }
                                                .padding(vertical = 3.dp, horizontal = 2.dp),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            Text(
                                                obj.type,
                                                color = Color(0xFF94A3B8),
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                } else if (row.textureIndex != null) {
                                    val tex = openedTab.textures[row.textureIndex]
                                    val isSelected =
                                        row.textureIndex == openedTab.activeTextureIndex
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isSelected) Color(0xFFE0F2FE).copy(
                                                    alpha = 0.55f
                                                ) else Color.Transparent
                                            )
                                            .border(
                                                1.dp,
                                                if (isSelected) Color(0xFFBAE6FD) else Color.Transparent,
                                                RoundedCornerShape(8.dp)
                                            )
                                            .clickable { onTextureSelected(row.textureIndex) }
                                            .padding(vertical = 6.dp, horizontal = 8.dp)
                                    ) {
                                        Text(
                                            "Texture ${tex.index} · ${tex.width}x${tex.height} · ${tex.format}",
                                            color = Color(0xFF475569),
                                            fontSize = 11.sp,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }

                        }
                    }
                } else if (activeBottomTab == "Textures") {
                    if (filteredTextures.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Нет текстур по запросу",
                                color = Color(0xFF64748B),
                                fontSize = 12.sp
                            )
                        }
                    } else {
                        LazyColumn(
                            state = texturesListState,
                            modifier = Modifier.fillMaxSize().padding(end = 8.dp),
                        ) {
                            itemsIndexed(filteredTextures) { _, tex ->
                                val isSelected = tex.index == openedTab.activeTextureIndex
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) Color(0xFFE0F2FE).copy(alpha = 0.55f) else Color.Transparent)
                                        .border(
                                            1.dp,
                                            if (isSelected) Color(0xFFBAE6FD) else Color.Transparent,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable { onTextureSelected(tex.index) }
                                        .padding(vertical = 6.dp, horizontal = 8.dp)
                                ) {
                                    Text(
                                        "Texture ${tex.index} · ${tex.width}x${tex.height}",
                                        color = Color(0xFF475569),
                                        fontSize = 11.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                } else {
                    val selectedObj =
                        if (openedTab.activeObjectIndex in openedTab.objects.indices) {
                            openedTab.objects[openedTab.activeObjectIndex]
                        } else null
                    val selectedTex =
                        if (openedTab.activeTextureIndex in openedTab.textures.indices) {
                            openedTab.textures[openedTab.activeTextureIndex]
                        } else null
                    GlassObjectInfo(selectedObj, selectedTex, openedTab)
                }

                if (activeBottomTab == "Objects") {
                    VerticalListScrollbar(objectsListState)
                } else if (activeBottomTab == "Textures") {
                    VerticalListScrollbar(texturesListState)
                }
            }

            if (activeBottomTab == "Objects" && activeSearchColumn != null) {
                val queryValue = when (activeSearchColumn) {
                    "Id" -> idQuery
                    "Name" -> nameQuery
                    else -> typeQuery
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .height(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.8f))
                        .border(1.dp, Color(0xFF93C5FD), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (queryValue.isEmpty()) {
                        Text(
                            "Искать в ${activeSearchColumn}...",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }
                    BasicTextField(
                        value = queryValue,
                        onValueChange = {
                            when (activeSearchColumn) {
                                "Id" -> idQuery = it
                                "Name" -> nameQuery = it
                                else -> typeQuery = it
                            }
                        },
                        textStyle = TextStyle(fontSize = 12.sp, color = Color(0xFF1E293B)),
                        singleLine = true,
                        cursorBrush = SolidColor(Color(0xFF2563EB)),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().height(35.dp)
                    .fillMaxWidth()
                    .height(35.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                    .padding(2.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf("Objects", "Info", "Textures").forEach { tab ->
                    val isActive = activeBottomTab == tab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isActive) Color.Black.copy(alpha = 0.1f) else Color.Transparent)
                            .clickable { activeBottomTab = tab }
                            .padding(2.dp),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Text(
                            text = tab,
                            color = if (isActive) Color(0xFF1E293B) else Color(0xFF64748B),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}