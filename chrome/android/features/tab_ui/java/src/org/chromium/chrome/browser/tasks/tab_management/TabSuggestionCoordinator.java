// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tasks.tab_management;

import android.app.Activity;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.support.v7.widget.RecyclerView;
import android.view.View;

import org.chromium.base.ContextUtils;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.browser.help.HelpAndFeedback;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.snackbar.Snackbar;
import org.chromium.chrome.browser.snackbar.SnackbarManager;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.TabList;
import org.chromium.chrome.browser.tabmodel.TabModelFilter;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.tabmodel.TabModelUtils;
import org.chromium.chrome.browser.tasks.tab_management.suggestions.TabContext;
import org.chromium.chrome.browser.tasks.tab_management.suggestions.TabSuggestion;
import org.chromium.chrome.browser.tasks.tab_management.suggestions.TabSuggestionsOrchestrator;
import org.chromium.chrome.browser.tasks.tab_management.suggestions.TabSuggestionsRanker;
import org.chromium.chrome.browser.widget.selection.SelectionDelegate;
import org.chromium.chrome.tab_ui.R;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A coordinator for serving a Tab Suggestion.
 */
public class TabSuggestionCoordinator {
    private static final String REVIEW_SUGGESTIONS_EVENT = "GridTabSwitcher.TappedReviewSuggestion";
    private static final String SUGGESTION_SHOWN_EVENT = "EnteredTabSwitcher.SuggestionShown";
    private static final String SUGGESTION_DISMISSED_EVENT = "SuggestionDismissed";
    private static final String TAB_SUGGESTION_TAB_COUNT = "GridTabSwitcher.SuggestedTabsCount";

    private static final String TAB_GROUP_SUGGESTION_ACCEPTED = "AcceptedTabGroupSuggestion";
    private static final String TAB_CLOSE_SUGGESTION_ACCEPTED = "AcceptedTabCloseSuggestion";
    private static final String TAB_SUGGESTION_MODIFIED_SELECTION = "ModifiedTabListSelection";

    class TabSuggestionReviewBarController implements SnackbarManager.SnackbarController {
        @Override
        public void onAction(Object actionData) {
            showTabSuggestion((TabSuggestion) actionData);
        }

        @Override
        public void onDismissNoAction(Object actionData) {
            TabSuggestion suggestion = (TabSuggestion) actionData;
            recordUserAction(suggestion.getProviderName(), SUGGESTION_DISMISSED_EVENT);
        }

        public void showTabSuggestionBar(TabSuggestion tabSuggestion) {
            recordUserAction(tabSuggestion.getProviderName(), SUGGESTION_SHOWN_EVENT);
            mActivitySnackbarManageable.getSnackbarManager().showSnackbar(
                    Snackbar.make(buildSnackBarContent(tabSuggestion), this,
                                    Snackbar.TYPE_PERSISTENT, Snackbar.UMA_TEST_SNACKBAR)
                            .setAction("Review", tabSuggestion)
                            .setSingleLine(false));
        }

        public void dismissTabSuggestionBar() {
            mActivitySnackbarManageable.getSnackbarManager().dismissSnackbars(this);
        }

        private int getSuggestionStringResourceId(TabSuggestion suggestion) {
            if ((TabSuggestionsRanker.SuggestionProviders.STALE_TABS_SUGGESTION_PROVIDER)
                            .equals(suggestion.getProviderName())) {
                return R.string.tab_suggestion_snackbar_close_stale;
            } else if ((TabSuggestionsRanker.SuggestionProviders.DUPLICATE_PAGE_SUGGESTION_PROVIDER)
                               .equals(suggestion.getProviderName())) {
                return R.string.tab_suggestion_snackbar_close_duplicates;
            } else {
                assert suggestion.getAction() == TabSuggestion.TabSuggestionAction.GROUP;
                return R.string.tab_suggestion_snackbar_group_related;
            }
        }

        private String buildSnackBarContent(TabSuggestion suggestion) {
            return ContextUtils.getApplicationContext().getResources().getString(
                    getSuggestionStringResourceId(suggestion), suggestion.getTabsInfo().size());
        }
    }

    class TabSuggestionFeedbackBarController implements SnackbarManager.SnackbarController {
        @Override
        public void onAction(Object actionData) {
            String helpContextId = HelpAndFeedback.getHelpContextIdFromUrl(mActivity, null, false);
            HelpAndFeedback.getInstance(mActivity).show(
                    mActivity, helpContextId, Profile.getLastUsedProfile(), null);
        }

        @Override
        public void onDismissNoAction(Object actionData) {}

        public Snackbar buildSnackbar() {
            return Snackbar.make("", this, Snackbar.TYPE_PERSISTENT, Snackbar.UMA_TEST_SNACKBAR)
                    .setAction("Send feedback about this suggestion", null)
                    .setSingleLine(true);
        }
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
            int lastSelectedViewPosition = mSuggestedTabCount - 1;

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
            // Offset one view if mSuggestedTabCount can't fill the last row in the selected
            // section.
            int lastSelectedViewPosition = mSuggestedTabCount % mSpanCount == 0
                    ? mSuggestedTabCount - 1
                    : mSuggestedTabCount;

            if (viewPosition == lastSelectedViewPosition - 1
                    || viewPosition == lastSelectedViewPosition) {
                outRect.bottom =
                        (int) mContext.getResources().getDimension(R.dimen.tab_list_card_padding)
                        + mDivider.getIntrinsicHeight();
            } else if (viewPosition == lastSelectedViewPosition + 1
                    || viewPosition == lastSelectedViewPosition + 2) {
                outRect.top =
                        (int) mContext.getResources().getDimension(R.dimen.tab_list_card_padding);
            }
        }
    }

    private final Activity mActivity;
    private final Context mContext;
    private final TabModelSelector mTabModelSelector;
    private final SnackbarManager.SnackbarManageable mActivitySnackbarManageable;
    private final TabSelectionEditorCoordinator mTabSelectionEditorCoordinator;
    private final TabSuggestionReviewBarController mTabSuggestionReviewBarController =
            new TabSuggestionReviewBarController();
    private final TabSuggestionFeedbackBarController mTabSuggestionFeedbackBarController =
            new TabSuggestionFeedbackBarController();
    private int mSuggestedTabCount;
    private String mCurrentProviderName;
    // TODO(meiliang): Need to support landscape mode.
    private int mSpanCount = 2;
    private final RecyclerView.ItemDecoration mItemDecoration;

    private final TabSelectionEditorToolbar.ActionButtonListener mCloseSuggestionListener;
    private final TabSelectionEditorToolbar.ActionButtonListener mGroupSuggestionListener;

    public TabSuggestionCoordinator(Activity activity, Context context,
            TabModelSelector tabModelSelector,
            SnackbarManager.SnackbarManageable snackbarManageable,
            TabSelectionEditorCoordinator tabSelectionEditorCoordinator) {
        mActivity = activity;
        mContext = context;
        mTabModelSelector = tabModelSelector;
        mTabSelectionEditorCoordinator = tabSelectionEditorCoordinator;
        mActivitySnackbarManageable = snackbarManageable;

        mItemDecoration = new SuggestedTabDividerItemDecoration();

        mCloseSuggestionListener = selectionDelegate -> {
            List<Integer> selectedItems = selectionDelegate.getSelectedItemsAsList();
            List<Tab> selectedTabs = new ArrayList<>();
            TabList currentTabList = mTabModelSelector.getCurrentModel();
            for (int i = 0; i < selectedItems.size(); i++) {
                selectedTabs.add(TabModelUtils.getTabById(currentTabList, selectedItems.get(i)));
            }

            mTabModelSelector.getCurrentModel().closeMultipleTabs(selectedTabs, true);

            recordUserAction(mCurrentProviderName, TAB_CLOSE_SUGGESTION_ACCEPTED);
        };

        mGroupSuggestionListener = selectionDelegate -> {
            recordUserAction(mCurrentProviderName, TAB_GROUP_SUGGESTION_ACCEPTED);
        };
    }

    /**
     * Reset the tab suggestion editor with the given {@link TabSuggestion}.
     * @param tabSuggestion The {@link TabSuggestion} to show in the editor.
     */
    public void showTabSuggestion(TabSuggestion tabSuggestion) {
        List<TabContext.TabInfo> suggestedTabsInfo = tabSuggestion.getTabsInfo();
        mSuggestedTabCount = suggestedTabsInfo.size();
        mCurrentProviderName = tabSuggestion.getProviderName();
        List<Tab> tabs = new ArrayList<>();
        Set<Integer> suggestedTabIds = new HashSet<>();

        for (int i = 0; i < suggestedTabsInfo.size(); i++) {
            int currentTabId = suggestedTabsInfo.get(i).getId();
            suggestedTabIds.add(currentTabId);
            tabs.add(mTabModelSelector.getTabById(currentTabId));
        }

        List<Tab> nonSuggestedTab = getNonSuggestedTabs(suggestedTabIds);
        tabs.addAll(nonSuggestedTab);

        int actionButtonTextResource = R.string.tab_selection_editor_group;
        TabSelectionEditorToolbar.ActionButtonListener actionButtonListener = null;
        boolean isAppendToDefaultListener = false;
        int actionButtonEnablingThreshold = 0;
        switch (tabSuggestion.getAction()) {
            case TabSuggestion.TabSuggestionAction.GROUP:
                isAppendToDefaultListener = true;
                actionButtonListener = mGroupSuggestionListener;
                actionButtonEnablingThreshold = 1;
                break;
            case TabSuggestion.TabSuggestionAction.CLOSE:
                actionButtonTextResource = R.string.tab_suggestion_editor_close_button;
                actionButtonListener = mCloseSuggestionListener;
                break;
            default:
                assert false;
        }

        mTabSelectionEditorCoordinator.resetSnackbar(
                mTabSuggestionFeedbackBarController.buildSnackbar());
        mTabSelectionEditorCoordinator.getController().showListOfTabs(tabs,
                suggestedTabsInfo.size(), mItemDecoration, actionButtonTextResource,
                actionButtonListener, isAppendToDefaultListener, actionButtonEnablingThreshold);
        SelectionDelegate<Integer> selectionDelegate =
                mTabSelectionEditorCoordinator.getSelectionDelegate();

        selectionDelegate.addObserver(new SelectionDelegate.SelectionObserver<Integer>() {
            @Override
            public void onSelectionStateChange(List<Integer> selectedItems) {
                // Don't record the first onSelectionStateChanged event (initial state).
                if (selectedItems.size() == mSuggestedTabCount) return;

                recordUserAction(mCurrentProviderName, TAB_SUGGESTION_MODIFIED_SELECTION);

                // We only record this once the first time a user modifies the selection.
                selectionDelegate.removeObserver(this);
            }
        });

        final String providerName = tabSuggestion.getProviderName();
        recordCountHistogram(
                providerName, TAB_SUGGESTION_TAB_COUNT, tabSuggestion.getTabsInfo().size());
        recordUserAction(providerName, REVIEW_SUGGESTIONS_EVENT);
    }

    private List<Tab> getNonSuggestedTabs(Set<Integer> suggestedTabIds) {
        List<Tab> nonSuggestedIndividualTabs = new ArrayList<>();
        TabModelFilter tabModelFilter =
                mTabModelSelector.getTabModelFilterProvider().getCurrentTabModelFilter();

        for (int i = 0; i < tabModelFilter.getCount(); i++) {
            Tab tab = tabModelFilter.getTabAt(i);
            List<Tab> relatedTabs = tabModelFilter.getRelatedTabList(tab.getId());

            if (relatedTabs.size() == 1
                    && (suggestedTabIds == null || !suggestedTabIds.contains(tab.getId()))) {
                nonSuggestedIndividualTabs.add(tab);
            }
        }

        return nonSuggestedIndividualTabs;
    }

    public void showEmptySuggestion() {
        mTabSelectionEditorCoordinator.getController().showListOfTabs(
                null, 0, null, 0, null, false, 0);
    }

    public void showTabSuggestionSnackbar(TabSuggestion tabSuggestion) {
        mTabSuggestionReviewBarController.showTabSuggestionBar(tabSuggestion);
    }

    public void dismissTabSuggetionSnackbar() {
        mTabSuggestionReviewBarController.dismissTabSuggestionBar();
    }

    private void recordUserAction(String providerName, String userActionEvent) {
        if (providerName == null || providerName.isEmpty()) return;
        final String userAction =
                String.format("%s.%s.%s", TabSuggestionsOrchestrator.TAB_SUGGESTIONS_UMA_PREFIX,
                        providerName, userActionEvent);
        RecordUserAction.record(userAction);
    }

    private void recordCountHistogram(String providerName, String histogramName, int count) {
        if (providerName == null || providerName.isEmpty()) return;
        final String histogram = String.format("%s.%s.%s",
                TabSuggestionsOrchestrator.TAB_SUGGESTIONS_UMA_PREFIX, providerName, histogramName);
        RecordHistogram.recordCountHistogram(histogram, count);
    }
}
