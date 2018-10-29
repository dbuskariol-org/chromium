// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.collection;

import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.View;

import org.chromium.base.Log;
import org.chromium.chrome.browser.widget.bottomsheet.BottomSheet.BottomSheetContent;
import org.chromium.chrome.browser.widget.bottomsheet.BottomSheet.ContentPriority;

/** A {@link BottomSheetContent} that displays contextual suggestions. */
public class CollectionBottomSheetContent implements BottomSheetContent {
    private final ViewPager mContentView;
    private final View mToolbarView;
    private final boolean mUseSlimPeek;

    /**
     * Construct a new {@link CollectionBottomSheetContent}.
     * @param contentView
     * @param toolbarView
     * @param useSlimPeek Whether the slim peek UI should be used for this content.
     */
    public CollectionBottomSheetContent(
            ViewPager contentView, View toolbarView, boolean useSlimPeek) {
        mContentView = contentView;
        mToolbarView = toolbarView;
        mUseSlimPeek = useSlimPeek;
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
        FragmentPagerAdapter adapter = (FragmentPagerAdapter) mContentView.getAdapter();
        CollectionList currentTab = (CollectionList) adapter.getItem(mContentView.getCurrentItem());
        return currentTab.getVerticalScrollOffset();
    }

    @Override
    public void destroy() {
        Log.e("", "Collection destroy");
        // CollectionManager.destroy();
    }

    @Override
    public @ContentPriority int getPriority() {
        return ContentPriority.HIGH;
    }

    @Override
    public boolean swipeToDismissEnabled() {
        return false;
    }

    @Override
    public boolean isPeekStateEnabled() {
        return true;
    }

    @Override
    public int getSheetContentDescriptionStringId() {
        return org.chromium.chrome.R.string.contextual_suggestions_button_description;
    }

    @Override
    public int getSheetHalfHeightAccessibilityStringId() {
        return org.chromium.chrome.R.string.contextual_suggestions_sheet_opened_half;
    }

    @Override
    public int getSheetFullHeightAccessibilityStringId() {
        return org.chromium.chrome.R.string.contextual_suggestions_sheet_opened_full;
    }

    @Override
    public int getSheetClosedAccessibilityStringId() {
        return org.chromium.chrome.R.string.contextual_suggestions_sheet_closed;
    }
}
