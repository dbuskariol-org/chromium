// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tasks.tab_management.suggestions;

import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tab suggestion provider suggesting tabs currently displaying duplicate pages.
 */
class DuplicatePageTabSuggestionProvider implements TabSuggestionProvider {
    @Override
    public List<TabSuggestion> suggest(TabContext tabContext) {
        if (tabContext == null || tabContext.getTabsInfo() == null
                || tabContext.getTabsInfo().isEmpty()) {
            return null;
        }

        Map<String, List<TabContext.TabInfo>> tabsByUrl = new HashMap<>();

        // Collect tabs by URL.
        for (TabContext.TabInfo tab : tabContext.getTabsInfo()) {
            // TODO(mattsimmons): Check if this URL is the one we want or if we need to expose User
            //  typed URL via JNI.
            String url = tab.getOriginalUrl();
            if (TextUtils.isEmpty(url)) continue;

            if (tabsByUrl.get(url) != null) {
                tabsByUrl.get(url).add(tab);
            } else {
                tabsByUrl.put(url, new ArrayList<TabContext.TabInfo>() {
                    { add(tab); }
                });
            }
        }

        if (tabsByUrl.isEmpty()) return null;
        List<TabSuggestion> tabSuggestions = new ArrayList<>();

        // Find all sets of tabs with more than 2 tabs having the same URL and remove the most
        // recently accessed tab.
        for (List<TabContext.TabInfo> tabsInfo : tabsByUrl.values()) {
            if (tabsInfo.size() <= 2) continue;

            Collections.sort(tabsInfo,
                    (tab1, tab2)
                            -> Long.compare(tab1.getTimestampMillis(), tab2.getTimestampMillis()));

            tabsInfo.remove(tabsInfo.size() - 1);
            tabSuggestions.add(new TabSuggestion(tabsInfo, TabSuggestion.TabSuggestionAction.CLOSE,
                    TabSuggestionsRanker.SuggestionProviders.DUPLICATE_PAGE_SUGGESTION_PROVIDER));
        }

        return tabSuggestions;
    }
}
