# Hermes WebUI Android App

WebViewベースのAndroidアプリで、Hermes Agent Web UIにアクセスします。

## 機能
- 全画面表示（イマージブモード）
- LANとTailscale VPNのURL切り替え
- OAuthログインフローサポート
- WebView内でのバックナビゲーション

## URL設定
デフォルトのURLは`MainActivity.kt`で定義されています:
- LAN: `http://192.168.1.27:8899`
- VPN: `http://100.124.137.25:8899`

必要に応じて変更してください。

## ビルド方法

### Android Studioで
1. プロジェクトを開く
2. Run > Run 'app' を選択
3. デバイスまたはエミュレータを選択

### コマンドラインで
```bash
./gradlew assembleDebug
```
APKは `app/build/outputs/apk/debug/app-debug.apk` に生成されます。

## インストール
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## 要件
- Android 7.0 (API 24) 以上
- Android Studio Hedgehog 以降（推奨）
- JDK 17
