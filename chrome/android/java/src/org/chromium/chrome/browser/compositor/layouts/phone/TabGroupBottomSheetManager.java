// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.compositor.layouts.phone;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.collection.CollectionManager;
import org.chromium.chrome.browser.compositor.layouts.content.TabContentManager;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.widget.bottomsheet.BottomSheet;
import org.chromium.chrome.browser.widget.bottomsheet.BottomSheetController;
import org.chromium.chrome.browser.widget.bottomsheet.EmptyBottomSheetObserver;

import java.util.List;

/**
 * This is a class that is responsible for managing the tab group bottom sheet.
 */
public class TabGroupBottomSheetManager {
    public interface OnTabGroupBottomSheetInteractionListener { void onTabClicked(int id); }

    private final BottomSheetController mBottomSheetController;
    private TabGroupBottomSheetContent mContent;
    private TabGroupBottomSheetRecyclerViewAdapter mRecyclerViewAdapter;

    private TabModelSelector mTabModelSelector;
    private TabContentManager mTabContentManager;

    private OnTabGroupBottomSheetInteractionListener mInteractionListener;

    // Setting width and height to one. Since createBitmap requires width and height to be
    // greater than 0. This is a hack around creating a grey background bitmap when thumbnail is not
    // available by the time the callback given by TabContentManager#getTabThumbnailFromCallback
    // runs and the callback runs before the Bottom sheet shows.
    private int mDefaultTabThumbnailHeight = 1;
    private int mDefaultTabThumbnailWidth = 1;

    private static TabGroupBottomSheetManager sInstance;

    @SuppressLint("SetTextI18n")
    public TabGroupBottomSheetManager(Context context, BottomSheetController bottomSheetController,
            OnTabGroupBottomSheetInteractionListener interactionListener) {
        mBottomSheetController = bottomSheetController;
        mInteractionListener = interactionListener;

        RecyclerView tabGroupBottomSheetView = (RecyclerView) LayoutInflater.from(context).inflate(
                R.layout.tabgroup_bottom_sheet_recycler_view_layout,
                mBottomSheetController.getBottomSheet(), false);

        // TODO(meiliang) : should use a different layout for tab group toolbar follow the spec
        View toolbarView = LayoutInflater.from(context).inflate(
                R.layout.collection_toolbar, mBottomSheetController.getBottomSheet(), false);

        View closeButton = toolbarView.findViewById(R.id.close_button);
        closeButton.setOnClickListener(v -> { closeTabGroupSheet(); });

        mBottomSheetController.getBottomSheet().addObserver(new EmptyBottomSheetObserver() {
            @Override
            public void onSheetClosed(@BottomSheet.StateChangeReason int reason) {
                CollectionManager.turnOnCollectionManager();
            }
        });

        TextView title = toolbarView.findViewById(R.id.title);
        // TODO(meiliang) : set title to be number of tab within group
        title.setText("Tab Group");

        mRecyclerViewAdapter = new TabGroupBottomSheetRecyclerViewAdapter((int tabId) -> {
            closeTabGroupSheet();
            mInteractionListener.onTabClicked(tabId);
        });

        tabGroupBottomSheetView.setLayoutManager(new GridLayoutManager(context, 2));
        tabGroupBottomSheetView.setHasFixedSize(true);
        tabGroupBottomSheetView.setAdapter(mRecyclerViewAdapter);

        tabGroupBottomSheetView.post(() -> {
            mDefaultTabThumbnailWidth = tabGroupBottomSheetView.getWidth();
            mDefaultTabThumbnailHeight = tabGroupBottomSheetView.getHeight();
        });

        mContent = new TabGroupBottomSheetContent(tabGroupBottomSheetView, toolbarView);
        sInstance = this;
    }

    static public TabGroupBottomSheetManager getInstance() {
        return sInstance;
    }

    /**
     * Sets the managers needed for the tab group bottom sheet to get information from outside.
     * @param modelSelector The {@link TabModelSelector} to get the tab group list
     * @param tabContentManager The {@link TabContentManager} to get tab thumbnail
     */
    public void setTabModelSelector(
            TabModelSelector modelSelector, TabContentManager tabContentManager) {
        mTabModelSelector = modelSelector;
        mTabContentManager = tabContentManager;
    }

    /**
     * Show the tab group bottom sheet to HALF state.
     * @param tabId The currently selected tab id
     */
    public void showTabGroupSheet(int tabId) {
        CollectionManager.turnOffCollectionManager();
        getAllThumbnailWithGroup(tabId);
        mBottomSheetController.requestShowContent(mContent, false);
        mBottomSheetController.expandSheet();
    }

    private void closeTabGroupSheet() {
        mBottomSheetController.hideContent(mContent, true);
    }

    private void getAllThumbnailWithGroup(int tabId) {
        mRecyclerViewAdapter.needUpdate();
        List<Integer> tabIdList = mTabModelSelector.getCurrentModel()
                                          .getTabGroupList(mTabContentManager)
                                          .getAllTabIdsInSameGroup(tabId);
        for (int id : tabIdList) {
            // TODO(meiliang) : create placeholder first then fetch bitmap in background, and add
            // cache
            mTabContentManager.getTabThumbnailFromCallback(id, result -> {
                if (result == null) {
                    Bitmap bitmap = Bitmap.createBitmap(mDefaultTabThumbnailWidth,
                            mDefaultTabThumbnailHeight, Bitmap.Config.ARGB_8888);
                    bitmap.eraseColor(Color.GRAY);
                    mRecyclerViewAdapter.add(new TabGroupBottomSheetItem(id, bitmap));
                } else {
                    mRecyclerViewAdapter.add(new TabGroupBottomSheetItem(id, result));
                }
            });
        }
    }
}
