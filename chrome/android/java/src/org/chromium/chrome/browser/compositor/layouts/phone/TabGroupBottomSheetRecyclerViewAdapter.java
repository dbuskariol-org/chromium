// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.compositor.layouts.phone;

import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.chromium.base.ContextUtils;
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
    private Drawable mSelectedTabIndicatorDrawable =
            ResourcesCompat.getDrawable(ContextUtils.getApplicationContext().getResources(),
                    R.drawable.selected_tab_background, null);
    private int mCardCornerRadius =
            (int) ContextUtils.getApplicationContext().getResources().getDimension(
                    R.dimen.card_corner_radius);

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

    /**
     * Remove {@link TabGroupBottomSheetItem} at {@code index} from the list, and update the
     * selected tab to {@code nextSelectedTabId}.
     * @param removeIndex Remove the item at this index.
     * @param nextSelectedTabId The new selected tab id.
     * @param shouldUpdateSelectedTab true if removing the currently selected tab.
     */
    public void remove(int removeIndex, int nextSelectedTabId, boolean shouldUpdateSelectedTab) {
        if (shouldUpdateSelectedTab) {
            updateSelectedTab(nextSelectedTabId);
        }
        mItems.remove(removeIndex);
        notifyItemRemoved(removeIndex);
    }

    private void updateSelectedTab(int nextSelectedTabId) {
        for (int i = 0; i < mItems.size(); i++) {
            if (mItems.get(i).getTabId() == nextSelectedTabId) {
                mItems.get(i).setSelected(true);
                notifyItemChanged(i);
                break;
            }
        }
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

    public int onItemMove(int fromPosition, int toPosition) {
        TabGroupBottomSheetItem item = mItems.remove(fromPosition);
        if (toPosition == mItems.size()) {
            mItems.add(item);
        } else {
            mItems.add(toPosition, item);
        }
        notifyItemMoved(fromPosition, toPosition);
        return item.getTabId();
    }

    /**
     * {@link android.support.v7.widget.RecyclerView.ViewHolder} for tab group bottom sheet.
     */
    public class TabGroupBottomSheetRecyclerViewViewHolder extends RecyclerView.ViewHolder {
        private View mView;
        private ImageView mThumbnail;
        private ImageView mFavicon;
        private int mTabId;
        private TextView mTitle;
        private ImageView mClosebutton;

        public TabGroupBottomSheetRecyclerViewViewHolder(View itemView) {
            super(itemView);
            mView = itemView;
            mThumbnail = itemView.findViewById(R.id.tab_thumbnail);
            mTitle = itemView.findViewById(R.id.tab_title);
            mFavicon = itemView.findViewById(R.id.tab_favicon);
            mClosebutton = itemView.findViewById(R.id.close_button);
        }

        /**
         * Bind {@link TabGroupBottomSheetItem} to {@link TabGroupBottomSheetRecyclerViewViewHolder}
         * @param item Item to be bind.
         * @param delegate Callback for the view if it got clicked.
         */
        public void bindData(final TabGroupBottomSheetItem item,
                TabGroupBottomSheetManager.OnTabGroupBottomSheetInteractionListener delegate) {
            mThumbnail.setImageBitmap(item.getBitmap());
            if (item.isCurrentlySelected()) {
                if (Build.VERSION.SDK_INT >= 23) mView.setForeground(mSelectedTabIndicatorDrawable);
            } else {
                if (Build.VERSION.SDK_INT >= 23) mView.setForeground(null);
            }
            mTabId = item.getTabId();
            mView.setOnClickListener(view -> { delegate.onTabClicked(mTabId); });
            mTitle.setText(item.getTitle());
            mFavicon.setImageBitmap(item.getFavicon());
            ((CardView) mView).setRadius(mCardCornerRadius);
            mClosebutton.setOnClickListener(
                    view -> { delegate.onTabCloseClicked(mTabId, getAdapterPosition()); });
        }

        public int getTabId() {
            return mTabId;
        }
    }
}
