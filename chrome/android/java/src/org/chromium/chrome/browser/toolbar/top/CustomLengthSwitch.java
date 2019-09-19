// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.toolbar.top;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.Switch;

import org.chromium.base.Log;

import java.lang.reflect.Field;

/**
 * This class is a customized Switch allows track lengths shorter that 2x thumb size.
 */
public class CustomLengthSwitch extends Switch {
    private static final String TAG = "CustomLengthSwitch";
    /** Constructor for when the gridview is inflated from XML. */
    public CustomLengthSwitch(Context context, AttributeSet attrs) {
        super(context, attrs);
    }
    @Override
    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // The native Switch forces the switch track to be 2 x the width of the switch thumb
        //
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        try {
            Field fieldSwitchWidth = Switch.class.getDeclaredField("mSwitchWidth");
            Field fieldThumbWidth = Switch.class.getDeclaredField("mThumbWidth");
            Field fieldSwitchMinWidth = Switch.class.getDeclaredField("mSwitchMinWidth");
            Field fieldThumbDrawable = Switch.class.getDeclaredField("mThumbDrawable");
            Field fieldTrackDrawable = Switch.class.getDeclaredField("mTrackDrawable");

            fieldSwitchWidth.setAccessible(true);
            fieldThumbWidth.setAccessible(true);
            fieldSwitchMinWidth.setAccessible(true);
            fieldTrackDrawable.setAccessible(true);

            Drawable trackDrawable = (Drawable) fieldTrackDrawable.get(this);
            final int thumbWidth = fieldThumbWidth.getInt(this);
            final int switchMinWidth = fieldSwitchMinWidth.getInt(this);

            // Compute track width from drawable if given.
            final int trackWidth;
            if (trackDrawable != null) {
                trackWidth = trackDrawable.getIntrinsicWidth();
            } else {
                trackWidth = 0;
            }

            // Set switch width to max of actual trackWidth or switchMinWidth, if these are shorter
            // than thumb, do not change default of 2 x thumbWidth + padding
            final int switchTrackWidth;
            if (switchMinWidth > thumbWidth || trackWidth > thumbWidth) {
                switchTrackWidth = Math.max(switchMinWidth, trackWidth);
                fieldSwitchWidth.setInt(this, switchTrackWidth);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to modfiy switch track length.", e);
        }
    }
}
