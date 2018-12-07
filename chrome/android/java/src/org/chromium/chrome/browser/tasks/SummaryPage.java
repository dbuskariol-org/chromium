// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tasks;

import static org.chromium.chrome.browser.tasks.SummaryTracker.SUMMARY_INFO_TYPE_PRODUCT;
import static org.chromium.chrome.browser.tasks.SummaryTracker.SUMMARY_INFO_TYPE_QUERY;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.TrafficStats;
import android.net.Uri;
import android.os.Build;
import android.support.v4.graphics.drawable.RoundedBitmapDrawableFactory;
import android.support.v7.widget.AppCompatImageView;
import android.support.v7.widget.AppCompatTextView;
import android.support.v7.widget.CardView;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.Callback;
import org.chromium.base.task.AsyncTask;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.UrlConstants;
import org.chromium.chrome.browser.favicon.FaviconHelper;
import org.chromium.chrome.browser.native_page.BasicNativePage;
import org.chromium.chrome.browser.native_page.NativePageHost;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.util.ColorUtils;
import org.chromium.content_public.browser.LoadUrlParams;
import org.chromium.ui.UiUtils;
import org.chromium.ui.interpolators.BakedBezierInterpolator;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Set;

/**
 * A native summary page.
 */
public class SummaryPage extends BasicNativePage {
    private int mBackgroundColor;
    private int mThemeColor;
    private int mFaviconSize;
    private int mScreenshotWidth;
    private int mScreenshotHeight;
    private int mChipElevation;

    private String mTitle;
    private Tab mTab;
    private ChromeActivity mActivity;
    private FrameLayout mView;
    private ChipGroup mChips;
    private ChipGroup mProducts;
    private AppCompatImageView mScreenshotView;
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
        mScreenshotWidth = (int) (activity.getResources().getDimension(R.dimen.summary_shot_width));
        mScreenshotHeight =
                (int) (activity.getResources().getDimension(R.dimen.summary_shot_height));
        mChipElevation = (int) mActivity.getResources().getDimension(R.dimen.chip_elevation);
    }

    @Override
    public View getView() {
        if (mView != null) return mView;

        mView = (FrameLayout) mActivity.getLayoutInflater().inflate(R.layout.summary_page, null);

        mChips = mView.findViewById(R.id.chip_group);
        mChips.setItemSpacing((int) mActivity.getResources().getDimension(R.dimen.chip_padding));
        mChips.setLineSpacing((int) mActivity.getResources().getDimension(R.dimen.chip_padding));

        mProducts = mView.findViewById(R.id.product_group);
        mProducts.setItemSpacing(
                ((int) mActivity.getResources().getDimension(R.dimen.chip_padding)));
        mProducts.setLineSpacing((int) mActivity.getResources().getDimension(R.dimen.chip_padding));

        mScreenshotView = mView.findViewById(R.id.thumbnail);
        ((CardView) mView.findViewById(R.id.thumbnail_card)).setRadius(mChipElevation);

        if (mActivity.getCurrentTabSummary() == null) return mView;

        TaskSummary.Summary summary = mActivity.getCurrentTabSummary().getSummary();
        int count = summary.getInfoCount();
        Set<String> urls = new HashSet<>();

        for (int i = 0; i < count; i++) {
            TaskSummary.SummaryInfo info = summary.getInfo(i);
            if (urls.contains(info.getImageUrl())) continue;
            urls.add(info.getImageUrl());
            if (info.getSummaryInfoType() == SUMMARY_INFO_TYPE_QUERY) {
                addChipForQuery(info);
            } else if (info.getSummaryInfoType() == SUMMARY_INFO_TYPE_PRODUCT) {
                addCardForProduct(info);
            }
        }

        addScreenshotToView();

        return mView;
    }

    private void addChipForQuery(TaskSummary.SummaryInfo info) {
        ViewGroup.LayoutParams params =
                new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                        (int) mActivity.getResources().getDimension(R.dimen.chip_height));

        Drawable drawable = mActivity.getResources().getDrawable(R.drawable.ic_chrome);
        drawable.setColorFilter(Color.LTGRAY, PorterDuff.Mode.SRC_IN);

        CheapChip chip = new CheapChip(mActivity);
        chip.setLayoutParams(params);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setText(info.getText());
        mFaviconHelper.getLocalFaviconImageForURL(Profile.getLastUsedProfile().getOriginalProfile(),
                info.getImageUrl(), mFaviconSize, new FaviconHelper.FaviconImageCallback() {
                    @Override
                    public void onFaviconAvailable(Bitmap image, String iconUrl) {
                        chip.setCompoundDrawables(RoundedBitmapDrawableFactory.create(
                                                          mActivity.getResources(), image),
                                null, null, null);
                    }
                });
        chip.setBackgroundColor(Color.rgb(245, 245, 245));
        chip.setCompoundDrawables(drawable, null, null, null);
        chip.setOnClickListener(view -> {
            if (mTab.getWebContents() != null) mTab.loadUrl(new LoadUrlParams(info.getUrl()));
        });
        mChips.addView(chip);
    }

    private void addCardForProduct(TaskSummary.SummaryInfo info) {
        LayoutInflater layoutInflater = mActivity.getLayoutInflater();

        CardView productCard = (CardView) layoutInflater.inflate(R.layout.product_card, null);
        ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(
                (int) mActivity.getResources().getDimension(R.dimen.product_width),
                (int) mActivity.getResources().getDimension(R.dimen.product_height));
        productCard.setLayoutParams(layoutParams);
        if (Build.VERSION.SDK_INT > 21) productCard.setElevation(mChipElevation);
        AppCompatImageView imageView = productCard.findViewById(R.id.product_image);
        new DownloadImageTask(imageView, info.getImageUrl())
                .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        AppCompatTextView label = productCard.findViewById(R.id.product_label);
        label.setText(info.getText());
        productCard.setOnClickListener(view -> mTab.loadUrl(new LoadUrlParams(info.getUrl())));
        productCard.setRadius(mChipElevation);
        mProducts.addView(productCard);
    }

    private void addScreenshotToView() {
        PropertyValuesHolder viewAlpha = PropertyValuesHolder.ofFloat(View.ALPHA, 0.f, 1.f);
        final ObjectAnimator viewAlphaAnimator =
                ObjectAnimator.ofPropertyValuesHolder(mScreenshotView, viewAlpha);
        viewAlphaAnimator.setDuration(500);
        viewAlphaAnimator.setInterpolator(BakedBezierInterpolator.FADE_IN_CURVE);

        // If there is already a thumbnail use that, if not take a screenshot of the current page.
        if (mActivity.getTabContentManager().hasFullCachedThumbnail(mTab.getId())) {
            mActivity.getTabContentManager().getTabThumbnailFromCallback(mTab.getId(), result -> {
                mScreenshotView.setImageBitmap(result);
                viewAlphaAnimator.start();
            });
        } else {
            try {
                // TODO(yusufo): Clean this directory before writing a new file.
                String path =
                        UiUtils.getDirectoryForImageCapture(mActivity) + File.separator + "summary";
                mTab.getWebContents().writeContentBitmapToDiskAsync(
                        mScreenshotWidth, mScreenshotHeight, path, (Callback<String>) result -> {
                            mScreenshotView.setImageURI(Uri.parse(result));
                            viewAlphaAnimator.start();
                        });
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
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

    private static class DownloadImageTask extends AsyncTask<Bitmap> {
        private WeakReference<AppCompatImageView> mImage;
        private String mUrl;

        DownloadImageTask(AppCompatImageView mImage, String url) {
            this.mImage = new WeakReference<>(mImage);
            mUrl = url;
        }

        @Override
        protected Bitmap doInBackground() {
            TrafficStats.setThreadStatsTag(0xff);

            Bitmap bmp = null;
            try {
                InputStream in = new java.net.URL(mUrl).openStream();
                bmp = BitmapFactory.decodeStream(in);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return bmp;
        }

        @Override
        protected void onPostExecute(Bitmap result) {
            AppCompatImageView image = mImage.get();
            if (image != null) {
                Drawable drawable = new BitmapDrawable(result);
                drawable.setBounds(0, 0, image.getMeasuredWidth(), image.getMeasuredHeight());
                image.setImageDrawable(drawable);
            }
            image.requestLayout();
        }
    }
}
