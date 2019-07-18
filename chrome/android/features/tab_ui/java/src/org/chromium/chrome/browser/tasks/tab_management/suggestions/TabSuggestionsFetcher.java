// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tasks.tab_management.suggestions;

import org.chromium.base.Callback;

/**
 * Defines the interface for suggestion fetchers.
 */
public interface TabSuggestionsFetcher {
    void fetch(TabContext tabContext, Callback<TabSuggestionsFetcherResults> callback);

    boolean isEnabled();
}
