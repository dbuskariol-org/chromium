// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tasks;

import static org.chromium.chrome.browser.tasks.TasksSurfaceProperties.FAKE_SEARCH_BOX_CLICK_LISTENER;
import static org.chromium.chrome.browser.tasks.TasksSurfaceProperties.FAKE_SEARCH_BOX_TEXT;
import static org.chromium.chrome.browser.tasks.TasksSurfaceProperties.FAKE_SEARCH_BOX_TEXT_WATCHER;
import static org.chromium.chrome.browser.tasks.TasksSurfaceProperties.IS_FAKE_SEARCH_BOX_VISIBLE;
import static org.chromium.chrome.browser.tasks.TasksSurfaceProperties.IS_VOICE_RECOGNITION_BUTTON_VISIBLE;
import static org.chromium.chrome.browser.tasks.TasksSurfaceProperties.VOICE_SEARCH_BUTTON_CLICK_LISTENER;

import android.support.annotation.Nullable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;

import org.chromium.chrome.browser.omnibox.LocationBarVoiceRecognitionHandler;
import org.chromium.chrome.browser.tasks.TasksSurface.FakeSearchBoxDelegate;
import org.chromium.ui.modelutil.PropertyModel;

/** The task surface mediator. */
class TasksSurfaceMediator implements TasksSurface.FakeSearchBoxController {
    private final PropertyModel mPropertyModel;
    private LocationBarVoiceRecognitionHandler mVoiceRecognitionHandler;
    @Nullable
    private FakeSearchBoxDelegate mSearchBoxDelegate;

    TasksSurfaceMediator(PropertyModel model) {
        mPropertyModel = model;

        mPropertyModel.set(FAKE_SEARCH_BOX_CLICK_LISTENER, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mSearchBoxDelegate == null) return;
                mSearchBoxDelegate.requestUrlFocus(null);
            }
        });
        mPropertyModel.set(FAKE_SEARCH_BOX_TEXT_WATCHER, new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (mSearchBoxDelegate == null) return;
                if (s.length() == 0) return;
                mSearchBoxDelegate.requestUrlFocus(s.toString());
                mPropertyModel.set(FAKE_SEARCH_BOX_TEXT, "");
            }
        });

        // Set the initial state.
        mPropertyModel.set(IS_VOICE_RECOGNITION_BUTTON_VISIBLE, false);
        mPropertyModel.set(IS_FAKE_SEARCH_BOX_VISIBLE, true);
    }

    @Override
    public void setDelegate(FakeSearchBoxDelegate delegate) {
        mSearchBoxDelegate = delegate;
    }

    @Override
    public void setVoiceRecognitionHandler(
            LocationBarVoiceRecognitionHandler voiceRecognitionHandler) {
        assert voiceRecognitionHandler != null;
        mVoiceRecognitionHandler = voiceRecognitionHandler;
        if (mVoiceRecognitionHandler.isVoiceSearchEnabled()) {
            mPropertyModel.set(IS_VOICE_RECOGNITION_BUTTON_VISIBLE, true);
            mPropertyModel.set(VOICE_SEARCH_BUTTON_CLICK_LISTENER, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mVoiceRecognitionHandler.startVoiceRecognition(
                            LocationBarVoiceRecognitionHandler.VoiceInteractionSource.NTP);
                }
            });
        } else {
            mPropertyModel.set(IS_VOICE_RECOGNITION_BUTTON_VISIBLE, false);
            mPropertyModel.set(VOICE_SEARCH_BUTTON_CLICK_LISTENER, null);
        }
    }
}
