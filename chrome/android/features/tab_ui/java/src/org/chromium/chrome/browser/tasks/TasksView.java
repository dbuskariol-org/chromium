// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tasks;

import android.content.Context;
import android.content.res.Resources;
import android.support.v4.view.MarginLayoutParamsCompat;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.browser.util.ColorUtils;
import org.chromium.chrome.tab_ui.R;

// The view of the tasks surface.
class TasksView extends LinearLayout {
    private final Context mContext;
    private FrameLayout mTabSwitcherContainer;

    /** Default constructor needed to inflate via XML. */
    public TasksView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mTabSwitcherContainer = (FrameLayout) findViewById(R.id.tab_switcher_container);
    }

    ViewGroup getTabSwitcherContainer() {
        return mTabSwitcherContainer;
    }

    void setIsTabCarousel(boolean isTabCarousel) {
        if (isTabCarousel) {
            // TODO(crbug.com/982018): Change view according to incognito and dark mode.
            findViewById(R.id.tab_switcher_title).setVisibility(View.VISIBLE);

            // Add negative margin to start so as to reduce the first Tab card's visual distance to
            // the start edge to ~16dp.
            // TODO(crbug.com/982018): Add test to guard the visual expectation.
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            MarginLayoutParamsCompat.setMarginStart(layoutParams,
                    mContext.getResources().getDimensionPixelSize(
                            R.dimen.tab_carousel_start_margin));
            mTabSwitcherContainer.setLayoutParams(layoutParams);
        }
    }

    void setMoreTabsOnClicklistener(@Nullable View.OnClickListener listener) {
        findViewById(R.id.more_tabs).setOnClickListener(listener);
    }

    void setIncognitoMode(boolean isIncognito, boolean isVoiceSearchButtonVisible) {
        Resources resources = mContext.getResources();
        setBackgroundColor(ColorUtils.getPrimaryBackgroundColor(resources, isIncognito));
        View searchBox = findViewById(R.id.search_box);
        searchBox.setBackgroundResource(
                isIncognito ? R.drawable.fake_search_box_bg_incognito : R.drawable.ntp_search_box);
        int hintTextColor = isIncognito
                ? ApiCompatibilityUtils.getColor(resources, R.color.locationbar_light_hint_text)
                : ApiCompatibilityUtils.getColor(resources, R.color.locationbar_dark_hint_text);
        ((TextView) searchBox.findViewById(R.id.search_box_text)).setHintTextColor(hintTextColor);

        // Do not display voice search button in incognito mode.
        if (isVoiceSearchButtonVisible) {
            findViewById(R.id.voice_search_button)
                    .setVisibility(isIncognito ? View.GONE : View.VISIBLE);
        }
    }
}