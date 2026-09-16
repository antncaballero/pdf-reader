package com.acaba.pdfreader.ui

import android.content.res.Configuration
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.pdf.PdfPoint
import androidx.pdf.SandboxedPdfLoader
import androidx.pdf.ExperimentalPdfApi
import androidx.pdf.compose.PdfViewer
import androidx.pdf.compose.rememberPdfViewerState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.acaba.pdfreader.PdfReaderApplication
import com.acaba.pdfreader.data.HighlightStrokeEntity
import com.acaba.pdfreader.data.PdfDocumentEntity
import com.acaba.pdfreader.data.StrokePoint
import com.acaba.pdfreader.pdf.PdfTextEngine
import com.acaba.pdfreader.pdf.TextGlyph
import com.acaba.pdfreader.sync.AnnotationSyncWorker
import com.acaba.pdfreader.sync.PdfDocumentLocks
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.hypot

private val Yellow = Color(0xFFFFFF00)
private val Green = Color(0xFF39FF14)
private val Blue = Color(0xFF00E5FF)
private const val HIGHLIGHT_OPACITY = 0.28f

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPdfApi::class)
@Composable
fun ReaderScreen(
    document: PdfDocumentEntity,
    initialPageIndex: Int? = null,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as PdfReaderApplication
    val repository = app.repository
    val orientation = LocalConfiguration.current.orientation
    val viewerState = rememberPdfViewerState()
    val strokes by repository.observeStrokes(document.id).collectAsStateWithLifecycle(emptyList())
    var openAttempt by rememberSaveable { mutableStateOf(0) }
    val textEngine = remember(document.uri) { PdfTextEngine(context.contentResolver, Uri.parse(document.uri)) }
    val pdfLoad = produceState<PdfLoad>(initialValue = PdfLoad.Loading, document.uri, openAttempt) {
        value = PdfLoad.Loading
        var session: PdfReadSession? = null
        try {
            val openedSession = withContext(Dispatchers.IO) {
                val lock = PdfDocumentLocks.forDocument(document.id)
                lock.lock()
                try {
                    val pdf = SandboxedPdfLoader(context, Dispatchers.IO)
                        .openDocument(Uri.parse(document.uri), null)
                    // Keep ownership before crossing the cancellable dispatcher boundary.
                    PdfReadSession(pdf, lock).also {
                        session = it
                        require(pdf.pageCount > 0) { "El PDF no contiene páginas." }
                    }
                } catch (error: Throwable) {
                    if (session == null) lock.unlock()
                    throw error
                }
            }
            value = PdfLoad.Ready(openedSession)
            awaitCancellation()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            value = PdfLoad.Error(error)
        } finally {
            try {
                try {
                    textEngine.close()
                } finally {
                    session?.close()
                }
            } finally {
                AnnotationSyncWorker.enqueue(context, document.id)
            }
        }
    }
    val pdfSession = (pdfLoad.value as? PdfLoad.Ready)?.session
    val pdfDocument = pdfSession?.document
    var darkMode by rememberSaveable { mutableStateOf(false) }
    var controlsVisible by rememberSaveable { mutableStateOf(true) }
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var highlightMode by rememberSaveable { mutableStateOf(false) }
    var goToPageOpen by rememberSaveable { mutableStateOf(false) }
    var colorArgb by rememberSaveable { mutableStateOf(Yellow.toArgb()) }
    var width by rememberSaveable { mutableStateOf(8f) }
    var savedPage by rememberSaveable(document.id, initialPageIndex) {
        mutableIntStateOf(initialPageIndex ?: document.lastPageIndex)
    }
    var stateRestored by remember(pdfDocument) { mutableStateOf(false) }
    var viewerSize by remember(pdfDocument) { mutableStateOf(IntSize.Zero) }
    var lastOrientation by remember { mutableIntStateOf(orientation) }
    var selectedGlyphs by remember { mutableStateOf<List<TextGlyph>>(emptyList()) }
    var selectedText by remember { mutableStateOf("") }
    var selectionStart by remember { mutableStateOf<Offset?>(null) }
    var selectionEnd by remember { mutableStateOf<Offset?>(null) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(selectionStart, selectionEnd) {
        val start = selectionStart
        val end = selectionEnd
        if (start != null && end != null) {
            val from = viewerState.visibleOffsetToPdfPoint(start)
            val to = viewerState.visibleOffsetToPdfPoint(end)
            if (from != null && to != null && from.pageNum == to.pageNum) {
                selectedGlyphs = runCatching { textEngine.select(from.pageNum, from.x, from.y, to.x, to.y) }.getOrDefault(emptyList())
                selectedText = textEngine.textOf(selectedGlyphs)
            }
        }
    }

    LaunchedEffect(viewerState.firstVisiblePage, pdfDocument, stateRestored) {
        if (stateRestored && pdfDocument != null && viewerState.visiblePagesCount > 0) {
            savedPage = viewerState.firstVisiblePage.coerceIn(0, pdfDocument.pageCount - 1)
            repository.updateReadingProgress(document.id, savedPage, pdfDocument.pageCount)
        }
    }

    LaunchedEffect(pdfDocument) {
        if (pdfDocument != null) {
            stateRestored = false
            savedPage = savedPage.coerceIn(0, pdfDocument.pageCount - 1)
            // Opening the document is asynchronous and precedes AndroidView attachment/layout.
            // Navigation before then is silently ignored by PdfViewerState. Let PdfView
            // calculate its natural fit scale; zoom=1 is pixels per PDF point, not fit-to-width.
            snapshotFlow {
                viewerSize.width > 0 && viewerSize.height > 0 && viewerState.visiblePagesCount > 0
            }.first { it }
            viewerState.scrollToPage(savedPage)
            snapshotFlow {
                savedPage >= viewerState.firstVisiblePage &&
                    savedPage < viewerState.firstVisiblePage + viewerState.visiblePagesCount
            }.first { it }
            repository.updateReadingProgress(document.id, savedPage, pdfDocument.pageCount)
            stateRestored = true
        }
    }

    val pageBeforeRotation = savedPage
    LaunchedEffect(orientation, pdfDocument) {
        val orientationChanged = orientation != lastOrientation
        lastOrientation = orientation
        if (orientationChanged && stateRestored && pdfDocument != null) {
            repeat(2) { withFrameNanos { } }
            viewerState.scrollToPage(pageBeforeRotation.coerceIn(0, pdfDocument.pageCount - 1))
        }
    }

    val viewerModifier = Modifier.fillMaxSize().onSizeChanged { viewerSize = it }.drawWithContent {
        if (!darkMode) {
            drawContent()
        } else {
            // Map the original white PDF background to dark gray and black text
            // to a softer bone-white instead of using a pure color inversion.
            val filter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
                -0.741176f, 0f, 0f, 0f, 232f,
                0f, -0.72549f, 0f, 0f, 228f,
                0f, 0f, -0.701961f, 0f, 222f,
                0f, 0f, 0f, 1f, 0f,
            )))
            val paint = androidx.compose.ui.graphics.Paint()
            paint.asFrameworkPaint().colorFilter = filter
            drawContext.canvas.saveLayer(androidx.compose.ui.geometry.Rect(Offset.Zero, size), paint)
            drawContent()
            drawContext.canvas.restore()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                if (controlsVisible) TopAppBar(
                    title = { Text(document.displayName, maxLines = 1) },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Volver") } },
                    actions = {
                        IconButton(
                            enabled = pdfDocument != null,
                            onClick = { goToPageOpen = true },
                        ) {
                            Icon(Icons.Default.FindInPage, contentDescription = "Ir a página")
                        }
                        IconButton(onClick = { controlsVisible = false }) {
                            Icon(Icons.Default.Fullscreen, contentDescription = "Ocultar controles")
                        }
                        IconButton(onClick = { darkMode = !darkMode }) { Icon(if (darkMode) Icons.Default.LightMode else Icons.Default.DarkMode, contentDescription = "Modo oscuro") }
                        IconButton(onClick = {
                            if (selectionMode) {
                                selectionMode = false
                                selectionStart = null
                                selectionEnd = null
                                selectedGlyphs = emptyList()
                                selectedText = ""
                            } else {
                                selectionMode = true
                                highlightMode = false
                            }
                        }) { Icon(Icons.Default.SelectAll, contentDescription = "Seleccionar texto") }
                        IconButton(onClick = {
                            if (!document.canWrite) scope.launch { snackbar.showSnackbar("Este proveedor es de solo lectura") }
                            else {
                                highlightMode = !highlightMode
                                selectionMode = false
                            }
                        }) { Icon(Icons.Default.Highlight, contentDescription = "Subrayar") }
                    },
                )
            },
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                if (selectionMode && selectedText.isNotBlank()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = {
                            clipboard.setText(AnnotatedString(selectedText))
                            scope.launch { snackbar.showSnackbar("Texto copiado") }
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null)
                            Text(" Copiar")
                        }
                    }
                } else if (highlightMode) {
                    HighlightControls(colorArgb, width, onColor = { colorArgb = it }, onWidth = { width = it })
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (val load = pdfLoad.value) {
                    PdfLoad.Loading -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        CircularProgressIndicator()
                        Text("Abriendo PDF…", Modifier.padding(top = 12.dp))
                    }
                    is PdfLoad.Error -> Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text("No se puede acceder a este PDF.", style = MaterialTheme.typography.titleMedium)
                        Text(load.error.message ?: "El URI fue revocado o el archivo no es válido.", Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { openAttempt++ }, Modifier.padding(top = 12.dp)) { Text("Reintentar") }
                        TextButton(onClick = onBack) { Text("Volver") }
                    }
                    is PdfLoad.Ready -> {
                        PdfViewer(pdfDocument = pdfDocument, state = viewerState, modifier = viewerModifier)
                        HighlightOverlay(
                            viewerState = viewerState,
                            strokes = strokes,
                            selectionGlyphs = selectedGlyphs,
                            selectionStart = selectionStart,
                            selectionEnd = selectionEnd,
                            highlightMode = highlightMode,
                            selectionMode = selectionMode,
                            colorArgb = colorArgb,
                            width = width,
                            onStroke = { page, points ->
                                scope.launch {
                                    repository.addStroke(
                                        HighlightStrokeEntity(
                                            documentId = document.id,
                                            pageIndex = page,
                                            colorArgb = colorArgb,
                                            widthPoints = width,
                                            points = points,
                                        ),
                                    )
                                    AnnotationSyncWorker.enqueue(context, document.id)
                                }
                            },
                            onSelection = { selectionStart = it.first; selectionEnd = it.second },
                        )
                    }
                }
            }
        }
        if (!controlsVisible) {
            IconButton(
                onClick = { controlsVisible = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.End))
                    .padding(
                        top = 8.dp,
                        end = if (orientation == Configuration.ORIENTATION_LANDSCAPE) 48.dp else 8.dp,
                    )
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.82f), CircleShape),
            ) {
                Icon(Icons.Default.FullscreenExit, contentDescription = "Mostrar controles")
            }
        }
    }
    if (goToPageOpen && pdfDocument != null) {
        GoToPageDialog(
            pageCount = pdfDocument.pageCount,
            currentPage = savedPage + 1,
            onDismiss = { goToPageOpen = false },
            onConfirm = { pageIndex ->
                goToPageOpen = false
                scope.launch {
                    val targetPage = pageIndex.coerceIn(0, pdfDocument.pageCount - 1)
                    viewerState.scrollToPage(targetPage)
                    savedPage = targetPage
                    repository.updateReadingProgress(document.id, targetPage, pdfDocument.pageCount)
                }
            },
        )
    }
}

@Composable
private fun HighlightControls(colorArgb: Int, width: Float, onColor: (Int) -> Unit, onWidth: (Float) -> Unit) {
    val colors = listOf(Yellow, Green, Blue)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Brush, contentDescription = null)
            colors.forEach { color ->
                val selected = color.toArgb() == colorArgb
                Box(
                    Modifier
                        .size(30.dp)
                        .background(color, CircleShape)
                        .border(
                            width = if (selected) 3.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            shape = CircleShape,
                        )
                        .clickable { onColor(color.toArgb()) },
                )
            }
            Text("${width.toInt()} pt", style = MaterialTheme.typography.labelMedium)
        }
        Slider(value = width, onValueChange = onWidth, valueRange = 2f..20f, steps = 17)
    }
}

@OptIn(ExperimentalPdfApi::class)
@Composable
private fun HighlightOverlay(
    viewerState: androidx.pdf.compose.PdfViewerState,
    strokes: List<HighlightStrokeEntity>,
    selectionGlyphs: List<TextGlyph>,
    selectionStart: Offset?,
    selectionEnd: Offset?,
    highlightMode: Boolean,
    selectionMode: Boolean,
    colorArgb: Int,
    width: Float,
    onStroke: (Int, List<StrokePoint>) -> Unit,
    onSelection: (Pair<Offset, Offset>) -> Unit,
) {
    var activePage by remember { mutableStateOf<Int?>(null) }
    var activePoints by remember { mutableStateOf<List<StrokePoint>>(emptyList()) }
    val gestureScope = rememberCoroutineScope()
    val pointerModifier = when {
        highlightMode -> Modifier.pointerInput(viewerState, colorArgb, width) {
            awaitEachGesture {
                val down = awaitFirstDown()
                val first = viewerState.visibleOffsetToPdfPoint(down.position) ?: return@awaitEachGesture
                activePage = first.pageNum
                activePoints = listOf(StrokePoint(first.x, first.y))
                var canceled = false
                var multiTouch = false
                var previousCentroid: Offset? = null
                var previousDistance = 0f
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.changes.size > 1) {
                        multiTouch = true
                        canceled = true
                        activePoints = emptyList()
                        activePage = null
                        val firstPointer = event.changes[0].position
                        val secondPointer = event.changes[1].position
                        val centroid = (firstPointer + secondPointer) / 2f
                        val distance = hypot(secondPointer.x - firstPointer.x, secondPointer.y - firstPointer.y)
                        if (previousCentroid != null && previousDistance > 0f) {
                            val factor = (distance / previousDistance).coerceIn(0.85f, 1.15f)
                            val nextZoom = (viewerState.zoom * factor).coerceIn(1f, 5f)
                            gestureScope.launch {
                                viewerState.zoomScroll {
                                    zoomTo(nextZoom)
                                    scrollBy(centroid - previousCentroid!!)
                                }
                            }
                        }
                        previousCentroid = centroid
                        previousDistance = distance
                        event.changes.forEach { it.consume() }
                        if (event.changes.none { it.pressed }) break
                        continue
                    }
                    if (multiTouch) {
                        if (event.changes.none { it.pressed }) break
                        continue
                    }
                    val change = event.changes.first()
                    val point = viewerState.visibleOffsetToPdfPoint(change.position)
                    if (point != null && point.pageNum == first.pageNum) activePoints = activePoints + StrokePoint(point.x, point.y)
                    change.consume()
                    if (!change.pressed) break
                }
                if (!canceled && activePoints.size > 1) onStroke(first.pageNum, activePoints)
                activePoints = emptyList()
                activePage = null
            }
        }
        selectionMode -> Modifier.pointerInput(viewerState) {
            var start: Offset? = null
            var end: Offset? = null
            detectDragGesturesAfterLongPress(
                onDragStart = { start = it; end = it },
                onDrag = { change, amount -> end = (end ?: change.position) + amount; change.consume() },
                onDragEnd = { if (start != null && end != null) onSelection(start!! to end!!) },
                onDragCancel = { start = null; end = null },
            )
        }
        else -> Modifier
    }

    Canvas(Modifier.fillMaxSize().then(pointerModifier)) {
        strokes.forEach { stroke ->
            val path = Path()
            stroke.points.forEachIndexed { index, point ->
                val offset = viewerState.pdfPointToVisibleOffset(PdfPoint(stroke.pageIndex, point.x, point.y))
                if (offset == Offset.Unspecified) return@forEachIndexed
                if (index == 0) path.moveTo(offset.x, offset.y) else path.lineTo(offset.x, offset.y)
            }
            drawPath(path, color = highlightColor(stroke.colorArgb), style = Stroke(width = stroke.widthPoints * viewerState.zoom, cap = StrokeCap.Round))
        }
        activePage?.let { page ->
            val path = Path()
            activePoints.forEachIndexed { index, point ->
                val offset = viewerState.pdfPointToVisibleOffset(PdfPoint(page, point.x, point.y))
                if (offset == Offset.Unspecified) return@forEachIndexed
                if (index == 0) path.moveTo(offset.x, offset.y) else path.lineTo(offset.x, offset.y)
            }
            drawPath(path, color = highlightColor(colorArgb), style = Stroke(width = width * viewerState.zoom, cap = StrokeCap.Round))
        }
        selectionGlyphs.forEach { glyph ->
            val left = viewerState.pdfPointToVisibleOffset(PdfPoint(glyph.pageIndex, glyph.left, glyph.top))
            val right = viewerState.pdfPointToVisibleOffset(PdfPoint(glyph.pageIndex, glyph.right, glyph.bottom))
            if (left != Offset.Unspecified && right != Offset.Unspecified) drawRect(Color(0x553D7EFF), topLeft = left, size = Size(right.x - left.x, right.y - left.y))
        }
        if (selectionGlyphs.isEmpty() && selectionStart != null && selectionEnd != null) {
            drawLine(Color(0x883D7EFF), selectionStart, selectionEnd, strokeWidth = 2f)
        }
    }
}

private sealed interface PdfLoad {
    data object Loading : PdfLoad
    data class Ready(val session: PdfReadSession) : PdfLoad
    data class Error(val error: Throwable) : PdfLoad
}

private class PdfReadSession(
    val document: androidx.pdf.PdfDocument,
    private val lock: kotlinx.coroutines.sync.Mutex,
) : AutoCloseable {
    private val closed = AtomicBoolean(false)

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            try {
                document.close()
            } finally {
                lock.unlock()
            }
        }
    }
}

private fun highlightColor(colorArgb: Int): Color = Color(colorArgb).copy(alpha = HIGHLIGHT_OPACITY)
