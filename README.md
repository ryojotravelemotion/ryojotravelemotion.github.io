# 時刻表（Android アプリ）

現在地の近くの駅を探し、駅ごとに「次の電車」を方面別に一覧で出す、時刻表に特化した Android アプリです。

## できること

- **近くの駅の一覧**：現在地から 500m / 1km / 2km 以内の駅を近い順に表示。徒歩の目安つき
- **次の電車**：路線・方面ごとに、これから出る 3 本（時刻・あと何分・種別・行き先・終電）
- **駅の時刻表**：駅の路線を押すと、昔ながらの「時・分」の形の時刻表。方面と平日／土曜／休日を切り替え可能。
  種別は色、行き先は略号で見分け、次の電車を強調、過ぎた列車は薄く表示
- **駅名で探す**：位置情報が使えないときも、駅名で探せる
- **今日のダイヤを自動で選ぶ**：土日・祝日（振替休日・国民の休日を含む）・年末年始を判定。深夜 0〜2 時台は前日のダイヤとして扱う
- **電波が無くても**：読み込んだ時刻表は端末に控えておき、通信できないときはそれを出す

## データ

時刻表は [公共交通オープンデータセンター（ODPT）](https://www.odpt.org/) の API から取ってきます。
使うには、[開発者サイト](https://developer.odpt.org/) で無料登録してアクセストークンをもらい、次のどれかで設定します。

1. アプリの「設定」画面で入力する（いちばん簡単）
2. `local.properties` に `odpt.apiKey=あなたのトークン` と書いてビルドする
3. GitHub の Secrets に `ODPT_API_KEY` を入れておき、GitHub Actions でビルドする

載っている会社・路線は ODPT で駅時刻表（`odpt:StationTimetable`）が公開されているものに限られます。

## ビルド

```sh
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # 単体テスト
```

Android Studio で開いてもそのまま動きます。push するたびに GitHub Actions が APK を作るので、
Actions の実行結果の「Artifacts」から `jikokuhyo-debug-apk` をダウンロードして端末に入れることもできます。

## つくり

| 場所 | 中身 |
| --- | --- |
| `data/OdptClient.kt` | ODPT API の呼び出しと、応答のファイルへの控え |
| `data/OdptParser.kt` | JSON-LD の応答を読む |
| `data/TimetableRepository.kt` | 駅・時刻表と、路線名・駅名などの名前の表 |
| `domain/ServiceDay.kt` | 祝日の計算、ダイヤの切り替え時刻、平日／休日ダイヤの選び方 |
| `domain/Board.kt` | 駅のまとめ方、距離、次の電車の選び方 |
| `location/LocationProvider.kt` | 現在地（Google Play 開発者サービスなしで動く） |
| `ui/` | Jetpack Compose の画面（近くの駅・時刻表・設定） |

Kotlin / Jetpack Compose / Material 3、minSdk 26（Android 8.0）以上。

## データの出どころの表記

本アプリケーション等が利用する公共交通データは、公共交通オープンデータセンターにおいて提供されるものです。
公共交通事業者により提供されたデータを元にしていますが、必ずしも正確・完全なものとは限りません。
本アプリケーションの表示内容について、公共交通事業者への直接の問合せは行わないでください。
