// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tasks.tab_management.suggestions;

import org.chromium.base.Callback;

import java.util.List;

/**
 * Interface for the Tab Suggestions framework.
 */
public interface TabSuggestions {
    void getSuggestions(TabContext tabContext, Callback<List<TabSuggestion>> callback);
}
