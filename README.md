# Hermes WebUI Android

Hermes Web UIをタブレットで使うWebViewアプリ。Android 7.0以上。

## 1.2の改善
- 接続先の自動切り替え（既定）。自宅Wi-FiでHermesが応答すればLAN、それ以外はTailscale（VPN）。どちらも応答しなければ案内画面。
- ネットワーク変化（Wi-Fi接続/切断、Tailscaleオン/オフ）、アプリ復帰、読み込み失敗で自動再判定。
- 判定はHermesの`/api/status`応答で行い、外出先の別の192.168.1.xネットワークをLANと誤認しない。
- 上部に現在の接続状態（LAN接続/VPN接続/確認中/接続できません）を色付きで表示。自動/LAN/VPNボタンは選択中が青。
- LAN/VPNボタンで手動固定も可能。自動に戻すには「自動」。

## 1.1の改善
- 接続先の選択を保存して次回起動でも維持。
- 上下の操作バーとWeb画面を分離し、ページへの重なりを解消。
- ↑↓Enterはフォーカス完了後に順番に送信。固定100ms待機を撤去。
- 現在の編集欄を優先し、なければtextarea、contenteditable、テキスト入力の順。パスワード・非表示・無効・読み取り専用は対象外。
- 入力欄がなければキーを送らない。切り替え・更新・終了時は保留キーを破棄。
- 終了時にWebViewを解放。Gradle Wrapper、回帰テスト、生成物除外を追加。

## 操作と接続先
自動/LAN/VPNボタンで接続方式を選択、↻で再読み込み（接続できない時は再判定）。↑↓で選択移動、Enterで確定（画面によってはメッセージ送信）。Androidの戻る操作でページ履歴を戻る。

接続先はMainActivity.ktの定数（編集画面は未対応）:
- LAN: `http://192.168.1.27:8899`
- VPN: `http://100.124.137.25:8899`

アプリはTailscaleを起動しません。外出先ではTailscaleアプリを先にオンにしてください。HTTPを許可しているため信頼できるLANかTailscale内で利用し、インターネットへポート開放しないでください。
OAuthコールバック登録はサーバー側の設定です。アプリの切り替えだけでは登録不一致を解決できません。

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
