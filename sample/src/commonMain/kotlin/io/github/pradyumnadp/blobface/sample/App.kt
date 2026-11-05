package io.github.pradyumnadp.blobface.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pradyumnadp.blobface.BlobfaceBackground
import io.github.pradyumnadp.blobface.BlobfaceExpression
import io.github.pradyumnadp.blobface.BlobfaceOptions
import io.github.pradyumnadp.blobface.BlobfacePalette
import io.github.pradyumnadp.blobface.BlobfaceShape
import io.github.pradyumnadp.blobface.BlobfaceTraitKeys
import io.github.pradyumnadp.blobface.TraitOverride
import io.github.pradyumnadp.blobface.compose.Blobface
import io.github.pradyumnadp.blobface.compose.BlobfaceAnimation
import io.github.pradyumnadp.blobface.compose.BlobfaceGazeState
import io.github.pradyumnadp.blobface.compose.BlobfacePointerArea
import io.github.pradyumnadp.blobface.compose.GazeAnchor
import io.github.pradyumnadp.blobface.compose.GazeTarget
import io.github.pradyumnadp.blobface.compose.gazeAnchor
import io.github.pradyumnadp.blobface.compose.rememberBlobfaceGaze
import io.github.pradyumnadp.blobface.compose.rememberGazeAnchor
import kotlin.math.roundToInt
import io.github.pradyumnadp.blobface.Blobface as BlobfaceModel

/** The demo: a live face for typed text, a control for every option, and a crowd. */
@Composable
fun App() {
    val st = remember { DemoState() }
    MaterialTheme(colorScheme = if (st.dark) darkColorScheme() else lightColorScheme()) {
        Surface(Modifier.fillMaxSize()) {
            BlobfacePointerArea(Modifier.fillMaxSize()) { Demo(st) }
        }
    }
}

private val CROWD: List<String> = buildList {
    val first = listOf(
        "alain", "ana", "bo", "chen", "dara", "eli", "femi", "gus", "hana", "ivo", "jun", "kai", "lena", "milo",
        "nia", "omar", "pia", "quinn", "rosa", "sami", "tara", "uma", "vik", "wren", "xiu", "yara", "zed",
    )
    val domains = listOf("example.com", "mail.dev", "corp.io", "uni.edu")
    for (i in 0 until 400) add("${first[i % first.size]}${if (i >= first.size) i else ""}@${domains[i % domains.size]}")
}

/** Every control's value. */
private class DemoState {
    var dark by mutableStateOf(false)
    var name by mutableStateOf("alain@example.com")
    var size by mutableStateOf(160f)
    var background by mutableStateOf(BlobfaceBackground.None)
    var lockHue by mutableStateOf(false)
    var hue by mutableStateOf(200f)
    var lockTone by mutableStateOf(false)
    var tone by mutableStateOf(0.5f)
    var normalize by mutableStateOf(true)
    var contrast by mutableStateOf(true)
    var title by mutableStateOf("")
    var head by mutableStateOf("")
    var eye by mutableStateOf("")
    var bg by mutableStateOf("")
    var expression by mutableStateOf(BlobfaceExpression.Idle)
    var animate by mutableStateOf(BlobfaceAnimation.Always)
    var gridAnimate by mutableStateOf(BlobfaceAnimation.Hover)
    var gazeMode by mutableStateOf("pointer")
    var travel by mutableStateOf(3f)
    var shapes by mutableStateOf(setOf<BlobfaceShape>())
    val pinned = mutableStateMapOf<String, Float>()
    var traitKey by mutableStateOf("eye.gap")
    var showSvg by mutableStateOf(false)

    fun options(): BlobfaceOptions {
        val traits = buildMap {
            if (shapes.isNotEmpty()) put("shape", TraitOverride.AnyOf(shapes.map { BlobfaceShape.midpoint(it) }))
            for ((k, v) in pinned) put(k, TraitOverride.Fixed(v.toDouble()))
        }
        val palette = BlobfacePalette(
            bg = bg.takeIf { it.isNotBlank() },
            head = head.takeIf { it.isNotBlank() },
            eye = eye.takeIf { it.isNotBlank() },
        ).takeIf { it.bg != null || it.head != null || it.eye != null }
        return BlobfaceOptions(
            background = background,
            palette = palette,
            hue = if (lockHue) hue.toDouble() else null,
            tone = if (lockTone) tone.toDouble() else null,
            traits = traits,
            normalize = normalize,
            contrast = contrast,
            title = title.ifBlank { null },
            expression = expression.takeIf { it != BlobfaceExpression.Idle },
        )
    }
}

@Composable
private fun Demo(st: DemoState) {
    val anchor = rememberGazeAnchor()
    val gaze = rememberBlobfaceGaze(travel = st.travel)
    gaze.travel = st.travel
    gaze.target = when (st.gazeMode) {
        "pointer" -> GazeTarget.Pointer
        "rest" -> GazeTarget.Rest
        "anchor" -> GazeTarget.Anchor(anchor)
        else -> GazeTarget.None
    }
    val options = st.options()

    BoxWithConstraints(Modifier.fillMaxSize().safeContentPadding()) {
        if (maxWidth >= 900.dp) {
            // Wide: controls on the left, preview and crowd on the right.
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.width(420.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(16.dp)) {
                    Controls(st, anchor)
                }
                CrowdGrid(st, options, Modifier.weight(1f)) {
                    item(span = { GridItemSpan(maxLineSpan) }) { Preview(st, options, gaze) }
                }
            }
        } else {
            CrowdGrid(st, options, Modifier.fillMaxSize()) {
                item(span = { GridItemSpan(maxLineSpan) }) { Preview(st, options, gaze) }
                item(span = { GridItemSpan(maxLineSpan) }) { Controls(st, anchor) }
            }
        }
    }
}

@Composable
private fun CrowdGrid(st: DemoState, options: BlobfaceOptions, modifier: Modifier, header: LazyGridScope.() -> Unit) {
    val crowdOptions = options.copy(title = null)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(72.dp),
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        header()
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                HorizontalDivider()
                Text(
                    "A crowd of ${CROWD.size} — ${st.gridAnimate.name.lowercase()} animation",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        items(CROWD, key = { it }) { n ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Blobface(n, crowdOptions, size = 56.dp, animate = st.gridAnimate)
                Text(n.substringBefore('@'), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun Preview(st: DemoState, options: BlobfaceOptions, gaze: BlobfaceGazeState) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("blobface", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.width(16.dp))
            Text("dark", fontSize = 12.sp)
            Switch(st.dark, { st.dark = it }, Modifier.padding(start = 4.dp))
        }
        Text("Unofficial Kotlin Multiplatform port of blobatar by @alain_0012", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))
        Blobface(st.name, options, size = st.size.dp, animate = st.animate, gaze = gaze)
        val model = remember(st.name, options) { BlobfaceModel(st.name, options) }
        Text("${model.shape.id} · ${model.palette.head}", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            st.name, { st.name = it },
            label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        if (st.showSvg) {
            val svg = remember(st.name, options) { model.toSvgString() }
            SelectionContainer(Modifier.padding(16.dp)) { Text(svg, fontFamily = FontFamily.Monospace, fontSize = 10.sp) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Controls(st: DemoState, anchor: GazeAnchor) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Section("Expression")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (e in BlobfaceExpression.entries) Chip(e.name, st.expression == e) { st.expression = e }
            }
            Section("Animation")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("preview", Modifier.align(Alignment.CenterVertically))
                for (a in BlobfaceAnimation.entries) Chip(a.name, st.animate == a) { st.animate = a }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("crowd", Modifier.align(Alignment.CenterVertically))
                for (a in BlobfaceAnimation.entries) Chip(a.name, st.gridAnimate == a) { st.gridAnimate = a }
            }
            Section("Gaze")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (m in listOf("none", "pointer", "rest", "anchor")) Chip(m, st.gazeMode == m) { st.gazeMode = m }
                Box(
                    Modifier.align(Alignment.CenterVertically).gazeAnchor(anchor)
                        .background(MaterialTheme.colorScheme.tertiaryContainer)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) { Text("look here", fontSize = 12.sp) }
            }
            Labeled("Travel ${st.travel.roundToInt()}") { Slider(st.travel, { st.travel = it }, valueRange = 0f..12f) }
            Section("Size & backdrop")
            Labeled("Size ${st.size.roundToInt()}dp") { Slider(st.size, { st.size = it }, valueRange = 24f..320f) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (b in BlobfaceBackground.entries) Chip(b.name, st.background == b) { st.background = b }
            }
            Section("Color")
            Toggle("Lock hue ${st.hue.roundToInt()}°", st.lockHue) { st.lockHue = it }
            if (st.lockHue) Slider(st.hue, { st.hue = it }, valueRange = 0f..360f)
            Toggle("Lock tone ${(st.tone * 1000).roundToInt() / 1000.0}", st.lockTone) { st.lockTone = it }
            if (st.lockTone) Slider(st.tone, { st.tone = it }, valueRange = 0f..0.999f)
            Toggle("Enforce contrast", st.contrast) { st.contrast = it }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                HexField("head", st.head) { st.head = it }
                HexField("eye", st.eye) { st.eye = it }
                HexField("bg", st.bg) { st.bg = it }
            }
            Section("Shape (any of)")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (s in BlobfaceShape.entries) {
                    Chip(s.id, s in st.shapes) { st.shapes = if (s in st.shapes) st.shapes - s else st.shapes + s }
                }
            }
            Section("Traits")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (k in BlobfaceTraitKeys.ALL.filter { it != "shape" && it != "hue" && it != "tone" }) {
                    Chip(k + if (k in st.pinned) " •" else "", st.traitKey == k) { st.traitKey = k }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Toggle("Pin ${st.traitKey}", st.traitKey in st.pinned) {
                    if (it) st.pinned[st.traitKey] = 0.5f else st.pinned.remove(st.traitKey)
                }
                TextButton({ st.pinned.clear() }) { Text("Clear") }
            }
            st.pinned[st.traitKey]?.let { v -> Slider(v, { st.pinned[st.traitKey] = it }, valueRange = 0f..0.999f) }
            Section("Other")
            Toggle("Normalize name", st.normalize) { st.normalize = it }
            OutlinedTextField(
                st.title, { st.title = it },
                label = { Text("Title (accessibility)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Toggle("Show SVG", st.showSvg) { st.showSvg = it }
        }
    }
}

@Composable
private fun Section(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp))
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label, fontSize = 12.sp) })
}

@Composable
private fun Toggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(value, onChange)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

@Composable
private fun Labeled(label: String, content: @Composable () -> Unit) {
    Column {
        Text(label, fontSize = 12.sp)
        content()
    }
}

@Composable
private fun HexField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, label = { Text("$label #hex") }, singleLine = true, modifier = Modifier.width(120.dp))
}
