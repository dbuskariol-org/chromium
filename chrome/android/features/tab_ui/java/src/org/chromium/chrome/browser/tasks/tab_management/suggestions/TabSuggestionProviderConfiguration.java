// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tasks.tab_management.suggestions;

class TabSuggestionProviderConfiguration {
    private final int mScore;
    private final boolean mIsEnabled;

    TabSuggestionProviderConfiguration(int score, boolean isEnabled) {
        mScore = score;
        mIsEnabled = isEnabled;
    }

    int getScore() {
        return mScore;
    }

    boolean isEnabled() {
        return mIsEnabled;
    }
}