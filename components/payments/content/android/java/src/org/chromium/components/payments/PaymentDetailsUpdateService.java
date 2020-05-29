// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.components.payments;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;

/**
 * A bound service responsible for receiving change payment method, shipping option, and shipping
 * address calls from an inoked native payment app.
 */
public class PaymentDetailsUpdateService extends Service {
    private final IPaymentDetailsUpdateService.Stub mBinder =
            new IPaymentDetailsUpdateService.Stub() {
                @Override
                public void changePaymentMethod(Bundle paymentHandlerMethodData,
                        IPaymentDetailsUpdateServiceCallback callback) {
                    if (!PaymentDetailsUpdateServiceHelper.getInstance().isCallerAuthorized(
                                Binder.getCallingUid())) {
                        return;
                    }
                    PaymentDetailsUpdateServiceHelper.getInstance().changePaymentMethod(
                            paymentHandlerMethodData, callback);
                }
                @Override
                public void changeShippingOption(
                        String shippingOptionId, IPaymentDetailsUpdateServiceCallback callback) {
                    if (!PaymentDetailsUpdateServiceHelper.getInstance().isCallerAuthorized(
                                Binder.getCallingUid())) {
                        return;
                    }
                    PaymentDetailsUpdateServiceHelper.getInstance().changeShippingOption(
                            shippingOptionId, callback);
                }
                @Override
                public void changeShippingAddress(
                        Bundle shippingAddress, IPaymentDetailsUpdateServiceCallback callback) {
                    if (!PaymentDetailsUpdateServiceHelper.getInstance().isCallerAuthorized(
                                Binder.getCallingUid())) {
                        return;
                    }
                    PaymentDetailsUpdateServiceHelper.getInstance().changeShippingAddress(
                            shippingAddress, callback);
                }
            };

    @Override
    public IBinder onBind(Intent intent) {
        if (!PaymentFeatureList.isEnabledOrExperimentalFeaturesEnabled(
                    PaymentFeatureList.ANDROID_APP_PAYMENT_UPDATE_EVENTS)) {
            return null;
        }
        return mBinder;
    }
}
