// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.compositor.layouts.components;

import org.chromium.base.Log;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.chrome.browser.compositor.layouts.content.TabContentManager;
import org.chromium.chrome.browser.compositor.layouts.phone.TabGroupList;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.EmptyTabModelSelectorObserver;
import org.chromium.chrome.browser.tabmodel.TabList;
import org.chromium.chrome.browser.tabmodel.TabModel;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;

import java.util.List;

/**
 * LayoutTabInfoManager for TabGroup UI.
 */
public class TabGroupLayoutTabInfoManager implements LayoutTabInfoManager {
    public TabGroupLayoutTabInfoManager() {}

    @Override
    public void destroy() {
        if (mLayoutTabInfo != null) {
            mLayoutTabInfo.destroy();
            mLayoutTabInfo = null;
        }
        Log.e("weiyinchen", "TabGroupLayoutTabInfoManager.destroy");
    }

    @Override
    public void setTabModelSelector(TabModelSelector modelSelector, TabContentManager manager) {
        mTabModelSelector = modelSelector;
        mTabModelSelector.addObserver(new EmptyTabModelSelectorObserver() {
            @Override
            public void onTabModelSelected(TabModel newModel, TabModel oldModel) {
                mCurrentTabListIndex = mTabModelSelector.getCurrentModel().isIncognito()
                        ? INCOGNITO_TABLIST_INDEX
                        : NORMAL_TABLIST_INDEX;
            }
        });
    }

    @Override
    public LayoutTabInfo getLayoutTabInfo() {
        return mLayoutTabInfo;
    }

    @Override
    public void onTabFocused(Tab tab, boolean shoudRecordUma) {
        Log.i("Meil", "on Focused tab");
        if (mLayoutTabInfo != null) {
            mLayoutTabInfo.destroy();
            mLayoutTabInfo = null;
        }
        Log.e("weiyinchen", "TabGroupLayoutTabInfoManager.onTabFocused");

        if (tab != null) {
            Log.i("Meil", "Create tabgroup layout tab info layer");
            List<Integer> tabIdList = ((TabGroupList) mTabLists.get(mCurrentTabListIndex))
                                              .getAllTabIdsInSameGroup(tab.getId());

            if (shoudRecordUma) {
                int focusedCount = ((TabGroupList) mTabLists.get(mCurrentTabListIndex))
                                           .updateAndGetGroupFocusedCount(tab.getId());
                RecordHistogram.recordCountHistogram("TabGroups.SessionsPerGroup", focusedCount);
            }

            Log.e("weiyinchen",
                    "TabGroupLayoutTabInfoManager.onTabFocused tabIdList = "
                            + tabIdList.toString());
            mLayoutTabInfo = new TabGroupLayoutTabInfo(tab, tabIdList);
        }
    }
    public void setTabLists(List<TabList> tabLists) {
        mTabLists = tabLists;
    }

    private TabModelSelector mTabModelSelector;
    private LayoutTabInfo mLayoutTabInfo;
    private List<TabList> mTabLists;
    private int mCurrentTabListIndex;
    public static final int NORMAL_TABLIST_INDEX = 0;
    public static final int INCOGNITO_TABLIST_INDEX = 1;
}
