// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tasks.tab_management;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.PopupWindow;

import org.chromium.chrome.browser.widget.selection.SelectableListLayout;
import org.chromium.chrome.browser.widget.selection.SelectionDelegate;
import org.chromium.chrome.tab_ui.R;

/**
 * This class is used to show the {@link SelectableListLayout} in a {@link PopupWindow}.
 */
class TabSelectionEditorLayout extends SelectableListLayout<Integer> {
    public static final long BASE_ANIMATION_DURATION_MS = 218;
    private TabSelectionEditorToolbar mToolbar;
    private PopupWindow mWindow;
    private View mParentView;
    private boolean mIsInitialized;
    private RecyclerView mRecyclerView;
    private RecyclerView.ItemDecoration mItemDecoration;
    private final Interpolator mInterpolator = new DecelerateInterpolator(1.0f);
    private ObjectAnimator mSlideUpAnimator;
    private ObjectAnimator mSlideDownAnimator;

    public TabSelectionEditorLayout(Context context, AttributeSet attrs) {
        super(context, attrs);

        DisplayMetrics displayMetrics = new DisplayMetrics();
        ((WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE))
                .getDefaultDisplay()
                .getMetrics(displayMetrics);
    }

    /**
     * Initializes the RecyclerView and the toolbar for the layout. This must be called before
     * calling show/hide.
     *
     * @param parentView The parent view for the {@link PopupWindow}.
     * @param recyclerView The recycler view to be shown.
     * @param adapter The adapter that provides views that represent items in the recycler view.
     * @param selectionDelegate The {@link SelectionDelegate} that will inform the toolbar of
     *                            selection changes.
     */
    void initialize(View parentView, RecyclerView recyclerView, RecyclerView.Adapter adapter,
            SelectionDelegate<Integer> selectionDelegate) {
        mIsInitialized = true;
        mRecyclerView = initializeRecyclerView(adapter, recyclerView);
        mToolbar =
                (TabSelectionEditorToolbar) initializeToolbar(R.layout.tab_selection_editor_toolbar,
                        selectionDelegate, 0, 0, 0, null, false, true);
        initializeEmptyView(R.string.tab_selection_editor_empty_list, 0);
        mParentView = parentView;
        mWindow = new PopupWindow(this, mParentView.getWidth(), mParentView.getHeight());

        mSlideUpAnimator =
                ObjectAnimator.ofFloat(this, View.TRANSLATION_Y, mParentView.getHeight(), 0);
        mSlideUpAnimator.setDuration(BASE_ANIMATION_DURATION_MS);
        mSlideUpAnimator.setInterpolator(mInterpolator);
        mSlideUpAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animator) {
                mWindow.showAtLocation(mParentView, Gravity.CENTER, 0, 0);
            }
        });

        mSlideDownAnimator =
                ObjectAnimator.ofFloat(this, View.TRANSLATION_Y, 0, mParentView.getHeight());
        mSlideDownAnimator.setDuration(BASE_ANIMATION_DURATION_MS);
        mSlideDownAnimator.setInterpolator(mInterpolator);
        mSlideDownAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (mWindow.isShowing()) mWindow.dismiss();
            }
        });
    }

    /**
     * Shows the layout in a {@link PopupWindow}.
     */
    public void show() {
        assert mIsInitialized;
        if (mWindow.isShowing()) return;
        mSlideUpAnimator.start();
    }

    /**
     * Hides the {@link PopupWindow}.
     */
    public void hide() {
        assert mIsInitialized;
        if (!mWindow.isShowing()) return;
        mSlideDownAnimator.start();
    }

    /**
     * @return The toolbar of the layout.
     */
    public TabSelectionEditorToolbar getToolbar() {
        return mToolbar;
    }

    public void setItemDecoration(RecyclerView.ItemDecoration itemDecoration) {
        if (itemDecoration == null) {
            if (mItemDecoration == null) return;
            mRecyclerView.removeItemDecoration(mItemDecoration);
        } else {
            if (itemDecoration == mItemDecoration) return;
            mRecyclerView.addItemDecoration(itemDecoration);
        }
        mItemDecoration = itemDecoration;
    }
}
