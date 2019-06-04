// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tasks.tab_management.suggestions;

import org.chromium.chrome.browser.tab.Tab;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Sample TabSuggestionsProvider that suggests to group two tabs at random.
 * */
public class RandomTabSuggestionProvider implements TabSuggestionProvider {
    @Override
    public List<TabSuggestion> suggest(TabContext tabContext) {
        if (tabContext == null || tabContext.getTabs() == null
                || tabContext.getTabs().size() <= 1) {
            return null;
        }

        List<Tab> workingTabsList = new ArrayList<>(tabContext.getTabs());
        Collections.shuffle(workingTabsList);
        return Arrays.asList(new TabSuggestion(workingTabsList.subList(0, 2),
                TabSuggestion.TabSuggestionAction.GROUP,
                TabSuggestionsRanker.SuggestionProviders.RANDOM_TABS_SUGGESTION_PROVIDER));
    }
}
