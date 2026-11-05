package io.github.pradyumnadp.blobface

import io.github.pradyumnadp.blobface.internal.Fdlibm
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.fail

/**
 * The math blobface runs on each platform must equal Node's `Math.*`
 * bit-for-bit, on vectors recorded from the engine that produced the fixtures.
 * `JsMath` uses this port on every platform.
 */
class MathParityTest {
    private val root: JsonObject by lazy { Json.parseToJsonElement(fixture("math.json")).jsonObject }

    private fun rows(key: String): List<List<Double>> =
        root[key]!!.jsonArray.map { r -> r.jsonArray.map { parseFixtureDouble(it.jsonPrimitive.content) } }

    private fun check(name: String, f: (List<Double>) -> Double) {
        val bad = ArrayList<String>()
        val rs = rows(name)
        for (r in rs) {
            val expected = r.last()
            val actual = f(r)
            // Exact bits, so the sign of zero counts too; any NaN matches any NaN.
            val same = expected.toRawBits() == actual.toRawBits() || (expected.isNaN() && actual.isNaN())
            if (!same) bad += "$name(${r.dropLast(1).joinToString()}) expected=$expected actual=$actual"
        }
        if (bad.isNotEmpty()) fail("$name: ${bad.size}/${rs.size} differ from Node. First: \n" + bad.take(10).joinToString("\n"))
    }

    @Test fun fdlibmSin() = check("sin") { Fdlibm.sin(it[0]) }
    @Test fun fdlibmCos() = check("cos") { Fdlibm.cos(it[0]) }
    @Test fun fdlibmAsin() = check("asin") { Fdlibm.asin(it[0]) }
    @Test fun fdlibmAtan2() = check("atan2") { Fdlibm.atan2(it[0], it[1]) }
    @Test fun fdlibmExp() = check("exp") { Fdlibm.exp(it[0]) }
    @Test fun fdlibmCbrt() = check("cbrt") { Fdlibm.cbrt(it[0]) }
    @Test fun fdlibmPow() = check("pow") { Fdlibm.pow(it[0], it[1]) }

}
