package dev.vector.lineextension;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LineVersion {

  private static volatile String detectedVersionName = "";
  private static volatile String resolvedVersionName = "";
  private static volatile String compatibilityState = "unknown";
  private static volatile String compatibilityDetail = "";
  private static final Pattern SEMANTIC_VERSION = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)");

  public static String getDetectedVersionName() {
    return detectedVersionName;
  }

  public static String getResolvedVersionName() {
    return resolvedVersionName;
  }

  public static String getCompatibilityState() {
    return compatibilityState;
  }

  public static String getCompatibilityDetail() {
    return compatibilityDetail;
  }

  public static class Config {
    public String linePkg = "jp.naver.line.android";

    public Main main = new Main();
    public Settings settings = new Settings();
    public PlusMenu plusMenu = new PlusMenu();
    public ChatListMoreMenu chatListMoreMenu = new ChatListMoreMenu();
    public Unsend unsend = new Unsend();
    public Thrift thrift = new Thrift();
    public Tabs tabs = new Tabs();
    public Ads ads = new Ads();
    public Home home = new Home();
    public Chat chat = new Chat();
    public Res res = new Res();
    public ReadReceipt readReceipt = new ReadReceipt();
    public ChatHeader chatHeader = new ChatHeader();
    public Font font = new Font();
    public Notification notification = new Notification();
    public NotificationFix notificationFix = new NotificationFix();
    public ForegroundKeepAlive foregroundKeepAlive = new ForegroundKeepAlive();
    public TalkTabHeader talkTabHeader = new TalkTabHeader();
    public SearchBarAgentI searchBarAgentI = new SearchBarAgentI();
    public AgentIInChat agentIInChat = new AgentIInChat();
    public AiIcon aiIcon = new AiIcon();
    public ImageQuality imageQuality = new ImageQuality();
    public Media media = new Media();
    public Profile profile = new Profile();
    public ProfileTimestamps profileTimestamps = new ProfileTimestamps();
    public AnnouncementFix announcementFix = new AnnouncementFix();
    public ChatJump chatJump = new ChatJump();
    public ChatTimestamp chatTimestamp = new ChatTimestamp();
    public ChatEditSelectAll chatEditSelectAll = new ChatEditSelectAll();
    public MessageEditHistory messageEditHistory = new MessageEditHistory();
    public Camera camera = new Camera();
    public Iab iab = new Iab();
    public HomeTab homeTab = new HomeTab();
    public NightMode nightMode = new NightMode();
    public Compose compose = new Compose();
    public Home26NavIcon home26NavIcon = new Home26NavIcon();

    public static class Compose {
      public String composerClass = "";
      public String clickableClass = "";
      public String methodClickable = "";
      public String methodCombinedClickable = "";
      public String onGloballyPositionedClass = "";
      public String methodOnGloballyPositioned = "";
      public String layoutCoordinatesClass = "";
      public String methodLocalToWindow = "";
      public String methodCoordinatesSize = "";
    }

    public static class Home26NavIcon {
      public String rendererClass = "";
      public String rendererMethod = "";
      public int agentDrawableId = 0;
      public int settingsDrawableId = 0;
    }

    public static class NightMode {
      public String nightModeConfiguratorClass = "";
      public String methodApplyNightMode = "";
      public String fieldSystemDarkMode = "";
      public String inputPassActivityClass = "";
      public String darkThemeManagerClass = "";
      public String methodIsDarkTheme = "";
      public String methodThemeMode = "";
    }

    public static class HomeTab {
      public String tabListProviderClass = "";
      public String methodBuildTabList = "";
      public String mainTabEnumClass = "";
    }

    public static class Camera {
      public String cameraModuleClass = "";
      public String methodUseExternalCamera = "";
    }

    public static class ChatTimestamp {
      public String displayTimeInterface = "";
      public String methodCreatedMillis = "";
    }

    public static class ChatEditSelectAll {
      public String selectionProviderClass = "";
      public String selectionStateClass = "";
      public String methodGetSelectionState = "";
      public String methodGetItem = "";
      public String methodGetCount = "getCount";
      public String methodGetSelectedIds = "";
      public String methodToggleItem = "";
      public String methodIsItemSelected = "";
    }

    public static class MessageEditHistory {
      public String editRequestClass = "";
      public String editRequestIdField = "";
      public String editRequestTextField = "";
      public String menuListBuilderClass = "";
      public String menuListMethod = "";
      public String menuItemEnumClass = "";
      public String menuPresentationEnumClass = "";
      public String methodMenuLabel = "";
      public String methodMenuIcon = "";
      public String methodMenuActionAccessor = "";
      public String menuActionLambdaClass = "";
      public String menuContextMessageField = "";
      public String menuMessageDataField = "";
      public String menuMessageIdField = "";
      public String menuEditedFlagField = "";
    }

    public static class Iab {
      public String inAppBrowserActivityClass = "";
    }

    public static class AnnouncementFix {
      public String formatterClass = "";
      public String formatMethod = "";
      public String nameResolverMethod = "";
      public String announcementEventClass = "";
    }

    public static class ChatJump {
      public String requestClass = "";
      public String launchActivityClass = "";
      public String requestExtraKey = "";
    }

    public static class Media {
      public String videoDurationCheckClass = "";
      public String videoDurationCheckMethod = "";
      public String mediaPickerParamsClass = "";
      public String fieldMediaPickerMaxVideoDuration = "";
      public String droppedMediaPreprocessorClass = "";
      public String videoDurationSuccessClass = "";
      public String fieldVideoDurationSuccess = "";
      public String galleryViewClass = "";
      public String fieldGalleryDurationLimit = "";
      public String selectionValidatorClass = "";
      public String selectionValidatorMethod = "";
      public String selectionValidatorParamClass = "";
      public String videoProfileTrimmerActivityClass = "";
      public String fieldVideoProfileTrimmerLimit = "";
    }

    public static class Profile {
      public String g50fClass = "";
      public String h13baClass = "";
      public String fieldH3 = "";
      public String g50aClass = "";
      public String methodGetProfile = "getProfile";
      public String fieldMid = "";
    }

    public static class ProfileTimestamps {
      public String activityClass = "";
      public String midExtraKey = "";
      public String resHeaderButtonContainer = "";
      public String optionalButtonWidthDimen = "userprofile_optional_button_width";
      public String optionalButtonSpacingDimen = "userprofile_optional_button_spacing";
    }

    public static class ImageQuality {
      public String qualityProfileHighClass = "";
      public String qualityProfileMediumClass = "";
      public String methodGetMaxDimension = "";
      public String methodGetQuality = "";
      public String imageUtilClass = "";
    }

    public static class Main {
      public String mainActivity = "";
      public String baseMainTabFragment = "";
      public String headerButton = "";
      public String headerButtonTypeClass = "";
      public String slotFarLeft = "";
      public String headerInterfaceA = "";
      public String fieldHeaderHelper = "";
      public String fieldChatActivity = "";
      public String methodSetHeaderButton = "";
      public String methodSetHeaderLabel = "";
      public String methodSetHeaderButtonVisibility = "";
      public String methodGetHeaderButtonView = "";
      public String methodSetHeaderOnClickListener = "";
      public String methodRefreshNavHeader = "";
      public String methodHeaderSetTitle = "";
      public String methodHeaderSetButtonVisibility = "";
      public String methodHeaderSetButtonListener = "";
    }

    public static class Settings {
      public String mainSettingsFragmentClass = "";
      public String settingsAdapterClass = "";
      public String settingsItemClass = "";
      public String settingsBaseAdapterClass = "";
      public String settingsSearchHelperClass = "";
      public String settingsAdapterWrapperClass = "";
      public String methodSetItems = "";
      public String methodBindViewHolder = "";
      public String methodGetItem = "";
      public String fieldItemModel = "";
      public String fieldModelTag = "";
      public String fieldViewHolderView = "";
      public String settingsHeaderItemClass = "";
      public String settingsRowItemClass = "";
      public String settingsHandlerBaseClass = "";
      public String fieldIsVisible = "";
      public String fieldLayoutId = "";
      public String fieldActionHandler = "";
      public String fieldIconProvider = "";
      public String fieldDescriptionProvider = "";
      public String fieldSubActionHandler = "";
      public String fieldVisibilityFilter = "";
      public String fieldDefaultHandler = "";
      public String fieldCommonHandler = "";
      public String methodSetDescription = "";
      public String methodProxyGetItemType = "";
      public String methodSetTitleText = "";
      public String methodSetChecked = "";
      public String methodSetItemType = "";
      public String methodSetSyncStatus = "";
      public String methodSetDividerVisible = "";

      public String textItemViewClass =
          "com.linecorp.line.settings.base.itemview.LineUserSettingTextItemView";
      public String methodRowSetTitleText = "setTitleText";
      public String methodRowSetDescription = "setDescriptionText";
      public String methodRowSetArrowVisible = "setArrowVisible";
      public String methodRowSetDividerVisible = "setDividerVisible";
      public String methodRowSetTitleColor = "setTitleTextColor";

      public String themeSettingItemId = "line-main-settings.themes";
    }

    public static class PlusMenu {
      public String plusMenuComponentClass = "";
      public String plusMenuComposerImplClass = "";
      public String plusMenuCallbackClass = "";
      public String plusMenuOnClickItemClass = "";

      public String methodAddMenuItem = "";
      public String methodCreateMenu = "";
      public String methodExecuteAction = "";

      public String editChatDrawable = "";
      public String moduleId = "dev.vector.lineextension";
      public String targetPkg = "jp.naver.line.android";
    }

    public static class ChatListMoreMenu {
      public String popupListViewClass = "";
      public String fieldListView = "";
      public String popupListAdapterClass = "";
      public String fieldPopupItems = "";
      public String clickListenerClass = "";
      public String methodAddItem = "";
    }

    public static class ReadReceipt {
      public String readReceiptManagerClass = "";
      public String methodSendReadReceipt = "";
      public String methodExecuteReadReceiptAsync = "";
      public String methodReadAll = "";
      public String methodResolveReadTarget = "";
      public String operationNotifiedReadName = "NOTIFIED_READ_MESSAGE";
      public String longPressReadClass = "";
    }

    public static class Unsend {
      public String notifiedReadMessageHandlerClass = "";
      public String notifiedSendReactionHandlerClass = "";
      public String notifiedDestroyMessageHandlerClass = "";
      public String chatMessageViewHolderClass = "";

      public String methodReadBuffer = "";
      public String methodBind = "";
      public String methodOperationTypeValueOf = "";
      public int methodBindIndex = 0;
      public String methodGetItemView = "";
      public String methodGetCommonData = "";
      public int operationTypeDummy = 0;

      public String chatServiceConfigClass = "";
      public String methodUnsendLimit = "";
      public String methodUnsendPremiumLimit = "";
      public String appInfoProviderClass = "";
      public String methodGetFullUserAgent = "";
      public String methodGetSimpleUserAgent = "";
      public String methodGetFullUserAgentWithContext = "";
      public String methodGetSimpleUserAgentWithContext = "";
      public String methodUnsendThrift = "";
      public String methodUnsendThriftSilent = "";
      public String methodUnsendAnnouncement = "";
      public String operationTypeField = "";
      public String operationParam1Field = "";
      public String operationParam2Field = "";
      public String operationParam3Field = "";
      public String operationCreatedTimeField = "";
      public String chatMessageIdField = "";

      public String operationUnsendName = "DESTROY_MESSAGE";
      public String operationNotifiedUnsendName = "NOTIFIED_DESTROY_MESSAGE";
      public String unsendDestroyHandlerClass = "";
      public String operationClass = "";
    }

    public static class Thrift {
      public String talkServiceClientImplClass = "";
      public String talkServiceClientInterface = "";
      public String v1 = "";
      public String protocolClass = "";
      public String messageClass = "";
      public String methodWriteMessageBegin = "";
      public String methodReadMessageBegin = "";

      public String methodDestroyMessage = "destroyMessage";
      public String methodDestroyMessages = "destroyMessages";
    }

    public static class Tabs {
      public String bottomNavigationBarTextViewClass = "";
      public String resVoom = "bnb_timeline";
      public String resNews = "bnb_news";
      public String resMini = "bnb_mini";
      public String resContainer = "main_tab_container";
      public String resBtnText = "bnb_button_text";
      public String resCall = "bnb_call";
      public String resCommerce = "bnb_commerce";
      public String resCommerceTw = "bnb_commerce_tw";
      public String resWallet = "bnb_wallet";
    }

    public static class Ads {
      public String ladAdView = "";
      public String ladAdViewV2 = "";
      public String smartChannel = "";
      public String classAdSdkBase = "";
      public String classAdMolinBase = "";
    }

    public static class Home {
      public String resRecommendation = "";
      public String resServiceCarouselId = "";
      public String resServiceTitleId = "";
      public String resNoServicesId = "";
      public String lypRecommendationModuleArgClass = "";
      public String lypRecommendationContextClass = "";
      public String lypRecommendationModuleClass = "";
      public String lypRecommendationControllerClass = "";
      public String lypRecommendationSectionClass = "";

      public String home26FeedTypePrefixes = "";
      public String home26ServiceTypePrefixes = "";
      public String home26LoadingMoreDataClass = "";
      public String home26ModuleBodyField = "";
    }

    public static class Chat {
      public String headerController = "";
      public String headerHelper = "";
      public String chatIdField = "";
      public String methodGetChatId = "";
      public String searchHeaderHelperClass = "";
      public String searchHeaderControllerField = "";
      public String searchHeaderEventBusField = "";
      public String searchControllerSearchBoxMethod = "";
      public String searchPresenterClass = "";
      public String searchKeywordTypeClass = "";
      public String searchKeywordTypeMethod = "";
      public String searchResultClass = "";
      public String searchResultCtorArgs = "";
      public String searchResultWrapperClass = "";
      public String searchBoxViewClass = "";
      public String searchBoxEditTextField = "";
      public String searchKeywordEventClass = "";
      public String searchKeywordEventKeywordField = "";
      public String searchPresenterKeywordChangedMethod = "";
      public String searchPresenterKeywordSubjectField = "";
      public String searchKeywordSubjectValueMethod = "";
      public String searchResultWrapperResultOptionalField = "";
      public String searchResultCountField = "";
      public String searchResultTitleViewHolderClass = "";
      public String searchResultTitleBindMethod = "";
      public String searchResultTitleBindingField = "";
      public String searchResultTitleTextViewField = "";
      public String searchFtsInChatQueryClass = "";
      public String searchFtsQueryField = "";
      public String searchFtsChatIdField = "";
      public String searchFtsLimitField = "";
    }

    public static class ChatHeader {
      public String chatHistoryActivity = "";
      public String fieldChatConfigChatId = "";
      public String fieldChatConfigIsMuted = "";
      public String fieldChatConfigType = "";
      public String fieldAppInfoVersion = "";
      public String fieldAppInfoPkg = "";
      public String fieldAppInfoId = "";
    }

    public static class Font {
      public String fontConfigClass = "";
      public String fontManagerClass = "";
      public String fontCallbackClass = "";
      public String fontInjectedClass = "";
      public String methodGetFontConfig = "a";
      public String methodGetFontSettings = "e";
      public String methodOnFontChanged = "b";
      public String fontRequestExecutorClass = "";
      public String fontCallbackWithHandlerClass = "";
    }

    public static class Res {
      public int idSettingList = 0;
      public int idPersonalInfo = 0;
      public int typeSection = 0;
      public int typeRow = 0;
      public int idIcon = 0;
      public int idDesc = 0;
      public int idMark = 0;
      public int idSeparator = 0;
      public int idArrow = 0;
      public int idNewMark = 0;
      public int idNoticeDot = 0;
      public int idTitle = 0;
      public int layoutCheckbox = 0;
      public int layoutSectionHeader = 0;
      public int layoutSettingsMain = 0;
      public int idHeader = 0;
      public int idStatusBarGuide = 0;
      public int idTimestamp = 0;
      public String resSettingsHeaderBtn = "";
      public String resSettingsBtn = "";
      public String resTooltipBackground = "";
      public String resTooltipArrowUp = "";
    }

    public static class Notification {
      public String chatHistoryRequestClass = "";
      public String chatHistoryActivityLaunchActivityClass = "";
      public String messageIdExtra = "line.message.id";
      public String messageNotificationTag = "NOTIFICATION_TAG_MESSAGE";
      public String chatNotificationTag = "jp.naver.line.android.notification.tag.chat";
    }

    public static class NotificationFix {
      public String lineFcmServiceClass = "";
      public String lineFcmDispatchMethod = "";
      public String lineFcmOwnershipMethod = "";
      public String lineFcmTokenMethod = "";
      public String lineFcmServiceBaseClass = "";
      public String firebaseRemoteMessageClass = "";
      public String firebaseReceiverClass = "";
      public String firebaseReceiverMethod = "";
      public String firebaseReceiverEnvelopeClass = "";
      public String firebaseReceiverIntentField = "";
      public String firebaseDispatcherClass = "";
      public String firebaseDispatcherSingletonField = "";
      public String firebaseDispatcherMethod = "";
      public String firebaseDispatcherContextField = "";
      public String firebaseDispatcherQueueField = "";
      public String firebaseBindDeliveryClass = "";
      public String firebaseBindDeliveryMethod = "";
      public String firebaseMessagingServiceClass = "";
      public String firebaseMessagingHandleMethod = "";
      public String firebaseWakefulStartClass = "";
      public String firebaseWakefulStartMethod = "";
      public String firebaseCompletedTaskClass = "";
      public String firebaseCompletedTaskMethod = "";
      public String firebaseMessagingClass = "";
      public String firebaseMessagingGetTokenMethod = "";
      public String firebaseMessagingTokenFreshMethod = "";
      public String firebaseAppClass = "";
      public String firebaseAppGetInstanceMethod = "";
      public String legyStreamingStateClass = "";
      public String legyStreamingLifecycleClass = "";
      public String legyStreamingLifecycleMethod = "";
      public String legyLifecycleOwnerClass = "";
      public String legyLifecycleEventClass = "";
      public String legyBackgroundStateField = "";
      public String legyDisconnectRunnableClass = "";
      public String legyStateField = "";
      public String legyTimeoutField = "";
      public String legyBackgroundWorkerFlagField = "";
      public String legyHandlerField = "";
      public String legyRunnableField = "";
      public String fisCertDigestClass = "";
      public String fisCertDigestMethod = "";
      public String fisCertSha1 = "";
      public String gmsSignatureCheckClass = "";
      public String gmsSignatureCheckMethod = "";
      public String gmsAvailabilityClass = "";
      public String gmsAvailabilityMethod = "";
    }

    public static class ForegroundKeepAlive {
      public String serviceClass = "";
    }

    public static class TalkTabHeader {
      public String chatTabHeaderStateClass = "";
      public String iconListStateField = "";
      public String buttonListStateField = "";
      public String iconTypeClass = "";
      public String iconTypeFieldInButton = "";
      public String subDeviceOpenChatButtonClass = "";
      public String subDeviceAlbumButtonClass = "";
    }

    public static class SearchBarAgentI {
      public String talkVisibleMethod = "";
      public String talkClickMethod = "";
      public String homeSearchBarClass = "";
      public String homeRefreshMethod = "";
      public String homeRootViewField = "";
      public String homeTabTypeField = "";
      public String homeTabName = "";
      public String homeTabV2Name = "";
      public String chatTabName = "";
      public String newsTabName = "";
      public int homeAiContainerId = 0;
      public int homeGuidelineId = 0;
      public int homeGuidelineEndDp = 0;
      public String homeGuidelineClass = "";

      public String miniTabHeaderClass = "";
      public String miniTabAgentMethod = "";

      public String commerceHeaderClass = "";
      public String commerceHeaderMethod = "";
    }

    public static class AgentIInChat {
      public String toggleComposableClass = "";
    }

    public static class AiIcon {
      public String repoClass = "";
      public String methodGetShownAfterMillis = "";
    }
  }

  private static final Map<String, Config> VERSION_TABLE = new HashMap<>();

  static {
    VERSION_TABLE.put("26.10.0", dev.vector.lineextension.versions.Version26100.create());
    VERSION_TABLE.put("26.10.1", dev.vector.lineextension.versions.Version26101.create());
    VERSION_TABLE.put("26.11.0", dev.vector.lineextension.versions.Version26110.create());
    VERSION_TABLE.put("26.13.0", dev.vector.lineextension.versions.Version26130.create());
    VERSION_TABLE.put("26.13.1", dev.vector.lineextension.versions.Version26131.create());
  }

  private static volatile Config cachedConfig = null;

  public static Config get() {
    return cachedConfig;
  }

  public static Config get(ClassLoader cl) {
    if (cachedConfig != null) return cachedConfig;
    return detect(cl);
  }

  public static String getVersionName(android.content.Context context) {
    if (context == null) return null;
    try {
      return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
    } catch (Throwable t) {
      Vector.log("Tencha: getVersionName failed: " + t.getMessage());
      return null;
    }
  }

  public static Config detect(ClassLoader cl) {
    if (cachedConfig != null) return cachedConfig;
    return resolveVersion(getVersionName(Vector.currentApplication()), classProbe(cl));
  }

  public static Config detectWithContext(android.content.Context context) {
    if (cachedConfig != null) return cachedConfig;
    return resolveVersion(getVersionName(context), null);
  }

  public static Config detectWithContext(android.content.Context context, ClassLoader cl) {
    if (cachedConfig != null) return cachedConfig;
    return resolveVersion(getVersionName(context), classProbe(cl));
  }

  interface ClassProbe {
    boolean exists(String className);
  }

  static Config resolveVersion(String verName, ClassProbe probe) {
    String rawVersion = verName == null ? "" : verName;
    detectedVersionName = rawVersion;
    safeLog("Tencha: Detected LINE version: " + displayVersion(rawVersion));

    String normalized = normalizeVersion(rawVersion);
    Config exact = VERSION_TABLE.get(normalized);
    if (exact != null) {
      cachedConfig = exact;
      resolvedVersionName = normalized;
      compatibilityState = "exact";
      compatibilityDetail = "正式対応";
      return exact;
    }

    if (probe != null) {
      List<String> candidates = new ArrayList<>(VERSION_TABLE.keySet());
      candidates.sort((left, right) -> Integer.compare(versionScore(right), versionScore(left)));
      for (String candidateVersion : candidates) {
        Config candidate = VERSION_TABLE.get(candidateVersion);
        int score = compatibilityScore(candidate, probe);
        if (score >= 5) {
          cachedConfig = candidate;
          resolvedVersionName = candidateVersion;
          compatibilityState = "automatic";
          compatibilityDetail =
              "LINE "
                  + displayVersion(verName)
                  + " に "
                  + candidateVersion
                  + " の構造を検証して自動適用 ("
                  + score
                  + "/8)";
          safeLog("Tencha: " + compatibilityDetail);
          return candidate;
        }
      }
    }

    resolvedVersionName = "";
    compatibilityState = "unsupported";
    compatibilityDetail = "互換構造を確認できないため全機能を停止";
    safeLog("Tencha: Unsupported LINE version: " + displayVersion(rawVersion));
    return null;
  }

  static String normalizeVersion(String rawVersion) {
    if (rawVersion == null) return "";
    Matcher matcher = SEMANTIC_VERSION.matcher(rawVersion);
    return matcher.find() ? matcher.group(1) + "." + matcher.group(2) + "." + matcher.group(3) : "";
  }

  private static String displayVersion(String rawVersion) {
    String normalized = normalizeVersion(rawVersion);
    if (!normalized.isEmpty()) return normalized;
    return rawVersion == null || rawVersion.isBlank() ? "不明" : rawVersion;
  }

  private static ClassProbe classProbe(ClassLoader cl) {
    if (cl == null) return null;
    return className -> {
      if (className == null || className.isEmpty()) return false;
      try {
        Class.forName(className, false, cl);
        return true;
      } catch (Throwable ignored) {
        return false;
      }
    };
  }

  private static int compatibilityScore(Config config, ClassProbe probe) {
    if (!probe.exists(config.main.mainActivity)) return 0;
    int score = 1;
    String[] anchors = {
      config.settings.mainSettingsFragmentClass,
      config.settings.settingsAdapterClass,
      config.plusMenu.plusMenuComponentClass,
      config.readReceipt.readReceiptManagerClass,
      config.unsend.notifiedReadMessageHandlerClass,
      config.compose.composerClass,
      config.iab.inAppBrowserActivityClass
    };
    for (String anchor : anchors) {
      if (probe.exists(anchor)) score++;
    }
    return score;
  }

  static void resetResolutionForTests() {
    cachedConfig = null;
    detectedVersionName = "";
    resolvedVersionName = "";
    compatibilityState = "unknown";
    compatibilityDetail = "";
  }

  private static void safeLog(String message) {
    try {
      Vector.log(message);
    } catch (Throwable ignored) {
      // Version resolution must never depend on the active logging backend.
    }
  }

  public static String getSupportedVersions() {
    List<String> keys = new ArrayList<>(VERSION_TABLE.keySet());
    keys.sort(Comparator.comparingInt(LineVersion::versionScore));
    return String.join(", ", keys);
  }

  private static int versionScore(String v) {
    int score = 0;
    for (String part : v.split("\\.")) score = score * 1000 + Integer.parseInt(part);
    return score;
  }
}
