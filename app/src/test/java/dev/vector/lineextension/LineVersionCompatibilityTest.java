package dev.vector.lineextension;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class LineVersionCompatibilityTest {
  @Before
  public void setUp() {
    LineVersion.resetResolutionForTests();
  }

  @After
  public void tearDown() {
    LineVersion.resetResolutionForTests();
  }

  @Test
  public void versionIsNormalizedBeforeExactLookup() {
    assertEquals("26.13.0", LineVersion.normalizeVersion("LINE/26.13.0 (261300096)"));
    assertNotNull(LineVersion.resolveVersion("LINE/26.13.0 (261300096)", name -> false));
    assertEquals("exact", LineVersion.getCompatibilityState());
    assertEquals("26.13.0", LineVersion.getResolvedVersionName());
  }

  @Test
  public void latestVerifiedVersionUsesLiveSettingsTemplateMapping() {
    LineVersion.Config config = LineVersion.resolveVersion("26.14.0", name -> false);
    assertNotNull(config);
    assertEquals("exact", LineVersion.getCompatibilityState());
    assertEquals("y78.f", config.settings.settingsAdapterClass);
    assertEquals("ka5.b", config.settings.settingsSearchHelperClass);
    assertEquals("m55.s", config.settings.settingsHeaderItemClass);
    assertEquals("m55.v", config.settings.settingsRowItemClass);
    assertEquals("l55.a", config.settings.settingsAdapterWrapperClass);
    assertEquals(0x7f0b2275, config.res.idIcon);
    assertEquals(0x7f0b2267, config.res.idDesc);
    assertEquals(0x7f0b22b0, config.res.idSeparator);
    assertEquals(0x7f0b22b8, config.res.idTitle);
    assertEquals("n0", config.chat.searchControllerSearchBoxMethod);
    assertEquals("f7.l", config.font.fontConfigClass);
    assertEquals("f7.k", config.font.fontManagerClass);
    assertEquals("f7.l$c", config.font.fontCallbackClass);
    assertEquals("f7.n", config.font.fontRequestExecutorClass);
    assertEquals(
        "com.linecorp.line.userprofile.impl.UserProfileActivity",
        config.profileViewer.profileActivityClass);
    assertEquals("qm6.i", config.profileViewer.decoControllerClass);
    assertEquals("qm6.l0", config.profileViewer.decoEditorControllerClass);
    assertEquals("z7", config.profileViewer.methodSaveDecorations);
    assertEquals("", config.home.lypRecommendationControllerClass);
    assertEquals("", config.home.home26LoadingMoreDataClass);
    assertEquals("tn1.a", config.announcementFix.formatterClass);
    assertEquals("a", config.announcementFix.formatMethod);
    assertEquals("b", config.announcementFix.nameResolverMethod);
    assertEquals("b41.g$d0", config.announcementFix.announcementEventClass);
  }

  @Test
  public void line26150UsesVerifiedSettingsAndMessageMappings() {
    LineVersion.Config config = LineVersion.resolveVersion("26.15.0", name -> false);
    assertNotNull(config);
    assertEquals("exact", LineVersion.getCompatibilityState());
    assertEquals("xe8.f", config.settings.settingsAdapterClass);
    assertEquals("xg5.b", config.settings.settingsSearchHelperClass);
    assertEquals("zb5.q", config.settings.settingsHeaderItemClass);
    assertEquals("zb5.u", config.settings.settingsRowItemClass);
    assertEquals("yb5.a", config.settings.settingsAdapterWrapperClass);
    assertEquals(0x7f0b229e, config.res.idSettingList);
    assertEquals("in8.c3", config.callTts.receiveProcessorClass);
    assertEquals("in8.d4", config.callTts.sendProcessorClass);
    assertEquals("gp8.od", config.callTts.messageClass);
    assertEquals("wq1.p", config.unsend.chatMessageViewHolderClass);
    assertEquals("I0", config.unsend.methodBind);
    assertEquals(3, config.unsend.methodBindIndex);
  }

  @Test
  public void line26150DoesNotInheritRetiredProfileSelectionCameraOrComposeMappings() {
    LineVersion.Config c = LineVersion.resolveVersion("26.15.0", name -> false);
    assertEquals("sc0.f", c.profile.g50fClass);
    assertEquals("zo3.b", c.profile.h13baClass);
    assertEquals("sd", c.profile.fieldH3);
    assertEquals("sc0.a", c.profile.g50aClass);
    assertEquals("b", c.profile.fieldMid);
    assertEquals("h", c.profile.fieldName);
    assertEquals("le1.c", c.chatEditSelectAll.selectionProviderClass);
    assertEquals("le1.d", c.chatEditSelectAll.selectionStateClass);
    assertEquals("e0", c.chatEditSelectAll.methodGetSelectionState);
    assertEquals("h0", c.chatEditSelectAll.methodGetItem);
    assertEquals("d", c.chatEditSelectAll.methodGetSelectedIds);
    assertEquals("g", c.chatEditSelectAll.methodToggleItem);
    assertEquals("ie2.g", c.camera.cameraModuleClass);
    assertEquals("d", c.camera.methodUseExternalCamera);
    assertEquals("dg1.b", c.media.videoDurationCheckClass);
    assertEquals("eg1.a$c", c.media.videoDurationSuccessClass);
    assertEquals("sp1.u", c.media.galleryViewClass);
    assertEquals("u1.h0", c.compose.clickableClass);
    assertEquals("z4.y1", c.compose.onGloballyPositionedClass);
    assertEquals("z4.b0", c.compose.layoutCoordinatesClass);
    assertEquals("k", c.compose.methodLocalToWindow);
    assertEquals("lm2.n", c.home26NavIcon.rendererClass);
    assertEquals(0x7f081298, c.home26NavIcon.settingsDrawableId);
    assertEquals(0x7f080b9f, c.home26NavIcon.agentDrawableId);
    assertEquals("y85.i", c.searchBarAgentI.homeSearchBarClass);
    assertEquals(0x7f0b164c, c.searchBarAgentI.homeAiContainerId);
    assertEquals(0x7f0b164e, c.searchBarAgentI.homeGuidelineId);
    assertEquals("com.linecorp.line.commerce.impl.c", c.searchBarAgentI.commerceHeaderClass);
    assertEquals("d", c.searchBarAgentI.commerceHeaderMethod);
    assertEquals("ho1.k", c.agentIInChat.toggleComposableClass);
    assertEquals("c71.t", c.plusMenu.plusMenuComponentClass);
    assertEquals("j3.r", c.compose.composerClass);
    assertEquals("j3.b1", c.plusMenu.plusMenuComposerImplClass);
    assertEquals("aq8.a", c.plusMenu.plusMenuCallbackClass);
    assertEquals("aq8.l", c.plusMenu.plusMenuOnClickItemClass);
  }

  @Test
  public void line26150ChatIdUsesCurrentRequestViewModelGetter() {
    LineVersion.Config c = LineVersion.resolveVersion("26.15.0", name -> false);
    assertEquals("j", c.chat.chatIdField);
    assertEquals("s", c.chat.methodGetChatId);
    // Do not change older versions' own verified getter when fixing 26.15.
    LineVersion.Config old = LineVersion.resolveVersion("26.13.0", name -> false);
    assertEquals("t", old.chat.methodGetChatId);
  }

  @Test
  public void line26150ReadHistoryUsesReadNotificationNotPremiumBackup() {
    LineVersion.Config c = LineVersion.resolveVersion("26.15.0", name -> false);
    assertEquals("in8.a2", c.unsend.notifiedReadMessageHandlerClass);
    assertEquals("b", c.unsend.methodReadBuffer);
    assertEquals("gp8.de", c.unsend.operationClass);
    assertEquals("NOTIFIED_READ_MESSAGE", c.readReceipt.operationNotifiedReadName);
    assertEquals("c", c.unsend.operationTypeField);
    assertEquals("g", c.unsend.operationParam1Field);
    assertEquals("h", c.unsend.operationParam2Field);
    assertEquals("i", c.unsend.operationParam3Field);
    assertEquals("b", c.unsend.operationCreatedTimeField);
  }

  @Test
  public void unknownVersionFailsClosedEvenWhenOldAnchorsExist() {
    assertNull(LineVersion.resolveVersion("26.16.0", name -> true));
    assertEquals("unsupported", LineVersion.getCompatibilityState());
    assertEquals("", LineVersion.getResolvedVersionName());
  }

  @Test
  public void missingVersionNameFailsClosed() {
    assertNull(LineVersion.resolveVersion(null, name -> true));
    assertEquals("unsupported", LineVersion.getCompatibilityState());
    assertEquals("", LineVersion.getResolvedVersionName());
  }

  @Test
  public void unknownVersionFailsClosedWithoutEnoughAnchors() {
    assertNull(
        LineVersion.resolveVersion(
            "26.16.0", name -> "jp.naver.line.android.activity.main.MainActivity".equals(name)));
    assertEquals("unsupported", LineVersion.getCompatibilityState());
    assertEquals("", LineVersion.getResolvedVersionName());
  }
}
