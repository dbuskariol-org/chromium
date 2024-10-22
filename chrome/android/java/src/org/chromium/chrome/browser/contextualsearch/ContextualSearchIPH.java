// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.contextualsearch;

import android.graphics.Point;
import android.graphics.Rect;
import android.text.TextUtils;
import android.view.View;
import android.widget.PopupWindow.OnDismissListener;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.compositor.bottombar.contextualsearch.ContextualSearchPanel;
import org.chromium.chrome.browser.feature_engagement.TrackerFactory;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.util.ChromeAccessibilityUtil;
import org.chromium.components.browser_ui.widget.textbubble.TextBubble;
import org.chromium.components.feature_engagement.EventConstants;
import org.chromium.components.feature_engagement.FeatureConstants;
import org.chromium.components.feature_engagement.Tracker;
import org.chromium.components.feature_engagement.TriggerState;
import org.chromium.ui.widget.RectProvider;

/**
 * Helper class for displaying In-Product Help UI for Contextual Search.
 */
public class ContextualSearchIPH {
    private View mParentView;
    private ContextualSearchPanel mSearchPanel;
    private TextBubble mHelpBubble;
    private RectProvider mRectProvider;
    private String mFeatureName;
    private boolean mIsShowing;
    private boolean mIsPositionedByPanel;
    private boolean mHasUserEverEngaged;
    private Point mFloatingBubbleAnchorPoint;
    private OnDismissListener mDismissListener;

    /**
     * Constructs the helper class.
     */
    ContextualSearchIPH() {}

    /**
     * @param searchPanel The instance of {@link ContextualSearchPanel}.
     */
    void setSearchPanel(ContextualSearchPanel searchPanel) {
        mSearchPanel = searchPanel;
    }

    /**
     * @param parentView The parent view that the {@link TextBubble} will be attached to.
     */
    void setParentView(View parentView) {
        mParentView = parentView;
    }

    /**
     * Called after the Contextual Search panel's animation is finished.
     * @param wasActivatedByTap Whether Contextual Search was activated by tapping.
     * @param profile The {@link Profile} used for {@link TrackerFactory}.
     */
    void onPanelFinishedShowing(boolean wasActivatedByTap, Profile profile) {
        if (!wasActivatedByTap) {
            maybeShow(FeatureConstants.CONTEXTUAL_SEARCH_PROMOTE_TAP_FEATURE, profile);
            maybeShow(FeatureConstants.CONTEXTUAL_SEARCH_WEB_SEARCH_FEATURE, profile);
        }
    }

    /**
     * Called after entity data is received.
     * @param wasActivatedByTap Whether Contextual Search was activated by tapping.
     * @param profile The {@link Profile} used for {@link TrackerFactory}.
     */
    void onEntityDataReceived(boolean wasActivatedByTap, Profile profile) {
        if (wasActivatedByTap) {
            maybeShow(FeatureConstants.CONTEXTUAL_SEARCH_PROMOTE_PANEL_OPEN_FEATURE, profile);
        }
    }

    /**
     * Should be called after the user taps but a tap will not trigger due to longpress activation.
     * @param profile The active user profile.
     * @param bubbleAnchorPoint The point where the bubble arrow should be positioned.
     * @param hasUserEverEngaged Whether the user has ever engaged Contextual Search by opening
     *        the panel.
     * @param dismissListener An {@link OnDismissListener} to call when the bubble is dismissed.
     */
    void onNonTriggeringTap(Profile profile, Point bubbleAnchorPoint, boolean hasUserEverEngaged,
            OnDismissListener dismissListener) {
        mFloatingBubbleAnchorPoint = bubbleAnchorPoint;
        mHasUserEverEngaged = hasUserEverEngaged;
        mDismissListener = dismissListener;
        maybeShow(FeatureConstants.CONTEXTUAL_SEARCH_TAPPED_BUT_SHOULD_LONGPRESS_FEATURE, profile,
                false);
    }

    /**
     * Shows the appropriate In-Product Help UI if the conditions are met.
     * @param featureName Name of the feature in IPH, look at {@link FeatureConstants}.
     * @param profile The {@link Profile} used for {@link TrackerFactory}.
     */
    private void maybeShow(String featureName, Profile profile) {
        maybeShow(featureName, profile, true);
    }

    /**
     * Shows the appropriate In-Product Help UI if the conditions are met.
     * @param featureName Name of the feature in IPH, look at {@link FeatureConstants}.
     * @param profile The {@link Profile} used for {@link TrackerFactory}.
     * @param isPositionedByPanel Whether the bubble positioning should be based on the
     *        panel position instead of floating somewhere on the base page.
     */
    private void maybeShow(String featureName, Profile profile, boolean isPositionedByPanel) {
        mIsPositionedByPanel = isPositionedByPanel;
        if (mIsShowing || profile == null || mParentView == null
                || mIsPositionedByPanel && mSearchPanel == null) {
            return;
        }

        mFeatureName = featureName;
        maybeShowFeaturedBubble(profile);
    }

    /**
     * Shows a help bubble if the In-Product Help conditions are met.
     * Private state members are used to determine which message to show in the bubble
     * and how to position it.
     * @param profile The {@link Profile} used for {@link TrackerFactory}.
     */
    private void maybeShowFeaturedBubble(Profile profile) {
        if (mIsPositionedByPanel && !mSearchPanel.isShowing()) return;

        final Tracker tracker = TrackerFactory.getTrackerForProfile(profile);
        if (!tracker.shouldTriggerHelpUI(mFeatureName)) return;

        int stringId = 0;
        switch (mFeatureName) {
            case FeatureConstants.CONTEXTUAL_SEARCH_WEB_SEARCH_FEATURE:
                stringId = R.string.contextual_search_iph_search_result;
                break;
            case FeatureConstants.CONTEXTUAL_SEARCH_PROMOTE_PANEL_OPEN_FEATURE:
                stringId = R.string.contextual_search_iph_entity;
                break;
            case FeatureConstants.CONTEXTUAL_SEARCH_PROMOTE_TAP_FEATURE:
                stringId = R.string.contextual_search_iph_tap;
                break;
            case FeatureConstants.CONTEXTUAL_SEARCH_TAPPED_BUT_SHOULD_LONGPRESS_FEATURE:
                // TODO(donnd): put the engaged user variant behind a separate fieldtrial parameter
                // so we can control it or collapse it later.
                if (mHasUserEverEngaged) {
                    stringId = R.string.contextual_search_iph_touch_and_hold_engaged;
                } else {
                    stringId = R.string.contextual_search_iph_touch_and_hold;
                }
                break;
        }

        assert stringId != 0;
        assert mHelpBubble == null;
        mRectProvider = new RectProvider(getHelpBubbleAnchorRect());
        mHelpBubble = new TextBubble(mParentView.getContext(), mParentView, stringId, stringId,
                mRectProvider, ChromeAccessibilityUtil.get().isAccessibilityEnabled());

        mHelpBubble.setDismissOnTouchInteraction(true);
        mHelpBubble.addOnDismissListener(() -> {
            tracker.dismissed(mFeatureName);
            mIsShowing = false;
            mHelpBubble = null;
        });
        if (mDismissListener != null) {
            mHelpBubble.addOnDismissListener(mDismissListener);
            mDismissListener = null;
        }

        mHelpBubble.show();
        mIsShowing = true;
    }

    /**
     * Updates the position of the help bubble if it is showing.
     */
    void updateBubblePosition() {
        if (!mIsShowing || mHelpBubble == null || !mHelpBubble.isShowing()) return;

        mRectProvider.setRect(getHelpBubbleAnchorRect());
    }

    /**
     * @return A {@link Rect} object that represents the appropriate anchor for {@link TextBubble}.
     */
    private Rect getHelpBubbleAnchorRect() {
        int yInsetPx = mParentView.getResources().getDimensionPixelOffset(
                R.dimen.contextual_search_bubble_y_inset);
        if (!mIsPositionedByPanel) {
            // Position the bubble to point to the tap location, since there's no panel, just a
            // selected word.  It would be better to point to the rectangle of the selected word,
            // but that's not easy to get.
            return new Rect(mFloatingBubbleAnchorPoint.x, mFloatingBubbleAnchorPoint.y,
                    mFloatingBubbleAnchorPoint.x, mFloatingBubbleAnchorPoint.y);
        }

        Rect anchorRect = mSearchPanel.getPanelRect();
        anchorRect.top -= yInsetPx;
        return anchorRect;
    }

    /**
     * Dismisses the In-Product Help UI.
     */
    void dismiss() {
        if (!mIsShowing || TextUtils.isEmpty(mFeatureName)) return;

        mHelpBubble.dismiss();

        mIsShowing = false;
    }

    /**
     * @return whether the bubble is currently showing for the tap-where-longpress-needed promo.
     */
    boolean isShowingForTappedButShouldLongpress() {
        return mIsShowing
                && FeatureConstants.CONTEXTUAL_SEARCH_TAPPED_BUT_SHOULD_LONGPRESS_FEATURE.equals(
                        mFeatureName);
    }

    /**
     * Notifies the Feature Engagement backend and logs UMA metrics.
     * @param profile The current {@link Profile}.
     * @param wasSearchContentViewSeen Whether the Contextual Search panel was opened.
     * @param wasActivatedByTap Whether the Contextual Search was activating by tapping.
     * @param wasContextualCardsDataShown Whether entity cards were received.
     */
    public static void doSearchFinishedNotifications(Profile profile,
            boolean wasSearchContentViewSeen, boolean wasActivatedByTap,
            boolean wasContextualCardsDataShown) {
        Tracker tracker = TrackerFactory.getTrackerForProfile(profile);
        if (wasSearchContentViewSeen) {
            tracker.notifyEvent(EventConstants.CONTEXTUAL_SEARCH_PANEL_OPENED);
            tracker.notifyEvent(wasActivatedByTap
                            ? EventConstants.CONTEXTUAL_SEARCH_PANEL_OPENED_AFTER_TAP
                            : EventConstants.CONTEXTUAL_SEARCH_PANEL_OPENED_AFTER_LONGPRESS);

            // Log whether IPH for opening the panel has been shown before.
            ContextualSearchUma.logPanelOpenedIPH(
                    tracker.getTriggerState(
                            FeatureConstants.CONTEXTUAL_SEARCH_PROMOTE_PANEL_OPEN_FEATURE)
                    == TriggerState.HAS_BEEN_DISPLAYED);

            // Log whether IPH for Contextual Search web search has been shown before.
            ContextualSearchUma.logContextualSearchIPH(
                    tracker.getTriggerState(FeatureConstants.CONTEXTUAL_SEARCH_WEB_SEARCH_FEATURE)
                    == TriggerState.HAS_BEEN_DISPLAYED);
        }
        if (wasContextualCardsDataShown) {
            tracker.notifyEvent(EventConstants.CONTEXTUAL_SEARCH_PANEL_OPENED_FOR_ENTITY);
        }
    }
}
