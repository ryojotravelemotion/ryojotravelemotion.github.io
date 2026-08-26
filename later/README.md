# Mapbox GL JS ハンズオン教材

JavaScript がほぼ初めての人向け。**npm もビルドツールも使わず、HTMLファイルをブラウザで開くだけ**で全ステップ動きます。
題材は「英国8ヶ月の旅」。最終的に、スクロール連動の旅行ストーリーマップが完成します。

対象バージョン: Mapbox GL JS v3（本教材作成時点で v3.28.1）

---

## 1. トークンを取得する（最初に1回だけ）

Mapbox は地図データを配信するサービスなので、利用にはトークン（利用者を識別する鍵）が要ります。

1. https://account.mapbox.com/auth/signup/ でアカウントを作る
2. ログイン後のダッシュボードに **Default public token** が表示される（`pk.` で始まる長い文字列）
3. それをコピーする
4. `config.js` を開き、`'pk.ここに自分のトークンを貼り付ける'` の**クォートの中身だけ**を差し替える

```js
const MAPBOX_TOKEN = 'pk.eyJ1IjoieXV0YWhhcnJ5MiIsImEiOiJjbXN0Mnh5MmswaWI0MzVuNDdiYXFkMTMwIn0.Yv54u0V7xW7F75PkN4ooWw';
```

### 料金について

無料枠は**月50,000回の地図読み込み**。学習中にこれを超えることはまず起きません。
ただし公開サイトに載せる場合は、Mapbox の管理画面で **URL制限（token restrictions）** を必ず設定してください。トークンを他人に使われると、自分の請求になります。

> 補足：`config.js` にトークンを書く方式は、ブラウザから丸見えです。これは Mapbox の公開トークン（`pk.`）では正常な使い方で、だからこそ URL 制限で守ります。`sk.` で始まる秘密トークンは絶対にフロントに書かないでください。

---

## 2. ファイルの開き方

### 推奨：VS Code + Live Server

1. [VS Code](https://code.visualstudio.com/) をインストール
2. 拡張機能で **Live Server** を検索してインストール
3. このフォルダを VS Code で開く
4. HTMLファイルを右クリック →「Open with Live Server」

保存するたびにブラウザが自動で更新されるので、学習効率が段違いです。

### 簡易：ダブルクリック

HTMLファイルをそのままダブルクリックしても、多くの環境では動きます。
もし地図が真っ白なら、上の Live Server 方式に切り替えてください。

### つまずいたら必ず開発者ツールを見る

**F12キー**（または右クリック →「検証」）→ **Console** タブ。
赤いエラーが出ていたら、それが原因です。ここを見る習慣が、学習速度を一番大きく変えます。

よくあるエラー:

| 表示 | 原因 |
|---|---|
| `Unauthorized` / 401 | トークンが間違っている、または貼り付けミス |
| `MAPBOX_TOKEN is not defined` | `config.js` が読み込めていない。同じフォルダにあるか確認 |
| 地図が真っ白・高さ0 | `#map` にCSSで height が指定されていない |
| 変な場所が表示される | 座標を `[緯度, 経度]` の順で書いている（正しくは `[経度, 緯度]`） |

---

## 3. 学習の進め方

**順番にファイルを開き、末尾の「やってみよう」を必ず手で試してください。**
読むだけでは身につきません。壊して直す回数が、そのまま理解度になります。

| ステップ | ファイル | 学ぶこと |
|---|---|---|
| 0 | `step0-first-map.html` | 地図を1枚出す / 座標の順序 / `const`・オブジェクト・配列 |
| 1 | `step1-map-options.html` | スタイル / カメラ4要素（center・zoom・pitch・bearing）/ コントロール |
| 2 | `step2-marker-popup.html` | マーカー / ポップアップ / メソッドチェーン / 自作ピン |
| 3 | `step3-many-markers.html` | 配列とオブジェクト / `forEach` / `fitBounds` / データと処理の分離 |
| 4 | `step4-camera-animation.html` | `flyTo`・`easeTo` / **イベントとコールバック** / `console.log` |
| 5 | `step5-buttons-tour.html` | DOM操作 / `addEventListener` / 関数 / `let`と`const` / 自動ツアー |
| 6 | `step6-geojson-layers.html` | **GeoJSON / Source と Layer / 式（expressions）/ `setData`** |
| 7 | `step7-scroll-story.html` | スクロール連動ストーリーマップ / `IntersectionObserver` |
| 8 | `step8-3d-terrain.html` | 3D地形 / 空 / Standard の設定 / `slot` |

### 特に重要な3つ

- **Step 4 のイベント**（`map.on('load', ...)`）— JavaScript最大の山です。「今すぐ動かない処理」に慣れてください
- **Step 6 の Source / Layer** — Mapbox の心臓部。ここを理解しているかで作れる物の幅が決まります
- **Step 7** — Yutaさんの目標そのものです

### 目安ペース

1日1〜2ステップ、1週間で一巡。
そのあと **Step 7 を土台に、自分の写真と文章を入れた作品** に作り変えてください。それがそのままポートフォリオ v1 になります。

---

## 4. Mapbox でできること（全体像）

今回扱ったのは **Mapbox GL JS**（地図を表示するJSライブラリ）です。Mapbox にはこれ以外にもサービスがあります。

### 本教材で扱う範囲

| 機能 | ステップ |
|---|---|
| 地図表示・スタイル | 0, 1 |
| カメラ操作とアニメーション | 4, 5, 7 |
| マーカー・ポップアップ | 2, 3 |
| GeoJSONデータの描画（点・線・面） | 6 |
| データ連動のスタイリング（式） | 6 |
| 3D地形・3D建物・空・時刻 | 1, 8 |

### 今後必要になったら調べるもの

| 機能 | 概要 |
|---|---|
| **Mapbox Studio** | ブラウザ上で地図デザインを作るGUIツール。コードを書かずに独自スタイルが作れる |
| **Geocoding API** | 「London」↔ 座標 の相互変換。検索ボックスを作るときに使う |
| **Directions API** | 2点間の経路（車・徒歩・自転車）と所要時間を取得 |
| **Isochrone API** | 「ここから30分で行ける範囲」を面で取得 |
| **Static Images API** | 地図を画像(PNG)として取得。OGP画像やメール添付に |
| **クラスタリング** | 大量の点を自動でまとめる。`cluster: true` をSourceに足すだけ |
| **カスタムレイヤー** | Three.js等で3Dモデルを地図上に置く |

### MapLibre との関係

**MapLibre GL JS** は Mapbox GL JS v1 から派生したオープンソース版で、APIがほぼ同じです。
この教材で学んだ知識の大半はそのまま通用します。

| | Mapbox GL JS | MapLibre GL JS |
|---|---|---|
| 料金 | 無料枠あり、超過で課金 | ライブラリは完全無料 |
| トークン | 必須 | 不要（ただし地図タイルの配信元は別途必要） |
| Standard スタイル・3D建物 | あり | 一部は自前で用意する必要あり |
| 地図データの品質 | 高い（商用サービス） | 配信元による |

**判断の目安**: 学習・ポートフォリオは Mapbox が楽。仕事で費用やベンダーロックインが問題になったら MapLibre を検討。まずは Mapbox で問題ありません。

---

## 5. React に移すとき（Phase 1後半）

この教材は素のJavaScriptですが、ロードマップ通り React に進むときは考え方がこう変わります。

- `document.getElementById` で要素を探す → JSX と `useRef`
- 地図インスタンスは `useRef` に保持し、`useEffect` の中で1回だけ生成する
- ボタンのHTML生成 → `cities.map(...)` で JSX を返す（Step 3・5 でやったことと同じ発想）
- `react-map-gl` というラッパーもありますが、**先に素のGL JSを理解してから**使ってください。ラッパーから入ると、問題が起きたときに何も分からなくなります

Step 3 と Step 5 で「データを配列に置いて、そこから画面を生成する」書き方を練習したのは、これが React の中心的な考え方だからです。意識して手を動かしておくと移行が楽になります。

---

## 6. 公式ドキュメント

分からなくなったら、まずここです。英語ですが、**コード例が豊富なので英語が完璧でなくても十分読めます**。むしろ技術英語に慣れる良い教材です。

- [Mapbox GL JS ドキュメント](https://docs.mapbox.com/mapbox-gl-js/guides/)
- [Examples（コード例集・最重要）](https://docs.mapbox.com/mapbox-gl-js/example/)
- [API リファレンス](https://docs.mapbox.com/mapbox-gl-js/api/)
- [Style Specification（式・レイヤー種別の一覧）](https://docs.mapbox.com/style-spec/guides/)
- [Mapbox 料金](https://www.mapbox.com/pricing)
- [MapLibre GL JS](https://maplibre.org/maplibre-gl-js/docs/)

**おすすめの使い方**: Examples ページを開いて、面白そうなものを片っ端から開く。気に入ったらコードをコピーして自分のファイルで動かし、値をいじって壊してみる。これが一番速いです。
