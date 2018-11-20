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
import org.chromium.base.ThreadUtils;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.base.metrics.RecordUserAction;
import org.chromium.base.task.AsyncTask;
import org.chromium.chrome.browser.compositor.layouts.content.TabContentManager;
import org.chromium.chrome.browser.tab.EmptyTabObserver;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tab.TabObserver;
import org.chromium.chrome.browser.tabmodel.EmptyTabModelObserver;
import org.chromium.chrome.browser.tabmodel.TabList;
import org.chromium.chrome.browser.tabmodel.TabModel;
import org.chromium.chrome.browser.tabmodel.TabModel.TabLaunchType;
import org.chromium.chrome.browser.tabmodel.TabModel.TabSelectionType;
import org.chromium.chrome.browser.tabmodel.TabModelUtils;

import java.util.ArrayList;
import java.util.HashMap;
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
    private static final String PARENT_PREF_NAME_PATTERN = "ParentOf-";
    private static final String FOCUSED_COUNT_FOR_GROUP = "FocusedCountFor-";
    private static SharedPreferences sPref;
    private static int sTabGroupCount;

    private boolean mShouldRecordUma;

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

            if (!mShouldRecordUma && mTabIdList.size() == 2) sTabGroupCount++;
        }

        public void setLastShownTabId(int tabId) {
            mLastShownTabId = tabId;
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
            if (!mShouldRecordUma && mTabIdList.size() == 1) sTabGroupCount--;
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
                addTab(tab);
                int groupId = getGroupId(tab.getId());
                if (!mShouldRecordUma && type == TabLaunchType.FROM_LONGPRESS_BACKGROUND) {
                    if (mTabGroupMap.get(groupId).getTabIdList().size() == 2) {
                        RecordUserAction.record("TabGroup.Created.OpenInNewTab");
                    }

                    // Because long press -> open new tab will automatically load in the background
                    int tabCount = getAllTabIdsInSameGroup(tab.getId()).size();
                    RecordHistogram.recordCountHistogram("TabStrips.TabCountOnPageLoad", tabCount);
                }
            }

            @Override
            public void didCloseTab(int tabId, boolean incognito) {
                Log.e(TAG, "weiyinchen TabGroupList didCloseTab tabId = %d", tabId);
                int groupId = getGroupId(tabId);
                mTabGroupMap.get(groupId).removeTabId(tabId);

                if (mTabGroupMap.get(groupId).getTabIdList().size() == 0) {
                    mTabGroupMap.remove(groupId);
                    updateGroupIdToIndexMap(getGroupIndex(tabId));
                    mGroupIdToIndexMap.remove(groupId);
                    mGroupIndex--;
                }
                dump();
            }

            @Override
            public void didSelectTab(Tab tab, @TabSelectionType int type, int lastId) {
                Log.e(TAG, "weiyinchen TabGroupList didSelectTab %d", tab.getId());
                if (tab.getId() == lastId) return;

                int groupId = getGroupId(tab.getId());
                mTabGroupMap.get(groupId).setLastShownTabId(tab.getId());
            }

        });

        mTabModel.setTabGroupList(this);

        init();
    }

    public TabGroupList(
            TabModel model, TabContentManager tabContentManager, boolean shouldRecordUma) {
        this(model, tabContentManager);
        mShouldRecordUma = shouldRecordUma;
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

    private void saveParentInfo(String key, int parentId) {
        ThreadUtils.assertOnBackgroundThread();

        SharedPreferences prefs = getSharedPreferences();
        if (prefs.getInt(key, Tab.INVALID_TAB_ID) != parentId) {
            // TODO: prune useless entries.
            // TODO: use lazy aggregated write?
            prefs.edit().putInt(key, parentId).apply();
            Log.e(TAG, "weiyinchen write prefs");
        }
    }

    private void saveParentInfo(Tab tab) {
        String key = PARENT_PREF_NAME_PATTERN + Integer.toString(tab.getId());
        int parentId = tab.getParentId();
        AsyncTask.THREAD_POOL_EXECUTOR.execute(() -> saveParentInfo(key, parentId));
    }

    private int getParentId(int tabId) {
        Tab tab = TabModelUtils.getTabById(mTabModel, tabId);
        if (tab != null) return tab.getParentId();

        try (StrictModeContext unused = StrictModeContext.allowDiskReads()) {
            SharedPreferences prefs = getSharedPreferences();
            String key = PARENT_PREF_NAME_PATTERN + Integer.toString(tabId);
            Log.e(TAG, "weiyinchen getParentId read prefs");
            return prefs.getInt(key, Tab.INVALID_TAB_ID);
        }
    }

    private int getGroupId(int tabId) {
        while (true) {
            int parentId = getParentId(tabId);
            if (parentId == Tab.INVALID_TAB_ID) return tabId;
            tabId = parentId;
        }
    }

    private void addTab(Tab tab) {
        // TODO: the ordering of tabs within a group should be preserved.
        // TODO: the ordering of tab groups should be preserved.
        saveParentInfo(tab);
        int groupId = getGroupId(tab.getId());

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

        dump();
    }

    private void dump() {
        Log.e(TAG, "weiyinchen mTabGroupMap = " + mTabGroupMap.toString());
        Log.e(TAG, "weiyinchen mGroupIdToIndexMap = " + mGroupIdToIndexMap.toString());
        Log.e(TAG, "weiyinchen mGroupIndex = " + mGroupIndex);
    }

    private void init() {
        for (int i = 0; i < mTabModel.getCount(); i++) {
            addTab(mTabModel.getTabAt(i));
        }
    }

    public List<Integer> getAllTabIdsInSameGroup(int tabId) {
        TabGroup tabGroup = mTabGroupMap.get(getGroupId(tabId));
        if (tabGroup == null) return null;
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
}
