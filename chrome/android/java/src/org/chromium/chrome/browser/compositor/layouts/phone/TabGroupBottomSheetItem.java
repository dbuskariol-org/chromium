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
    private Bitmap mBitmap;

    public TabGroupBottomSheetItem(final int tabId, Bitmap bitmap) {
        mTabId = tabId;
        mBitmap = bitmap;
    }

    public int getTabId() {
        return mTabId;
    }

    public Bitmap getBitmap() {
        return mBitmap;
    }

    public void setTabId(final int tabId) {
        mTabId = tabId;
    }
}
