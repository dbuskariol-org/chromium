// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.toolbar;

import org.chromium.chrome.browser.compositor.layouts.LayoutManager;
import org.chromium.chrome.browser.compositor.layouts.ToolbarSwipeLayout;
import org.chromium.chrome.browser.modelutil.PropertyModel;
import org.chromium.chrome.browser.toolbar.TabStripToolbarButtonSlotData.TabStripToolbarButtonData;
import org.chromium.ui.resources.ResourceManager;

import java.util.List;

/**
 * All of the state for the bottom toolbar, updated by the {@link BottomToolbarCoordinator}.
 */
public class TabStripBottomToolbarModel extends PropertyModel {
    /** The Y offset of the view in px. */
    public static final WritableIntPropertyKey Y_OFFSET = new WritableIntPropertyKey();

    /** Whether the Android view version of the toolbar is visible. */
    public static final WritableBooleanPropertyKey ANDROID_VIEW_VISIBLE =
            new WritableBooleanPropertyKey();

    /** Whether the composited version of the toolbar is visible. */
    public static final WritableBooleanPropertyKey COMPOSITED_VIEW_VISIBLE =
            new WritableBooleanPropertyKey();

    /** A {@link LayoutManager} to attach overlays to. */
    public static final WritableObjectPropertyKey<LayoutManager> LAYOUT_MANAGER =
            new WritableObjectPropertyKey<>();

    /** The browser's {@link ToolbarSwipeLayout}. */
    public static final WritableObjectPropertyKey<ToolbarSwipeLayout> TOOLBAR_SWIPE_LAYOUT =
            new WritableObjectPropertyKey<>();

    /** A {@link ResourceManager} for loading textures into the compositor. */
    public static final WritableObjectPropertyKey<ResourceManager> RESOURCE_MANAGER =
            new WritableObjectPropertyKey<>();

    /** Data used to show the scroll view buttons. */
    public static final WritableObjectPropertyKey<List<TabStripToolbarButtonData>>
            SCROLL_VIEW_DATA = new WritableObjectPropertyKey<>();

    /** Primary color of bottom toolbar. */
    public static final WritableIntPropertyKey PRIMARY_COLOR = new WritableIntPropertyKey();

    /** Default constructor. */
    public TabStripBottomToolbarModel() {
        super(Y_OFFSET, ANDROID_VIEW_VISIBLE, COMPOSITED_VIEW_VISIBLE, LAYOUT_MANAGER,
                TOOLBAR_SWIPE_LAYOUT, RESOURCE_MANAGER, SCROLL_VIEW_DATA, PRIMARY_COLOR);
    }
}
