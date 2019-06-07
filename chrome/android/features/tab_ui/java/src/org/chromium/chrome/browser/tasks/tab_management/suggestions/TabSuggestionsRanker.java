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
    private static final int DEFAULT_CLIENT_PROVIDER_SCORE = 100;
    private static final int DEFAULT_SERVER_PROVIDER_SCORE = 200;

    /**
     * List of all known providers (server & client).
     * TODO: decouple server providers from client. crbug.com/970933
     * */
    @StringDef({SuggestionProviders.DUPLICATE_PAGE_SUGGESTION_PROVIDER,
            SuggestionProviders.STALE_TABS_SUGGESTION_PROVIDER,
            SuggestionProviders.META_TAG_SUGGESTION_PROVIDER,
            SuggestionProviders.SHOPPING_PRODUCT_PROVIDER,
            SuggestionProviders.SESSION_TAB_SWITCHES_SUGGESTION_PROVIDER})
    public @interface SuggestionProviders {
        String DUPLICATE_PAGE_SUGGESTION_PROVIDER = "DuplicatePageSuggestionProvider";
        String SESSION_TAB_SWITCHES_SUGGESTION_PROVIDER = "SessionTabSwitchesSuggestionProvider";
        String STALE_TABS_SUGGESTION_PROVIDER = "StaleTabSuggestionProvider";
        String META_TAG_SUGGESTION_PROVIDER = "MetaTagSuggestionProvider";
        String SHOPPING_PRODUCT_PROVIDER = "ProductsSuggestionProvider";
    }

    // TODO: move this mapping to config. crbug.com/959938
    private static Map<String, Integer> sScores = new HashMap<String, Integer>() {
        {
            put(SuggestionProviders.DUPLICATE_PAGE_SUGGESTION_PROVIDER,
                    DEFAULT_CLIENT_PROVIDER_SCORE);
            put(SuggestionProviders.STALE_TABS_SUGGESTION_PROVIDER, DEFAULT_CLIENT_PROVIDER_SCORE);
            put(SuggestionProviders.META_TAG_SUGGESTION_PROVIDER, DEFAULT_SERVER_PROVIDER_SCORE);
            put(SuggestionProviders.SHOPPING_PRODUCT_PROVIDER, DEFAULT_SERVER_PROVIDER_SCORE);
            put(SuggestionProviders.SESSION_TAB_SWITCHES_SUGGESTION_PROVIDER,
                    DEFAULT_CLIENT_PROVIDER_SCORE);
        }
    };

    /**
     * Ranks suggestions based on the number of tabs first and the score of the provider in case of
     * a tie. This logic is subject to change in the future.
     * @param suggestions to be ranked
     * @return sorted suggestions list where first suggestion in the list is the most preferred
     */
    public static List<TabSuggestion> getRankedSuggestions(List<TabSuggestion> suggestions) {
        if (suggestions.isEmpty()) {
            return suggestions;
        }

        Collections.sort(suggestions, (a, b) -> {
            if (a == b) return 0;

            if (a.getTabsInfo().size() == b.getTabsInfo().size()) {
                int aScore = sScores.containsKey(a.getProviderName())
                        ? sScores.get(a.getProviderName())
                        : 0;
                int bScore = sScores.containsKey(b.getProviderName())
                        ? sScores.get(b.getProviderName())
                        : 0;

                return Integer.compare(bScore, aScore);
            }

            return Integer.compare(b.getTabsInfo().size(), a.getTabsInfo().size());
        });

        return suggestions;
    }
}
