/*
 * 国土数値情報 N03（行政区域）→ 都道府県界 に変換する
 *
 * 使い方:
 *   node --max-old-space-size=4096 tools/build-admin-data.js <N03のgeojson> [出力先]
 * 例:
 *   node --max-old-space-size=4096 tools/build-admin-data.js ~/Downloads/N03-20230101_GML/N03-23_230101.geojson data/admin
 *
 * 出力:
 *   data/admin/prefectures.geojson   都道府県の境界線（47件）
 *   data/admin/labels.geojson        都道府県名を置く点（47件）
 *
 * 出典: 国土数値情報（行政区域データ）国土交通省
 *
 * ---------------------------------------------------------------------------
 * 仕組み
 *
 * N03 は市区町村の面データで、704MB・約123,000件ある。
 * ここから都道府県界だけを取り出すには、市区町村の境界を「溶かす」必要がある。
 *
 * 面の合成（union）は本来やっかいだが、N03 は位相を持って作られていて、
 * 隣り合う市区町村は同じ座標列を共有している。そこで:
 *
 *   1. 全ての頂点を格子に丸める（共有辺が確実に一致するように）
 *   2. 辺を数える
 *   3. 2回出てきた辺は「内側」なので捨てる  ← ここで市区町村界が消える
 *   4. 1回しか出てこない辺をつなぎ直す      ← 都道府県界と海岸線が残る
 *
 * 面の重なり計算をせずに済み、1回のストリーム処理で終わる。
 *
 * 注意: 格子に丸めるのは辺を数える前でなければならない。
 * 先に簡略化すると、共有辺が一致しなくなって相殺できない。
 */

'use strict';

const fs = require('fs');
const path = require('path');
const readline = require('readline');

const SRC = process.argv[2];
const OUT = process.argv[3] || path.join(__dirname, '..', 'data', 'admin');

if (!SRC) {
  console.error('使い方: node --max-old-space-size=4096 tools/build-admin-data.js <N03のgeojson> [出力先]');
  process.exit(1);
}

const GRID = 1e-4;      // 約11m。共有辺を一致させるための丸め
const TOLERANCE = 0.001; // 約110m。出力時の簡略化
const DIGITS = 5;

// ---------------------------------------------------------------- 節点の管理

const nodeIndex = new Map();   // 格子座標のキー → 通し番号
const nodeCoord = [];          // 通し番号 → [lon, lat]

function nodeOf(c) {
  const gx = Math.round(c[0] / GRID);
  const gy = Math.round(c[1] / GRID);
  const key = gx * 1e7 + gy;

  let i = nodeIndex.get(key);
  if (i === undefined) {
    i = nodeCoord.length;
    nodeIndex.set(key, i);
    nodeCoord.push([gx * GRID, gy * GRID]);
  }
  return i;
}

// ---------------------------------------------------------------- 都道府県ごとの辺

const prefs = new Map();  // 都道府県名 → { edges: Map<辺キー, 出現回数> }

const SHIFT = 8388608;    // 2^23。節点番号を2つ1つの数値に詰める

// N03 は3文字の県だけ「県」を落として入っている（神奈川・和歌山・鹿児島）。
// 表示や検索で他と揃わなくなるので、読み込む時点で補う。
const SHORT = { '神奈川': '神奈川県', '和歌山': '和歌山県', '鹿児島': '鹿児島県' };

function prefName(n) { return n ? (SHORT[n] || n) : n; }

function prefOf(name) {
  if (!prefs.has(name)) prefs.set(name, { name: name, edges: new Map() });
  return prefs.get(name);
}

function addRing(pref, ring) {
  let prev = nodeOf(ring[0]);
  const first = prev;

  for (let i = 1; i < ring.length; i++) {
    const cur = nodeOf(ring[i]);
    if (cur === prev) continue;          // 丸めで潰れた点は飛ばす
    countEdge(pref, prev, cur);
    prev = cur;
  }
  if (prev !== first) countEdge(pref, prev, first);
}

function countEdge(pref, a, b) {
  const key = a < b ? a * SHIFT + b : b * SHIFT + a;
  pref.edges.set(key, (pref.edges.get(key) || 0) + 1);
}

// ---------------------------------------------------------------- 読み込み

let features = 0;
let rings = 0;

const rl = readline.createInterface({
  input: fs.createReadStream(SRC, { encoding: 'utf8' }),
  crlfDelay: Infinity
});

console.log('読み込み中... (704MBあるので数分かかります)');

rl.on('line', function (line) {
  const s = line.trim();
  if (!s.startsWith('{ "type": "Feature"') && !s.startsWith('{"type":"Feature"')) return;

  let f;
  try { f = JSON.parse(s.replace(/,$/, '')); } catch (e) { return; }
  if (!f.geometry) return;

  const name = prefName(f.properties && f.properties.N03_001);
  if (!name) return;

  const pref = prefOf(name);
  const g = f.geometry;
  const polys = g.type === 'Polygon' ? [g.coordinates]
              : g.type === 'MultiPolygon' ? g.coordinates : [];

  polys.forEach(function (poly) {
    poly.forEach(function (ring) {
      if (ring.length > 3) { addRing(pref, ring); rings++; }
    });
  });

  features++;
  if (features % 20000 === 0) {
    const mb = Math.round(process.memoryUsage().heapUsed / 1048576);
    console.log('  ' + features.toLocaleString() + ' 件 / 節点 ' +
                nodeCoord.length.toLocaleString() + ' / メモリ ' + mb + 'MB');
  }
});

rl.on('close', function () {
  console.log('  読み込み完了: ' + features.toLocaleString() + ' 件 / 環 ' + rings.toLocaleString());
  console.log('  節点 ' + nodeCoord.length.toLocaleString() + ' / 都道府県 ' + prefs.size);
  console.log('');
  build();
});

// ---------------------------------------------------------------- 境界の抽出

function build() {
  fs.mkdirSync(OUT, { recursive: true });

  // 各都道府県の「1回しか出てこない辺」＝その県の外周
  // それを全国で数え直すと:
  //   2県が持つ辺 → 県境（陸の上の境界）
  //   1県だけの辺 → 海岸線
  //
  // 航空写真の上では海岸線は写真自体に写っているので、
  // 本当に必要なのは前者だけ。分けて出力する。
  const owners = new Map();   // 辺 → [県名, ...]

  [...prefs.values()].forEach(function (pref) {
    pref.edges.forEach(function (count, key) {
      if (count !== 1) return;          // 2回 = 市区町村どうしの内側
      if (!owners.has(key)) owners.set(key, []);
      owners.get(key).push(pref.name);
    });
  });

  const borderEdges = [];
  const coastEdges = new Map();         // 県名 → 辺の配列

  owners.forEach(function (names, key) {
    if (names.length >= 2) borderEdges.push(key);
    else {
      if (!coastEdges.has(names[0])) coastEdges.set(names[0], []);
      coastEdges.get(names[0]).push(key);
    }
  });

  console.log('');
  console.log('県境の辺  : ' + borderEdges.length.toLocaleString());
  console.log('海岸線の辺: ' + [...coastEdges.values()].reduce(function (a, b) { return a + b.length; }, 0).toLocaleString());
  console.log('');

  // --- 県境
  const borderLines = chain(borderEdges).map(function (c) { return simplifyChain(c); })
    .filter(function (c) { return c.length > 1; });

  write('borders.geojson', {
    type: 'FeatureCollection',
    features: [{
      type: 'Feature',
      properties: { kind: '県境', attribution: '国土数値情報（行政区域データ）国土交通省' },
      geometry: { type: 'MultiLineString', coordinates: borderLines }
    }]
  });

  // --- 海岸線（県ごと。小さすぎる島は捨てる）
  const MIN_SPAN = 0.008;   // 約900m。これ未満の島は表示上見えない
  const coastFeatures = [];
  const labelFeatures = [];
  let dropped = 0;

  [...prefs.values()].forEach(function (pref) {
    const edges = coastEdges.get(pref.name) || [];
    const lines = [];

    chain(edges).forEach(function (c) {
      const coords = c.map(function (i) { return nodeCoord[i]; });
      if (span(coords) < MIN_SPAN) { dropped++; return; }
      const s = simplifyChain(c);
      if (s.length > 1) lines.push(s);
    });

    if (!lines.length) return;

    coastFeatures.push({
      type: 'Feature',
      properties: { name: pref.name, attribution: '国土数値情報（行政区域データ）国土交通省' },
      geometry: { type: 'MultiLineString', coordinates: lines }
    });

    // 名前を置く点: 一番広がりの大きい環の中心（本土側になる）
    const main = lines.reduce(function (a, b) { return span(b) > span(a) ? b : a; });
    labelFeatures.push({
      type: 'Feature',
      properties: { name: pref.name },
      geometry: { type: 'Point', coordinates: center(main) }
    });
  });

  // 内陸県は海岸線を持たないので、県境から名前の位置を作る
  [...prefs.values()].forEach(function (pref) {
    if (labelFeatures.some(function (f) { return f.properties.name === pref.name; })) return;
    const own = [];
    owners.forEach(function (names, key) {
      if (names.indexOf(pref.name) >= 0) own.push(key);
    });
    const cs = chain(own);
    if (!cs.length) return;
    const main = cs.map(function (c) { return c.map(function (i) { return nodeCoord[i]; }); })
                   .reduce(function (a, b) { return span(b) > span(a) ? b : a; });
    labelFeatures.push({
      type: 'Feature',
      properties: { name: pref.name },
      geometry: { type: 'Point', coordinates: center(main) }
    });
  });

  write('coastline.geojson', { type: 'FeatureCollection', features: coastFeatures });
  write('labels.geojson', { type: 'FeatureCollection', features: labelFeatures });

  console.log('県境の線     : ' + borderLines.length + ' 本');
  console.log('海岸線を持つ県: ' + coastFeatures.length + ' / 小島を除外: ' + dropped);
  console.log('名前の点     : ' + labelFeatures.length);
  console.log('');
  console.log('borders.geojson   : ' + mb('borders.geojson') + '  ← 航空写真にはこれだけでよい');
  console.log('coastline.geojson : ' + mb('coastline.geojson'));
  console.log('labels.geojson    : ' + mb('labels.geojson'));
}

// 辺の集合を、つながった線の並びに直す
function chain(keys) {
  const adj = new Map();
  keys.forEach(function (key) {
    const a = Math.floor(key / SHIFT);
    const b = key - a * SHIFT;
    if (!adj.has(a)) adj.set(a, []);
    if (!adj.has(b)) adj.set(b, []);
    adj.get(a).push(b);
    adj.get(b).push(a);
  });

  const used = new Set();
  const out = [];
  function ek(a, b) { return a < b ? a * SHIFT + b : b * SHIFT + a; }

  function walk(start) {
    const c = [start];
    let cur = start;
    for (;;) {
      const next = (adj.get(cur) || []).find(function (n) { return !used.has(ek(cur, n)); });
      if (next === undefined) break;
      used.add(ek(cur, next));
      c.push(next);
      cur = next;
      if (cur === start) break;
    }
    return c;
  }

  // 端のある線（分岐点・行き止まり）から先に、そのあと閉じた環
  const ends = [...adj.keys()].filter(function (n) { return adj.get(n).length !== 2; });
  ends.concat([...adj.keys()]).forEach(function (n) {
    while ((adj.get(n) || []).some(function (m) { return !used.has(ek(n, m)); })) {
      const c = walk(n);
      if (c.length > 2) out.push(c);
    }
  });
  return out;
}

function simplifyChain(c) {
  const coords = c.map(function (i) { return nodeCoord[i]; });
  return round(simplify(coords, TOLERANCE), DIGITS);
}

function span(coords) {
  let minX = 180, minY = 90, maxX = -180, maxY = -90;
  coords.forEach(function (c) {
    if (c[0] < minX) minX = c[0];
    if (c[0] > maxX) maxX = c[0];
    if (c[1] < minY) minY = c[1];
    if (c[1] > maxY) maxY = c[1];
  });
  return Math.max(maxX - minX, maxY - minY);
}

function center(coords) {
  let sx = 0, sy = 0;
  coords.forEach(function (c) { sx += c[0]; sy += c[1]; });
  return [+(sx / coords.length).toFixed(4), +(sy / coords.length).toFixed(4)];
}

function write(name, data) {
  fs.writeFileSync(path.join(OUT, name), JSON.stringify(data));
}
function mb(name) {
  return (fs.statSync(path.join(OUT, name)).size / 1048576).toFixed(2) + ' MB';
}

// ---------------------------------------------------------------- 簡略化

function simplify(coords, tol) {
  if (coords.length <= 2) return coords;

  let maxDist = 0, index = 0;
  const first = coords[0], last = coords[coords.length - 1];

  for (let i = 1; i < coords.length - 1; i++) {
    const d = perpDist(coords[i], first, last);
    if (d > maxDist) { maxDist = d; index = i; }
  }
  if (maxDist <= tol) return [first, last];

  return simplify(coords.slice(0, index + 1), tol)
    .slice(0, -1)
    .concat(simplify(coords.slice(index), tol));
}

function perpDist(p, a, b) {
  let x = a[0], y = a[1];
  let dx = b[0] - x, dy = b[1] - y;

  if (dx !== 0 || dy !== 0) {
    const t = ((p[0] - x) * dx + (p[1] - y) * dy) / (dx * dx + dy * dy);
    if (t > 1) { x = b[0]; y = b[1]; }
    else if (t > 0) { x += dx * t; y += dy * t; }
  }
  dx = p[0] - x; dy = p[1] - y;
  return Math.sqrt(dx * dx + dy * dy);
}

function round(coords, digits) {
  const f = Math.pow(10, digits);
  return coords.map(function (c) {
    return [Math.round(c[0] * f) / f, Math.round(c[1] * f) / f];
  });
}
