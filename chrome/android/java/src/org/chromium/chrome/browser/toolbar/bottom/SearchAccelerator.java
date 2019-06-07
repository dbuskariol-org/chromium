// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.toolbar.bottom;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.graphics.drawable.VectorDrawableCompat;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ThemeColorProvider;
import org.chromium.chrome.browser.ThemeColorProvider.ThemeColorObserver;
import org.chromium.chrome.browser.ThemeColorProvider.TintObserver;
import org.chromium.chrome.browser.toolbar.IncognitoStateProvider;
import org.chromium.chrome.browser.toolbar.IncognitoStateProvider.IncognitoStateObserver;
import org.chromium.chrome.browser.toolbar.top.ToolbarPhone;
import org.chromium.chrome.browser.util.ColorUtils;
import org.chromium.ui.interpolators.BakedBezierInterpolator;
import org.chromium.chrome.browser.util.FeatureUtilities;

/**
 * The search accelerator.
 */
public class SearchAccelerator extends ImageButton
        implements ThemeColorObserver, TintObserver, IncognitoStateObserver {
    /** The gray pill background behind the search icon. */
    private final Drawable mBackground;

    /** The {@link Resources} used to compute the background color. */
    private final Resources mResources;
    private View.OnClickListener mClickListener;
    private int mWidth;
    private int mHeight;
    private int mPadding;

    /** A provider that notifies components when the theme color changes.*/
    private ThemeColorProvider mThemeColorProvider;

    /** A provider that notifies when incognito mode is entered or exited. */
    private IncognitoStateProvider mIncognitoStateProvider;

    /** The search accelerator text label. */
    private TextView mLabel;

    /** The wrapper View that contains the search accelerator and the label. */
    private View mWrapper;

    public SearchAccelerator(Context context, AttributeSet attrs) {
        super(context, attrs);

        mResources = context.getResources();

        mBackground = ApiCompatibilityUtils.getDrawable(mResources, R.drawable.ntp_search_box);
        mBackground.mutate();
        setBackground(mBackground);
        mWidth = (int) mResources.getDimension(R.dimen.search_accelerator_width);
        mHeight = (int) mResources.getDimension(R.dimen.search_accelerator_height);
        mPadding = (int) mResources.getDimension(R.dimen.search_accelerator_height_padding);
    }

    /**
     * @param wrapper The wrapping View of this button.
     */
    public void setWrapperView(ViewGroup wrapper) {
        mWrapper = wrapper;
        mLabel = mWrapper.findViewById(R.id.search_accelerator_label);
        if (FeatureUtilities.isLabeledBottomToolbarEnabled()) mLabel.setVisibility(View.VISIBLE);
    }

    @Override
    public void setOnClickListener(OnClickListener listener) {
        if (mWrapper != null) {
            mWrapper.setOnClickListener(listener);
        } else {
            super.setOnClickListener(listener);
        }
        if (mClickListener == null) {
            mClickListener = listener;
        }
    }

    void setThemeColorProvider(ThemeColorProvider themeColorProvider) {
        mThemeColorProvider = themeColorProvider;
        mThemeColorProvider.addThemeColorObserver(this);
        mThemeColorProvider.addTintObserver(this);
    }

    void setIncognitoStateProvider(IncognitoStateProvider provider) {
        mIncognitoStateProvider = provider;
        mIncognitoStateProvider.addIncognitoStateObserverAndTrigger(this);
    }

    void destroy() {
        if (mThemeColorProvider != null) {
            mThemeColorProvider.removeThemeColorObserver(this);
            mThemeColorProvider.removeTintObserver(this);
            mThemeColorProvider = null;
        }

        if (mIncognitoStateProvider != null) {
            mIncognitoStateProvider.removeObserver(this);
            mIncognitoStateProvider = null;
        }
    }

    @Override
    public void onThemeColorChanged(int color, boolean shouldAnimate) {
        updateBackground();
    }

    @Override
    public void onTintChanged(ColorStateList tint, boolean useLight) {
        //ApiCompatibilityUtils.setImageTintList(this, tint);
        if (mLabel != null) mLabel.setTextColor(tint);
    }

    @Override
    public void onIncognitoStateChanged(boolean isIncognito) {
        updateBackground();
    }

    private void updateBackground() {
        if (mThemeColorProvider == null || mIncognitoStateProvider == null) return;

        mBackground.setColorFilter(ColorUtils.getTextBoxColorForToolbarBackground(mResources, false,
                                           mThemeColorProvider.getThemeColor(),
                                           mIncognitoStateProvider.isIncognitoSelected()),
                PorterDuff.Mode.SRC_IN);
    }

    public void reset(String productName, OnClickListener listener) {
        if (TextUtils.isEmpty(productName)) {
            animateDown();
            setOnClickListener(mClickListener);
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                setOnClickListener(listener);
                animateUp(productName);
            }
        }
    }

    private void setProductInfoDrawable(String productName) {
        Drawable drawable = ApiCompatibilityUtils.getDrawable(getResources(), R.drawable.ic_logo_googleg_24dp);
        drawable.setBounds(mPadding, mPadding, mHeight - mPadding, mHeight - mPadding);
        Bitmap bitmap = Bitmap.createBitmap(2 * mWidth, mHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.TRANSPARENT);
        drawable.draw(canvas);
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setFakeBoldText(true);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTextSize(1.2f * getResources().getDimension(R.dimen.compositor_tab_title_text_size));
        paint.setColor(Color.BLACK);
        android.util.Log.e("Yusuf","Product name is "+productName);
        Paint.FontMetrics metric = paint.getFontMetrics();
        int textHeight = (int) Math.ceil(metric.descent - metric.ascent) + mPadding;
        int y = (int)(textHeight);
        canvas.drawText("Product Info", mHeight , mHeight - 2* mPadding, paint);



        setImageBitmap(bitmap);
        getDrawable().setAlpha(0);
        ValueAnimator animator = ValueAnimator.ofFloat(0, 1).setDuration(
                ToolbarPhone.THEME_COLOR_TRANSITION_DURATION);
        animator.setInterpolator(BakedBezierInterpolator.FADE_IN_CURVE);
        animator.addUpdateListener(animation -> {
            getDrawable().setAlpha((int) animation.getAnimatedFraction() * 255);
        });
        animator.start();

    }

    private void animateUp(String productName) {
        ValueAnimator animator = ValueAnimator.ofFloat(0, 1).setDuration(
                ToolbarPhone.THEME_COLOR_TRANSITION_DURATION);
        animator.setInterpolator(BakedBezierInterpolator.TRANSFORM_CURVE);
        animator.addUpdateListener(animation -> {
            ViewGroup.LayoutParams params = getLayoutParams();
            params.width = mWidth + (int) (mWidth*animation.getAnimatedFraction());
            setLayoutParams(params);
            getDrawable().setAlpha((int) (255 * (1 - animation.getAnimatedFraction())));
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                setProductInfoDrawable(productName);
            }
        });
        animator.start();
    }

    private void animateDown() {
        if (getWidth() == mWidth) return;
        ValueAnimator animator = ValueAnimator.ofFloat(0, 1).setDuration(
                ToolbarPhone.THEME_COLOR_TRANSITION_DURATION);
        animator.setInterpolator(BakedBezierInterpolator.TRANSFORM_CURVE);
        animator.addUpdateListener(animation -> {
            ViewGroup.LayoutParams params = getLayoutParams();
            params.width = 2 * mWidth - (int) (mWidth*animation.getAnimatedFraction());
            setLayoutParams(params);
            getDrawable().setAlpha((int) (255 * (1 - animation.getAnimatedFraction())));
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                setImageDrawable(VectorDrawableCompat.create(getContext().getResources(),
                        R.drawable.new_tab_icon, getContext().getTheme()));
                getDrawable().setAlpha(255);
                ViewGroup.LayoutParams params = getLayoutParams();
                params.width = mWidth;
                setLayoutParams(params);
            }
        });
        animator.start();
    }

}
