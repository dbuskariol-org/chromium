// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tasks;

import static org.chromium.chrome.browser.tasks.TasksSurfaceProperties.FAKE_SEARCH_BOX_CLICK_LISTENER;
import static org.chromium.chrome.browser.tasks.TasksSurfaceProperties.FAKE_SEARCH_BOX_TEXT;
import static org.chromium.chrome.browser.tasks.TasksSurfaceProperties.FAKE_SEARCH_BOX_TEXT_WATCHER;
import static org.chromium.chrome.browser.tasks.TasksSurfaceProperties.IS_FAKE_SEARCH_BOX_VISIBLE;
import static org.chromium.chrome.browser.tasks.TasksSurfaceProperties.IS_INCOGNITO;
import static org.chromium.chrome.browser.tasks.TasksSurfaceProperties.IS_MV_TILES_VISIBLE;
import static org.chromium.chrome.browser.tasks.TasksSurfaceProperties.IS_TAB_CAROUSEL;
import static org.chromium.chrome.browser.tasks.TasksSurfaceProperties.IS_VOICE_RECOGNITION_BUTTON_VISIBLE;
import static org.chromium.chrome.browser.tasks.TasksSurfaceProperties.MORE_TABS_CLICK_LISTENER;
import static org.chromium.chrome.browser.tasks.TasksSurfaceProperties.VOICE_SEARCH_BUTTON_CLICK_LISTENER;

import android.view.View;
import android.widget.TextView;

import org.chromium.chrome.tab_ui.R;
import org.chromium.ui.modelutil.PropertyKey;
import org.chromium.ui.modelutil.PropertyModel;

// The view binder of the tasks surface view.
class TasksViewBinder {
    public static void bind(PropertyModel model, TasksView view, PropertyKey propertyKey) {
        if (propertyKey == IS_INCOGNITO) {
            view.setIncognitoMode(
                    model.get(IS_INCOGNITO), model.get(IS_VOICE_RECOGNITION_BUTTON_VISIBLE));
        } else if (propertyKey == IS_FAKE_SEARCH_BOX_VISIBLE) {
            // TODO(crbug.com/982018): Investigate why setText("") is not enough in
            // 'afterTextChanged'.
            ((TextView) view.findViewById(R.id.search_box_text)).setText("");
            View fakeSearchBox = view.findViewById(R.id.search_box);
            fakeSearchBox.setVisibility(
                    model.get(IS_FAKE_SEARCH_BOX_VISIBLE) ? View.VISIBLE : View.GONE);
            fakeSearchBox.requestLayout();
        } else if (propertyKey == IS_MV_TILES_VISIBLE) {
            view.findViewById(R.id.mv_tiles_container)
                    .setVisibility(model.get(IS_MV_TILES_VISIBLE) ? View.VISIBLE : View.GONE);
        } else if (propertyKey == IS_TAB_CAROUSEL) {
            view.setIsTabCarousel(model.get(IS_TAB_CAROUSEL));
        } else if (propertyKey == IS_VOICE_RECOGNITION_BUTTON_VISIBLE) {
            view.findViewById(R.id.voice_search_button)
                    .setVisibility(model.get(IS_VOICE_RECOGNITION_BUTTON_VISIBLE)
                                            && !model.get(IS_INCOGNITO)
                                    ? View.VISIBLE
                                    : View.GONE);
        } else if (propertyKey == MORE_TABS_CLICK_LISTENER) {
            view.setMoreTabsOnClicklistener(model.get(MORE_TABS_CLICK_LISTENER));
        } else if (propertyKey == FAKE_SEARCH_BOX_CLICK_LISTENER) {
            view.findViewById(R.id.search_box_text)
                    .setOnClickListener(model.get(FAKE_SEARCH_BOX_CLICK_LISTENER));
        } else if (propertyKey == FAKE_SEARCH_BOX_TEXT) {
            ((TextView) view.findViewById(R.id.search_box_text))
                    .setText(model.get(FAKE_SEARCH_BOX_TEXT));
        } else if (propertyKey == FAKE_SEARCH_BOX_TEXT_WATCHER) {
            ((TextView) view.findViewById(R.id.search_box_text))
                    .addTextChangedListener(model.get(FAKE_SEARCH_BOX_TEXT_WATCHER));
        } else if (propertyKey == VOICE_SEARCH_BUTTON_CLICK_LISTENER) {
            view.findViewById(R.id.voice_search_button)
                    .setOnClickListener(model.get(VOICE_SEARCH_BUTTON_CLICK_LISTENER));
        }
    }
}