// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.
package org.chromium.chrome.browser.tasks.tab_management;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.browser.widget.selection.SelectableItemView;
import org.chromium.chrome.tab_ui.R;
import org.chromium.ui.widget.ButtonCompat;

/**
 * Holds the view for tab grid.
 */
public class TabGridView extends SelectableItemView<Integer> {
    public ImageView favicon;
    public TextView title;
    public ImageView thumbnail;
    public ImageView closeButton;
    public ImageView selectedView;
    public ImageView unSelectedView;
    public ButtonCompat createGroupButton;
    public View backgroundView;
    public Drawable selectedTabForeground;

    public TabGridView(Context context, AttributeSet attrs) {
        super(context, attrs);
        selectedTabForeground = new InsetDrawable(
                ResourcesCompat.getDrawable(getResources(), R.drawable.selected_tab_background,
                        getContext().getTheme()),
                (int) getResources().getDimension(R.dimen.tab_list_selected_inset));
    }

    @Override
    protected void onClick() {
        super.onClick(this);
    }

    @Override
    protected void updateView() {
        boolean isViewSelected = isChecked();
        selectedView.setVisibility(isViewSelected ? View.VISIBLE : View.GONE);
        unSelectedView.setVisibility(isViewSelected ? View.GONE : View.VISIBLE);
        if (isViewSelected) {
            mCheckDrawable.start();
        }
        setForeground(isViewSelected ? selectedTabForeground : null);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        this.thumbnail = findViewById(R.id.tab_thumbnail);
        this.title = findViewById(R.id.tab_title);
        this.favicon = findViewById(R.id.tab_favicon);
        this.closeButton = findViewById(R.id.close_button);
        DrawableCompat.setTint(this.closeButton.getDrawable(),
                ApiCompatibilityUtils.getColor(
                        getResources(), org.chromium.chrome.R.color.light_active_color));
        this.createGroupButton = findViewById(R.id.create_group_button);
        this.backgroundView = findViewById(R.id.background_view);

        // For TabGridViewHolder.TabGridViewItemType.SELECTION_TAB
        this.selectedView = findViewById(R.id.selected);
        this.unSelectedView = findViewById(R.id.unselected);
        selectedView.setImageDrawable(mCheckDrawable);
        ApiCompatibilityUtils.setImageTintList(selectedView, mIconColorList);
    }
}
