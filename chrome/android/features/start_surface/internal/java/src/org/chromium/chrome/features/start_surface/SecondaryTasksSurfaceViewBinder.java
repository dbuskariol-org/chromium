// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.features.start_surface;

import static org.chromium.chrome.features.start_surface.StartSurfaceProperties.IS_SECONDARY_SURFACE_VISIBLE;
import static org.chromium.chrome.features.start_surface.StartSurfaceProperties.IS_SHOWING_OVERVIEW;
import static org.chromium.chrome.features.start_surface.StartSurfaceProperties.TOP_BAR_HEIGHT;

import android.support.v4.widget.NestedScrollView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.chromium.ui.modelutil.PropertyKey;
import org.chromium.ui.modelutil.PropertyModel;

/** The binder controls the display of the secondary {@link TasksView} in its parent. */
class SecondaryTasksSurfaceViewBinder {
    /**
     * The view holder holds the parent view, the scroll container and the secondary tasks surface
     * view.
     */
    public static class ViewHolder {
        public final ViewGroup parentView;
        public final NestedScrollView scrollContainer;
        public final ViewGroup tasksSurfaceView;

        ViewHolder(ViewGroup parentView, NestedScrollView scrollContainer,
                ViewGroup tasksSurfaceView) {
            this.parentView = parentView;
            this.scrollContainer = scrollContainer;
            this.tasksSurfaceView = tasksSurfaceView;
        }
    }

    public static void bind(PropertyModel model, ViewHolder viewHolder, PropertyKey propertyKey) {
        if (IS_SECONDARY_SURFACE_VISIBLE == propertyKey) {
            setVisibility(viewHolder, model,
                    model.get(IS_SHOWING_OVERVIEW) && model.get(IS_SECONDARY_SURFACE_VISIBLE));
        } else if (IS_SHOWING_OVERVIEW == propertyKey) {
            setVisibility(viewHolder, model,
                    model.get(IS_SHOWING_OVERVIEW) && model.get(IS_SECONDARY_SURFACE_VISIBLE));
        }
    }

    private static void setVisibility(
            ViewHolder viewHolder, PropertyModel model, boolean isShowing) {
        if (isShowing && viewHolder.tasksSurfaceView.getParent() == null) {
            FrameLayout.LayoutParams layoutParams =
                    (FrameLayout.LayoutParams) viewHolder.scrollContainer.getLayoutParams();
            layoutParams.topMargin = model.get(TOP_BAR_HEIGHT);
            viewHolder.parentView.addView(viewHolder.scrollContainer);
            viewHolder.scrollContainer.addView(viewHolder.tasksSurfaceView);
        }

        viewHolder.scrollContainer.setVisibility(isShowing ? View.VISIBLE : View.GONE);
    }
}
