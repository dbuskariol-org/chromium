// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.features.start_surface;

import android.support.annotation.Nullable;

import org.chromium.chrome.browser.compositor.layouts.OverviewModeUiController;
import org.chromium.chrome.browser.omnibox.LocationBarVoiceRecognitionHandler;
import org.chromium.chrome.browser.tasks.TasksSurface;

/** Implements {@link OverviewModeUiController} to control the given start surface. */
public final class StartSurfaceUiController implements OverviewModeUiController,
                                                       StartSurfaceMediator.Delegate,
                                                       TasksSurface.FakeSearchBoxDelegate {
    private final StartSurfaceCoordinator mStartSurfaceCoordinator;
    private OverviewModeUiController.FakeSearchBoxDelegate mFakeSearchBoxDelegate;
    private OverviewModeUiController.BottomBarDelegate mBarDelegate;

    public StartSurfaceUiController(StartSurface startSurface) {
        mStartSurfaceCoordinator = (StartSurfaceCoordinator) startSurface;
        mStartSurfaceCoordinator.setFakeSearchBoxDelegate(this);
        mStartSurfaceCoordinator.setDelegate(this);
    }

    // Implements OverviewModeUiController.
    @Override
    public void setFakeSearchBoxDelegate(OverviewModeUiController.FakeSearchBoxDelegate delegate) {
        mFakeSearchBoxDelegate = delegate;
    }

    @Override
    public void onUrlFocusChange(boolean hasFocus) {
        mStartSurfaceCoordinator.onUrlFocusChange(hasFocus);
    }

    @Override
    public void setVoiceRecognitionHandler(
            LocationBarVoiceRecognitionHandler voiceRecognitionHandler) {
        mStartSurfaceCoordinator.setVoiceRecognitionHandler(voiceRecognitionHandler);
    }

    @Override
    public void setBottomBarDelegate(BottomBarDelegate delegate) {
        mBarDelegate = delegate;
    }

    // Implements StartSurface.Delegate.
    @Override
    public void setOmniboxVisibility(boolean isVisible) {
        if (mBarDelegate != null) mBarDelegate.setOmniboxVisibility(isVisible);
    }

    // Implements TasksSurface.FakeSearchBoxDelegate
    @Override
    public void requestUrlFocus(@Nullable String pastedText) {
        if (mFakeSearchBoxDelegate != null) mFakeSearchBoxDelegate.requestUrlFocus(pastedText);
    }
}
