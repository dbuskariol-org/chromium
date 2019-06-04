// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tasks.tab_management;

import static org.chromium.chrome.browser.tasks.tab_management.TabListContainerProperties.ANIMATE_VISIBILITY_CHANGES;
import static org.chromium.chrome.browser.tasks.tab_management.TabListContainerProperties.BOTTOM_CONTROLS_HEIGHT;
import static org.chromium.chrome.browser.tasks.tab_management.TabListContainerProperties.INITIAL_SCROLL_INDEX;
import static org.chromium.chrome.browser.tasks.tab_management.TabListContainerProperties.IS_INCOGNITO;
import static org.chromium.chrome.browser.tasks.tab_management.TabListContainerProperties.IS_VISIBLE;
import static org.chromium.chrome.browser.tasks.tab_management.TabListContainerProperties.TOP_CONTROLS_HEIGHT;
import static org.chromium.chrome.browser.tasks.tab_management.TabListContainerProperties.TOP_PADDING;
import static org.chromium.chrome.browser.tasks.tab_management.TabListContainerProperties.VISIBILITY_LISTENER;

import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.widget.Toast;

import org.chromium.base.ContextUtils;
import org.chromium.base.ObserverList;
import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.UrlConstants;
import org.chromium.chrome.browser.compositor.CompositorViewHolder;
import org.chromium.chrome.browser.fullscreen.ChromeFullscreenManager;
import org.chromium.chrome.browser.fullscreen.FullscreenManager;
import org.chromium.chrome.browser.tab.EmptyTabObserver;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.EmptyTabModelObserver;
import org.chromium.chrome.browser.tabmodel.EmptyTabModelSelectorObserver;
import org.chromium.chrome.browser.tabmodel.TabLaunchType;
import org.chromium.chrome.browser.tabmodel.TabList;
import org.chromium.chrome.browser.tabmodel.TabModel;
import org.chromium.chrome.browser.tabmodel.TabModelFilter;
import org.chromium.chrome.browser.tabmodel.TabModelObserver;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.tabmodel.TabModelSelectorObserver;
import org.chromium.chrome.browser.tabmodel.TabModelUtils;
import org.chromium.chrome.browser.tabmodel.TabSelectionType;
import org.chromium.chrome.browser.tasks.tab_management.suggestions.TabContext;
import org.chromium.chrome.browser.tasks.tab_management.suggestions.TabSuggestion;
import org.chromium.chrome.browser.tasks.tab_management.suggestions.TabSuggestions;
import org.chromium.chrome.browser.tasks.tab_management.suggestions.TabSuggestionsOrchestrator;
import org.chromium.chrome.browser.tasks.ReturnToChromeExperimentsUtil;
import org.chromium.chrome.browser.tasks.tabgroup.TabGroupModelFilter;
import org.chromium.chrome.browser.util.FeatureUtilities;
import org.chromium.content_public.browser.LoadUrlParams;
import org.chromium.ui.modelutil.PropertyModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The Mediator that is responsible for resetting the tab grid based on visibility and model
 * changes.
 */
class GridTabSwitcherMediator
        implements GridTabSwitcher.GridController, TabListRecyclerView.VisibilityListener {
    // This should be the same as TabListCoordinator.GRID_LAYOUT_SPAN_COUNT for the selected tab
    // to be on the 2nd row.
    static final int INITIAL_SCROLL_INDEX_OFFSET = 2;
    private static final int SOFT_HYPHEN_CHAR = '\u00AD';

    private static final int DEFAULT_TOP_PADDING = 0;

    private final ResetHandler mResetHandler;
    private final PropertyModel mContainerViewModel;
    private final TabModelSelector mTabModelSelector;
    private final TabModelObserver mTabModelObserver;
    private final TabModelSelectorObserver mTabModelSelectorObserver;
    private final ObserverList<OverviewModeObserver> mObservers = new ObserverList<>();
    private final ChromeFullscreenManager mFullscreenManager;
    private final TabGridDialogMediator.ResetHandler mTabGridDialogResetHandler;
    private final ChromeFullscreenManager.FullscreenListener mFullscreenListener =
            new ChromeFullscreenManager.FullscreenListener() {
                @Override
                public void onContentOffsetChanged(int offset) {}

                @Override
                public void onControlsOffsetChanged(
                        int topOffset, int bottomOffset, boolean needsAnimate) {}

                @Override
                public void onToggleOverlayVideoMode(boolean enabled) {}

                @Override
                public void onBottomControlsHeightChanged(int bottomControlsHeight) {
                    mContainerViewModel.set(BOTTOM_CONTROLS_HEIGHT, bottomControlsHeight);
                }
            };

    private final CompositorViewHolder mCompositorViewHolder;

    /**
     * In cases where a didSelectTab was due to switching models with a toggle,
     * we don't change tab grid visibility.
     */
    private boolean mShouldIgnoreNextSelect;

    private GridTabSwitcherCoordinator.TabSuggestionBarController mTabSuggestionBarController;
    private final TabSuggestions mTabSuggestionsProvider;
    private TabContext mCurrentTabContext;
    private TabSuggestion mCurrentTabSuggestion;

    /**
     * Interface to delegate resetting the tab grid.
     */
    interface ResetHandler {
        void resetWithTabList(TabList tabList);
    }

    /**
     * Basic constructor for the Mediator.
     * @param resetHandler The {@link ResetHandler} that handles reset for this Mediator.
     * @param containerViewModel The {@link PropertyModel} to keep state on the View containing the
     *         grid.
     * @param tabModelSelector {@link TabModelSelector} to observer for model and selection changes.
     * @param fullscreenManager {@link FullscreenManager} to use.
     * @param compositorViewHolder {@link CompositorViewHolder} to use.
     * @param tabSuggestionBarController
     */
    GridTabSwitcherMediator(ResetHandler resetHandler, PropertyModel containerViewModel,
            TabModelSelector tabModelSelector, ChromeFullscreenManager fullscreenManager,
            CompositorViewHolder compositorViewHolder,
            TabGridDialogMediator.ResetHandler tabGridDialogResetHandler,
            GridTabSwitcherCoordinator.TabSuggestionBarController tabSuggestionBarController) {
        mResetHandler = resetHandler;
        mContainerViewModel = containerViewModel;
        mTabModelSelector = tabModelSelector;
        mFullscreenManager = fullscreenManager;

        mTabModelSelectorObserver = new EmptyTabModelSelectorObserver() {
            @Override
            public void onTabModelSelected(TabModel newModel, TabModel oldModel) {
                mShouldIgnoreNextSelect = true;

                TabList currentTabModelFilter =
                        mTabModelSelector.getTabModelFilterProvider().getCurrentTabModelFilter();
                mResetHandler.resetWithTabList(currentTabModelFilter);
                mContainerViewModel.set(IS_INCOGNITO, currentTabModelFilter.isIncognito());
                fetchTabSuggestion();
            }
        };
        mTabModelSelector.addObserver(mTabModelSelectorObserver);

        mTabModelObserver = new EmptyTabModelObserver() {
            @Override
            public void didAddTab(Tab tab, int type) {
                mShouldIgnoreNextSelect = false;
                // TODO: Make this a TabModelSelectorTabObserver.
                tab.addObserver(new EmptyTabObserver() {
                    @Override
                    public void onLoadStarted(Tab tab, boolean toDifferentDocument) {
                        fetchTabSuggestion();
                    }
                });
                fetchTabSuggestion();
            }

            @Override
            public void didSelectTab(Tab tab, int type, int lastId) {
                if (type == TabSelectionType.FROM_CLOSE || mShouldIgnoreNextSelect) {
                    mShouldIgnoreNextSelect = false;
                    return;
                }
                if (mContainerViewModel.get(IS_VISIBLE)) {
                    TabModelFilter modelFilter = mTabModelSelector.getTabModelFilterProvider()
                                                         .getCurrentTabModelFilter();
                    if (modelFilter instanceof TabGroupModelFilter) {
                        ((TabGroupModelFilter) modelFilter).recordSessionsCount(tab);
                    }
                    setVisibility(false);
                }
            }

            @Override
            public void didMoveTab(Tab tab, int newIndex, int curIndex) {
                TabList tabList =
                        mTabModelSelector.getTabModelFilterProvider().getCurrentTabModelFilter();
                mResetHandler.resetWithTabList(tabList);
                fetchTabSuggestion();
            }

            @Override
            public void didCloseTab(int tabId, boolean incognito) {
                fetchTabSuggestion();
            }
        };

        mFullscreenManager.addListener(mFullscreenListener);
        mTabModelSelector.getTabModelFilterProvider().addTabModelFilterObserver(mTabModelObserver);

        mContainerViewModel.set(VISIBILITY_LISTENER, this);
        mContainerViewModel.set(IS_INCOGNITO,
                mTabModelSelector.getTabModelFilterProvider()
                        .getCurrentTabModelFilter()
                        .isIncognito());
        mContainerViewModel.set(ANIMATE_VISIBILITY_CHANGES, true);
        mContainerViewModel.set(TOP_CONTROLS_HEIGHT, fullscreenManager.getTopControlsHeight());
        mContainerViewModel.set(
                BOTTOM_CONTROLS_HEIGHT, fullscreenManager.getBottomControlsHeight());
        int topPadding = ReturnToChromeExperimentsUtil.shouldShowOmniboxOnTabSwitcher()
                ? ContextUtils.getApplicationContext().getResources().getDimensionPixelSize(
                        R.dimen.toolbar_height_no_shadow)
                : DEFAULT_TOP_PADDING;
        mContainerViewModel.set(TOP_PADDING, topPadding);

        mCompositorViewHolder = compositorViewHolder;
        mTabGridDialogResetHandler = tabGridDialogResetHandler;

        mTabSuggestionsProvider = new TabSuggestionsOrchestrator();
        mCurrentTabContext = TabContext.createCurrentContext(tabModelSelector);
        mTabSuggestionBarController = tabSuggestionBarController;
    }

    private void fetchTabSuggestion() {
        mCurrentTabContext = TabContext.createCurrentContext(mTabModelSelector);
        mTabSuggestionsProvider.getSuggestions(
                mCurrentTabContext, res -> suggestionsCallback((List<TabSuggestion>) res));
    }

    private void suggestionsCallback(List<TabSuggestion> suggestions) {
        mCurrentTabSuggestion = suggestions.size() > 0 ? suggestions.get(0) : null;
    }

    private void setVisibility(boolean isVisible) {
        if (isVisible) {
            RecordUserAction.record("MobileToolbarShowStackView");
        }

        mContainerViewModel.set(IS_VISIBLE, isVisible);
    }

    private void setContentOverlayVisibility(boolean isVisible) {
        Tab currentTab = mTabModelSelector.getCurrentTab();
        if (currentTab == null) return;
        mCompositorViewHolder.setContentOverlayVisibility(isVisible, true);
    }

    private void checkForSameProduct() {
        TabList tabList = mTabModelSelector.getTabModelFilterProvider().getCurrentTabModelFilter();
        Map<String, List<Integer>> productMap = new ArrayMap<>();
        List<String> productsToCheck = new ArrayList<>();
        for (int i = 0; i < tabList.getCount(); i++) {
            Tab tab = tabList.getTabAt(i);
            if (tab.hasProductUrl()) {

                String productName = tab.getProductUrl();
                String brandName = productName.substring(0,findWordEndOffset(0, productName));

                if (productMap.get(brandName) == null) {
                    List<Integer> list = new ArrayList<>();
                    list.add(i);
                    productMap.put(brandName, list);
                } else {
                    productMap.get(brandName).add(i);
                    if (!productsToCheck.contains(brandName)) {
                        productsToCheck.add(brandName);
                    }
                }
            }
        }
        if (!productsToCheck.isEmpty()) {
            String brands = "";
            for (String brand : productsToCheck) {
                if (TextUtils.isEmpty(brands)) {
                    brands = brand;
                } else {
                    brands = brands + " and "+brand;
                }
            }
            Toast.makeText(ContextUtils.getApplicationContext(), "Found similar products from "+brands, Toast.LENGTH_LONG).show();
        }
    }


    /**
     * @return The start of the word that contains the given initial offset, within the surrounding
     *         text, or {@code INVALID_OFFSET} if not found.
     */
    private int findWordStartOffset(int initial, String text) {
        // Scan before, aborting if we hit any ideographic letter.
        for (int offset = initial - 1; offset >= 0; offset--) {
            if (isWordBreakAtIndex(offset, text)) {
                // The start of the word is after this word break.
                return offset + 1;
            }
        }

        return -1;
    }

    /**
     * Finds the offset of the end of the word that includes the given initial offset.
     * NOTE: this is the index of the character just past the last character of the word,
     * so a 3 character word "who" has start index 0 and end index 3.
     * The character at the initial offset is examined and each one after that too until a non-word
     * character is encountered, and that offset will be returned.
     * @param initial The initial offset to scan from.
     * @return The end of the word that contains the given initial offset, within the surrounding
     *         text.
     */
    private int findWordEndOffset(int initial, String text) {
        // Scan after, aborting if we hit any CJKV letter.
        for (int offset = initial; offset < text.length(); offset++) {
            if (isWordBreakAtIndex(offset, text)) {
                // The end of the word is the offset of this word break.
                return offset;
            }
        }
        return -1;
    }

    /**
     * @return Whether the character at the given index is a word-break.
     */
    private boolean isWordBreakAtIndex(int index, String text) {
        return !Character.isLetterOrDigit(text.charAt(index))
                && text.charAt(index) != SOFT_HYPHEN_CHAR;
    }

    @Override
    public boolean overviewVisible() {
        return mContainerViewModel.get(IS_VISIBLE);
    }

    @Override
    public void addOverviewModeObserver(OverviewModeObserver observer) {
        mObservers.addObserver(observer);
    }

    @Override
    public void removeOverviewModeObserver(OverviewModeObserver observer) {
        mObservers.removeObserver(observer);
    }

    @Override
    public void hideOverview(boolean animate) {
        if (!animate) mContainerViewModel.set(ANIMATE_VISIBILITY_CHANGES, false);
        setVisibility(false);
        mContainerViewModel.set(ANIMATE_VISIBILITY_CHANGES, true);
    }

    public void prepareOverview() {
        mResetHandler.resetWithTabList(
                mTabModelSelector.getTabModelFilterProvider().getCurrentTabModelFilter());
        int initialPosition = Math.max(
                mTabModelSelector.getTabModelFilterProvider().getCurrentTabModelFilter().index()
                        - INITIAL_SCROLL_INDEX_OFFSET,
                0);
        mContainerViewModel.set(INITIAL_SCROLL_INDEX, initialPosition);
    }

    @Override
    public void showOverview(boolean animate) {
        if (!animate) mContainerViewModel.set(ANIMATE_VISIBILITY_CHANGES, false);
        setVisibility(true);
        mContainerViewModel.set(ANIMATE_VISIBILITY_CHANGES, true);
        checkForSameProduct();
    }

    @Override
    public void startedShowing(boolean isAnimating) {
        for (OverviewModeObserver observer : mObservers) {
            observer.onOverviewModeStartedShowing(true);
        }
    }

    @Override
    public void finishedShowing() {
        TabContext tabContext = TabContext.createCurrentContext(mTabModelSelector);
        if (mTabSuggestionBarController != null && mCurrentTabContext.isEqual(tabContext)
                && mCurrentTabSuggestion != null) {
            mTabSuggestionBarController.showTabSuggestionBar(mCurrentTabSuggestion);
        } else {
            fetchTabSuggestion();
        }

        for (OverviewModeObserver observer : mObservers) {
            observer.onOverviewModeFinishedShowing();
        }
        setContentOverlayVisibility(false);
    }

    @Override
    public void startedHiding(boolean isAnimating) {
        setContentOverlayVisibility(true);
        mTabSuggestionBarController.dismissTabSuggestionBar();
        for (OverviewModeObserver observer : mObservers) {
            observer.onOverviewModeStartedHiding(true, false);
        }
    }

    @Override
    public void finishedHiding() {
        // TODO(crbug.com/964406): see if we can lazily release it.
        mResetHandler.resetWithTabList(null);
        mContainerViewModel.set(INITIAL_SCROLL_INDEX, 0);
        for (OverviewModeObserver observer : mObservers) {
            observer.onOverviewModeFinishedHiding();
        }
    }

    /**
     * Destroy any members that needs clean up.
     */
    public void destroy() {
        mTabModelSelector.removeObserver(mTabModelSelectorObserver);
        mFullscreenManager.removeListener(mFullscreenListener);
        mTabModelSelector.getTabModelFilterProvider().removeTabModelFilterObserver(
                mTabModelObserver);
    }

    @Nullable
    TabListMediator.TabActionListener getGridCardOnClickListener(Tab tab) {
        if (!ableToOpenDialog(tab)) return null;
        return tabId -> {
            mTabGridDialogResetHandler.resetWithListOfTabs(getRelatedTabs(tabId));
        };
    }

    @Nullable
    TabListMediator.TabActionListener getCreateGroupButtonOnClickListener(Tab tab) {
        if (!ableToCreateGroup(tab)) return null;

        return tabId -> {
            Tab parentTab = TabModelUtils.getTabById(mTabModelSelector.getCurrentModel(), tabId);
            mTabModelSelector.getCurrentModel().commitAllTabClosures();
            mTabModelSelector.openNewTab(new LoadUrlParams(UrlConstants.NTP_URL),
                    TabLaunchType.FROM_CHROME_UI, parentTab,
                    mTabModelSelector.isIncognitoSelected());
            RecordUserAction.record("TabGroup.Created.TabSwitcher");
        };
    }

    private boolean ableToCreateGroup(Tab tab) {
        return FeatureUtilities.isTabGroupsAndroidEnabled()
                && mTabModelSelector.isIncognitoSelected() == tab.isIncognito()
                && getRelatedTabs(tab.getId()).size() == 1;
    }

    private boolean ableToOpenDialog(Tab tab) {
        return FeatureUtilities.isTabGroupsAndroidEnabled()
                && mTabModelSelector.isIncognitoSelected() == tab.isIncognito()
                && getRelatedTabs(tab.getId()).size() != 1;
    }

    private List<Tab> getRelatedTabs(int tabId) {
        return mTabModelSelector.getTabModelFilterProvider()
                .getCurrentTabModelFilter()
                .getRelatedTabList(tabId);
    }
}
