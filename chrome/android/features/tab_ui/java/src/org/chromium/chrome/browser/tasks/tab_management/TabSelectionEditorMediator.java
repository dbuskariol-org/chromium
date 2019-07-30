// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tasks.tab_management;

import android.content.Context;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.view.View;

import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.browser.snackbar.Snackbar;
import org.chromium.chrome.browser.snackbar.SnackbarManager;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.TabModel;
import org.chromium.chrome.browser.tabmodel.TabModelFilter;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.tabmodel.TabModelSelectorTabModelObserver;
import org.chromium.chrome.browser.tabmodel.TabModelUtils;
import org.chromium.chrome.browser.tasks.tabgroup.TabGroupModelFilter;
import org.chromium.chrome.browser.widget.selection.SelectionDelegate;
import org.chromium.chrome.tab_ui.R;
import org.chromium.ui.modelutil.PropertyModel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * This class is the mediator that contains all business logic for TabSelectionEditor component. It
 * is also responsible for resetting the selectable tab grid based on visibility property.
 */
class TabSelectionEditorMediator
        implements TabSelectionEditorCoordinator.TabSelectionEditorController {
    // TODO(977271): Unify similar interfaces in other components that used the TabListCoordinator.
    /**
     * Interface for resetting the selectable tab grid.
     */
    interface ResetHandler {
        /**
         * Handles the reset event.
         * @param tabs List of {@link Tab}s to reset.
         */
        void resetWithListOfTabs(@Nullable List<Tab> tabs);
    }

    private final Context mContext;
    private final TabModelSelector mTabModelSelector;
    private final ResetHandler mResetHandler;
    private final PropertyModel mModel;
    private final SelectionDelegate<Integer> mSelectionDelegate;
    private final SnackbarManager.SnackbarManageable mSnackbarManageable;
    private Snackbar mSnackbar;
    private boolean mAbleToShowSnackbar;
    private final TabModelSelectorTabModelObserver mTabModelSelectorTabModelObserver;

    private final View.OnClickListener mNavigationClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            RecordUserAction.record("TabMultiSelect.Cancelled");
            hide();
        }
    };

    private final View.OnClickListener mGroupButtonOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            List<Tab> selectedTabs = new ArrayList<>();

            for (int tabId : mSelectionDelegate.getSelectedItems()) {
                selectedTabs.add(
                        TabModelUtils.getTabById(mTabModelSelector.getCurrentModel(), tabId));
            }

            Tab destinationTab = getDestinationTab(selectedTabs);

            TabGroupModelFilter tabGroupModelFilter =
                    (TabGroupModelFilter) mTabModelSelector.getTabModelFilterProvider()
                            .getCurrentTabModelFilter();

            tabGroupModelFilter.mergeListOfTabsToGroup(selectedTabs, destinationTab, false, true);

            hide();

            RecordUserAction.record("TabMultiSelect.Done");
            RecordUserAction.record("TabGroup.Created.TabMultiSelect");
        }
    };

    TabSelectionEditorMediator(Context context, TabModelSelector tabModelSelector,
            ResetHandler resetHandler, PropertyModel model,
            SelectionDelegate<Integer> selectionDelegate,
            SnackbarManager.SnackbarManageable snackbarManageable) {
        mContext = context;
        mTabModelSelector = tabModelSelector;
        mResetHandler = resetHandler;
        mModel = model;
        mSelectionDelegate = selectionDelegate;
        mSnackbarManageable = snackbarManageable;

        mModel.set(
                TabSelectionEditorProperties.TOOLBAR_NAVIGATION_LISTENER, mNavigationClickListener);
        mModel.set(TabSelectionEditorProperties.TOOLBAR_ACTION_BUTTON_LISTENER,
                mGroupButtonOnClickListener);

        mTabModelSelectorTabModelObserver =
                new TabModelSelectorTabModelObserver(mTabModelSelector) {
                    @Override
                    public void didAddTab(Tab tab, int type) {
                        if (isEditorVisible()) hide();
                    }
                };
    }

    public void destroy() {
        mTabModelSelectorTabModelObserver.destroy();
    }

    /**
     * @return The {@link Tab} that has the greatest index in TabModel among the given list of tabs.
     */
    private Tab getDestinationTab(List<Tab> tabs) {
        int greatestIndex = TabModel.INVALID_TAB_INDEX;
        for (int i = 0; i < tabs.size(); i++) {
            int index = TabModelUtils.getTabIndexById(
                    mTabModelSelector.getCurrentModel(), tabs.get(i).getId());
            greatestIndex = Math.max(index, greatestIndex);
        }
        return mTabModelSelector.getCurrentModel().getTabAt(greatestIndex);
    }

    @Override
    public void hide() {
        mModel.set(TabSelectionEditorProperties.ITEM_DECORATION, null);
        mResetHandler.resetWithListOfTabs(null);
        if (mAbleToShowSnackbar) dismissSnackbar();
        mModel.set(TabSelectionEditorProperties.IS_VISIBLE, false);
    }

    private boolean isEditorVisible() {
        return mModel.get(TabSelectionEditorProperties.IS_VISIBLE);
    }

    public void resetSnackbar(@Nullable Snackbar snackbar) {
        mAbleToShowSnackbar = snackbar != null;
        mSnackbar = snackbar;
    }

    private void showSnackbar() {
        if (!mAbleToShowSnackbar) return;
        mSnackbarManageable.getSnackbarManager().showSnackbar(mSnackbar);
    }

    private void dismissSnackbar() {
        if (!mAbleToShowSnackbar) return;
        mSnackbarManageable.getSnackbarManager().dismissAllSnackbars();
    }

    @Override
    public void show() {
        mSelectionDelegate.setSelectionModeEnabledForZeroItems(true);
        mModel.set(TabSelectionEditorProperties.TOOLBAR_ACTION_BUTTON_TEXT_RESOURCE,
                R.string.tab_selection_editor_group);
        mModel.set(TabSelectionEditorProperties.TOOLBAR_ACTION_BUTTON_LISTENER,
                mGroupButtonOnClickListener);
        mModel.set(TabSelectionEditorProperties.TOOLBAR_ACTION_BUTTON_ENABLING_THRESHOLD, 1);
        mModel.set(TabSelectionEditorProperties.IS_VISIBLE, true);
        mResetHandler.resetWithListOfTabs(getTabsToShow());
    }

    private List<Tab> getTabsToShow() {
        List<Tab> tabs = new ArrayList<>();
        TabModelFilter tabModelFilter =
                mTabModelSelector.getTabModelFilterProvider().getCurrentTabModelFilter();
        for (int i = 0; i < tabModelFilter.getCount(); i++) {
            Tab tab = tabModelFilter.getTabAt(i);
            // TODO(977302): This filtered out the tabs that has related tabs. This should be done
            // at the TabModelFilter.
            if (tabModelFilter.getRelatedTabList(tab.getId()).size() == 1) {
                tabs.add(tab);
            }
        }
        return tabs;
    }

    @Override
    public void showListOfTabs(List<Tab> tabs, int selectedTabCount,
            RecyclerView.ItemDecoration itemDecoration, int actionButtonResource,
            TabSelectionEditorToolbar.ActionButtonListener actionButtonOnClickListener,
            boolean isAppendToDefaultListener, int actionButtonEnablingThreshold) {
        if (tabs == null) {
            mResetHandler.resetWithListOfTabs(tabs);
            mModel.set(TabSelectionEditorProperties.IS_VISIBLE, true);
            return;
        }

        mModel.set(TabSelectionEditorProperties.ITEM_DECORATION, itemDecoration);
        mModel.set(TabSelectionEditorProperties.TOOLBAR_ACTION_BUTTON_TEXT_RESOURCE,
                actionButtonResource);
        mModel.set(TabSelectionEditorProperties.TOOLBAR_ACTION_BUTTON_ENABLING_THRESHOLD,
                actionButtonEnablingThreshold);
        View.OnClickListener onClickListener = view -> {
            if (isAppendToDefaultListener) {
                mGroupButtonOnClickListener.onClick(view);
            }
            actionButtonOnClickListener.run(mSelectionDelegate);

            hide();
        };
        mModel.set(TabSelectionEditorProperties.TOOLBAR_ACTION_BUTTON_LISTENER, onClickListener);

        Set<Integer> selectedTabIds = new HashSet<>();
        for (int i = 0; i < selectedTabCount && i < tabs.size(); i++) {
            selectedTabIds.add(tabs.get(i).getId());
        }
        mSelectionDelegate.setSelectedItems(selectedTabIds);

        if (selectedTabCount % 2 != 0) {
            tabs.add(selectedTabCount, tabs.get(0));
        }

        mResetHandler.resetWithListOfTabs(tabs);
        mModel.set(TabSelectionEditorProperties.IS_VISIBLE, true);
        if (mAbleToShowSnackbar) showSnackbar();
    }

    @Override
    public boolean handleBackPressed() {
        if (!isEditorVisible()) return false;
        hide();
        RecordUserAction.record("TabMultiSelect.Cancelled");
        return true;
    }
}
