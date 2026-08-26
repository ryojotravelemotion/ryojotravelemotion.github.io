/*
 * OurAirports の空港一覧 → 旅情の空港データ に変換する
 *
 * 使い方:
 *   node tools/build-airport-data.js [出力先] [手元のairports.csv]
 * 例:
 *   node tools/build-airport-data.js data/air
 *   （csvを省くと https://davidmegginson.github.io/ourairports-data/ から取ってくる）
 *
 * 出力:
 *   data/air/airports.json   定期便のある空港だけ（世界で約4,300件）
 *
 * 出典: OurAirports (https://ourairports.com/data/)
 *   「All data is released to the Public Domain」と明記されている。
 *   条件としては制限が無いが、先方が謝意を求めているので出典は表示する。
 *
 * ---------------------------------------------------------------------------
 * なぜ空港が要るのか
 *
 * 飛行機の区間を「街の中心から街の中心へ」で描くと、
 * 東京駅から飛び立って首里城に着陸することになる。
 * 実際には羽田から那覇空港へ飛ぶ。
 *
 * 航空路そのもの（空の上の決められた道）は、無償で再配布できる
 * 世界規模のデータが見つからなかった。
 * だが両端を空港にするだけでも、線は実際の形にかなり近づく。
 * たとえば広島空港は市街地の40km東にあるので、
 * 広島を出た線はいったん東へ向かってから南西へ曲がる。実際そう飛ぶ。
 *
 * ---------------------------------------------------------------------------
 * 絞り込みの方針
 *
 * ・定期便のある空港だけ（scheduled_service = yes）
 * ・大きい空港と中くらいの空港だけ。小さな飛行場やヘリポートは外す
 *
 * 全85,945件のうち、残るのは約4,300件。
 */

'use strict';

const fs = require('fs');
const path = require('path');

const OUT = process.argv[2] || path.join(__dirname, '..', 'data', 'air');
const SRC = process.argv[3];

const URL = 'https://davidmegginson.github.io/ourairports-data/airports.csv';

const SIZE = { large_airport: 2, medium_airport: 1 };

// 1行のCSVを、引用符を考慮して分解する
function splitCsv(line) {
  const out = [];
  let cur = '', inQ = false;
  for (let i = 0; i < line.length; i++) {
    const c = line[i];
    if (inQ) {
      if (c === '"') {
        if (line[i + 1] === '"') { cur += '"'; i++; }
        else inQ = false;
      } else cur += c;
    } else if (c === '"') inQ = true;
    else if (c === ',') { out.push(cur); cur = ''; }
    else cur += c;
  }
  out.push(cur);
  return out;
}

/* OurAirports の名前は英語で、keywords に日本語名が入っていることもあるが、
   入っていない空港も多い（伊丹・関西・福岡など）。
   日本語の画面に英語名が混ざると読みにくいので、
   よく使われる空港だけ、こちらで名前を持っておく。
   ここに無いものは keywords → 英語名の順で決まる。 */
const JA_NAMES = {
  HND: '羽田空港',        NRT: '成田国際空港',   ITM: '伊丹空港',
  KIX: '関西国際空港',     UKB: '神戸空港',       NGO: '中部国際空港',
  CTS: '新千歳空港',       OKD: '丘珠空港',       HKD: '函館空港',
  AKJ: '旭川空港',         KUH: '釧路空港',       OBO: '帯広空港',
  MMB: '女満別空港',       WKJ: '稚内空港',       SHB: '中標津空港',
  AOJ: '青森空港',         MSJ: '三沢空港',       AXT: '秋田空港',
  HNA: '花巻空港',         SDJ: '仙台空港',       GAJ: '山形空港',
  SYO: '庄内空港',         FKS: '福島空港',       IBR: '茨城空港',
  KMQ: '小松空港',         TOY: '富山空港',       NTQ: '能登空港',
  FSZ: '静岡空港',         MMJ: '松本空港',       OKJ: '岡山空港',
  HIJ: '広島空港',         IWJ: '石見空港',       IZO: '出雲空港',
  YGJ: '米子空港',         UBJ: '山口宇部空港',   IWK: '岩国錦帯橋空港',
  TAK: '高松空港',         TKS: '徳島空港',       MYJ: '松山空港',
  KCZ: '高知空港',         FUK: '福岡空港',       KKJ: '北九州空港',
  HSG: '佐賀空港',         NGS: '長崎空港',       TSJ: '対馬空港',
  FUJ: '福江空港',         KMJ: '熊本空港',       OIT: '大分空港',
  KMI: '宮崎空港',         KOJ: '鹿児島空港',     TNE: '種子島空港',
  KUM: '屋久島空港',       ASJ: '奄美空港',       OKA: '那覇空港',
  ISG: '石垣空港',         MMY: '宮古空港',       UEO: '久米島空港',
  OGN: '与那国空港',       HAC: '八丈島空港'
};

// keywords に日本語の名前が入っていることがある。あればそちらを使う。
const JA = /[぀-ヿ一-龯]/;

function pickName(name, keywords) {
  const ks = (keywords || '').split(',').map(function (s) { return s.trim(); });
  for (let i = 0; i < ks.length; i++) {
    if (JA.test(ks[i]) && /空港|飛行場/.test(ks[i])) return ks[i];
  }
  for (let i = 0; i < ks.length; i++) {
    if (JA.test(ks[i])) return ks[i];
  }
  return name;
}

async function loadCsv() {
  if (SRC) {
    console.log('手元のファイルを読みます: ' + SRC);
    return fs.readFileSync(SRC, 'utf8');
  }
  console.log('OurAirports から取得しています...');
  const res = await fetch(URL, { headers: { 'User-Agent': 'ryojo-build-airport-data/1.0' } });
  if (!res.ok) throw new Error('取得に失敗: ' + res.status + ' ' + res.statusText);
  return await res.text();
}

async function main() {
  const text = await loadCsv();
  const lines = text.split(/\r?\n/);
  const head = splitCsv(lines[0]);
  const col = {};
  head.forEach(function (h, i) { col[h.replace(/"/g, '')] = i; });

  const airports = [];
  let seen = 0;

  for (let i = 1; i < lines.length; i++) {
    if (!lines[i]) continue;
    seen++;
    const f = splitCsv(lines[i]);
    if (f[col.scheduled_service] !== 'yes') continue;
    const size = SIZE[f[col.type]];
    if (!size) continue;

    const lon = parseFloat(f[col.longitude_deg]);
    const lat = parseFloat(f[col.latitude_deg]);
    if (!isFinite(lon) || !isFinite(lat)) continue;

    const iata = f[col.iata_code] || '';

    airports.push([
      Math.round(lon * 1e5) / 1e5,
      Math.round(lat * 1e5) / 1e5,
      JA_NAMES[iata] || pickName(f[col.name], f[col.keywords]),
      iata,
      size
    ]);
  }

  airports.sort(function (a, b) { return a[0] - b[0]; });

  fs.mkdirSync(OUT, { recursive: true });
  const file = path.join(OUT, 'airports.json');
  fs.writeFileSync(file, JSON.stringify({
    source: 'OurAirports airports.csv',
    attribution: 'OurAirports',
    license: 'Public Domain',
    generated: new Date().toISOString().slice(0, 10),
    fields: ['lon', 'lat', 'name', 'iata', 'size'],
    airports: airports
  }));

  const jp = airports.filter(function (a) { return JA.test(a[2]); });
  console.log('');
  console.log('保存しました: ' + file);
  console.log('  読んだ行 ' + seen + ' → 残した空港 ' + airports.length + ' 件');
  console.log('  うち大きい空港 ' + airports.filter(function (a) { return a[4] === 2; }).length + ' 件');
  console.log('  日本語の名前がついたもの ' + jp.length + ' 件');
  console.log('  大きさ ' + (fs.statSync(file).size / 1024).toFixed(0) + ' KB');
  console.log('');
  console.log('出典: OurAirports（パブリックドメイン）');
}

main().catch(function (err) {
  console.error('失敗しました:', err.message);
  process.exit(1);
});
