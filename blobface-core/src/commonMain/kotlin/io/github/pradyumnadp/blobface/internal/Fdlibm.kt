/*
 * Pure-Kotlin port of the fdlibm functions blobface needs, transcribed from
 * V8 12.4's src/base/ieee754.cc (the engine behind Node 22, which produced the
 * parity fixtures). Running the same arithmetic on every non-JS platform makes
 * results bit-identical to JavaScript's Math.* instead of depending on the
 * platform libm (Apple's differs in the last bit for some inputs).
 *
 * ====================================================
 * Copyright (C) 1993 by Sun Microsystems, Inc. All rights reserved.
 *
 * Developed at SunSoft, a Sun Microsystems, Inc. business.
 * Permission to use, copy, modify, and distribute this
 * software is freely granted, provided that this notice
 * is preserved.
 * ====================================================
 *
 * The original source code covered by the above license has been modified
 * significantly by Google Inc. (V8, BSD-3-Clause, Copyright 2016 the V8 project
 * authors) and translated to Kotlin for blobface.
 */
package io.github.pradyumnadp.blobface.internal

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sqrt

internal object Fdlibm {
    // ---- IEEE word access -------------------------------------------------

    private fun hi(d: Double): Int = (d.toRawBits() ushr 32).toInt()
    private fun lo(d: Double): Int = d.toRawBits().toInt()
    private fun words(h: Int, l: Int): Double = Double.fromBits((h.toLong() shl 32) or (l.toLong() and 0xFFFFFFFFL))
    private fun withLow(d: Double, l: Int): Double = words(hi(d), l)
    private fun withHigh(d: Double, h: Int): Double = words(h, lo(d))

    /** x · 2^n. */
    private fun scalbn(x: Double, n: Int): Double {
        var y = x
        var k = n
        while (k > 1023) { y *= TWO_1023; k -= 1023 }
        while (k < -1022) { y *= TWO_M1022; k += 1022 }
        return y * Double.fromBits((k + 1023).toLong() shl 52)
    }

    private val TWO_1023 = Double.fromBits(0x7FE0000000000000L)
    private val TWO_M1022 = Double.fromBits(0x0010000000000000L)

    // ---- argument reduction ----------------------------------------------

    private val TWO_OVER_PI = intArrayOf(
        0xA2F983, 0x6E4E44, 0x1529FC, 0x2757D1, 0xF534DD, 0xC0DB62, 0x95993C,
        0x439041, 0xFE5163, 0xABDEBB, 0xC561B7, 0x246E3A, 0x424DD2, 0xE00649,
        0x2EEA09, 0xD1921C, 0xFE1DEB, 0x1CB129, 0xA73EE8, 0x8235F5, 0x2EBB44,
        0x84E99C, 0x7026B4, 0x5F7E41, 0x3991D6, 0x398353, 0x39F49C, 0x845F8B,
        0xBDF928, 0x3B1FF8, 0x97FFDE, 0x05980F, 0xEF2F11, 0x8B5A0A, 0x6D1F6D,
        0x367ECF, 0x27CB09, 0xB74F46, 0x3F669E, 0x5FEA2D, 0x7527BA, 0xC7EBE5,
        0xF17B3D, 0x0739F7, 0x8A5292, 0xEA6BFB, 0x5FB11F, 0x8D5D08, 0x560330,
        0x46FC7B, 0x6BABF0, 0xCFBC20, 0x9AF436, 0x1DA9E3, 0x91615E, 0xE61B08,
        0x659985, 0x5F14A0, 0x68408D, 0xFFD880, 0x4D7327, 0x310606, 0x1556CA,
        0x73A8C9, 0x60E27B, 0xC08C6B,
    )

    private val NPIO2_HW = intArrayOf(
        0x3FF921FB, 0x400921FB, 0x4012D97C, 0x401921FB, 0x401F6A7A, 0x4022D97C,
        0x4025FDBB, 0x402921FB, 0x402C463A, 0x402F6A7A, 0x4031475C, 0x4032D97C,
        0x40346B9C, 0x4035FDBB, 0x40378FDB, 0x403921FB, 0x403AB41B, 0x403C463A,
        0x403DD85A, 0x403F6A7A, 0x40407E4C, 0x4041475C, 0x4042106C, 0x4042D97C,
        0x4043A28C, 0x40446B9C, 0x404534AC, 0x4045FDBB, 0x4046C6CB, 0x40478FDB,
        0x404858EB, 0x404921FB,
    )

    private const val TWO24 = 1.67772160000000000000e+07
    private const val TWON24 = 5.96046447753906250000e-08

    /** Returns n and sets y[0] + y[1] = x − n·π/2. */
    private fun remPio2(x: Double, y: DoubleArray): Int {
        val invpio2 = 6.36619772367581382433e-01
        val pio2_1 = 1.57079632673412561417e+00
        val pio2_1t = 6.07710050650619224932e-11
        val pio2_2 = 6.07710050630396597660e-11
        val pio2_2t = 2.02226624879595063154e-21
        val pio2_3 = 2.02226624871116645580e-21
        val pio2_3t = 8.47842766036889956997e-32

        val hx = hi(x)
        val ix = hx and 0x7FFFFFFF
        if (ix <= 0x3FE921FB) {
            y[0] = x; y[1] = 0.0
            return 0
        }
        if (ix < 0x4002D97C) {
            if (hx > 0) {
                var z = x - pio2_1
                if (ix != 0x3FF921FB) {
                    y[0] = z - pio2_1t
                    y[1] = (z - y[0]) - pio2_1t
                } else {
                    z -= pio2_2
                    y[0] = z - pio2_2t
                    y[1] = (z - y[0]) - pio2_2t
                }
                return 1
            } else {
                var z = x + pio2_1
                if (ix != 0x3FF921FB) {
                    y[0] = z + pio2_1t
                    y[1] = (z - y[0]) + pio2_1t
                } else {
                    z += pio2_2
                    y[0] = z + pio2_2t
                    y[1] = (z - y[0]) + pio2_2t
                }
                return -1
            }
        }
        if (ix <= 0x413921FB) {
            var t = abs(x)
            val n = (t * invpio2 + 0.5).toInt()
            val fn = n.toDouble()
            var r = t - fn * pio2_1
            var w = fn * pio2_1t
            if (n < 32 && ix != NPIO2_HW[n - 1]) {
                y[0] = r - w
            } else {
                val j = ix shr 20
                y[0] = r - w
                var i = j - ((hi(y[0]) ushr 20) and 0x7FF)
                if (i > 16) {
                    t = r
                    w = fn * pio2_2
                    r = t - w
                    w = fn * pio2_2t - ((t - r) - w)
                    y[0] = r - w
                    i = j - ((hi(y[0]) ushr 20) and 0x7FF)
                    if (i > 49) {
                        t = r
                        w = fn * pio2_3
                        r = t - w
                        w = fn * pio2_3t - ((t - r) - w)
                        y[0] = r - w
                    }
                }
            }
            y[1] = (r - y[0]) - w
            return if (hx < 0) {
                y[0] = -y[0]; y[1] = -y[1]; -n
            } else {
                n
            }
        }
        if (ix >= 0x7FF00000) {
            y[0] = x - x; y[1] = y[0]
            return 0
        }
        var z = words(0, lo(x))
        val e0 = (ix shr 20) - 1046
        z = withHigh(z, ix - (e0 shl 20))
        val tx = DoubleArray(3)
        for (i in 0 until 2) {
            tx[i] = z.toInt().toDouble()
            z = (z - tx[i]) * TWO24
        }
        tx[2] = z
        var nx = 3
        while (tx[nx - 1] == 0.0) nx--
        val n = kernelRemPio2(tx, y, e0, nx, 2, TWO_OVER_PI)
        if (hx < 0) {
            y[0] = -y[0]; y[1] = -y[1]
            return -n
        }
        return n
    }

    private val INIT_JK = intArrayOf(2, 3, 4, 6)
    private val PIO2 = doubleArrayOf(
        1.57079625129699707031e+00, 7.54978941586159635335e-08, 5.39030252995776476554e-15,
        3.28200341580791294123e-22, 1.27065575308067607349e-29, 1.22933308981111328932e-36,
        2.73370053816464559624e-44, 2.16741683877804819444e-51,
    )

    private fun kernelRemPio2(x: DoubleArray, y: DoubleArray, e0: Int, nx: Int, prec: Int, ipio2: IntArray): Int {
        val jk = INIT_JK[prec]
        val jp = jk
        val jx = nx - 1
        var jv = (e0 - 3) / 24
        if (jv < 0) jv = 0
        var q0 = e0 - 24 * (jv + 1)
        val f = DoubleArray(20)
        val q = DoubleArray(20)
        val fq = DoubleArray(20)
        val iq = IntArray(20)

        var j = jv - jx
        val m = jx + jk
        for (i in 0..m) {
            f[i] = if (j < 0) 0.0 else ipio2[j].toDouble()
            j++
        }
        for (i in 0..jk) {
            var fw = 0.0
            for (jj in 0..jx) fw += x[jj] * f[jx + i - jj]
            q[i] = fw
        }
        var jz = jk
        var z: Double
        var n: Int
        var ih: Int
        while (true) { // recompute:
            var i = 0
            j = jz
            z = q[jz]
            while (j > 0) {
                val fw = (TWON24 * z).toInt().toDouble()
                iq[i] = (z - TWO24 * fw).toInt()
                z = q[j - 1] + fw
                i++; j--
            }
            z = scalbn(z, q0)
            z -= 8.0 * floor(z * 0.125)
            n = z.toInt()
            z -= n.toDouble()
            ih = 0
            if (q0 > 0) {
                i = iq[jz - 1] shr (24 - q0)
                n += i
                iq[jz - 1] -= i shl (24 - q0)
                ih = iq[jz - 1] shr (23 - q0)
            } else if (q0 == 0) {
                ih = iq[jz - 1] shr 23
            } else if (z >= 0.5) {
                ih = 2
            }
            if (ih > 0) {
                n += 1
                var carry = 0
                for (ii in 0 until jz) {
                    j = iq[ii]
                    if (carry == 0) {
                        if (j != 0) {
                            carry = 1
                            iq[ii] = 0x1000000 - j
                        }
                    } else {
                        iq[ii] = 0xFFFFFF - j
                    }
                }
                if (q0 > 0) {
                    when (q0) {
                        1 -> iq[jz - 1] = iq[jz - 1] and 0x7FFFFF
                        2 -> iq[jz - 1] = iq[jz - 1] and 0x3FFFFF
                    }
                }
                if (ih == 2) {
                    z = 1.0 - z
                    if (carry != 0) z -= scalbn(1.0, q0)
                }
            }
            if (z == 0.0) {
                j = 0
                for (ii in jz - 1 downTo jk) j = j or iq[ii]
                if (j == 0) {
                    var k = 1
                    while (jk >= k && iq[jk - k] == 0) k++
                    for (ii in jz + 1..jz + k) {
                        f[jx + ii] = ipio2[jv + ii].toDouble()
                        var fw = 0.0
                        for (jj in 0..jx) fw += x[jj] * f[jx + ii - jj]
                        q[ii] = fw
                    }
                    jz += k
                    continue
                }
            }
            break
        }
        if (z == 0.0) {
            jz -= 1
            q0 -= 24
            while (iq[jz] == 0) {
                jz--
                q0 -= 24
            }
        } else {
            z = scalbn(z, -q0)
            if (z >= TWO24) {
                val fw = (TWON24 * z).toInt().toDouble()
                iq[jz] = (z - TWO24 * fw).toInt()
                jz += 1
                q0 += 24
                iq[jz] = fw.toInt()
            } else {
                iq[jz] = z.toInt()
            }
        }
        var fw = scalbn(1.0, q0)
        for (i in jz downTo 0) {
            q[i] = fw * iq[i]
            fw *= TWON24
        }
        for (i in jz downTo 0) {
            fw = 0.0
            var k = 0
            while (k <= jp && k <= jz - i) {
                fw += PIO2[k] * q[i + k]
                k++
            }
            fq[jz - i] = fw
        }
        when (prec) {
            0 -> {
                fw = 0.0
                for (i in jz downTo 0) fw += fq[i]
                y[0] = if (ih == 0) fw else -fw
            }
            1, 2 -> {
                fw = 0.0
                for (i in jz downTo 0) fw += fq[i]
                y[0] = if (ih == 0) fw else -fw
                fw = fq[0] - fw
                for (i in 1..jz) fw += fq[i]
                y[1] = if (ih == 0) fw else -fw
            }
            else -> {
                for (i in jz downTo 1) {
                    fw = fq[i - 1] + fq[i]
                    fq[i] += fq[i - 1] - fw
                    fq[i - 1] = fw
                }
                for (i in jz downTo 2) {
                    fw = fq[i - 1] + fq[i]
                    fq[i] += fq[i - 1] - fw
                    fq[i - 1] = fw
                }
                fw = 0.0
                for (i in jz downTo 2) fw += fq[i]
                if (ih == 0) {
                    y[0] = fq[0]; y[1] = fq[1]; y[2] = fw
                } else {
                    y[0] = -fq[0]; y[1] = -fq[1]; y[2] = -fw
                }
            }
        }
        return n and 7
    }

    // ---- sin / cos ---------------------------------------------------------

    private fun kernelCos(x: Double, y: Double): Double {
        val c1 = 4.16666666666666019037e-02
        val c2 = -1.38888888888741095749e-03
        val c3 = 2.48015872894767294178e-05
        val c4 = -2.75573143513906633035e-07
        val c5 = 2.08757232129817482790e-09
        val c6 = -1.13596475577881948265e-11
        val ix = hi(x) and 0x7FFFFFFF
        if (ix < 0x3E400000) {
            if (x.toInt() == 0) return 1.0
        }
        val z = x * x
        val r = z * (c1 + z * (c2 + z * (c3 + z * (c4 + z * (c5 + z * c6)))))
        if (ix < 0x3FD33333) return 1.0 - (0.5 * z - (z * r - x * y))
        val qx = if (ix > 0x3FE90000) 0.28125 else words(ix - 0x00200000, 0)
        val iz = 0.5 * z - qx
        val a = 1.0 - qx
        return a - (iz - (z * r - x * y))
    }

    private fun kernelSin(x: Double, y: Double, iy: Int): Double {
        val s1 = -1.66666666666666324348e-01
        val s2 = 8.33333333332248946124e-03
        val s3 = -1.98412698298579493134e-04
        val s4 = 2.75573137070700676789e-06
        val s5 = -2.50507602534068634195e-08
        val s6 = 1.58969099521155010221e-10
        val ix = hi(x) and 0x7FFFFFFF
        if (ix < 0x3E400000) {
            if (x.toInt() == 0) return x
        }
        val z = x * x
        val v = z * x
        val r = s2 + z * (s3 + z * (s4 + z * (s5 + z * s6)))
        return if (iy == 0) x + v * (s1 + z * r) else x - ((z * (0.5 * y - v * r) - y) - v * s1)
    }

    fun sin(x: Double): Double {
        val ix = hi(x) and 0x7FFFFFFF
        if (ix <= 0x3FE921FB) return kernelSin(x, 0.0, 0)
        if (ix >= 0x7FF00000) return x - x
        val y = DoubleArray(3)
        val n = remPio2(x, y)
        return when (n and 3) {
            0 -> kernelSin(y[0], y[1], 1)
            1 -> kernelCos(y[0], y[1])
            2 -> -kernelSin(y[0], y[1], 1)
            else -> -kernelCos(y[0], y[1])
        }
    }

    fun cos(x: Double): Double {
        val ix = hi(x) and 0x7FFFFFFF
        if (ix <= 0x3FE921FB) return kernelCos(x, 0.0)
        if (ix >= 0x7FF00000) return x - x
        val y = DoubleArray(3)
        val n = remPio2(x, y)
        return when (n and 3) {
            0 -> kernelCos(y[0], y[1])
            1 -> -kernelSin(y[0], y[1], 1)
            2 -> -kernelCos(y[0], y[1])
            else -> kernelSin(y[0], y[1], 1)
        }
    }

    // ---- asin ----------------------------------------------------------------

    fun asin(x: Double): Double {
        val huge = 1.000e+300
        val pio2Hi = 1.57079632679489655800e+00
        val pio2Lo = 6.12323399573676603587e-17
        val pio4Hi = 7.85398163397448278999e-01
        val pS0 = 1.66666666666666657415e-01
        val pS1 = -3.25565818622400915405e-01
        val pS2 = 2.01212532134862925881e-01
        val pS3 = -4.00555345006794114027e-02
        val pS4 = 7.91534994289814532176e-04
        val pS5 = 3.47933107596021167570e-05
        val qS1 = -2.40339491173441421878e+00
        val qS2 = 2.02094576023350569471e+00
        val qS3 = -6.88283971605453293030e-01
        val qS4 = 7.70381505559019352791e-02

        var t = 0.0
        val hx = hi(x)
        val ix = hx and 0x7FFFFFFF
        if (ix >= 0x3FF00000) {
            if (((ix - 0x3FF00000) or lo(x)) == 0) return x * pio2Hi + x * pio2Lo
            return Double.NaN
        } else if (ix < 0x3FE00000) {
            if (ix < 0x3E400000) {
                if (huge + x > 1.0) return x
            } else {
                t = x * x
            }
            val p = t * (pS0 + t * (pS1 + t * (pS2 + t * (pS3 + t * (pS4 + t * pS5)))))
            val q = 1.0 + t * (qS1 + t * (qS2 + t * (qS3 + t * qS4)))
            val w = p / q
            return x + x * w
        }
        var w = 1.0 - abs(x)
        t = w * 0.5
        var p = t * (pS0 + t * (pS1 + t * (pS2 + t * (pS3 + t * (pS4 + t * pS5)))))
        var q = 1.0 + t * (qS1 + t * (qS2 + t * (qS3 + t * qS4)))
        val s = sqrt(t)
        if (ix >= 0x3FEF3333) {
            w = p / q
            t = pio2Hi - (2.0 * (s + s * w) - pio2Lo)
        } else {
            w = withLow(s, 0)
            val c = (t - w * w) / (s + w)
            val r = p / q
            p = 2.0 * s * r - (pio2Lo - 2.0 * c)
            q = pio4Hi - 2.0 * w
            t = pio4Hi - (p - q)
        }
        return if (hx > 0) t else -t
    }

    // ---- atan / atan2 --------------------------------------------------------

    private val ATANHI = doubleArrayOf(
        4.63647609000806093515e-01, 7.85398163397448278999e-01,
        9.82793723247329054082e-01, 1.57079632679489655800e+00,
    )
    private val ATANLO = doubleArrayOf(
        2.26987774529616870924e-17, 3.06161699786838301793e-17,
        1.39033110312309984516e-17, 6.12323399573676603587e-17,
    )
    private val AT = doubleArrayOf(
        3.33333333333329318027e-01, -1.99999999998764832476e-01, 1.42857142725034663711e-01,
        -1.11111104054623557880e-01, 9.09088713343650656196e-02, -7.69187620504482999495e-02,
        6.66107313738753120669e-02, -5.83357013379057348645e-02, 4.97687799461593236017e-02,
        -3.65315727442169155270e-02, 1.62858201153657823623e-02,
    )

    private fun atan(x0: Double): Double {
        var x = x0
        val hx = hi(x)
        val ix = hx and 0x7FFFFFFF
        val id: Int
        if (ix >= 0x44100000) {
            val low = lo(x)
            if (ix > 0x7FF00000 || (ix == 0x7FF00000 && low != 0)) return x + x
            return if (hx > 0) ATANHI[3] + ATANLO[3] else -ATANHI[3] - ATANLO[3]
        }
        if (ix < 0x3FDC0000) {
            if (ix < 0x3E400000) {
                if (1.0e300 + x > 1.0) return x
            }
            id = -1
        } else {
            x = abs(x)
            if (ix < 0x3FF30000) {
                if (ix < 0x3FE60000) {
                    id = 0
                    x = (2.0 * x - 1.0) / (2.0 + x)
                } else {
                    id = 1
                    x = (x - 1.0) / (x + 1.0)
                }
            } else {
                if (ix < 0x40038000) {
                    id = 2
                    x = (x - 1.5) / (1.0 + 1.5 * x)
                } else {
                    id = 3
                    x = -1.0 / x
                }
            }
        }
        var z = x * x
        val w = z * z
        val s1 = z * (AT[0] + w * (AT[2] + w * (AT[4] + w * (AT[6] + w * (AT[8] + w * AT[10])))))
        val s2 = w * (AT[1] + w * (AT[3] + w * (AT[5] + w * (AT[7] + w * AT[9]))))
        if (id < 0) return x - x * (s1 + s2)
        z = ATANHI[id] - ((x * (s1 + s2) - ATANLO[id]) - x)
        return if (hx < 0) -z else z
    }

    fun atan2(y: Double, x: Double): Double {
        val tiny = 1.0e-300
        val piO4 = 7.8539816339744827900E-01
        val piO2 = 1.5707963267948965580E+00
        val pi = 3.1415926535897931160E+00
        val piLo = 1.2246467991473531772E-16

        val hx = hi(x)
        val lx = lo(x)
        val ix = hx and 0x7FFFFFFF
        val hy = hi(y)
        val ly = lo(y)
        val iy = hy and 0x7FFFFFFF
        if ((ix or ((lx or -lx) ushr 31)) > 0x7FF00000 || (iy or ((ly or -ly) ushr 31)) > 0x7FF00000) {
            return x + y
        }
        if (((hx - 0x3FF00000) or lx) == 0) return atan(y)
        var m = ((hy shr 31) and 1) or ((hx shr 30) and 2)
        if ((iy or ly) == 0) {
            when (m) {
                0, 1 -> return y
                2 -> return pi + tiny
                3 -> return -pi - tiny
            }
        }
        if ((ix or lx) == 0) return if (hy < 0) -piO2 - tiny else piO2 + tiny
        if (ix == 0x7FF00000) {
            if (iy == 0x7FF00000) {
                return when (m) {
                    0 -> piO4 + tiny
                    1 -> -piO4 - tiny
                    2 -> 3.0 * piO4 + tiny
                    else -> -3.0 * piO4 - tiny
                }
            } else {
                return when (m) {
                    0 -> 0.0
                    1 -> -0.0
                    2 -> pi + tiny
                    else -> -pi - tiny
                }
            }
        }
        if (iy == 0x7FF00000) return if (hy < 0) -piO2 - tiny else piO2 + tiny
        val k = (iy - ix) shr 20
        val z: Double
        if (k > 60) {
            z = piO2 + 0.5 * piLo
            m = m and 1
        } else if (hx < 0 && k < -60) {
            z = 0.0
        } else {
            z = atan(abs(y / x))
        }
        return when (m) {
            0 -> z
            1 -> -z
            2 -> pi - (z - piLo)
            else -> (z - piLo) - pi
        }
    }

    // ---- exp -------------------------------------------------------------------

    fun exp(x0: Double): Double {
        var x = x0
        val halF = doubleArrayOf(0.5, -0.5)
        val oThreshold = 7.09782712893383973096e+02
        val uThreshold = -7.45133219101941108420e+02
        val ln2HI = doubleArrayOf(6.93147180369123816490e-01, -6.93147180369123816490e-01)
        val ln2LO = doubleArrayOf(1.90821492927058770002e-10, -1.90821492927058770002e-10)
        val invln2 = 1.44269504088896338700e+00
        val p1 = 1.66666666666666019037e-01
        val p2 = -2.77777777770155933842e-03
        val p3 = 6.61375632143793436117e-05
        val p4 = -1.65339022054652515390e-06
        val p5 = 4.13813679705723846039e-08
        val e = 2.718281828459045
        val huge = 1.0e+300
        val twom1000 = 9.33263618503218878990e-302
        val two1023 = 8.988465674311579539e307

        var hi = 0.0
        var lo = 0.0
        var k = 0
        var hx = hi(x)
        val xsb = (hx ushr 31) and 1
        hx = hx and 0x7FFFFFFF
        if (hx >= 0x40862E42) {
            if (hx >= 0x7FF00000) {
                return if (((hx and 0xFFFFF) or lo(x)) != 0) x + x else if (xsb == 0) x else 0.0
            }
            if (x > oThreshold) return huge * huge
            if (x < uThreshold) return twom1000 * twom1000
        }
        if (hx > 0x3FD62E42) {
            if (hx < 0x3FF0A2B2) {
                if (x == 1.0) return e
                hi = x - ln2HI[xsb]
                lo = ln2LO[xsb]
                k = 1 - xsb - xsb
            } else {
                k = (invln2 * x + halF[xsb]).toInt()
                val t = k.toDouble()
                hi = x - t * ln2HI[0]
                lo = t * ln2LO[0]
            }
            x = hi - lo
        } else if (hx < 0x3E300000) {
            if (huge + x > 1.0) return 1.0 + x
        } else {
            k = 0
        }
        val t = x * x
        val twopk = if (k >= -1021) words(0x3FF00000 + (k shl 20), 0) else words(0x3FF00000 + ((k + 1000) shl 20), 0)
        val c = x - t * (p1 + t * (p2 + t * (p3 + t * (p4 + t * p5))))
        if (k == 0) return 1.0 - ((x * c) / (c - 2.0) - x)
        val y = 1.0 - ((lo - (x * c) / (2.0 - c)) - hi)
        return if (k >= -1021) {
            if (k == 1024) y * 2.0 * two1023 else y * twopk
        } else {
            y * twopk * twom1000
        }
    }

    // ---- cbrt ------------------------------------------------------------------

    fun cbrt(x: Double): Double {
        val b1 = 715094163
        val b2 = 696219795
        val p0 = 1.87595182427177009643
        val p1 = -1.88497979543377169875
        val p2 = 1.621429720105354466140
        val p3 = -0.758397934778766047437
        val p4 = 0.145996192886612446982

        var hx = hi(x)
        val low = lo(x)
        val sign = hx and 0x80000000.toInt()
        hx = hx xor sign
        if (hx >= 0x7FF00000) return x + x
        var t: Double
        if (hx < 0x00100000) {
            if ((hx or low) == 0) return x
            t = words(0x43500000, 0)
            t *= x
            val high = hi(t)
            t = words(sign or ((high and 0x7FFFFFFF) / 3 + b2), 0)
        } else {
            t = words(sign or (hx / 3 + b1), 0)
        }
        var r = (t * t) * (t / x)
        t = t * ((p0 + r * (p1 + r * p2)) + ((r * r) * r) * (p3 + r * p4))
        t = Double.fromBits((t.toRawBits() + 0x80000000L) and -0x40000000L)
        val s = t * t
        r = x / s
        val w = t + t
        r = (r - t) / (w + r)
        t = t + t * r
        return t
    }

    // ---- pow -------------------------------------------------------------------

    private val BP = doubleArrayOf(1.0, 1.5)
    private val DP_H = doubleArrayOf(0.0, 5.84962487220764160156e-01)
    private val DP_L = doubleArrayOf(0.0, 1.35003920212974897128e-08)

    fun pow(x: Double, y: Double): Double {
        val two53 = 9007199254740992.0
        val huge = 1.0e300
        val tiny = 1.0e-300
        val l1 = 5.99999999999994648725e-01
        val l2 = 4.28571428578550184252e-01
        val l3 = 3.33333329818377432918e-01
        val l4 = 2.72728123808534006489e-01
        val l5 = 2.30660745775561754067e-01
        val l6 = 2.06975017800338417784e-01
        val p1 = 1.66666666666666019037e-01
        val p2 = -2.77777777770155933842e-03
        val p3 = 6.61375632143793436117e-05
        val p4 = -1.65339022054652515390e-06
        val p5 = 4.13813679705723846039e-08
        val lg2 = 6.93147180559945286227e-01
        val lg2H = 6.93147182464599609375e-01
        val lg2L = -1.90465429995776804525e-09
        val ovt = 8.0085662595372944372e-0017
        val cp = 9.61796693925975554329e-01
        val cpH = 9.61796700954437255859e-01
        val cpL = -7.02846165095275826516e-09
        val ivln2 = 1.44269504088896338700e+00
        val ivln2H = 1.44269502162933349609e+00
        val ivln2L = 1.92596299112661746887e-08

        val hx = hi(x)
        val lx = lo(x)
        val hy = hi(y)
        val ly = lo(y)
        var ix = hx and 0x7fffffff
        val iy = hy and 0x7fffffff
        var z: Double
        var t1: Double
        var t2: Double
        var t: Double
        var u: Double
        var v: Double
        var w: Double
        var i: Int
        var j: Int
        var k: Int
        var n: Int

        if ((iy or ly) == 0) return 1.0
        if (ix > 0x7ff00000 || (ix == 0x7ff00000 && lx != 0) || iy > 0x7ff00000 || (iy == 0x7ff00000 && ly != 0)) {
            return x + y
        }
        var yisint = 0
        if (hx < 0) {
            if (iy >= 0x43400000) {
                yisint = 2
            } else if (iy >= 0x3ff00000) {
                k = (iy shr 20) - 0x3ff
                if (k > 20) {
                    j = ly ushr (52 - k)
                    if ((j shl (52 - k)) == ly) yisint = 2 - (j and 1)
                } else if (ly == 0) {
                    j = iy shr (20 - k)
                    if ((j shl (20 - k)) == iy) yisint = 2 - (j and 1)
                }
            }
        }
        if (ly == 0) {
            if (iy == 0x7ff00000) {
                return if (((ix - 0x3ff00000) or lx) == 0) {
                    y - y
                } else if (ix >= 0x3ff00000) {
                    if (hy >= 0) y else 0.0
                } else {
                    if (hy < 0) -y else 0.0
                }
            }
            if (iy == 0x3ff00000) return if (hy < 0) 1.0 / x else x
            if (hy == 0x40000000) return x * x
            if (hy == 0x3fe00000) {
                if (hx >= 0) return sqrt(x)
            }
        }
        var ax = abs(x)
        if (lx == 0) {
            if (ix == 0x7ff00000 || ix == 0 || ix == 0x3ff00000) {
                z = ax
                if (hy < 0) z = 1.0 / z
                if (hx < 0) {
                    if (((ix - 0x3ff00000) or yisint) == 0) {
                        z = Double.NaN
                    } else if (yisint == 1) {
                        z = -z
                    }
                }
                return z
            }
        }
        n = (hx shr 31) + 1
        if ((n or yisint) == 0) return Double.NaN
        var s = 1.0
        if ((n or (yisint - 1)) == 0) s = -1.0

        if (iy > 0x41e00000) {
            if (iy > 0x43f00000) {
                if (ix <= 0x3fefffff) return if (hy < 0) huge * huge else tiny * tiny
                if (ix >= 0x3ff00000) return if (hy > 0) huge * huge else tiny * tiny
            }
            if (ix < 0x3fefffff) return if (hy < 0) s * huge * huge else s * tiny * tiny
            if (ix > 0x3ff00000) return if (hy > 0) s * huge * huge else s * tiny * tiny
            t = ax - 1.0
            w = (t * t) * (0.5 - t * (0.3333333333333333333333 - t * 0.25))
            u = ivln2H * t
            v = t * ivln2L - w * ivln2
            t1 = withLow(u + v, 0)
            t2 = v - (t1 - u)
        } else {
            n = 0
            if (ix < 0x00100000) {
                ax *= two53
                n -= 53
                ix = hi(ax)
            }
            n += (ix shr 20) - 0x3ff
            j = ix and 0x000fffff
            ix = j or 0x3ff00000
            if (j <= 0x3988E) {
                k = 0
            } else if (j < 0xBB67A) {
                k = 1
            } else {
                k = 0
                n += 1
                ix -= 0x00100000
            }
            ax = withHigh(ax, ix)
            u = ax - BP[k]
            v = 1.0 / (ax + BP[k])
            val ss = u * v
            val sH = withLow(ss, 0)
            var tH = words(((ix shr 1) or 0x20000000) + 0x00080000 + (k shl 18), 0)
            var tL = ax - (tH - BP[k])
            val sL = v * ((u - sH * tH) - sH * tL)
            var s2 = ss * ss
            var r = s2 * s2 * (l1 + s2 * (l2 + s2 * (l3 + s2 * (l4 + s2 * (l5 + s2 * l6)))))
            r += sL * (sH + ss)
            s2 = sH * sH
            tH = withLow(3.0 + s2 + r, 0)
            tL = r - ((tH - 3.0) - s2)
            u = sH * tH
            v = sL * tH + tL * ss
            val pH = withLow(u + v, 0)
            val pL = v - (pH - u)
            val zH = cpH * pH
            val zL = cpL * pH + pL * cp + DP_L[k]
            t = n.toDouble()
            t1 = withLow(((zH + zL) + DP_H[k]) + t, 0)
            t2 = zL - (((t1 - t) - DP_H[k]) - zH)
        }

        val y1 = withLow(y, 0)
        var pL = (y - y1) * t1 + y * t2
        var pH = y1 * t1
        z = pL + pH
        j = hi(z)
        i = lo(z)
        if (j >= 0x40900000) {
            if (((j - 0x40900000) or i) != 0) {
                return s * huge * huge
            } else {
                if (pL + ovt > z - pH) return s * huge * huge
            }
        } else if ((j and 0x7fffffff) >= 0x4090cc00) {
            if (((j - 0xc090cc00.toInt()) or i) != 0) {
                return s * tiny * tiny
            } else {
                if (pL <= z - pH) return s * tiny * tiny
            }
        }
        i = j and 0x7fffffff
        k = (i shr 20) - 0x3ff
        n = 0
        if (i > 0x3fe00000) {
            n = j + (0x00100000 shr (k + 1))
            k = ((n and 0x7fffffff) shr 20) - 0x3ff
            t = words(n and (0x000fffff shr k).inv(), 0)
            n = ((n and 0x000fffff) or 0x00100000) shr (20 - k)
            if (j < 0) n = -n
            pH -= t
        }
        t = withLow(pL + pH, 0)
        u = t * lg2H
        v = (pL - (t - pH)) * lg2 + t * lg2L
        z = u + v
        w = v - (z - u)
        t = z * z
        t1 = z - t * (p1 + t * (p2 + t * (p3 + t * (p4 + t * p5))))
        val r = (z * t1) / ((t1 - 2.0) - (w + z * w))
        z = 1.0 - (r - z)
        j = hi(z)
        j += n shl 20
        z = if ((j shr 20) <= 0) scalbn(z, n) else withHigh(z, hi(z) + (n shl 20))
        return s * z
    }
}
