package dev.vector.lineextension.core;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import androidx.core.content.FileProvider;
import dev.vector.lineextension.BuildConfig;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/** Checks and installs signed root-edition Tencha APKs published through GitHub Releases. */
public final class GitHubUpdater {
  private static final String RELEASE_API =
      "https://api.github.com/repos/yukkuri-matcha-tea/Tencha/releases/latest";
  private static final String PACKAGE_NAME = "dev.vector.lineextension";
  private static final long MAX_METADATA_BYTES = 1024L * 1024;
  private static final long MAX_APK_BYTES = 100L * 1024 * 1024;

  private GitHubUpdater() {}

  public static final class UpdateInfo {
    public final String version;
    public final String assetName;
    public final String downloadUrl;
    public final String sha256;
    public final String releaseUrl;

    private UpdateInfo(
        String version, String assetName, String downloadUrl, String sha256, String releaseUrl) {
      this.version = version;
      this.assetName = assetName;
      this.downloadUrl = downloadUrl;
      this.sha256 = sha256;
      this.releaseUrl = releaseUrl;
    }

    public boolean isNewerThanCurrent() {
      return compareVersions(version, BuildConfig.VERSION_NAME) > 0;
    }
  }

  public static UpdateInfo checkLatest() throws Exception {
    HttpURLConnection connection = open(RELEASE_API);
    connection.setRequestProperty("Accept", "application/vnd.github+json");
    connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
    try {
      requireSuccess(connection);
      String response;
      try (InputStream in = connection.getInputStream()) {
        response = new String(readLimited(in, MAX_METADATA_BYTES), StandardCharsets.UTF_8);
      }
      JSONObject release = new JSONObject(response);
      if (release.optBoolean("draft") || release.optBoolean("prerelease")) {
        throw new IllegalStateException("安定版Releaseではありません");
      }
      String version = release.optString("tag_name", "").replaceFirst("^[vV]", "");
      if (!version.matches("[0-9]+(?:\\.[0-9]+){1,3}")) {
        throw new IllegalStateException("Releaseのバージョン表記が不正です");
      }
      JSONArray assets = release.optJSONArray("assets");
      if (assets == null) throw new IllegalStateException("ReleaseにAPKがありません");
      String expectedName = "Tencha-root-" + version + ".apk";
      for (int i = 0; i < assets.length(); i++) {
        JSONObject asset = assets.optJSONObject(i);
        if (asset == null || !expectedName.equals(asset.optString("name"))) continue;
        String digest = asset.optString("digest", "");
        String sha256 = digest.startsWith("sha256:") ? digest.substring(7) : "";
        if (!sha256.matches("[0-9a-fA-F]{64}")) {
          throw new SecurityException("GitHub ReleaseにSHA-256がありません");
        }
        return new UpdateInfo(
            version,
            expectedName,
            asset.optString("browser_download_url"),
            sha256.toLowerCase(Locale.ROOT),
            release.optString("html_url"));
      }
      throw new IllegalStateException("root版Tencha APKがReleaseにありません");
    } finally {
      connection.disconnect();
    }
  }

  public static File downloadAndVerify(Context context, UpdateInfo info) throws Exception {
    if (info == null || info.downloadUrl.isBlank())
      throw new IllegalArgumentException("更新情報がありません");
    File updateDir = new File(context.getCacheDir(), "updates");
    deleteContents(updateDir);
    if (!updateDir.isDirectory() && !updateDir.mkdirs()) {
      throw new IllegalStateException("更新保存領域を作れません");
    }
    File temporary = new File(updateDir, info.assetName + ".part");
    File completed = new File(updateDir, info.assetName);
    HttpURLConnection connection = open(info.downloadUrl);
    try {
      requireSuccess(connection);
      long declared = connection.getContentLengthLong();
      if (declared > MAX_APK_BYTES) throw new IllegalStateException("APKが大きすぎます");
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      long total = 0L;
      try (InputStream in = connection.getInputStream();
          FileOutputStream out = new FileOutputStream(temporary, false)) {
        byte[] buffer = new byte[64 * 1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
          total += read;
          if (total > MAX_APK_BYTES) throw new IllegalStateException("APKが大きすぎます");
          digest.update(buffer, 0, read);
          out.write(buffer, 0, read);
        }
        out.getFD().sync();
      }
      String actual = hex(digest.digest());
      if (!actual.equalsIgnoreCase(info.sha256)) throw new SecurityException("APKのSHA-256が一致しません");
      verifyApk(context, temporary);
      try {
        Files.move(
            temporary.toPath(),
            completed.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING);
      } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
        Files.move(temporary.toPath(), completed.toPath(), StandardCopyOption.REPLACE_EXISTING);
      }
      return completed;
    } finally {
      connection.disconnect();
      if (temporary.exists()) temporary.delete();
    }
  }

  public static boolean canRequestInstall(Context context) {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.O
        || context.getPackageManager().canRequestPackageInstalls();
  }

  public static void openInstallPermission(Context context) {
    Intent intent =
        new Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:" + context.getPackageName()));
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  }

  public static void launchInstaller(Context context, File apk) {
    if (apk == null || !apk.isFile()) throw new IllegalArgumentException("APKがありません");
    Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".update_files", apk);
    Intent intent = new Intent(Intent.ACTION_VIEW);
    intent.setDataAndType(uri, "application/vnd.android.package-archive");
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
    context.startActivity(intent);
  }

  static int compareVersions(String left, String right) {
    int[] a = versionParts(left);
    int[] b = versionParts(right);
    for (int i = 0; i < Math.max(a.length, b.length); i++) {
      int av = i < a.length ? a[i] : 0;
      int bv = i < b.length ? b[i] : 0;
      if (av != bv) return Integer.compare(av, bv);
    }
    return 0;
  }

  private static int[] versionParts(String value) {
    String normalized = value == null ? "" : value.replaceFirst("^[vV]", "");
    String[] parts = normalized.split("\\.");
    int[] result = new int[parts.length];
    for (int i = 0; i < parts.length; i++) {
      try {
        result[i] = Integer.parseInt(parts[i].replaceAll("[^0-9].*$", ""));
      } catch (NumberFormatException ignored) {
        result[i] = 0;
      }
    }
    return result;
  }

  private static void verifyApk(Context context, File apk) throws Exception {
    PackageManager pm = context.getPackageManager();
    int flags =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
            ? PackageManager.GET_SIGNING_CERTIFICATES
            : PackageManager.GET_SIGNATURES;
    @SuppressWarnings("deprecation")
    PackageInfo candidate = pm.getPackageArchiveInfo(apk.getAbsolutePath(), flags);
    if (candidate == null || !PACKAGE_NAME.equals(candidate.packageName)) {
      throw new SecurityException("Tencha APKではありません");
    }
    @SuppressWarnings("deprecation")
    PackageInfo installed = pm.getPackageInfo(PACKAGE_NAME, flags);
    if (longVersionCode(candidate) <= longVersionCode(installed)) {
      throw new IllegalStateException("現在より新しいAPKではありません");
    }
    if (!certificateDigests(candidate).equals(certificateDigests(installed))) {
      throw new SecurityException("APKの署名が現在のTenchaと一致しません");
    }
  }

  @SuppressWarnings("deprecation")
  private static Signature[] signatures(PackageInfo info) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && info.signingInfo != null) {
      return info.signingInfo.hasMultipleSigners()
          ? info.signingInfo.getApkContentsSigners()
          : info.signingInfo.getSigningCertificateHistory();
    }
    return info.signatures == null ? new Signature[0] : info.signatures;
  }

  private static Set<String> certificateDigests(PackageInfo info) throws Exception {
    Set<String> values = new HashSet<>();
    for (Signature signature : signatures(info)) {
      values.add(hex(MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())));
    }
    if (values.isEmpty()) throw new SecurityException("APK署名を確認できません");
    return values;
  }

  @SuppressWarnings("deprecation")
  private static long longVersionCode(PackageInfo info) {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
        ? info.getLongVersionCode()
        : info.versionCode;
  }

  private static HttpURLConnection open(String url) throws Exception {
    HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
    connection.setInstanceFollowRedirects(true);
    connection.setConnectTimeout(15_000);
    connection.setReadTimeout(60_000);
    connection.setRequestProperty(
        "User-Agent", "Tencha-Android-Updater/" + BuildConfig.VERSION_NAME);
    return connection;
  }

  private static void requireSuccess(HttpURLConnection connection) throws Exception {
    int code = connection.getResponseCode();
    if (code < 200 || code >= 300) throw new IllegalStateException("GitHub HTTP " + code);
  }

  private static byte[] readLimited(InputStream in, long limit) throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buffer = new byte[8192];
    long total = 0L;
    int read;
    while ((read = in.read(buffer)) != -1) {
      total += read;
      if (total > limit) throw new IllegalStateException("GitHub応答が大きすぎます");
      out.write(buffer, 0, read);
    }
    return out.toByteArray();
  }

  private static void deleteContents(File directory) {
    File[] files = directory.listFiles();
    if (files == null) return;
    for (File file : files) {
      if (file.isDirectory()) deleteContents(file);
      file.delete();
    }
  }

  private static String hex(byte[] bytes) {
    StringBuilder result = new StringBuilder(bytes.length * 2);
    for (byte value : bytes) result.append(String.format(Locale.ROOT, "%02x", value & 0xff));
    return result.toString();
  }
}
