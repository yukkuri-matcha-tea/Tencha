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
  public void line26151UsesVerifiedComposeAndGalleryMappings() {
    LineVersion.Config c = LineVersion.resolveVersion("26.15.1", name -> false);
    assertNotNull(c);
    assertEquals("exact", LineVersion.getCompatibilityState());
    assertEquals("26.15.1", LineVersion.getResolvedVersionName());
    assertEquals("j3.q", c.compose.composerClass);
    assertEquals("j3.c1", c.plusMenu.plusMenuComposerImplClass);
    assertEquals("z4.w1", c.compose.onGloballyPositionedClass);
    assertEquals("z4.a0", c.compose.layoutCoordinatesClass);
    assertEquals("sp1.x", c.media.galleryViewClass);
    assertEquals("rt6.g", c.profileViewer.decoControllerClass);
    assertEquals("rt6.d0", c.profileViewer.decoEditorControllerClass);
    assertEquals("v", c.profileViewer.methodGetDecorationView);
    assertEquals("du6.y$e", c.profileViewer.mediaValueClass);
    assertEquals("D", c.profileViewer.editorSelectionFrameField);
    assertEquals("R", c.profileViewer.methodLoadImageIntoView);
    assertEquals("s", c.chat.methodGetChatId);
    assertEquals("xe8.f", c.settings.settingsAdapterClass);
    assertEquals("in8.a2", c.unsend.notifiedReadMessageHandlerClass);
    LineVersion.Config old = LineVersion.resolveVersion("26.15.0", name -> false);
    assertEquals("j3.r", old.compose.composerClass);
    assertEquals("sp1.u", old.media.galleryViewClass);
    assertEquals("F", old.profileViewer.editorSelectionFrameField);
    assertEquals("S", old.profileViewer.methodLoadImageIntoView);
  }

  @Test
  public void line26151RepairsHeaderSearchAndNotificationMappings() {
    LineVersion.Config c = LineVersion.resolveVersion("26.15.1", name -> false);
    assertEquals("il1.z0", c.chat.headerController);
    assertEquals("il1.m0", c.chatHeader.fieldChatConfigType);
    assertEquals("ry1.v", c.chat.searchHeaderHelperClass);
    assertEquals("n", c.chat.searchHeaderControllerField);
    assertEquals("d", c.chat.searchControllerSearchBoxMethod);
    assertEquals("A", c.chat.searchPresenterKeywordSubjectField);
    assertEquals("c", c.chat.searchBoxEditTextField);
    assertEquals("e", c.chat.searchBoxIconField);
    assertEquals("ta5.u", c.unsend.chatServiceConfigClass);
    assertEquals("ux.e", c.notificationFix.firebaseAppClass);
    assertEquals("iz.i", c.notificationFix.lineFcmServiceBaseClass);
    assertEquals("iz.s0", c.notificationFix.firebaseRemoteMessageClass);
    assertEquals("dr.a", c.notificationFix.firebaseReceiverEnvelopeClass);
    assertEquals("iz.n", c.notificationFix.firebaseDispatcherClass);
    assertEquals("iz.x0", c.notificationFix.firebaseQueueClass);
    assertEquals("a", c.notificationFix.firebaseQueueGetInstanceMethod);
    assertEquals("iz.n1", c.notificationFix.firebaseBindDeliveryClass);
    assertEquals("iz.i1", c.notificationFix.firebaseWakefulStartClass);
    assertEquals("qt.n", c.notificationFix.firebaseCompletedTaskClass);
    assertEquals("er.j", c.notificationFix.gmsAvailabilityClass);
    assertEquals("e", c.notificationFix.gmsAvailabilityMethod);
    assertEquals("er.k", c.notificationFix.gmsSignatureCheckClass);
    assertEquals("pr.a", c.notificationFix.fisCertDigestClass);
    assertEquals("ta0.j", c.notificationFix.legyDisconnectRunnableClass);
    LineVersion.Config old = LineVersion.resolveVersion("26.13.0", name -> false);
    assertEquals("ns.e", old.notificationFix.firebaseAppClass);
    assertEquals("", old.notificationFix.firebaseQueueClass);
  }

  @Test
  public void callRestrictionBypassUsesOnlyVerified26151EntryPoints() {
    LineVersion.Config c = LineVersion.resolveVersion("26.15.1", name -> false);
    assertEquals("en1.q2", c.callRestrictions.voicePresenterClass);
    assertEquals("jo1.p", c.callRestrictions.videoLauncherClass);
    assertEquals("u81.a", c.callRestrictions.videoRequestClass);
    assertEquals("ry0.h0", c.callRestrictions.cameraGateClass);
    assertEquals("ti1.c", c.callRestrictions.chatCallStateClass);
    assertEquals("ie2.g", c.camera.cameraModuleClass);
    LineVersion.Config old = LineVersion.resolveVersion("26.15.0", name -> false);
    assertEquals("", old.callRestrictions.voicePresenterClass);
  }

  @Test
  public void unknownVersionFailsClosedEvenWhenOldAnchorsExist() {
    assertNull(LineVersion.resolveVersion("26.17.0", name -> true));
    assertEquals("unsupported", LineVersion.getCompatibilityState());
    assertEquals("", LineVersion.getResolvedVersionName());
  }

  @Test
  public void line26160HasItsOwnVerifiedMappingsAndRemoteProfileWriteBarrier() {
    LineVersion.Config c = LineVersion.resolveVersion("26.16.0", name -> false);
    assertNotNull(c);
    assertEquals("exact", LineVersion.getCompatibilityState());
    assertEquals("26.16.0", LineVersion.getResolvedVersionName());
    assertEquals("ji8.f", c.settings.settingsAdapterClass);
    assertEquals("wk1.t0", c.chat.headerController);
    assertEquals("hy1.v", c.chat.searchHeaderHelperClass);
    assertEquals("qg3.e", c.readReceipt.readReceiptManagerClass);
    assertEquals("uq8.k2", c.unsend.notifiedSendReactionHandlerClass);
    assertEquals("wp8.d", c.unsend.appInfoProviderClass);
    assertEquals("B7", c.profileViewer.methodSaveDecorations);
    assertEquals("I7", c.profileViewer.methodSetEditMode);
    assertEquals("t7", c.profileViewer.methodResetDecorations);
    assertEquals("F7", c.profileViewer.methodSelectDecoration);
    assertEquals("c", c.profileViewer.editorDeleteButtonField);
    assertEquals("d", c.profileViewer.editorEditButtonField);
    assertEquals("k0", c.profile.fieldH3);
    assertEquals("w", c.callRestrictions.methodChatCallState);
    assertEquals("w", c.callRestrictions.methodCameraCallState);
    assertEquals("kq7.a", c.classAliases.get("so7.a"));
    assertEquals("qz7.i", c.classAliases.get("ew7.i"));
    assertEquals("h08.e", c.classAliases.get("vw7.e"));
    assertEquals("hz.y0", c.notificationFix.firebaseQueueClass);
    assertEquals("z60.a", c.nightMode.nightModeConfiguratorClass);
    assertEquals("ih6.j", c.nightMode.darkThemeManagerClass);
    assertEquals("A", c.nightMode.methodThemeMode);
    assertEquals("a", c.unsend.itemViewField);
    assertEquals("", c.unsend.methodGetItemView);
    assertEquals("tq8.a$a$c", c.unsend.destroyResultClass);
    assertEquals("r21.b", c.aiIcon.repoClass);
    assertEquals("a", c.aiIcon.methodGetShownAfterMillis);
    assertEquals("oy1.k", c.chat.searchResultTitleViewHolderClass);
    assertEquals(0, c.res.idStatusBarGuide);
    LineVersion.Config old = LineVersion.resolveVersion("26.15.1", name -> false);
    assertEquals("z7", old.profileViewer.methodSaveDecorations);
    assertEquals("b", old.profileViewer.editorDeleteButtonField);
    assertEquals("il1.z0", old.chat.headerController);
    assertEquals(0, old.classAliases.size());
  }

  @Test
  public void missingVersionNameFailsClosed() {
    assertNull(LineVersion.resolveVersion(null, name -> true));
    assertEquals("unsupported", LineVersion.getCompatibilityState());
    assertEquals("", LineVersion.getResolvedVersionName());
  }

  @Test
  public void line26171ResolvesItsOwnHookFamiliesWithoutChangingThePreviousVersion() {
    LineVersion.Config current =
        LineVersion.resolveVersion("LINE/26.17.1 (261710173)", name -> false);
    assertNotNull(current);
    assertEquals("exact", LineVersion.getCompatibilityState());
    assertEquals("26.17.1", LineVersion.getResolvedVersionName());
    assertEquals("ji5.b", current.settings.settingsSearchHelperClass);
    assertEquals("jr1.y", current.unsend.chatMessageViewHolderClass);
    assertEquals("x18.i", current.classAliases.get("vw7.j"));
    assertEquals("C7", current.profileViewer.methodSaveDecorations);
    LineVersion.Config previous = LineVersion.resolveVersion("26.16.0", name -> false);
    assertEquals("eh5.b", previous.settings.settingsSearchHelperClass);
    assertEquals("B7", previous.profileViewer.methodSaveDecorations);
    assertEquals("h08.j", previous.classAliases.get("vw7.j"));
  }

  @Test
  public void unknownVersionFailsClosedWithoutEnoughAnchors() {
    assertNull(
        LineVersion.resolveVersion(
            "26.17.0", name -> "jp.naver.line.android.activity.main.MainActivity".equals(name)));
    assertEquals("unsupported", LineVersion.getCompatibilityState());
    assertEquals("", LineVersion.getResolvedVersionName());
  }
}
