/*
 * 国土数値情報 N02（鉄道）→ 旅情の路線データ に変換する
 *
 * 使い方:
 *   node tools/build-rail-data.js <N02のUTF-8フォルダ> [出力先]
 * 例:
 *   node tools/build-rail-data.js ~/Downloads/N02-21_GML/UTF-8 data/rail
 *
 * 出力:
 *   data/rail/index.json        路線一覧（選択UI用・約100KB）
 *   data/rail/stations.json     駅名 → 所属路線と座標
 *   data/rail/lines/<id>.geojson  路線ごとの線形（必要な時だけ読む）
 *
 * 出典: 国土数値情報（鉄道データ）国土交通省
 *   利用にあたっては出典の明記が必要です。
 */

'use strict';

const fs = require('fs');
const path = require('path');

const SRC = process.argv[2];
const OUT = process.argv[3] || path.join(__dirname, '..', 'data', 'rail');

if (!SRC) {
  console.error('使い方: node tools/build-rail-data.js <N02のUTF-8フォルダ> [出力先]');
  process.exit(1);
}

// ---------------------------------------------------------------- コード表
// データから実証的に確認したもの（メタデータには含まれていない）

const RAIL_TYPE = {
  '11': '普通鉄道(JR)',
  '12': '普通鉄道',
  '13': '鋼索鉄道',        // ケーブルカー
  '14': '懸垂式モノレール',
  '15': '跨座式モノレール',
  '16': '案内軌条式',      // 新交通システム・札幌市営地下鉄
  '17': '無軌条電車',      // 立山トロリーバス
  '21': '軌道',            // 路面電車
  '22': '懸垂式モノレール(軌道)',
  '23': '跨座式モノレール(軌道)',
  '24': '案内軌条式(軌道)',
  '25': '浮上式'           // リニモ
};

const OPERATOR_TYPE = {
  '1': 'JR新幹線',
  '2': 'JR在来線',
  '3': '公営',
  '4': '民営',
  '5': '第三セクター'
};

// 旅情での描画上の分類（モードごとに線の太さ・色を変えるため）
function category(railType, opType) {
  if (opType === '1') return 'shinkansen';
  if (railType === '21') return 'tram';
  if (railType === '13') return 'cable';
  if (['14', '15', '22', '23'].includes(railType)) return 'monorail';
  if (['16', '24', '25'].includes(railType)) return 'agt';
  if (railType === '17') return 'trolleybus';
  return 'rail';
}

// ---------------------------------------------------------------- 補助

function readJson(p) {
  return JSON.parse(fs.readFileSync(p, 'utf8'));
}

// 路線IDを作る。事業者名と路線名の両方を使う。
// 「本線」は11社、「鋼索線」は6社が使っているため、路線名だけでは一意にならない。
function lineId(operator, name) {
  return (operator + '_' + name).replace(/[\/\\:*?"<>|\s]/g, '-');
}

// 座標を丸めてキーにする。N02は小数点以下5桁で、端点は完全一致する。
function ptKey(c) {
  return c[0].toFixed(5) + ',' + c[1].toFixed(5);
}

// Douglas-Peucker。始点と終点は必ず残るので、区間どうしの接続は壊れない。
function simplify(coords, tol) {
  if (coords.length <= 2) return coords;

  let maxDist = 0;
  let index = 0;
  const first = coords[0];
  const last = coords[coords.length - 1];

  for (let i = 1; i < coords.length - 1; i++) {
    const d = perpDist(coords[i], first, last);
    if (d > maxDist) { maxDist = d; index = i; }
  }

  if (maxDist <= tol) return [first, last];

  const left = simplify(coords.slice(0, index + 1), tol);
  const right = simplify(coords.slice(index), tol);
  return left.slice(0, -1).concat(right);
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

// 駅はホーム形状のLineStringで入っている。代表点として中点を取る。
function midpoint(coords) {
  let total = 0;
  const acc = [0];
  for (let i = 1; i < coords.length; i++) {
    total += Math.hypot(coords[i][0] - coords[i - 1][0], coords[i][1] - coords[i - 1][1]);
    acc.push(total);
  }
  if (total === 0) return coords[0];

  const half = total / 2;
  for (let i = 1; i < acc.length; i++) {
    if (acc[i] >= half) {
      const t = (half - acc[i - 1]) / (acc[i] - acc[i - 1]);
      return [
        +(coords[i - 1][0] + (coords[i][0] - coords[i - 1][0]) * t).toFixed(6),
        +(coords[i - 1][1] + (coords[i][1] - coords[i - 1][1]) * t).toFixed(6)
      ];
    }
  }
  return coords[coords.length - 1];
}

// 連結成分の数を数える（データ品質の確認用）
function componentCount(segments) {
  const parent = {};
  function find(x) { while (parent[x] !== x) { x = parent[x] = parent[parent[x]]; } return x; }
  function add(x) { if (!(x in parent)) parent[x] = x; }

  segments.forEach(function (seg) {
    const a = ptKey(seg[0]);
    const b = ptKey(seg[seg.length - 1]);
    add(a); add(b);
    const ra = find(a), rb = find(b);
    if (ra !== rb) parent[ra] = rb;
  });

  const roots = new Set(Object.keys(parent).map(find));
  return roots.size;
}

// ---------------------------------------------------------------- 本体

const TOLERANCE = 0.00005;  // 約5m。表示ズームでは差が見えない
const DIGITS = 5;           // N02の元データと同じ桁数

console.log('読み込み中...');
const sections = readJson(path.join(SRC, 'N02-21_RailroadSection.geojson')).features;
const stationFeatures = readJson(path.join(SRC, 'N02-21_Station.geojson')).features;
console.log('  区間 ' + sections.length + ' / 駅 ' + stationFeatures.length);

// --- 路線ごとに区間をまとめる
const lines = new Map();

sections.forEach(function (f) {
  const p = f.properties;
  const id = lineId(p.N02_004, p.N02_003);

  if (!lines.has(id)) {
    lines.set(id, {
      id: id,
      name: p.N02_003,
      operator: p.N02_004,
      railType: RAIL_TYPE[p.N02_001] || p.N02_001,
      operatorType: OPERATOR_TYPE[p.N02_002] || p.N02_002,
      category: category(p.N02_001, p.N02_002),
      segments: []
    });
  }
  lines.get(id).segments.push(f.geometry.coordinates);
});

// --- 駅を路線に結びつける
const stationsByLine = new Map();
const stationIndex = new Map();

stationFeatures.forEach(function (f) {
  const p = f.properties;
  const id = lineId(p.N02_004, p.N02_003);
  const pt = midpoint(f.geometry.coordinates);

  if (!stationsByLine.has(id)) stationsByLine.set(id, new Map());
  const perLine = stationsByLine.get(id);

  // 同じ駅が複線・複数ホームで複数レコードある。最初の1つを代表にする。
  if (!perLine.has(p.N02_005)) perLine.set(p.N02_005, pt);

  if (!stationIndex.has(p.N02_005)) stationIndex.set(p.N02_005, []);
  const entry = stationIndex.get(p.N02_005);
  if (!entry.some(function (e) { return e.line === id; })) {
    entry.push({ line: id, coord: pt });
  }
});

// --- 出力
fs.mkdirSync(path.join(OUT, 'lines'), { recursive: true });

const index = [];
let rawPoints = 0;
let keptPoints = 0;
let broken = [];

lines.forEach(function (line) {
  const simplified = line.segments.map(function (seg) {
    rawPoints += seg.length;
    const s = round(simplify(seg, TOLERANCE), DIGITS);
    keptPoints += s.length;
    return s;
  });

  const comps = componentCount(line.segments);
  if (comps > 1) broken.push({ id: line.id, name: line.operator + ' / ' + line.name, parts: comps });

  // bbox
  let minX = 180, minY = 90, maxX = -180, maxY = -90;
  simplified.forEach(function (seg) {
    seg.forEach(function (c) {
      if (c[0] < minX) minX = c[0];
      if (c[0] > maxX) maxX = c[0];
      if (c[1] < minY) minY = c[1];
      if (c[1] > maxY) maxY = c[1];
    });
  });

  const stations = stationsByLine.get(line.id) || new Map();

  const geojson = {
    type: 'Feature',
    properties: {
      id: line.id,
      name: line.name,
      operator: line.operator,
      railType: line.railType,
      operatorType: line.operatorType,
      category: line.category,
      attribution: '国土数値情報（鉄道データ）国土交通省'
    },
    geometry: { type: 'MultiLineString', coordinates: simplified }
  };

  fs.writeFileSync(
    path.join(OUT, 'lines', line.id + '.geojson'),
    JSON.stringify(geojson)
  );

  index.push({
    id: line.id,
    name: line.name,
    operator: line.operator,
    category: line.category,
    operatorType: line.operatorType,
    bbox: [minX, minY, maxX, maxY],
    parts: comps,
    stations: [...stations.keys()]
  });
});

index.sort(function (a, b) {
  return a.operator.localeCompare(b.operator, 'ja') || a.name.localeCompare(b.name, 'ja');
});

fs.writeFileSync(path.join(OUT, 'index.json'), JSON.stringify({
  source: '国土数値情報（鉄道データ）N02-21 国土交通省',
  attribution: '国土数値情報（鉄道データ）国土交通省',
  generated: new Date().toISOString().slice(0, 10),
  lines: index
}));

const stationOut = {};
[...stationIndex.keys()].sort(function (a, b) { return a.localeCompare(b, 'ja'); })
  .forEach(function (n) { stationOut[n] = stationIndex.get(n); });

fs.writeFileSync(path.join(OUT, 'stations.json'), JSON.stringify({
  attribution: '国土数値情報（鉄道データ）国土交通省',
  stations: stationOut
}));

// --- 結果
function mb(p) { return (fs.statSync(p).size / 1048576).toFixed(2) + ' MB'; }
function dirSize(d) {
  return fs.readdirSync(d).reduce(function (sum, f) {
    return sum + fs.statSync(path.join(d, f)).size;
  }, 0);
}

console.log('');
console.log('路線数        : ' + index.length);
console.log('駅名の異なり数: ' + Object.keys(stationOut).length);
console.log('座標点        : ' + rawPoints.toLocaleString() + ' → ' + keptPoints.toLocaleString() +
            '  (' + (100 - keptPoints / rawPoints * 100).toFixed(1) + '% 削減)');
console.log('');
console.log('index.json    : ' + mb(path.join(OUT, 'index.json')));
console.log('stations.json : ' + mb(path.join(OUT, 'stations.json')));
console.log('lines/ 合計   : ' + (dirSize(path.join(OUT, 'lines')) / 1048576).toFixed(2) + ' MB (' + index.length + 'ファイル)');
console.log('');

if (broken.length) {
  console.log('※ 線形が分断されている路線 ' + broken.length + ' 件（飛び地・支線などで正常な場合もある）');
  broken.slice(0, 10).forEach(function (b) {
    console.log('   ' + b.name + '  → ' + b.parts + '本に分かれている');
  });
}
