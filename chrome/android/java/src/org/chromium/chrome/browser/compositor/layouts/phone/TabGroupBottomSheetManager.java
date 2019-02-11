// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.compositor.layouts.phone;

import android.annotation.SuppressLint;
import android.content.Context;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import org.chromium.base.Log;
import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.collection.CollectionManager;
import org.chromium.chrome.browser.compositor.layouts.content.TabContentManager;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.tabmodel.TabModelUtils;
import org.chromium.chrome.browser.widget.bottomsheet.BottomSheet;
import org.chromium.chrome.browser.widget.bottomsheet.BottomSheetController;
import org.chromium.chrome.browser.widget.bottomsheet.EmptyBottomSheetObserver;

import java.util.List;

/**
 * This is a class that is responsible for managing the tab group bottom sheet.
 */
public class TabGroupBottomSheetManager {
    /**
     * This is an interface for item click listener.
     */
    public interface OnTabGroupBottomSheetInteractionListener {
        void onTabClicked(int id);
        default void
            onTabCloseClicked(int id, int position) {}
    }

    private final BottomSheetController mBottomSheetController;
    private TabGroupBottomSheetContent mContent;
    private TabGroupBottomSheetRecyclerViewAdapter mRecyclerViewAdapter;

    private TabModelSelector mTabModelSelector;
    private TabContentManager mTabContentManager;

    private OnTabGroupBottomSheetInteractionListener mInteractionListener;

    private ItemTouchHelper.SimpleCallback mCallback = new ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP | ItemTouchHelper.DOWN | ItemTouchHelper.START | ItemTouchHelper.END,
            0) {
        @Override
        public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder,
                RecyclerView.ViewHolder target) {
            int fromPosition = viewHolder.getAdapterPosition();
            int toPosition = target.getAdapterPosition();
            int tabId = mRecyclerViewAdapter.onItemMove(fromPosition, toPosition);
            int index = TabModelUtils.getTabIndexById(mTabModelSelector.getCurrentModel(), tabId);
            mTabModelSelector.getCurrentModel().moveTabForDrag(
                    tabId, index + (toPosition - fromPosition));
            return true;
        }

        @Override
        public void onSwiped(RecyclerView.ViewHolder viewHolder, int i) {}
    };

    private static TabGroupBottomSheetManager sInstance;

    static public TabGroupBottomSheetManager getInstance() {
        return sInstance;
    }

    public static TabGroupBottomSheetManager init(Context context,
            BottomSheetController bottomSheetController,
            OnTabGroupBottomSheetInteractionListener interactionListener,
            TabModelSelector modelSelector, TabContentManager tabContentManager) {
        if (sInstance == null) {
            sInstance = new TabGroupBottomSheetManager(context, bottomSheetController,
                    interactionListener, modelSelector, tabContentManager);
        }

        return sInstance;
    }

    @SuppressLint("SetTextI18n")
    private TabGroupBottomSheetManager(Context context, BottomSheetController bottomSheetController,
            OnTabGroupBottomSheetInteractionListener interactionListener,
            TabModelSelector modelSelector, TabContentManager tabContentManager) {
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

        mRecyclerViewAdapter = new TabGroupBottomSheetRecyclerViewAdapter(
                new OnTabGroupBottomSheetInteractionListener() {
                    @Override
                    public void onTabClicked(int tabId) {
                        closeTabGroupSheet();
                        mInteractionListener.onTabClicked(tabId);
                    }

                    @Override
                    public void onTabCloseClicked(int tabId, int position) {
                        Log.i("MeilUma", "TabGroupSwitcher.CloseTab");
                        RecordUserAction.record("TabGroupSwitcher.CloseTab");
                        Tab tabToClose = TabModelUtils.getTabById(
                                mTabModelSelector.getCurrentModel(), tabId);
                        int groupId = mTabModelSelector.getCurrentModel()
                                              .getTabGroupList(mTabContentManager)
                                              .getTabGroupId(tabId);
                        mTabModelSelector.getCurrentModel().closeTab(tabToClose);
                        int nextSelectedTabId = mTabModelSelector.getCurrentModel()
                                                        .getTabGroupList(mTabContentManager)
                                                        .getLastShownTabInGroup(groupId);
                        mRecyclerViewAdapter.remove(
                                position, nextSelectedTabId, nextSelectedTabId != tabId);
                        if (mRecyclerViewAdapter.getItemCount() == 1) {
                            closeTabGroupSheet();
                        }
                    }
                });

        tabGroupBottomSheetView.setLayoutManager(new GridLayoutManager(context, 2));
        tabGroupBottomSheetView.setHasFixedSize(true);
        tabGroupBottomSheetView.setAdapter(mRecyclerViewAdapter);
        new ItemTouchHelper(mCallback).attachToRecyclerView(tabGroupBottomSheetView);

        mContent = new TabGroupBottomSheetContent(tabGroupBottomSheetView, toolbarView);
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
            final Tab tab = TabModelUtils.getTabById(mTabModelSelector.getCurrentModel(), id);
            // TODO(meiliang) : create placeholder first then fetch bitmap in background, and add
            // cache
            mTabContentManager.getTabThumbnailWithCallback(id, result -> {
                mRecyclerViewAdapter.add(new TabGroupBottomSheetItem(
                        tab.getTitle(), tab.getFavicon(), id, result, id == tabId));
            });
        }
    }
}
