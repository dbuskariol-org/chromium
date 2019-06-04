// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tasks.tab_management.suggestions;

import org.chromium.base.Callback;

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

    public TabSuggestionsOrchestrator() {
        mClientFetcher = new TabSuggestionsClientFetcher();
        mServerFetcher = new TabSuggestionsServerFetcher();
    }

    @Override
    public void getSuggestions(TabContext tabContext, Callback<List<TabSuggestion>> callback) {
        mCallback = callback;
        mClientFetcher.fetch(tabContext, res -> orchestratorCallbackClient(res));
        mServerFetcher.fetch(tabContext, res -> orchestratorCallbackServer(res));
    }

    public void orchestratorCallbackClient(List<TabSuggestion> clientSuggestions) {
        mClientSuggestions = clientSuggestions;
    }

    public void orchestratorCallbackServer(List<TabSuggestion> serverSuggestions) {
        List<TabSuggestion> aggregatedSuggestions = new LinkedList<>();
        aggregatedSuggestions.addAll(mClientSuggestions);
        aggregatedSuggestions.addAll(serverSuggestions);
        aggregatedSuggestions = TabSuggestionsRanker.getRankedSuggestions(aggregatedSuggestions);
        mCallback.onResult(aggregatedSuggestions);
    }
}
