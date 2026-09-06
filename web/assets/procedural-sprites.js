// Caladon — procedural placeholder sprites (SVG strings).
// Framework-agnostic: every generator returns an SVG markup string. These are placeholders,
// keyed to the asset-registry names in ARCHITECTURE.md ("Asset manifest & rendering").
// Swap a key for a real PNG sprite later without touching the renderer.
//
// Terrain object sprites and entities are drawn OVER a tiled grass ground, back-to-front by (y, x).

(function (root) {
  const SH = "#00000026";
  const p = (a) => a.map((q) => q[0].toFixed(1) + "," + q[1].toFixed(1)).join(" ");
  function rng(seed) { let s = seed >>> 0; return () => { s = (s * 1664525 + 1013904223) >>> 0; return s / 4294967296; }; }
  function shade(hex, d) {
    const n = parseInt(hex.slice(1), 16);
    let r = (n >> 16) + d, g = ((n >> 8) & 255) + d, b = (n & 255) + d;
    r = Math.max(0, Math.min(255, r)); g = Math.max(0, Math.min(255, g)); b = Math.max(0, Math.min(255, b));
    return "#" + ((1 << 24) + (r << 16) + (g << 8) + b).toString(16).slice(1);
  }

  // ---- grass ground: seamless <pattern> (base layer) ----
  function grassPattern(id) {
    const rand = rng(20260905), T = 64, base = "#9ccb63", blades = ["#7fb04d", "#8cbe56", "#b4dc7e"];
    let c = "";
    for (let i = 0; i < 24; i++) {
      const x = rand() * T, y = rand() * T, sh = blades[(rand() * blades.length) | 0], n = 2 + ((rand() * 3) | 0);
      for (const dx of [-T, 0, T]) for (const dy of [-T, 0, T]) {
        const px = x + dx, py = y + dy; if (px < -6 || px > T + 6 || py < -6 || py > T + 6) continue;
        for (let b = 0; b < n; b++) {
          const bx = px + (rand() * 4 - 2), hh = 4 + rand() * 5, ww = 1.1 + rand() * 0.9, ln = (rand() * 2 - 1) * 1.6;
          c += `<polygon points="${(bx - ww).toFixed(1)},${py.toFixed(1)} ${(bx + ww).toFixed(1)},${py.toFixed(1)} ${(bx + ln).toFixed(1)},${(py - hh).toFixed(1)}" fill="${sh}"/>`;
        }
      }
    }
    return `<pattern id="${id}" width="64" height="64" patternUnits="userSpaceOnUse"><rect width="64" height="64" fill="${base}"/>${c}</pattern>`;
  }

  // ---- tree & forest ----
  function tree(x, y, h, w) {
    const G = { d: "#3f6b39", m: "#4e8043", l: "#6fa657", t: "#5b3f27" };
    let s = `<ellipse cx="${x}" cy="${y + 2}" rx="${w * 1.15}" ry="${w * 0.45}" fill="${SH}"/>`;
    s += `<rect x="${x - w * 0.14}" y="${y - h * 0.16}" width="${w * 0.28}" height="${h * 0.22}" rx="${w * 0.06}" fill="${G.t}"/>`;
    for (let k = 2; k >= 0; k--) {
      const by = y - h * 0.12 - k * (h * 0.26), tw = w * (1 - k * 0.20), th = h * 0.42, ap = by - th;
      s += `<polygon points="${x - tw},${by} ${x + tw},${by} ${x},${ap}" fill="${G.m}"/>`;
      s += `<polygon points="${x - tw},${by} ${x},${by} ${x},${ap}" fill="${G.l}"/>`;
      s += `<polygon points="${x + tw},${by} ${x},${by} ${x},${ap}" fill="${G.d}" opacity="0.55"/>`;
    }
    return s;
  }
  function forest(cx, cy, count, seed) {
    const rand = rng(seed * 131 + 9), t = [];
    for (let i = 0; i < count; i++) { const a = rand() * 6.28, r = Math.sqrt(rand()); t.push({ x: cx + Math.cos(a) * r * 70, y: cy + Math.sin(a) * r * 34, h: 40 + rand() * 22, w: 11 + rand() * 4 }); }
    t.sort((p, q) => p.y - q.y); return t.map((o) => tree(o.x, o.y, o.h, o.w)).join("");
  }

  // ---- mountain ----
  function mountain(cx, cy, s = 1) {
    const M = { l: "#bcc0c6", m: "#9296a0", d: "#6f7480", dd: "#565b66", sn: "#f2f6f9", sn2: "#d3dbe2" };
    const W = 64 * s, H = 98 * s, A = [cx - 6 * s, cy - H], S = [cx + 40 * s, cy - 58 * s];
    let o = `<ellipse cx="${cx}" cy="${cy + 4}" rx="${W * 1.02}" ry="${W * 0.32}" fill="${SH}"/>`;
    o += `<polygon points="${p([[cx - W, cy], [cx - 18 * s, cy - 44 * s], A])}" fill="${M.l}"/>`;
    o += `<polygon points="${p([[cx - W, cy], [cx - 18 * s, cy - 44 * s], [cx - 4 * s, cy]])}" fill="${M.m}"/>`;
    o += `<polygon points="${p([[cx - 18 * s, cy - 44 * s], A, [cx + 12 * s, cy - 48 * s], [cx - 4 * s, cy]])}" fill="${M.m}"/>`;
    o += `<polygon points="${p([A, S, [cx + 12 * s, cy - 48 * s]])}" fill="${M.d}"/>`;
    o += `<polygon points="${p([[cx + 12 * s, cy - 48 * s], S, [cx + 34 * s, cy], [cx - 4 * s, cy]])}" fill="${M.d}"/>`;
    o += `<polygon points="${p([S, [cx + W, cy], [cx + 34 * s, cy]])}" fill="${M.dd}"/>`;
    o += `<polygon points="${p([[cx - W * 0.55, cy], [cx - W * 0.28, cy - 18 * s], [cx - W * 0.06, cy]])}" fill="${M.d}" opacity=".45"/>`;
    o += `<polygon points="${p([[cx + W * 0.15, cy], [cx + W * 0.34, cy - 16 * s], [cx + W * 0.5, cy]])}" fill="${M.m}" opacity=".5"/>`;
    o += `<polygon points="${p([A, [cx - 18 * s, cy - 62 * s], [cx - 8 * s, cy - 54 * s], [cx - 1 * s, cy - 64 * s], [cx + 7 * s, cy - 55 * s], [cx + 15 * s, cy - 60 * s]])}" fill="${M.sn}"/>`;
    o += `<polygon points="${p([A, [cx - 1 * s, cy - 64 * s], [cx + 15 * s, cy - 60 * s]])}" fill="${M.sn2}" opacity=".85"/>`;
    o += `<polygon points="${p([S, [cx + 30 * s, cy - 50 * s], [cx + 40 * s, cy - 46 * s], [cx + 50 * s, cy - 50 * s]])}" fill="${M.sn}"/>`;
    return o;
  }

  // ---- lake ----
  function lake(cx, cy, s = 1) {
    const rand = rng(77), n = 14; let shore = [], water = [], deep = [];
    for (let i = 0; i < n; i++) {
      const a = i / n * 6.28, r = 1 + (rand() * 0.28 - 0.14);
      shore.push([cx + Math.cos(a) * 44 * s * r, cy + Math.sin(a) * 24 * s * r]);
      water.push([cx + Math.cos(a) * 37 * s * r, cy + Math.sin(a) * 20 * s * r]);
      deep.push([cx + Math.cos(a) * 26 * s * r, cy + 2 + Math.sin(a) * 13 * s * r]);
    }
    let o = `<polygon points="${p(shore)}" fill="#c9b884"/>`;
    o += `<polygon points="${p(shore.map((q) => [q[0], q[1] - 1]))}" fill="#b6d07a" opacity=".6"/>`;
    o += `<polygon points="${p(water)}" fill="#3b7aa8"/>`;
    o += `<polygon points="${p(deep)}" fill="#356f9c"/>`;
    o += `<polygon points="${p(water.map((q) => [q[0], q[1] - 2]))}" fill="#5aa0cf" opacity=".55"/>`;
    o += `<ellipse cx="${cx - 8 * s}" cy="${cy - 4 * s}" rx="${12 * s}" ry="${2.4 * s}" fill="#a9d6f0" opacity=".55"/>`;
    o += `<ellipse cx="${cx + 9 * s}" cy="${cy + 3 * s}" rx="${7 * s}" ry="${1.6 * s}" fill="#a9d6f0" opacity=".4"/>`;
    return o;
  }

  // ---- free slot ----
  function slot(cx, cy) {
    let o = `<ellipse cx="${cx}" cy="${cy}" rx="26" ry="12" fill="#c9ad76"/>`;
    o += `<ellipse cx="${cx}" cy="${cy}" rx="26" ry="12" fill="none" stroke="#a5854f" stroke-width="1.5"/>`;
    o += `<ellipse cx="${cx}" cy="${cy - 1}" rx="17" ry="7" fill="#b89a5f" opacity=".7"/>`;
    o += `<polygon points="${cx - 9},${cy - 3} ${cx + 9},${cy - 3} ${cx + 9},${cy - 9} ${cx - 9},${cy - 9}" fill="none" stroke="#7d6438" stroke-width="1" stroke-dasharray="2 2" opacity=".7"/>`;
    for (let a = 0; a < 6.28; a += 6.28 / 8) { const px = cx + Math.cos(a) * 25, py = cy + Math.sin(a) * 11.5; o += `<rect x="${(px - 1).toFixed(1)}" y="${(py - 5).toFixed(1)}" width="2" height="6" fill="#6b4a2c"/>`; }
    o += `<line x1="${cx}" y1="${cy - 2}" x2="${cx}" y2="${cy - 30}" stroke="#5b3f27" stroke-width="2.4"/>`;
    o += `<polygon points="${cx},${cy - 30} ${cx + 16},${cy - 25} ${cx},${cy - 20}" fill="#d7a53a"/>`;
    o += `<polygon points="${cx},${cy - 30} ${cx + 16},${cy - 25} ${cx},${cy - 25}" fill="#c08f28"/>`;
    return o;
  }

  // ---- houses & village tiers ----
  function house(x, y, w, roof) {
    const wall = "#efe6cf", roofDk = shade(roof, -28);
    let o = `<ellipse cx="${x}" cy="${y + 1}" rx="${w * 0.85}" ry="${w * 0.3}" fill="${SH}"/>`;
    o += `<rect x="${x - w / 2}" y="${y - w * 0.95}" width="${w}" height="${w * 0.95}" fill="${wall}" stroke="#00000018"/>`;
    o += `<rect x="${x - w * 0.16}" y="${y - w * 0.5}" width="${w * 0.32}" height="${w * 0.5}" fill="#7a5a38"/>`;
    o += `<polygon points="${x - w * 0.66},${y - w * 0.95} ${x + w * 0.66},${y - w * 0.95} ${x},${y - w * 1.6}" fill="${roof}"/>`;
    o += `<polygon points="${x - w * 0.66},${y - w * 0.95} ${x},${y - w * 0.95} ${x},${y - w * 1.6}" fill="${roofDk}"/>`;
    return o;
  }
  function wallRing(cx, cy, rx, ry, towers) {
    let o = `<ellipse cx="${cx}" cy="${cy}" rx="${rx}" ry="${ry}" fill="none" stroke="#7d776a" stroke-width="7"/>`;
    o += `<ellipse cx="${cx}" cy="${cy}" rx="${rx}" ry="${ry}" fill="none" stroke="#cfc9bd" stroke-width="3"/>`;
    o += `<rect x="${cx - 5}" y="${cy + ry - 4}" width="10" height="8" fill="#5b3f27"/>`;
    if (towers) [[-1, -1], [1, -1], [-1, 1], [1, 1]].forEach(([sx, sy]) => { const tx = cx + sx * rx * 0.72, ty = cy + sy * ry * 0.72; o += `<circle cx="${tx}" cy="${ty}" r="6.5" fill="#cfc9bd" stroke="#7d776a" stroke-width="2"/>`; o += `<polygon points="${tx - 6.5},${ty - 4} ${tx + 6.5},${ty - 4} ${tx},${ty - 13}" fill="#8c8677"/>`; });
    return o;
  }
  function banner(x, y, c) { return `<line x1="${x}" y1="${y}" x2="${x}" y2="${y - 18}" stroke="#4a3a24" stroke-width="1.8"/><polygon points="${x},${y - 18} ${x + 12},${y - 14.5} ${x},${y - 11}" fill="${c}"/>`; }
  function village(cx, cy, tier, seed) {
    const rand = rng(seed * 53 + 3); let s = "";
    const roofs = ["#b1442f", "#9c3b2b", "#c05a3a"];
    if (tier >= 2) s += wallRing(cx, cy - 4, tier === 3 ? 62 : 46, tier === 3 ? 32 : 24, tier === 3);
    const n = tier === 1 ? 4 : tier === 2 ? 6 : 9, R = tier === 1 ? 16 : tier === 2 ? 28 : 40, items = [];
    for (let i = 0; i < n; i++) { const a = rand() * 6.28, r = Math.sqrt(rand()) * R; items.push({ x: cx + Math.cos(a) * r, y: cy - 4 + Math.sin(a) * r * 0.5, w: 12 + rand() * 3, roof: roofs[(rand() * 3) | 0] }); }
    items.sort((p, q) => p.y - q.y);
    s += items.map((h) => house(h.x, h.y, h.w, h.roof)).join("");
    if (tier === 3) {
      const kx = cx, ky = cy - 6;
      s += `<ellipse cx="${kx}" cy="${ky + 2}" rx="16" ry="6" fill="${SH}"/>`;
      s += `<rect x="${kx - 11}" y="${ky - 26}" width="22" height="28" fill="#cfc9bd" stroke="#7d776a" stroke-width="2"/>`;
      s += `<rect x="${kx - 11}" y="${ky - 30}" width="6" height="6" fill="#cfc9bd"/><rect x="${kx - 2}" y="${ky - 30}" width="6" height="6" fill="#cfc9bd"/><rect x="${kx + 7}" y="${ky - 30}" width="4" height="6" fill="#cfc9bd"/>`;
      s += banner(kx + 2, ky - 30, "#b23a2e");
    } else if (tier === 2) { s += banner(cx, cy - 24, "#41579d"); }
    return s;
  }

  // ---- barbarian camp ----
  function barbarians(cx, cy, seed) {
    const rand = rng(seed * 29 + 5); let s = `<ellipse cx="${cx}" cy="${cy + 2}" rx="42" ry="16" fill="${SH}"/>`;
    for (let a = Math.PI * 0.15; a < Math.PI * 0.85; a += 0.22) { const px = cx + Math.cos(a) * 40, py = cy - Math.sin(a) * 18 - 4; s += `<polygon points="${px - 2},${py} ${px + 2},${py} ${px},${py - 9}" fill="#6b4a2c"/>`; }
    const items = [];
    for (let i = 0; i < 4; i++) { const a = rand() * 6.28, r = Math.sqrt(rand()) * 22; items.push({ x: cx + Math.cos(a) * r, y: cy + Math.sin(a) * r * 0.5, w: 11 + rand() * 5, hide: rand() < 0.5 }); }
    items.sort((p, q) => p.y - q.y);
    for (const t of items) {
      const c1 = t.hide ? "#b9935f" : "#8a6d43", c2 = t.hide ? "#96774a" : "#6f5636";
      s += `<polygon points="${t.x - t.w},${t.y} ${t.x + t.w},${t.y} ${t.x},${t.y - t.w * 1.6}" fill="${c1}"/>`;
      s += `<polygon points="${t.x - t.w},${t.y} ${t.x},${t.y} ${t.x},${t.y - t.w * 1.6}" fill="${c2}"/>`;
      s += `<line x1="${t.x - 3}" y1="${t.y - t.w * 1.6 + 4}" x2="${t.x + 3}" y2="${t.y - t.w * 1.6 - 4}" stroke="#4a3a24" stroke-width="1.4"/>`;
      s += `<line x1="${t.x + 3}" y1="${t.y - t.w * 1.6 + 4}" x2="${t.x - 3}" y2="${t.y - t.w * 1.6 - 4}" stroke="#4a3a24" stroke-width="1.4"/>`;
    }
    s += `<ellipse cx="${cx}" cy="${cy + 5}" rx="7" ry="3" fill="#3a2a1a"/>`;
    s += `<polygon points="${cx - 5},${cy + 5} ${cx + 5},${cy + 5} ${cx},${cy - 8}" fill="#e8892b"/>`;
    s += `<polygon points="${cx - 2.5},${cy + 5} ${cx + 2.5},${cy + 5} ${cx},${cy - 2}" fill="#f4c542"/>`;
    s += `<line x1="${cx + 30}" y1="${cy}" x2="${cx + 30}" y2="${cy - 22}" stroke="#5b3f27" stroke-width="2"/>`;
    s += `<circle cx="${cx + 30}" cy="${cy - 25}" r="4" fill="#e8e2d2"/>`;
    return s;
  }

  // ---- registry keyed to ARCHITECTURE.md "Asset manifest & rendering" ----
  // Object generators take (x, y, seed?) and return SVG markup anchored at the tile base (x, y).
  const registry = {
    grassPattern,                                  // base ground layer (SVG <pattern>)
    "terrain.forest": (x, y, seed = 1) => forest(x, y, 16, seed),
    "terrain.lake": (x, y) => lake(x, y, 1),
    "terrain.mountain": (x, y) => mountain(x, y, 1),
    "entity.slot": (x, y) => slot(x, y),
    "entity.city.t1": (x, y, seed = 1) => village(x, y, 1, seed),
    "entity.city.t2": (x, y, seed = 1) => village(x, y, 2, seed),
    "entity.city.t3": (x, y, seed = 1) => village(x, y, 3, seed),
    "entity.barbarian": (x, y, seed = 1) => barbarians(x, y, seed),
  };

  // city tier from points (placeholder thresholds; tunable)
  function cityTierKey(points) { return points >= 5000 ? "entity.city.t3" : points >= 1000 ? "entity.city.t2" : "entity.city.t1"; }

  const api = { registry, grassPattern, tree, forest, mountain, lake, slot, village, barbarians, cityTierKey };
  if (typeof module !== "undefined" && module.exports) module.exports = api;
  root.CaladonSprites = api;
})(typeof window !== "undefined" ? window : globalThis);
