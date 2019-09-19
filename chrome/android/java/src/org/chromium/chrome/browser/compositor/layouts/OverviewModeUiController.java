// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.compositor.layouts;

import android.support.annotation.Nullable;

import org.chromium.chrome.browser.omnibox.LocationBarVoiceRecognitionHandler;

/** Interface to control the overview UI. */
public interface OverviewModeUiController {
    /** The delegate for the fake search box. */
    interface FakeSearchBoxDelegate {
        /**
         * Interface to request focusing on the URL bar.
         * @param pastedText The text to be pasted in the Omnibox or null.
         * */
        void requestUrlFocus(@Nullable String pastedText);
    }

    /** The delegate for the bottom bar. */
    interface BottomBarDelegate {
        /**
         * Set the visibility of the Omnibox.
         * @param isVisible Whether is showing the Omnibox.
         */
        void setOmniboxVisibility(boolean isVisible);
    }

    /**
     * Set the {@link FakeSearchBoxDelegate}.
     * @param delegate The given {@link FakeSearchBoxDelegate}.
     */
    void setFakeSearchBoxDelegate(FakeSearchBoxDelegate delegate);

    /** Show the fake search box. */
    void onUrlFocusChange(boolean hasFocus);

    /**
     * Set the voice recognition handler.
     * @param voiceRecognitionHandler The given {@link LocationBarVoiceRecognitionHandler}
     */
    void setVoiceRecognitionHandler(LocationBarVoiceRecognitionHandler voiceRecognitionHandler);

    /**
     * Set the {@link BottomBarDelegate}.
     * @param delegate The given {@link BottomBarDelegate}.
     */
    void setBottomBarDelegate(BottomBarDelegate delegate);
}
