// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tasks.tab_management;

import android.view.View;

import org.chromium.chrome.browser.tabmodel.TabList;
import org.chromium.chrome.browser.tasks.tab_management.suggestions.TabSuggestion;

/**
 * Interface for the TabSuggestionEditorLayout.
 */
public interface TabSuggestionEditorLayout {
    /**
     * Resets TabSuggestionEditor with the given TabList.
     * @param tabList
     * @param suggestedTabCount
     * @param actionListener
     */
    void resetTabSuggestion(
            TabList tabList, int suggestedTabCount, View.OnClickListener actionListener);

    /**
     * Resets TabSuggestionEditor with given {@link TabSuggestion}.
     * @param suggestion {@link TabSuggestion} to use.
     */
    void resetTabSuggestion(TabSuggestion suggestion);

    /**
     * Shows the TabSuggestionEditor.
     */
    void show();
}
