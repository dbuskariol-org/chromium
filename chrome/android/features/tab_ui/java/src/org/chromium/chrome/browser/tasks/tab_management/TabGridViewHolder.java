// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tasks.tab_management;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.annotation.IntDef;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.chromium.chrome.tab_ui.R;
import org.chromium.ui.widget.ButtonCompat;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.ref.WeakReference;

/**
 * {@link RecyclerView.ViewHolder} for tab grid. Owns the tab info card
 * and the associated view hierarchy.
 */
class TabGridViewHolder extends RecyclerView.ViewHolder {
    @IntDef({TabGridViewItemType.NORMAL_TAB, TabGridViewItemType.SELECTION_TAB})
    @Retention(RetentionPolicy.SOURCE)
    public @interface TabGridViewItemType {
        int NORMAL_TAB = 0;
        int SELECTION_TAB = 1;
        int NUM_ENTRIES = 2;
    }

    private int mTabId;
    public final TabGridView tabGridView;
    private static WeakReference<Bitmap> sCloseButtonBitmapWeakRef;
    public final ImageView favicon;
    public final TextView title;
    public final ImageView thumbnail;
    public final ImageView closeButton;
    public final ImageView selectedView;
    public final ImageView unselectedView;
    public final ButtonCompat createGroupButton;
    public final View backgroundView;

    private TabGridViewHolder(TabGridView itemView, int itemViewType) {
        super(itemView);
        this.thumbnail = itemView.findViewById(R.id.tab_thumbnail);
        this.title = itemView.findViewById(R.id.tab_title);
        // TODO(yuezhanggg): Remove this when the strip is properly tinted. (crbug/939915)
        title.setTextColor(ContextCompat.getColor(
                itemView.getContext(), org.chromium.chrome.R.color.default_text_color_dark));
        this.favicon = itemView.findViewById(R.id.tab_favicon);
        this.closeButton = itemView.findViewById(R.id.close_button);
        this.createGroupButton = itemView.findViewById(R.id.create_group_button);
        this.backgroundView = itemView.findViewById(R.id.background_view);
        tabGridView = (TabGridView) itemView;
        this.selectedView = itemView.findViewById(R.id.selected);
        this.unselectedView = itemView.findViewById(R.id.unselected);

        if (itemViewType == TabGridViewItemType.NORMAL_TAB) {
            this.closeButton.setVisibility(View.VISIBLE);
        } else {
            this.unselectedView.setVisibility(View.VISIBLE);
        }

        if (sCloseButtonBitmapWeakRef == null || sCloseButtonBitmapWeakRef.get() == null) {
            int closeButtonSize =
                    (int) itemView.getResources().getDimension(R.dimen.tab_grid_close_button_size);
            Bitmap bitmap = BitmapFactory.decodeResource(
                    itemView.getResources(), org.chromium.chrome.R.drawable.btn_close);
            sCloseButtonBitmapWeakRef = new WeakReference<>(
                    Bitmap.createScaledBitmap(bitmap, closeButtonSize, closeButtonSize, true));
            bitmap.recycle();
        }
        this.closeButton.setImageBitmap(sCloseButtonBitmapWeakRef.get());
    }

    public static TabGridViewHolder create(ViewGroup parent, int itemViewType) {
        TabGridView view = (TabGridView) LayoutInflater.from(parent.getContext())
                                   .inflate(R.layout.tab_grid_card_item, parent, false);
        return new TabGridViewHolder(view, itemViewType);
    }

    public void setTabId(int tabId) {
        mTabId = tabId;
    }

    public int getTabId() {
        return mTabId;
    }

    public void resetThumbnail() {
        thumbnail.setImageResource(0);
        thumbnail.setMinimumHeight(thumbnail.getWidth());
    }
}
