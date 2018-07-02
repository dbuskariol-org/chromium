// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.compositor.layouts.components;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Point;
import android.graphics.RectF;

import org.chromium.chrome.browser.journey.Journey;
import org.chromium.chrome.browser.journey.JourneyManager;

import java.util.List;

/**
 * {@link LayoutAutotab} is used to keep track of a thumbnail's bitmap and position and to
 * draw itself onto the GL canvas at the desired Y Offset.
 */
public class LayoutAutotab {
    private static float sDpToPx;
    private static float sPxToDp;
    private static int sHorizontalPaddingBetweenAutotabsPx;
    private static int sVerticalPaddingBetweenAutotabsPx;
    private static Point sSize;

    private final long mTimestamp;
    private float mX;
    private float mY;
    private boolean mIsPinned;

    private final RectF mBounds = new RectF(); // Pre-allocated to avoid in-frame allocations.

    public LayoutAutotab(long timestamp) {
        mTimestamp = timestamp;
        mX = 0.0f;
        mY = 0.0f;
        mIsPinned = Journey.isTimestampPinned(timestamp);
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
        sSize = new Point((int) (104 * sDpToPx), (int) (156 * sDpToPx));
        sHorizontalPaddingBetweenAutotabsPx = (int) (8 * sDpToPx);
        sVerticalPaddingBetweenAutotabsPx = (int) (8 * sDpToPx);
    }

    /**
     * @return The timestamp of the autotab, same as the timestamp for the corresponding history
     * entry.
     */
    public long getTimestamp() {
        return mTimestamp;
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
     * @return The rectangle that represents the click target of the tab.
     */
    public RectF getClickTargetBounds() {
        mBounds.top = mY;
        mBounds.bottom = mY + sSize.y;
        mBounds.left = mX;
        mBounds.right = mX + sSize.x;
        return mBounds;
    }

    public static LayoutAutotab[] getAutotabs(List<Long> pageloads) {
        int autotabsCount = pageloads != null ? pageloads.size() : 0;
        LayoutAutotab[] autotabs = new LayoutAutotab[autotabsCount];
        for (int i = 0; i < autotabsCount; i++) {
            autotabs[i] = new LayoutAutotab(pageloads.get(i));
        }
        return autotabs;
    }

    public static LayoutAutotab[] putAutotabsOnGrid(
            LayoutAutotab[] autotabs, float sizeX, int gridOriginY) {
        float sideMargin = (sizeX - (3 * sSize.x + 2 * sHorizontalPaddingBetweenAutotabsPx)) / 2;
        int autotabsCount = autotabs != null ? autotabs.length : 0;
        for (int i = 0; i < autotabsCount; i++) {
            int column = i % 3;
            int row = (i - column) / 3;
            autotabs[i].setX(sideMargin + column * (sHorizontalPaddingBetweenAutotabsPx + sSize.x));
            autotabs[i].setY(gridOriginY + row * (sVerticalPaddingBetweenAutotabsPx + sSize.y));
        }
        return autotabs;
    }
}
