package dev.vector.lineextension

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.vector.lineextension.core.ControlClient
import dev.vector.lineextension.core.FeatureStatus
import dev.vector.lineextension.core.GitHubUpdater
import dev.vector.lineextension.core.TenchaBackup
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class VectorMainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent { VectorTheme { VectorApp() } }
  }
}

@Composable
private fun VectorTheme(content: @Composable () -> Unit) {
  val context = LocalContext.current
  val dark = isSystemInDarkTheme()
  val scheme =
    when {
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dark -> dynamicDarkColorScheme(context)
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
      dark -> darkColorScheme(primary = Color(0xFFACD370), onPrimary = Color(0xFF213600), primaryContainer = Color(0xFF324F00), onPrimaryContainer = Color(0xFFC8F08A))
      else -> lightColorScheme(primary = Color(0xFF476810), onPrimary = Color.White, primaryContainer = Color(0xFFC7F089), onPrimaryContainer = Color(0xFF121F00))
    }
  MaterialTheme(colorScheme = scheme, content = content)
}

private enum class Screen { HOME, SETTINGS, DIAGNOSTICS, ABOUT, ROOTLESS }

private data class FeatureRow(val id: String, val title: String, val fallback: String)

private val visibleFeatures =
  listOf(
    FeatureRow("read_block", "既読", "無効"),
    FeatureRow("unsend_retention", "送信取消", "無効"),
    FeatureRow("message_seconds", "メッセージ", "無効"),
    FeatureRow("external_browser", "ブラウザ", "無効"),
    FeatureRow("media_quality", "メディア", "無効"),
    FeatureRow("search_enhancement", "検索", "無効"),
    FeatureRow("ad_removal", "広告・おすすめ", "無効"),
    FeatureRow("tab_customizer", "タブ・UI", "無効"),
    FeatureRow("agenti_hider", "AgentI", "無効"),
    FeatureRow("custom_font", "外観", "無効"),
    FeatureRow("fcm_fix", "通知", "無効"),
    FeatureRow("line_settings_ui", "LINE内設定", "確認中"),
  )

private val restartRequiredKeys =
  setOf(
    "prevent_mark_as_read", "temporary_read_block", "per_chat_read_block", "record_read_history", "prevent_unsend_message",
    "show_seconds_in_chat_time", "open_url_in_default_browser", "high_quality_photo",
    "long_video", "search_by_member", "search_min_1_char", "hide_ai_icon_permanently",
    "remove_ads", "use_custom_font", "experimental_fcm_fix", "developer_mode",
  )

private val experimentalKeys =
  setOf(
    "experimental_fcm_fix", "fcm_force_registration", "line_foreground_keep_alive",
    "spoof_version", "spoof_version_unsend_only", "fix_signature_mismatch", "long_video",
  )

private val recommendedPreset =
  setOf(
    "show_seconds_in_chat_time", "open_url_in_default_browser", "remove_ads",
    "remove_home_recommendations", "remove_home_services", "remove_home_accordion",
    "hide_ai_icon_permanently", "remove_search_bar_agent_i_button", "search_min_1_char",
  )

@Composable
private fun VectorApp() {
  val context = LocalContext.current
  val snackbar = remember { SnackbarHostState() }
  var screen by remember { mutableStateOf(Screen.HOME) }
  var snapshot by remember { mutableStateOf(ControlClient.snapshot(context)) }
  var settings by remember { mutableStateOf(ControlClient.settingsSnapshot(context)) }

  fun refresh() {
    snapshot = ControlClient.snapshot(context)
    settings = ControlClient.settingsSnapshot(context)
  }

  key(screen) {
    when (screen) {
      Screen.HOME -> DashboardScreen(snapshot, ::refresh, { screen = it; refresh() }, snackbar)
      Screen.SETTINGS -> SettingsScreen(settings, { settings = ControlClient.settingsSnapshot(context) }, { screen = it; refresh() }, snackbar)
      Screen.DIAGNOSTICS -> DiagnosticsScreen(snapshot, ::refresh, { screen = it; refresh() }, snackbar)
      Screen.ABOUT -> AboutScreen({ screen = it }, snackbar)
      Screen.ROOTLESS -> RootlessSetupScreen({ screen = Screen.ABOUT }, snackbar)
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardScreen(
  snapshot: Bundle,
  onRefresh: () -> Unit,
  onNavigate: (Screen) -> Unit,
  snackbar: SnackbarHostState,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val lineVersion = remember { installedLineVersion(context.packageManager) }
  val lastSeen = snapshot.getLong("lastLineSeen", 0L)
  val connected = lastSeen > 0L
  var nextLaunchOff by remember(snapshot) { mutableStateOf(snapshot.getBoolean("nextLaunchOff", false)) }
  val featureStates = visibleFeatures.map { it to snapshot.getBundle("feature.${it.id}") }
  val workingCount = featureStates.count { (_, state) -> state?.getString("status") == FeatureStatus.WORKING.name }
  val issueCount = featureStates.count { (_, state) ->
    state?.getString("status") == FeatureStatus.HOOK_FAILED.name ||
      state?.getString("status") == FeatureStatus.SAFE_MODE.name
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Tencha") },
        actions = { TextButton(onClick = onRefresh) { Text("更新") } },
      )
    },
    bottomBar = { TenchaNavigationBar(Screen.HOME, onNavigate) },
    snackbarHost = { SnackbarHost(snackbar) },
  ) { inner ->
    LazyColumn(
      modifier = Modifier.fillMaxSize().padding(inner),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item {
        Text(
          "Enhance your LINE.",
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      item {
        val containerColor = if (connected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
        val contentColor = if (connected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
        Card(colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor)) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Box(Modifier.size(16.dp).background(if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, CircleShape))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text(
                if (connected) "LINEに接続済み" else "LINEに未接続",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
              )
              Text(
                if (connected) "Tenchaは正常に動作しています" else "VectorでTenchaを有効にしてLINEを再起動してください",
                style = MaterialTheme.typography.bodyMedium,
              )
              Text(
                if (connected) "最終接続 ${formatTime(lastSeen)}" else "接続すると機能状態が反映されます",
                style = MaterialTheme.typography.labelMedium,
              )
            }
          }
        }
      }
      item {
        Card {
          Column {
            ListItem(
              headlineContent = { Text("LINE") },
              supportingContent = { Text("対象アプリのバージョン") },
              trailingContent = { Text(lineVersion ?: "未導入", style = MaterialTheme.typography.labelLarge) },
            )
            ListItem(
              headlineContent = { Text("Tencha") },
              supportingContent = { Text("モジュールのバージョン") },
              trailingContent = { Text(BuildConfig.VERSION_NAME, style = MaterialTheme.typography.labelLarge) },
            )
            ListItem(
              headlineContent = { Text("動作中の機能") },
              supportingContent = { Text("Runtime報告を受信済み") },
              trailingContent = { Text("$workingCount / ${visibleFeatures.size}", style = MaterialTheme.typography.labelLarge) },
            )
          }
        }
      }
      item {
        SectionTitle("機能の状態")
        Text(
          if (issueCount == 0) "問題は見つかっていません" else "$issueCount 件の確認が必要です",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      item {
        Card {
          Column {
            featureStates.forEachIndexed { index, (feature, state) ->
              val statusName = state?.getString("status")
              val label = FeatureStatus.entries.firstOrNull { it.name == statusName }?.label ?: feature.fallback
              ListItem(
                colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text(feature.title, fontWeight = FontWeight.Medium) },
                supportingContent = { state?.getString("detail").orEmpty().takeIf { it.isNotBlank() }?.let { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
                leadingContent = { StatusDot(label) },
                trailingContent = { StatusText(label) },
              )
              if (index != featureStates.lastIndex) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
          }
        }
      }
      item { SectionTitle("クイック復旧") }
      item {
        Card {
          ListItem(
            colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = Color.Transparent),
            headlineContent = { Text("次回起動のみ全拡張OFF") },
            supportingContent = { Text("設定を消さず、次のLINE起動だけHookを適用しません") },
            trailingContent = {
              Switch(
                checked = nextLaunchOff,
                onCheckedChange = { checked ->
                  val ok = ControlClient.setNextLaunchOff(context, checked)
                  if (ok) nextLaunchOff = checked
                  scope.launch { snackbar.showSnackbar(if (ok) if (checked) "次回起動のみOFFを予約しました" else "予約を解除しました" else "変更に失敗しました") }
                },
              )
            },
          )
        }
      }
      item {
        Button(
          modifier = Modifier.fillMaxWidth(),
          enabled = lineVersion != null,
          onClick = { context.packageManager.getLaunchIntentForPackage("jp.naver.line.android")?.let { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } },
        ) { Text("LINEを開く") }
      }
      item {
        Text("設定変更後はLINEを完全終了して再起動してください。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
  settings: Bundle,
  onSettingsChanged: () -> Unit,
  onNavigate: (Screen) -> Unit,
  snackbar: SnackbarHostState,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val config = remember { VectorConfig() }
  val boolKeys = settings.getStringArrayList("booleanKeys").orEmpty().toSet()
  val developerModeEnabled =
    if (boolKeys.contains("developer_mode")) settings.getBoolean("bool.developer_mode", false) else false
  var showResetDialog by remember { mutableStateOf(false) }
  var showPresetDialog by remember { mutableStateOf(false) }
  var showBlockedChatsDialog by remember { mutableStateOf(false) }
  var showRestoreDialog by remember { mutableStateOf(false) }

  val createBackup =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
      if (uri != null) {
        scope.launch {
          val result = withContext(Dispatchers.IO) { runCatching { TenchaBackup.exportTo(context, uri) } }
          snackbar.showSnackbar(if (result.isSuccess) "バックアップを保存しました" else "バックアップに失敗しました: ${result.exceptionOrNull()?.message.orEmpty()}")
        }
      }
    }
  val restoreBackup =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) {
        scope.launch {
          val result = withContext(Dispatchers.IO) { runCatching { TenchaBackup.restoreFrom(context, uri) } }
          if (result.isSuccess) onSettingsChanged()
          snackbar.showSnackbar(if (result.isSuccess) "復元しました。LINEを再起動してください" else "復元に失敗しました: ${result.exceptionOrNull()?.message.orEmpty()}")
        }
      }
    }

  val blockedChats = remember(settings) {
    runCatching { JSONObject(settings.getString("string.read_blocked_chats_json", "{}")) }
      .getOrElse { JSONObject() }
  }

  if (showBlockedChatsDialog) {
    AlertDialog(
      onDismissRequest = { showBlockedChatsDialog = false },
      title = { Text("既読回避中のトーク") },
      text = {
        val ids = blockedChats.keys().asSequence().toList()
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          if (ids.isEmpty()) {
            Text("登録はありません。LINEのチャット上部にある本アイコンを長押しすると追加できます。")
          } else {
            ids.take(12).forEach { chatId ->
              val name = blockedChats.optJSONObject(chatId)?.optString("name", chatId) ?: chatId
              ListItem(
                headlineContent = { Text(name) },
                supportingContent = { Text(chatId, maxLines = 1) },
                trailingContent = {
                  TextButton(onClick = {
                    blockedChats.remove(chatId)
                    val ok = ControlClient.putSetting(context, "read_blocked_chats_json", blockedChats.toString())
                    if (ok) onSettingsChanged()
                    scope.launch { snackbar.showSnackbar(if (ok) "既読回避から削除しました" else "削除に失敗しました") }
                  }) { Text("削除") }
                },
              )
            }
            if (ids.size > 12) Text("ほか ${ids.size - 12} 件")
          }
        }
      },
      confirmButton = { TextButton(onClick = { showBlockedChatsDialog = false }) { Text("閉じる") } },
    )
  }

  if (showRestoreDialog) {
    AlertDialog(
      onDismissRequest = { showRestoreDialog = false },
      title = { Text("バックアップを復元しますか？") },
      text = { Text("現在のTencha設定と履歴を、選択したバックアップの内容で置き換えます。") },
      confirmButton = {
        TextButton(onClick = {
          showRestoreDialog = false
          restoreBackup.launch(arrayOf("application/zip", "application/octet-stream"))
        }) { Text("ファイルを選択") }
      },
      dismissButton = { TextButton(onClick = { showRestoreDialog = false }) { Text("キャンセル") } },
    )
  }

  if (showResetDialog) {
    AlertDialog(
      onDismissRequest = { showResetDialog = false },
      title = { Text("全設定を初期化しますか？") },
      text = { Text("機能設定のみ初期値へ戻します。履歴データは削除しません。") },
      confirmButton = {
        TextButton(onClick = {
          ControlClient.resetSettings(context); onSettingsChanged(); showResetDialog = false
          scope.launch { snackbar.showSnackbar("設定を初期化しました。LINEを再起動してください") }
        }) { Text("初期化") }
      },
      dismissButton = { TextButton(onClick = { showResetDialog = false }) { Text("キャンセル") } },
    )
  }

  if (showPresetDialog) {
    AlertDialog(
      onDismissRequest = { showPresetDialog = false },
      title = { Text("おすすめ設定を適用") },
      text = { Text("秒表示、既定ブラウザ、広告・おすすめ非表示、AgentI非表示、1文字検索をONにします。既読回避やFCM実験機能は含みません。") },
      confirmButton = {
        TextButton(onClick = {
          recommendedPreset.forEach { ControlClient.putSetting(context, it, true) }
          onSettingsChanged(); showPresetDialog = false
          scope.launch { snackbar.showSnackbar("適用しました。LINEを完全終了して再起動してください") }
        }) { Text("適用") }
      },
      dismissButton = { TextButton(onClick = { showPresetDialog = false }) { Text("キャンセル") } },
    )
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("機能設定") },
        actions = { TextButton(onClick = { showResetDialog = true }) { Text("初期化") } },
      )
    },
    bottomBar = { TenchaNavigationBar(Screen.SETTINGS, onNavigate) },
    snackbarHost = { SnackbarHost(snackbar) },
  ) { inner ->
    LazyColumn(
      modifier = Modifier.fillMaxSize().padding(inner),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      item {
        Card {
          Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("安全な実用プリセット", style = MaterialTheme.typography.titleMedium)
            Text("日常機能だけをまとめて有効化します。各項目は後から個別に変更できます。", style = MaterialTheme.typography.bodyMedium)
            FilledTonalButton(onClick = { showPresetDialog = true }) { Text("おすすめを適用") }
          }
        }
      }
      item {
        Card {
          ListItem(
            headlineContent = { Text("既読回避中のトークを管理") },
            supportingContent = { Text("登録 ${blockedChats.length()} 件 / 個別解除できます") },
            trailingContent = { TextButton(onClick = { showBlockedChatsDialog = true }) { Text("開く") } },
          )
        }
      }
      item {
        Card {
          Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("バックアップ", style = MaterialTheme.typography.titleMedium)
            Text("端末のフォルダまたはGoogle Driveへ、Tenchaの設定・履歴とLINE内で作成したトーク履歴バックアップを保存します。", style = MaterialTheme.typography.bodyMedium)
            Text("履歴を含むため、作成したファイルの共有には注意してください。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              FilledTonalButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                  val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                  createBackup.launch("Tencha-backup-$stamp.tencha.zip")
                },
              ) { Text("バックアップ") }
              OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { showRestoreDialog = true }) { Text("復元") }
            }
          }
        }
      }
      VectorConfig.Category.entries.forEach { category ->
        val categoryItems = config.items.filter {
          it.category == category &&
            (category != VectorConfig.Category.DEVELOPER || it.key == "developer_mode" || developerModeEnabled)
        }
        if (categoryItems.isNotEmpty()) {
          item { SectionTitle(category.label) }
          items(categoryItems, key = { it.key }) { option ->
            val checked = if (boolKeys.contains(option.key)) settings.getBoolean("bool.${option.key}", option.enabled) else option.enabled
            val dependencyEnabled = option.disabledWhenEnabledKey?.let { dependency ->
              val defaultValue = config.items.firstOrNull { it.key == dependency }?.enabled ?: false
              !(if (boolKeys.contains(dependency)) settings.getBoolean("bool.$dependency", defaultValue) else defaultValue)
            } ?: true
            val experimental = experimentalKeys.contains(option.key)
            ListItem(
              headlineContent = { Text(option.label) },
              supportingContent = {
                Column {
                  if (option.description.isNotBlank()) Text(option.description)
                  if (experimental) Text("実験的機能・不具合時は個別にOFF", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                  if (option.key == "custom_font_path") Text("フォント選択はLINE内の拡張設定から行います")
                  if (option.key == "home_tab_type") Text("ホーム種別の選択はLINE内の拡張設定から行います")
                  if (option.key == "fcm_fix_mode") Text("FCM方式の選択はLINE内の拡張設定から行います")
                }
              },
              trailingContent = {
                if (option.key != "custom_font_path" && option.key != "home_tab_type" && option.key != "fcm_fix_mode") {
                  Switch(
                    checked = checked, enabled = dependencyEnabled,
                    onCheckedChange = { enabled ->
                      val ok = ControlClient.putSetting(context, option.key, enabled)
                      if (ok) onSettingsChanged()
                      scope.launch { snackbar.showSnackbar(if (ok) if (restartRequiredKeys.contains(option.key)) "保存しました。LINE再起動後に反映します" else "保存しました" else "保存に失敗しました") }
                    },
                  )
                }
              },
            )
          }
        }
      }
      item { Spacer(Modifier.height(12.dp)) }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiagnosticsScreen(
  snapshot: Bundle,
  onRefresh: () -> Unit,
  onNavigate: (Screen) -> Unit,
  snackbar: SnackbarHostState,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val ids = snapshot.getStringArrayList("featureIds").orEmpty().sorted()
  Scaffold(
    topBar = { TopAppBar(title = { Text("診断・復旧") }, actions = { TextButton(onClick = onRefresh) { Text("更新") } }) },
    bottomBar = { TenchaNavigationBar(Screen.DIAGNOSTICS, onNavigate) },
    snackbarHost = { SnackbarHost(snackbar) },
  ) { inner ->
    LazyColumn(
      modifier = Modifier.fillMaxSize().padding(inner),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      item {
        Card {
          Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("障害復旧", style = MaterialTheme.typography.titleMedium)
            Text("Safe Modeは連続してHook登録に失敗した機能だけを停止します。個人情報やメッセージ本文は診断へ保存しません。")
            OutlinedButton(onClick = {
              val ok = ControlClient.clearAllSafeModes(context); onRefresh()
              scope.launch { snackbar.showSnackbar(if (ok) "Safe Modeを解除しました" else "解除に失敗しました") }
            }) { Text("すべてのSafe Modeを解除") }
          }
        }
      }
      item { SectionTitle("Hook状態") }
      if (ids.isEmpty()) {
        item { Text("実行記録がありません。VectorでTenchaを有効化してLINEを起動してください。") }
      } else {
        items(ids, key = { it }) { id ->
          val state = snapshot.getBundle("feature.$id") ?: Bundle.EMPTY
          val status = FeatureStatus.entries.firstOrNull { it.name == state.getString("status") }?.label ?: "不明"
          ListItem(
            headlineContent = { Text(id) },
            supportingContent = {
              Column {
                state.getString("detail", "").takeIf { it.isNotBlank() }?.let { Text(it) }
                Text("連続失敗 ${state.getInt("failures", 0)} / 最終成功 ${formatTime(state.getLong("lastSuccess", 0L))}", style = MaterialTheme.typography.bodySmall)
              }
            },
            trailingContent = { StatusText(status) },
          )
        }
      }
      item { Text("「動作中」はRuntime実行報告が届いた場合だけ表示します。Hook登録済みは「確認中」です。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutScreen(onNavigate: (Screen) -> Unit, snackbar: SnackbarHostState) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var downloadedApk by remember { mutableStateOf<java.io.File?>(null) }
  var updateStatus by remember { mutableStateOf("1回押すだけで最新版へ更新します") }
  var updateBusy by remember { mutableStateOf(false) }
  val installPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
      val readyApk = downloadedApk
      when {
        readyApk == null || !readyApk.isFile -> {
          updateStatus = "ダウンロード済みAPKが見つかりません"
        }
        !GitHubUpdater.canRequestInstall(context) -> {
          updateStatus = "インストールの許可が必要です"
          scope.launch { snackbar.showSnackbar("Tenchaからのアプリインストールを許可してください") }
        }
        else -> {
          runCatching { GitHubUpdater.launchInstaller(context, readyApk) }
            .onSuccess { updateStatus = "インストーラーを開きました" }
            .onFailure {
              updateStatus = "インストーラーを開けませんでした"
              scope.launch { snackbar.showSnackbar("インストーラーを開けません: ${it.message.orEmpty()}") }
            }
        }
      }
    }
  Scaffold(
    topBar = { TopAppBar(title = { Text("Tenchaについて") }) },
    bottomBar = { TenchaNavigationBar(Screen.ABOUT, onNavigate) },
    snackbarHost = { SnackbarHost(snackbar) },
  ) { inner ->
    LazyColumn(
      modifier = Modifier.fillMaxSize().padding(inner),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      item {
        Card {
          Column(
            Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Icon(painterResource(R.drawable.ic_tencha_settings), contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
            Text("Tencha", style = MaterialTheme.typography.headlineSmall)
            Text("Enhance your LINE.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelLarge)
          }
        }
      }
      item {
        Card {
          Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Text("アプリの更新", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(updateStatus, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (updateBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Button(
              modifier = Modifier.fillMaxWidth(),
              enabled = !updateBusy,
              onClick = {
                val readyApk = downloadedApk
                if (readyApk != null && readyApk.isFile) {
                  if (GitHubUpdater.canRequestInstall(context)) {
                    runCatching { GitHubUpdater.launchInstaller(context, readyApk) }
                      .onSuccess { updateStatus = "インストーラーを開きました" }
                      .onFailure { scope.launch { snackbar.showSnackbar("インストーラーを開けません: ${it.message.orEmpty()}") } }
                  } else {
                    updateStatus = "インストールの許可を有効にしてください"
                    installPermissionLauncher.launch(GitHubUpdater.installPermissionIntent(context))
                  }
                  return@Button
                }

                updateBusy = true
                updateStatus = "最新版を確認しています…"
                scope.launch {
                  try {
                    val available = withContext(Dispatchers.IO) { GitHubUpdater.checkLatest() }
                    if (!available.isNewerThanCurrent) {
                      updateStatus = "最新版です（v${BuildConfig.VERSION_NAME}）"
                      return@launch
                    }

                    updateStatus = "v${available.version} をダウンロードしています…"
                    val apk = withContext(Dispatchers.IO) { GitHubUpdater.downloadAndVerify(context, available) }
                    downloadedApk = apk
                    updateStatus = "検証完了。インストーラーを開きます…"
                    updateBusy = false

                    if (GitHubUpdater.canRequestInstall(context)) {
                      GitHubUpdater.launchInstaller(context, apk)
                      updateStatus = "インストーラーを開きました"
                    } else {
                      updateStatus = "インストールの許可を有効にしてください"
                      installPermissionLauncher.launch(GitHubUpdater.installPermissionIntent(context))
                    }
                  } catch (error: Exception) {
                    updateStatus = "更新に失敗しました"
                    snackbar.showSnackbar(error.message ?: "GitHubへ接続できません")
                  } finally {
                    updateBusy = false
                  }
                }
              },
            ) {
              Text(if (updateBusy) "更新中…" else "最新版へ更新")
            }
            Text(
              "APKの署名とSHA-256を検証してから更新します。",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
      item {
        Card {
          ListItem(
            headlineContent = { Text("rootなしで使う") },
            supportingContent = { Text("LSPatchとShizukuを使った非rootセットアップ") },
            trailingContent = { TextButton(onClick = { onNavigate(Screen.ROOTLESS) }) { Text("開く") } },
          )
        }
      }
      item { SectionTitle("Creator") }
      item {
        Card {
          Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("yukkuri-matcha-tea", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            SocialLinkButton("GitHub", "github.com/yukkuri-matcha-tea") { openUrl(context, "https://github.com/yukkuri-matcha-tea") }
            SocialLinkButton("X", "@yukkuri_matcha_") { openUrl(context, "https://x.com/yukkuri_matcha_") }
            SocialLinkButton("YouTube", "ゆっくり抹茶ティー") { openUrl(context, "https://www.youtube.com/channel/UCuhltKmciQLwQTBEIIiCH2g") }
          }
        }
      }
      item { SectionTitle("このアプリについて") }
      item {
        Card {
          Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("TenchaはAndroid版LINEを拡張する非公式プロジェクトです。LINEヤフー株式会社とは関係ありません。", style = MaterialTheme.typography.bodyMedium)
            Text("LINEやTenchaの更新により互換性が失われる場合があります。重要なデータは事前にバックアップしてください。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RootlessSetupScreen(onBack: () -> Unit, snackbar: SnackbarHostState) {
  val context = LocalContext.current
  val lspatchVersion = remember { installedPackageVersion(context.packageManager, "org.lsposed.lspatch") }
  val lspatchReady = remember(lspatchVersion) { lspatchVersion != null && versionAtLeast(lspatchVersion, "1.1") }
  val shizukuVersion = remember { installedPackageVersion(context.packageManager, "moe.shizuku.privileged.api") }
  val lineVersion = remember { installedLineVersion(context.packageManager) }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("非rootセットアップ") },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "戻る")
          }
        },
      )
    },
    snackbarHost = { SnackbarHost(snackbar) },
  ) { inner ->
    LazyColumn(
      modifier = Modifier.fillMaxSize().padding(inner),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
          Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("LSPatch版Tencha", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text("rootやZygiskを使わず、利用者自身のLINEへTenchaを読み込ませます。", style = MaterialTheme.typography.bodyMedium)
            Text("Shizukuはパッチ済みアプリのインストール補助に使います。", style = MaterialTheme.typography.bodySmall)
          }
        }
      }
      item { SectionTitle("準備状況") }
      item {
        Card {
          Column {
            SetupStatusItem("LINE", lineVersion?.let { "インストール済み $it" } ?: "未インストール", lineVersion != null)
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            SetupStatusItem(
              "LSPatch Manager",
              when {
                lspatchVersion == null -> "未インストール"
                lspatchReady -> "使用可能 $lspatchVersion"
                else -> "更新が必要 $lspatchVersion → 1.1以上"
              },
              lspatchReady,
            )
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            SetupStatusItem("Shizuku", shizukuVersion?.let { "インストール済み $it" } ?: "未インストール・任意", shizukuVersion != null)
          }
        }
      }
      item {
        Button(
          modifier = Modifier.fillMaxWidth(),
          onClick = {
            if (lspatchReady) openPackage(context, "org.lsposed.lspatch")
            else openUrl(context, "https://github.com/JingMatrix/LSPatch/releases/latest")
          },
        ) { Text(if (lspatchReady) "LSPatchを開く" else if (lspatchVersion != null) "LSPatch 1.1へ更新" else "LSPatchを入手") }
      }
      item {
        OutlinedButton(
          modifier = Modifier.fillMaxWidth(),
          onClick = {
            if (shizukuVersion != null) openPackage(context, "moe.shizuku.privileged.api")
            else openUrl(context, "https://shizuku.rikka.app/download/")
          },
        ) { Text(if (shizukuVersion != null) "Shizukuを開く" else "Shizukuを入手") }
      }
      item { SectionTitle("導入手順") }
      item {
        Card {
          Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SetupStep("1", "LINEをバックアップ", "アカウント情報を確認し、必要なトーク履歴をLINE公式機能でバックアップします。")
            SetupStep("2", "Shizukuを起動", "任意。Android 11以降はワイヤレスデバッグから起動できます。")
            SetupStep("3", "LSPatchでLINEを選択", "Manager modeでLINEをパッチします。Split APKはLSPatchが一式として処理します。")
            SetupStep("4", "Tenchaを有効化", "LSPatchのモジュール画面でTenchaを選び、パッチ済みLINEへ適用します。")
            SetupStep("5", "LINEを再起動", "Tenchaホームで接続状態と機能のRuntime報告を確認します。")
          }
        }
      }
      item {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
          Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("インストール前の注意", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("パッチ済みLINEは公式版と署名が異なるため、公式LINEを直接上書きできない場合があります。TenchaはLINEの削除やデータ消去を自動実行しません。", style = MaterialTheme.typography.bodyMedium)
          }
        }
      }
    }
  }
}

@Composable
private fun SetupStatusItem(title: String, detail: String, ready: Boolean) {
  ListItem(
    colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = Color.Transparent),
    headlineContent = { Text(title) },
    supportingContent = { Text(detail) },
    leadingContent = { StatusDot(if (ready) "接続済み" else "未接続") },
  )
}

@Composable
private fun SetupStep(number: String, title: String, description: String) {
  Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
    Text(number, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(title, style = MaterialTheme.typography.titleMedium)
      Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

@Composable
private fun TenchaNavigationBar(selected: Screen, onNavigate: (Screen) -> Unit) {
  NavigationBar {
    NavigationItem(Screen.HOME, "ホーム", R.drawable.ic_nav_home, selected, onNavigate)
    NavigationItem(Screen.SETTINGS, "機能", R.drawable.ic_nav_tune, selected, onNavigate)
    NavigationItem(Screen.DIAGNOSTICS, "診断", R.drawable.ic_nav_health, selected, onNavigate)
    NavigationItem(Screen.ABOUT, "情報", R.drawable.ic_nav_info, selected, onNavigate)
  }
}

@Composable
private fun RowScope.NavigationItem(
  screen: Screen,
  label: String,
  icon: Int,
  selected: Screen,
  onNavigate: (Screen) -> Unit,
) {
  NavigationBarItem(
    selected = selected == screen,
    onClick = { if (selected != screen) onNavigate(screen) },
    icon = { Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(24.dp)) },
    label = { Text(label) },
  )
}

@Composable
private fun StatusDot(status: String) {
  val color = when (status) {
    "動作中", "接続済み" -> MaterialTheme.colorScheme.primary
    "Hook失敗", "Safe Mode", "未接続" -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.outline
  }
  Box(Modifier.size(10.dp).background(color, CircleShape))
}

@Composable
private fun SocialLinkButton(title: String, subtitle: String, onClick: () -> Unit) {
  OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
      Text(title, fontWeight = FontWeight.SemiBold)
      Text(subtitle, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
  }
}

@Composable
private fun SectionTitle(text: String) {
  Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun StatusText(text: String) {
  val color = when (text) {
    "動作中", "接続済み" -> MaterialTheme.colorScheme.primary
    "Hook失敗", "Safe Mode", "未接続" -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
  }
  Text(text, style = MaterialTheme.typography.labelLarge, color = color)
}

private fun formatTime(time: Long): String =
  if (time <= 0L) "なし" else DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date(time))

private fun installedLineVersion(packageManager: PackageManager): String? =
  try {
    @Suppress("DEPRECATION") packageManager.getPackageInfo("jp.naver.line.android", 0).versionName
  } catch (_: PackageManager.NameNotFoundException) {
    null
  }

private fun installedPackageVersion(packageManager: PackageManager, packageName: String): String? =
  try {
    @Suppress("DEPRECATION") packageManager.getPackageInfo(packageName, 0).versionName
  } catch (_: PackageManager.NameNotFoundException) {
    null
  }

private fun versionAtLeast(actual: String, required: String): Boolean {
  val actualParts = actual.split('.').map { it.toIntOrNull() ?: 0 }
  val requiredParts = required.split('.').map { it.toIntOrNull() ?: 0 }
  val count = maxOf(actualParts.size, requiredParts.size)
  return (0 until count).firstNotNullOfOrNull { index ->
    val left = actualParts.getOrElse(index) { 0 }
    val right = requiredParts.getOrElse(index) { 0 }
    when {
      left > right -> true
      left < right -> false
      else -> null
    }
  } ?: true
}

private fun openPackage(context: android.content.Context, packageName: String) {
  context.packageManager.getLaunchIntentForPackage(packageName)?.let {
    context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
  }
}

private fun openUrl(context: android.content.Context, url: String) {
  context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
