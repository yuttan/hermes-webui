# Hermes Remote（非公式）

自分のPCで動かしている [Hermes Agent](https://github.com/NousResearch/hermes-agent) のWebダッシュボード（`hermes dashboard`）を、Androidのスマホ・タブレットから全画面で使うためのアプリです。Android 7.0以上。

> 個人が作った**非公式アプリ**です。Nous Research とは関係ありません。

## できること
- ダッシュボードを全画面で表示（チャット画面＝Hermes TUI もそのまま使える）
- 下部バーの ↑ ↓ Enter と、キーボードボタンで開くショートカットパネル
  - キー: Esc / Esc×2 / Ctrl+C / Tab / Shift+Tab / Alt+Enter / ← → / Home / End / PgUp / PgDn / Ctrl+U・K・W・O・T・R・L・X
  - コマンド: /new /stop /retry /undo /model /compress /usage /status /sessions /help（入力欄に入るだけ。Enterで実行）
- 左端のアンテナで接続状態を表示（緑＝接続中、灰の点滅＝確認中、赤＝接続できません）。切れると案内画面を出し、つながると自動で戻る
- 歯車ボタンで接続先をいつでも変更

## インストール
1. [Releases](https://github.com/yuttan/hermes-webui/releases) から最新の `hermes-remote-<版>.apk` をスマホでダウンロード
2. 開いてインストール（「提供元不明のアプリ」の許可を求められたら、ダウンロードに使ったブラウザに許可する）
3. 初回起動で接続先を入力 →「接続テスト」→「保存して開く」

更新も同じ手順で、新しいAPKを上書きインストールします（ログイン状態は残ります）。
[Obtainium](https://github.com/ImranR98/Obtainium) にこのリポジトリのURLを登録すると、新しい版が出たときに知らせてくれます。

## サーバー側の準備（Hermes を動かしているPC）
外出先から使うには、PCとスマホの両方に [Tailscale](https://tailscale.com/) を入れて同じアカウントでログインしておくのがおすすめです。ポートをインターネットに開放する必要はありません。

### おすすめ: Tailscale Serve で HTTPS にする
通信が暗号化され、アドレスも名前で覚えやすくなります。

1. Tailscale の管理画面（DNS 設定）で **MagicDNS** と **HTTPS Certificates** を有効にする
2. ログイン方法を決める（`127.0.0.1` 以外から見られるダッシュボードにはログインが必須です）
   - かんたん: ユーザー名とパスワード。`~/.hermes/.env` に `HERMES_DASHBOARD_BASIC_AUTH_USERNAME` と `HERMES_DASHBOARD_BASIC_AUTH_PASSWORD`（または `_PASSWORD_HASH`）を書く
   - Nous アカウントでログイン: `hermes dashboard register`
3. `~/.hermes/.env` に公開アドレスを書く
   ```
   HERMES_DASHBOARD_PUBLIC_URL=https://<PC名>.<tailnet名>.ts.net
   ```
4. ダッシュボードをPC内だけで起動し、Tailscale Serve で中継する
   ```sh
   hermes dashboard --host 127.0.0.1 --port 9119 --no-open
   tailscale serve --bg 9119
   ```
5. アプリには `https://<PC名>.<tailnet名>.ts.net` を入力（`tailscale serve status` で確認できます）

### かんたん: Tailscale のIPに直接つなぐ
```sh
hermes dashboard --host 0.0.0.0 --port 9119 --no-open
```
アプリには `http://100.x.y.z:9119`（PCの Tailscale IP）を入力します。この場合もログイン設定（上の手順2）が必要です。Tailscale の中は暗号化されていますが、Nous ログインのコールバックやログインCookieはアドレスに結びつくので、`HERMES_DASHBOARD_PUBLIC_URL` も同じアドレスにそろえてください。家のLAN内だけで使うなら `http://192.168.x.x:9119` でもかまいません。

詳しくは Hermes の公式ドキュメント「Web Dashboard」の Authentication と Public URL の節を見てください。

### うまくつながらないとき
- スマホ側の Tailscale がオンか（Androidの「設定 → VPN → Tailscale → 常時接続VPN」をオンにすると確実）
- PCで `hermes dashboard` が動いているか。PCがスリープしていないか
- 「接続テスト」は、ログインなしで見られる `GET /api/status` に Hermes の応答（`version` と `gateway_state`）が返るかで判定しています

## 安全のための仕様
- `http://` で家のLAN・Tailscale・`localhost` 以外へつなごうとすると、保存前に警告を出します
- アドレスにユーザー名・パスワード・`?` 以降を含めることはできません
- ログインCookieと接続先は端末内だけに保存し、クラウドバックアップや機種変更時の移行の対象外にしています
- アプリが使う権限はインターネットとネットワーク状態の確認だけです

## 開発者向け
JDK 17 と Android SDK 34 が必要です。`local.properties` に `sdk.dir=...` を書きます。自分用ビルドでは `hermes.url=<アドレス>` を書いておくと、初回の入力欄にそのアドレスが入ります（Gitには含めません）。

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug   # Windows は gradlew.bat
node --test tests/focus.test.cjs                      # 入力欄選択のテスト
```

リリースビルドの署名鍵は環境変数で渡し、リポジトリには置きません。
```sh
HR_KEYSTORE=/path/release.jks HR_KEYSTORE_PASSWORD=... HR_KEY_ALIAS=... HR_KEY_PASSWORD=... \
  ./gradlew testDebugUnitTest lintRelease assembleRelease
```
署名鍵を失くすと既存の利用者が更新できなくなります。必ずバックアップしてください。

### 仕組みの注意
チャット画面のショートカットは、ダッシュボードのターミナル（xterm.js）が日本語入力のために持っている仕組み（compositionend）を使ってバイト列を送っています（`assets/terminal-send.js`）。Hermes の更新でこの部分が変わると動かなくなる可能性があります。

## 変更履歴
- 2.0.0: 誰でも使える配布版に。初回起動の接続先設定画面と歯車ボタンを追加し、接続先をビルド時の埋め込みから端末の設定に変更。パッケージ名を `io.github.yuttan.hermesremote`、アプリ名を「Hermes Remote」に変更（1.x とは別アプリとして入ります）。HTTPS（Tailscale Serve）に対応し、平文で外部へつなぐときは警告。リリース署名、バックアップ除外を追加
- 1.7: 接続先アドレスをソースから外し、`local.properties` から埋め込む方式に変更
- 1.6: Ctrl+X を追加
- 1.5: ソフトキーボードで入力欄が隠れる問題を修正
- 1.4: ショートカットパネル、アイコン変更
- 1.3: Tailscale 一本化、アンテナ表示、下部バー刷新

## ライセンス
MIT License（`LICENSE`）
