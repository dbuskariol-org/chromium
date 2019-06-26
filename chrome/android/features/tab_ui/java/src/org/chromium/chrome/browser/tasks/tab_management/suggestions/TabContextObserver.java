// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tasks.tab_management.suggestions;

import org.chromium.chrome.browser.lifecycle.Destroyable;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.EmptyTabModelObserver;
import org.chromium.chrome.browser.tabmodel.TabModelObserver;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.tabmodel.TabModelSelectorTabObserver;

/**
 * Monitors changes to a TabContext and executes a callback function if there are changes.
 */
public class TabContextObserver implements Destroyable {
    private TabModelSelectorTabObserver mTabModelSelectorTabObserver;
    private TabModelObserver mTabModelObserver;
    private TabModelSelector mTabModelSelector;

    public TabContextObserver(TabModelSelector selector, Runnable runnable) {
        mTabModelSelectorTabObserver = new TabModelSelectorTabObserver(selector) {
            @Override
            public void didFirstVisuallyNonEmptyPaint(Tab tab) {
                runnable.run();
            }
        };

        mTabModelObserver = new EmptyTabModelObserver() {
            @Override
            public void didAddTab(Tab tab, int type) {
                runnable.run();
            }

            @Override
            public void didMoveTab(Tab tab, int newIndex, int curIndex) {
                runnable.run();
            }

            @Override
            public void didCloseTab(int tabId, boolean incognito) {
                runnable.run();
            }
        };
        selector.getTabModelFilterProvider().addTabModelFilterObserver(mTabModelObserver);
        mTabModelSelector = selector;
    }

    @Override
    public void destroy() {
        mTabModelSelector.getTabModelFilterProvider().removeTabModelFilterObserver(
                mTabModelObserver);
        mTabModelSelectorTabObserver.destroy();
    }
}
