# Supabase のはじめかた

所要時間 15〜20分。**この手順を上から順にやるだけで、旅情にログインと保存が付きます。**

分からない言葉が出てきても、書いてある通りに押していけば進みます。意味は後から分かれば十分です。

---

## 1. アカウントを作る

1. https://supabase.com を開く
2. 右上の **Start your project** を押す
3. **Continue with GitHub** か、メールアドレスで登録する

GitHubアカウントがあればそちらが早いです。無ければメールで構いません。

---

## 2. プロジェクトを作る

1. **New project** を押す
2. 次の3つを入力する

| 欄 | 入れるもの |
|---|---|
| Name | `ryojo` |
| Database Password | **強いパスワードを自動生成し、どこかに控える** |
| Region | **Northeast Asia (Tokyo)** |

3. **Create new project** を押す

> **Database Password は必ず控えてください。** 今回の作業では使いませんが、後で必要になり、再表示できません。

作成に1〜2分かかります。待ちます。

---

## 3. 表を作る

データを入れる箱を用意します。

1. 左のメニューから **SQL Editor** を開く
2. **New query** を押す
3. 下の枠の中身を**全部コピーして貼り付ける**
4. 右下の **Run** を押す

```sql
create table trips (
  id uuid primary key default gen_random_uuid(),
  author_id uuid not null references auth.users on delete cascade,
  title text not null default '',
  data jsonb not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

alter table trips enable row level security;

create policy "自分の旅だけ読める"
  on trips for select using (auth.uid() = author_id);

create policy "自分として保存できる"
  on trips for insert with check (auth.uid() = author_id);

create policy "自分の旅だけ更新できる"
  on trips for update using (auth.uid() = author_id);

create policy "自分の旅だけ消せる"
  on trips for delete using (auth.uid() = author_id);

create index trips_author_updated on trips (author_id, updated_at desc);
```

**Success. No rows returned** と出れば成功です。

### この4行が何をしているか

`policy` と書かれた4つが**権限の設定**です。`auth.uid() = author_id` は「**ログインしている本人の行だけ**」という意味です。

これを書き忘れると、**全員が全員のデータを読めます。** 設計書で「最も慎重にやる箇所」と書いたのがここです。今は自分だけが使うので実感がありませんが、人が増えたときに効きます。

---

## 4. 勝手に登録されないようにする

今は自分だけが使うので、他人が登録できないようにしておきます。

1. 左メニューの **Authentication** を開く
2. **Sign In / Providers**（または Settings）を開く
3. **Allow new users to sign up** を **オフ** にする

> **ただし、自分がまだ登録していないなら、先に5と6を済ませてログインしてから、これをオフにしてください。** 順番を間違えると自分も入れなくなります。

---

## 5. 鍵を2つ持ってくる

1. 左メニューのいちばん下 **Project Settings**（歯車）を開く
2. **API** を開く
3. 次の2つをコピーする

| 名前 | 見た目 |
|---|---|
| **Project URL** | `https://xxxxxxxx.supabase.co` |
| **anon public** キー | `eyJ...` で始まる長い文字列 |

> **anon public は公開して大丈夫な鍵です。** ブラウザに書く前提で作られていて、手順3で設定した権限が守ってくれます。
> 同じ画面にある **service_role** の方は**絶対にブラウザに書かないでください。** そちらは全権限を持ちます。

---

## 6. 旅情に貼り付ける

`config.js` を開いて、次の2行の `''` の中に貼り付けます。

```js
const SUPABASE_URL = 'https://xxxxxxxx.supabase.co';
const SUPABASE_ANON_KEY = 'eyJ...';
```

保存してページを開き直します。

---

## 7. 確かめる

1. サイドバー下部の **「接続を確認」** を押す
2. 「鍵が設定されている」「サーバーに繋がった」「表がある」が緑になれば成功
3. **「ログイン」** を押してメールアドレスを入れる
4. 届いたメールのリンクを押す
5. 戻ってきたら、サイドバーにメールアドレスが出ます

ログインできたら、手順4の「勝手に登録されないように」をやってください。

---

## うまくいかないとき

| 症状 | 原因 |
|---|---|
| 「鍵が未設定」 | `config.js` の貼り付け漏れ。`''` の中に入っているか確認 |
| 「サーバーに繋がらない」 | URLの打ち間違い。末尾の `/` は不要 |
| 「表が見つからない」 | 手順3のSQLが実行されていない |
| メールが来ない | 迷惑メールを確認。Supabaseの無料枠は送信数に上限あり |
| リンクを押しても戻らない | Authentication → URL Configuration の **Site URL** に、今開いているアドレス（例 `http://127.0.0.1:5500`）を入れる |

---

## この段階でできるようになること

- 旅程がサーバーに保存される
- 別の端末からログインしても同じ旅程が出る
- 他人からは見えない（権限で守られている）

**まだできないこと**（次の段階）

- 写真はまだ旅程に埋め込まれたまま。**大きな写真を何枚も入れると保存が重くなります**
- 他人に見せる機能はまだありません

---

## 費用について

無料枠は データベース500MB / ファイル保管1GB / 月間5万人まで。

**注意が1つ。無料枠のプロジェクトは1週間使われないと停止します。** 止まっても消えるわけではなく、ダッシュボードから再開できます。人が少ない時期は起こり得ます。

---

*Supabaseの画面は変わることがあります。ボタン名が違っても、探しているものは「SQL Editor」「Authentication」「Project Settings の API」の3つです。*
