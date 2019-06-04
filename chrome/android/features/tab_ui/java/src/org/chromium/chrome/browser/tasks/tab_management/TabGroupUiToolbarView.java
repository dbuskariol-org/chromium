// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tasks.tab_management;

import android.content.Context;
import android.content.res.ColorStateList;
import android.support.graphics.drawable.VectorDrawableCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.browser.ChromeFeatureList;
import org.chromium.chrome.browser.toolbar.TabSwitcherButtonView;
import org.chromium.chrome.browser.toolbar.bottom.SearchAccelerator;
import org.chromium.chrome.tab_ui.R;
import org.chromium.ui.widget.ChromeImageView;

/**
 * Represents a generic toolbar used in the bottom strip/grid component.
 * {@link TabGridSheetToolbarCoordinator}
 */
public class TabGroupUiToolbarView extends FrameLayout {
    private ChromeImageView mRightButton;
    private ViewGroup mContainerView;
    private TextView mTitleTextView;
    private SearchAccelerator mSearchAccelarator;
    private TabSwitcherButtonView mTabSwitcherButtonView;
    private View mMainContent;

    public TabGroupUiToolbarView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mRightButton = findViewById(R.id.toolbar_right_button);
        mContainerView = (ViewGroup) findViewById(R.id.toolbar_container_view);
        mTitleTextView = (TextView) findViewById(R.id.title);
        mSearchAccelarator = (SearchAccelerator) findViewById(R.id.search_accelerator);
        mMainContent = findViewById(R.id.main_content);
        mTabSwitcherButtonView = (TabSwitcherButtonView) findViewById(R.id.tab_switcher_button);
    }

    void setLeftButtonOnClickListener(OnClickListener listener) {
    }

    void setRightButtonOnClickListener(OnClickListener listener) {
        mRightButton.setOnClickListener(listener);
    }

    void setTitleOnClickListener(OnClickListener listener) {
        mTitleTextView.setOnClickListener(listener);
    }

    ViewGroup getViewContainer() {
        return mContainerView;
    }

    void setMainContentVisibility(boolean isVisible) {
        if (mContainerView == null)
            throw new IllegalStateException("Current Toolbar doesn't have a container view");

        for (int i = 0; i < ((ViewGroup) mContainerView).getChildCount(); i++) {
            View child = ((ViewGroup) mContainerView).getChildAt(i);
            child.setVisibility(isVisible ? View.VISIBLE : View.GONE);
        }
        mMainContent.setVisibility(isVisible ? View.VISIBLE : View.INVISIBLE);
        if (mSearchAccelarator != null) {
            mSearchAccelarator.setVisibility(isVisible ? View.INVISIBLE : View.VISIBLE);
            if (!isVisible) {
                mSearchAccelarator.setImageDrawable(
                        VectorDrawableCompat.create(getContext().getResources(),
                                R.drawable.new_tab_icon, getContext().getTheme()));
            }
        }
        if (ChromeFeatureList.isInitialized()
                && !ChromeFeatureList.isEnabled(ChromeFeatureList.SHOPPING_ASSIST)) {
            LayoutParams layoutParams = (LayoutParams) mMainContent.getLayoutParams();
            layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
            layoutParams.setMarginStart(0);
            layoutParams.setMarginEnd(0);
            mMainContent.setLayoutParams(layoutParams);
            mMainContent.setBackgroundResource(android.R.color.white);
        }
    }

    void setTitle(String title) {
        if (mTitleTextView == null)
            throw new IllegalStateException("Current Toolbar doesn't have a title text view");

        mTitleTextView.setText(title);
    }

    void setPrimaryColor(int color) {
        DrawableCompat.setTint(mMainContent.getBackground(), color);
    }

    void setTint(ColorStateList tint) {
        ApiCompatibilityUtils.setImageTintList(mRightButton, tint);
        ApiCompatibilityUtils.setImageTintList(mTabSwitcherButtonView, tint);
        DrawableCompat.setTintList(mRightButton.getDrawable(), tint);
        if (mTitleTextView != null) mTitleTextView.setTextColor(tint);
    }
}
