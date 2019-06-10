// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tasks.tab_management.suggestions;

import org.chromium.base.Callback;
import org.chromium.chrome.browser.ChromeVersionInfo;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Represents the entry point for the TabSuggestions component. Responsible for
 * registering and invoking the different {@link TabSuggestionsFetcher}.
 */
public final class TabSuggestionsOrchestrator implements TabSuggestions {
    private TabSuggestionsClientFetcher mClientFetcher;
    private TabSuggestionsServerFetcher mServerFetcher;
    private List<TabSuggestion> mClientSuggestions;
    private Callback<List<TabSuggestion>> mCallback;

    public TabSuggestionsOrchestrator(TabModelSelector selector) {
        mClientFetcher = new TabSuggestionsClientFetcher(selector);
        mServerFetcher = new TabSuggestionsServerFetcher();
    }

    @Override
    public void getSuggestions(TabContext tabContext, Callback<List<TabSuggestion>> callback) {
        mCallback = callback;
        android.util.Log.e("TabSuggestionsDetailed","getSuggestions"+":"+System.currentTimeMillis());
        if (ChromeVersionInfo.isOfficialBuild()) {
            mClientFetcher.fetch(tabContext, res -> orchestratorCallbackClient(res));
            mServerFetcher.fetch(tabContext, res -> orchestratorCallbackServer(res));
        } else {
            mClientFetcher.fetch(tabContext, res -> orchestratorCallbackClientOnly(res));
        }
    }

    public void orchestratorCallbackClient(List<TabSuggestion> clientSuggestions) {
        android.util.Log.e("TabSuggestionsDetailed","orchestratorCallbackClient with "+clientSuggestions.size()+":"+System.currentTimeMillis());
        mClientSuggestions = clientSuggestions;
    }

    public void orchestratorCallbackServer(List<TabSuggestion> serverSuggestions) {
        android.util.Log.e("TabSuggestionsDetailed","orchestratorCallbackServer with "+serverSuggestions.size()+":"+System.currentTimeMillis());
        List<TabSuggestion> aggregatedSuggestions = new ArrayList<>();
        aggregatedSuggestions.addAll(mClientSuggestions);
        aggregatedSuggestions.addAll(serverSuggestions);
        android.util.Log.e("TabSuggestionsDetailed","Aggregated suggestions list size "+aggregatedSuggestions.size());

        // prune and rank suggestions
        List<TabSuggestion> finalSuggestions = new ArrayList<>();
        for (TabSuggestion suggestion : aggregatedSuggestions) {
            // Additional checks to avoid user experience.
            if (suggestion.getTabsInfo().size() > 1) {
                finalSuggestions.add(suggestion);
            }
        }
        android.util.Log.e("TabSuggestionsDetailed","Final suggestions list size "+finalSuggestions.size());

        finalSuggestions = TabSuggestionsRanker.getRankedSuggestions(finalSuggestions);
        mCallback.onResult(finalSuggestions);
    }

    public void orchestratorCallbackClientOnly(List<TabSuggestion> clientSuggestions) {
        mCallback.onResult(TabSuggestionsRanker.getRankedSuggestions(clientSuggestions));
    }
}
