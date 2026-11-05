package io.github.pradyumnadp.blobface

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * Asserts blobface matches upstream blobatar 2.7.0 exactly, against fixtures
 * produced by `tools/generate-fixtures.mjs` from the published npm package.
 *
 * Every comparison is exact: SVG strings byte-for-byte, numbers by `==`.
 */
class ParityTest {
    private class Mismatches(val label: String) {
        val items = ArrayList<String>()
        var checked = 0
        fun check(what: String, expected: Any?, actual: Any?) {
            checked++
            if (canon(expected) != canon(actual)) items += "$what\n    expected: $expected\n    actual:   $actual"
        }
        fun assertNone() {
            if (items.isNotEmpty()) {
                fail("$label: ${items.size}/$checked mismatches. First ${minOf(10, items.size)}:\n" + items.take(10).joinToString("\n"))
            }
        }
    }

    private fun load(name: String): JsonObject = Json.parseToJsonElement(fixture(name)).jsonObject

    private val JsonElement.str: String get() = jsonPrimitive.content
    private val JsonElement.num: Double get() = parseFixtureDouble(jsonPrimitive.content)
    private fun JsonObject.d(k: String): Double = getValue(k).num
    private fun JsonElement.nums(): List<Double> = jsonArray.map { it.num }

    private fun decodeOptions(o: JsonObject): BlobfaceOptions {
        val bg = o["background"]?.jsonPrimitive
        return BlobfaceOptions(
            size = o["size"]?.num?.toInt(),
            background = when {
                bg == null -> BlobfaceBackground.None
                bg.booleanOrNull == true -> BlobfaceBackground.Squircle
                bg.booleanOrNull == false -> BlobfaceBackground.None
                else -> BlobfaceBackground.valueOf(bg.content.replaceFirstChar { it.uppercase() })
            },
            palette = o["palette"]?.jsonObject?.let {
                BlobfacePalette(it["bg"]?.str, it["head"]?.str, it["eye"]?.str)
            },
            hue = o["hue"]?.num,
            tone = o["tone"]?.num,
            traits = o["traits"]?.jsonObject?.mapValues { (_, v) ->
                when (v) {
                    is JsonArray -> TraitOverride.AnyOf(v.nums())
                    else -> TraitOverride.Fixed(if (v.jsonPrimitive.isString) Double.NaN else v.num)
                }
            } ?: emptyMap(),
            normalize = o["normalize"]?.jsonPrimitive?.booleanOrNull ?: true,
            contrast = o["contrast"]?.jsonPrimitive?.booleanOrNull ?: true,
            title = o["title"]?.str,
            expression = o["expression"]?.str?.let { BlobfaceExpression.byName(it)!! },
        )
    }

    private fun checkLayout(m: Mismatches, tag: String, f: Blobface, j: JsonObject) {
        val l = f.layout
        m.check("$tag shape", j["shape"]!!.str, l.shape.id)
        val p = j["palette"]!!.jsonObject
        m.check("$tag palette", listOf(p["bg"]?.str, p["head"]?.str, p["eye"]?.str), listOf(f.palette.bg, f.palette.head, f.palette.eye))
        val b = j["body"]!!.jsonObject
        m.check(
            "$tag body",
            listOf(b.d("cx"), b.d("cy"), b.d("rx"), b.d("ry"), b.d("n"), b.d("rot")) + b["radii"]!!.nums(),
            listOf(l.body.cx, l.body.cy, l.body.rx, l.body.ry, l.body.n, l.body.rot) + l.body.radii,
        )
        m.check("$tag sides/round", listOf((b["sides"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.num, (b["round"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.num),
            listOf(l.body.sides?.toDouble(), l.body.round))
        val fc = j["face"]!!.jsonObject
        m.check("$tag face", listOf(fc.d("cx"), fc.d("cy"), fc.d("rx"), fc.d("ry")), listOf(l.face.cx, l.face.cy, l.face.rx, l.face.ry))
        m.check("$tag petals", j["petals"]!!.jsonArray.map { val o = it.jsonObject; listOf(o.d("cx"), o.d("cy"), o.d("r")) },
            l.petals.map { listOf(it.cx, it.cy, it.r) })
        m.check("$tag eyes", j["eyes"]!!.jsonArray.map { val o = it.jsonObject; listOf(o.d("cx"), o.d("cy"), o.d("rx"), o.d("ry"), o.d("n"), o.d("rot")) },
            l.eyes.map { listOf(it.cx, it.cy, it.rx, it.ry, it.n, it.rot) })
    }

    @Test
    fun namesMatchUpstream() {
        val m = Mismatches("names.json")
        for (c in load("names.json")["cases"]!!.jsonArray) {
            val o = c.jsonObject
            val name = o["name"]!!.str
            val f = Blobface(name)
            val tag = "name=${name.take(40).encodeForMessage()}"
            m.check("$tag normalized", o["normalized"]!!.str, normalizeSeed(name))
            m.check("$tag svg", o["svg"]!!.str, f.toSvgString())
            m.check("$tag uri", o["uri"]!!.str, f.toDataUri())
            checkLayout(m, tag, f, o["layout"]!!.jsonObject)
        }
        m.assertNone()
    }

    @Test
    fun optionsMatchUpstream() {
        val m = Mismatches("options.json")
        for (c in load("options.json")["cases"]!!.jsonArray) {
            val o = c.jsonObject
            val name = o["name"]!!.str
            val opts = decodeOptions(o["options"]!!.jsonObject)
            val f = Blobface(name, opts)
            val tag = "name=${name.take(30).encodeForMessage()} opts=${o["options"]}"
            m.check("$tag svg", o["svg"]!!.str, f.toSvgString())
            checkLayout(m, tag, f, o["layout"]!!.jsonObject)
            m.check("$tag transform", o["transform"]!!.str, f.bodyTransform)
            m.check(
                "$tag marks",
                o["marks"]!!.jsonArray.map {
                    val k = it.jsonObject
                    if (k["kind"]!!.str == "circle") "c ${k.d("cx")} ${k.d("cy")} ${k.d("r")} ${k["fill"]!!.str}"
                    else "p ${k["d"]!!.str} ${k["fill"]!!.str}"
                },
                f.marks.map {
                    when (it) {
                        is BlobfaceMark.Circle -> "c ${it.cx} ${it.cy} ${it.r} ${it.fill}"
                        is BlobfaceMark.Path -> "p ${it.d} ${it.fill}"
                    }
                },
            )
            val parts = o["parts"]!!.jsonObject
            val mine = f.toAnimatedSvgParts(BlobfaceAnimate.Hover)
            m.check("$tag parts.cls", parts["cls"]!!.str, mine.cls)
            m.check("$tag parts.always", o["partsAlways"]!!.str, f.toAnimatedSvgParts(BlobfaceAnimate.Always).cls)
            m.check("$tag parts.inner", parts["inner"]!!.str, mine.inner)
            m.check("$tag parts.vars", parts["vars"]!!.jsonObject.entries.map { it.key to it.value.str }, mine.vars.entries.map { it.key to it.value })
            val bg = parts["bg"]
            m.check("$tag parts.bg", if (bg == null || bg is JsonNull) null else bg.jsonObject.let { it["d"]!!.str to it["fill"]!!.str },
                mine.background?.let { it.d to it.fill })
        }
        m.assertNone()
    }

    @Test
    fun motionMatchesUpstream() {
        val root = load("motion.json")
        val m = Mismatches("motion.json")
        for (c in root["motion"]!!.jsonArray) {
            val o = c.jsonObject
            val name = o["name"]!!.str
            val f = Blobface(name)
            val s = f.idleSeeds
            val js = o["seeds"]!!.jsonObject
            m.check(
                "$name seeds",
                listOf("phase", "bob", "blink", "blinkPhase", "saccade", "saccadePhase", "lookX", "lookY", "lookMX", "lookMY").map { js.d(it) },
                listOf(s.phase, s.bob, s.blink, s.blinkPhase, s.saccade, s.saccadePhase, s.lookX, s.lookY, s.lookMX, s.lookMY),
            )
            for (fr in o["frames"]!!.jsonArray) {
                val q = fr.jsonObject
                val frame = idleAt(s, q.d("t"), q.d("amp"), q.d("shake"))
                val e = q["frame"]!!.jsonObject
                val w = e["wrap"]!!.jsonObject
                m.check(
                    "$name frame t=${q["t"]} amp=${q["amp"]}",
                    e["shake"]!!.nums() + e["breathe"]!!.nums() + listOf(e.d("bob")) + e["saccade"]!!.nums() +
                        listOf(e.d("rockp"), e.d("blink"), w.d("mx"), w.d("side"), w.d("sy"), w.d("rot")),
                    listOf(frame.shakeX, frame.shakeY, frame.breatheX, frame.breatheY, frame.bob, frame.saccadeX, frame.saccadeY,
                        frame.rockp, frame.blink, frame.wrapMx, frame.wrapSide, frame.wrapSy, frame.wrapRot),
                )
                val pose = BlobfaceExpression.byName(q["expression"]!!.str)!!.pose
                val tr = idleTransforms(f.baseLayout, pose, frame)
                val et = q["transforms"]!!.jsonObject
                m.check(
                    "$name transforms t=${q["t"]}",
                    listOf(et["root"]!!.str, et["breathe"]!!.str, et["bob"]!!.str, et["eyes"]!!.str) +
                        et["eye"]!!.jsonArray.map { it.str } + et["glance"]!!.jsonArray.map { it.str },
                    listOf(tr.root, tr.breathe, tr.bob, tr.eyes) + tr.eye + tr.glance,
                )
            }
            for (p in o["poses"]!!.jsonArray) {
                val q = p.jsonObject
                val pose = BlobfaceExpression.byName(q["expression"]!!.str)!!.pose
                val eyes = q["eyes"]!!.jsonArray
                m.check("$name pose ${q["expression"]} rockp=1", eyes[0].jsonArray.map { it.str }, poseTransforms(f.baseLayout, pose, 1.0).map { it.toSvg() })
                m.check("$name pose ${q["expression"]} rockp=-0.3", eyes[1].jsonArray.map { it.str }, poseTransforms(f.baseLayout, pose, -0.3).map { it.toSvg() })
            }
        }
        for (c in root["lerps"]!!.jsonArray) {
            val q = c.jsonObject
            val a = (q["a"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.let { BlobfaceExpression.byName(it)!!.pose }
            val b = (q["b"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.let { BlobfaceExpression.byName(it)!!.pose }
            val got = Pose.lerp(a, b, q.d("t"))
            val exp = q["pose"]!!.jsonObject
            m.check("lerp $a $b ${q["t"]}", got.channels().map { exp.d(it.first) }, got.channels().map { it.second })
        }
        m.assertNone()
    }

    @Test
    fun utilitiesMatchUpstream() {
        val root = load("utils.json")
        val m = Mismatches("utils.json")
        for (c in root["colors"]!!.jsonArray) {
            val q = c.jsonObject
            val hue = q.d("hue")
            val tone = q.d("tone")
            val enforce = q["enforce"]!!.jsonPrimitive.booleanOrNull!!
            val p = palette(hue, enforce, tone)
            val e = q["palette"]!!.jsonObject
            m.check("palette $hue $tone $enforce", listOf(e["bg"]!!.str, e["head"]!!.str, e["eye"]!!.str), listOf(p.bg, p.head, p.eye))
            val r = ramp(hue, enforce, tone)
            val er = q["ramp"]!!.jsonObject
            for ((key, k) in listOf("bg" to ColorKey.Bg, "head" to ColorKey.Head, "eye" to ColorKey.Eye)) {
                val x = er[key]!!.jsonObject
                val y = r.getValue(k)
                m.check("ramp $hue $tone $key", listOf(x.d("l"), x.d("c"), x.d("h")), listOf(y.l, y.c, y.h))
            }
        }
        for (c in root["pairs"]!!.jsonArray) {
            val q = c.jsonObject
            fun ok(o: JsonObject) = Oklch(o.d("l"), o.d("c"), o.d("h"))
            m.check("contrast", q.d("contrast"), contrast(ok(q["a"]!!.jsonObject), ok(q["b"]!!.jsonObject)))
        }
        for (c in root["fades"]!!.jsonArray) {
            val q = c.jsonObject
            m.check("fade $q", q["out"]!!.str, fadeHex(q["a"]!!.str, q["b"]!!.str, q.d("t")))
        }
        val g = root["gaze"]!!.jsonObject
        for (c in g["project"]!!.jsonArray) {
            val q = c.jsonObject
            val mk = q["m"]!!.jsonObject
            val p = GazeMath.project(GazeMath.Mark(mk.d("x"), mk.d("y")), q.d("yaw"), q.d("pitch"))
            val e = q["p"]!!.jsonObject
            m.check("project $q", listOf(e.d("dx"), e.d("dy"), e.d("sx"), e.d("sy"), e.d("t")), listOf(p.dx, p.dy, p.sx, p.sy, p.t))
        }
        for (c in g["step"]!!.jsonArray) {
            val i = c.jsonObject["i"]!!.jsonObject
            val s = GazeMath.step(
                i.d("x"), i.d("y"), i.d("dx"), i.d("dy"), i.d("radius"), i.d("k"),
                i["gain"]?.num ?: 1.0, i["snap"]?.num ?: GazeMath.SNAP,
            )
            val e = c.jsonObject["s"]!!.jsonObject
            m.check("step $i", listOf(e.d("x"), e.d("y"), e.d("tx"), e.d("ty"), e.d("f")), listOf(s.x, s.y, s.tx, s.ty, s.f))
        }
        for (c in g["pursuit"]!!.jsonArray) {
            val q = c.jsonObject
            val dt = q.d("dt")
            m.check("pursuit $dt", listOf(q.d("v"), q.d("v0"), q.d("v200")),
                listOf(GazeMath.pursuit(dt), GazeMath.pursuit(dt, 0.0), GazeMath.pursuit(dt, 200.0)))
        }
        for (c in g["threshold"]!!.jsonArray) {
            val q = c.jsonObject
            m.check("threshold $q", q.d("v"), GazeMath.threshold(q.d("w"), q.d("tr")))
        }
        for (c in g["smoothstep"]!!.jsonArray) {
            val q = c.jsonObject
            m.check("smoothstep $q", q.d("v"), GazeMath.smoothstep(q.d("t")))
        }
        m.assertNone()
    }

    @Test
    fun fixtureVersionIsPinned() {
        assertEquals("2.7.0", load("utils.json")["version"]!!.str)
    }
}

/**
 * JSON cannot represent `-0` (`JSON.stringify(-0) === "0"`), and JS `===` treats
 * the two zeros as equal, so numeric comparisons fold `-0.0` into `0.0`.
 */
private fun canon(v: Any?): Any? = when (v) {
    is Double -> if (v == 0.0) 0.0 else v
    is List<*> -> v.map(::canon)
    is Pair<*, *> -> canon(v.first) to canon(v.second)
    else -> v
}

private fun String.encodeForMessage(): String = buildString {
    for (c in this@encodeForMessage) {
        if (c.code < 0x20 || c.code in 0xD800..0xDFFF) append("\\u" + c.code.toString(16).padStart(4, '0')) else append(c)
    }
}
