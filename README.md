# Tencha

**Enhance your LINE.**

Android版LINE 26.13.0を基準に、更新後の構造互換性を起動時に検証する非公式Vector / Xposed拡張モジュールです。

LINEヤフー株式会社とは無関係です。アカウント制限、データ消失、LINE更新による非互換などの可能性があります。重要なトークやメディアは事前にバックアップし、自己責任で使用してください。

## ダウンロード

root版APKと非root版LSPatchセットは[GitHub Releases](https://github.com/yukkuri-matcha-tea/Tencha/releases)で配布します。パッチ済みLINE APKは配布しません。

## 対応環境

- パッケージ: `jp.naver.line.android`
- 検証済みLINE: `26.13.0` / versionCode `261300096`
- 未登録版: 8個の構造アンカーを検査し、5個以上一致した既知設定だけ自動適用
- Xposed API: 101以上（target API 102）
- 想定環境: Root + Zygisk + Vector
- モジュールパッケージ: `dev.vector.lineextension`

別バージョンのLINEでは安全のため通常Hookを適用しません。

## 使い方

### Root端末

1. Releasesから`Tencha-root-1.5.24.apk`をインストールします。
2. Vectorで「Tencha」を有効化します。
3. スコープはLINE (`jp.naver.line.android`) だけにします。
4. モジュールAPKを開き、「機能設定」から必要な機能をONにします。
5. LINEをタスク一覧から消すだけでなく完全終了し、再起動します。
6. モジュールAPKの「Vector接続」と「診断・復旧」を確認します。

LINE設定内にも拡張設定への入口を追加します。カスタムフォント、ホーム種別、FCM方式などLINEのRuntime情報が必要な選択はLINE内設定から行います。

### 非root端末

非root版はLSPatchをHook基盤として使い、Shizukuは任意の操作補助として使います。公式LINEとパッチ後LINEは署名が異なるため直接上書きできません。既存LINEを自動削除する機能はありません。詳細は`rootless/README.md`を参照してください。

## 実装済みの機能群

- 全体の既読送信回避、手動既読、既読ユーザー・時刻履歴
- 次に開く1トークだけの既読回避（予約後5分間）
- トーク単位の常時既読回避（チャット上部の本アイコン長押し）と登録トーク管理
- 設定・既読履歴・取消履歴・編集履歴をTenchaの非公開内部領域へ保存
- 任意の端末フォルダ／Google Driveへのバックアップ書き出しと復元
- GitHub Releasesからの更新確認、APK検証、Android標準インストーラーへの引き渡し
- LINE内で作成したトーク履歴スナップショットをTencha内部へ保存し、Tenchaバックアップへ同梱
- LINE内のバックアップ操作から端末フォルダ／Google Driveへ直接書き出し
- 送信取消イベント保持、取消表示、取消時間制限延長
- メッセージ時刻の秒表示、既定ブラウザ起動
- 高品質画像、長時間動画のクライアント制限緩和
- メンバー指定検索、1文字検索
- 広告・おすすめ・サービス欄の非表示
- VOOM/ニュース/MINI等のタブ・ラベル調整
- AgentIおよびヘッダーボタン非表示
- TTF/OTF、AMOLED、ダークモード関連
- 通知表示調整、リアクション通知、実験的FCM Fix
- Feature単位Safe Mode、次回起動だけ全Hook停止

これらはLINE 26.13.0の実APKマッピングに基づく実Hookです。ただし端末・アカウント・サーバー条件で分岐するため、最終的なRuntime動作は端末上で確認してください。5分超動画はクライアント側チェックだけを緩和し、サーバー制限を回避するものではありません。

## 安全設計

- 全動作変更機能は初期OFF
- LINEのメインプロセス以外では通常Hookを適用しない
- 1機能のHook登録失敗を他機能から分離
- 同一機能が連続失敗すると、その機能だけSafe Mode
- LINE更新時は版名だけで全停止せず、既知設定との構造互換性を検証する
- 構造アンカーが不足する版は全機能を停止し、誤ったHookを適用しない
- 自動互換時もHook登録失敗は機能単位で隔離する
- Hook登録だけでは「動作中」と表示しない
- 診断情報へメッセージ本文や個人情報を保存しない
- 「次回起動のみ全拡張OFF」で設定を消さず復旧可能

## ビルド

Android SDK 37とJDK 17を用意して実行します。

```powershell
$env:ANDROID_HOME = 'C:\Users\name\AppData\Local\Android\Sdk'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

生成先は`app/build/outputs/apk/debug/app-debug.apk`です。

## Release公開

`app/build.gradle`の`versionName`と同じ`v<version>`タグをpushすると、GitHub Actionsがテスト、APKビルド、署名、rootless kit作成、SHA-256生成、GitHub Release公開まで自動実行します。署名鍵はGitHub Actions Secretから復元し、リポジトリには含めません。

## Tencha固有部分とライセンス

製品名、管理UI、診断基盤、Safe Mode、26.13.0向け対応表、自動互換リゾルバ、アイコンはTencha固有です。アプリ内に旧プロジェクト名や旧ロゴは表示しません。

Hook機能の一部はGPL-3.0コードを改変しているため、法的に必要な由来は`VECTOR_NOTICE.md`へ分離して保持しています。配布物全体はGPL-3.0です。
