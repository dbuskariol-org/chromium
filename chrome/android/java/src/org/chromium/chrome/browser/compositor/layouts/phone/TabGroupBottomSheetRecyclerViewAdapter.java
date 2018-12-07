// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.compositor.layouts.phone;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import org.chromium.chrome.R;

import java.util.ArrayList;
import java.util.List;

/**
 * This class is a {@link RecyclerView.Adapter} that display {@link TabGroupBottomSheetItem}.
 */
public class TabGroupBottomSheetRecyclerViewAdapter extends RecyclerView.Adapter<
        TabGroupBottomSheetRecyclerViewAdapter.TabGroupBottomSheetRecyclerViewViewHolder> {
    private List<TabGroupBottomSheetItem> mItems = new ArrayList<>();
    private TabGroupBottomSheetManager.OnTabGroupBottomSheetInteractionListener mDelegate;

    public TabGroupBottomSheetRecyclerViewAdapter(
            TabGroupBottomSheetManager.OnTabGroupBottomSheetInteractionListener delegate) {
        mDelegate = delegate;
    }

    /**
     * Notified {@link RecyclerView.Adapter} data set is changed.
     */
    public void needUpdate() {
        mItems.clear();
        notifyDataSetChanged();
    }

    /**
     * Add {@link TabGroupBottomSheetItem} to the end of the list.
     * @param item {@link TabGroupBottomSheetItem} to be added.
     */
    public void add(TabGroupBottomSheetItem item) {
        mItems.add(item);
        notifyItemInserted(mItems.size() - 1);
    }

    @Override
    public TabGroupBottomSheetRecyclerViewViewHolder onCreateViewHolder(
            ViewGroup parent, int viewType) {
        final View view = LayoutInflater.from(parent.getContext()).inflate(viewType, parent, false);
        return new TabGroupBottomSheetRecyclerViewViewHolder(view);
    }

    @Override
    public void onBindViewHolder(TabGroupBottomSheetRecyclerViewViewHolder viewHolder, int i) {
        viewHolder.bindData(mItems.get(i), mDelegate);
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @Override
    public int getItemViewType(final int position) {
        return R.layout.tabgroup_bottom_sheet_item;
    }

    /**
     * {@link android.support.v7.widget.RecyclerView.ViewHolder} for tab group bottom sheet.
     */
    public class TabGroupBottomSheetRecyclerViewViewHolder extends RecyclerView.ViewHolder {
        private View mView;
        private ImageView mThumbnail;
        private int mTabId;

        public TabGroupBottomSheetRecyclerViewViewHolder(View itemView) {
            super(itemView);
            mView = itemView;
            mThumbnail = itemView.findViewById(R.id.tab_thumbnail);
        }

        /**
         * Bind {@link TabGroupBottomSheetItem} to {@link TabGroupBottomSheetRecyclerViewViewHolder}
         * @param thumbnail Item to be bind.
         * @param delegate Callback for the view if it got clicked.
         */
        public void bindData(final TabGroupBottomSheetItem thumbnail,
                TabGroupBottomSheetManager.OnTabGroupBottomSheetInteractionListener delegate) {
            mThumbnail.setImageBitmap(thumbnail.getBitmap());
            mTabId = thumbnail.getTabId();
            mView.setOnClickListener(view -> { delegate.onTabClicked(mTabId); });
        }

        public int getTabId() {
            return mTabId;
        }
    }
}
