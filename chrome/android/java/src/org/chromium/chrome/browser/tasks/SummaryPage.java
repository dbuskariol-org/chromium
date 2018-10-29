// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tasks;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.support.v4.graphics.drawable.RoundedBitmapDrawableFactory;
import android.support.v7.widget.AppCompatImageView;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.Callback;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.UrlConstants;
import org.chromium.chrome.browser.favicon.FaviconHelper;
import org.chromium.chrome.browser.native_page.BasicNativePage;
import org.chromium.chrome.browser.native_page.NativePageFactory;
import org.chromium.chrome.browser.native_page.NativePageHost;
import org.chromium.chrome.browser.omnibox.UrlBarData;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.util.ColorUtils;

/**
 * A native summary page.
 */
public class SummaryPage extends BasicNativePage {
    private int mBackgroundColor;
    private int mThemeColor;
    private int mFaviconSize;

    private String mTitle;
    private Tab mTab;
    private ChromeActivity mActivity;
    private FrameLayout mView;
    private FaviconHelper mFaviconHelper;

    public SummaryPage(ChromeActivity activity, NativePageHost host) {
        super(activity, host);
    }

    @Override
    protected void initialize(ChromeActivity activity, NativePageHost host) {
        mBackgroundColor = ApiCompatibilityUtils.getColor(
                activity.getResources(), org.chromium.chrome.R.color.modern_primary_color);
        mThemeColor = ColorUtils.getDefaultThemeColor(activity.getResources(), false);
        mFaviconSize = (int) (activity.getResources().getDimension(R.dimen.chip_icon_size) * 3 / 4);
        mFaviconHelper = new FaviconHelper();
        mActivity = activity;
        mTab = host.getActiveTab();
        mTitle = activity.getResources().getString(org.chromium.chrome.R.string.button_new_tab);
        mActivity.getTabContentManager().cacheTabThumbnail(mTab);
    }

    @Override
    public View getView() {
        if (mView != null) return mView;
        LayoutInflater layoutInflater = mActivity.getLayoutInflater();
        mView = (FrameLayout) layoutInflater.inflate(R.layout.summary_page, null);
        ChipGroup mChips = (ChipGroup) mView.findViewById(R.id.chip_group);
        mChips.setItemSpacing((int) mActivity.getResources().getDimension(R.dimen.chip_padding));
        mChips.setLineSpacing((int) mActivity.getResources().getDimension(R.dimen.chip_padding));
        Drawable drawable = mActivity.getResources().getDrawable(R.drawable.ic_chrome);
        drawable.setColorFilter(Color.LTGRAY, PorterDuff.Mode.SRC_IN);
        ViewGroup.LayoutParams params =
                new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                        (int) mActivity.getResources().getDimension(R.dimen.chip_height));
        int count = mTab.getWebContents() != null ? mTab.getWebContents()
                                                            .getNavigationController()
                                                            .getNavigationHistory()
                                                            .getEntryCount()
                                                  : 0;
        android.util.Log.e("Yusuf", "Entry count is " + count);
        for (int i = 0; i < count; i++) {
            CheapChip chip = new CheapChip(mActivity);
            chip.setLayoutParams(params);
            chip.setGravity(Gravity.CENTER_VERTICAL);
            UrlBarData data = UrlBarData.forUrl(
                    mTab.getWebContents().getNavigationController().getEntryAtIndex(i).getUrl());
            if (NativePageFactory.isNativePageUrl(data.url, mTab.isIncognito())) continue;
            chip.setText(data.getEditingOrDisplayText().subSequence(
                    data.originStartIndex, data.originEndIndex));
            mFaviconHelper.getLocalFaviconImageForURL(
                    Profile.getLastUsedProfile().getOriginalProfile(), data.url, mFaviconSize,
                    new FaviconHelper.FaviconImageCallback() {
                        @Override
                        public void onFaviconAvailable(Bitmap image, String iconUrl) {
                            chip.setCompoundDrawables(RoundedBitmapDrawableFactory.create(
                                                              mActivity.getResources(), image),
                                    null, null, null);
                        }
                    });
            chip.setBackgroundColor(Color.rgb(245, 245, 245));
            chip.setCompoundDrawables(drawable, null, null, null);
            final int index = i;
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (mTab.getWebContents() != null)
                        mTab.getWebContents().getNavigationController().goToNavigationIndex(index);
                }
            });
            mChips.addView(chip);
        }
        mActivity.getTabContentManager().getTabThumbnailFromCallback(
                mTab.getId(), new Callback<Bitmap>() {
                    @Override
                    public void onResult(Bitmap result) {
                        ((AppCompatImageView) mView.findViewById(R.id.thumbnail))
                                .setImageBitmap(result);
                    }
                });
        return mView;
    }

    @Override
    public String getTitle() {
        return mTitle;
    }

    @Override
    public String getUrl() {
        return UrlConstants.SUMMARY_URL;
    }

    @Override
    public String getHost() {
        return UrlConstants.SUMMARY_HOST;
    }

    @Override
    public int getBackgroundColor() {
        return mBackgroundColor;
    }

    @Override
    public int getThemeColor() {
        return mThemeColor;
    }

    @Override
    public boolean needsToolbarShadow() {
        return false;
    }

    @Override
    public void updateForUrl(String url) {}

    @Override
    public void destroy() {
        mActivity = null;
    }
}
