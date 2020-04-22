// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.dom_distiller;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import org.chromium.base.UserDataHost;
import org.chromium.base.test.BaseRobolectricTestRunner;
import org.chromium.base.test.util.Feature;
import org.chromium.base.test.util.JniMocker;
import org.chromium.chrome.browser.dom_distiller.ReaderModeManager.DistillationStatus;
import org.chromium.chrome.browser.dom_distiller.TabDistillabilityProvider.DistillabilityObserver;
import org.chromium.chrome.browser.infobar.ReaderModeInfoBar;
import org.chromium.chrome.browser.infobar.ReaderModeInfoBarJni;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tab.TabObserver;
import org.chromium.components.dom_distiller.core.DomDistillerUrlUtils;
import org.chromium.components.dom_distiller.core.DomDistillerUrlUtilsJni;
import org.chromium.content_public.browser.NavigationController;
import org.chromium.content_public.browser.WebContents;

import java.util.concurrent.TimeoutException;

/** This class tests the behavior of the {@link ReaderModeManager}. */
@RunWith(BaseRobolectricTestRunner.class)
public class ReaderModeManagerTest {
    @Rule
    public JniMocker jniMocker = new JniMocker();

    @Mock
    private Tab mTab;

    @Mock
    private WebContents mWebContents;

    @Mock
    private TabDistillabilityProvider mDistillabilityProvider;

    @Mock
    private NavigationController mNavController;

    @Mock
    private DomDistillerTabUtils.Natives mDistillerTabUtilsJniMock;

    @Mock
    private DomDistillerUrlUtils.Natives mDistillerUrlUtilsJniMock;

    @Mock
    private ReaderModeInfoBar.Natives mReaderModeInfobarJniMock;

    @Captor
    private ArgumentCaptor<TabObserver> mTabObserverCaptor;
    private TabObserver mTabObserver;

    @Captor
    private ArgumentCaptor<DistillabilityObserver> mDistillabilityObserverCaptor;
    private DistillabilityObserver mDistillabilityObserver;

    private UserDataHost mUserDataHost;
    private ReaderModeManager mManager;

    @Before
    public void setUp() throws TimeoutException {
        MockitoAnnotations.initMocks(this);
        jniMocker.mock(org.chromium.chrome.browser.dom_distiller.DomDistillerTabUtilsJni.TEST_HOOKS,
                mDistillerTabUtilsJniMock);
        jniMocker.mock(DomDistillerUrlUtilsJni.TEST_HOOKS, mDistillerUrlUtilsJniMock);
        jniMocker.mock(ReaderModeInfoBarJni.TEST_HOOKS, mReaderModeInfobarJniMock);

        DomDistillerTabUtils.setExcludeMobileFriendlyForTesting(true);

        mUserDataHost = new UserDataHost();
        mUserDataHost.setUserData(TabDistillabilityProvider.USER_DATA_KEY, mDistillabilityProvider);

        when(mTab.getUserDataHost()).thenReturn(mUserDataHost);
        when(mTab.getWebContents()).thenReturn(mWebContents);
        when(mTab.getUrlString()).thenReturn("url");
        when(mWebContents.getNavigationController()).thenReturn(mNavController);
        when(mNavController.getUseDesktopUserAgent()).thenReturn(false);

        when(DomDistillerUrlUtils.isDistilledPage("url")).thenReturn(false);

        mManager = new ReaderModeManager(mTab);

        // Ensure the tab observer is attached when the manager is created.
        verify(mTab).addObserver(mTabObserverCaptor.capture());
        mTabObserver = mTabObserverCaptor.getValue();

        // Ensure the distillability observer is attached when the tab is shown.
        mTabObserver.onShown(mTab, 0);
        verify(mDistillabilityProvider).addObserver(mDistillabilityObserverCaptor.capture());
        mDistillabilityObserver = mDistillabilityObserverCaptor.getValue();
    }

    @Test
    @Feature("ReaderMode")
    public void testReaderModeUI_triggered() {
        mDistillabilityObserver.onIsPageDistillableResult(mTab, true, true, false);
        assertEquals("Distillation should be possible.", DistillationStatus.POSSIBLE,
                mManager.getDistillationStatus());
        verify(mReaderModeInfobarJniMock).create(mTab);
    }

    @Test
    @Feature("ReaderMode")
    public void testReaderModeUI_notTriggered() {
        mDistillabilityObserver.onIsPageDistillableResult(mTab, false, true, false);
        assertEquals("Distillation should not be possible.", DistillationStatus.NOT_POSSIBLE,
                mManager.getDistillationStatus());
    }
}
