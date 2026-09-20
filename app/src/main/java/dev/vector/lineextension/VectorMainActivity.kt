package dev.vector.lineextension

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import dev.vector.lineextension.core.ControlClient
import dev.vector.lineextension.core.GitHubUpdater
import dev.vector.lineextension.core.RuntimeEnvironment
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VectorMainActivity : ComponentActivity() {
  private val resumeGeneration = mutableIntStateOf(0)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent { VectorTheme { TenchaApp(resumeGeneration.intValue) } }
  }

  override fun onResume() {
    super.onResume()
    resumeGeneration.intValue++
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

private const val UPDATE_CHECK_PREFS = "tencha_update_check"
private const val UPDATE_LAST_CHECKED_AT = "last_checked_at"
private const val UPDATE_CHECKED_APP_VERSION = "checked_app_version"
private const val UPDATE_AVAILABLE = "update_available"
private const val UPDATE_AVAILABLE_VERSION = "available_version"
private const val UPDATE_CHECK_INTERVAL_MS = 24L * 60L * 60L * 1000L

internal fun currentConnectionReported(
  lastSeen: Long,
  reportedLineVersion: String,
  installedLineVersion: String?,
  tenchaUpdatedAt: Long,
  lineUpdatedAt: Long,
  compatibilityState: String,
): Boolean =
  lastSeen > 0L &&
    installedLineVersion != null &&
    reportedLineVersion == installedLineVersion &&
    lastSeen >= maxOf(tenchaUpdatedAt, lineUpdatedAt) &&
    compatibilityState != "unsupported"

internal fun runtimeModeLabel(mode: String, reported: Boolean): String =
  when (mode) {
    RuntimeEnvironment.MODE_ROOT -> "Vector経由で動作中（root）"
    RuntimeEnvironment.MODE_LSPATCH -> "LSPatch経由で動作中（非root）"
    else -> if (reported) "接続済み（実行方式を判定できません）" else "LINE再起動後に実行方式を表示"
  }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TenchaApp(resumeGeneration: Int) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val snackbar = remember { SnackbarHostState() }
  var snapshot by remember { mutableStateOf(ControlClient.snapshot(context)) }
  val linePackage = remember(resumeGeneration) { packageInfoOrNull(context.packageManager, "jp.naver.line.android") }
  val tenchaPackage = remember(resumeGeneration) { packageInfoOrNull(context.packageManager, context.packageName) }
  val lineVersion = linePackage?.versionName
  val lastSeen = snapshot.getLong("lastLineSeen", 0L)
  val connected = currentConnectionReported(
    lastSeen,
    snapshot.getString("lineVersion", ""),
    lineVersion,
    tenchaPackage?.lastUpdateTime ?: 0L,
    linePackage?.lastUpdateTime ?: 0L,
    snapshot.getString("compatibilityState", "unknown"),
  )
  val updatePrefs = remember { context.getSharedPreferences(UPDATE_CHECK_PREFS, Context.MODE_PRIVATE) }
  var updateAvailable by remember {
    mutableStateOf(
      updatePrefs.getString(UPDATE_CHECKED_APP_VERSION, null) == BuildConfig.VERSION_NAME &&
        updatePrefs.getBoolean(UPDATE_AVAILABLE, false),
    )
  }
  var availableVersion by remember { mutableStateOf(updatePrefs.getString(UPDATE_AVAILABLE_VERSION, null)) }
  var downloadedApk by remember { mutableStateOf<java.io.File?>(null) }
  var updateStatus by remember { mutableStateOf<String?>(null) }
  var updateBusy by remember { mutableStateOf(false) }

  fun recordUpdateResult(available: Boolean, version: String?) {
    updateAvailable = available
    availableVersion = version
    updatePrefs.edit {
      putLong(UPDATE_LAST_CHECKED_AT, System.currentTimeMillis())
      putString(UPDATE_CHECKED_APP_VERSION, BuildConfig.VERSION_NAME)
      putBoolean(UPDATE_AVAILABLE, available)
      putString(UPDATE_AVAILABLE_VERSION, version)
    }
  }

  LaunchedEffect(resumeGeneration) { snapshot = ControlClient.snapshot(context) }
  LaunchedEffect(Unit) {
    val now = System.currentTimeMillis()
    val lastCheckedAt = updatePrefs.getLong(UPDATE_LAST_CHECKED_AT, 0L)
    val checkedVersion = updatePrefs.getString(UPDATE_CHECKED_APP_VERSION, null)
    if (checkedVersion != BuildConfig.VERSION_NAME || lastCheckedAt <= 0L || now - lastCheckedAt >= UPDATE_CHECK_INTERVAL_MS) {
      updatePrefs.edit {
        putLong(UPDATE_LAST_CHECKED_AT, now)
        putString(UPDATE_CHECKED_APP_VERSION, BuildConfig.VERSION_NAME)
        if (checkedVersion != BuildConfig.VERSION_NAME) {
          putBoolean(UPDATE_AVAILABLE, false)
          remove(UPDATE_AVAILABLE_VERSION)
        }
      }
      runCatching { withContext(Dispatchers.IO) { GitHubUpdater.checkLatest() } }
        .onSuccess { recordUpdateResult(it.isNewerThanCurrent, it.version) }
    }
  }

  val installPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
      val apk = downloadedApk
      if (apk == null || !apk.isFile) {
        updateStatus = "ダウンロード済みAPKが見つかりません"
      } else if (!GitHubUpdater.canRequestInstall(context)) {
        updateStatus = "Tenchaからのアプリインストールを許可してください"
      } else {
        runCatching { GitHubUpdater.launchInstaller(context, apk) }
          .onSuccess { updateStatus = "インストーラーを開きました" }
          .onFailure { updateStatus = "インストーラーを開けませんでした" }
      }
    }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Tencha", fontWeight = FontWeight.SemiBold) },
        actions = { TextButton(onClick = { snapshot = ControlClient.snapshot(context) }) { Text("再確認") } },
      )
    },
    snackbarHost = { SnackbarHost(snackbar) },
  ) { inner ->
    LazyColumn(
      modifier = Modifier.fillMaxSize().padding(inner),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      item {
        Card {
          Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(painterResource(R.drawable.ic_tencha_settings), contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
              Text("Tencha", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
              Text("Enhance your LINE.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
        }
      }
      item {
        val color by animateColorAsState(
          if (connected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
          animationSpec = tween(250),
          label = "Connection background",
        )
        Card(colors = CardDefaults.cardColors(containerColor = color)) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Box(Modifier.size(14.dp).background(if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text(if (connected) "LINEに接続済み" else "LINEへの接続を確認できません", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
              if (connected) {
                Text(runtimeModeLabel(snapshot.getString("loaderMode", "unknown"), snapshot.getBoolean("loaderModeReported", false)), style = MaterialTheme.typography.bodyMedium)
              } else {
                Text(if (lastSeen > 0L) "更新後の接続を確認するにはLINEを再起動してください" else "LINEでTenchaを有効化して起動してください", style = MaterialTheme.typography.bodyMedium)
              }
              if (lastSeen > 0L) Text("最終確認 ${formatTime(lastSeen)}", style = MaterialTheme.typography.labelMedium)
            }
          }
        }
      }
      item {
        Card {
          Column {
            ListItem(headlineContent = { Text("LINEのバージョン") }, trailingContent = { Text(lineVersion ?: "未導入", style = MaterialTheme.typography.titleMedium) })
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            ListItem(headlineContent = { Text("Tenchaのバージョン") }, trailingContent = { Text(BuildConfig.VERSION_NAME, style = MaterialTheme.typography.titleMedium) })
          }
        }
      }
      item {
        Card {
          Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("機能の設定", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("機能のオン・オフはLINEの「設定 → Tencha → モジュール設定」で変更できます。", style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(
              modifier = Modifier.fillMaxWidth(),
              enabled = lineVersion != null,
              onClick = { openPackage(context, "jp.naver.line.android") },
            ) { Text("LINEを開く") }
          }
        }
      }
      item { SectionTitle("Tenchaについて") }
      item {
        Card {
          Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("TenchaはAndroid版LINEを拡張する非公式プロジェクトです。LINEヤフー株式会社とは関係ありません。", style = MaterialTheme.typography.bodyMedium)
            Text("LINEやTenchaの更新により互換性が失われる場合があります。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider()
            Text(if (updateAvailable) "v${availableVersion ?: "最新版"} が利用できます" else "GitHubから更新", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            AnimatedVisibility(visible = updateStatus != null, enter = fadeIn(tween(180)), exit = fadeOut(tween(120))) {
              updateStatus?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
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
                } else {
                  updateBusy = true
                  updateStatus = "最新版を確認しています…"
                  scope.launch {
                    try {
                      val latest = withContext(Dispatchers.IO) { GitHubUpdater.checkLatest() }
                      recordUpdateResult(latest.isNewerThanCurrent, latest.version)
                      if (!latest.isNewerThanCurrent) {
                        updateStatus = "最新版です（v${BuildConfig.VERSION_NAME}）"
                      } else {
                        updateStatus = "v${latest.version} をダウンロードしています…"
                        val apk = withContext(Dispatchers.IO) { GitHubUpdater.downloadAndVerify(context, latest) }
                        downloadedApk = apk
                        updateStatus = "検証完了。インストーラーを開きます…"
                        if (GitHubUpdater.canRequestInstall(context)) {
                          GitHubUpdater.launchInstaller(context, apk)
                          updateStatus = "インストーラーを開きました"
                        } else {
                          updateStatus = "インストールの許可を有効にしてください"
                          installPermissionLauncher.launch(GitHubUpdater.installPermissionIntent(context))
                        }
                      }
                    } catch (error: Exception) {
                      updateStatus = "更新に失敗しました"
                      snackbar.showSnackbar(error.message ?: "GitHubへ接続できません")
                    } finally {
                      updateBusy = false
                    }
                  }
                }
              },
            ) { Text(if (updateBusy) "更新中…" else "最新版へ更新") }
          }
        }
      }
      item { SectionTitle("開発者") }
      item {
        Card {
          Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("yukkuri-matcha-tea", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            SocialLinkButton("GitHub", "github.com/yukkuri-matcha-tea", R.drawable.ic_social_github) { openUrl(context, "https://github.com/yukkuri-matcha-tea") }
            SocialLinkButton("X", "@yukkuri_matcha_", R.drawable.ic_social_x) { openUrl(context, "https://x.com/yukkuri_matcha_") }
            SocialLinkButton("YouTube", "ゆっくり抹茶ティー", R.drawable.ic_social_youtube) { openUrl(context, "https://www.youtube.com/channel/UCuhltKmciQLwQTBEIIiCH2g") }
          }
        }
      }
      item { Spacer(Modifier.height(8.dp)) }
    }
  }
}

@Composable
private fun SectionTitle(text: String) {
  Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun SocialLinkButton(title: String, subtitle: String, iconRes: Int, onClick: () -> Unit) {
  OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
      Icon(painterResource(iconRes), contentDescription = null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurface)
      Text(title, fontWeight = FontWeight.SemiBold)
      Text(subtitle, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
  }
}

private fun formatTime(time: Long): String =
  DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date(time))

private fun packageInfoOrNull(packageManager: PackageManager, packageName: String): android.content.pm.PackageInfo? =
  try {
    @Suppress("DEPRECATION") packageManager.getPackageInfo(packageName, 0)
  } catch (_: PackageManager.NameNotFoundException) {
    null
  }

private fun openPackage(context: Context, packageName: String) {
  context.packageManager.getLaunchIntentForPackage(packageName)?.let {
    context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
  }
}

private fun openUrl(context: Context, url: String) {
  context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
