// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tasks.tab_management.suggestions;

import android.support.annotation.StringDef;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ranker which ranks all suggestions based on static rules.
 */
public final class TabSuggestionsRanker {
    private static final int DEFAULT_PROVIDER_SCORE = 100;

    @StringDef({SuggestionProviders.RANDOM_TABS_SUGGESTION_PROVIDER,
            SuggestionProviders.STALE_TABS_SUGGESTION_PROVIDER})
    public @interface SuggestionProviders {
        String DUPLICATE_PAGE_SUGGESTION_PROVIDER = "DuplicatePageSuggestionProvider";
        String RANDOM_TABS_SUGGESTION_PROVIDER = "RandomTabSuggestionProvider";
        String STALE_TABS_SUGGESTION_PROVIDER = "StaleTabSuggestionProvider";
    }

    // TODO: move this mapping to config. crbug.com/959938
    private static Map<String, Integer> sScores = new HashMap<String, Integer>() {
        {
            put(SuggestionProviders.DUPLICATE_PAGE_SUGGESTION_PROVIDER, DEFAULT_PROVIDER_SCORE);
            put(SuggestionProviders.RANDOM_TABS_SUGGESTION_PROVIDER, DEFAULT_PROVIDER_SCORE);
            put(SuggestionProviders.STALE_TABS_SUGGESTION_PROVIDER, DEFAULT_PROVIDER_SCORE);
        }
    };

    /**
     * Ranks suggestions based on predefined static rules
     * @param suggestions to be ranked
     * @return sorted suggestions list where first suggestion in the list is the most preferred
     */
    public static List<TabSuggestion> getRankedSuggestions(List<TabSuggestion> suggestions) {
        if (suggestions.isEmpty()) {
            return suggestions;
        }

        Collections.sort(suggestions, (a, b) -> {
            if (a == b) return 0;

            int aScore =
                    sScores.containsKey(a.getProviderName()) ? sScores.get(a.getProviderName()) : 0;
            int bScore =
                    sScores.containsKey(b.getProviderName()) ? sScores.get(b.getProviderName()) : 0;

            if (aScore == bScore) {
                return Integer.compare(b.getTabs().size(), a.getTabs().size());
            }

            return Integer.compare(bScore, aScore);
        });

        return suggestions;
    }
}
