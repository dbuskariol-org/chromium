// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.features.start_surface;

import static org.chromium.chrome.features.start_surface.StartSurfaceProperties.TOP_BAR_HEIGHT;

import android.support.v4.widget.NestedScrollView;
import android.view.LayoutInflater;

import androidx.annotation.Nullable;

import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.ChromeFeatureList;
import org.chromium.chrome.browser.omnibox.LocationBarVoiceRecognitionHandler;
import org.chromium.chrome.browser.tasks.TasksSurface;
import org.chromium.chrome.browser.tasks.TasksSurfaceProperties;
import org.chromium.chrome.browser.tasks.tab_management.TabManagementModuleProvider;
import org.chromium.chrome.browser.tasks.tab_management.TabSwitcher;
import org.chromium.chrome.browser.util.FeatureUtilities;
import org.chromium.chrome.start_surface.R;
import org.chromium.ui.modelutil.PropertyKey;
import org.chromium.ui.modelutil.PropertyModel;
import org.chromium.ui.modelutil.PropertyModelChangeProcessor;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Root coordinator that is responsible for showing start surfaces, like a grid of Tabs, explore
 * surface and the bottom bar to switch between them.
 */
public class StartSurfaceCoordinator implements StartSurface {
    private final ChromeActivity mActivity;
    private final StartSurfaceMediator mStartSurfaceMediator;
    private final @StartSurfaceMediator.SurfaceMode int mSurfaceMode;

    // Non-null in StartSurfaceMediator.SurfaceMode.TASKS_ONLY,
    // StartSurfaceMediator.SurfaceMode.TWO_PANES and StartSurfaceMediator.SurfaceMode.SINGLE_PANE
    // modes.
    @Nullable
    private TasksSurface mTasksSurface;

    // Non-null in StartSurfaceMediator.SurfaceMode.TASKS_ONLY,
    // StartSurfaceMediator.SurfaceMode.TWO_PANES and StartSurfaceMediator.SurfaceMode.SINGLE_PANE
    // modes.
    @Nullable
    private PropertyModelChangeProcessor mTasksSurfacePropertyModelChangeProcessor;

    // Non-null in StartSurfaceMediator.SurfaceMode.SINGLE_PANE mode to show more tabs.
    @Nullable
    private TasksSurface mSecondaryTasksSurface;

    // Non-null in StartSurfaceMediator.SurfaceMode.SINGLE_PANE mode to show more tabs.
    @Nullable
    private PropertyModelChangeProcessor mSecondaryTasksSurfacePropertyModelChangeProcessor;

    // Non-null in StartSurfaceMediator.SurfaceMode.NO_START_SURFACE to show the tabs.
    @Nullable
    private TabSwitcher mTabSwitcher;

    // Non-null in StartSurfaceMediator.SurfaceMode.TWO_PANES mode.
    @Nullable
    private BottomBarCoordinator mBottomBarCoordinator;

    // Non-null in StartSurfaceMediator.SurfaceMode.TWO_PANES and
    // StartSurfaceMediator.SurfaceMode.SINGLE_PANE modes.
    @Nullable
    private ExploreSurfaceCoordinator mExploreSurfaceCoordinator;

    // Non-null in StartSurfaceMediator.SurfaceMode.TWO_PANES and
    // StartSurfaceMediator.SurfaceMode.SINGLE_PANE modes.
    // TODO(crbug.com/982018): Get rid of this reference since the mediator keeps a reference to it.
    @Nullable
    private PropertyModel mPropertyModel;

    // Used to remember TabSwitcher.OnTabSelectingListener in
    // StartSurfaceMediator.SurfaceMode.SINGLE_PANE mode for more tabs surface if necessary.
    @Nullable
    private TabSwitcher.OnTabSelectingListener mOnTabSelectingListener;

    // Used to remember TasksSurface.FakeSearchBoxDelegate in
    // StartSurfaceMediator.SurfaceMode.SINGLE_PANE mode for more tabs surface if necessary.
    @Nullable
    private TasksSurface.FakeSearchBoxDelegate mFakeSearchBoxDelegate;

    // Used to remember LocationBarVoiceRecognitionHandler in
    // StartSurfaceMediator.SurfaceMode.SINGLE_PANE mode for more tabs surface if necessary.
    @Nullable
    private LocationBarVoiceRecognitionHandler mVoiceRecognitionHandler;

    public StartSurfaceCoordinator(ChromeActivity activity) {
        mActivity = activity;
        mSurfaceMode = computeSurfaceMode();

        if (mSurfaceMode == StartSurfaceMediator.SurfaceMode.NO_START_SURFACE) {
            // Create Tab switcher directly to save one layer in the view hierarchy.
            mTabSwitcher = TabManagementModuleProvider.getDelegate().createGridTabSwitcher(
                    mActivity, mActivity.getCompositorViewHolder());
        } else {
            createAndSetStartSurface();
        }

        StartSurfaceMediator.OverlayVisibilityHandler overlayVisibilityHandler =
                new StartSurfaceMediator.OverlayVisibilityHandler() {
                    @Override
                    // TODO(crbug.com/982018): Consider moving this to LayoutManager.
                    public void setContentOverlayVisibility(boolean isVisible) {
                        if (mActivity.getTabModelSelector().getCurrentTab() == null) return;
                        mActivity.getCompositorViewHolder().setContentOverlayVisibility(
                                isVisible, true);
                    }
                };
        TabSwitcher.Controller controller =
                mTabSwitcher != null ? mTabSwitcher.getController() : mTasksSurface.getController();
        mStartSurfaceMediator = new StartSurfaceMediator(controller,
                mActivity.getTabModelSelector(), overlayVisibilityHandler, mPropertyModel,
                mExploreSurfaceCoordinator == null
                        ? null
                        : mExploreSurfaceCoordinator.getFeedSurfaceCreator(),
                mSurfaceMode == StartSurfaceMediator.SurfaceMode.SINGLE_PANE
                        ? this::initializeSecondaryTasksSurface
                        : null,
                mSurfaceMode);
    }

    // Implements StartSurface.
    @Override
    public void setOnTabSelectingListener(StartSurface.OnTabSelectingListener listener) {
        if (mTasksSurface != null) {
            mTasksSurface.setOnTabSelectingListener(listener);
        } else {
            mTabSwitcher.setOnTabSelectingListener(listener);
        }

        // Set OnTabSelectingListener to the more tabs tasks surface as well if it has been
        // instantiated, otherwise remember it for the future instantiation.
        if (mSurfaceMode == StartSurfaceMediator.SurfaceMode.SINGLE_PANE) {
            if (mSecondaryTasksSurface == null) {
                mOnTabSelectingListener = listener;
            } else {
                mSecondaryTasksSurface.setOnTabSelectingListener(listener);
            }
        }
    }

    @Override
    public Controller getController() {
        return mStartSurfaceMediator;
    }

    @Override
    public TabSwitcher.TabListDelegate getTabListDelegate() {
        if (mTasksSurface != null) {
            return mTasksSurface.getTabListDelegate();
        }

        return mTabSwitcher.getTabListDelegate();
    }

    void setFakeSearchBoxDelegate(TasksSurface.FakeSearchBoxDelegate fakeSearchBoxDelegate) {
        mFakeSearchBoxDelegate = fakeSearchBoxDelegate;
        if (mTasksSurface != null) {
            mTasksSurface.getFakeSearchBoxController().setDelegate(mFakeSearchBoxDelegate);
        }
        if (mSecondaryTasksSurface != null) {
            mSecondaryTasksSurface.getFakeSearchBoxController().setDelegate(mFakeSearchBoxDelegate);
        }
    }

    void setVoiceRecognitionHandler(LocationBarVoiceRecognitionHandler voiceRecognitionHandler) {
        mVoiceRecognitionHandler = voiceRecognitionHandler;
        if (mTasksSurface != null) {
            mTasksSurface.getFakeSearchBoxController().setVoiceRecognitionHandler(
                    mVoiceRecognitionHandler);
        }
        if (mSecondaryTasksSurface != null) {
            mSecondaryTasksSurface.getFakeSearchBoxController().setVoiceRecognitionHandler(
                    mVoiceRecognitionHandler);
        }
    }

    void setDelegate(StartSurfaceMediator.Delegate delegate) {
        mStartSurfaceMediator.setDelegate(delegate);
    }

    void onUrlFocusChange(boolean hasFocus) {
        if (mTasksSurface != null) {
            mStartSurfaceMediator.onUrlFocusChange(hasFocus);
        }
    }

    private @StartSurfaceMediator.SurfaceMode int computeSurfaceMode() {
        if (!FeatureUtilities.isStartSurfaceEnabled()) {
            return StartSurfaceMediator.SurfaceMode.NO_START_SURFACE;
        }

        String feature = ChromeFeatureList.getFieldTrialParamByFeature(
                ChromeFeatureList.START_SURFACE_ANDROID, "start_surface_variation");

        // Do not enable two panes when the bottom bar is enabled since it will
        // overlap the two panes' bottom bar.
        if (feature.equals("twopanes") && !FeatureUtilities.isBottomToolbarEnabled()) {
            return StartSurfaceMediator.SurfaceMode.TWO_PANES;
        }

        if (feature.equals("single")) return StartSurfaceMediator.SurfaceMode.SINGLE_PANE;

        if (feature.equals("tasksonly")) return StartSurfaceMediator.SurfaceMode.TASKS_ONLY;

        return StartSurfaceMediator.SurfaceMode.SINGLE_PANE;
    }

    private void createAndSetStartSurface() {
        ArrayList<PropertyKey> allProperties =
                new ArrayList<>(Arrays.asList(TasksSurfaceProperties.ALL_KEYS));
        allProperties.addAll(Arrays.asList(StartSurfaceProperties.ALL_KEYS));
        mPropertyModel = new PropertyModel(allProperties);
        mPropertyModel.set(TOP_BAR_HEIGHT,
                mActivity.getResources().getDimensionPixelSize(R.dimen.toolbar_height_no_shadow));

        mTasksSurface = TabManagementModuleProvider.getDelegate().createTasksSurface(mActivity,
                mSurfaceMode == StartSurfaceMediator.SurfaceMode.SINGLE_PANE, mPropertyModel);

        // The tasks surface is added to the explore surface in the single pane mode below.
        if (mSurfaceMode != StartSurfaceMediator.SurfaceMode.SINGLE_PANE) {
            // TODO(crbug.com/1000295): Put TasksSurface view inside Tab switcher recycler view.
            // This is a temporarily solution to make the TasksSurfaceView scroll together with the
            // Tab switcher, however this solution has performance issue when the Tab switcher is in
            // Grid mode, which is a launcher blocker. Check the bug details.
            mTasksSurfacePropertyModelChangeProcessor = PropertyModelChangeProcessor.create(
                    mPropertyModel,
                    new TasksSurfaceViewBinder.ViewHolder(mActivity.getCompositorViewHolder(),
                            (NestedScrollView) LayoutInflater.from(mActivity).inflate(
                                    R.layout.ss_explore_scroll_container,
                                    mActivity.getCompositorViewHolder(), false),
                            mTasksSurface.getContainerView()),
                    TasksSurfaceViewBinder::bind);
        }

        // There is nothing else to do for SurfaceMode.TASKS_ONLY.
        if (mSurfaceMode == StartSurfaceMediator.SurfaceMode.TASKS_ONLY) {
            return;
        }

        if (mSurfaceMode == StartSurfaceMediator.SurfaceMode.TWO_PANES) {
            mBottomBarCoordinator = new BottomBarCoordinator(
                    mActivity, mActivity.getCompositorViewHolder(), mPropertyModel);
        }

        // Create the explore surface.
        mExploreSurfaceCoordinator =
                new ExploreSurfaceCoordinator(mActivity, mActivity.getCompositorViewHolder(),
                        mSurfaceMode == StartSurfaceMediator.SurfaceMode.SINGLE_PANE
                                ? mTasksSurface.getContainerView()
                                : null,
                        mPropertyModel);
    }

    private TabSwitcher.Controller initializeSecondaryTasksSurface() {
        assert mSurfaceMode == StartSurfaceMediator.SurfaceMode.SINGLE_PANE;
        assert mSecondaryTasksSurface == null;

        PropertyModel propertyModel = new PropertyModel(TasksSurfaceProperties.ALL_KEYS);
        mStartSurfaceMediator.setSecondaryTasksSurfacePropertyModel(propertyModel);
        mSecondaryTasksSurface = TabManagementModuleProvider.getDelegate().createTasksSurface(
                mActivity, false, propertyModel);
        // TODO(crbug.com/1000295): Put TasksSurface view inside Tab switcher recycler view.
        // This is a temporarily solution to make the TasksSurfaceView scroll together with the
        // Tab switcher, however this solution has performance issue when the Tab switcher is in
        // Grid mode, which is a launcher blocker. Check the bug details.
        mSecondaryTasksSurfacePropertyModelChangeProcessor = PropertyModelChangeProcessor.create(
                mPropertyModel,
                new SecondaryTasksSurfaceViewBinder.ViewHolder(mActivity.getCompositorViewHolder(),
                        (NestedScrollView) LayoutInflater.from(mActivity).inflate(
                                R.layout.ss_explore_scroll_container,
                                mActivity.getCompositorViewHolder(), false),
                        mSecondaryTasksSurface.getContainerView()),
                SecondaryTasksSurfaceViewBinder::bind);
        if (mOnTabSelectingListener != null) {
            mSecondaryTasksSurface.setOnTabSelectingListener(mOnTabSelectingListener);
            mOnTabSelectingListener = null;
        }
        if (mFakeSearchBoxDelegate != null) {
            mSecondaryTasksSurface.getFakeSearchBoxController().setDelegate(mFakeSearchBoxDelegate);
            mFakeSearchBoxDelegate = null;
        }
        if (mVoiceRecognitionHandler != null) {
            mSecondaryTasksSurface.getFakeSearchBoxController().setVoiceRecognitionHandler(
                    mVoiceRecognitionHandler);
            mVoiceRecognitionHandler = null;
        }
        return mSecondaryTasksSurface.getController();
    }
}
