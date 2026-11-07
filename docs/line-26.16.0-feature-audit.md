# LINE 26.16.0 再点検（2026-11-02）

対象は端末から取得したLINE 26.16.0 / 261600106。以前の「宣言100項目が通る」という検査は、実際に使用するView取得経路を網羅していなかった。クラスが存在しても役割が違う例もあったため、全機能が正常という根拠にはならない。

## 今回見つけて修正したもの

| 対象 | 根拠と修正 |
| --- | --- |
| 取消マーク・秒表示 | 実機ログに `lq1.i#a0` 等のNoSuchMethodErrorが177回。最新版RecyclerView.e0にa0()はなく、公開finalフィールドaがitemView。両機能で同じ取得ヘルパーを使用する。 |
| 取り消し保持 | 同じuq8.z0.bへ二重フックし、処理前にoperation.hを空にした後、処理後に別フックでIDを読む構造だった。最新版のb(v0,de,Continuation)はDB更新・FTS削除・メディア削除を実行し、a$a$c(boolean)を返すことをAPKで確認。対象の取消通知だけを受理済みとして返し、共有operationのID/種類を改変しない。通常の削除・受信・既読通知は対象外。 |
| 再利用される時刻View | 従来はnative描画後に非同期で全drawable・クリックを消していた。Tencha自身が付けたマークだけを次のnative bind前に片付ける。秒表示のメタデータも毎bindでクリアし、前メッセージの秒を持ち越さない。 |
| Agent i非表示 | o21.bは設定画面のOnClickListener。r21.b.a()がCHATROOM_AI_BUTTON_SHOWN_AFTER_MILLISを読むことを確認し参照先を変更。 |
| 検索結果件数 | ts1.mはOnTouchListener。oy1.k.H0(fa1.c,fa1.e$a,boolean,boolean)がtitle/countを読み、500+へ丸める処理を確認。binding x:b12.x0 / b:TextViewを含め修正。 |
| ダークテーマ | 先頭で指定したih6.jを後段で無関係なw76.nに上書きしていた。重複代入を除去し、最終的な設定値も回帰テスト対象にした。 |
| 編集履歴 | 現行APKのKotlin Unitはvs8.i0.a。存在しないkotlin.Unitへの固定参照を、既存のバージョン別Unit取得へ合わせた。 |
| ヘッダーボタン | AI Friends非表示がアルバムまで同時に対象にし、アルバム単独のフラグは状態フィルターの条件に入っていなかった。各設定で独立して対象を決める。 |
| 登録結果 | Vector.hookAllが対象メソッド0件でも正常終了していた。0件はNoSuchMethodErrorとして記録させる。取消フックの登録失敗も監督側へ伝播する。 |

## 検査の範囲

- hooks配下76個のHook/補助クラスを対象に設定参照を走査。バージョン継承後の137個の設定クラスと、ソースから直接関連付けできる16個のメソッド参照は存在照合エラー0。
- この16件には、ローカル変数経由・間接反射・実行時に選ばれる全メソッドは含まれない。別途レビューした115項目の宣言マニフェストでは正確な引数・戻り値・フィールド型を照合する。
- 登録だけの検査に加え、上表の問題箇所は実APKのメソッド本体・フィールド参照を確認した。
- 単体テスト91件成功、失敗0。spotlessCheck、lintDebug、assembleDebug、git diff --checkも実施。
- 実機で取消履歴に一致する既存メッセージの赤いマークと、表示中の時刻3件すべての秒表示をスクリーンショット/UI階層で確認。修正後プロセスの同画面操作で先のNoSuchMethodErrorは出ていない。
- 設定検索TTSを再確認し、音声送信の該当項目だけに絞られ、無関係な開発者項目が残らないことをUI階層で確認。
- 設定値・履歴・音源は削除せず、新しい送信・通話発信・プロフィール更新は行っていない。

## まだ正常を断言できないもの

新たな取消通知の到着から保持までの端末間テスト、編集/既読通知の到着、Agent iをOFF状態から再表示する条件、500件超の検索、実際の通話・相手側へのTTS/サウンドボード到達、着信/呼出音、メディア送信、プロフィールの全操作、バックアップ復元、非root端末の動作は今回の実機テスト範囲外。表の参照修正やテスト成功を、これらの動作確認済みとは扱わない。

再実行コマンド:

```powershell
python scripts/Audit-LineHookTargets.py ../line-26.16.0-apk/base.apk Version26160
python scripts/Verify-LineApk.py ../line-26.16.0-apk/base.apk docs/line-26.16.0-audit-mappings.json
./gradlew.bat spotlessCheck testDebugUnitTest lintDebug assembleDebug
```

スクリーンショット・UI階層・端末データはリポジトリへ追加しない。今回GitHubへの公開はしていない。
