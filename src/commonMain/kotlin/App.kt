import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import com.luklaaa.sceditor.AppLog
import com.luklaaa.sceditor.ui.AppTheme
import com.luklaaa.sceditor.ui.ThemeMode
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import dev.donutquine.editor.assets.SupercellTextureAssetFileLoader
import dev.donutquine.editor.assets.SupercellSWFAssetFileLoader
import dev.donutquine.utilities.ImageUtils
import dev.donutquine.utilities.rgbaBytesToArgbInts
import team.nulls.ntengine.assets.KhronosTextureDataLoader
import ui.GlassSidebar
import ui.CompactMenuDrawer
import ui.GlassViewport
import ui.GlassTimelinePanel
import kotlinx.coroutines.launch
import ui.OpenedTab
import ui.ToastHost
import ui.ToastState
import ui.ScObjectItem
import ui.ScTextureItem
import ui.ScMatrixBankItem
import ui.ScMatrixItem
import ui.ScColorTransformItem
import ui.ScMovieClipChildItem
import ui.ScMovieClipFrameItem
import ui.ScMovieClipFrameElementItem
import ui.ScRectItem
import ui.ScMovieClipModifierItem
import ui.ScMovieClipModifierType
import dev.donutquine.editor.renderer.impl.swf.objects.ScMovieClipView
import dev.donutquine.editor.renderer.impl.swf.objects.rememberMovieClipController
import java.nio.ByteBuffer
import java.nio.IntBuffer

fun bufferToIntArray(buffer: Any, width: Int, height: Int, pixelTypeStr: String = ""): IntArray {
    return when (buffer) {
        is IntBuffer -> {
            buffer.rewind()
            val ints = IntArray(buffer.remaining())
            buffer.get(ints)
            for (i in ints.indices) {
                val v = ints[i]
                ints[i] = (v and 0xFF00FF00.toInt()) or ((v and 0xFF) shl 16) or ((v ushr 16) and 0xFF)
            }
            ints
        }
        is java.nio.ShortBuffer -> {
            buffer.rewind()
            val shorts = ShortArray(buffer.remaining())
            buffer.get(shorts)
            val type = pixelTypeStr.uppercase()
            val isLuminanceAlpha = type == "TYPE_6" || type.contains("LUMINANCE")
            val isRgba5551 = type == "TYPE_3" || type.contains("5551") || type.contains("RGB5_A1")
            val isRgba4444 = type == "TYPE_2" || type == "TYPE_8" || type.contains("RGBA4") || type.contains("4444")
            val pixels = IntArray(width * height)
            for (i in 0 until minOf(pixels.size, shorts.size)) {
                val v = shorts[i].toInt() and 0xFFFF
                pixels[i] = when {
                    isLuminanceAlpha -> {
                        val l = v and 0xFF
                        val a = (v ushr 8) and 0xFF
                        (a shl 24) or (l shl 16) or (l shl 8) or l
                    }
                    isRgba5551 -> {
                        val r = (v ushr 11) and 0x1F
                        val g = (v ushr 6) and 0x1F
                        val b = (v ushr 1) and 0x1F
                        val a = v and 0x1
                        ((a * 255) shl 24) or ((r * 255 / 31) shl 16) or ((g * 255 / 31) shl 8) or (b * 255 / 31)
                    }
                    isRgba4444 -> {
                        val r = (v ushr 12) and 0xF
                        val g = (v ushr 8) and 0xF
                        val b = (v ushr 4) and 0xF
                        val a = v and 0xF
                        ((a * 17) shl 24) or ((r * 17) shl 16) or ((g * 17) shl 8) or (b * 17)
                    }
                    else -> { // RGB565 (TYPE_4)
                        val r = (v ushr 11) and 0x1F
                        val g = (v ushr 5) and 0x3F
                        val b = v and 0x1F
                        (0xFF shl 24) or ((r * 255 / 31) shl 16) or ((g * 255 / 63) shl 8) or (b * 255 / 31)
                    }
                }
            }
            pixels
        }
        is ByteBuffer -> {
            buffer.rewind()
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            if (bytes.size >= width * height * 4) rgbaBytesToArgbInts(width, height, bytes)
            else convert8BitToArgb(width, height, bytes)
        }
        else -> IntArray(width * height)
    }
}

fun convert16BitToArgb(width: Int, height: Int, bytes: ByteArray, pixelTypeName: String): IntArray {
    val pixels = IntArray(width * height)
    val isRgba4 = pixelTypeName.contains("RGBA4", ignoreCase = true) || pixelTypeName.contains("4444", ignoreCase = true)
    for (i in pixels.indices) {
        val b1 = bytes[i * 2].toInt() and 0xFF
        val b2 = bytes[i * 2 + 1].toInt() and 0xFF
        val val16 = (b2 shl 8) or b1
        if (isRgba4) {
            val r = (val16 ushr 12) and 0x0F
            val g = (val16 ushr 8) and 0x0F
            val b = (val16 ushr 4) and 0x0F
            val a = val16 and 0x0F
            pixels[i] = ((a * 17) shl 24) or ((r * 17) shl 16) or ((g * 17) shl 8) or (b * 17)
        } else {
            val r = (val16 ushr 11) and 0x1F
            val g = (val16 ushr 5) and 0x3F
            val b = val16 and 0x1F
            pixels[i] = (0xFF shl 24) or ((r * 255 / 31) shl 16) or ((g * 255 / 63) shl 8) or (b * 255 / 31)
        }
    }
    return pixels
}

fun convert8BitToArgb(width: Int, height: Int, bytes: ByteArray): IntArray {
    val pixels = IntArray(width * height)
    for (i in pixels.indices) {
        val gray = bytes[i].toInt() and 0xFF
        pixels[i] = (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray
    }
    return pixels
}

// Декодер текстур SWF/SCTX в ImageBitmap для вьюпорта
fun decodeTextureToBitmap(width: Int, height: Int, rawBuffer: Any?, ktxData: ByteArray?, pixelTypeStr: String): ImageBitmap? {
    if (rawBuffer != null) {
        val argbPixels = bufferToIntArray(rawBuffer, width, height, pixelTypeStr)
        return ImageUtils.createBitmap(width, height, argbPixels, false)
    } else if (ktxData != null) {
        val ktx = KhronosTextureDataLoader.decodeKtx(ktxData)
        if (ktx.glType != 0 && ktx.glFormat != 0) {
            val ktxBytes = ktx.levels[0]
            val size32 = ktx.width * ktx.height * 4
            val size16 = ktx.width * ktx.height * 2
            val argbPixels = try {
                when {
                    ktxBytes.size >= size32 -> rgbaBytesToArgbInts(ktx.width, ktx.height, ktxBytes)
                    ktxBytes.size >= size16 -> convert16BitToArgb(ktx.width, ktx.height, ktxBytes, pixelTypeStr)
                    else -> convert8BitToArgb(ktx.width, ktx.height, ktxBytes)
                }
            } catch (e: Exception) { null }
            if (argbPixels != null) {
                return ImageUtils.createBitmap(ktx.width, ktx.height, argbPixels, false)
            }
        } else {
            return ImageUtils.decompressKtx(ktx)
        }
    }
    return null
}

@Suppress("UnusedBoxWithConstraintsScope")
@Composable
fun App(
    onTitleChanged: (String) -> Unit,
    onExit: () -> Unit,
    triggerOpenFile: Boolean,
    onOpenFileHandled: () -> Unit,
    triggerCloseFile: Boolean,
    onCloseFileHandled: () -> Unit,
    triggerCloseAllFiles: Boolean,
    onCloseAllFilesHandled: () -> Unit,
    // Компактный режим (телефон): Objects/Textures/Info уезжают в выдвижное «бургер»-меню.
    compactLayout: Boolean = false,
    menuOnRight: Boolean = false,
    menuOpen: Boolean = false,
    onMenuOpenChange: (Boolean) -> Unit = {},
    // Отступы под вырез камеры (по бокам). Платформа сама решает, какие именно (Android передаёт displayCutout).
    contentInsets: Modifier = Modifier,
    // Тема приложения. По умолчанию LIGHT - так UI выглядит как раньше (цвета ещё не переведены на тему).
    themeMode: ThemeMode = ThemeMode.LIGHT,
    // Необязательная верхняя панель (на Android - кнопки Open / Settings); рисуется над рабочей областью.
    topBar: (@Composable () -> Unit)? = null,
    // Показывать таймлайн даже у объекта с одним кадром.
    showTimeline: Boolean = false
) {
    val openedTabs = remember { mutableStateListOf<OpenedTab>() }
    var activeTabIndex by remember { mutableStateOf(-1) }
    // Панель с открытыми файлами свёрнута (только компактный режим): вьюпорт занимает её место.
    var tabsCollapsed by remember { mutableStateOf(false) }
    LaunchedEffect(openedTabs.isEmpty()) { if (openedTabs.isEmpty()) tabsCollapsed = false }

    // Динамическая ширина сайдбара
    var sidebarWidth by remember { mutableStateOf(280.dp) }

    val activeTab = if (activeTabIndex in openedTabs.indices) openedTabs[activeTabIndex] else null

    LaunchedEffect(activeTab) {
        if (activeTab != null) {
            onTitleChanged(activeTab.name)
        } else {
            onTitleChanged("SC Editor 1.6.5")
        }
    }

    val scope = rememberCoroutineScope()
    val toast = remember { ToastState() }

    fun String.isTextureFile() =
        substringAfterLast('\\').substringAfterLast('/').endsWith("_tex.sc", ignoreCase = true)

    fun fileNameOf(path: String) = path.substringAfterLast('\\').substringAfterLast('/')

    fun finishLoad(outcome: LoadOutcome) {
        when (outcome) {
            is LoadOutcome.Loaded -> {
                openedTabs.add(outcome.tab)
                activeTabIndex = openedTabs.size - 1
                toast.dismiss()
            }
            is LoadOutcome.Failed -> {
                AppLog.w("Open failed: ${outcome.message}")
                val tip = if (outcome.message.startsWith("Not enough memory") && openedTabs.isNotEmpty())
                    "\nTip: close other open files to free memory and try again."
                else ""
                toast.show(outcome.message + tip + "\n(details: Logs)")
            }
            is LoadOutcome.NeedsTexture -> toast.show("Texture file ${outcome.expectedName} doesn't match this .sc")
        }
    }

    // path - основной файл (.sc / .sctx); texturePath - _tex.sc, который пользователь уже выбрал (если выбрал).
    fun loadFile(path: String, texturePath: String?, askedForTexture: Boolean) {
        val name = fileNameOf(path)
        toast.show("Opening $name...", persistent = true)
        scope.launch {
            val report: (String) -> Unit = { toast.show(it, persistent = true) }
            var outcome = loadTabOnLargeStack(path, null, report)

            // Библиотека сама ищет _tex.sc рядом с .sc. Если рядом нет, а пользователь выбрал его
            // отдельно (из другой папки или под другим именем) - подставляем выбранный.
            if (outcome is LoadOutcome.NeedsTexture && texturePath != null) {
                outcome = loadTabOnLargeStack(path, ExternalTexture(texturePath, outcome.expectedName), report)
            }

            if (outcome is LoadOutcome.NeedsTexture && !askedForTexture) {
                // .sc действительно ссылается на внешние текстуры, а их нет - только тогда просим выбрать _tex.sc.
                openFilePicker("Select _tex.sc file", false) { picked ->
                    val tex = picked.firstOrNull()
                    if (tex == null) {
                        toast.show("$name needs a _tex.sc file")
                    } else {
                        loadFile(path, tex, askedForTexture = true)
                    }
                }
                return@launch
            }
            finishLoad(outcome)
        }
    }

    val openFileLambda = {
        openFilePicker("Select .sc file\n(if it has _tex.sc, select both files)", true) { paths ->
            AppLog.i("File picker returned: ${if (paths.isEmpty()) "nothing" else paths.joinToString { fileNameOf(it) }}")
            if (paths.isNotEmpty()) {
                val main = paths.firstOrNull { !it.isTextureFile() }
                val texture = paths.firstOrNull { it.isTextureFile() }
                if (main == null) {
                    toast.show("Select the main .sc file, not only _tex.sc")
                } else {
                    loadFile(main, texture, askedForTexture = false)
                }
            }
        }
    }

    LaunchedEffect(triggerOpenFile) {
        if (triggerOpenFile) {
            onOpenFileHandled()
            openFileLambda()
        }
    }

    LaunchedEffect(triggerCloseFile) {
        if (triggerCloseFile) {
            onCloseFileHandled()
            if (activeTabIndex in openedTabs.indices) {
                openedTabs.removeAt(activeTabIndex)
                activeTabIndex = if (openedTabs.isEmpty()) -1 else openedTabs.size - 1
            }
        }
    }

    LaunchedEffect(triggerCloseAllFiles) {
        if (triggerCloseAllFiles) {
            onCloseAllFilesHandled()
            openedTabs.clear()
            activeTabIndex = -1
        }
    }

    AppTheme(themeMode) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                topBar?.invoke()
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Brush.linearGradient(colors = listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.background)))
                            .then(contentInsets)
                            .padding(12.dp)
                    ) {
                        val sidebarMaxWidth = maxWidth - 200.dp

                        Column(modifier = Modifier.fillMaxSize()) {

                            Spacer(modifier = Modifier.height((-3).dp))

                            // Вкладки открытых файлов (Tabs)
                            if (openedTabs.isNotEmpty()) {
                                val showBurger = compactLayout && activeTab != null
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = !(compactLayout && tabsCollapsed),
                                    enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                                    exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
                                ) {
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (showBurger && !menuOnRight) {
                                                ui.BurgerButton(onClick = { onMenuOpenChange(true) })
                                                Spacer(modifier = Modifier.width(8.dp))
                                            }
// закоменчено потому что хуево выглядит и сделано

//                                            if (compactLayout && !menuOnRight) {
//                                                ui.TabsHandleButton(collapsed = false, onRight = false, onClick = { tabsCollapsed = true })
//                                                Spacer(modifier = Modifier.width(8.dp))
//                                            }
                                            ui.GlassFileTabBar(
                                                openedTabs = openedTabs,
                                                activeTabIndex = activeTabIndex,
                                                onTabSelect = { activeTabIndex = it },
                                                onTabClose = { index ->
                                                    openedTabs.removeAt(index)
                                                    activeTabIndex = if (openedTabs.isEmpty()) -1 else openedTabs.size - 1
                                                },
                                                modifier = Modifier.weight(1f)
                                            )
// закоменчено потому что хуево выглядит и сделано

//                                            if (compactLayout && menuOnRight) {
//                                                Spacer(modifier = Modifier.width(8.dp))
//                                                ui.TabsHandleButton(collapsed = false, onRight = true, onClick = { tabsCollapsed = true })
//                                            }
                                            if (showBurger && menuOnRight) {
                                                Spacer(modifier = Modifier.width(8.dp))
                                                ui.BurgerButton(onClick = { onMenuOpenChange(true) })
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(12.dp))
                                    }
                                }
                            }

                            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                if (activeTab != null && !compactLayout) {
                                    GlassSidebar(
                                        openedTab = activeTab,
                                        onObjectSelected = { objIndex ->
                                            openedTabs[activeTabIndex] = activeTab.copy(activeObjectIndex = objIndex, viewMode = "OBJECT")
                                        },
                                        onTextureSelected = { texIndex ->
                                            openedTabs[activeTabIndex] = activeTab.copy(activeTextureIndex = texIndex, viewMode = "TEXTURE")
                                        },
                                        onResizeDrag = { deltaDp ->
                                            sidebarWidth = (sidebarWidth + deltaDp).coerceIn(220.dp, sidebarMaxWidth)
                                        },
                                        modifier = Modifier.width(sidebarWidth).fillMaxHeight()
                                    )

                                    Spacer(modifier = Modifier.width(6.dp))
                                }

                                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                    val selectedObj = if (activeTab != null && activeTab.activeObjectIndex in activeTab.objects.indices) {
                                        activeTab.objects[activeTab.activeObjectIndex]
                                    } else null

                                    val currentTexture = if (activeTab != null && activeTab.textures.isNotEmpty()) {
                                        val texIndex = activeTab.activeTextureIndex.coerceIn(activeTab.textures.indices)
                                        activeTab.textures[texIndex]
                                    } else null

                                    val viewMode = activeTab?.viewMode ?: "OBJECT"

                                    val isShapeSelected = viewMode == "OBJECT" && selectedObj?.type == "Shape" && selectedObj.shapeCommands.isNotEmpty()
                                    val isMovieClipSelected = viewMode == "OBJECT" && selectedObj?.type == "MovieClip" && selectedObj.mcFrames.isNotEmpty()
                                    val showTextureCanvas = !isShapeSelected && !isMovieClipSelected

                                    val mcController = if (isMovieClipSelected && selectedObj != null) {
                                        rememberMovieClipController(selectedObj)
                                    } else null

                                    GlassViewport(
                                        loadedImage = if (showTextureCanvas) currentTexture?.bitmap else null,
                                        cameraResetKey = Triple(activeTab?.path, viewMode, selectedObj?.id ?: currentTexture?.index),
                                        infoLabel = when {
                                            isShapeSelected -> "Shape ${selectedObj?.id} · Objects: ${selectedObj?.shapeCommands?.size}"
                                            isMovieClipSelected -> {
                                                val nameSuffix = if (!selectedObj?.name.isNullOrEmpty()) "\n· ${selectedObj?.name}" else ""
                                                "MovieClip ${selectedObj?.id}$nameSuffix\n· ${selectedObj?.fps} fps\n· frame ${(mcController?.currentFrame ?: 0) + 1}/${selectedObj?.mcFrames?.size}"
                                            }
                                            currentTexture != null -> "Texture ${currentTexture.index} · ${currentTexture.width}×${currentTexture.height} · ${currentTexture.format}"
                                            else -> null
                                        },
                                        content = when {
                                            isShapeSelected && activeTab != null && selectedObj != null -> {
                                                {
                                                    dev.donutquine.editor.renderer.impl.swf.objects.ScShapeView(
                                                        commands = selectedObj.shapeCommands,
                                                        textures = activeTab.textures,
                                                        useStrip = activeTab.containerVersion >= 5,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                }
                                            }
                                            isMovieClipSelected && activeTab != null && selectedObj != null && mcController != null -> {
                                                {
                                                    ScMovieClipView(
                                                        movieClip = selectedObj,
                                                        objectsById = activeTab.objectsById,
                                                        matrixBanks = activeTab.matrixBanks,
                                                        modifiersById = activeTab.modifiersById,
                                                        textures = activeTab.textures,
                                                        useStrip = activeTab.containerVersion >= 5,
                                                        timeSeconds = mcController.timeSeconds,
                                                        // Гизмо (перетаскивание/ресайз children) не то, что
                                                        // просил пользователь — оставлен в коде, но выключен.
                                                        gizmoEnabled = false,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                }
                                            }
                                            else -> null
                                        },
                                        modifier = Modifier.weight(1f).fillMaxWidth()
                                    )

                                    if (isMovieClipSelected && selectedObj != null && mcController != null && showTimeline) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        GlassTimelinePanel(
                                            frameCount = selectedObj.mcFrames.size,
                                            currentFrame = mcController.currentFrame,
                                            isPlaying = mcController.isPlaying,
                                            onFrameChange = { mcController.setFrame(it) },
                                            onTogglePlaying = { mcController.togglePlaying() },
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                }
                            }
                        }

                    }
// закоменчено потому что хуево выглядит и сделано
                    // Панель с файлами свёрнута: кнопки (бургер + ручка) плавают поверх вьюпорта в углу выбранной стороны.
//                    if (compactLayout && openedTabs.isNotEmpty() && tabsCollapsed) {
//                        Row(
//                            modifier = Modifier.align(if (menuOnRight) Alignment.TopEnd else Alignment.TopStart),
//                            verticalAlignment = Alignment.CenterVertically,
//                            horizontalArrangement = Arrangement.spacedBy(8.dp)
//                        ) {
//                            if (menuOnRight) {
//                                ui.TabsHandleButton(collapsed = true, onRight = true, onClick = { tabsCollapsed = false })
//                                if (activeTab != null) ui.BurgerButton(onClick = { onMenuOpenChange(true) })
//                            } else {
//                                if (activeTab != null) ui.BurgerButton(onClick = { onMenuOpenChange(true) })
//                                ui.TabsHandleButton(collapsed = true, onRight = false, onClick = { tabsCollapsed = false })
//                            }
//                        }
//                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .align(Alignment.TopCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Black.copy(alpha = 0.10f), Color.Transparent)
                                )
                            )
                    )
                }
            }
            ToastHost(
                state = toast,
                modifier = Modifier.align(Alignment.BottomCenter).then(contentInsets).padding(bottom = 28.dp)
            )
            if (compactLayout && activeTab != null) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val drawerWidth = minOf(300.dp, maxWidth * 0.78f)
                    Box(modifier = Modifier.fillMaxSize()) {
                        CompactMenuDrawer(
                            onRight = menuOnRight,
                            open = menuOpen,
                            onDismiss = { onMenuOpenChange(false) },
                            drawerWidth = drawerWidth
                        ) {
                            GlassSidebar(
                                openedTab = activeTab,
                                onObjectSelected = { objIndex ->
                                    openedTabs[activeTabIndex] = activeTab.copy(activeObjectIndex = objIndex, viewMode = "OBJECT")
                                    onMenuOpenChange(false)
                                },
                                onTextureSelected = { texIndex ->
                                    openedTabs[activeTabIndex] = activeTab.copy(activeTextureIndex = texIndex, viewMode = "TEXTURE")
                                    onMenuOpenChange(false)
                                },
                                showResizeHandle = false,
                                modifier = Modifier.fillMaxSize().then(contentInsets)
                            )
                        }
                    }
                }
            }
        }
    }
}