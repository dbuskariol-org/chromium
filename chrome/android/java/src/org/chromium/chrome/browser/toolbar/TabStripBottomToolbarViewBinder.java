// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.toolbar;

import android.support.v7.view.ContextThemeWrapper;
import android.support.v7.widget.AppCompatImageButton;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.compositor.scene_layer.ScrollingBottomViewSceneLayer;
import org.chromium.chrome.browser.modelutil.PropertyKey;
import org.chromium.chrome.browser.modelutil.PropertyModelChangeProcessor;
import org.chromium.chrome.browser.toolbar.TabStripToolbarButtonSlotData.TabStripToolbarButtonData;

import java.util.List;

/**
 * This class is responsible for pushing updates to both the Android view and the compositor
 * component of the bottom toolbar. These updates are pulled from the {@link
 * TabStripBottomToolbarModel} when a notification of an update is received.
 */
public class TabStripBottomToolbarViewBinder
        implements PropertyModelChangeProcessor.ViewBinder<TabStripBottomToolbarModel,
                TabStripBottomToolbarViewBinder.ViewHolder, PropertyKey> {
    /**
     * A wrapper class that holds a {@link ViewGroup} (the toolbar view) and a composited layer to
     * be used with the {@link TabStripBottomToolbarViewBinder}.
     */
    public static class ViewHolder {
        /** A handle to the Android View based version of the toolbar. */
        public final ScrollingBottomViewResourceFrameLayout toolbarRoot;

        /** A handle to the composited bottom toolbar layer. */
        public ScrollingBottomViewSceneLayer sceneLayer;

        /** Cached {@link LinearLayout} of the scroll container between the buttons. */
        public final LinearLayout scrollContainer;

        /** Cached {@link HorizontalScrollView} of the scroll view between the buttons. */
        public final HorizontalScrollView scrollView;

        /**
         * @param toolbarRootView The Android View based toolbar.
         */
        public ViewHolder(ScrollingBottomViewResourceFrameLayout toolbarRootView) {
            toolbarRoot = toolbarRootView;
            scrollContainer = toolbarRoot.findViewById(R.id.scroll_container);
            scrollView = toolbarRoot.findViewById(R.id.scroll_view);
        }
    }

    /**
     * Build a binder that handles interaction between the model and the views that make up the
     * bottom toolbar.
     */
    public TabStripBottomToolbarViewBinder() {}

    @Override
    public final void bind(
            TabStripBottomToolbarModel model, ViewHolder view, PropertyKey propertyKey) {
        if (TabStripBottomToolbarModel.Y_OFFSET == propertyKey) {
            assert view.sceneLayer != null;
            view.sceneLayer.setYOffset(model.get(TabStripBottomToolbarModel.Y_OFFSET));
        } else if (TabStripBottomToolbarModel.ANDROID_VIEW_VISIBLE == propertyKey) {
            view.toolbarRoot.setVisibility(
                    model.get(TabStripBottomToolbarModel.ANDROID_VIEW_VISIBLE) ? View.VISIBLE
                                                                               : View.INVISIBLE);
        } else if (TabStripBottomToolbarModel.COMPOSITED_VIEW_VISIBLE == propertyKey) {
            view.sceneLayer.setIsVisible(
                    model.get(TabStripBottomToolbarModel.COMPOSITED_VIEW_VISIBLE));
            model.get(TabStripBottomToolbarModel.LAYOUT_MANAGER).requestUpdate();
        } else if (TabStripBottomToolbarModel.LAYOUT_MANAGER == propertyKey) {
            assert view.sceneLayer == null;
            view.sceneLayer = new ScrollingBottomViewSceneLayer(
                    view.toolbarRoot, view.toolbarRoot.getTopShadowHeight());
            model.get(TabStripBottomToolbarModel.LAYOUT_MANAGER)
                    .addSceneOverlayToBack(view.sceneLayer);
        } else if (TabStripBottomToolbarModel.TOOLBAR_SWIPE_LAYOUT == propertyKey) {
            assert view.sceneLayer != null;
            model.get(TabStripBottomToolbarModel.TOOLBAR_SWIPE_LAYOUT)
                    .setBottomToolbarSceneLayers(new ScrollingBottomViewSceneLayer(view.sceneLayer),
                            new ScrollingBottomViewSceneLayer(view.sceneLayer));
        } else if (TabStripBottomToolbarModel.RESOURCE_MANAGER == propertyKey) {
            model.get(TabStripBottomToolbarModel.RESOURCE_MANAGER)
                    .getDynamicResourceLoader()
                    .registerResource(
                            view.toolbarRoot.getId(), view.toolbarRoot.getResourceAdapter());
        } else if (TabStripBottomToolbarModel.SCROLL_VIEW_DATA == propertyKey) {
            updateScrollContainerButtons(view.scrollContainer, view.scrollView,
                    model.get(TabStripBottomToolbarModel.SCROLL_VIEW_DATA));
        } else if (TabStripBottomToolbarModel.PRIMARY_COLOR == propertyKey) {
            // For now keep the same color for incognito.
            //            view.toolbarRoot.findViewById(R.id.bottom_sheet_toolbar)
            //                    .setBackgroundColor(
            //                        model.getValue(TabStripBottomToolbarModel.PRIMARY_COLOR));
            //            updateButtonDrawable(view.firstTintedImageButton,
            //                    model.getValue(TabStripBottomToolbarModel.FIRST_BUTTON_DATA));
            //            updateButtonDrawable(view.secondTintedImageButton,
            //                    model.getValue(TabStripBottomToolbarModel.SECOND_BUTTON_DATA));
        } else {
            assert false : "Unhandled property detected in BottomToolbarViewBinder!";
        }
    }

    private static void updateScrollContainerButtons(LinearLayout scrollContainer,
            HorizontalScrollView scrollView, List<TabStripToolbarButtonData> buttons) {
        if (buttons == null || buttons.isEmpty()) {
            scrollContainer.setVisibility(View.INVISIBLE);
        } else {
            scrollContainer.removeAllViews();
            for (TabStripToolbarButtonData buttonData : buttons) {
                AppCompatImageButton button = new AppCompatImageButton(scrollView.getContext());
                ContextThemeWrapper themeContext = new ContextThemeWrapper(
                        scrollView.getContext(), R.style.BottomToolbarButton);
                RelativeLayout.LayoutParams lp =
                        new RelativeLayout.LayoutParams(themeContext, null);
                button.setLayoutParams(lp);
                button.setBackgroundColor(ApiCompatibilityUtils.getColor(
                        scrollView.getResources(), R.color.modern_primary_color));
                buttonData.setButton(button);
                scrollContainer.addView(button);
                if (buttonData.isSelected()) {
                    scrollView.requestChildFocus(button, button);
                }
            }
            scrollContainer.setVisibility(View.VISIBLE);
        }
    }
}
