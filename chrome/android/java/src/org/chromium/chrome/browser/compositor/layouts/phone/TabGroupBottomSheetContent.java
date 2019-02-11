// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.compositor.layouts.phone;

import android.support.v7.widget.RecyclerView;
import android.view.View;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.widget.bottomsheet.BottomSheet;

/**
 * This class defines the content that is displayed inside of the bottom sheet.
 */
public class TabGroupBottomSheetContent implements BottomSheet.BottomSheetContent {
    private final RecyclerView mContentView;
    private final View mToolbarView;

    public TabGroupBottomSheetContent(RecyclerView contentView, View toolbarView) {
        mContentView = contentView;
        mToolbarView = toolbarView;
    }

    @Override
    public View getContentView() {
        return mContentView;
    }

    @Override
    public View getToolbarView() {
        return mToolbarView;
    }

    @Override
    public int getVerticalScrollOffset() {
        return mContentView.computeVerticalScrollOffset();
    }

    @Override
    public void destroy() {}

    @Override
    public int getPriority() {
        return BottomSheet.ContentPriority.HIGH;
    }

    @Override
    public boolean swipeToDismissEnabled() {
        return false;
    }

    @Override
    public boolean isPeekStateEnabled() {
        return false;
    }

    @Override
    public int getSheetContentDescriptionStringId() {
        return R.string.tabgroup_expanded_description;
    }

    @Override
    public int getSheetHalfHeightAccessibilityStringId() {
        return R.string.tabgroup_expanded_sheet_opened_half;
    }

    @Override
    public int getSheetFullHeightAccessibilityStringId() {
        return R.string.tabgroup_expanded_sheet_opened_full;
    }

    @Override
    public int getSheetClosedAccessibilityStringId() {
        return R.string.tabgroup_expanded_sheet_closed;
    }
}
