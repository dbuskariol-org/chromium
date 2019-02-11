// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.compositor.layouts.phone;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;

import org.chromium.base.ContextUtils;
import org.chromium.base.Log;
import org.chromium.base.StrictModeContext;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.browser.compositor.layouts.content.TabContentManager;
import org.chromium.chrome.browser.tab.EmptyTabObserver;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tab.TabObserver;
import org.chromium.chrome.browser.tabmodel.EmptyTabModelObserver;
import org.chromium.chrome.browser.tabmodel.TabList;
import org.chromium.chrome.browser.tabmodel.TabModel;
import org.chromium.chrome.browser.tabmodel.TabLaunchType;
import org.chromium.chrome.browser.tabmodel.TabSelectionType;
import org.chromium.chrome.browser.tabmodel.TabModelImpl;
import org.chromium.chrome.browser.tabmodel.TabModelObserver;
import org.chromium.chrome.browser.tabmodel.TabModelUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// TODO(meiliang): fix the JavaDoc
/**
 * JavaDoc place holder
 */
public class TabGroupList implements TabList {
    public final int INVALID_TAB_GROUP_INDEX = -1;
    private static final String TAG = "TabGroupList";
    private static final String PREFS_FILE = "tab_group_list_pref";
    private static final String FOCUSED_COUNT_FOR_GROUP = "FocusedCountFor-";
    private static SharedPreferences sPref;
    private static int sTabGroupCount;

    private TabModel mTabModel;
    private TabContentManager mTabContentManager;
    private TabObserver mTabObserver = new EmptyTabObserver() {
        @Override
        public void onFaviconUpdated(Tab tab, Bitmap icon) {
            mTabContentManager.updateTabGroupTabFavicon(tab.getId(), tab.getUrl());
        }

        @Override
        public void onTitleUpdated(Tab tab) {
            mTabContentManager.updateTabGroupTabTitle(tab.getId(), tab.getTitle());
        }

        @Override
        public void onUrlUpdated(Tab tab) {
            mTabContentManager.updateTabGroupTabUrl(tab.getId(), tab.getUrl());
        }
    };

    private class TabGroup {
        private final Set<Integer> mTabIdList;
        private int mLastShownTabId;

        public TabGroup() {
            mTabIdList = new LinkedHashSet<>();
            mLastShownTabId = Tab.INVALID_TAB_ID;
        }

        public void addTabId(int tabId) {
            mTabIdList.add(tabId);
            Tab tab = TabModelUtils.getTabById(mTabModel, tabId);
            tab.addObserver(mTabObserver);

            mTabContentManager.cacheTabAsTabGroupTab(tab.getId(), tab.getUrl(), tab.getTitle());

            if (mTabIdList.size() == 2) sTabGroupCount++;
        }

        public void setLastShownTabId(int tabId) {
            mLastShownTabId = tabId;
        }

        public void setTabIds(List<Integer> tabIds) {
            mTabIdList.clear();
            mTabIdList.addAll(tabIds);
        }

        public List<Integer> getTabIdList() {
            return new ArrayList<>(mTabIdList);
        }

        public void removeTabId(int tabId) {
            if (mLastShownTabId == tabId) {
                List<Integer> idList = getTabIdList();
                int position = idList.indexOf(tabId);
                if (idList.size() <= 1 || position == -1)
                    mLastShownTabId = Tab.INVALID_TAB_ID;
                else if (position == 0)
                    mLastShownTabId = idList.get(position + 1);
                else
                    mLastShownTabId = idList.get(position - 1);
            }
            Log.e(TAG, "weiyinchen removeTabId %d, mLastShownTabId = %d", tabId, mLastShownTabId);

            boolean found = mTabIdList.remove(tabId);
            assert found;
            mTabContentManager.removeTabGroupTabFromCache(tabId);
            if (mTabIdList.size() == 1) sTabGroupCount--;
        }

        public boolean contains(int tabId) {
            return mTabIdList.contains(tabId);
        }

        @Override
        public String toString() {
            return "(" + mTabIdList.toString() + " last = " + mLastShownTabId + ")";
        }
    }

    private Map<Integer, TabGroup> mTabGroupMap = new HashMap<>();
    private Map<Integer, Integer> mGroupIdToIndexMap = new HashMap<>();

    private int mGroupIndex;

    public TabGroupList(TabModel model, TabContentManager tabContentManager) {
        assert !(model instanceof TabGroupList);

        mTabModel = model;
        mTabContentManager = tabContentManager;

        mTabModel.addObserver(new EmptyTabModelObserver() {
            @Override
            public void didAddTab(Tab tab, @TabLaunchType int type) {
                Log.e(TAG, "weiyinchen TabGroupList didAddTab tabId = %d", tab.getId());
                onTabAdded(tab);
                int groupId = tab.getRootId();
                if (type == TabLaunchType.FROM_LONGPRESS_BACKGROUND) {
                    if (mTabGroupMap.get(groupId).getTabIdList().size() == 2) {
                        RecordUserAction.record("TabGroup.Created.OpenInNewTab");
                    }

                    // Because long press -> open new tab will automatically load in the background
                    int tabCount = getAllTabIdsInSameGroup(tab.getId()).size();
                    RecordHistogram.recordCountHistogram("TabStrips.TabCountOnPageLoad", tabCount);
                }
            }

            @Override
            public void didSelectTab(Tab tab, @TabSelectionType int type, int lastId) {
                Log.e(TAG, "weiyinchen TabGroupList didSelectTab %d", tab.getId());
                if (tab.getId() == lastId) return;
                dump();
                int groupId = tab.getRootId();
                mTabGroupMap.get(groupId).setLastShownTabId(tab.getId());
            }
        });

        init();
    }

    private void updateGroupIdToIndexMap(int removeGroupIndex) {
        Set<Integer> groupIdSet = mGroupIdToIndexMap.keySet();
        for (Integer key : groupIdSet) {
            int oldGroupIndex = mGroupIdToIndexMap.get(key);
            if (oldGroupIndex > removeGroupIndex) {
                mGroupIdToIndexMap.put(key, oldGroupIndex - 1);
            }
        }
    }

    private int generateNextGroupIndex() {
        return mGroupIndex++;
    }

    private SharedPreferences getSharedPreferences() {
        if (sPref == null) {
            sPref = ContextUtils.getApplicationContext().getSharedPreferences(
                    PREFS_FILE, Context.MODE_PRIVATE);
            Log.e(TAG, "weiyinchen getSharedPreferences");
        }
        return sPref;
    }

    private int getGroupId(int tabId) {
        for (int groupId : mTabGroupMap.keySet()) {
            if (mTabGroupMap.get(groupId).contains(tabId)) return groupId;
        }

        return tabId;
    }

    private void onTabAdded(Tab tab) {
        int groupId = tab.getRootId();

        if (mTabGroupMap.containsKey(groupId)) {
            // add tab to an existing group
            mTabGroupMap.get(groupId).addTabId(tab.getId());
        } else {
            // create new group
            TabGroup tabGroup = new TabGroup();
            tabGroup.addTabId(tab.getId());
            int tabGroupIndex = generateNextGroupIndex();
            mTabGroupMap.put(groupId, tabGroup);
            mGroupIdToIndexMap.put(groupId, tabGroupIndex);
        }

        if (mTabGroupMap.get(groupId).mLastShownTabId == Tab.INVALID_TAB_ID)
            mTabGroupMap.get(groupId).setLastShownTabId(tab.getId());

        reorderGroups();
        dump();
    }

    private void reorderGroups() {
        Set<Integer> knownIds = new HashSet<>();
        for (int groupId : mTabGroupMap.keySet()) {
            knownIds.addAll(mTabGroupMap.get(groupId).getTabIdList());
        }

        int count = mTabModel.getCount();
        Map<Integer, List<Integer>> tabGroups = new HashMap<>();
        int position = 0;
        Set<Integer> seenGroups = new HashSet<>();
        for (int i = 0; i < count; i++) {
            Tab tab = mTabModel.getTabAt(i);

            // mTabModel might have tabs not added through addTab().
            if (!knownIds.contains(tab.getId())) continue;

            int groupId = tab.getRootId();
            if (!seenGroups.contains(groupId)) {
                seenGroups.add(groupId);
                mGroupIdToIndexMap.put(groupId, position);
                tabGroups.put(groupId, new ArrayList<>());
                position++;
            }
            tabGroups.get(groupId).add(tab.getId());
        }
        assert position == getCount();

        for (int groupId : tabGroups.keySet()) {
            List<Integer> tabIds = tabGroups.get(groupId);
            assert tabIds.size() == mTabGroupMap.get(groupId).getTabIdList().size();
            mTabGroupMap.get(groupId).setTabIds(tabIds);
        }
    }

    private void dump() {
        Log.e(TAG, "weiyinchen mTabGroupMap = " + mTabGroupMap.toString());
        Log.e(TAG, "weiyinchen mGroupIdToIndexMap = " + mGroupIdToIndexMap.toString());
        Log.e(TAG, "weiyinchen mGroupIndex = " + mGroupIndex);
    }

    private void init() {
        for (int i = 0; i < mTabModel.getCount(); i++) {
            onTabAdded(mTabModel.getTabAt(i));
        }
    }

    public List<Integer> getAllTabIdsInSameGroup(int tabId) {
        TabGroup tabGroup = mTabGroupMap.get(getGroupId(tabId));
        if (tabGroup == null) return new ArrayList<>();
        return tabGroup.getTabIdList();
    }

    /**
     * @return Whether this tab group list contains only incognito tabs or only normal tabs.
     */
    @Override
    public boolean isIncognito() {
        return mTabModel.isIncognito();
    }

    /**
     * @return The group index of the current tab, or {@link #INVALID_TAB_GROUP_INDEX} if the
     * current tab is {@link Tab#INVALID_TAB_ID}.
     */
    @Override
    public int index() {
        int currentTabId = TabModelUtils.getCurrentTabId(mTabModel);

        if (currentTabId == Tab.INVALID_TAB_ID) return INVALID_TAB_GROUP_INDEX;

        return getGroupIndex(currentTabId);
    }

    // This needs to happen before tabClosureUndone event.
    public void cancelTabClosure(Tab tab) {
        Log.e(TAG, "weiyinchen TabGroupList cancelTabClosure tabId = %d", tab.getId());
        onTabAdded(tab);
    }

    /**
     * This cannot be at {@link TabModelObserver#willCloseTab} event because this needs to be after
     * {@link TabModelImpl#getNextTabIfClosed}. This cannot be at
     * {@link TabModelObserver#tabPendingClosure} event, either, because of racing condition with
     * tab strip.
     */
    public void onTabClosed(Tab tab) {
        int tabId = tab.getId();
        Log.e(TAG, "weiyinchen TabGroupList onTabClosed tabId = %d", tabId);
        int groupId = tab.getRootId();
        mTabGroupMap.get(groupId).removeTabId(tabId);

        if (mTabGroupMap.get(groupId).getTabIdList().size() == 0) {
            mTabGroupMap.remove(groupId);
            updateGroupIdToIndexMap(mGroupIdToIndexMap.get(groupId));
            mGroupIdToIndexMap.remove(groupId);
            mGroupIndex--;
        }
        dump();
    }

    public void onTabMoved() {
        reorderGroups();
        dump();
    }

    private int getGroupIndex(int tabId) {
        int groupId = getGroupId(tabId);
        if (!mGroupIdToIndexMap.containsKey(groupId)) return INVALID_TAB_GROUP_INDEX;

        return mGroupIdToIndexMap.get(groupId);
    }

    /**
     * @return the number of tab groups.
     */
    @Override
    public int getCount() {
        return mTabGroupMap.size();
    }

    /**
     * Get the last shown tab at the specified position.
     *
     * @param index The index of the tab group.
     * @return The last shown {@code Tab} at group position {@code index}, or {@code null} if {@code
     * index} < 0 or {@code index} >= {@link #getCount()}.
     */
    @Override
    public Tab getTabAt(int index) {
        if (index < 0 || index >= getCount()) return null;

        // TODO: add an index-to-id map?
        int groupId = Tab.INVALID_TAB_ID;
        Set<Integer> groupIdSet = mGroupIdToIndexMap.keySet();
        for (Integer key : groupIdSet) {
            if (mGroupIdToIndexMap.get(key) == index) {
                groupId = key;
                break;
            }
        }
        if (groupId == Tab.INVALID_TAB_ID || !mTabGroupMap.containsKey(groupId)) {
            Log.e(TAG, "weiyinchen getTabAt index = %d", index);
            dump();
            return null;
        }

        return TabModelUtils.getTabById(mTabModel, mTabGroupMap.get(groupId).mLastShownTabId);
    }

    /**
     * @return group index of the given tab.
     */
    @Override
    public int indexOf(Tab tab) {
        return getGroupIndex(tab.getId());
    }

    /**
     * @param tabId The id of the {@link Tab} that might have a pending closure.
     * @return      Whether or not the {@link Tab} specified by {@code tabId} has a pending
     *              closure.
     */
    @Override
    public boolean isClosurePending(int tabId) {
        return mTabModel.isClosurePending(tabId);
    }

    public static void recordTabGroupCount() {
        RecordHistogram.recordCountHistogram("TabGroups.UserTabGroupCount", sTabGroupCount);
    }

    public int updateAndGetGroupFocusedCount(int tabId) {
        int groupId = getGroupId(tabId);
        String key = FOCUSED_COUNT_FOR_GROUP + Integer.toString(groupId);
        int focusedCount = 0;
        try (StrictModeContext unused = StrictModeContext.allowDiskWrites()) {
            SharedPreferences prefs = getSharedPreferences();
            focusedCount = prefs.getInt(key, 0);
            focusedCount++;
            prefs.edit().putInt(key, focusedCount).apply();
        }

        return focusedCount;
    }

    public int getLastShownTabInGroup(int groupId) {
        if (mTabGroupMap.get(groupId) == null) return INVALID_TAB_GROUP_INDEX;
        return mTabGroupMap.get(groupId).mLastShownTabId;
    }

    public int getTabGroupId(int tabId) {
        return getGroupId(tabId);
    }
}
