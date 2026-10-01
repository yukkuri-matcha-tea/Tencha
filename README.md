# Tencha

**Enhance your LINE.**

Android版LINEを拡張する非公式のVector / Xposedモジュールです。機能のON・OFFはLINE内で行い、Tencha本体は接続状態・バージョン・更新情報を確認するために使います。

LINEヤフー株式会社とは無関係です。LINE更新による非互換、アカウント制限、データ消失などの可能性があります。導入前に公式のトークバックアップとログイン手段を確認してください。

## ダウンロード

[GitHub Releases](https://github.com/yukkuri-matcha-tea/Tencha/releases/latest)で配布しています。1.9.1の配布物：

- [root向けAPK](https://github.com/yukkuri-matcha-tea/Tencha/releases/download/v1.9.1/Tencha-root-1.9.1.apk)
- [非root向けセット](https://github.com/yukkuri-matcha-tea/Tencha/releases/download/v1.9.1/Tencha-rootless-kit-1.9.1.zip) — モジュール、LSPatch、作成用スクリプト・説明書
- [非root向けモジュール単体](https://github.com/yukkuri-matcha-tea/Tencha/releases/download/v1.9.1/Tencha-rootless-module-1.9.1.apk)
- [SHA-256チェックサム](https://github.com/yukkuri-matcha-tea/Tencha/releases/download/v1.9.1/Tencha-1.9.1-SHA256SUMS.txt)

root向け・非root向けモジュールAPKは同じ内容で、導入方法が異なります。パッチ済みLINE APKは配布しません。

## 対応環境

- Android 8.0以降、arm64-v8a
- 対象アプリ：LINE（`jp.naver.line.android`）
- root端末：Vectorなど、Xposed API 101以上に対応した実行環境（target API 102）
- 非root端末：LSPatch。Shizukuは任意の操作補助で、Hookエンジンではありません
- Tenchaパッケージ：`dev.vector.lineextension`

1.9.0ではLINE **26.15.0（261500177）**の実APKを解析し、プロフィール情報、全選択、カメラ、動画、設定入口・メニューなどの参照先を修正しました。

旧版の対応表も保持しています：26.10.0 / 26.10.1 / 26.11.0 / 26.13.0 / 26.13.1 / 26.14.0。ただし、すべての版で新機能や全機能の動作を保証するものではありません。未登録版では構造互換性を検査しますが、将来のLINE更新への完全対応を保証しません。

## 導入と設定

### root端末

1. root向けAPKをインストールします。
2. VectorでTenchaを有効化し、スコープをLINEだけに設定します。
3. LINEを完全終了して再起動します。
4. Tencha本体で接続状態とLINE・Tenchaのバージョンを確認します。
5. **LINEの設定 → Tencha → モジュール設定**から必要な機能を設定します。

Tencha本体には機能のON・OFFや診断画面を置いていません。接続状態、バージョン、Tencha・開発者の情報、GitHubからの更新を確認できます。

### 非root端末

LSPatchで利用者自身のLINE APKにTenchaを組み込んで使用します。手順は[非root版セットアップ](rootless/README.md)を参照してください。

公式LINEとパッチ後のLINEは署名が異なるため直接上書きできません。Shizukuでも署名の制約は解除できません。既存LINEの削除が必要になる場合があるので、バックアップとログイン手段の確認を先に行ってください。Tenchaが既存LINEを自動削除することはありません。

## 主な機能

### トーク・プライバシー

- 既読送信回避、手動既読、既読ユーザー・時刻履歴
- 次に開く1トークだけの既読回避、トーク単位の常時既読回避
- 送信取消メッセージの保持・取消表示、編集履歴
- メッセージ時刻の秒表示、全選択、メンバー指定検索・1文字検索
- メッセージ長押しメニューからGoogle検索・翻訳（表示テキストの置き換え）
- 既定ブラウザ・標準カメラの利用、高品質画像、長時間動画のクライアント制限緩和

長時間動画はクライアント側の制限を緩和するもので、サーバー側の制限を解除する機能ではありません。

### 他人のプロフィール

- プロフィール画像・背景画像の拡大表示と保存
- 配置画像の単体表示・保存
- オブジェクト閲覧モードで、移動・拡大縮小・回転・重なりの確認

自分のプロフィールには追加しません。閲覧モードでの操作は一時的な表示変更で、相手のプロフィールを保存・更新する通信は行いません。閲覧終了時に元の配置へ戻します。

### 通話

- **サウンドボード**：登録した音声ファイルを通話へ流し、自分でも再生音を聞く
- **TTS**：通話中のテキストメッセージを読み上げる。対象ユーザー、出力先、送信者名の読み上げ、最大文字数を設定可能

サウンドボード音声はLINE内のTencha設定から登録します。通話画面のメニューにある「Tencha 通話調整」から操作し、TTS読み上げをONにしてください。

自分のメッセージは、DBへ保存され送信確定したテキストを対象にします。画像・スタンプ・送信失敗は除外します。未送信・保留中のメッセージはすぐには読み上げず、確認待ちは最大60秒です。

個別音量調整・マイクレベルメーターの設定項目は削除しています。予約送信も現在の提供機能には含みません。

### 表示・通知

- 広告・おすすめ・サービス欄の非表示
- VOOM・ニュース・MINIなどのタブ、ラベル、ヘッダーボタンの調整
- Agent i関連ボタンの非表示
- TTF/OTFカスタムフォント、AMOLED・ダークモード関連設定
- 通知表示調整、リアクション通知
- 開発者モード内の実験的設定（FCM関連など）

### データと更新

- 設定・履歴をTenchaの非公開内部領域に保存
- 必要な人だけ端末フォルダ・Google Driveへバックアップを書き出し、復元
- LINE内で作成したトーク履歴スナップショットの保存・バックアップ同梱
- GitHub Releasesからの更新。起動時の自動確認は1日1回で、勝手なインストールや常駐処理は行いません

TenchaのバックアップはLINE公式のバックアップ・アカウント引き継ぎの代わりにはなりません。

## 1.9.1の修正

LINE 26.15.0で「既読履歴を記録」が別の通知処理を監視していた問題を修正しました。正しい既読通知からトーク・既読者・最終既読メッセージ・通知時刻を取得します。受信していない通知や、過去の正確な既読時刻を復元する機能ではありません。詳細は[既読履歴の修正記録](docs/read-history-fix-26.15.0.md)を参照してください。

## 動作確認と制限

1.9.0の今回の修正では、APK宣言との照合35項目、自動テスト31件、lint・APKビルドが通っています。ただし、これを実機での全機能確認として扱っていません。今回の修正後の画面操作・通話・TTS再生は未検証です。詳細は[修正と検証の記録](docs/line-26.15.0-audit-fixes.md)を参照してください。

機能はLINEの版、端末、アカウント、サーバー条件によって動作が変わります。Hook登録の成功だけでは実際の動作を保証できません。不具合報告にはLINE・Tenchaのバージョン、導入方法、再現手順を添えてください。スクリーンショットやログを公開する際は、メッセージ本文・名前・IDなどを伏せてください。

## ビルド

JDK 21、Android SDK（compileSdk 37）、NDK `29.0.14206865`、CMake `3.22.1`を用意します。CIのSDK導入設定は[release.yml](.github/workflows/release.yml)を参照してください。

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-21'
$env:ANDROID_HOME = 'C:\Users\name\AppData\Local\Android\Sdk'
.\gradlew.bat spotlessCheck testDebugUnitTest lintDebug assembleDebug
```

生成先：`app/build/outputs/apk/debug/app-debug.apk`。ローカルで別の署名鍵を使ったAPKは、配布版を上書きできない場合があります。署名鍵やパスワードはリポジトリに含めないでください。

## リリース公開

`app/build.gradle`の`versionName`と同じ`v<version>`タグをpushすると、GitHub Actionsがテスト、APKビルド、署名確認、rootlessキット作成、SHA-256生成、Release公開を実行します。署名鍵はGitHub Actions Secretから復元します。READMEなどの通常のブランチ更新だけではリリースしません。

## 開発者

- [GitHub — yukkuri-matcha-tea](https://github.com/yukkuri-matcha-tea)
- [X — @yukkuri_matcha_](https://x.com/yukkuri_matcha_)
- [YouTube](https://www.youtube.com/channel/UCuhltKmciQLwQTBEIIiCH2g)

## ライセンス・由来

Tenchaは独自の製品名・管理UI・設定保存・対応表・アイコンを使用しています。一部のHook実装はGPL-3.0の[2b-zipper/Knot](https://github.com/2b-zipper/Knot)を改変したものです。由来は[VECTOR_NOTICE.md](VECTOR_NOTICE.md)、ライセンスは[LICENSE](LICENSE)を参照してください。配布物全体はGPL-3.0です。
