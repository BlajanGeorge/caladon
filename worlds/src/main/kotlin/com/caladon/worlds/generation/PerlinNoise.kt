package com.caladon.worlds.generation

import java.util.Random
import kotlin.math.floor

/** Classic 2D Perlin gradient noise with a seed-shuffled permutation table. Output in roughly [-1, 1]. */
class PerlinNoise(seed: Long) {
    private val perm = IntArray(512)

    init {
        val p = IntArray(256) { it }
        val random = Random(seed)
        for (i in 255 downTo 1) {
            val j = random.nextInt(i + 1)
            val t = p[i]; p[i] = p[j]; p[j] = t
        }
        for (i in 0 until 512) perm[i] = p[i and 255]
    }

    fun noise(x: Double, y: Double): Double {
        val fx = floor(x)
        val fy = floor(y)
        val xi = fx.toInt() and 255
        val yi = fy.toInt() and 255
        val xf = x - fx
        val yf = y - fy
        val u = fade(xf)
        val v = fade(yf)

        val aa = perm[perm[xi] + yi]
        val ab = perm[perm[xi] + yi + 1]
        val ba = perm[perm[xi + 1] + yi]
        val bb = perm[perm[xi + 1] + yi + 1]

        val x1 = lerp(grad(aa, xf, yf), grad(ba, xf - 1, yf), u)
        val x2 = lerp(grad(ab, xf, yf - 1), grad(bb, xf - 1, yf - 1), u)
        return lerp(x1, x2, v)
    }

    /** Fractal Brownian motion: several octaves of [noise] summed with decreasing amplitude. */
    fun fbm(x: Double, y: Double, octaves: Int = 4, lacunarity: Double = 2.0, gain: Double = 0.5): Double {
        var sum = 0.0
        var amplitude = 1.0
        var frequency = 1.0
        var norm = 0.0
        repeat(octaves) {
            sum += amplitude * noise(x * frequency, y * frequency)
            norm += amplitude
            amplitude *= gain
            frequency *= lacunarity
        }
        return sum / norm
    }

    private fun fade(t: Double) = t * t * t * (t * (t * 6 - 15) + 10)
    private fun lerp(a: Double, b: Double, t: Double) = a + t * (b - a)
    private fun grad(hash: Int, x: Double, y: Double): Double =
        when (hash and 3) {
            0 -> x + y
            1 -> -x + y
            2 -> x - y
            else -> -x - y
        }
}
