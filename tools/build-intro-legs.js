/*
 * 冒頭デモの道路の経路と、写真の在り処を、あらかじめ調べて保存する
 *
 * 使い方:
 *   ブラウザで index.html を開き、開発者ツールのコンソールに
 *   このファイルの中身を貼り付けて実行する。
 *   ファイルが1つダウンロードされるので、data/intro/legs.json として置く。
 *
 *   （Node から実行できないのは、Mapbox の経路案内に鍵が要り、
 *     その鍵はブラウザで動く config.js の中にあるため。）
 *
 * 出力:
 *   data/intro/legs.json
 *
 * ---------------------------------------------------------------------------
 * なぜ必要か
 *
 * 冒頭デモの旅程は毎回まったく同じなのに、
 * 開くたびに Mapbox の経路案内を2回使っていた。
 * 「大洗港→東京」（バス）と「大阪→広島」（車）である。
 *
 * しかもこの2回は、何もせず去っていく人のぶんも消費する。
 * 訪問者の大半は何もせず離脱するので、ここが一番の無駄だった。
 *
 * 写真も同じで、開くたびにWikipediaへ7回（地点の数だけ）問い合わせていた。
 * 写真そのものは変わらないので、毎回探す必要がない。
 *
 * 一度調べて保存してしまえば、以後ゼロになる。
 * 通信が減るぶん、冒頭デモの表示も速くなる。
 *
 * ---------------------------------------------------------------------------
 * 点の間引きについて
 *
 * 経路案内が返す線は細かすぎる（大阪→広島で4,969点、約67mおき）。
 * 動かして見せるだけならそこまで要らないので、
 * ダグラス・ポイカー法で 30m の誤差まで許して減らす。
 * 曲がり角は残り、まっすぐな所だけ間引かれる。
 */

(async function () {
  'use strict';

  const TOLERANCE_M = 30;

  // --- 点を減らす（ダグラス・ポイカー法）
  function perpDist(p, a, b) {
    const kx = Math.cos((a[1] + b[1]) / 2 * Math.PI / 180);
    const ax = a[0] * kx, ay = a[1], bx = b[0] * kx, by = b[1];
    const px = p[0] * kx, py = p[1];
    const dx = bx - ax, dy = by - ay;
    const len2 = dx * dx + dy * dy;
    if (len2 === 0) return Math.hypot(px - ax, py - ay);
    let t = ((px - ax) * dx + (py - ay) * dy) / len2;
    t = Math.max(0, Math.min(1, t));
    return Math.hypot(px - (ax + t * dx), py - (ay + t * dy));
  }

  function simplify(pts, tolDeg) {
    if (pts.length < 3) return pts.slice();
    const keep = new Array(pts.length).fill(false);
    keep[0] = keep[pts.length - 1] = true;
    const stack = [[0, pts.length - 1]];
    while (stack.length) {
      const [s, e] = stack.pop();
      let far = -1, best = tolDeg;
      for (let i = s + 1; i < e; i++) {
        const d = perpDist(pts[i], pts[s], pts[e]);
        if (d > best) { best = d; far = i; }
      }
      if (far > 0) { keep[far] = true; stack.push([s, far], [far, e]); }
    }
    return pts.filter(function (p, i) { return keep[i]; });
  }

  const tolDeg = TOLERANCE_M / 111000;

  // --- 道路を使う区間だけを取り出して計算する
  const legs = {};
  let before = 0, after = 0;

  for (let i = 0; i < INTRO_SPOTS.length - 1; i++) {
    const a = INTRO_SPOTS[i], b = INTRO_SPOTS[i + 1];
    const conf = MODES[b.mode];
    if (!conf || !conf.api) continue;      // 道路を使わない区間は対象外

    // index.html と同じ鍵の作り方をなぞる。ずれると読み込まれない。
    const via = viaCoords(b);
    const key = b.mode + '|' + a.coord.join(',') + '|' +
                via.map(function (v) { return v.join(','); }).join(';') + '|' +
                viaList(b).map(function (v) { return v.line ? v.line.name : ''; }).join(';') + '|' +
                (b.legLine ? b.legLine.id : '') + '|' +
                b.coord.join(',');

    const pts = [a.coord].concat(via.slice(0, 23), [b.coord]);
    const coordStr = pts.map(function (p) {
      return p[0].toFixed(5) + ',' + p[1].toFixed(5);
    }).join(';');
    const url = 'https://api.mapbox.com/directions/v5/mapbox/' + conf.api + '/' + coordStr +
      '?geometries=geojson&overview=full&access_token=' + mapboxgl.accessToken;

    const res = await fetch(url);
    if (!res.ok) { console.error(a.name + ' → ' + b.name + ' : ' + res.status); continue; }
    const json = await res.json();
    const raw = json.routes[0].geometry.coordinates;

    const small = simplify(raw, tolDeg).map(function (p) {
      return [Math.round(p[0] * 1e5) / 1e5, Math.round(p[1] * 1e5) / 1e5];
    });

    before += raw.length;
    after += small.length;
    legs[key] = small;
    console.log(a.name + ' → ' + b.name + ' [' + b.mode + '] ' +
                raw.length + '点 → ' + small.length + '点');
  }

  // --- 写真の在り処を調べる
  const images = {};
  for (let i = 0; i < INTRO_SPOTS.length; i++) {
    const s = INTRO_SPOTS[i];
    try {
      const hit = await wikiByName(s.wiki);
      if (hit && hit.url) {
        images[s.name] = hit.url;
        console.log(s.name + ' の写真: ' + (hit.title || '(題名なし)'));
      } else {
        console.log(s.name + ' の写真: 見つからず');
      }
    } catch (err) {
      console.log(s.name + ' の写真: 取得に失敗 ' + err.message);
    }
  }

  const out = {
    source: 'Mapbox Directions API / Wikipedia',
    note: '冒頭デモの道路経路。INTRO_SPOTS の座標を変えたら作り直すこと。',
    tolerance_m: TOLERANCE_M,
    generated: new Date().toISOString().slice(0, 10),
    legs: legs,
    images: images
  };

  const text = JSON.stringify(out);
  console.log('経路 合計 ' + before + '点 → ' + after + '点');
  console.log('写真 ' + Object.keys(images).length + ' 件 / ファイル ' +
              Math.round(text.length / 1024) + ' KB');

  const blob = new Blob([text], { type: 'application/json' });
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = 'legs.json';
  document.body.appendChild(a);
  a.click();
  a.remove();
  console.log('legs.json をダウンロードしました。data/intro/ に置いてください。');
})();
