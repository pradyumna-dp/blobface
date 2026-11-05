# Upstream analysis: blobatar → blobface

blobface is an unofficial Kotlin Multiplatform port of
[blobatar](https://github.com/Alain00/blobatar) by Alain (@alain_0012). It is not
affiliated with or endorsed by the original author. This document records how
upstream works and which parts need care when porting.

## Pinned upstream

| | |
|---|---|
| Repository | https://github.com/Alain00/blobatar |
| Version ported | **2.7.0** (npm `blobatar@2.7.0`, `@blobatar/react@2.7.0`) |
| Commit | `ebb7ea4808b1263629fc8fa65e2398b9cbdb6f6b` (tag `v2.7.0`, 2026-08-29) |
| License | MIT, `Copyright (c) 2026 Alain` |

The git `main` branch is already tagged `v2.8.0` (`44bdffda`), but **2.8.0 is not on
npm**. We port 2.7.0 so the fixture generator can pin the exact npm package. The
2.7→2.8 diff touches adapters, harness and docs. If 2.8.0 is published later,
re-run the fixtures to check that output has not changed.

Files read: everything under `packages/blobatar/src` (`hash`, `traits`, `render`,
`blobatar`, `blob`, `uri`, `internal`, `index`, `color`, `shape`,
`styles/{blob,compose,shapes}`, `expression`, `morph`, `animate`, `ease`, `idle`,
`motion.css`, `react.tsx`, plus `gaze.ts`/`gaze.css` skimmed), `packages/react/src`,
`packages/blobatar/README.md` and `CHANGELOG.md`, `test/keys.ts`, `test/golden/*`,
`docs/motion-spec.md` (by reference) and `apps/site/docs/editor-spec.md`.

---

## 1. Pipeline overview

```
name ──normalizeSeed──► seedState (32-bit) ──stream(key)──► u∈[0,1) per trait key
                                                │
             overrides (traits / hue / tone) ───┤
                                                ▼
           palette(hue, contrast, tone) ◄── resolve()       layout(t)  (shape band → body → face → decorations → eyes)
                       │                                         │
                       └──────────── expression (bake + tint) ───┤
                                                                 ▼
                                                     render() → SVG string
```

Everything is a pure function of `(name, options)`. There is no sequential PRNG.
Each trait is **addressed by a string key** and hashed independently (see §3).

## 2. Hash function (`hash.ts`)

```ts
feed(h, bytes):   for b in bytes: h = imul(h ^ b, 3432918353); h = (h << 13) | (h >>> 19)
finalize(h):      h = imul(h ^ (h>>>16), 2246822507)
                  h = imul(h ^ (h>>>13), 3266489909)
                  return (h ^ (h>>>16)) >>> 0           // murmur3 fmix32, unsigned
seedState(seed):  s = normalize ? normalizeSeed(seed) : seed
                  return feed(1779033703 ^ s.length, utf8(s))
stream(state,k):  finalize(feed(feed(state, [0xFF]), utf8(k))) / 4294967296
normalizeSeed(s): s.normalize("NFC").trim().toLowerCase()
```

- `3432918353` = `0xCC9E2D51` (murmur3 c1). It is above `Int.MAX_VALUE`, so in
  Kotlin it is `0xCC9E2D51.toInt()`. `imul` is a plain wrapping `Int *`.
- The rotate step is `(h shl 13) or (h ushr 19)`, which is `rotateLeft(13)`.
- The **initial state mixes `s.length`**, which is the **UTF-16 code-unit length** of
  the normalized string, not the byte length or code-point count. Kotlin
  `String.length` is also UTF-16, so the two match.
- Bytes come from `TextEncoder`: **UTF-8, with lone surrogates replaced by U+FFFD
  (`EF BF BD`)**. On JVM, Kotlin `encodeToByteArray()` replaces malformed input with
  `?` (0x3F), so blobface needs its own UTF-8 encoder.
- `h ^ bytes[i]` reads bytes as 0–255. Kotlin `Byte` is signed, so mask with
  `and 0xFF`.
- `finalize` ends with `>>> 0`. Convert with `toUInt().toDouble()`, or
  `(h.toLong() and 0xFFFFFFFFL)`, before dividing by `4294967296.0`. The result is
  exact in a double.

## 3. Trait reader / "PRNG" (`traits.ts`)

There is no generator state. `t(key) = stream(state, key)` is a uniform double in
`[0, 1)`. Helpers:

| helper | formula |
|---|---|
| `t(key)` | override if present (see below), else `stream(state, key)` |
| `num(k,min,max)` | `min + t(k)*(max-min)` |
| `int(k,min,max)` | `min + floor(t(k)*(max-min+1))` |
| `pick(k,opts)` | `opts[floor(t(k)*opts.length)]` |
| `bool(k,p=0.5)` | `t(k) < p` |
| `jitter(k,a)` | `(t(k)*2-1)*a` |

**Overrides** (`traits` option, `Record<string, number | number[]>`):
- A number pins the key. It is clamped: `o > 0 ? (o < 1 ? o : 0.999999) : 0`.
  **NaN becomes 0**, because both comparisons are false.
- A list means "any of these": `v[floor(stream(state,key) * v.length)]`, then the
  same clamp. **An empty list behaves like no override**, because indexing it gives
  `undefined`.
- An override of `0` is valid. It is not treated as missing.
- Reading the same key twice gives the same value. For example `eye.n` is read once
  per eye, so both eyes get the same `n`.

### Complete trait keyspace (gen2)

`shape, hue, tone, body.r, body.ratio, body.x, body.y, body.n, body.rot, body.pts,
body.r0…body.r7, gaze.x, gaze.y, eye.rx, eye.ratio, eye.scale, eye.stretch, eye.gap,
eye.n, eye.lean, eye.lean2, eye.dy, sun.n, sun.dist, sun.r, sun.rot, cloud.n,
cloud.r0…cloud.r5, nub.n, nub.a0, nub.a1, nub.r0, nub.r1, poly.round, capsule.squat,
droplet.tip`, plus motion keys `motion.blink, motion.saccade, motion.lookX,
motion.lookY, motion.phase, motion.bob, motion.blinkPhase, motion.saccadePhase,
motion.lookXFlip, motion.lookYFlip`.

## 4. Layout: how every trait is derived (`styles/compose.ts`, `shapes.ts`, `blob.ts`)

### 4.1 Shape band (`shape` key)
`pick(v)` returns the first band with `v < upTo` (fallback: last band):

| shape | upper edge | p | core | path | face | extra |
|---|---|---|---|---|---|---|
| round | 0.22 | 22% | 1.00 | superellipse | body | – |
| organic | 0.48 | 26% | 0.98 | blobPath (spline) | shrunk(min(radii)·0.95) | – |
| boxy | 0.60 | 12% | 0.86 | superellipse | body | `body.n`∈[3.4,6), `body.rot`∈[-20,20) |
| capsule | 0.70 | 10% | 1.02 | `box` | shrunk(0.94) | `ry *= capsule.squat`∈[.55,.68); 2 end-cap circles r=ry at cx±(rx−ry) |
| nub | 0.79 | 9% | 0.88 | superellipse | body | `nub.n`∈{1,2} circles at angle `nub.a{i}`∈[0,2π), dist rx·0.88, r=rx·`nub.r{i}`∈[.24,.4) |
| cloud | 0.86 | 7% | 0.78 | blobPath | splineFace | `cloud.n`∈{4..6} lobes, a=π+π(i+.5)/n, (cos·rx·.8, sin·rx·.5), r=rx·`cloud.r{i}`∈[.44,.62) |
| droplet | 0.915 | 5.5% | 0.78 | superellipse (n=2) | (cx, cy+.05ry, .88rx, .88ry) | `cy += .22ry`, taper path with `droplet.tip`∈[1.4,1.65) |
| hexagon | 0.95 | 3.5% | 1.05 | polygon(6) | shrunk(0.84) | `body.rot`∈[-12,12), `poly.round`∈[.24,.5) |
| sun | 0.98 | 3% | 0.70 | superellipse | body | `sun.n`∈{6..9} petals, dist rx·`sun.dist`∈[1,1.08), r=rx·`sun.r`∈[.2,.26), offset `sun.rot`∈[0,2π) |
| triangle | 1.00 | 2% | 1.15 | polygon(3) | (cx, cy+.1ry, .54rx, .36ry) | `body.rot`∈[-5,5), `poly.round`∈[.24,.5) |

### 4.2 Body (shared, before the shape patches it)
```
r      = num("body.r", 31, 38) * shape.core
cx     = 50 + jitter("body.x", 1.5);  cy = 50 + jitter("body.y", 1.5)
rx     = r;  ry = r * num("body.ratio", 0.92, 1.08)
n      = num("body.n", 1.9, 2.5);  rot = 0
radii  = [1 + jitter("body.r{i}", 0.16) for i in 0 until int("body.pts", 6, 8)]
```
Then `shape.body?.(t, body)`, then `face = shape.face?.(body) ?? body`, then
`decorate`. Order matters only for which body values the face and decorations
see.

### 4.3 Eyes (`faceFit`)
```
er0 = num(eye.rx,.075,.105)*rx   ratio = num(eye.ratio,1.9,3.2)
scale = num(eye.scale,.78,1.24)  stretch = num(eye.stretch,.85,1.18)
clearance = num(eye.gap,.1,.24)*rx
wide = er0*max(1,scale);  tall = er0*ratio*max(1,scale*stretch)
gap0 = wide + rx*.03 + clearance
gx = jitter(gaze.x,.09)*face.rx;  gy = num(gaze.y,-.2,.08)*face.ry;  dy = jitter(eye.dy,.04)*face.ry
reach = hypot(wide,tall)
need = hypot((|gx|+gap0+reach)/face.rx, (|gy|+|dy|+reach)/face.ry)
fit = need > .9 ? .9/need : 1
er = er0*fit; eyeRy = er*ratio; gap = gap0*fit
room = clamp(clearance/tall, 0, 1); bound = min(12, asin(room)·180/π)
lean = num(eye.lean,-1,1)*bound;  lean2 = clamp(lean + jitter(eye.lean2,3.5), -12, 12)
cx = face.cx + gx*fit; cy = face.cy + gy*fit
left  = (cx-gap, cy,         er,       eyeRy,                 n=num(eye.n,3.5,6), rot=lean)
right = (cx+gap, cy+dy*fit,  er*scale, eyeRy*scale*stretch,   n=num(eye.n,3.5,6), rot=lean2)
```

## 5. Path primitives (`shape.ts`)

All coordinates go through `r2(v) = String(Math.round(v*100)/100)`, and `-0`
prints as `"0"`.

- **superellipse**: `k = min(1, (8·2^(-1/n) − 4)/3)`. It draws 4 cubic Béziers
  through 13 points (a,0),(a,bk),(ak,b),(0,b)… and rotates by `rot`° around
  (cx,cy). Output: `M x y` followed by 4 × `C x y x y x y`, then `Z`.
- **blobPath**: n vertices at `t0 + 2πi/n`, scaled by `rx·m, ry·m`. It is a closed
  Catmull-Rom spline converted to cubic Béziers with tension 1/6.
- **polygon**: vertices start at `rot − 90°`. Each corner is cut by
  `k = clamp(round/2, 0, 0.5)` and drawn as `Q vertex cut`. `L` is emitted only
  when `k < 0.5`. The default `round` is 0.3, but shapes always set it.
- **box**: `M l top H r V bottom H l Z`.
- **taper**: `t = max(1.05, tip)`. It is the tangent lines from the apex to the
  ellipse, with a quadratic ease at the tip.
- `arc` exists but **no gen2 shape uses it**.

## 6. Color and contrast (`color.ts`)

- Colors are authored in **OKLCh**, converted to linear sRGB with the Björn
  Ottosson matrices, then **gamut-mapped by chroma bisection** (12 iterations,
  tolerance 1e-4), clamped to [0,1], gamma-encoded with the sRGB transfer function,
  and written as `#rrggbb` (lowercase, `Math.round(s*255)`).
- **Hue** comes from `opts.hue ?? num("hue", 0, 360)`, in degrees. Out-of-range
  values are allowed.
- **Tone** comes from `opts.tone ?? t("tone")`. Half-open bands select one of six
  swatches. A value ≥ 1 falls back to swatch 0.

| edge | name | L | C |
|---|---|---|---|
| <0.20 | pastel | 0.86 | 0.085 |
| <0.36 | pale neutral | 0.90 | 0.028 |
| <0.62 | mid | 0.73 | 0.135 |
| <0.80 | deep | 0.62 | 0.165 |
| <0.93 | bright | 0.87 | 0.16 |
| <1.00 | ink | 0.34 | 0.035 |

- **Ramp**: `bg = (0.965, 0.01, h)`. `head` = the swatch, pushed by `ensureContrast`
  to at least **1.5:1 against `DARK_SURFACE` (0.145,0,0)**. `eye` = `(0.17,0.02,h)`
  when `head.l ≥ 0.5`, otherwise `(0.97,0.012,h)` (polarity flip).
- **FLOORS** (only applied when `contrast` is true, in order): `head` vs `bg` ≥
  1.25, then `eye` vs `head` ≥ 4.5.
- **ensureContrast**: if the pair already passes, return it. Otherwise step L by
  ±0.02 (clamped), up to 60 steps, starting in the direction it already leans and
  then trying the other direction. As a last resort return black or white with
  c=0.
- WCAG luminance is computed on **linear** gamut-mapped RGB, with no extra
  linearization.
- The `palette` option overrides `bg`/`head`/`eye` hex values **after** the ramp, so
  it bypasses the contrast rules. Overrides are not validated.
- Expression tints (`HOT`, `ROSE`, `BLUSH`, `BILE`) use `tinted()`. It moves the
  head toward the target L by `pull`, sets the target hue, applies a chroma floor,
  enforces the 1.5:1 dark-surface rule, pushes the eye to ≥ 4.55:1, and checks
  contrast at 11 points along the OKLab mix. The final color is
  `mixHex(base, hot, heat)`, mixed in OKLab.
- `fadeHex`: a byte-wise sRGB lerp. The react-native port uses it for morphing.

## 7. Public options and props (with defaults)

`blobatar(name, opts)`:

| option | type | default | effect |
|---|---|---|---|
| `size` | number | — | adds `width`/`height` attributes when truthy (0 → omitted) |
| `background` | `false \| "square" \| "circle" \| "squircle"` (or `true`) | **`false`** (style default) | backdrop path: square `M0 0H100V100H0Z`, circle superellipse n=2, squircle (and `true`) n=6, all r=50 |
| `palette` | `{bg?, head?, eye?}` hex | — | per-key override |
| `hue` | number (deg) | per name | locks hue |
| `tone` | number 0–1 | per name | locks swatch |
| `traits` | `Record<string, number \| number[]>` | — | trait overrides (§3) |
| `normalize` | boolean | `true` | NFC+trim+lowercase |
| `contrast` | boolean | `true` | apply FLOORS |
| `title` | string | — | `<title>` with only `&`, `<`, `>` escaped |
| `animate` | `"hover" \| "always"` | off | **ignored by the string API**; adapters only |
| `expression` | Expression value | `idle` | static pose bake + tint |

`@blobatar/react` `<Blobatar name … />` takes the same props. **It has no default
`size`.** When `size` is omitted the `<img>`/`<svg>` has no width or height, so CSS
decides. When not animating, React renders an `<img src="data:…">` via
`blobatarUri`. When animating, it renders inline `<svg>` with
`role="img"`/`aria-hidden`, the backdrop, and `<g class=cls>` with innerHTML.
The README examples use `size={48}`. That is the size proposed for blobface,
because Compose needs a concrete `Dp`.

Exported utilities: `palette`, `ramp`, `contrast`, `FLOORS`, `traits`,
`normalizeSeed`, `layout` (blob entry), `blobatarUri`, `VERSION`, the 15 expressions
(`idle, happy, sad, mad, surprised, wink, sleepy, smug, unsure, scared, love, shy,
sick, thinking`), and `lerpPose`/`poseTransforms`/`idleAt`/`idleSeeds` (internal and
idle entries).

### Expressions (pose channels)
Each pose sets `esx, esy, tilt, edy, edx, esx2, esy2, tilt2, edy2, lock, heat, shake,
rock, bdy`. `bakePose` applies the eye channels to the geometry (the right eye gets
the `*2` deltas, and `tilt`/`edx` are mirrored per side). `bdy` becomes a wrapper
`<g transform="translate(0 bdy)">` drawn **outside the backdrop**. Numbers use
`r3` (3 decimals). Tints apply to `mad`(HOT), `love`(ROSE), `shy`(BLUSH),
`sick`(BILE).

## 8. SVG output structure (static)

```
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100"[ width="S" height="S"]>
  [<title>…</title>]
  [<path d="BACKDROP" fill="BG"/>]
  [<g transform="translate(0 BDY)">]                 ← only with a non-zero expression bdy
    <g fill="HEAD">
      <circle cx cy r/>…        (petals: capsule caps, nub, cloud lobes, sun)
      <path d="EXTRA"/>…        (droplet taper)
      <path d="BODY"/>
    </g>
    <g fill="EYE"><path d="LEFT"/><path d="RIGHT"/></g>
  [</g>]
</svg>
```
There are no ids, `<defs>`, gradients or filters, and no whitespace between
elements. Attribute order is fixed. Circle values use `r2`, but **without the `-0`
check**. That makes no difference, because template literals also print `-0` as
`"0"`.

## 9. Animations (`motion.css`, `animate.ts`, `idle.ts`, `ease.ts`)

Seeded timings (`motionSeeds`): `blink` = round(num(3500,6500)) ms, `saccade` =
round(num(4200,7600)), `phase` = round(num(0,2800)), `bob` = round(num(0,3400)),
`blinkPhase`/`saccadePhase` ∈ [0, period). `lookX` = r2(num(1,2.2)),
`lookY` = r2(num(.8,1.7)), each with an independent random sign flip.

| layer | target | period | curve | amplitude (× `amp`) |
|---|---|---|---|---|
| hover lift | root | 220ms in / 160ms out | cubic-bezier(.23,1,.32,1) | translateY(−1.5px) scale(1.04) |
| `amp` ramp | root | 400ms | ease-out | 0 → 1 |
| breathe | body+eyes, about viewBox center | 2800ms alternate | ease-in-out | scaleX 1+.022, scaleY 1−.018 |
| bob | body+eyes | 3400ms alternate | ease-in-out | translateY −1.1 |
| blink | each eye, in its leaned frame | `blink` ms | ease-in 97.2→98.6%, ease-out →100% | scaleY 1−.92 |
| saccade | eye pair | `saccade` ms, linear stepped table | 6 fixations (§ SACCADE table) | ±lookX/lookY |
| wrap | each eye | `saccade` | same table | foreshortening scale + small rotate |
| shake | root | 112ms linear | 4 stops | ±0.7 × `shake` (pose) |
| rock | eyes | 900ms ease-in-out | `rockp` 1→−1→1 | edy2 seesaw × `rock` |
| morph | pose channels + fill | 300ms cubic-bezier(.45,.05,.5,1) in / 400ms ease-in-out out | | |

Modes: `"hover"` sets `amp` to 1 only while hovered. `"always"` keeps it at 1.
**On non-hover devices upstream disables hover motion entirely** (`@media not
(hover:hover and pointer:fine)`), and `prefers-reduced-motion` turns all animation
off. `idle.ts` already evaluates every loop as numbers (`idleAt`,
`idleTransforms`) for React Native. The Compose port will mirror that.

**Gaze** (`blobatar/gaze`, `useGaze`): makes the eyes follow the pointer. It needs
a JS driver, projects the eyes onto a sphere, and accepts targets such as
pointer, element, rest and null. It is a separate opt-in entry point and is **not**
a `BlobatarOptions` field.

## 10. JS-specific behavior that affects porting

| concern | upstream behavior | blobface plan |
|---|---|---|
| 32-bit ops | `Math.imul`, `<<`, `|` on int32 | Kotlin `Int` multiply/shl wrap identically |
| `>>>` vs `>>` | `>>>` everywhere in the hash | `ushr`; final `>>> 0` → unsigned via `toUInt()` |
| byte sign | `Uint8Array` 0–255 | `b.toInt() and 0xFF` |
| UTF-16 | `s.length` = code units; `TextEncoder` replaces lone surrogates with U+FFFD | `String.length` matches; **custom UTF-8 encoder** (JVM's encoder emits `?`) |
| NFC | `String.prototype.normalize("NFC")` | not in Kotlin common → `expect/actual`: JVM/Android `java.text.Normalizer`, JS/Wasm `String.normalize`, iOS `precomposedStringWithCanonicalMapping`. Results depend on each platform's Unicode version |
| trim | ECMAScript WhiteSpace + LineTerminator (incl. U+FEFF, U+1680, U+2000–200A, U+202F, U+205F, U+3000, U+2028/9, U+00A0) | custom `jsTrim()`. Kotlin `trim()` uses `isWhitespace`, which differs (e.g. U+FEFF, U+180E, U+001C–1F) |
| lowercase | locale-independent full Unicode mapping (`İ` → `i̇`) | `lowercase()` (invariant root locale). Verify with fixtures |
| `Math.round` | rounds .5 toward +∞ (`-1.5`→`-1`) | `floor(x + 0.5)` with JS's edge-case fix, **not** Kotlin `round()` (half-even) |
| number → string | shortest round-trip, no `.0`, exponent only <1e-6 or ≥1e21 | values are always `n/100` or `n/1000`, so format from the integer `n` (exact) |
| float math | V8 `sin/cos/atan2/asin/cbrt/pow` (fdlibm ports), V8 `hypot` | `kotlin.math` per platform; JVM `Math` may differ by 1 ulp. Usually hidden by the 2-decimal and 8-bit rounding, but fixtures will show any boundary flips. Port V8's `hypot` algorithm if needed |
| `NaN` | override NaN → 0 via comparisons | same comparisons in Kotlin (NaN compares false) |
| array index out of range | `undefined` → falls through (empty override list) | explicit empty check |
| object spread order | palette `{...ramp, ...override}` | map merge |

## 11. Porting decisions to confirm before Step 2

1. **Scope**: port all 14 expressions and the static bake/tint (they are part of
   the `expression` option). **Gaze** (pointer tracking) is a separate opt-in entry
   point and not an option. Proposal: defer it to a later version.
2. **Touch platforms**: upstream turns hover motion **off** on touch. The brief
   asks for press-to-animate on touch, which differs from upstream. Proposal:
   `BlobfaceAnimation.Hover` animates while hovered on pointer devices and while
   pressed on touch, `Always` loops, and `None` is the default.
3. **Default size**: upstream has none. Proposal: `48.dp`, the size used in the
   upstream README.
4. **Reduced motion**: map it to the platform setting where one is available
   (Android animator scale). Otherwise treat it as "not reduced".
5. **API naming**: `Blobface.svg(name, options)` / `blobface(name, options).toSvgString()`,
   `BlobfaceOptions`, `BlobfaceExpression`, `BlobfaceBackground`, all under
   `io.github.pradyumnadp.blobface`.
