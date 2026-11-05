// Generates blobface parity fixtures from the upstream npm package.
//
//   cd tools && npm ci && node generate-fixtures.mjs
//
// Output: blobface-core/src/commonTest/resources/fixtures/*.json
//
// The upstream version is pinned in package.json (blobatar 2.7.0, commit
// ebb7ea4808b1263629fc8fa65e2398b9cbdb6f6b). Regenerating against any other
// version is a deliberate act: the fixtures ARE the compatibility contract.

import { mkdirSync, writeFileSync, readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

import { blobatar, palette, ramp, contrast, normalizeSeed, VERSION } from "blobatar";
import { blobatarUri } from "blobatar/uri";
import { _layout, _marks, _parts, fadeHex, poseTransforms, lerpPose } from "blobatar/internal";
import * as X from "blobatar/expression";
import { idleSeeds, idleAt, idleTransforms } from "blobatar/idle";
import { project, step, pursuit, threshold, smoothstep } from "blobatar/gaze";

const here = dirname(fileURLToPath(import.meta.url));
const out = join(here, "..", "blobface-core", "src", "commonTest", "resources", "fixtures");
mkdirSync(out, { recursive: true });

const pkg = JSON.parse(readFileSync(join(here, "node_modules", "blobatar", "package.json"), "utf8"));
if (pkg.version !== "2.7.0" || VERSION !== "2.7.0") {
  throw new Error(`expected blobatar 2.7.0, found ${pkg.version}`);
}

const EXPRESSIONS = [
  "idle", "happy", "sad", "mad", "surprised", "wink", "sleepy", "smug",
  "unsure", "scared", "love", "shy", "sick", "thinking",
];

// ---------------------------------------------------------------------------
// Deterministic input corpus (~500 names).

let seed = 0x2545f491;
const rand = () => {
  // xorshift32, only to pick corpus strings reproducibly.
  seed ^= seed << 13; seed >>>= 0;
  seed ^= seed >>> 17;
  seed ^= seed << 5; seed >>>= 0;
  return seed / 4294967296;
};
const ALPHA = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789._-+@ ";
const randAscii = (n) => Array.from({ length: n }, () => ALPHA[Math.floor(rand() * ALPHA.length)]).join("");

const names = [
  "", " ", "   ", "\t\n", " ", "﻿", "　x　", "᠎", "\u0085x",
  "a", "A", "z", "0", "alain", "Alain", "ALAIN", "alaim", "alain@example.com", "Alain@Example.com",
  "  alain@example.com  ", "\talain@example.com\n", "john.doe", "Jane Doe", "jane doe", "JaNe DoE",
  "user-42", "user_42", "bot", "github-actions[bot]", "dependabot", "root", "admin", "test", "Test User",
  "café", "café", "CAFÉ", "naïve", "Zoë", "Ångström", "Ægir", "straße", "STRASSE", "İstanbul", "ıi",
  "ΟΔΟΣ", "Σίσυφος", "ΣΑΣ", "Ελλάδα", "Москва", "Привет мир", "日本語", "東京", "中文名字", "한국어", "ไทย",
  "العربية", "עברית", "हिन्दी", "தமிழ்", "ქართული", "Հայերեն", "ⓐⓑⓒ", "ℌ𝔢𝔩𝔩𝔬", "𝐀𝐁𝐂",
  "🦊", "🦊🐱", "👍🏽", "👨‍👩‍👧‍👦", "🏳️‍🌈", "🇯🇵", "❤️", "a🦊b", "🦊 fox", "\ud83e", "\udd8a", "x\ud800y", "\udc00\ud800",
  "Ǆ", "ǅ", "ǆ", "ﬁ", "Ω", "Ω", "Å", "K", "Ⅻ", "①", "ﬀ", "ǰ", "ΐ",
  "a".repeat(100), "b".repeat(1000), "long name ".repeat(200), "🦊".repeat(300), "é".repeat(500),
  "mixed CASE Name", "MiXeD.CaSe@ExAmPlE.CoM", "with\u0000null", "tab\there", "new\nline", "\r\n",
  "<script>", "a&b", "\"quoted\"", "'single'", "back\\slash", "100%", "#hash", "{braces}", "[x]", "`tick`", "^|",
];
for (let i = 0; names.length < 420; i++) names.push(randAscii(1 + Math.floor(rand() * 24)));
for (let i = 0; i < 40; i++) names.push(`user${i}@example.com`);
for (let i = 0; i < 40; i++) names.push(`Person ${String.fromCharCode(65 + (i % 26))}${i}`);

// ---------------------------------------------------------------------------
// Option variations, encoded in a JSON-safe form the Kotlin tests decode.
//   expression: name string; traits: {k: number|number[]|"NaN"}; background as upstream.

const variants = [
  {},
  { size: 48 },
  { background: "squircle" }, { background: "circle" }, { background: "square" }, { background: true }, { background: false },
  { hue: 0 }, { hue: 200 }, { hue: -45 }, { hue: 725.5 },
  { tone: 0 }, { tone: 0.25 }, { tone: 0.5 }, { tone: 0.7 }, { tone: 0.9 }, { tone: 0.999 }, { tone: 1 },
  { hue: 30, tone: 0.95 },
  { contrast: false }, { contrast: false, tone: 0.999, hue: 90 },
  { normalize: false },
  { title: "Alain's avatar" }, { title: "a <b> & c" }, { title: "" },
  { palette: { head: "#ff0000" } }, { palette: { bg: "#000000", eye: "#ffffff" } },
  { traits: { shape: 0.95 } }, { traits: { shape: [0.11, 0.825, 0.965] } }, { traits: { shape: [] } },
  { traits: { "eye.ratio": 0, "eye.gap": 1 } }, { traits: { "eye.gap": -3, "body.r": 7 } },
  { traits: { "body.r": "NaN", hue: 0.5 } }, { traits: { hue: 0.5 }, hue: 120 },
  { traits: { tone: [0.1, 0.99] } }, { traits: { "body.pts": 0.999, "body.r0": 0, "body.r7": 0.99 } },
  ...[0.11, 0.35, 0.54, 0.65, 0.745, 0.825, 0.8875, 0.9325, 0.965, 0.99].map((s) => ({ traits: { shape: s } })),
  ...EXPRESSIONS.map((e) => ({ expression: e })),
  { expression: "mad", palette: { head: "#123456" } },
  { expression: "love", tone: 0.999 }, { expression: "sick", hue: 300, tone: 0.95 },
  { expression: "happy", background: "squircle", size: 64, title: "hi" },
];

const decode = (o) => {
  const opts = { ...o };
  if (o.expression) opts.expression = X[o.expression];
  if (o.traits) {
    opts.traits = Object.fromEntries(Object.entries(o.traits).map(([k, v]) => [k, v === "NaN" ? NaN : v]));
  }
  return opts;
};

const layoutJson = (l) => ({
  shape: l.shape,
  palette: l.palette,
  body: { cx: l.body.cx, cy: l.body.cy, rx: l.body.rx, ry: l.body.ry, n: l.body.n, rot: l.body.rot,
    radii: l.body.radii, sides: l.body.sides ?? null, round: l.body.round ?? null },
  face: { cx: l.face.cx, cy: l.face.cy, rx: l.face.rx, ry: l.face.ry },
  petals: l.petals,
  eyes: l.eyes,
});

// ---------------------------------------------------------------------------
// 1. names × default options: traits (layout), svg, uri.
const cases = [];
for (const name of names) {
  cases.push({
    name,
    normalized: normalizeSeed(name),
    svg: blobatar(name),
    uri: blobatarUri(name),
    layout: layoutJson(_layout(name)),
  });
}
writeFileSync(join(out, "names.json"), JSON.stringify({ version: VERSION, cases }));

// 2. option variants × a subset of names.
const subset = names.filter((_, i) => i % 25 === 0).slice(0, 16);
const optionCases = [];
for (const v of variants) {
  for (const name of subset) {
    const opts = decode(v);
    const parts = _parts(name, { ...opts, animate: "hover" });
    const m = _marks(name, opts);
    optionCases.push({
      name,
      options: v,
      svg: blobatar(name, opts),
      layout: layoutJson(_layout(name, opts)),
      marks: m.marks,
      transform: m.transform,
      parts: { cls: parts.cls, bg: parts.bg ?? null, inner: parts.inner, vars: parts.vars },
      partsAlways: _parts(name, { ...opts, animate: "always" }).cls,
    });
  }
}
writeFileSync(join(out, "options.json"), JSON.stringify({ version: VERSION, cases: optionCases }));

// 3. Motion: seeds, idle frames, idle transforms, pose transforms, lerp.
const motion = [];
for (const name of names.slice(0, 60)) {
  const s = idleSeeds(name);
  const l = _layout(name);
  const frames = [];
  for (const t of [0, 16, 123.4, 999, 2800, 4321.5, 5600, 9999, 61234]) {
    for (const amp of [0, 0.5, 1]) {
      const f = idleAt(s, t, amp, 0.55);
      const e = X[EXPRESSIONS[Math.floor(t) % EXPRESSIONS.length]];
      frames.push({ t, amp, shake: 0.55, frame: f, expression: e === X.idle ? "idle" : EXPRESSIONS[Math.floor(t) % EXPRESSIONS.length],
        transforms: idleTransforms(l, e.p, f) });
    }
  }
  const poses = EXPRESSIONS.map((n) => ({ expression: n, rockp: [1, -0.3], eyes: [poseTransforms(l, X[n].p, 1).eyes, poseTransforms(l, X[n].p, -0.3).eyes] }));
  motion.push({ name, seeds: s, frames, poses });
}
const lerps = [];
for (const [a, b] of [["idle", "mad"], ["happy", "sad"], ["thinking", null], [null, "love"]]) {
  for (const t of [0, 0.25, 0.5, 1]) lerps.push({ a, b, t, pose: lerpPose(a && X[a].p, b && X[b].p, t) });
}
writeFileSync(join(out, "motion.json"), JSON.stringify({ version: VERSION, motion, lerps }));

// 4. Color utilities and gaze math.
const colors = [];
for (let h = 0; h < 360; h += 17.5) {
  for (const tone of [0, 0.3, 0.5, 0.7, 0.85, 0.95]) {
    for (const enforce of [true, false]) colors.push({ hue: h, tone, enforce, palette: palette(h, enforce, tone), ramp: ramp(h, enforce, tone) });
  }
}
const pairs = [];
for (const [a, b] of [[{ l: 0.5, c: 0.1, h: 20 }, { l: 0.9, c: 0.02, h: 200 }], [{ l: 0.2, c: 0.3, h: 270 }, { l: 0.97, c: 0, h: 0 }]]) {
  pairs.push({ a, b, contrast: contrast(a, b) });
}
const fades = [];
for (const [a, b] of [["#000000", "#ffffff"], ["#d0d897", "#0f1006"], ["#ff0000", "#00ff00"], ["#12ab", "zz"]]) {
  for (const t of [0, 0.33, 0.5, 1]) fades.push({ a, b, t, out: fadeHex(a, b, t) });
}
const gaze = { project: [], step: [], pursuit: [], threshold: [], smoothstep: [] };
for (const m of [{ x: 0, y: 0 }, { x: -0.4, y: -0.1 }, { x: 0.45, y: 0.05 }, { x: 0.9, y: 0.9 }, { x: 1.4, y: 0 }]) {
  for (const [yaw, pitch] of [[0, 0], [0.3, -0.2], [-1.2, 0.8], [2, 2], [-3, 0.1]]) gaze.project.push({ m, yaw, pitch, p: project(m, yaw, pitch) });
}
for (const i of [
  { x: 0, y: 0, dx: 100, dy: 0, radius: 50, k: 0.2 },
  { x: 0.5, y: 0.1, dx: -10, dy: 5, radius: 50, k: 0.13 },
  { x: -1, y: 1, dx: 300, dy: -400, radius: 20, k: 0.5, snap: 0.5, gain: 0.8 },
  { x: 0, y: 0, dx: 0, dy: 0, radius: 0, k: 1 },
]) gaze.step.push({ i, s: step(i) });
for (const dt of [0, 8, 16, 33.3, 64]) gaze.pursuit.push({ dt, v: pursuit(dt), v0: pursuit(dt, 0), v200: pursuit(dt, 200) });
for (const [w, tr] of [[48, 3], [200, 3], [100, 0], [10, 1], [500, 20]]) gaze.threshold.push({ w, tr, v: threshold(w, tr) });
for (const t of [0, 0.1, 0.5, 0.9, 1]) gaze.smoothstep.push({ t, v: smoothstep(t) });
writeFileSync(join(out, "utils.json"), JSON.stringify({ version: VERSION, colors, pairs, fades, gaze }));

console.log(`blobatar ${VERSION}: ${cases.length} names, ${optionCases.length} option cases, ${motion.length} motion cases → ${out}`);
