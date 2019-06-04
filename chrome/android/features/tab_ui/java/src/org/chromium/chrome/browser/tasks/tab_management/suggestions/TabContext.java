// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tasks.tab_management.suggestions;

import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.TabModel;
import org.chromium.chrome.browser.tabmodel.TabModelFilter;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a snapshot of the current tabs and tab groups.
 * */
public class TabContext {
    /**
     * Holds basic information about a tab group.
     */
    public static class TabGroupInfo {
        private final List<Tab> mTabs;

        public TabGroupInfo(List<Tab> tabs) {
            mTabs = tabs;
        }

        public List<Tab> getTabs() {
            return mTabs;
        }
    }

    private final List<Tab> mTabs;
    private List<TabGroupInfo> mExistingTabGroups;

    // TODO(ayman): use tab IDs to remove dependency on Tab object.
    private TabContext(List<Tab> tabs, List<TabGroupInfo> groups) {
        mTabs = tabs;
        mExistingTabGroups = groups;
    }

    /**
     * Gets the list of orphan {@link Tab}s in the current {@link TabModel}
     */
    public List<Tab> getTabs() {
        return mTabs;
    }

    /**
     * Gets the list of {@link TabGroupInfo} in the current {@link TabModel}
     */
    public List<TabGroupInfo> getTabGroups() {
        return mExistingTabGroups;
    }

    /**
     * Check the equality of the {@link TabContext}. Only checks the orphan {@link Tab}s.
     * @param context {@link TabContext} compares against.
     * @return true if the list of orphan {@link Tab}s are the same.
     */
    public boolean isEqual(TabContext context) {
        List<Tab> tabs = context.getTabs();
        if (mTabs.size() != context.getTabs().size()) return false;

        for (int i = 0; i < mTabs.size(); i++) {
            if (mTabs.get(i) != tabs.get(i)) return false;
        }
        return true;
    }

    /**
     * Creates an instance of TabContext based on the provided {@link TabModelSelector}.
     * @param tabModelSelector
     * @return an instance of TabContext
     */
    public static TabContext createCurrentContext(TabModelSelector tabModelSelector) {
        TabModelFilter tabModelFilter =
                tabModelSelector.getTabModelFilterProvider().getCurrentTabModelFilter();
        List<Tab> tabs = new ArrayList<>();
        List<TabGroupInfo> existingGroups = new ArrayList<>();

        // Examine each tab in the current model and either add it to the list of orphan tabs or add
        // it to a group it belongs to.
        for (int i = 0; i < tabModelFilter.getCount(); i++) {
            Tab currentTab = tabModelFilter.getTabAt(i);

            List<Tab> relatedTabs = tabModelFilter.getRelatedTabList(currentTab.getId());

            if (relatedTabs != null && relatedTabs.size() > 1) {
                existingGroups.add(new TabGroupInfo(relatedTabs));
            } else {
                tabs.add(currentTab);
            }
        }

        return new TabContext(tabs, existingGroups);
    }
}