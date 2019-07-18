// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tasks.tab_management.suggestions;

import java.util.List;

/**
 * Represents results for recommendations regarding whether Tabs should be grouped or
 * closed
 */
public class TabSuggestionsFetcherResults {
    private List<TabSuggestion> mTabSuggestions;
    private TabContext mTabContext;

    TabSuggestionsFetcherResults(List<TabSuggestion> tabSuggestions, TabContext tabContext) {
        this.mTabSuggestions = tabSuggestions;
        this.mTabContext = tabContext;
    }

    public List<TabSuggestion> getTabSuggestions() {
        return mTabSuggestions;
    }

    public TabContext getTabContext() {
        return mTabContext;
    }
}
