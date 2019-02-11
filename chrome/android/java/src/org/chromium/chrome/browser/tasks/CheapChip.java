// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tasks;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.annotation.Nullable;
import android.support.v7.widget.AppCompatTextView;
import android.text.TextUtils;
import android.util.AttributeSet;

import org.chromium.chrome.R;

/**
 * A single view Chip class. Cheap as in doesn't contain many views, not cheap as in cheap to draw.
 */
public class CheapChip extends AppCompatTextView {
    private final Drawable mBackgroundDrawable;
    private final int mChipIconSize;
    private final int mChipElevation;
    private final int mChipElevationPressed;
    private final int mCompoundDrawablePadding;

    public CheapChip(Context context) {
        this(context, null);
    }

    public CheapChip(Context context, AttributeSet attrs) {
        super(context, attrs);
        mCompoundDrawablePadding =
                (int) getResources().getDimension(R.dimen.compound_drawable_padding);
        int chipPadding = (int) getResources().getDimension(R.dimen.chip_padding);
        mChipIconSize = (int) getResources().getDimension(R.dimen.chip_icon_size);
        mChipElevation = (int) getResources().getDimension(R.dimen.chip_elevation);
        mChipElevationPressed = (int) getResources().getDimension(R.dimen.chip_elevation_pressed);

        setCompoundDrawablePadding(mCompoundDrawablePadding);
        setPadding(chipPadding, chipPadding, chipPadding, chipPadding);
        mBackgroundDrawable = getResources().getDrawable(R.drawable.chip_background);
        setBackground(mBackgroundDrawable);
        if (Build.VERSION.SDK_INT > 21) {
            setElevation(mChipElevation);
        }
    }

    @Override
    public void setBackgroundColor(int color) {
        mBackgroundDrawable.setColorFilter(color, PorterDuff.Mode.SRC_IN);
        setBackground(mBackgroundDrawable);
    }

    @Override
    public void setCompoundDrawables(@Nullable Drawable left, @Nullable Drawable top,
            @Nullable Drawable right, @Nullable Drawable bottom) {
        if (left != null) left.setBounds(0, 0, mChipIconSize, mChipIconSize);
        super.setCompoundDrawables(left, top, right, bottom);
    }

    @Override
    public void setText(CharSequence text, BufferType type) {
        setCompoundDrawablePadding(TextUtils.isEmpty(text) ? 0 : mCompoundDrawablePadding);
        super.setText(text, type);
    }

    @Override
    public final void setBackground(Drawable background) {
        super.setBackground(mBackgroundDrawable);
    }
}
