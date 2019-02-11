// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.compositor.layouts.components;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Point;
import android.graphics.RectF;

import java.util.List;

/**
 * {@link LayoutTabGroupTab} is used to keep track of a thumbnail's bitmap and position and to
 * draw itself onto the GL canvas at the desired Y Offset.
 */
public class LayoutTabGroupTab {
    public static int NEW_TAB_IN_GROUP_ID = -2;
    private static float sDpToPx;
    private static float sPxToDp;
    private static int sHorizontalPaddingBetweenTabsPx;
    private static int sVerticalPaddingBetweenTabsPx;
    private static Point sSize;

    private final long mId;

    private static final int NUM_COLUMNS = 1;
    // private static final int TILE_WIDTH_DP = 104;
    private static final int TILE_WIDTH_DP = 400;
    // private static final int TILE_HEIGHT_DP = 156;
    private static final int TILE_HEIGHT_DP = 88;
    private static final int TILE_PADDING_DP = 8;
    private static final int CLOSE_BTN_SIZE_DP = 24;
    private static final int CLOSE_BTN_PADDING_DP = 16;

    private float mX;
    private float mY;
    private boolean mIsPinned;

    private final RectF mBounds = new RectF(); // Pre-allocated to avoid in-frame allocations.

    public LayoutTabGroupTab(long id) {
        mId = id;
        mX = 0.0f;
        mY = 0.0f;
    }

    /**
     * Helper function that gather the static constants from values/dimens.xml.
     *
     * @param context The Android Context.
     */
    public static void resetDimensionConstants(Context context) {
        Resources res = context.getResources();
        sDpToPx = res.getDisplayMetrics().density;
        sPxToDp = 1.0f / sDpToPx;
        sSize = new Point((int) (TILE_WIDTH_DP * sDpToPx), (int) (TILE_HEIGHT_DP * sDpToPx));
        sHorizontalPaddingBetweenTabsPx = (int) (TILE_PADDING_DP * sDpToPx);
        sVerticalPaddingBetweenTabsPx = (int) (TILE_PADDING_DP * sDpToPx);
    }

    /**
     * @return The id of the Tab Group Tab
     */
    public long getId() {
        return mId;
    }

    /**
     * @param y The vertical draw position.
     */
    public void setY(float y) {
        mY = y;
    }

    /**
     * @return The vertical draw position for the update logic.
     */
    public float getY() {
        return mY;
    }

    /**
     * @param x The horizontal draw position.
     */
    public void setX(float x) {
        mX = x;
    }

    /**
     * @return The horizontal draw position for the update logic.
     */
    public float getX() {
        return mX;
    }

    public boolean isPinned() {
        return mIsPinned;
    }

    public void setPinned(boolean isPinned) {
        mIsPinned = isPinned;
    }

    /**
     * Computes the Manhattan-ish distance to the edge of the tab.
     * This distance is good enough for click detection.
     *
     * @param x          X coordinate of the hit testing point.
     * @param y          Y coordinate of the hit testing point.
     * @return           The Manhattan-ish distance to the tab.
     */
    public float computeDistanceTo(float x, float y) {
        final RectF bounds = getClickTargetBounds();
        float dx = Math.max(bounds.left - x, x - bounds.right);
        float dy = Math.max(bounds.top - y, y - bounds.bottom);
        return Math.max(0.0f, Math.max(dx, dy));
    }

    /**
     * Computes the Manhattan-ish distance to the edge of the close button.
     * This distance is good enough for click detection.
     *
     * @param x          X coordinate of the hit testing point.
     * @param y          Y coordinate of the hit testing point.
     * @return           The Manhattan-ish distance to the tab.
     */
    public float computeDistanceToClose(float x, float y) {
        final RectF bounds = getCloseClickTargetBounds();
        float dx = Math.max(bounds.left - x, x - bounds.right);
        float dy = Math.max(bounds.top - y, y - bounds.bottom);
        return Math.max(0.0f, Math.max(dx, dy));
    }

    /**
     * @return The rectangle that represents the click target of the tab.
     */
    public RectF getClickTargetBounds() {
        mBounds.top = mY;
        mBounds.bottom = mY + sSize.y;
        mBounds.left = mX;
        mBounds.right = mX + sSize.x;
        return mBounds;
    }

    /**
     * @return The rectangle that represents the click target of the close button.
     */
    public RectF getCloseClickTargetBounds() {
        mBounds.top = mY;
        mBounds.bottom = mY + sSize.y;
        mBounds.left = mX + sSize.x - (CLOSE_BTN_SIZE_DP + CLOSE_BTN_PADDING_DP * 2) * sDpToPx;
        mBounds.right = mX + sSize.x;
        return mBounds;
    }

    public static LayoutTabGroupTab[] getTabGroupTabs(List<? extends Number> ids) {
        int tabGroupTabCount = ids != null ? ids.size() : 0;
        LayoutTabGroupTab[] tabGroupTabs = new LayoutTabGroupTab[tabGroupTabCount + 1];
        for (int i = 0; i < tabGroupTabCount; i++) {
            tabGroupTabs[i] = new LayoutTabGroupTab(ids.get(i).longValue());
        }
        tabGroupTabs[tabGroupTabCount] = new LayoutTabGroupTab(NEW_TAB_IN_GROUP_ID);
        return tabGroupTabs;
    }

    /*
     * @return The bounding rectangle that contains all the items in the grid
     * and the padding around them.
     */
    public static RectF putTabGroupTabOnGrid(
            LayoutTabGroupTab[] tabGroupTabs, float sizeX, int gridOriginY) {
        float sideMargin = (sizeX
                                   - (NUM_COLUMNS * sSize.x
                                             + (NUM_COLUMNS - 1) * sHorizontalPaddingBetweenTabsPx))
                / 2;
        float topMargin = gridOriginY + sVerticalPaddingBetweenTabsPx / 2;
        int tabGroupTabCount = tabGroupTabs != null ? tabGroupTabs.length : 0;
        RectF bounds = new RectF();
        bounds.left = sideMargin - sHorizontalPaddingBetweenTabsPx / 2;
        bounds.top = gridOriginY;
        for (int i = 0; i < tabGroupTabCount; i++) {
            int column = i % NUM_COLUMNS;
            int row = (i - column) / NUM_COLUMNS;
            tabGroupTabs[i].setX(sideMargin + column * (sHorizontalPaddingBetweenTabsPx + sSize.x));
            tabGroupTabs[i].setY(topMargin + row * (sVerticalPaddingBetweenTabsPx + sSize.y));

            bounds.right = Math.max(bounds.right,
                    tabGroupTabs[i].getX() + sSize.x + sHorizontalPaddingBetweenTabsPx / 2);
            bounds.bottom = Math.max(bounds.bottom,
                    tabGroupTabs[i].getY() + sSize.y + sVerticalPaddingBetweenTabsPx / 2);
        }
        return bounds;
    }
}
