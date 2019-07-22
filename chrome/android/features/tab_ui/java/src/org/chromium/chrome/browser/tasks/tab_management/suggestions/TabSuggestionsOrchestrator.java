// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tasks.tab_management.suggestions;

import org.chromium.chrome.browser.lifecycle.Destroyable;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Represents the entry point for the TabSuggestions component. Responsible for
 * registering and invoking the different {@link TabSuggestionsFetcher}.
 */
public final class TabSuggestionsOrchestrator implements TabSuggestions, Destroyable {
    private static final String TAG = "TabSuggestionsDetailed";
    public static final String TAB_SUGGESTIONS_UMA_PREFIX = "TabSuggestions";

    private List<TabSuggestionsFetcher> mTabSuggestionsFetchers;
    private List<TabSuggestion> mPrefetchedResults = new LinkedList<>();
    private TabContext mPrefetchedTabContext;
    private TabContextObserver mTabContextObserver;
    private TabModelSelector mTabModelSelector;
    private TabSuggestionsConfiguration mTabSuggestionsConfiguration;
    private static TabSuggestionsOrchestrator sTabSuggestionsOrchestrator;

    public static TabSuggestionsOrchestrator getInstance(TabModelSelector selector) {
        if (sTabSuggestionsOrchestrator == null) {
            sTabSuggestionsOrchestrator = new TabSuggestionsOrchestrator(selector);
            sTabSuggestionsOrchestrator.initialize();
        }
        return sTabSuggestionsOrchestrator;
    }

    private TabSuggestionsOrchestrator(TabModelSelector selector) {
        mTabModelSelector = selector;
        mTabSuggestionsFetchers = new LinkedList<>();
        mTabSuggestionsFetchers.add(new TabSuggestionsClientFetcher(selector));
        mTabSuggestionsFetchers.add(new TabSuggestionsServerFetcher());
        mTabContextObserver = new TabContextObserver(selector, this::prefetchSuggestions);
        mTabSuggestionsConfiguration = new TabSuggestionsConfiguration();
    }

    private void initialize() {
        mTabSuggestionsConfiguration.fetchConfiguration();
    }

    @Override
    public List<TabSuggestion> getSuggestions(TabContext tabContext) {
        android.util.Log.e(TAG, "Getting the suggestions");
        synchronized (mPrefetchedResults) {
            if (tabContext.equals(mPrefetchedTabContext)) {
                return aggregateResults(mPrefetchedResults);
            }
            return new LinkedList<>();
        }
    }

    private List<TabSuggestion> aggregateResults(List<TabSuggestion> tabSuggestions) {
        List<TabSuggestion> aggregated = new LinkedList<>();
        android.util.Log.e(
                TAG, "Aggregated suggestions list size from all fetchers " + tabSuggestions.size());
        Map<String, TabSuggestionProviderConfiguration> providerConfig =
                mTabSuggestionsConfiguration.getProviderConfigurations();

        for (TabSuggestion tabSuggestion : tabSuggestions) {
            if (tabSuggestion.getTabsInfo().isEmpty()) continue;

            if (providerConfig.get(tabSuggestion.getProviderName()) != null
                    && !providerConfig.get(tabSuggestion.getProviderName()).isEnabled())
                continue;

            aggregated.add(tabSuggestion);
        }
        android.util.Log.e(TAG, "Final suggestions list size " + aggregated.size());
        Collections.shuffle(aggregated);
        return aggregated;
    }

    @Override
    public void destroy() {
        mTabSuggestionsConfiguration.destroy();
        mTabContextObserver.destroy();
    }

    private void prefetchSuggestions() {
        TabContext tabContext = TabContext.createCurrentContext(mTabModelSelector);
        android.util.Log.e(TAG,
                "Prefetching Tab suggestions "
                        + ":" + System.currentTimeMillis());
        synchronized (mPrefetchedResults) {
            mPrefetchedTabContext = tabContext;
            mPrefetchedResults = new LinkedList<>();
            for (TabSuggestionsFetcher tabSuggestionsFetcher : mTabSuggestionsFetchers) {
                if (tabSuggestionsFetcher.isEnabled()) {
                    tabSuggestionsFetcher.fetch(tabContext, res -> prefetchCallback(res));
                }
            }
        }
    }

    public void prefetchCallback(TabSuggestionsFetcherResults suggestions) {
        android.util.Log.e(TAG,
                "prefetchCallback with " + suggestions.getTabSuggestions().size() + ":"
                        + System.currentTimeMillis());
        synchronized (mPrefetchedResults) {
            if (suggestions.getTabContext().equals(mPrefetchedTabContext)) {
                android.util.Log.e(TAG, "Add new suggestions to cached suggestions");
                mPrefetchedResults.addAll(suggestions.getTabSuggestions());
            }
        }
    }
}
