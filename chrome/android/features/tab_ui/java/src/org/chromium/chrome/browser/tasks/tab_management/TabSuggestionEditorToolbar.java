// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tasks.tab_management;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.Button;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.widget.TintedDrawable;
import org.chromium.chrome.browser.widget.selection.SelectableListToolbar;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles toolbar functionality for TabSuggestionEditor.
 */

public class TabSuggestionEditorToolbar extends SelectableListToolbar<Integer> {
    private Button mActionButton;
    private OnClickListener mExitListener;

    public TabSuggestionEditorToolbar(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mActionButton = (Button) findViewById(org.chromium.chrome.R.id.action_button);

        setNavigationIcon(TintedDrawable.constructTintedDrawable(
                getContext(), R.drawable.btn_close, R.color.default_icon_color));
        setNavigationContentDescription(R.string.close);
    }

    @Override
    protected void showNormalView() {
        showSelectionView(new ArrayList<>(), true);
        if (mExitListener != null) setNavigationOnClickListener(mExitListener);
    }

    @Override
    protected void showSelectionView(List<Integer> selectedItems, boolean wasSelectionEnabled) {
        super.showSelectionView(selectedItems, wasSelectionEnabled);
        if (selectedItems.size() == 0 && mExitListener != null) setNavigationOnClickListener(mExitListener);
    }

    @Override
    public void onSelectionStateChange(List<Integer> selectedItems) {
        super.onSelectionStateChange(selectedItems);
        mActionButton.setEnabled(selectedItems.size() > 0);
    }

    public void updateActionButton(OnClickListener listener, int buttonTextResource) {
        mActionButton.setText(buttonTextResource);
        mActionButton.setOnClickListener(listener);
    }

    public void setExitListener(OnClickListener listener) {
        mExitListener = listener;
        setNavigationOnClickListener(mExitListener);
    }
}
