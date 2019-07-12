// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tasks.tab_management;

import android.app.Activity;
import android.content.Context;
import android.graphics.Rect;
import android.support.annotation.NonNull;

import org.chromium.base.ContextUtils;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.compositor.CompositorViewHolder;
import org.chromium.chrome.browser.compositor.layouts.content.TabContentManager;
import org.chromium.chrome.browser.fullscreen.ChromeFullscreenManager;
import org.chromium.chrome.browser.gesturenav.HistoryNavigationDelegate;
import org.chromium.chrome.browser.gesturenav.HistoryNavigationLayout;
import org.chromium.chrome.browser.lifecycle.ActivityLifecycleDispatcher;
import org.chromium.chrome.browser.lifecycle.Destroyable;
import org.chromium.chrome.browser.snackbar.Snackbar;
import org.chromium.chrome.browser.snackbar.SnackbarManager;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.TabCreatorManager;
import org.chromium.chrome.browser.tabmodel.TabList;
import org.chromium.chrome.browser.tabmodel.TabModel;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.tabmodel.TabModelUtils;
import org.chromium.chrome.browser.tasks.tab_management.suggestions.TabSuggestion;
import org.chromium.chrome.browser.tasks.tab_management.suggestions.TabSuggestionsOrchestrator;
import org.chromium.chrome.browser.tasks.tab_management.suggestions.TabSuggestionsRanker;
import org.chromium.chrome.browser.toolbar.ToolbarManager;
import org.chromium.chrome.browser.util.FeatureUtilities;
import org.chromium.ui.modelutil.PropertyModel;
import org.chromium.ui.modelutil.PropertyModelChangeProcessor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Parent coordinator that is responsible for showing a grid of tabs for the main TabSwitcher UI.
 */
public class GridTabSwitcherCoordinator
        implements Destroyable, GridTabSwitcher, GridTabSwitcherMediator.ResetHandler {
    final static String COMPONENT_NAME = "GridTabSwitcher";

    private static final String REVIEW_SUGGESTIONS_EVENT = "GridTabSwitcher.TappedReviewSuggestion";
    private static final String SUGGESTION_SHOWN_EVENT = "EnteredTabSwitcher.SuggestionShown";
    private static final String SUGGESTION_DISMISSED_EVENT = "SuggestionDismissed";
    private static final String TAB_SUGGESTION_TAB_COUNT = "GridTabSwitcher.SuggestedTabsCount";

    private final PropertyModelChangeProcessor mContainerViewChangeProcessor;
    private final ActivityLifecycleDispatcher mLifecycleDispatcher;
    private final TabListCoordinator mTabGridCoordinator;
    private final GridTabSwitcherMediator mMediator;
    private final MultiThumbnailCardProvider mMultiThumbnailCardProvider;
    private final TabGridDialogCoordinator mTabGridDialogCoordinator;
    private final TabSuggestionEditorCoordinator mTabSuggestionEditorCoordinator;
    private final SnackbarManager.SnackbarManageable mSnackbarManageable;
    private final TabModelSelector mTabModelSelector;

    class TabSuggestionBarController implements SnackbarManager.SnackbarController {
        @Override
        public void onAction(Object actionData) {
            showTabSuggestionEditor((TabSuggestion) actionData);
        }

        @Override
        public void onDismissNoAction(Object actionData) {
            TabSuggestion suggestion = (TabSuggestion) actionData;
            recordUserAction(suggestion.getProviderName(), SUGGESTION_DISMISSED_EVENT);
        }

        public void showTabSuggestionBar(TabSuggestion tabSuggestion) {
            recordUserAction(tabSuggestion.getProviderName(), SUGGESTION_SHOWN_EVENT);
            mSnackbarManageable.getSnackbarManager().showSnackbar(
                    Snackbar.make(buildSnackBarContent(tabSuggestion), this,
                                    Snackbar.TYPE_PERSISTENT, Snackbar.UMA_TEST_SNACKBAR)
                            .setAction("Review", tabSuggestion)
                            .setSingleLine(false));
        }

        public void dismissTabSuggestionBar() {
            mSnackbarManageable.getSnackbarManager().dismissSnackbars(this);
        }

        private int getSuggestionStringResourceId(TabSuggestion suggestion) {
            if ((TabSuggestionsRanker.SuggestionProviders.STALE_TABS_SUGGESTION_PROVIDER)
                            .equals(suggestion.getProviderName())) {
                return org.chromium.chrome.R.string.tab_suggestion_snackbar_close_stale;
            } else if ((TabSuggestionsRanker.SuggestionProviders.DUPLICATE_PAGE_SUGGESTION_PROVIDER)
                               .equals(suggestion.getProviderName())) {
                return org.chromium.chrome.R.string.tab_suggestion_snackbar_close_duplicates;
            } else {
                assert suggestion.getAction() == TabSuggestion.TabSuggestionAction.GROUP;
                return org.chromium.chrome.R.string.tab_suggestion_snackbar_group_related;
            }
        }

        private String buildSnackBarContent(TabSuggestion suggestion) {
            return ContextUtils.getApplicationContext().getResources().getString(
                           getSuggestionStringResourceId(suggestion),
                           suggestion.getTabsInfo().size())
                    + getProviderString(suggestion);
        }

        private String getProviderString(TabSuggestion suggestion) {
            return " - " + suggestion.getProviderName();
        }
    }

    class UndoGroupBarController implements SnackbarManager.SnackbarController {
        @Override
        public void onAction(Object actionData) {
            undoAction((Map<Integer, Integer>) actionData);
        }

        private void undoAction(Map<Integer, Integer> tabIdToTabIndex) {
            for (Map.Entry<Integer, Integer> entry : tabIdToTabIndex.entrySet()) {
                Tab tab = TabModelUtils.getTabById(
                        mTabModelSelector.getCurrentModel(), entry.getKey());
                tab.setRootId(tab.getId());
                mTabModelSelector.getCurrentModel().moveTab(tab.getId(), entry.getValue());
            }
        }

        @Override
        public void onDismissNoAction(Object actionData) {}

        public void showUndoBar(Map<Integer, Integer> tabIdToTabIndex) {
            mSnackbarManageable.getSnackbarManager().showSnackbar(
                    Snackbar.make(buildSnackBarContent(tabIdToTabIndex), this, Snackbar.TYPE_ACTION,
                                    Snackbar.UMA_TEST_SNACKBAR)
                            .setAction("Undo", tabIdToTabIndex));
        }

        private String buildSnackBarContent(Map<Integer, Integer> tabIdToTabIndex) {
            return tabIdToTabIndex.size() + " tabs grouped";
        }

        public void dismissUndoBar() {
            mSnackbarManageable.getSnackbarManager().dismissSnackbars(this);
        }
    }

    private final TabSuggestionBarController mTabSuggestionBarController =
            new TabSuggestionBarController();

    private final UndoGroupBarController mUndoGroupBarController = new UndoGroupBarController();

    public GridTabSwitcherCoordinator(Context context,
            ActivityLifecycleDispatcher lifecycleDispatcher, ToolbarManager toolbarManager,
            TabModelSelector tabModelSelector, TabContentManager tabContentManager,
            CompositorViewHolder compositorViewHolder, ChromeFullscreenManager fullscreenManager,
            TabCreatorManager tabCreatorManager, Runnable backPress, Activity activity) {
        PropertyModel containerViewModel = new PropertyModel(TabListContainerProperties.ALL_KEYS);
        TabListMediator.GridCardOnClickListenerProvider gridCardOnClickListenerProvider;
        if (FeatureUtilities.isTabGroupsAndroidUiImprovementsEnabled()) {
            mTabGridDialogCoordinator = new TabGridDialogCoordinator(context, tabModelSelector,
                    tabContentManager, tabCreatorManager, new CompositorViewHolder(context), this);

            mMediator = new GridTabSwitcherMediator(this, containerViewModel, tabModelSelector,
                    fullscreenManager, compositorViewHolder,
                    mTabGridDialogCoordinator.getResetHandler(), mTabSuggestionBarController);

            gridCardOnClickListenerProvider = mMediator::getGridCardOnClickListener;
        } else {
            mTabGridDialogCoordinator = null;

            mMediator = new GridTabSwitcherMediator(this, containerViewModel, tabModelSelector,
                    fullscreenManager, compositorViewHolder, null, mTabSuggestionBarController);

            gridCardOnClickListenerProvider = null;
        }

        mMultiThumbnailCardProvider =
                new MultiThumbnailCardProvider(context, tabContentManager, tabModelSelector);

        TabListMediator.TitleProvider titleProvider = tab -> {
            int numRelatedTabs = tabModelSelector.getTabModelFilterProvider()
                                         .getCurrentTabModelFilter()
                                         .getRelatedTabList(tab.getId())
                                         .size();
            if (numRelatedTabs == 1) return tab.getTitle();
            return context.getResources().getQuantityString(
                    R.plurals.bottom_tab_grid_title_placeholder, numRelatedTabs, numRelatedTabs);
        };

        mTabGridCoordinator = new TabListCoordinator(TabListCoordinator.TabListMode.GRID, context,
                tabModelSelector, mMultiThumbnailCardProvider, titleProvider, true,
                mMediator::getCreateGroupButtonOnClickListener, gridCardOnClickListenerProvider,
        null, this::getItemViewType,compositorViewHolder, compositorViewHolder.getDynamicResourceLoader(), true,
                org.chromium.chrome.tab_ui.R.layout.grid_tab_switcher_layout, COMPONENT_NAME);

        HistoryNavigationLayout navigation = compositorViewHolder.findViewById(
                org.chromium.chrome.tab_ui.R.id.history_navigation);

        navigation.setNavigationDelegate(HistoryNavigationDelegate.createForTabSwitcher(
                context, backPress, tabModelSelector::getCurrentTab));
        mContainerViewChangeProcessor = PropertyModelChangeProcessor.create(containerViewModel,
                mTabGridCoordinator.getContainerView(), TabGridContainerViewBinder::bind);

        mSnackbarManageable = (SnackbarManager.SnackbarManageable) activity;
        mTabSuggestionEditorCoordinator = new TabSuggestionEditorCoordinator(
                context, compositorViewHolder, tabModelSelector, tabContentManager, activity);
        mTabSuggestionEditorCoordinator.addActionListener(
                new TabSuggestionEditorCoordinator.ActionListener() {
                    @Override
                    public void doneAction(int actionType, Map<Integer, Integer> tabIdsToTabIndex) {
                        mUndoGroupBarController.showUndoBar(tabIdsToTabIndex);
                    }
                });
        mTabModelSelector = tabModelSelector;

        mLifecycleDispatcher = lifecycleDispatcher;
        mLifecycleDispatcher.register(this);
    }

    public int getItemViewType(PropertyModel item) {
        return TabGridViewHolder.TabGridViewItemType.NORMAL_TAB;
    }

    /**
     * @return GridController implementation that will can be used for controlling
     *         grid visibility changes.
     */
    @Override
    public GridController getGridController() {
        return mMediator;
    }

    @Override
    public boolean prepareOverview() {
        mTabGridCoordinator.prepareOverview();
        return mMediator.prepareOverview();
    }

    @Override
    public void postHiding() {
        mTabGridCoordinator.postHiding();
        mMediator.postHiding();
    }

    @Override
    @NonNull
    public Rect getThumbnailLocationOfCurrentTab(boolean forceUpdate) {
        if (forceUpdate) mTabGridCoordinator.updateThumbnailLocation();
        return mTabGridCoordinator.getThumbnailLocationOfCurrentTab();
    }

    @Override
    public int getResourceId() {
        return mTabGridCoordinator.getResourceId();
    }

    @Override
    public long getLastDirtyTimeForTesting() {
        return mTabGridCoordinator.getLastDirtyTimeForTesting();
    }

    /**
     * Reset the tab grid with the given {@link TabModel}. Can be null.
     * @param tabList The current {@link TabList} to show the tabs for in the grid.
     */
    @Override
    public boolean resetWithTabList(TabList tabList) {
        List<Tab> tabs = null;
        if (tabList != null) {
            tabs = new ArrayList<>();
            for (int i = 0; i < tabList.getCount(); i++) {
                tabs.add(tabList.getTabAt(i));
            }
        }
        return mTabGridCoordinator.resetWithListOfTabs(tabs);
    }

    @Override
    public void destroy() {
        mTabSuggestionEditorCoordinator.destroy();
        mTabGridCoordinator.destroy();
        mContainerViewChangeProcessor.destroy();
        mMediator.destroy();
        mLifecycleDispatcher.unregister(this);
    }

    /**
     * Reset the tab suggestion editor with the given {@link TabSuggestion}.
     * @param tabSuggestion The {@link TabSuggestion} to show in the editor.
     */
    public void showTabSuggestionEditor(TabSuggestion tabSuggestion) {
        mTabSuggestionEditorCoordinator.resetTabSuggestion(tabSuggestion);
        mTabSuggestionEditorCoordinator.show();
        final String providerName = tabSuggestion.getProviderName();
        recordCountHistogram(
                providerName, TAB_SUGGESTION_TAB_COUNT, tabSuggestion.getTabsInfo().size());
        recordUserAction(providerName, REVIEW_SUGGESTIONS_EVENT);
    }

    private void recordUserAction(String providerName, String userActionEvent) {
        final String userAction =
                String.format("%s.%s.%s", TabSuggestionsOrchestrator.TAB_SUGGESTIONS_UMA_PREFIX,
                        providerName, userActionEvent);
        RecordUserAction.record(userAction);
    }

    private void recordCountHistogram(String providerName, String histogramName, int count) {
        final String histogram = String.format("%s.%s.%s",
                TabSuggestionsOrchestrator.TAB_SUGGESTIONS_UMA_PREFIX, providerName, histogramName);
        RecordHistogram.recordCountHistogram(histogram, count);
    }
}
