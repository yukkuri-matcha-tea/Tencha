# パーティー画面へのサウンドボード移動

## APKで確認した根拠

LINE 26.15.0 の `classes4.dex` と `resources.arsc` を確認した。

- `WtMenuContainerViewHolder(so7.a, FrameLayout, Guideline)` がパーティー画面を生成する。
- `f: gm7.b5` の `getRoot()` が `WtMenuView` を返す。
- `wt_menu.xml` の `tab` は FrameLayout 内の TabLayout。
- ネイティブのタブは YouTube / SCREEN_SHARE の enum と ViewPager2 のページに対応する。
- `eu7.f` は `wt_menu_tab` を inflate し、`menu_title` にサービス名を設定する。`menu_badge_dot` は通知用であり、追加項目では非表示にする。

enum や ViewPager のデータには架空のサービスを追加しない。元の TabLayout をそのまま保持し、同じ `wt_menu_tab` を使うサウンドボードの入口を横に配置する。タブの ID・アダプター・既存クリック処理は保持する。未知の View 階層では変更を行わずログに記録する。

サウンドボードはパーティー画面の `menu_pager` と同じ領域にスクロール可能なページとして表示する。旧「通話調整」の下から出るパネルは開かない。元の ViewPager2 とアダプターを保持し、ネイティブのタブ選択・再選択で元のページへ戻す。音声ボタンは既存の再生状態管理を再利用し、文字・未再生時の背景色は純正タブのテーマに合わせる。通話調整は TTS 専用。音声ファイル登録・ミキサー・ミュート処理は変更しない。

## 検証範囲

サウンドボードとの往復も横方向のページ遷移を行う。LINE同梱の TabLayoutMediator は `ViewPager2.d(index, true)` を呼び、LinearLayoutManager は `androidx.recyclerview.widget.v` のページ移動を使う。その減速補間と移動距離・densityDpiに基づく所要時間（25 ms/inch、減速係数 0.3356）をローカルページ側にも適用する。連続切り替えは現在の移動位置から反転し、画面破棄時は停止する。Androidでアニメーションを無効にしている場合は即時切り替えする。LINE純正の2ページ間の処理は変更しない。

DEX の宣言照合、Gradle ビルド、既存ユニットテスト、Android Lint を実施。通話中の実機表示・再生・縦横切り替え・パーティー画面の再表示は未確認。LINE 26.15.0 以外のパーティー画面は未検証。
