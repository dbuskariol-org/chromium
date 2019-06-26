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
        private final List<TabInfo> mTabs;

        public TabGroupInfo(List<TabInfo> tabs) {
            mTabs = tabs;
        }

        public List<TabInfo> getTabs() {
            return mTabs;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (other == null) return false;
            if (other instanceof TabGroupInfo) {
                TabGroupInfo otherGroupInfo = (TabGroupInfo) other;
                return mTabs == null ? otherGroupInfo.getTabs() == null
                                     : mTabs.equals(otherGroupInfo.getTabs());
            }

            return false;
        }

        @Override
        public int hashCode() {
            return 31 * (mTabs == null ? 0 : mTabs.hashCode());
        }
    }

    /**
     * Holds basic information about a tab.
     */
    public static class TabInfo implements Comparable<TabInfo> {
        private final String mUrl;
        private final String mReferrerUrl;
        private final long mTimestampMillis;
        private final int mId;
        private final String mTitle;
        private final String mOriginalUrl;

        public int getId() {
            return mId;
        }
        public String getTitle() {
            return mTitle;
        }
        public String getUrl() {
            return mUrl;
        }
        public String getOriginalUrl() {
            return mOriginalUrl;
        }
        public String getReferrerUrl() {
            return mReferrerUrl;
        }
        public long getTimestampMillis() {
            return mTimestampMillis;
        }

        /**
         * Constructs a new TabInfo object
         * */
        public TabInfo(int id, String title, String url, String originalUrl, String referrerUrl,
                long timestampMillis) {
            mId = id;
            mTitle = title;
            mUrl = url;
            mOriginalUrl = originalUrl;
            mReferrerUrl = referrerUrl;
            mTimestampMillis = timestampMillis;
        }

        /**
         * Creates a new TabInfo object from {@link Tab}
         * */
        public static TabInfo createFromTab(Tab tab) {
            return new TabInfo(tab.getId(), tab.getTitle(), tab.getUrl(), tab.getOriginalUrl(),
                    tab.getReferrer() != null ? tab.getReferrer().getUrl() : "",
                    tab.getTimestampMillis());
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (other == null) return false;
            if (other instanceof TabInfo) {
                TabInfo otherTabInfo = (TabInfo) other;
                return mId == otherTabInfo.getId()
                        && (mUrl == null ? otherTabInfo.getUrl() == null
                                         : mUrl.equals(otherTabInfo.getUrl()));
            }

            return false;
        }

        @Override
        public int hashCode() {
            int result = 17;
            result = 31 * result + mId;
            result = 31 * result + mUrl == null ? 0 : mUrl.hashCode();
            return result;
        }

        @Override
        public int compareTo(TabInfo other) {
            return Integer.compare(mId, other.getId());
        }
    }

    private final List<TabInfo> mTabsInfo;
    private List<TabGroupInfo> mExistingTabGroups;

    private TabContext(List<TabInfo> tabsInfo, List<TabGroupInfo> groups) {
        mTabsInfo = tabsInfo;
        mExistingTabGroups = groups;
    }

    /**
     * Gets the list of orphan {@link TabInfo}s in the current {@link TabModel}
     */
    public List<TabInfo> getTabsInfo() {
        return mTabsInfo;
    }

    /**
     * Gets the list of {@link TabGroupInfo} in the current {@link TabModel}
     */
    public List<TabGroupInfo> getTabGroups() {
        return mExistingTabGroups;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null) return false;
        if (other instanceof TabContext) {
            TabContext otherTabContext = (TabContext) other;
            return (mExistingTabGroups == null
                                   ? otherTabContext.getTabGroups() == null
                                   : mExistingTabGroups.equals(otherTabContext.getTabGroups()))
                    && (mTabsInfo == null ? otherTabContext.getTabsInfo() == null
                                          : mTabsInfo.equals(otherTabContext.getTabsInfo()));
        }

        return false;
    }

    @Override
    public int hashCode() {
        int result = 17;
        result = 31 * result + (mExistingTabGroups == null ? 0 : mExistingTabGroups.hashCode());
        result = 31 * result + (mTabsInfo == null ? 0 : mTabsInfo.hashCode());
        return result;
    }

    /**
     * Creates an instance of TabContext based on the provided {@link TabModelSelector}.
     * @param tabModelSelector
     * @return an instance of TabContext
     */
    public static TabContext createCurrentContext(TabModelSelector tabModelSelector) {
        TabModelFilter tabModelFilter =
                tabModelSelector.getTabModelFilterProvider().getCurrentTabModelFilter();
        List<TabInfo> tabs = new ArrayList<>();
        List<TabGroupInfo> existingGroups = new ArrayList<>();

        // Examine each tab in the current model and either add it to the list of orphan tabs or add
        // it to a group it belongs to.
        for (int i = 0; i < tabModelFilter.getCount(); i++) {
            Tab currentTab = tabModelFilter.getTabAt(i);

            List<Tab> relatedTabs = tabModelFilter.getRelatedTabList(currentTab.getId());

            if (relatedTabs != null && relatedTabs.size() > 1) {
                existingGroups.add(new TabGroupInfo(createTabInfoList(relatedTabs)));
            } else {
                tabs.add(TabInfo.createFromTab(currentTab));
            }
        }

        return new TabContext(tabs, existingGroups);
    }

    private static List<TabInfo> createTabInfoList(List<Tab> tabs) {
        List<TabInfo> tabInfoList = new ArrayList<>();

        for (Tab tab : tabs) {
            tabInfoList.add(TabInfo.createFromTab(tab));
        }

        return tabInfoList;
    }
}