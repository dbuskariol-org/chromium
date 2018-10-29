// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.compositor.layouts.components;

import android.content.Context;
import android.graphics.RectF;

import org.chromium.base.ContextUtils;
import org.chromium.base.Log;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.compositor.layouts.content.TabContentManager;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.ui.resources.ResourceManager;

import java.util.List;

/**
 * LayoutTabInfo for TabGroup UI.
 */
@JNINamespace("android")
public class TabGroupLayoutTabInfo implements LayoutTabInfo {
    public static final int CLOSE_MASK = 0x40000000;

    // Height of the "Create Tab Group" button in dp units.
    private static final int CREATE_TAB_GROUP_BUTTON_HEIGHT = 50;

    private long mNativePtr;
    private float mDpToPx;
    private int mWidthInPixels = 0;
    private int mLabelYOffsetInPixels = 0;
    private final int mFocusedTabId;

    protected LayoutTabGroupTab[] mLayoutTabGroupTabs = new LayoutTabGroupTab[0];

    public TabGroupLayoutTabInfo(Tab tab, List<Integer> tabIdsInSameGroup) {
        Context context = ContextUtils.getApplicationContext();
        mDpToPx = context.getResources().getDisplayMetrics().density;

        initializeNative();
        LayoutTabGroupTab.resetDimensionConstants(context);
        mLayoutTabGroupTabs = LayoutTabGroupTab.getTabGroupTabs(tabIdsInSameGroup);
        mFocusedTabId = tab.getId();
    }

    @Override
    public int computeLayout(
            int startingXOffsetInPixels, int startingYOffsetInPixels, int widthInPixels) {
        Log.i("Meil", "computeLayout");
        mWidthInPixels = widthInPixels;
        mLabelYOffsetInPixels = (int) (startingYOffsetInPixels + 13 * mDpToPx);
        final int tabYOffsetInPixels = (int) (mLabelYOffsetInPixels + 25 * mDpToPx);
        int endingYOffsetInPixels = tabYOffsetInPixels;
        if (mLayoutTabGroupTabs != null && mLayoutTabGroupTabs.length > 2) {
            RectF gridBoundsInPixels = LayoutTabGroupTab.putTabGroupTabOnGrid(
                    mLayoutTabGroupTabs, widthInPixels, tabYOffsetInPixels);
            endingYOffsetInPixels = (int) gridBoundsInPixels.bottom;
        } else {
            // TODO: Fix this so the value is returned from native code so it can't get out of sync.
            endingYOffsetInPixels =
                    (int) (mLabelYOffsetInPixels + CREATE_TAB_GROUP_BUTTON_HEIGHT * mDpToPx);
        }
        nativeOnComputeLayout(mNativePtr);
        return endingYOffsetInPixels;
    }

    @Override
    public void updateLayer(TabContentManager tabContentManager, ResourceManager resourceManager) {
        Log.i("Meil", "updateLayer");
        LayoutTabGroupTab[] tabGroupTabs = getLayoutTabGroupTabsToRender();

        if (tabGroupTabs.length > 2) {
            nativePutTabGroupLayerLabel(
                    mNativePtr, tabContentManager, mWidthInPixels, mLabelYOffsetInPixels);
            for (int i = 0; i < tabGroupTabs.length - 1; i++) {
                LayoutTabGroupTab tab = tabGroupTabs[i];
                nativePutTabGroupLayer(mNativePtr, tabContentManager, resourceManager,
                        R.drawable.btn_delete_24dp, R.drawable.ic_pin, 36 * mDpToPx, tab.getId(),
                        tab.isPinned(), tab.getX(), tab.getY(), tab.getId() == mFocusedTabId);
            }
            LayoutTabGroupTab addTab = tabGroupTabs[tabGroupTabs.length - 1];
            nativePutTabGroupAddTabLayer(mNativePtr, tabContentManager, resourceManager,
                    R.drawable.shortcut_newtab, addTab.getX(), addTab.getY());
        } else {
            Log.i("Meil", "Put creation layer at " + mWidthInPixels + ", " + mLabelYOffsetInPixels);
            nativePutTabGroupCreationLayer(
                    mNativePtr, tabContentManager, mWidthInPixels, mLabelYOffsetInPixels);
        }

        nativeUpdateLayer(mNativePtr, tabContentManager, resourceManager);
    }

    @Override
    public boolean checkForClick(int x, int y, ClickCallback tabSwitchCallback,
            ClickCallback closeCallback, ClickCallback newTabInGroupCallback) {
        if (mLayoutTabGroupTabs.length == 2 && hitTestCreateTabGroup(x, y)) {
            RecordUserAction.record("TabGroup.Created.TabSwitcher");
            newTabInGroupCallback.run(mFocusedTabId);
            return true;
        }

        int tabGroupTabHit = hitTestTabGroupTabs(x, y, true);
        Log.e("weiyinchen", "checkForClick, hit = %d", tabGroupTabHit);

        if (tabGroupTabHit == -1) return false;

        boolean isClose = (tabGroupTabHit & CLOSE_MASK) != 0;
        tabGroupTabHit &= (~CLOSE_MASK);
        Log.e("weiyinchen", "checkForClick, hit = %d, isClose = %b", tabGroupTabHit, isClose);

        int tabId = (int) mLayoutTabGroupTabs[tabGroupTabHit].getId();

        if (isClose) {
            RecordUserAction.record("TabGroupSwitcher.CloseTab");
            closeCallback.run(tabId);
        } else {
            if (tabId == LayoutTabGroupTab.NEW_TAB_IN_GROUP_ID) {
                RecordUserAction.record("TabGroupSwitcher.NewTab");
                newTabInGroupCallback.run(mFocusedTabId);
            } else {
                RecordUserAction.record("TabGroupSwitcher.SwitchTab");
                tabSwitchCallback.run(tabId);
            }
        }
        return true;
    }

    @Override
    public boolean checkForLongPress(int x, int y) {
        int tabGroupTabHit = hitTestTabGroupTabs(x, y, false);
        if (tabGroupTabHit == -1) return false;
        // TODO(meiliang) : add action for onLongPress
        tabGroupTabHit &= (~CLOSE_MASK);
        return true;
    }

    private LayoutTabGroupTab[] getLayoutTabGroupTabsToRender() {
        return mLayoutTabGroupTabs;
    }

    private int hitTestTabGroupTabs(float x, float y, boolean isClick) {
        int count = mLayoutTabGroupTabs == null ? 0 : mLayoutTabGroupTabs.length;
        int hit = -1;
        // We only show tab group entries when there are more than one.
        if (count <= 1) return hit;
        for (int i = 0; i < count; i++) {
            if (mLayoutTabGroupTabs[i].computeDistanceTo(x, y) == 0) {
                hit = i;
                if (mLayoutTabGroupTabs[i].computeDistanceToClose(x, y) == 0) {
                    hit |= CLOSE_MASK;
                }
                break;
            }
        }
        return hit;
    }

    private boolean hitTestCreateTabGroup(int x, int y) {
        RectF bounds = new RectF();
        bounds.top = mLabelYOffsetInPixels;
        bounds.bottom = bounds.top + CREATE_TAB_GROUP_BUTTON_HEIGHT * mDpToPx;
        bounds.left = mWidthInPixels / 2 - 100 * mDpToPx;
        bounds.right = mWidthInPixels / 2 + 100 * mDpToPx;

        float dx = Math.max(bounds.left - x, x - bounds.right);
        float dy = Math.max(bounds.top - y, y - bounds.bottom);

        return Math.max(0.0f, Math.max(dx, dy)) == 0;
    }

    /**
     * Initializes the native component of this object.  Must be
     * overridden to have a custom native component.
     */
    private void initializeNative() {
        if (mNativePtr == 0) {
            mNativePtr = nativeInit();
        }
        assert mNativePtr != 0;
    }

    /**
     * Destroys this object and the corresponding native component.
     */
    @Override
    public void destroy() {
        assert mNativePtr != 0;
        nativeDestroy(mNativePtr);
        mNativePtr = 0;
    }

    @CalledByNative
    private long getNativePtr() {
        return mNativePtr;
    }

    private native long nativeInit();
    private native void nativeDestroy(long nativeTabGroupLayoutTabInfo);
    private native void nativeOnComputeLayout(long nativeTabGroupLayoutTabInfo);
    private native void nativeUpdateLayer(long nativeTabGroupLayoutTabInfo,
            TabContentManager tabContentManager, ResourceManager resourceManager);
    private native void nativePutTabGroupLayer(long nativeTabGroupLayoutTabInfo,
            TabContentManager tabContentManager, ResourceManager resourceManager,
            int closeResourceId, int pinResourceId, float pinnedSize, long timestamp,
            boolean isPinned, float x, float y, boolean isFocusedTab);
    private native void nativePutTabGroupCreationLayer(long nativeTabGroupLayoutTabInfo,
            TabContentManager tabContentManager, float x, float y);
    private native void nativePutTabGroupLayerLabel(long nativeTabGroupLayoutTabInfo,
            TabContentManager tabContentManager, float x, float y);

    private native void nativePutTabGroupAddTabLayer(long nativeTabGroupLayoutTabInfo,
            TabContentManager tabContentManager, ResourceManager resourceManager, int addResourceId,
            float x, float y);
}
