/*
 * 冒頭デモの経路と、写真の在り処を、あらかじめ調べて保存する
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
 * 開くたびに一から作り直していた。
 *
 *   ・道路の経路      Mapbox の経路案内を呼ぶ（お金の数に入る）
 *   ・線路の経路      data/rail/ の路線ファイルを何本も読む
 *   ・航路           data/ferry/ の一覧（189KB）と航路ファイルを読む
 *   ・空港           data/air/airports.json（182KB）を読む
 *   ・写真の在り処    Wikipedia に地点の数だけ問い合わせる
 *
 * しかも、何もせず去っていく人のぶんも全部かかる。
 * 訪問者の大半は何もせず離脱するので、ここが一番の無駄だった。
 *
 * 一度作って保存してしまえば、以後は 1ファイル読むだけで済む。
 *
 * ---------------------------------------------------------------------------
 * どうやって作るか
 *
 * 旅情の本体と同じ buildLegBetween を呼んで、その結果をそのまま保存する。
 * 経路の作り方をここに書き写すと、本体を直したときにずれるため。
 *
 * buildLegBetween は作った経路を legCache に仕舞うので、
 * 空にしてから呼び、あとで中身を取り出せばよい。
 * 鍵の作り方も本体のものがそのまま使われるので、食い違わない。
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

  // --- 経路を作る。本体と同じ道筋を通す。
  Object.keys(legCache).forEach(function (k) { delete legCache[k]; });
  introLegs = [];

  let before = 0;
  for (let i = 0; i < INTRO_SPOTS.length - 1; i++) {
    const a = INTRO_SPOTS[i], b = INTRO_SPOTS[i + 1];
    const coords = await buildLegBetween(a, b);
    before += coords.length;
    console.log(a.name + ' → ' + b.name + ' [' + b.mode + '] ' + coords.length + '点');
  }

  // --- legCache に仕舞われたものを、間引いて取り出す
  const legs = {};
  let after = 0;
  Object.keys(legCache).forEach(function (k) {
    const small = simplify(legCache[k], tolDeg).map(function (p) {
      return [Math.round(p[0] * 1e5) / 1e5, Math.round(p[1] * 1e5) / 1e5];
    });
    after += small.length;
    legs[k] = small;
  });

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
    source: 'Mapbox Directions / 国土数値情報 / OpenStreetMap / OurAirports / Wikipedia',
    note: '冒頭デモの経路と写真。INTRO_SPOTS を変えたら作り直すこと。',
    tolerance_m: TOLERANCE_M,
    generated: new Date().toISOString().slice(0, 10),
    legs: legs,
    images: images
  };

  const text = JSON.stringify(out);
  console.log('経路 ' + Object.keys(legs).length + '区間 / ' +
              before + '点 → ' + after + '点');
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
