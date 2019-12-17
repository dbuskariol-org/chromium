// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.share;

import android.app.Activity;
import android.graphics.drawable.Drawable;
import android.support.v7.content.res.AppCompatResources;
import android.view.View.OnClickListener;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.ActivityTabProvider;
import org.chromium.chrome.browser.send_tab_to_self.SendTabToSelfShareActivity;
import org.chromium.chrome.browser.share.qrcode.QrCodeCoordinator;
import org.chromium.chrome.browser.tab.TabLaunchType;
import org.chromium.chrome.browser.tabmodel.TabCreatorManager;
import org.chromium.chrome.browser.widget.bottomsheet.BottomSheetController;
import org.chromium.content_public.browser.LoadUrlParams;
import org.chromium.ui.modelutil.PropertyModel;

import java.util.ArrayList;

/**
 * Coordinator for displaying the share sheet.
 */
public class ShareSheetCoordinator {
    private final BottomSheetController mBottomSheetController;
    private final ActivityTabProvider mActivityTabProvider;
    private final TabCreatorManager.TabCreator mTabCreator;

    /**
     * Constructs a new ShareSheetCoordinator.
     * @param controller The BottomSheetController for the current activity.
     * @param provider The ActivityTabProvider for the current visible tab.
     * @param tabCreator The TabCreator for the current selected {@link TabModel}.
     */
    public ShareSheetCoordinator(BottomSheetController controller, ActivityTabProvider provider,
            TabCreatorManager.TabCreator tabCreator) {
        mBottomSheetController = controller;
        mActivityTabProvider = provider;
        mTabCreator = tabCreator;
    }

    protected void showShareSheet(ShareParams params) {
        Activity activity = params.getWindow().getActivity().get();
        if (activity == null) {
            return;
        }

        ShareSheetBottomSheetContent bottomSheet = new ShareSheetBottomSheetContent(activity);

        ArrayList<PropertyModel> chromeFeatures = createTopRowPropertyModels(bottomSheet, activity);
        ArrayList<PropertyModel> thirdPartyApps =
                createBottomRowPropertyModels(bottomSheet, activity, params);

        bottomSheet.createRecyclerViews(chromeFeatures, thirdPartyApps);

        mBottomSheetController.requestShowContent(bottomSheet, true);
    }

    private ArrayList<PropertyModel> createTopRowPropertyModels(
            ShareSheetBottomSheetContent bottomSheet, Activity activity) {
        ArrayList<PropertyModel> models = new ArrayList<>();
        // QR Codes
        PropertyModel qrcodePropertyModel =
                createPropertyModel(AppCompatResources.getDrawable(activity, R.drawable.qr_code),
                        activity.getResources().getString(R.string.qr_code_share_icon_label),
                        (currentActivity) -> {
                            mBottomSheetController.hideContent(bottomSheet, true);
                            QrCodeCoordinator qrCodeCoordinator =
                                    new QrCodeCoordinator(activity, this::createNewTab);
                            qrCodeCoordinator.show();
                        });
        models.add(qrcodePropertyModel);

        // Send Tab To Self
        PropertyModel
                sttsPropertyModel =
                        createPropertyModel(
                                AppCompatResources.getDrawable(activity, R.drawable.send_tab),
                                activity.getResources().getString(
                                        R.string.send_tab_to_self_share_activity_title),
                                (shareParams) -> {
                                    mBottomSheetController.hideContent(bottomSheet, true);
                                    SendTabToSelfShareActivity.actionHandler(activity,
                                            mActivityTabProvider.get()
                                                    .getWebContents()
                                                    .getNavigationController()
                                                    .getVisibleEntry(),
                                            mBottomSheetController);
                                });
        models.add(sttsPropertyModel);
        return models;
    }

    private ArrayList<PropertyModel> createBottomRowPropertyModels(
            ShareSheetBottomSheetContent bottomSheet, Activity activity, ShareParams params) {
        ArrayList<PropertyModel> models = new ArrayList<>();
        // More...
        PropertyModel morePropertyModel = createPropertyModel(
                AppCompatResources.getDrawable(activity, R.drawable.sharing_more),
                activity.getResources().getString(R.string.sharing_more_icon_label),
                (shareParams) -> {
                    mBottomSheetController.hideContent(bottomSheet, true);
                    ShareHelper.showDefaultShareUi(params);
                });
        models.add(morePropertyModel);

        return models;
    }

    private PropertyModel createPropertyModel(
            Drawable icon, String label, OnClickListener listener) {
        PropertyModel propertyModel =
                new PropertyModel.Builder(ShareSheetItemViewProperties.ALL_KEYS)
                        .with(ShareSheetItemViewProperties.ICON, icon)
                        .with(ShareSheetItemViewProperties.LABEL, label)
                        .with(ShareSheetItemViewProperties.CLICK_LISTENER, listener)
                        .build();
        return propertyModel;
    }

    private void createNewTab(String url) {
        mTabCreator.createNewTab(
                new LoadUrlParams(url), TabLaunchType.FROM_LINK, mActivityTabProvider.get());
    }
}
