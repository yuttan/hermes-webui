# Hermes WebUI Android

Hermes Web UIをタブレットで使うWebViewアプリ。Android 7.0以上。

## 1.4の変更
- アプリアイコンをHermes Agent（デスクトップ版と同じ画像）に変更。アダプティブアイコン対応。
- 下部バーにキーボードボタンを追加。押すとショートカットパネルが開閉。
  - キー: Esc / Esc×2 / Ctrl+C / Tab / Shift+Tab / Alt+Enter(改行) / ← → / Home / End / PgUp / PgDn / Ctrl+U・K・W / Ctrl+O・T・R・L（Hermes TUIのキー割り当てに合わせた）。
  - コマンド: /new /stop /retry /undo /model /compress /usage /status /sessions /help を入力欄に挿入（実行はEnterで確定）。
- 送信方式: チャット画面のxterm（screenReaderMode有効のため合成inputイベントは無視される）へ、ダッシュボードのIME compositionendフォールバック経由で生バイトを送る（assets/terminal-send.js）。チャット以外の画面ではEsc・Tab・矢印などをAndroidのキーイベントとして送る。ダッシュボードの実装が変わると動かなくなる可能性あり。

## 1.3の変更
- 接続先はTailscale経由の `http://100.124.137.25:8899` に一本化。LAN/VPN切り替えと自動判定を廃止（NousログインのコールバックとログインCookieがこのアドレスに固定されるため）。
- 上部バーを削除し、Web画面を最大化。
- 下部バーを刷新：左端のアンテナアイコンでHermesへの接続状態を表示（緑=接続中、灰の点滅=確認中、赤＋斜線=接続できません）。右側に↑・↓・Enter・更新。
- 接続確認は`/api/status`へ15秒ごと＋ネットワーク変化時。切断時は案内画面、復帰すると自動で再表示。
- Tailscaleは常時オンが前提。Androidの「常時接続VPN」にTailscaleを設定すると確実。

## 1.1の改善
- 接続先の選択を保存して次回起動でも維持。
- 上下の操作バーとWeb画面を分離し、ページへの重なりを解消。
- ↑↓Enterはフォーカス完了後に順番に送信。固定100ms待機を撤去。
- 現在の編集欄を優先し、なければtextarea、contenteditable、テキスト入力の順。パスワード・非表示・無効・読み取り専用は対象外。
- 入力欄がなければキーを送らない。切り替え・更新・終了時は保留キーを破棄。
- 終了時にWebViewを解放。Gradle Wrapper、回帰テスト、生成物除外を追加。

## 操作と接続先
アンテナで接続状態を確認、↑↓で選択移動、Enterで確定、更新ボタンで再読み込み。Androidの戻る操作でページ履歴を戻る。
接続先はMainActivity.ktの`HERMES_URL`（`http://100.124.137.25:8899`）。アプリはTailscaleを起動しません。HTTPのためTailscale内でのみ利用し、インターネットへポート開放しないでください。

## ビルド・テスト
JDK 17、Android SDK 34が必要。ANDROID_HOMEまたはlocal.propertiesにSDKを指定。

Windows:
```bat
gradlew.bat testDebugUnitTest lintDebug assembleDebug
```
macOS/Linux:
```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```
入力欄選択のテスト（Node.js）:
```sh
node --test tests/focus.test.cjs
```
APK: `app/build/outputs/apk/debug/app-debug.apk`
```sh
adb devices -l
adb -s <tablet-serial> install -r app/build/outputs/apk/debug/app-debug.apk
```
更新には既存アプリと同じ署名鍵が必要。別PCのdebug鍵では更新できない場合があります。

## 検証と残課題
- Robolectric/JUnit: 接続先保存、レイアウト制約、非同期キー順序・キャンセル。
- Node.js: 入力欄選択、繰り返し、無効な入力欄除外（DOMテストダブル）。
- 1.1の実機検証は未実施。modelの連続選択、Gboard表示時レイアウト、LAN/VPN認証はRedmi Pad2で確認が必要。
- URL編集画面、接続エラー専用表示、旧全画面・戻るAPIの更新、HTTP許可範囲の制限は残課題。
- Web UIのDOM変更に合わせ、入力欄の判定調整が必要になる可能性があります。
- Gitから生成物を除外しましたが過去の履歴には残っています。履歴は書き換えていません。
