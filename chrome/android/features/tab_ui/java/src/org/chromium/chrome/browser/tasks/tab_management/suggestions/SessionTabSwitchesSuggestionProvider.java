// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tasks.tab_management.suggestions;

import android.util.Pair;

import org.chromium.chrome.browser.ChromeFeatureList;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.tabmodel.TabModelSelectorTabModelObserver;
import org.chromium.chrome.browser.tabmodel.TabModelSelectorTabObserver;
import org.chromium.chrome.browser.tabmodel.TabSelectionType;
import org.chromium.content_public.browser.LoadUrlParams;
import org.chromium.ui.base.PageTransition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * Tab suggestion provider suggesting tabs frequently switched between per session.
 */
class SessionTabSwitchesSuggestionProvider implements TabSuggestionProvider {
    /** PMI threshold property name. */
    private static final String PMI_THRESHOLD_PARAM_NAME = "intertab_suggestion_pmi_threshold";

    /** The minimum amount of switches to a tab, from another, before considering suggesting. */
    private static final int MIN_NUMBER_OF_SWITCHES = 3;

    private final TabModelSelectorTabModelObserver mTabModelSelectorTabModelObserver;
    private final TabModelSelectorTabObserver mTabModelSelectorTabObserver;

    private final int mConfiguredPmiThreshold;

    /** The total number of tab switches for the current session. */
    private int mTotalNumberOfTabSwitches;

    /** The total number of switches to a tab, keyed by Tab ID. Resets on clobber. */
    private Map<Integer, Integer> mSwitchesPerTab = new HashMap<>();

    /** Pair-wise count of tab switches for the current session. Pair is (toTabId, fromTabId). */
    private Map<Pair<Integer, Integer>, Integer> mInterTabSwitches = new HashMap<>();

    public SessionTabSwitchesSuggestionProvider(TabModelSelector selector) {
        super();

        mConfiguredPmiThreshold = ChromeFeatureList.getFieldTrialParamByFeatureAsInt(
                ChromeFeatureList.INTERTAB_GROUPING_SUGGESTIONS, PMI_THRESHOLD_PARAM_NAME,
                Integer.MAX_VALUE);

        mTabModelSelectorTabModelObserver = new TabModelSelectorTabModelObserver(selector) {
            @Override
            public void didSelectTab(Tab tab, @TabSelectionType int type, int lastId) {
                if (tab == null || tab.getId() == lastId) return;

                recordTabSwitch(tab.getId(), lastId);
            }
        };

        mTabModelSelectorTabObserver = new TabModelSelectorTabObserver(selector) {
            @Override
            public void onLoadUrl(Tab tab, LoadUrlParams params, int loadType) {
                // Reset switch count for this tab if it's clobbered from the address bar.
                if ((params.getTransitionType() & PageTransition.FROM_ADDRESS_BAR) == 0) return;

                mSwitchesPerTab.remove(tab.getId());
            }
        };
    }

    @Override
    public List<TabSuggestion> suggest(TabContext tabContext) {
        if (tabContext == null || tabContext.getTabsInfo() == null
                || tabContext.getTabsInfo().isEmpty()) {
            return null;
        }

        if (mInterTabSwitches.isEmpty()) return null;

        List<Pair<Integer, Integer>> candidatePairs = new ArrayList<>();
        for (Map.Entry<Pair<Integer, Integer>, Integer> entry : mInterTabSwitches.entrySet()) {
            if (entry.getValue() < MIN_NUMBER_OF_SWITCHES) continue;

            candidatePairs.add(entry.getKey());
        }

        if (candidatePairs.isEmpty()) return null;

        // This represents a potentially disconnected undirected graph.
        Map<Integer, Set<Integer>> adjacencyList = new HashMap<>();

        for (Pair<Integer, Integer> tabPair : candidatePairs) {
            if (mSwitchesPerTab.get(tabPair.first) == null
                    || mSwitchesPerTab.get(tabPair.second) == null) {
                continue;
            }

            double pmi = computePmi(tabPair);
            if (pmi < mConfiguredPmiThreshold) continue;

            addEdges(tabPair, adjacencyList);
        }

        return suggestionsFromConnectedTabs(tabContext, adjacencyList);
    }

    private List<TabSuggestion> suggestionsFromConnectedTabs(
            TabContext tabContext, Map<Integer, Set<Integer>> adjacencyList) {
        if (adjacencyList.isEmpty()) return null;

        Map<Integer, Boolean> visited = new HashMap<>();
        List<List<Integer>> tabIdLists = new ArrayList<>();

        // Find transitively connected tab switches.
        for (int n : adjacencyList.keySet()) {
            Queue<Integer> q = new LinkedList<>();
            q.offer(n);
            List<Integer> tabIds = new ArrayList<>();

            while (!q.isEmpty()) {
                int id = q.poll();
                tabIds.add(id);
                visited.put(id, true);

                for (int adjacentId : adjacencyList.get(id)) {
                    if (visited.get(adjacentId) != null) continue;
                    q.offer(adjacentId);
                }
            }

            // We've searched this entire disconnected sub-component, add to the overall list.
            tabIdLists.add(tabIds);
        }

        Map<Integer, TabContext.TabInfo> tabMap = new HashMap<>();
        for (TabContext.TabInfo tabInfo : tabContext.getTabsInfo())
            tabMap.put(tabInfo.getId(), tabInfo);

        List<TabSuggestion> suggestions = new ArrayList<>();

        for (List<Integer> tabIds : tabIdLists) {
            if (tabIds.size() < 2) continue;

            List<TabContext.TabInfo> tabs = new ArrayList<>();
            for (int id : tabIds) tabs.add(tabMap.get(id));

            suggestions.add(new TabSuggestion(tabs, TabSuggestion.TabSuggestionAction.GROUP,
                    TabSuggestionsRanker.SuggestionProviders
                            .SESSION_TAB_SWITCHES_SUGGESTION_PROVIDER));
        }

        return suggestions;
    }

    private double computePmi(Pair<Integer, Integer> tabPair) {
        // P(tab) = N(tab) / totalTabSwitches
        float pSwitchToTab =
                mSwitchesPerTab.get(tabPair.first).floatValue() / mTotalNumberOfTabSwitches;
        // pmi(toTab, fromTab) = P(toTab|fromTab)/P(toTab)
        float pToTabFromTab =
                mInterTabSwitches.get(tabPair).floatValue() / mSwitchesPerTab.get(tabPair.second);

        return Math.log(pToTabFromTab / pSwitchToTab) / Math.log(2);
    }

    private void addEdges(Pair<Integer, Integer> tabPair, Map<Integer, Set<Integer>> adjList) {
        if (adjList.get(tabPair.first) != null) {
            adjList.get(tabPair.first).add(tabPair.second);
        } else {
            adjList.put(tabPair.first, new HashSet<Integer>() {
                { add(tabPair.second); }
            });
        }

        if (adjList.get(tabPair.second) != null) {
            adjList.get(tabPair.second).add(tabPair.first);
        } else {
            adjList.put(tabPair.second, new HashSet<Integer>() {
                { add(tabPair.first); }
            });
        }
    }

    private void recordTabSwitch(int toTabId, int fromTabId) {
        mTotalNumberOfTabSwitches++;

        incrementTabSwitchCounts(toTabId, fromTabId);
    }

    private void incrementTabSwitchCounts(int toTabId, int fromTabId) {
        if (mSwitchesPerTab.get(toTabId) != null) {
            final int currentCount = mSwitchesPerTab.get(toTabId);
            mSwitchesPerTab.put(toTabId, currentCount + 1);
        } else {
            mSwitchesPerTab.put(toTabId, 1);
        }

        Pair<Integer, Integer> mSwitchPair = new Pair<>(toTabId, fromTabId);
        if (mInterTabSwitches.get(mSwitchPair) != null) {
            final int currentCount = mInterTabSwitches.get(mSwitchPair);
            mInterTabSwitches.put(mSwitchPair, currentCount + 1);
        } else {
            mInterTabSwitches.put(mSwitchPair, 1);
        }
    }
}
