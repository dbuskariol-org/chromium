// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tasks.tab_management.suggestions;

import org.chromium.chrome.browser.lifecycle.Destroyable;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;

import java.util.LinkedList;
import java.util.List;

/**
 * Represents the entry point for the TabSuggestions component. Responsible for
 * registering and invoking the different {@link TabSuggestionsFetcher}.
 */
public final class TabSuggestionsOrchestrator implements TabSuggestions, Destroyable {
    private List<TabSuggestionsFetcher> mTabSuggestionsFetchers;
    private List<TabSuggestion> mPrefetchedResults = new LinkedList<>();
    private TabContext mPrefetchedTabContext;
    private TabContextObserver mTabContextObserver;
    private TabModelSelector mTabModelSelector;
    private static TabSuggestionsOrchestrator sTabSuggestionsOrchestrator;

    public static TabSuggestionsOrchestrator getInstance(TabModelSelector selector) {
        if (sTabSuggestionsOrchestrator == null) {
            sTabSuggestionsOrchestrator = new TabSuggestionsOrchestrator(selector);
        }
        return sTabSuggestionsOrchestrator;
    }

    private TabSuggestionsOrchestrator(TabModelSelector selector) {
        mTabModelSelector = selector;
        mTabSuggestionsFetchers = new LinkedList<>();
        mTabSuggestionsFetchers.add(new TabSuggestionsClientFetcher(selector));
        mTabSuggestionsFetchers.add(new TabSuggestionsServerFetcher());
        mTabContextObserver = new TabContextObserver(selector, new Runnable() {
            @Override
            public void run() {
                prefetchSuggestions();
            }
        });
    }

    @Override
    public List<TabSuggestion> getSuggestions(TabContext tabContext) {
        android.util.Log.e("TabSuggestionsDetailed", "Getting the suggestions");
        synchronized (mPrefetchedResults) {
            if (tabContext.equals(mPrefetchedTabContext)) {
                return aggregateResults(mPrefetchedResults);
            }
            return new LinkedList<>();
        }
    }

    private static List<TabSuggestion> aggregateResults(List<TabSuggestion> tabSuggestions) {
        List<TabSuggestion> aggregated = new LinkedList<>();
        android.util.Log.e("TabSuggestionsDetailed",
                "Aggregated suggestions list size from all fetchers " + tabSuggestions.size());
        for (TabSuggestion tabSuggestion : tabSuggestions) {
            if (!tabSuggestion.getTabsInfo().isEmpty()) {
                aggregated.add(tabSuggestion);
            }
        }
        android.util.Log.e(
                "TabSuggestionsDetailed", "Final suggestions list size " + aggregated.size());
        return TabSuggestionsRanker.getRankedSuggestions(aggregated);
    }

    @Override
    public void destroy() {
        mTabContextObserver.destroy();
    }

    private void prefetchSuggestions() {
        TabContext tabContext = TabContext.createCurrentContext(mTabModelSelector);
        android.util.Log.e("TabSuggestionsDetailed",
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
        android.util.Log.e("TabSuggestionsDetailed",
                "prefetchCallback with " + suggestions.getTabSuggestions().size() + ":"
                        + System.currentTimeMillis());
        synchronized (mPrefetchedResults) {
            if (suggestions.getTabContext().equals(mPrefetchedTabContext)) {
                mPrefetchedResults.addAll(suggestions.getTabSuggestions());
            }
        }
    }
}
