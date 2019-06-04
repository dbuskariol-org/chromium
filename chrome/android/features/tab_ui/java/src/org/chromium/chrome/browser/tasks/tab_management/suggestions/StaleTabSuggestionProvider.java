// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tasks.tab_management.suggestions;

import org.chromium.chrome.browser.ChromeFeatureList;
import org.chromium.chrome.browser.tab.Tab;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Provider which returns tabs which have not been used in 1 day or more
 * */
public class StaleTabSuggestionProvider implements TabSuggestionProvider {
    private static final int DEFAULT_THRESHOLD = (int) TimeUnit.DAYS.toMillis(1);

    @Override
    public List<TabSuggestion> suggest(TabContext tabContext) {
        if (tabContext == null || tabContext.getTabs() == null || tabContext.getTabs().size() < 1) {
            return null;
        }

        long staleThreshold = ChromeFeatureList.getFieldTrialParamByFeatureAsInt(
                ChromeFeatureList.CLOSE_TAB_SUGGESTIONS_STALE,
                "close_tab_suggestions_stale_time_ms", DEFAULT_THRESHOLD);

        long now = System.currentTimeMillis();
        List<Tab> staleTabs = new ArrayList<>();
        for (Tab tab : tabContext.getTabs()) {
            if (now - tab.getTimestampMillis() > staleThreshold) {
                staleTabs.add(tab);
            }
        }
        return Arrays.asList(new TabSuggestion(staleTabs, TabSuggestion.TabSuggestionAction.CLOSE,
                TabSuggestionsRanker.SuggestionProviders.STALE_TABS_SUGGESTION_PROVIDER));
    }
}
