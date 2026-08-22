# Tencha 非root版セットアップ

Tenchaの非root版は、LSPatchで利用者自身のLINE APKへTenchaを組み込んで使います。ShizukuはLSPatch Managerの操作を補助する任意要素であり、Hookエンジンではありません。

## Android端末だけで行う場合（推奨）

1. LINE公式のトークバックアップを完了します。
2. 同梱のLSPatch Manager 1.1をインストールします。
3. 必要ならShizukuを公式配布元からインストールし、起動します。
4. LSPatch ManagerのManager modeで、端末上のLINE APKセットを選択してパッチします。
5. Tencha APKをモジュールとして選び、対象を`jp.naver.line.android`にします。
6. 生成されたLINE APKセットをインストールし、Tenchaの接続状態を確認します。

## 重要な制限

- パッチ後のLINEは公式LINEと署名が異なるため、そのまま上書きできません。
- Shizukuを使ってもAndroidの署名検証やLINEのアカウント移行制約は消えません。
- 既存LINEの削除が必要になり得ます。必ず公式バックアップとログイン手段を確認してください。
- LINE更新後は対応版を確認し、原則として再パッチが必要です。
- Tenchaは既存LINEの自動削除、データ消去、パッチ済みLINEの自動インストールを行いません。
- 改変したLINE APKは再配布しないでください。このセットにも同梱していません。

## PCで作成する場合

Java 21以上と、同じLINEバージョンから取得したbase APK・全split APKを用意し、`Build-TenchaRootless.ps1`を実行します。

```powershell
.\Build-TenchaRootless.ps1 `
  -BaseApk 'C:\path\base.apk' `
  -SplitApk 'C:\path\split_config.arm64_v8a.apk','C:\path\split_config.xxhdpi.apk'
```

生成物は既定で`patched-line`へ出力されます。このスクリプトは端末を操作しません。

## 上流プロジェクト

- LSPatch: https://github.com/JingMatrix/LSPatch
- Shizuku: https://github.com/RikkaApps/Shizuku

LSPatchおよびShizukuはTenchaとは別プロジェクトです。各配布物にはそれぞれのライセンスが適用されます。
