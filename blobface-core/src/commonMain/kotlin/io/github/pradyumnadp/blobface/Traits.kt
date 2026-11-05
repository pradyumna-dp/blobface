package io.github.pradyumnadp.blobface

import io.github.pradyumnadp.blobface.internal.seedState
import io.github.pradyumnadp.blobface.internal.stream
import kotlin.math.floor

/**
 * A fixed value for one trait key, in the same 0–1 units the hash would have
 * produced. See [BlobfaceOptions.traits].
 */
public sealed interface TraitOverride {
    /** Pins the key to [value], clamped into [0, 1). `NaN` becomes 0. */
    public data class Fixed(public val value: Double) : TraitOverride

    /**
     * Narrows the key to one of [values], chosen per name by the key's own hash.
     * An empty list behaves exactly like no override.
     */
    public data class AnyOf(public val values: List<Double>) : TraitOverride

    /** Convenience constructors. */
    public companion object {
        /** `TraitOverride.Fixed(value)`. */
        public fun of(value: Double): TraitOverride = Fixed(value)

        /** `TraitOverride.AnyOf(values)`. */
        public fun anyOf(vararg values: Double): TraitOverride = AnyOf(values.toList())
    }
}

/**
 * A deterministic trait reader for one name.
 *
 * Every value is addressed by a string key rather than drawn from a sequence, so
 * reading `"eye.gap"` always returns the same number for the same name regardless
 * of which other keys were read. This mirrors blobatar's `traits()` exactly.
 */
public class BlobfaceTraits internal constructor(
    private val state: Int,
    private val overrides: Map<String, TraitOverride>,
) {
    /** Uniform double in [0, 1) for [key] (or its override). */
    public operator fun invoke(key: String): Double {
        val o: Double? = when (val v = overrides[key]) {
            null -> null
            is TraitOverride.Fixed -> v.value
            is TraitOverride.AnyOf -> v.values.getOrNull(floor(stream(state, key) * v.values.size).toInt())
        }
        if (o == null) return stream(state, key)
        // Same comparisons as upstream, so NaN falls to 0 and 1 to just under it.
        return if (o > 0) (if (o < 1) o else 0.999999) else 0.0
    }

    /** Uniform double in [min, max). */
    public fun num(key: String, min: Double, max: Double): Double = min + this(key) * (max - min)

    /** Uniform integer in [min, max]. */
    public fun int(key: String, min: Int, max: Int): Int = min + floor(this(key) * (max - min + 1)).toInt()

    /** Uniform choice among [options]. */
    public fun <T> pick(key: String, options: List<T>): T = options[floor(this(key) * options.size).toInt()]

    /** True with probability [p]. */
    public fun bool(key: String, p: Double = 0.5): Boolean = this(key) < p

    /** Symmetric jitter in [-amount, amount). */
    public fun jitter(key: String, amount: Double): Double = (this(key) * 2 - 1) * amount

    /** Factory. */
    public companion object {
        /**
         * Creates the trait reader for [seed].
         *
         * @param normalize apply [normalizeSeed] first (default true).
         * @param overrides per-key overrides; see [BlobfaceOptions.traits].
         */
        public fun of(
            seed: String,
            normalize: Boolean = true,
            overrides: Map<String, TraitOverride> = emptyMap(),
        ): BlobfaceTraits = BlobfaceTraits(seedState(seed, normalize), overrides)
    }
}

/** Every trait key the current generation reads, in upstream `test/keys.ts` order. */
public object BlobfaceTraitKeys {
    /** Layout and color keys. */
    public val ALL: List<String> = buildList {
        addAll(listOf("shape", "hue", "tone", "body.r", "body.ratio", "body.x", "body.y", "body.n", "body.rot", "body.pts"))
        for (i in 0 until 8) add("body.r$i")
        addAll(listOf("gaze.x", "gaze.y", "eye.rx", "eye.ratio", "eye.scale", "eye.stretch", "eye.gap", "eye.n", "eye.lean", "eye.lean2", "eye.dy"))
        addAll(listOf("sun.n", "sun.dist", "sun.r", "sun.rot", "cloud.n"))
        for (i in 0 until 6) add("cloud.r$i")
        addAll(listOf("nub.n", "nub.a0", "nub.a1", "nub.r0", "nub.r1", "poly.round", "capsule.squat", "droplet.tip"))
    }

    /** Idle-motion timing keys. */
    public val MOTION: List<String> = listOf(
        "motion.blink", "motion.saccade", "motion.lookX", "motion.lookY", "motion.phase", "motion.bob",
        "motion.blinkPhase", "motion.saccadePhase", "motion.lookXFlip", "motion.lookYFlip",
    )
}
