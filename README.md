<img src="docs/icon.png" width="96" align="right" alt="Icon">

# Unofficial Hermes Remote

English | [日本語](README.ja.md)

[![CI](https://github.com/yuttan/unofficial-hermes-remote/actions/workflows/ci.yml/badge.svg)](https://github.com/yuttan/unofficial-hermes-remote/actions/workflows/ci.yml) [![Latest release](https://img.shields.io/github/v/release/yuttan/unofficial-hermes-remote)](https://github.com/yuttan/unofficial-hermes-remote/releases/latest)

A full-screen Android client for the web dashboard (`hermes dashboard`) of a [Hermes Agent](https://github.com/NousResearch/hermes-agent) that you run on your own computer. Android 7.0 or later. The UI follows the device language: English, Japanese, German, Spanish, French, Italian, Portuguese, Russian, Chinese (Simplified / Traditional), Korean and Indonesian (English for any other language). On the home screen the app is shown as "Hermes Remote".

> This is an **unofficial** app made by an individual. It is not affiliated with or endorsed by Nous Research.

## Features
- The dashboard in full screen, including the Chat tab (the real Hermes TUI)
- Up / Down / Enter buttons in the bottom bar, plus a shortcut panel (keyboard button) for keys a phone keyboard cannot type
  - Keys: Esc / Esc×2 / Ctrl+C / Tab / Shift+Tab / Alt+Enter / ← → / Home / End / PgUp / PgDn / Ctrl+U, K, W, O, T, R, L, X
  - Commands: /new /stop /retry /undo /model /compress /usage /status /sessions /help (inserted into the input only; press Enter to run)
- Connection status antenna on the left (green = connected, pulsing grey = checking, red = unreachable). When the server is unreachable a help page is shown, and the dashboard comes back automatically once it is reachable again
- Change the server at any time with the gear button

## Install
1. Download the latest `hermes-remote-<version>.apk` from [Releases](https://github.com/yuttan/unofficial-hermes-remote/releases) on your phone
2. Open it and install (allow "install unknown apps" for your browser if asked)
3. On first launch enter your dashboard address → **Test connection** → **Save and open**

To update, install the newer APK over the old one; you stay signed in.
Add this repository to [Obtainium](https://github.com/ImranR98/Obtainium) to be notified of new releases.

## Server setup (the computer running Hermes)
To use it away from home, install [Tailscale](https://tailscale.com/) on both the computer and the phone and sign in with the same account. You do not need to open any port to the internet.

### Recommended: HTTPS with Tailscale Serve
Traffic is encrypted end to end and the address is a readable name.

1. In the Tailscale admin console (DNS settings) enable **MagicDNS** and **HTTPS Certificates**
2. Choose how to sign in (a dashboard reachable from anywhere other than `127.0.0.1` always requires sign-in)
   - Simple: user name and password. Put `HERMES_DASHBOARD_BASIC_AUTH_USERNAME` and `HERMES_DASHBOARD_BASIC_AUTH_PASSWORD` (or `_PASSWORD_HASH`) in `~/.hermes/.env`
   - Nous account: `hermes dashboard register`
3. Put the public address in `~/.hermes/.env`
   ```
   HERMES_DASHBOARD_PUBLIC_URL=https://<machine>.<tailnet>.ts.net
   ```
4. Run the dashboard on loopback only and let Tailscale Serve proxy it
   ```sh
   hermes dashboard --host 127.0.0.1 --port 9119 --no-open
   tailscale serve --bg 9119
   ```
5. Enter `https://<machine>.<tailnet>.ts.net` in the app (`tailscale serve status` shows it)

### Simple: connect to the Tailscale IP directly
```sh
hermes dashboard --host 0.0.0.0 --port 9119 --no-open
```
Enter `http://100.x.y.z:9119` (the computer's Tailscale IP) in the app. Sign-in (step 2 above) is required here too. Tailscale encrypts the traffic, but the Nous sign-in callback and the login cookie are bound to one address, so set `HERMES_DASHBOARD_PUBLIC_URL` to that same address. On a home LAN only, `http://192.168.x.x:9119` also works.

See the Authentication and Public URL sections of the official Hermes "Web Dashboard" documentation for details.

### Troubleshooting
- Is Tailscale on on the phone? (Android: Settings → VPN → Tailscale → Always-on VPN makes it reliable)
- Is `hermes dashboard` running, and is the computer awake?
- **Test connection** checks that the unauthenticated `GET /api/status` returns a Hermes answer (`version` and `gateway_state`)

## Safety
- Saving an `http://` address outside your home network, Tailscale or `localhost` shows a warning first
- Addresses with a user name, password, or anything after `?` / `#` are rejected
- The login cookie and the saved address stay on the device: they are excluded from cloud backup and device-to-device transfer
- The only permissions are Internet and network state

## For developers
JDK 17 and Android SDK 34 are required. Put `sdk.dir=...` in `local.properties`. For your own builds you can also add `hermes.url=<address>` to pre-fill the first-run screen (it is never committed).

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug   # gradlew.bat on Windows
node --test tests/focus.test.cjs                      # input-focus tests
```

Release signing keys come from environment variables and never live in the repository.
```sh
HR_KEYSTORE=/path/release.jks HR_KEYSTORE_PASSWORD=... HR_KEY_ALIAS=... HR_KEY_PASSWORD=... \
  ./gradlew testDebugUnitTest lintRelease assembleRelease
```
Losing the key means existing users can no longer update. Keep a backup.

Translations live in `app/src/main/res/values*/strings.xml` (English in `values`). Languages other than English and Japanese were machine-translated; corrections and new languages are welcome as pull requests ([help wanted](https://github.com/yuttan/unofficial-hermes-remote/issues/6)). `TranslationsTest` fails if a language misses or adds a string.

The launcher icon is generated from the Hermes Agent artwork by `python tools/make_icon.py` (amber dot-matrix "terminal" rendering).

### How the shortcuts work
Chat shortcuts are sent as raw bytes through the IME fallback (`compositionend`) of the dashboard's xterm.js terminal (`assets/terminal-send.js`). A Hermes update that changes this part of the dashboard may break them.

## Changelog
- 2.3.0: Targets Android 16 (predictive back, edge-to-edge). Full-width (CJK keyboard) addresses are accepted. The help page appears within ~10 s when the server cannot be reached instead of after the WebView's own long timeout. Tested on a Redmi Pad 2 (Android 16)
- 2.2.0: Dot-matrix terminal-style icon. UI in 13 languages. Repository renamed to `unofficial-hermes-remote`
- 2.1.0: Renamed to "Unofficial Hermes Remote". English UI (Japanese on Japanese devices). English and Japanese READMEs
- 2.0.0: First public build. First-run server setup and gear button; the address is a device setting instead of a build-time constant. Package `io.github.yuttan.hermesremote` (installs alongside 1.x). HTTPS (Tailscale Serve) support and a warning for cleartext to public hosts. Release signing; app data excluded from backup
- 1.7 and earlier: personal tablet builds

## License
MIT License (`LICENSE`)
