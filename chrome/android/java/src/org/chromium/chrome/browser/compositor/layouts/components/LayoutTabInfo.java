// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.compositor.layouts.components;

import org.chromium.chrome.browser.compositor.layouts.content.TabContentManager;
import org.chromium.ui.resources.ResourceManager;

/**
 * Interface for components that want to display extra information below a tab
 * in the tab switcher UI.
 */
public interface LayoutTabInfo {
    /** Callback interface used to change tab focus in the tab switcher UI */
    public interface ClickCallback { public void run(int tabId); }

    /**
     * Called when this object is being destroyed.
     */
    public void destroy();

    /**
     * Checks to see if the coordinates correspond to a control and if so triggers
     * logic to handle the click.
     *
     * @return True if the coordinates resulted in a click on an control managed
     * by this object. Otherwise the click was not processed by this object
     * and should be forwarded to other objects for handling.
     */
    public boolean checkForClick(int x, int y, ClickCallback switchCallback,
            ClickCallback closeCallback, ClickCallback newTabInGroupCallback);

    /**
     * Checks to see if the coordinates correspond to a control and if so triggers
     * logic to handle the long press.
     *
     * @return True if the coordinates resulted in a long press on an control
     * managed by this object. Otherwise the long press was not processed by this
     * object and should be forwarded to other objects for handling.
     */
    public boolean checkForLongPress(int x, int y);

    /**
     * Computes the layout for the UI managed by this component.
     *
     * @param startingXOfffsetInPixels Starting x offset of upper left corner of the region
     * available for rendering.
     * @param startingYOffsetInPixels Starting y offset of upper left corner of the region available
     * for rendering.
     * @param widthInPixels Width of the region available for rendering.
     *
     * @return The ending Y offset in pixels for the bottom of LayoutTabInfo content.
     * Note: The value returned MUST be >= startingYOffsetInPixels.
     */
    public int computeLayout(
            int startingXOfffsetInPixels, int startingYOffsetInPixels, int widthInPixels);

    /**
     * Updates the tabInfoLayer stored in the TabContentManager.
     */
    public void updateLayer(TabContentManager tabContentManager, ResourceManager resourceManager);
}
