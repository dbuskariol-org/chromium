// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.compositor.layouts.phone;

import android.graphics.Bitmap;

/**
 * This class contains all the information about a item that is displayed in the tab group bottom
 * sheet.
 */
public class TabGroupBottomSheetItem {
    private int mTabId;
    private boolean mIsCurrentlySelected;
    private final Bitmap mBitmap;
    private final Bitmap mFavicon;
    private String mTitle;

    public TabGroupBottomSheetItem(
            String title, Bitmap favicon, int tabId, Bitmap bitmap, boolean isCurrentlySelected) {
        mTitle = title;
        mFavicon = favicon;
        mTabId = tabId;
        mBitmap = bitmap;
        mIsCurrentlySelected = isCurrentlySelected;
    }

    public int getTabId() {
        return mTabId;
    }

    public Bitmap getBitmap() {
        return mBitmap;
    }

    public String getTitle() {
        return mTitle;
    }

    public Bitmap getFavicon() {
        return mFavicon;
    }

    public void setTabId(final int tabId) {
        mTabId = tabId;
    }

    public boolean isCurrentlySelected() {
        return mIsCurrentlySelected;
    }

    public void setSelected(boolean isSelected) {
        mIsCurrentlySelected = isSelected;
    }
}
