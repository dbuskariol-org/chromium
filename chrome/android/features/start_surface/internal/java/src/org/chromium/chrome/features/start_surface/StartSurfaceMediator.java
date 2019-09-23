// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.features.start_surface;

import static org.chromium.chrome.browser.tasks.TasksSurfaceProperties.IS_FAKE_SEARCH_BOX_VISIBLE;
import static org.chromium.chrome.browser.tasks.TasksSurfaceProperties.IS_INCOGNITO;
import static org.chromium.chrome.browser.tasks.TasksSurfaceProperties.IS_MV_TILES_VISIBLE;
import static org.chromium.chrome.browser.tasks.TasksSurfaceProperties.IS_TAB_CAROUSEL_VISIBLE;
import static org.chromium.chrome.browser.tasks.TasksSurfaceProperties.MORE_TABS_CLICK_LISTENER;
import static org.chromium.chrome.features.start_surface.StartSurfaceProperties.BOTTOM_BAR_CLICKLISTENER;
import static org.chromium.chrome.features.start_surface.StartSurfaceProperties.BOTTOM_BAR_HEIGHT;
import static org.chromium.chrome.features.start_surface.StartSurfaceProperties.BOTTOM_BAR_SELECTED_TAB_POSITION;
import static org.chromium.chrome.features.start_surface.StartSurfaceProperties.FEED_SURFACE_COORDINATOR;
import static org.chromium.chrome.features.start_surface.StartSurfaceProperties.IS_BOTTOM_BAR_VISIBLE;
import static org.chromium.chrome.features.start_surface.StartSurfaceProperties.IS_EXPLORE_SURFACE_VISIBLE;
import static org.chromium.chrome.features.start_surface.StartSurfaceProperties.IS_SECONDARY_SURFACE_VISIBLE;
import static org.chromium.chrome.features.start_surface.StartSurfaceProperties.IS_SHOWING_OVERVIEW;

import android.view.View;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;

import org.chromium.base.ContextUtils;
import org.chromium.base.ObserverList;
import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.browser.feed.FeedSurfaceCoordinator;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.EmptyTabModelObserver;
import org.chromium.chrome.browser.tabmodel.EmptyTabModelSelectorObserver;
import org.chromium.chrome.browser.tabmodel.TabModel;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.tasks.tab_management.TabSwitcher;
import org.chromium.chrome.start_surface.R;
import org.chromium.ui.modelutil.PropertyModel;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** The mediator implements the logic to interact with the surfaces and caller. */
class StartSurfaceMediator
        implements StartSurface.Controller, TabSwitcher.OverviewModeObserver, View.OnClickListener {
    @IntDef({SurfaceMode.NO_START_SURFACE, SurfaceMode.TASKS_ONLY, SurfaceMode.TWO_PANES,
            SurfaceMode.SINGLE_PANE})
    @Retention(RetentionPolicy.SOURCE)
    @interface SurfaceMode {
        int NO_START_SURFACE = 0;
        int TASKS_ONLY = 1;
        int TWO_PANES = 2;
        int SINGLE_PANE = 3;
    }

    /** The delegate to interact with the toolbar. */
    interface Delegate {
        /**
         * Set the visibility of the Omnibox.
         * @param isVisible Whether is visible.
         */
        void setOmniboxVisibility(boolean isVisible);
    }

    /** Interface to control overlay visibility. */
    interface OverlayVisibilityHandler {
        /**
         * Set the content overlay visibility.
         * @param isVisible Whether the content overlay should be visible.
         */
        void setContentOverlayVisibility(boolean isVisible);
    }

    /** Interface to initialize a secondary tasks surface for more tabs. */
    interface SecondaryTasksSurfaceInitializer {
        /**
         * Initialize the secondary tasks surface and return the surface controller, which is
         * TabSwitcher.Controller.
         * @return The {@link TabSwitcher.Controller} of the secondary tasks surface.
         */
        TabSwitcher.Controller initialize();
    }

    private final ObserverList<StartSurface.OverviewModeObserver> mObservers = new ObserverList<>();
    private final TabSwitcher.Controller mController;
    private final TabModelSelector mTabModelSelector;
    private final OverlayVisibilityHandler mOverlayVisibilityHandler;
    @Nullable
    private final PropertyModel mPropertyModel;
    @Nullable
    private final ExploreSurfaceCoordinator.FeedSurfaceCreator mFeedSurfaceCreator;
    @Nullable
    private final SecondaryTasksSurfaceInitializer mSecondaryTasksSurfaceInitializer;
    @SurfaceMode
    private final int mSurfaceMode;
    @Nullable
    private TabSwitcher.Controller mSecondaryTasksSurfaceController;
    @Nullable
    private PropertyModel mSecondaryTasksSurfacePropertyModel;
    private boolean mIsIncognito;
    @Nullable
    private Delegate mDelegate;

    StartSurfaceMediator(TabSwitcher.Controller controller, TabModelSelector tabModelSelector,
            OverlayVisibilityHandler overlayVisibilityHandler,
            @Nullable PropertyModel propertyModel,
            @Nullable ExploreSurfaceCoordinator.FeedSurfaceCreator feedSurfaceCreator,
            @Nullable SecondaryTasksSurfaceInitializer secondaryTasksSurfaceInitializer,
            @SurfaceMode int surfaceMode) {
        mController = controller;
        mTabModelSelector = tabModelSelector;
        mOverlayVisibilityHandler = overlayVisibilityHandler;
        mPropertyModel = propertyModel;
        mFeedSurfaceCreator = feedSurfaceCreator;
        mSecondaryTasksSurfaceInitializer = secondaryTasksSurfaceInitializer;
        mSurfaceMode = surfaceMode;

        if (mPropertyModel != null) {
            assert mSurfaceMode == SurfaceMode.SINGLE_PANE || mSurfaceMode == SurfaceMode.TWO_PANES
                    || mSurfaceMode == SurfaceMode.TASKS_ONLY;

            mPropertyModel.set(
                    BOTTOM_BAR_CLICKLISTENER, new StartSurfaceProperties.BottomBarClickListener() {
                        // TODO(crbug.com/982018): Animate switching between explore and home
                        // surface.
                        @Override
                        public void onHomeButtonClicked() {
                            setExploreSurfaceVisibility(false);
                            RecordUserAction.record("StartSurface.TwoPanes.BottomBar.TapHome");
                        }

                        @Override
                        public void onExploreButtonClicked() {
                            // TODO(crbug.com/982018): Hide the Tab switcher toolbar when showing
                            // explore surface.
                            setExploreSurfaceVisibility(true);
                            RecordUserAction.record(
                                    "StartSurface.TwoPanes.BottomBar.TapExploreSurface");
                        }
                    });

            mIsIncognito = mTabModelSelector.isIncognitoSelected();
            if (mSurfaceMode == SurfaceMode.SINGLE_PANE) {
                mPropertyModel.set(MORE_TABS_CLICK_LISTENER, this);

                TabModel normalTabModel = mTabModelSelector.getModel(false);
                normalTabModel.addObserver(new EmptyTabModelObserver() {
                    @Override
                    public void willCloseTab(Tab tab, boolean animate) {
                        if (!mPropertyModel.get(IS_SHOWING_OVERVIEW)) return;

                        // Hide the more Tabs view when the last Tab will be closed.
                        if (normalTabModel.getCount() <= 1
                                // Secondary tasks surface is used as the main surface in incognito
                                // mode.
                                && !mIsIncognito) {
                            assert mSurfaceMode == SurfaceMode.SINGLE_PANE;

                            if (mSecondaryTasksSurfaceController != null
                                    && mSecondaryTasksSurfaceController.overviewVisible()) {
                                setSecondaryTasksSurfaceVisibility(false);
                                setExploreSurfaceVisibility(true);
                            }
                            mPropertyModel.set(IS_TAB_CAROUSEL_VISIBLE, false);
                        }
                    }
                });
            }
            mTabModelSelector.addObserver(new EmptyTabModelSelectorObserver() {
                @Override
                public void onTabModelSelected(TabModel newModel, TabModel oldModel) {
                    updateIncognitoMode(newModel.isIncognito());
                }
            });
            mPropertyModel.set(IS_INCOGNITO, mIsIncognito);

            // Set the initial state.

            // Show explore surface if not in incognito and either in SINGLE PANES mode
            // or in TWO PANES mode with last visible pane explore.
            boolean shouldShowExploreSurface =
                    (mSurfaceMode == SurfaceMode.SINGLE_PANE
                            || ReturnToStartSurfaceUtil.shouldShowExploreSurface())
                    && !mIsIncognito;
            setExploreSurfaceVisibility(shouldShowExploreSurface);
            if (mSurfaceMode == SurfaceMode.TWO_PANES) {
                mPropertyModel.set(BOTTOM_BAR_HEIGHT,
                        ContextUtils.getApplicationContext().getResources().getDimensionPixelSize(
                                R.dimen.ss_bottom_bar_height));
                mPropertyModel.set(IS_BOTTOM_BAR_VISIBLE, !mIsIncognito);
            }
            mPropertyModel.set(IS_MV_TILES_VISIBLE, !mIsIncognito);
        }
        mController.addOverviewModeObserver(this);
    }

    void setSecondaryTasksSurfacePropertyModel(PropertyModel propertyModel) {
        mSecondaryTasksSurfacePropertyModel = propertyModel;
        mSecondaryTasksSurfacePropertyModel.set(IS_INCOGNITO, mIsIncognito);
    }

    void setDelegate(Delegate delegate) {
        mDelegate = delegate;
    }

    void onUrlFocusChange(boolean hasFocus) {
        assert mDelegate != null;

        // Always show the Omnibox on the two panes explore surface.
        boolean showOmnibox = hasFocus
                || (mSurfaceMode == SurfaceMode.TWO_PANES
                        && mPropertyModel.get(IS_EXPLORE_SURFACE_VISIBLE));
        setOmniboxVisibility(showOmnibox);
    }

    // Implements StartSurface.Controller
    @Override
    public boolean overviewVisible() {
        return mController.overviewVisible();
    }

    @Override
    public void addOverviewModeObserver(StartSurface.OverviewModeObserver observer) {
        mObservers.addObserver(observer);
    }

    @Override
    public void removeOverviewModeObserver(StartSurface.OverviewModeObserver observer) {
        mObservers.removeObserver(observer);
    }

    @Override
    public void hideOverview(boolean animate) {
        if (mSecondaryTasksSurfaceController != null
                && mSecondaryTasksSurfaceController.overviewVisible()) {
            assert mSurfaceMode == SurfaceMode.SINGLE_PANE;

            setSecondaryTasksSurfaceVisibility(false);
        }
        mController.hideOverview(animate);
    }

    @Override
    public void showOverview(boolean animate) {
        // TODO(crbug.com/982018): Animate the bottom bar together with the Tab Grid view.
        if (mPropertyModel != null) {
            if (mSurfaceMode == SurfaceMode.SINGLE_PANE) {
                RecordUserAction.record("StartSurface.SinglePane");
                if (mIsIncognito) {
                    setSecondaryTasksSurfaceVisibility(true);
                } else {
                    setExploreSurfaceVisibility(true);

                    mPropertyModel.set(IS_TAB_CAROUSEL_VISIBLE,
                            mTabModelSelector.getModel(false).getCount() > 0);
                }
            } else if (mSurfaceMode == SurfaceMode.TWO_PANES) {
                RecordUserAction.record("StartSurface.TwoPanes");
                String defaultOnUserActionString = mPropertyModel.get(IS_EXPLORE_SURFACE_VISIBLE)
                        ? "ExploreSurface"
                        : "HomeSurface";
                RecordUserAction.record(
                        "StartSurface.TwoPanes.DefaultOn" + defaultOnUserActionString);
            }

            if (mDelegate != null) {
                // Always show the Omnibox on the two panes explore surface.
                boolean shouldShowOmnibox = mSurfaceMode == SurfaceMode.TWO_PANES
                        && mPropertyModel.get(IS_EXPLORE_SURFACE_VISIBLE);
                setOmniboxVisibility(shouldShowOmnibox);
            }

            // Make sure FeedSurfaceCoordinator is built before the explore surface is showing by
            // default.
            if (mPropertyModel.get(IS_EXPLORE_SURFACE_VISIBLE)
                    && mPropertyModel.get(FEED_SURFACE_COORDINATOR) == null) {
                mPropertyModel.set(FEED_SURFACE_COORDINATOR,
                        mFeedSurfaceCreator.createFeedSurfaceCoordinator());
            }
            mPropertyModel.set(IS_MV_TILES_VISIBLE, !mIsIncognito);
            mPropertyModel.set(IS_SHOWING_OVERVIEW, true);
        }

        mController.showOverview(animate);
    }

    @Override
    public boolean onBackPressed() {
        if (mSecondaryTasksSurfaceController != null
                && mSecondaryTasksSurfaceController.overviewVisible()
                // Secondary tasks surface is used as the main surface in incognito mode.
                && !mIsIncognito) {
            assert mSurfaceMode == SurfaceMode.SINGLE_PANE;

            setSecondaryTasksSurfaceVisibility(false);
            setExploreSurfaceVisibility(true);
            return true;
        }

        if (mPropertyModel != null && mPropertyModel.get(IS_EXPLORE_SURFACE_VISIBLE)
                && mSurfaceMode == SurfaceMode.TWO_PANES) {
            setExploreSurfaceVisibility(false);
            return true;
        }

        return mController.onBackPressed();
    }

    // Implements TabSwitcher.OverviewModeObserver.
    @Override
    public void startedShowing() {
        for (StartSurface.OverviewModeObserver observer : mObservers) {
            observer.startedShowing();
        }
    }

    @Override
    public void finishedShowing() {
        for (StartSurface.OverviewModeObserver observer : mObservers) {
            observer.finishedShowing();
        }
        mOverlayVisibilityHandler.setContentOverlayVisibility(false);
    }

    @Override
    public void startedHiding() {
        mOverlayVisibilityHandler.setContentOverlayVisibility(true);
        if (mPropertyModel != null) {
            mPropertyModel.set(IS_SHOWING_OVERVIEW, false);
            destroyFeedSurfaceCoordinator();
        }
        for (StartSurface.OverviewModeObserver observer : mObservers) {
            observer.startedHiding();
        }
    }

    @Override
    public void finishedHiding() {
        for (StartSurface.OverviewModeObserver observer : mObservers) {
            observer.finishedHiding();
        }
    }

    // Implements View.OnClickListener, which listens for the more tabs button.
    @Override
    public void onClick(View v) {
        assert mSurfaceMode == SurfaceMode.SINGLE_PANE;

        if (mSecondaryTasksSurfaceController == null) {
            mSecondaryTasksSurfaceController = mSecondaryTasksSurfaceInitializer.initialize();
            assert mSecondaryTasksSurfacePropertyModel != null;
        }

        setExploreSurfaceVisibility(false);
        setSecondaryTasksSurfaceVisibility(true);
        RecordUserAction.record("StartSurface.SinglePane.MoreTabs");
    }

    /** This interface builds the feed surface coordinator when showing if needed. */
    private void setExploreSurfaceVisibility(boolean isVisible) {
        if (isVisible == mPropertyModel.get(IS_EXPLORE_SURFACE_VISIBLE)) return;

        if (isVisible && mPropertyModel.get(IS_SHOWING_OVERVIEW)
                && mPropertyModel.get(FEED_SURFACE_COORDINATOR) == null) {
            mPropertyModel.set(
                    FEED_SURFACE_COORDINATOR, mFeedSurfaceCreator.createFeedSurfaceCoordinator());
        }

        mPropertyModel.set(IS_EXPLORE_SURFACE_VISIBLE, isVisible);

        if (mSurfaceMode == SurfaceMode.TWO_PANES) {
            if (mDelegate != null) setOmniboxVisibility(isVisible);

            // Update the 'BOTTOM_BAR_SELECTED_TAB_POSITION' property to reflect the change. This is
            // needed when clicking back button on the explore surface.
            mPropertyModel.set(BOTTOM_BAR_SELECTED_TAB_POSITION, isVisible ? 1 : 0);
            ReturnToStartSurfaceUtil.setExploreSurfaceVisibleLast(isVisible);
        }
    }

    private void updateIncognitoMode(boolean isIncognito) {
        if (isIncognito == mIsIncognito) return;
        mIsIncognito = isIncognito;

        if (mSurfaceMode == SurfaceMode.SINGLE_PANE) {
            setExploreSurfaceVisibility(!mIsIncognito);
            setSecondaryTasksSurfaceVisibility(
                    mIsIncognito && mPropertyModel.get(IS_SHOWING_OVERVIEW));
        } else if (mSurfaceMode == SurfaceMode.TWO_PANES) {
            mPropertyModel.set(BOTTOM_BAR_HEIGHT,
                    mIsIncognito ? 0
                                 : ContextUtils.getApplicationContext()
                                           .getResources()
                                           .getDimensionPixelSize(R.dimen.ss_bottom_bar_height));
            mPropertyModel.set(IS_BOTTOM_BAR_VISIBLE, !mIsIncognito);

            // Hide explore surface if going incognito. When returning to normal mode, we
            // always show the Home Pane, so the Explore Pane stays hidden.
            if (mIsIncognito) setExploreSurfaceVisibility(false);
        }

        mPropertyModel.set(IS_MV_TILES_VISIBLE, !mIsIncognito);
        mPropertyModel.set(IS_INCOGNITO, mIsIncognito);
        if (mSecondaryTasksSurfacePropertyModel != null) {
            mSecondaryTasksSurfacePropertyModel.set(IS_INCOGNITO, mIsIncognito);
        }
    }

    private void destroyFeedSurfaceCoordinator() {
        FeedSurfaceCoordinator feedSurfaceCoordinator =
                mPropertyModel.get(FEED_SURFACE_COORDINATOR);
        if (feedSurfaceCoordinator != null) feedSurfaceCoordinator.destroy();
        mPropertyModel.set(FEED_SURFACE_COORDINATOR, null);
    }

    private void setSecondaryTasksSurfaceVisibility(boolean isVisible) {
        assert mSurfaceMode == SurfaceMode.SINGLE_PANE;

        if (isVisible) {
            if (mSecondaryTasksSurfaceController == null) {
                mSecondaryTasksSurfaceController = mSecondaryTasksSurfaceInitializer.initialize();
            }
            mSecondaryTasksSurfaceController.showOverview(false);
            mPropertyModel.set(IS_SECONDARY_SURFACE_VISIBLE, true);
            mSecondaryTasksSurfacePropertyModel.set(IS_FAKE_SEARCH_BOX_VISIBLE, mIsIncognito);
        } else {
            if (mSecondaryTasksSurfaceController == null) return;
            mSecondaryTasksSurfaceController.hideOverview(false);
            mPropertyModel.set(IS_SECONDARY_SURFACE_VISIBLE, false);
        }
    }

    private void setOmniboxVisibility(boolean isVisible) {
        mDelegate.setOmniboxVisibility(isVisible);
        mPropertyModel.set(IS_FAKE_SEARCH_BOX_VISIBLE, !isVisible);
        if (mSecondaryTasksSurfacePropertyModel != null) {
            mSecondaryTasksSurfacePropertyModel.set(IS_FAKE_SEARCH_BOX_VISIBLE, !isVisible);
        }
    }
}
