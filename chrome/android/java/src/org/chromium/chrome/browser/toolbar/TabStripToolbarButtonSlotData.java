// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.toolbar;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v7.widget.AppCompatImageButton;
import android.view.View;
import android.view.View.OnClickListener;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;

/**
 * This class can hold two buttons, one to be shown in browsing mode and one for tab switching mode.
 */
class TabStripToolbarButtonSlotData {
    /** The button to be shown when in browsing mode. */
    public TabStripToolbarButtonData browsingModeButtonData;

    TabStripToolbarButtonSlotData() {}

    /**
     * A class that holds all the state for a bottom toolbar button. It is used to swap between
     * buttons when entering or leaving tab switching mode.
     */
    static class TabStripToolbarButtonData {
        private Drawable mDrawable;
        private final CharSequence mAccessibilityString;
        private final OnClickListener mOnClickListener;
        private final OnClickListener mOnCloseTabClickListener;
        private boolean mIsSelected;
        private AppCompatImageButton mImageButton;

        /**
         * @param drawable The {@link Drawable} that will be shown in the button slot.
         * @param accessibilityString The accessibility string to be used in light mode.
         * @param onClickListener The listener that will be fired when this button is clicked.
         * @param context The {@link Context} that is used to obtain tinting information.
         */
        TabStripToolbarButtonData(Drawable drawable, CharSequence accessibilityString,
                OnClickListener onClickListener, OnClickListener onCloseTabClickListener,
                Context context, boolean isSelected) {
            mDrawable = drawable != null ? DrawableCompat.wrap(drawable) : null;
            mAccessibilityString = accessibilityString;
            mOnClickListener = onClickListener;
            mOnCloseTabClickListener = onCloseTabClickListener;
            mIsSelected = isSelected;
        }

        /**
         * @param imageButton The {@link AppCompatImageButton} this button data will fill.
         */
        void setButton(AppCompatImageButton imageButton) {
            mImageButton = imageButton;
            imageButton.setOnClickListener(mOnClickListener);
            updateButtonDrawable(mDrawable);
        }

        void updateButtonDrawable(Drawable drawable) {
            mDrawable = drawable != null ? DrawableCompat.wrap(drawable) : null;
            // Don't update the drawable if the TintedImageButton hasn't been set yet.
            if (mImageButton == null) {
                return;
            }
            redrawButton();
        }

        private void redrawButton() {
            if (mDrawable != null) {
                mImageButton.setVisibility(View.VISIBLE);
                mImageButton.setEnabled(true);
            }
            mImageButton.setBackgroundResource(R.drawable.tab_strip_favicon_background);
            mImageButton.setImageDrawable(mDrawable);
            if (mIsSelected) {
                Drawable foreground = ApiCompatibilityUtils.getDrawable(
                        mImageButton.getResources(), R.drawable.tabstrip_selected);
                mImageButton.setOnClickListener(mOnCloseTabClickListener);
                if (Build.VERSION.SDK_INT >= 23) mImageButton.setForeground(foreground);
                if (mImageButton.getParent() != null) {
                    mImageButton.getParent().requestChildFocus(mImageButton, mImageButton);
                }
            } else {
                if (Build.VERSION.SDK_INT >= 23) mImageButton.setForeground(null);
                mImageButton.setOnClickListener(mOnClickListener);
            }
            mImageButton.setContentDescription(mAccessibilityString);
            mImageButton.invalidate();
        }

        static void clearButton(AppCompatImageButton button) {
            TabStripToolbarButtonData emptyButtonData =
                    new TabStripToolbarButtonData(null, "", null, null, button.getContext(), false);
            emptyButtonData.setButton(button);
        }

        void setIsSelected(boolean isSelected) {
            mIsSelected = isSelected;
            redrawButton();
        }

        boolean isSelected() {
            return mIsSelected;
        }
    }
}
