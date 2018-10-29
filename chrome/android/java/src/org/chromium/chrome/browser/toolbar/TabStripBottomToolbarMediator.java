// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.toolbar;

import android.content.res.Resources;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.compositor.bottombar.OverlayPanelManager.OverlayPanelManagerObserver;
import org.chromium.chrome.browser.compositor.layouts.Layout;
import org.chromium.chrome.browser.compositor.layouts.LayoutManager;
import org.chromium.chrome.browser.compositor.layouts.OverviewModeBehavior;
import org.chromium.chrome.browser.compositor.layouts.OverviewModeBehavior.OverviewModeObserver;
import org.chromium.chrome.browser.compositor.layouts.SceneChangeObserver;
import org.chromium.chrome.browser.compositor.layouts.ToolbarSwipeLayout;
import org.chromium.chrome.browser.fullscreen.ChromeFullscreenManager;
import org.chromium.chrome.browser.fullscreen.ChromeFullscreenManager.FullscreenListener;
import org.chromium.chrome.browser.toolbar.TabStripToolbarButtonSlotData.TabStripToolbarButtonData;
import org.chromium.ui.KeyboardVisibilityDelegate;
import org.chromium.ui.base.WindowAndroid;
import org.chromium.ui.resources.ResourceManager;

import java.util.List;

/**
 * This class is responsible for reacting to events from the outside world, interacting with other
 * coordinators, running most of the business logic associated with the bottom toolbar, and updating
 * the model accordingly.
 */
class TabStripBottomToolbarMediator
        implements FullscreenListener, KeyboardVisibilityDelegate.KeyboardVisibilityListener,
                   OverlayPanelManagerObserver, OverviewModeObserver, SceneChangeObserver {
    /** The model for the bottom toolbar that holds all of its state. */
    private TabStripBottomToolbarModel mModel;

    /** The fullscreen manager to observe browser controls events. */
    private ChromeFullscreenManager mFullscreenManager;

    /** The overview mode manager. */
    private OverviewModeBehavior mOverviewModeBehavior;

    /** A {@link WindowAndroid} for watching keyboard visibility events. */
    private WindowAndroid mWindowAndroid;

    /** The previous height of the bottom toolbar. */
    private int mBottomToolbarHeightBeforeHide;

    /** Whether the swipe layout is currently active. */
    private boolean mIsInSwipeLayout;

    /** The data required to fill in the bottom toolbar scrool view buttons slot.*/
    private List<TabStripToolbarButtonData> mScrollViewData;

    private boolean mKeepToolbarDisabled;

    private boolean mInOverviewMode;

    /**
     * Build a new mediator that handles events from outside the bottom toolbar.
     * @param model The {@link TabStripBottomToolbarModel} that holds all the state for the bottom
     * toolbar.
     * @param fullscreenManager A {@link ChromeFullscreenManager} for events related to the browser
     *                          controls.
     * @param resources Android {@link Resources} to pull dimensions from.
     * @param firstSlotData The data required to fill in the first bottom toolbar button slot.
     * @param secondSlotData The data required to fill in the second bottom toolbar button slot.
     * @param primaryColor The initial color for the bottom toolbar.
     */
    TabStripBottomToolbarMediator(TabStripBottomToolbarModel model,
            ChromeFullscreenManager fullscreenManager, Resources resources, int primaryColor) {
        mModel = model;
        mFullscreenManager = fullscreenManager;
        mFullscreenManager.addListener(this);

        // Notify the fullscreen manager that the bottom controls now have a height.
        fullscreenManager.setBottomControlsHeight(
                resources.getDimensionPixelOffset(R.dimen.bottom_toolbar_height));
        fullscreenManager.updateViewportSize();

        mModel.set(TabStripBottomToolbarModel.PRIMARY_COLOR, primaryColor);
    }

    /**
     * Clean up anything that needs to be when the bottom toolbar is destroyed.
     */
    void destroy() {
        mFullscreenManager.removeListener(this);
        if (mOverviewModeBehavior != null) mOverviewModeBehavior.removeOverviewModeObserver(this);
        if (mWindowAndroid != null)
            mWindowAndroid.getKeyboardDelegate().removeKeyboardVisibilityListener(this);
        if (mModel.get(TabStripBottomToolbarModel.LAYOUT_MANAGER) != null) {
            LayoutManager manager = mModel.get(TabStripBottomToolbarModel.LAYOUT_MANAGER);
            manager.getOverlayPanelManager().removeObserver(this);
            manager.removeSceneChangeObserver(this);
        }
    }

    @Override
    public void onContentOffsetChanged(float offset) {}

    @Override
    public void onControlsOffsetChanged(float topOffset, float bottomOffset, boolean needsAnimate) {
        mModel.set(TabStripBottomToolbarModel.Y_OFFSET, (int) bottomOffset);
        if (topOffset != 0
                && (bottomOffset > 0 || mFullscreenManager.getBottomControlsHeight() == 0)) {
            mModel.set(TabStripBottomToolbarModel.ANDROID_VIEW_VISIBLE, false);
        } else {
            tryShowingAndroidView();
        }
    }

    @Override
    public void onToggleOverlayVideoMode(boolean enabled) {}

    @Override
    public void onBottomControlsHeightChanged(int bottomControlsHeight) {}

    @Override
    public void onOverviewModeStartedShowing(boolean showToolbar) {
        mInOverviewMode = true;
        disableToolbar(false);
    }

    @Override
    public void onOverviewModeFinishedShowing() {}

    @Override
    public void onOverviewModeStartedHiding(boolean showToolbar, boolean delayAnimation) {}

    @Override
    public void onOverviewModeFinishedHiding() {
        mInOverviewMode = false;
        maybeEnableToolbar();
    }

    @Override
    public void onOverlayPanelShown() {
        mModel.set(TabStripBottomToolbarModel.ANDROID_VIEW_VISIBLE, false);
    }

    @Override
    public void onOverlayPanelHidden() {
        tryShowingAndroidView();
    }

    @Override
    public void keyboardVisibilityChanged(boolean isShowing) {
        // The toolbars are force shown when the keyboard is visible, so we can blindly set
        // the bottom toolbar view to visible or invisible regardless of the previous state.
        if (isShowing) {
            mBottomToolbarHeightBeforeHide = mFullscreenManager.getBottomControlsHeight();
            mModel.set(TabStripBottomToolbarModel.ANDROID_VIEW_VISIBLE, false);
            mModel.set(TabStripBottomToolbarModel.COMPOSITED_VIEW_VISIBLE, false);
            mFullscreenManager.setBottomControlsHeight(0);
        } else {
            maybeEnableToolbar();
        }
    }

    private void disableToolbar(boolean keepToolbarDisabled) {
        mKeepToolbarDisabled = keepToolbarDisabled;
        mModel.set(TabStripBottomToolbarModel.SCROLL_VIEW_DATA, null);
        mBottomToolbarHeightBeforeHide = mFullscreenManager.getBottomControlsHeight();
        mModel.set(TabStripBottomToolbarModel.ANDROID_VIEW_VISIBLE, false);
        mModel.set(TabStripBottomToolbarModel.COMPOSITED_VIEW_VISIBLE, false);
        mFullscreenManager.setBottomControlsHeight(0);
    }

    private void maybeEnableToolbar() {
        if (mKeepToolbarDisabled || mInOverviewMode) return;
        mFullscreenManager.setBottomControlsHeight(mBottomToolbarHeightBeforeHide);
        tryShowingAndroidView();
        mModel.set(TabStripBottomToolbarModel.Y_OFFSET,
                (int) mFullscreenManager.getBottomControlOffset());
    }

    /**
     * Try showing the toolbar's Android view after it has been hidden. This accounts for cases
     * where a browser signal would ordinarily re-show the view, but others still require it to be
     * hidden.
     */
    private void tryShowingAndroidView() {
        if (mKeepToolbarDisabled) return;
        if (mFullscreenManager.getBottomControlOffset() > 0) return;
        if (mModel.get(TabStripBottomToolbarModel.Y_OFFSET) != 0) return;
        mModel.set(TabStripBottomToolbarModel.ANDROID_VIEW_VISIBLE, true);
    }

    void setLayoutManager(LayoutManager layoutManager) {
        mModel.set(TabStripBottomToolbarModel.LAYOUT_MANAGER, layoutManager);
        layoutManager.addSceneChangeObserver(this);
        layoutManager.getOverlayPanelManager().addObserver(this);
    }

    @Override
    public void onTabSelectionHinted(int tabId) {}

    @Override
    public void onSceneChange(Layout layout) {
        //        if (layout instanceof ToolbarSwipeLayout) {
        //            mIsInSwipeLayout = true;
        //            mModel.set(TabStripBottomToolbarModel.ANDROID_VIEW_VISIBLE, false);
        //        } else if (mIsInSwipeLayout) {
        //            // Only change to visible if leaving the swipe layout.
        //            mIsInSwipeLayout = false;
        //            mModel.set(TabStripBottomToolbarModel.ANDROID_VIEW_VISIBLE, true);
        //        }
    }

    void setResourceManager(ResourceManager resourceManager) {
        mModel.set(TabStripBottomToolbarModel.RESOURCE_MANAGER, resourceManager);
    }

    void setOverviewModeBehavior(OverviewModeBehavior overviewModeBehavior) {
        mOverviewModeBehavior = overviewModeBehavior;
        mOverviewModeBehavior.addOverviewModeObserver(this);
    }

    void setToolbarSwipeLayout(ToolbarSwipeLayout layout) {
        mModel.set(TabStripBottomToolbarModel.TOOLBAR_SWIPE_LAYOUT, layout);
    }

    void setWindowAndroid(WindowAndroid windowAndroid) {
        assert mWindowAndroid == null : "#setWindowAndroid should only be called once per toolbar.";
        // Watch for keyboard events so we can hide the bottom toolbar when the keyboard is showing.
        mWindowAndroid = windowAndroid;
        mWindowAndroid.getKeyboardDelegate().addKeyboardVisibilityListener(this);
    }

    void setScrollViewData(List<TabStripToolbarButtonData> scrollViewData) {
        mScrollViewData = scrollViewData;
        mModel.set(TabStripBottomToolbarModel.SCROLL_VIEW_DATA, mScrollViewData);

        if (scrollViewData == null || scrollViewData.isEmpty()) {
            disableToolbar(true);
        } else {
            mKeepToolbarDisabled = false;
            maybeEnableToolbar();
        }
    }

    void setPrimaryColor(int color) {
        mModel.set(TabStripBottomToolbarModel.PRIMARY_COLOR, color);
    }
}
