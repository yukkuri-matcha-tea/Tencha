# 通話中のメディア起動制限（2026-10-23）

LINE 26.15.1のbase.apkと全DEXを調査した。Tencha側に該当文言はなく、LINEのリソース `voip_msg_not_available_function_for_calling`（0x7f1546eb）の表示元を調査。

## 解除対象

- `en1.q2.p0(boolean)`: ボイスメッセージ入力。`X().s2():ti1.a` の `c()` とTelephonyManager.getCallStateを確認してから録音画面へ進む。
- `jo1.p.a(u81.a)`: チャットの動画選択入口。`ti1.a.c()` と端末の電話状態で拒否していた。
- `ry0.h0.a(Activity)`: カメラ入口の共通事前チェック。`ho4.m.c()` の後に空き容量100MiB以上のチェックがある。

実装は `CallMultitaskingHook`。上記入口の同期実行中だけThreadLocalスコープを有効にし、`ti1.c.c()` と `ie2.g.c()` の通話中判定をfalseにする。元のgetterは実行し、その戻り値がtrueの場合だけ上書き。ie2.g.cはfy6.a.dへ、ti1.c.cはwm7.d.cへ委譲することを確認した。

LINEのセッション・状態Flow、AudioManager、TelephonyManager、マイクの所有権、通話のミュート状態は変更しない。スコープは例外・入れ子でもfinallyで破棄し、別スレッドには伝播しない。動画選択後の送信や録音後の送信を自動実行しない。

LINE内Tencha設定の通話カテゴリに「通話中の操作制限を解除」を追加。依頼した動作になるよう初期ON、OFFにすると元のチェックを維持する。71件の単体テスト、spotless、lint（エラー0）、ビルド成功。インストール・LINE再起動後、Hook登録成功ログと起動を確認。実通話と録音/カメラの同時使用は未試験。

## 解除していない箇所

`call_charge_block_on_calling`（0x7f15085b）の参照元には別通話の開始・参加、`call_ampkit_already_in_use`（0x7f150834）には実音声エンジンの競合エラーが含まれる。これらを単に隠しても同時利用可能にはならないため対象外。ライブトークの同時開始、フォント変更・削除、端末の電話中・権限・容量不足、OSやハードウェアが拒否するカメラ/マイク同時使用も、この修正で解除したとは扱わない。

現在の対応表は26.15.1のみ。旧版や未知版に推測したクラス名を適用しない。今回のGitHub公開は行っていない。
