// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tasks.tab_management;

import android.content.Context;
import android.graphics.Rect;
import android.support.annotation.NonNull;

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
        public void onDismissNoAction(Object actionData) {}

        public void showTabSuggestionBar(TabSuggestion tabSuggestion) {
            mSnackbarManageable.getSnackbarManager().showSnackbar(
                    Snackbar.make(buildSnackBarContent(tabSuggestion), this,
                                    Snackbar.TYPE_PERSISTENT, Snackbar.UMA_TEST_SNACKBAR)
                            .setAction("Review", tabSuggestion));
        }

        public void dismissTabSuggestionBar() {
            mSnackbarManageable.getSnackbarManager().dismissSnackbars(this);
        }

        private String getActionString(TabSuggestion suggestion) {
            switch (suggestion.getAction()) {
                case TabSuggestion.TabSuggestionAction.GROUP:
                    return "Group";
                case TabSuggestion.TabSuggestionAction.CLOSE:
                    return "Close";
                default:
                    assert false;
            }
            return "";
        }

        private String buildSnackBarContent(TabSuggestion suggestion) {
            String action = "";
            String suffix = "";
            switch (suggestion.getAction()) {
                case TabSuggestion.TabSuggestionAction.GROUP:
                    action = getActionString(suggestion);
                    suffix = "related tabs";
                    break;
                case TabSuggestion.TabSuggestionAction.CLOSE:
                    action = getActionString(suggestion);
                    suffix = "old tabs";
                    break;
                default:
                    assert false : "Unsupported TabSuggestion action";
            }
            return action + " " + suggestion.getTabsInfo().size() + " " + suffix;
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
            TabCreatorManager tabCreatorManager, Runnable backPress,
            SnackbarManager.SnackbarManageable snackbarManageable) {
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
        HistoryNavigationLayout navigation =
                compositorViewHolder.findViewById(R.id.history_navigation);
        navigation.setNavigationDelegate(HistoryNavigationDelegate.createForTabSwitcher(
                context, backPress, tabModelSelector::getCurrentTab));
        mContainerViewChangeProcessor = PropertyModelChangeProcessor.create(containerViewModel,
                mTabGridCoordinator.getContainerView(), TabGridContainerViewBinder::bind);

        mSnackbarManageable = snackbarManageable;
        mTabSuggestionEditorCoordinator = new TabSuggestionEditorCoordinator(
                context, compositorViewHolder, tabModelSelector, tabContentManager);
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
    public void prepareOverview() {
        mTabGridCoordinator.prepareOverview();
        mMediator.prepareOverview();
    }

    @Override
    @NonNull
    public Rect getThumbnailLocationOfCurrentTab() {
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
    public void resetWithTabList(TabList tabList) {
        List<Tab> tabs = null;
        if (tabList != null) {
            tabs = new ArrayList<>();
            for (int i = 0; i < tabList.getCount(); i++) {
                tabs.add(tabList.getTabAt(i));
            }
        }
        mTabGridCoordinator.resetWithListOfTabs(tabs);
    }

    @Override
    public void destroy() {
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
    }
}
