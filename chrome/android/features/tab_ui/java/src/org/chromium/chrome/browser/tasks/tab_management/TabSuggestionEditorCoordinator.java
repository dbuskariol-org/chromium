// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tasks.tab_management;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.support.v7.widget.RecyclerView;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.PopupWindow;

import org.chromium.base.ObserverList;
import org.chromium.chrome.browser.compositor.layouts.content.TabContentManager;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.TabList;
import org.chromium.chrome.browser.tabmodel.TabModelFilter;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.tabmodel.TabModelUtils;
import org.chromium.chrome.browser.tasks.tab_management.suggestions.TabSuggestion;
import org.chromium.chrome.browser.widget.selection.SelectableListLayout;
import org.chromium.chrome.browser.widget.selection.SelectionDelegate;
import org.chromium.chrome.tab_ui.R;
import org.chromium.ui.modelutil.PropertyModel;
import org.chromium.ui.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Coordinator that is responsible for showing a grid of tabs and a toolbar for the TabSuggestion.
 */
public class TabSuggestionEditorCoordinator implements TabSuggestionEditorLayout {
    static final String COMPONENT_NAME = "TabSuggestionEditor";
    private final Context mContext;
    private final TabListCoordinator mTabSuggestionListCoordinator;
    private final SelectableListLayout<Integer> mTabSuggestionEditorLayout;
    private TabSuggestionEditorToolbar mTabSuggestionEditorToolbar;
    private final SelectionDelegate<Integer> mTabSelectionDelegate = new SelectionDelegate<>();
    private PopupWindow mPopupWindow;
    private final TabModelSelector mTabModelSelector;
    private final View mParentView;

    private final View.OnClickListener mClosingSuggestedTabsListener;
    private final View.OnClickListener mGroupingSuggestedTabsListener;
    private int mSuggestedTabCount;
    // Not handling landscape mode for now.
    private int mSpanCount = 2;

    ObserverList<ActionListener> mActionListeners = new ObserverList<>();

    public interface ActionListener {
        void doneAction(int actionType, Map<Integer, Integer> tabIdsToTabIndex);
    }

    public TabSuggestionEditorCoordinator(Context context, View parentView,
            TabModelSelector tabModelSelector, TabContentManager tabContentManager) {
        mContext = context;
        mParentView = parentView;
        mTabModelSelector = tabModelSelector;
        mTabSuggestionListCoordinator = new TabListCoordinator(TabListCoordinator.TabListMode.GRID,
                context, tabModelSelector, tabContentManager::getTabThumbnailWithCallback, null,
                false, null, null, this::getSelectionDelegate, this::getItemViewType, null, null, false,
                R.layout.tab_list_recycler_view_layout, COMPONENT_NAME);
        mTabSuggestionEditorLayout = LayoutInflater.from(context)
                                             .inflate(R.layout.tab_suggestion_editor_layout, null)
                                             .findViewById(R.id.selectable_list);
        initializeSelectableListLayout();

        DisplayMetrics displayMetrics = new DisplayMetrics();
        ((WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE))
                .getDefaultDisplay()
                .getMetrics(displayMetrics);
        mPopupWindow = new PopupWindow(mTabSuggestionEditorLayout, displayMetrics.widthPixels,
                displayMetrics.heightPixels);

        mClosingSuggestedTabsListener = view -> {
            List<Tab> selectedTabs = getSelectedTabs();

            mTabModelSelector.getCurrentModel().closeMultipleTabs(selectedTabs, true);

            if (mPopupWindow.isShowing()) mPopupWindow.dismiss();

            // TODO(meiliang): remove later!
            CharSequence text = "Closed" + selectedTabs.size() + " Tab(s)";
            Toast toast = Toast.makeText(mContext, text, Toast.LENGTH_SHORT);
            toast.show();
        };

        mGroupingSuggestedTabsListener = view -> {
            Map<Integer, Integer> tabIdsToTabIndexMap = new HashMap<>();
            List<Tab> selectedTabs = getSelectedTabs();
            Tab lastSelectedTab = selectedTabs.get(selectedTabs.size() - 1);
            tabIdsToTabIndexMap.put(lastSelectedTab.getId(),
                    TabModelUtils.getTabIndexById(
                            mTabModelSelector.getCurrentModel(), lastSelectedTab.getId()));

            for (int i = 0; i < selectedTabs.size() - 1; i++) {
                int newTabIndex = TabModelUtils.getTabIndexById(
                        mTabModelSelector.getCurrentModel(), lastSelectedTab.getId());
                Tab selectedTab = selectedTabs.get(i);
                int oldTabIndex = TabModelUtils.getTabIndexById(
                        mTabModelSelector.getCurrentModel(), selectedTab.getId());
                tabIdsToTabIndexMap.put(selectedTab.getId(), oldTabIndex);

                selectedTab.setRootId(lastSelectedTab.getRootId());
                mTabModelSelector.getCurrentModel().moveTab(selectedTab.getId(), newTabIndex);
            }

            if (mPopupWindow.isShowing()) mPopupWindow.dismiss();

            for (ActionListener listener : mActionListeners) {
                listener.doneAction(TabSuggestion.TabSuggestionAction.GROUP, tabIdsToTabIndexMap);
            }

            // TODO(meiliang): remove later!
            CharSequence text = "Grouped" + selectedTabs.size() + " Tab(s)";
            Toast toast = Toast.makeText(mContext, text, Toast.LENGTH_SHORT);
            toast.show();
        };
    }

    private List<Tab> getSelectedTabs() {
        List<Integer> selectedItems = mTabSelectionDelegate.getSelectedItemsAsList();
        List<Tab> tabs = new ArrayList<>();
        TabList currentTabList = mTabModelSelector.getCurrentModel();
        for (int i = 0; i < selectedItems.size(); i++) {
            tabs.add(TabModelUtils.getTabById(currentTabList, selectedItems.get(i)));
        }
        return tabs;
    }

    private void initializeSelectableListLayout() {
        RecyclerView recyclerView = mTabSuggestionEditorLayout.initializeRecyclerView(
                mTabSuggestionListCoordinator.getContainerView().getAdapter(),
                mTabSuggestionListCoordinator.getContainerView());

        recyclerView.addItemDecoration(new SuggestedTabDividerItemDecoration());

        mTabSuggestionEditorToolbar =
                (TabSuggestionEditorToolbar) mTabSuggestionEditorLayout.initializeToolbar(
                        R.layout.tab_suggestion_editor_toolbar, mTabSelectionDelegate,
                        org.chromium.chrome.R.string.tab_suggestion_editor_select_tabs, 0, 0, null,
                        false, true);

        mTabSuggestionEditorLayout.initializeEmptyView(null,
                R.string.tab_suggestion_editor_empty_suggestion,
                R.string.tab_suggestion_editor_empty_suggestion);

        mTabSuggestionEditorLayout.updateEmptyViewVisibility();

        mTabSuggestionEditorToolbar.setExitListener(view -> mPopupWindow.dismiss());

        mTabSuggestionListCoordinator.resetWithListOfTabs(null);
    }

    public SelectionDelegate<Integer> getSelectionDelegate() {
        return mTabSelectionDelegate;
    }

    public int getItemViewType(PropertyModel item) {
        return TabGridViewHolder.TabGridViewItemType.SELECTION_TAB;
    }

    // TODO(meiliang): remove later! Currently, this is used for showing manual grouping mode.
    @Override
    public void resetTabSuggestion(
            TabList tabList, int suggestedTabCount, View.OnClickListener actionListener) {
        List<Tab> tabs = new ArrayList<>();
        Set<Integer> selectedSet = new HashSet<>();

        for (int i = 0; i < tabList.getCount(); i++) {
            tabs.add(tabList.getTabAt(i));
            if (i < suggestedTabCount) {
                selectedSet.add(tabList.getTabAt(i).getId());
            }
        }
        mTabSuggestionListCoordinator.resetWithListOfTabs(tabs);

        // TODO(meiliang): Correct action listener
        mTabSuggestionEditorToolbar.updateActionButton(view -> {
            List selectedItems = mTabSelectionDelegate.getSelectedItemsAsList();
            CharSequence text = "Grouped " + selectedItems.size() + " Tab(s)";
            Toast toast = Toast.makeText(mContext, text, Toast.LENGTH_SHORT);
            if (mPopupWindow.isShowing()) mPopupWindow.dismiss();
            toast.show();
        }, R.string.tab_suggestion_editor_group_button);

        mTabSelectionDelegate.setSelectedItems(selectedSet);
    }

    @Override
    public void resetTabSuggestion(TabSuggestion suggestion) {
        List<Tab> suggestedTabs = suggestion.getTabs();
        mSuggestedTabCount = suggestedTabs.size();

        Set<Integer> suggestedTabIds = new HashSet<>();
        for (int i = 0; i < suggestedTabs.size(); i++) {
            suggestedTabIds.add(suggestedTabs.get(i).getId());
        }

        List<Tab> tabs = new ArrayList<>();
        tabs.addAll(suggestedTabs);
        List<Tab> nonSuggestedTab = getNonSuggestedTabs(suggestedTabIds);
        if (nonSuggestedTab.size() > 0) {
            if (mSuggestedTabCount % mSpanCount != 0) {
                tabs.add(nonSuggestedTab.get(0));
            }
            tabs.addAll(nonSuggestedTab);
        }

        mTabSuggestionListCoordinator.resetWithListOfTabs(tabs);
        mTabSelectionDelegate.setSelectedItems(suggestedTabIds);

        switch (suggestion.getAction()) {
            case TabSuggestion.TabSuggestionAction.GROUP:
                mTabSuggestionEditorToolbar.updateActionButton(mGroupingSuggestedTabsListener,
                        R.string.tab_suggestion_editor_group_button);
                break;
            case TabSuggestion.TabSuggestionAction.CLOSE:
                mTabSuggestionEditorToolbar.updateActionButton(
                        mClosingSuggestedTabsListener, R.string.tab_suggestion_editor_close_button);
                break;
            default:
                assert false;
        }
    }

    private List<Tab> getNonSuggestedTabs(Set<Integer> suggestedTabIds) {
        List<Tab> nonSuggestedIndividualTabs = new ArrayList<>();
        TabModelFilter tabModelFilter =
                mTabModelSelector.getTabModelFilterProvider().getCurrentTabModelFilter();

        for (int i = 0; i < tabModelFilter.getCount(); i++) {
            Tab tab = tabModelFilter.getTabAt(i);
            List<Tab> relatedTabs = tabModelFilter.getRelatedTabList(tab.getId());

            if (relatedTabs.size() == 1 && (suggestedTabIds == null || !suggestedTabIds.contains(tab.getId()))) {
                nonSuggestedIndividualTabs.add(tab);
            }
        }

        return nonSuggestedIndividualTabs;
    }

    @Override
    public void show() {
        mPopupWindow.showAtLocation(mParentView, Gravity.CENTER, 0, 0);
    }

    public void addActionListener(ActionListener listener) {
        mActionListeners.addObserver(listener);
    }

    class SuggestedTabDividerItemDecoration extends RecyclerView.ItemDecoration {
        private Drawable mDivider;
        private final int[] mATTRS = new int[] {android.R.attr.listDivider};
        private final Rect mBounds = new Rect();

        public SuggestedTabDividerItemDecoration() {
            TypedArray a = mContext.obtainStyledAttributes(mATTRS);
            mDivider = a.getDrawable(0);
            a.recycle();
        }

        @Override
        public void onDraw(Canvas c, RecyclerView parent, RecyclerView.State state) {
            int childCount = parent.getChildCount();
            int lastSelectedViewPosition = mSuggestedTabCount - 1 ;

            for (int i = 0; i < childCount; i++) {
                View view = parent.getChildAt(i);
                int position = parent.getChildAdapterPosition(view);

                if (position == lastSelectedViewPosition && position != childCount - 1) {
                    parent.getDecoratedBoundsWithMargins(view, mBounds);
                    int dividerBottom = mBounds.bottom;
                    int dividerTop = dividerBottom - mDivider.getIntrinsicHeight();
                    mDivider.setBounds(0, dividerTop, parent.getRight(), dividerBottom);
                    mDivider.draw(c);
                }

                if (mSuggestedTabCount % mSpanCount != 0 && position == mSuggestedTabCount) {
                    view.setVisibility(View.INVISIBLE);
                } else {
                    view.setVisibility(View.VISIBLE);
                }
            }
        }

        @Override
        public void getItemOffsets(
                Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
            if (mSuggestedTabCount == 0) return;

            int viewPosition = parent.getChildAdapterPosition(view);
            // Offset one view if mSuggestedTabCount can't fill the last row in the selected section.
            int lastSelectedViewPosition = mSuggestedTabCount % mSpanCount == 0 ? mSuggestedTabCount - 1 : mSuggestedTabCount;

            if (viewPosition == lastSelectedViewPosition - 1 || viewPosition == lastSelectedViewPosition) {
                outRect.bottom =
                        (int) mContext.getResources().getDimension(R.dimen.tab_list_card_padding)
                                + mDivider.getIntrinsicHeight();
            } else if (viewPosition == lastSelectedViewPosition + 1 || viewPosition == lastSelectedViewPosition + 2) {
                outRect.top =
                        (int) mContext.getResources().getDimension(R.dimen.tab_list_card_padding);
            }
        }
    }
}
