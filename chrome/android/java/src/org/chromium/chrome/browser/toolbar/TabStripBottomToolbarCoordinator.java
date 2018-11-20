// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.toolbar;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.support.annotation.ColorInt;
import android.support.v7.widget.AppCompatImageButton;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewStub;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.ContextUtils;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.UrlConstants;
import org.chromium.chrome.browser.collection.CollectionManager;
import org.chromium.chrome.browser.compositor.layouts.OverviewModeBehavior;
import org.chromium.chrome.browser.compositor.layouts.phone.TabGroupList;
import org.chromium.chrome.browser.favicon.FaviconHelper;
import org.chromium.chrome.browser.fullscreen.ChromeFullscreenManager;
import org.chromium.chrome.browser.modelutil.PropertyModelChangeProcessor;
import org.chromium.chrome.browser.tab.EmptyTabObserver;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tab.TabObserver;
import org.chromium.chrome.browser.tabmodel.TabModel;
import org.chromium.chrome.browser.tabmodel.TabModel.TabSelectionType;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.tabmodel.TabModelSelectorTabObserver;
import org.chromium.chrome.browser.tabmodel.TabModelUtils;
import org.chromium.chrome.browser.toolbar.TabStripBottomToolbarViewBinder.ViewHolder;
import org.chromium.chrome.browser.toolbar.TabStripToolbarButtonSlotData.TabStripToolbarButtonData;
import org.chromium.chrome.browser.util.ViewUtils;
import org.chromium.chrome.browser.widget.RoundedIconGenerator;
import org.chromium.content_public.browser.LoadUrlParams;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The coordinator for the bottom toolbar. This class handles all interactions that the bottom
 * toolbar has with the outside world. This class has two primary components, an Android view that
 * handles user actions and a composited texture that draws when the controls are being scrolled
 * off-screen. The Android version does not draw unless the controls offset is 0.
 */
public class TabStripBottomToolbarCoordinator {
    /** The mediator that handles events from outside the bottom toolbar. */
    private final TabStripBottomToolbarMediator mMediator;

    /** The primary color to be used in normal mode. */
    private final int mNormalPrimaryColor;

    /** The primary color to be used in incognito mode. */
    private final int mIncognitoPrimaryColor;

    private Map<Integer, TabStripToolbarButtonData> mScrollViewButtons;

    private int mSelectedTab;

    private FaviconHelper mFaviconHelper;

    private final int mFaviconSize;

    private final RoundedIconGenerator mIconGenerator;

    private OnClickListener mCloseTabClickListener;

    private ArrayList<TabGroupList> mTabGroupLists = new ArrayList<>();

    private boolean mIsTabGroupFeatureEnable = true;

    private View mTabStripBottomToolbarView;

    private Drawable mNTPRoundedBitmapDrawable;

    private AppCompatImageButton mSecondButton;

    /**
     * Build the coordinator that manages the bottom toolbar.
     * @param fullscreenManager A {@link ChromeFullscreenManager} to update the bottom controls
     *                          height for the renderer.
     * @param root The root {@link ViewGroup} for locating the views to inflate.
     */
    public TabStripBottomToolbarCoordinator(
            ChromeFullscreenManager fullscreenManager, ViewGroup root) {
        TabStripBottomToolbarModel model = new TabStripBottomToolbarModel();

        int shadowHeight =
                root.getResources().getDimensionPixelOffset(R.dimen.toolbar_shadow_height);

        // This is the Android view component of the views that constitute the bottom toolbar.
        mTabStripBottomToolbarView =
                ((ViewStub) root.findViewById(R.id.tab_strip_bottom_toolbar)).inflate();
        final ScrollingBottomViewResourceFrameLayout toolbarRoot =
                (ScrollingBottomViewResourceFrameLayout) mTabStripBottomToolbarView;
        toolbarRoot.setTopShadowHeight(shadowHeight);

        PropertyModelChangeProcessor.create(
                model, new ViewHolder(toolbarRoot), new TabStripBottomToolbarViewBinder());

        mNormalPrimaryColor =
                ApiCompatibilityUtils.getColor(root.getResources(), R.color.modern_primary_color);
        mIncognitoPrimaryColor = ApiCompatibilityUtils.getColor(
                root.getResources(), R.color.incognito_modern_primary_color);

        mMediator = new TabStripBottomToolbarMediator(
                model, fullscreenManager, root.getResources(), mNormalPrimaryColor);

        mScrollViewButtons = new LinkedHashMap<>();
        mSelectedTab = -1;
        mFaviconSize = root.getResources().getDimensionPixelSize(R.dimen.default_favicon_size);
        mIconGenerator = ViewUtils.createDefaultRoundedIconGenerator(true);

        Resources resources = ContextUtils.getApplicationContext().getResources();

        mNTPRoundedBitmapDrawable = ViewUtils.createRoundedBitmapDrawable(
                Bitmap.createScaledBitmap(
                        BitmapFactory.decodeResource(resources, R.drawable.chromelogo16),
                        mFaviconSize, mFaviconSize, true),
                ViewUtils.DEFAULT_FAVICON_CORNER_RADIUS);
    }

    /**
     * Initialize the bottom toolbar with the components that had native initialization
     * dependencies.
     * <p>
     * Calling this must occur after the native library have completely loaded.
     */
    public void initializeWithNative(ChromeActivity activity, TabModelSelector tabModelSelector,
            OverviewModeBehavior overviewModeBehavior) {
        mMediator.setLayoutManager(activity.getCompositorViewHolder().getLayoutManager());
        mMediator.setResourceManager(activity.getCompositorViewHolder().getResourceManager());
        mMediator.setOverviewModeBehavior(overviewModeBehavior);
        mMediator.setWindowAndroid(activity.getWindowAndroid());

        mSecondButton = mTabStripBottomToolbarView.findViewById(R.id.second_button);

        final Runnable newTab = () -> {
            tabModelSelector.getModel(false).commitAllTabClosures();
            TabModel currentModel = tabModelSelector.getCurrentModel();
            List<Integer> tabIdList = getTabGroup(currentModel);

            int firstTabId = tabIdList.get(0);
            Tab lastTabInGroup = TabModelUtils.getTabById(currentModel, firstTabId);

            if (lastTabInGroup.getWebContents().getNavigationController() == null) {
                lastTabInGroup = null;
            }

            tabModelSelector.openNewTab(new LoadUrlParams(UrlConstants.NTP_URL),
                    TabModel.TabLaunchType.FROM_CHROME_UI, lastTabInGroup,
                    currentModel.isIncognito());

            RecordUserAction.record("TabStrip.NewTab");
        };

        mSecondButton.setOnClickListener((v) -> {
            CollectionManager collectionManager = CollectionManager.getInstance();

            if (collectionManager != null && collectionManager.hasOverlayOnCurrentTab()) {
                collectionManager.setOverlayVisibility(false);
                updateSecondButton();
            } else {
                newTab.run();
            }
        });

        mCloseTabClickListener = v -> {
            // TODO: Closing the parent tab causes a crash.
            TabModel model = tabModelSelector.getCurrentModel();
            Tab currentTab = model.getTabAt(model.index());

            List<Integer> tabIdList = getTabGroup(model);

            // If this tab is the first tab in the group, set the next tab to be the next one in the
            // group.
            if (tabIdList.get(0) == currentTab.getId()) {
                int newParentId = tabIdList.get(1);
                model.closeTab(currentTab, TabModelUtils.getTabById(model, newParentId), true);
                return;
            } else {
                int tabIdListIndex = tabIdList.indexOf(currentTab.getId());
                int nextTabId = tabIdList.get(tabIdListIndex - 1);
                model.closeTab(currentTab, TabModelUtils.getTabById(model, nextTabId), true);
            }
            RecordUserAction.record("TabStrip.CloseTab");
        };

        mFaviconHelper = new FaviconHelper();

        mTabGroupLists = new ArrayList<>();
        mTabGroupLists.add(new TabGroupList(
                tabModelSelector.getModel(false), activity.getTabContentManager()));
        mTabGroupLists.add(
                new TabGroupList(tabModelSelector.getModel(true), activity.getTabContentManager()));

        new TabModelSelectorTabObserver(tabModelSelector) {
            @Override
            public void onPageLoadStarted(Tab tab, String url) {
                int tabGroupListIndex = tabModelSelector.isIncognitoSelected() ? 1 : 0;
                int tabCount = mTabGroupLists.get(tabGroupListIndex)
                                       .getAllTabIdsInSameGroup(tab.getId())
                                       .size();

                // This means the tab strip is not showing
                if (tabCount == 1) tabCount = 0;

                RecordHistogram.recordCountHistogram("TabStrips.TabCountOnPageLoad", tabCount);
            }
        };
    }

    private Drawable generateIcon(Tab tab, Bitmap icon) {
        Drawable iconDrawable;
        Resources resources = ContextUtils.getApplicationContext().getResources();
        @ColorInt
        int color = ApiCompatibilityUtils.getColor(resources, R.color.modern_grey_500);
        mIconGenerator.setBackgroundColor(color);

        if (UrlConstants.NTP_URL.equals(tab.getUrl())) {
            iconDrawable = mNTPRoundedBitmapDrawable;
        } else if (icon == null) {
            iconDrawable =
                    new BitmapDrawable(resources, mIconGenerator.generateIconForUrl(tab.getUrl()));
        } else {
            iconDrawable = ViewUtils.createRoundedBitmapDrawable(
                    Bitmap.createScaledBitmap(icon, mFaviconSize, mFaviconSize, true),
                    ViewUtils.DEFAULT_FAVICON_CORNER_RADIUS);
        }
        return iconDrawable;
    }

    private void updateSecondButton() {
        CollectionManager collectionManager = CollectionManager.getInstance();

        if (collectionManager != null && collectionManager.hasOverlayOnCurrentTab()) {
            mSecondButton.setImageResource(R.drawable.ic_check_googblue_24dp);
        } else {
            mSecondButton.setImageResource(R.drawable.new_tab_icon);
        }
    }

    /**
     * Updates the current bottom bar Tab buttons based on the TabModel this Toolbar contains.
     */
    public void updateBottomBarButtons(TabModel tabModel) {
        if (mIsTabGroupFeatureEnable) {
            updateTabGroupBottomBar(tabModel);
        } else {
            updateAllTabsBottomBar(tabModel);
        }
        updateSecondButton();
    }

    private List<Integer> getTabGroup(TabModel tabModel) {
        TabGroupList tabList = mTabGroupLists.get(tabModel.isIncognito() ? 1 : 0);
        Tab currentTab = TabModelUtils.getCurrentTab(tabModel);
        if (currentTab == null) {
            return null;
        }
        return tabList.getAllTabIdsInSameGroup(currentTab.getId());
    }

    private void updateTabGroupBottomBar(TabModel tabModel) {
        List<Integer> tabIdList = getTabGroup(tabModel);

        if (tabIdList == null || tabModel == null || tabIdList.size() <= 1) {
            mMediator.setScrollViewData(null);
            return;
        }

        TabObserver tabObserver = new EmptyTabObserver() {
            @Override
            public void onFaviconUpdated(Tab tab, Bitmap icon) {
                TabStripToolbarButtonData button = mScrollViewButtons.get(tab.getId());
                if (button == null) return;
                button.updateButtonDrawable(generateIcon(tab, icon));
            }
        };

        // This logic will keep tab strip buttons in the same order as given by TabGroup's
        // getTabGroup. Any new tab will go at the end of the list rather than next to the
        // parent.
        Map<Integer, TabStripToolbarButtonData> tempScrollViewButtons = new LinkedHashMap<>();
        for (int i = 0; i < tabIdList.size(); i++) {
            int tabId = tabIdList.get(i);
            int tabIndex = TabModelUtils.getTabIndexById(tabModel, tabId);
            Tab tab = TabModelUtils.getTabById(tabModel, tabId);
            if (tabModel.isClosurePending(tabId)) continue;
            if (mScrollViewButtons.containsKey(tabId)) {
                TabStripToolbarButtonData button = mScrollViewButtons.get(tabId);
                button.setIsSelected(tabModel.index() == tabIndex);
                tempScrollViewButtons.put(tabId, button);
            } else {
                tempScrollViewButtons.put(
                        tabId, createSwitchTabButton(tab, tabModel.index() == tabIndex, tabModel));
                tab.addObserver(tabObserver);
            }
        }

        mScrollViewButtons = tempScrollViewButtons;

        mMediator.setScrollViewData(new ArrayList<>(mScrollViewButtons.values()));
    }

    public void updateAllTabsBottomBar(TabModel tabModel) {
        int numberOfTabs = tabModel != null ? tabModel.getCount() : -1;
        int selectedTab = tabModel != null ? tabModel.index() : -1;

        // Don't update the buttons if the number of tabs and selected tab hasn't changed.
        if (numberOfTabs == mScrollViewButtons.size() && selectedTab == mSelectedTab) {
            return;
        }

        mSelectedTab = selectedTab;

        TabObserver tabObserver = new EmptyTabObserver() {
            @Override
            public void onFaviconUpdated(Tab tab, Bitmap icon) {
                TabStripToolbarButtonData button = mScrollViewButtons.get(tab.getId());
                if (button == null) return;
                button.updateButtonDrawable(generateIcon(tab, icon));
            }
        };

        Map<Integer, TabStripToolbarButtonData> tempScrollViewButtons = new LinkedHashMap<>();
        for (int tabIndex = 0; tabIndex < numberOfTabs; tabIndex++) {
            Tab tab = tabModel.getTabAt(tabIndex);
            int id = tab.getId();
            if (mScrollViewButtons.containsKey(id)) {
                TabStripToolbarButtonData button = mScrollViewButtons.get(id);
                button.setIsSelected(selectedTab == tabIndex);
                tempScrollViewButtons.put(id, button);
            } else {
                tempScrollViewButtons.put(
                        id, createSwitchTabButton(tab, selectedTab == tabIndex, tabModel));
                tab.addObserver(tabObserver);
            }
        }

        mScrollViewButtons = tempScrollViewButtons;

        mMediator.setScrollViewData(new ArrayList<>(mScrollViewButtons.values()));
    }

    private TabStripToolbarButtonData createSwitchTabButton(
            Tab tab, boolean isSelected, TabModel tabModel) {
        final OnClickListener switchTabButtonListener = v -> {
            tabModel.setIndex(tabModel.indexOf(tab), TabSelectionType.FROM_USER);
            RecordUserAction.record("TabStrip.SwitchTab");
        };
        final CharSequence accessibilityString =
                ContextUtils.getApplicationContext().getResources().getString(
                        R.string.accessibility_toolbar_btn_new_tab);

        TabStripToolbarButtonData buttonData = new TabStripToolbarButtonData(
                generateIcon(tab, tab.getFavicon()), accessibilityString, switchTabButtonListener,
                mCloseTabClickListener, ContextUtils.getApplicationContext(), isSelected);

        if (tab.getFavicon() == null) {
            mFaviconHelper.getLocalFaviconImageForURL(tabModel.getProfile(), tab.getUrl(),
                    mFaviconSize, new FaviconHelper.FaviconImageCallback() {
                        @Override
                        public void onFaviconAvailable(Bitmap image, String iconUrl) {
                            if (image != null) {
                                buttonData.updateButtonDrawable(generateIcon(tab, image));
                            }
                        }
                    });
        }

        return buttonData;
    }

    public void setIncognito(boolean isIncognito) {
        // When toggling incognito, reset the tab state.
        mSelectedTab = -1;
        mScrollViewButtons = new LinkedHashMap<>();
        mMediator.setPrimaryColor(isIncognito ? mIncognitoPrimaryColor : mNormalPrimaryColor);
    }

    /**
     * Clean up any state when the bottom toolbar is destroyed.
     */
    public void destroy() {
        mFaviconHelper.destroy();
        mMediator.destroy();
    }
}
