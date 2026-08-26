/*
 * OpenStreetMap の航路（route=ferry）→ 旅情の航路データ に変換する
 *
 * 使い方:
 *   node tools/build-ferry-data.js [出力先]
 * 例:
 *   node tools/build-ferry-data.js data/ferry
 *
 * 出力:
 *   data/ferry/index.json          航路一覧（近くの航路を探すのに使う）
 *   data/ferry/lines/<id>.geojson  航路ごとの線形（必要な時だけ読む）
 *
 * 出典: © OpenStreetMap contributors
 *   OpenStreetMap のデータは ODbL という条件で公開されています。
 *   これを加工して作った data/ferry/ も、同じ ODbL で提供する必要があります。
 *   （index.html そのものは ODbL になりません。約束が及ぶのはデータの側だけです。）
 *
 * ---------------------------------------------------------------------------
 * なぜ OpenStreetMap なのか
 *
 * 国土数値情報 N09（定期旅客航路）も調べたが、856航路すべてが
 * 「港と港を結ぶ2点の直線」でしかなく、航路の形を持っていなかった。
 *
 * OpenStreetMap は違う。100km を超える航路は例外なく実際の航跡で描かれていて、
 * 大洗〜苫小牧は226点・741.8km（N09の公式値751kmと1.2%差）だった。
 * 海岸線と突き合わせても、陸を1度も横切らない。
 *
 * ---------------------------------------------------------------------------
 * 仕組み
 *
 * Overpass API（OpenStreetMap に問い合わせるための公開サーバー）に
 * 1回だけ問い合わせて、日本周辺の航路をまとめて受け取る。
 * あとは鉄道データと同じ形に整えて保存するだけ。
 *
 * 同じ航路が複数の線に分かれていることがあるので、
 * 事業者名と航路名が同じものは1つにまとめている。
 */

'use strict';

const fs = require('fs');
const path = require('path');

const OUT = process.argv[2] || path.join(__dirname, '..', 'data', 'ferry');

const ENDPOINT = 'https://overpass-api.de/api/interpreter';

// 日本の周辺。鉄道データ（国土数値情報）と同じ範囲に揃えてある。
const BBOX = [24, 122, 46, 154];   // 南, 西, 北, 東

const QUERY = '[out:json][timeout:240];\n' +
  'way[route=ferry](' + BBOX.join(',') + ');\n' +
  'out geom;';

// 短すぎるものは港の中の渡し船。旅程の区間としては使わないので落とす。
const MIN_KM = 0.5;

// ファイル名に使えない文字を置き換える
function safeName(s) {
  return String(s).replace(/[\\/:*?"<>|\s]+/g, '_').replace(/^_+|_+$/g, '');
}

function haversine(a, b) {
  const R = 6371;
  const p1 = a[1] * Math.PI / 180, p2 = b[1] * Math.PI / 180;
  const dp = p2 - p1, dl = (b[0] - a[0]) * Math.PI / 180;
  const h = Math.sin(dp / 2) * Math.sin(dp / 2) +
            Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
  return 2 * R * Math.asin(Math.sqrt(h));
}

function lengthOf(coords) {
  let d = 0;
  for (let i = 0; i < coords.length - 1; i++) d += haversine(coords[i], coords[i + 1]);
  return d;
}

async function main() {
  console.log('OpenStreetMap に問い合わせています（1回だけ・少し時間がかかります）...');

  const res = await fetch(ENDPOINT, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
      'User-Agent': 'ryojo-build-ferry-data/1.0'
    },
    body: new URLSearchParams({ data: QUERY })
  });

  if (!res.ok) throw new Error('Overpass API: ' + res.status + ' ' + res.statusText);
  const json = await res.json();
  const ways = (json.elements || []).filter(function (e) {
    return e.type === 'way' && e.geometry && e.geometry.length >= 2;
  });
  console.log('受け取った航路の線: ' + ways.length + ' 本');

  // --- 事業者名＋航路名が同じものを1つにまとめる
  const groups = new Map();

  ways.forEach(function (w) {
    const t = w.tags || {};
    const name = t.name || t.ref || ('way' + w.id);
    const operator = t.operator || '';
    const key = operator + '|' + name;

    const coords = w.geometry.map(function (p) { return [p.lon, p.lat]; });
    if (lengthOf(coords) < MIN_KM) return;

    if (!groups.has(key)) {
      groups.set(key, { name: name, operator: operator, parts: [], from: t.from, to: t.to });
    }
    groups.get(key).parts.push(coords);
  });

  console.log('まとめた結果: ' + groups.size + ' 航路');

  // --- 保存
  fs.mkdirSync(path.join(OUT, 'lines'), { recursive: true });

  // 前回の残りを消しておく（航路が減ったときに古いファイルが残らないように）
  fs.readdirSync(path.join(OUT, 'lines')).forEach(function (f) {
    if (f.endsWith('.geojson')) fs.unlinkSync(path.join(OUT, 'lines', f));
  });

  const index = [];
  const used = new Set();

  groups.forEach(function (g) {
    let id = safeName((g.operator ? g.operator + '_' : '') + g.name);
    if (!id) return;
    // 同じ名前が重なったら番号を足す
    let n = 2, base = id;
    while (used.has(id)) id = base + '_' + (n++);
    used.add(id);

    let minX = 180, minY = 90, maxX = -180, maxY = -90;
    let km = 0;
    g.parts.forEach(function (seg) {
      km += lengthOf(seg);
      seg.forEach(function (c) {
        if (c[0] < minX) minX = c[0];
        if (c[0] > maxX) maxX = c[0];
        if (c[1] < minY) minY = c[1];
        if (c[1] > maxY) maxY = c[1];
      });
    });

    const geojson = {
      type: 'Feature',
      properties: {
        id: id,
        name: g.name,
        operator: g.operator,
        category: 'ferry',
        attribution: '© OpenStreetMap contributors'
      },
      geometry: { type: 'MultiLineString', coordinates: g.parts }
    };

    fs.writeFileSync(path.join(OUT, 'lines', id + '.geojson'), JSON.stringify(geojson));

    index.push({
      id: id,
      name: g.name,
      operator: g.operator,
      category: 'ferry',
      bbox: [minX, minY, maxX, maxY],
      parts: g.parts.length,
      km: Math.round(km * 10) / 10
    });
  });

  index.sort(function (a, b) {
    return (a.operator || '').localeCompare(b.operator || '', 'ja') ||
           a.name.localeCompare(b.name, 'ja');
  });

  fs.writeFileSync(path.join(OUT, 'index.json'), JSON.stringify({
    source: 'OpenStreetMap (route=ferry) via Overpass API',
    attribution: '© OpenStreetMap contributors',
    license: 'ODbL 1.0',
    generated: new Date().toISOString().slice(0, 10),
    lines: index
  }));

  // --- 結果
  function dirSize(d) {
    return fs.readdirSync(d).reduce(function (sum, f) {
      return sum + fs.statSync(path.join(d, f)).size;
    }, 0);
  }

  const long = index.filter(function (l) { return l.km >= 100; });
  console.log('');
  console.log('保存しました: ' + OUT);
  console.log('  航路 ' + index.length + ' 本（うち100km超 ' + long.length + ' 本）');
  console.log('  lines/ 合計 ' + (dirSize(path.join(OUT, 'lines')) / 1048576).toFixed(2) + ' MB');
  console.log('  index.json ' + (fs.statSync(path.join(OUT, 'index.json')).size / 1024).toFixed(0) + ' KB');
  console.log('');
  console.log('出典: © OpenStreetMap contributors (ODbL 1.0)');
}

main().catch(function (err) {
  console.error('失敗しました:', err.message);
  process.exit(1);
});
