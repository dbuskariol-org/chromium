// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.compositor.layouts.components;

import org.chromium.chrome.browser.compositor.layouts.content.TabContentManager;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;

/**
 * Manages state for information that is rendered under the tab in the tab switcher ui.
 */
public interface LayoutTabInfoManager {
    /**
     * Called when this object is being destroyed.
     */
    public void destroy();

    /**
     * Notifies this object what TabModelSelector and TabContentManager should be used.
     */
    public void setTabModelSelector(TabModelSelector modelSelector, TabContentManager manager);

    /**
     * Gets the currently selected LayoutTabInfo that should be rendered under the tab.
     */
    public LayoutTabInfo getLayoutTabInfo();

    /**
     * Called when a new tab comes into becomes the focus of the tab selector UI.
     */
    public void onTabFocused(Tab tab, boolean shouldRecordUma);
}
