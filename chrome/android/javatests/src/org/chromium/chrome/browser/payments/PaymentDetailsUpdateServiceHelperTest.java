// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.payments;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.test.filters.MediumTest;
import android.text.TextUtils;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;

import org.chromium.base.test.util.CommandLineFlags;
import org.chromium.base.test.util.Feature;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.flags.ChromeSwitches;
import org.chromium.chrome.test.ChromeActivityTestRule;
import org.chromium.chrome.test.ChromeJUnit4ClassRunner;
import org.chromium.chrome.test.util.browser.Features.EnableFeatures;
import org.chromium.components.payments.Address;
import org.chromium.components.payments.IPaymentDetailsUpdateService;
import org.chromium.components.payments.IPaymentDetailsUpdateServiceCallback;
import org.chromium.components.payments.PaymentDetailsUpdateService;
import org.chromium.components.payments.PaymentDetailsUpdateServiceHelper;
import org.chromium.components.payments.PaymentFeatureList;
import org.chromium.components.payments.PaymentRequestUpdateEventListener;
import org.chromium.content_public.browser.test.util.Criteria;
import org.chromium.content_public.browser.test.util.CriteriaHelper;
import org.chromium.payments.mojom.PaymentAddress;

/**
 * Tests for PaymentDetailsUpdateServiceHelper.
 **/
@RunWith(ChromeJUnit4ClassRunner.class)
@EnableFeatures({PaymentFeatureList.ANDROID_APP_PAYMENT_UPDATE_EVENTS})
@CommandLineFlags.Add({ChromeSwitches.DISABLE_FIRST_RUN_EXPERIENCE})
public class PaymentDetailsUpdateServiceHelperTest {
    private static final int DECODER_STARTUP_TIMEOUT_IN_MS = 10000;

    @Rule
    public ChromeActivityTestRule<ChromeActivity> mRule =
            new ChromeActivityTestRule<>(ChromeActivity.class);

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    /** Simulates a package manager in memory. */
    private final MockPackageManagerDelegate mPackageManager = new MockPackageManagerDelegate();

    private Context mContext;

    private boolean mBound;
    private IPaymentDetailsUpdateService mIPaymentDetailsUpdateService;
    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            mIPaymentDetailsUpdateService = IPaymentDetailsUpdateService.Stub.asInterface(service);
            mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            mIPaymentDetailsUpdateService = null;
            mBound = false;
        }
    };

    @Before
    public void setUp() throws Throwable {
        mRule.startMainActivityOnBlankPage();
        mContext = mRule.getActivity();
    }

    private void installPaymentApp() {
        mPackageManager.installPaymentApp(
                "BobPay", /*packageName=*/"com.bobpay", null /* no metadata */, /*signature=*/"01");
        mPackageManager.setInvokedAppPackageName(/*packageName=*/"com.bobpay");
    }

    private void installAndInvokePaymentApp() {
        installPaymentApp();
        PaymentDetailsUpdateServiceHelper.getInstance().initialize(
                mPackageManager, /*packageName=*/"com.bobpay", mUpdateListener);
    }

    private void startPaymentDetailsUpdateService() {
        Intent intent = new Intent(/*ContextUtils.getApplicationContext()*/ mContext,
                PaymentDetailsUpdateService.class);
        intent.setAction(IPaymentDetailsUpdateService.class.getName());
        mContext.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

        CriteriaHelper.pollUiThread(new Criteria() {
            @Override
            public boolean isSatisfied() {
                return mBound;
            }
        }, DECODER_STARTUP_TIMEOUT_IN_MS, CriteriaHelper.DEFAULT_POLLING_INTERVAL);
    }

    private boolean mMethodChangeListenerNotified;
    private boolean mShippingOptionChangeListenerNotified;
    private boolean mShippingAddressChangeListenerNotified;
    private PaymentRequestUpdateEventListener mUpdateListener =
            new FakePaymentRequestUpdateEventListener();
    private class FakePaymentRequestUpdateEventListener
            implements PaymentRequestUpdateEventListener {
        @Override
        public boolean changePaymentMethodFromInvokedApp(
                String methodName, String stringifiedDetails) {
            Assert.assertFalse(TextUtils.isEmpty(methodName));
            mMethodChangeListenerNotified = true;
            return true;
        }
        @Override
        public boolean changeShippingOptionFromInvokedApp(String shippingOptionId) {
            Assert.assertFalse(TextUtils.isEmpty(shippingOptionId));
            mShippingOptionChangeListenerNotified = true;
            return true;
        }
        @Override
        public boolean changeShippingAddressFromInvokedApp(PaymentAddress shippingAddress) {
            mShippingAddressChangeListenerNotified = true;
            return true;
        }
    }

    private class PaymentDetailsUpdateServiceCallback
            extends IPaymentDetailsUpdateServiceCallback.Stub {
        @Override
        public void updateWith(Bundle updatedPaymentDetails) {}

        @Override
        public void paymentDetailsNotUpdated() {}
    }

    @Test
    @MediumTest
    @Feature({"Payments"})
    public void testConnectWhenPaymentAppNotInvoked() throws Exception {
        installPaymentApp();
        startPaymentDetailsUpdateService();
        Bundle bundle = new Bundle();
        bundle.putString(PaymentDetailsUpdateServiceHelper.KEY_METHOD_NAME, "method name");
        bundle.putString(PaymentDetailsUpdateServiceHelper.KEY_STRINGIFIED_DETAILS, "details");
        mIPaymentDetailsUpdateService.changePaymentMethod(
                bundle, new PaymentDetailsUpdateServiceCallback());
        Assert.assertFalse(
                PaymentDetailsUpdateServiceHelper.getInstance().isWaitingForPaymentDetailsUpdate());
        Assert.assertFalse(mMethodChangeListenerNotified);
    }

    @Test
    @MediumTest
    @Feature({"Payments"})
    public void testChangePaymentMethod() throws Exception {
        installAndInvokePaymentApp();
        startPaymentDetailsUpdateService();
        Bundle bundle = new Bundle();
        bundle.putString(PaymentDetailsUpdateServiceHelper.KEY_METHOD_NAME, "method name");
        bundle.putString(PaymentDetailsUpdateServiceHelper.KEY_STRINGIFIED_DETAILS, "details");
        mIPaymentDetailsUpdateService.changePaymentMethod(
                bundle, new PaymentDetailsUpdateServiceCallback());
        Assert.assertTrue(
                PaymentDetailsUpdateServiceHelper.getInstance().isWaitingForPaymentDetailsUpdate());
        Assert.assertTrue(mMethodChangeListenerNotified);
    }

    @Test
    @MediumTest
    @Feature({"Payments"})
    public void testChangePaymentMethodMissingBundle() throws Exception {
        installAndInvokePaymentApp();
        startPaymentDetailsUpdateService();
        mIPaymentDetailsUpdateService.changePaymentMethod(
                null, new PaymentDetailsUpdateServiceCallback());
        Assert.assertFalse(
                PaymentDetailsUpdateServiceHelper.getInstance().isWaitingForPaymentDetailsUpdate());
        Assert.assertFalse(mMethodChangeListenerNotified);
    }

    @Test
    @MediumTest
    @Feature({"Payments"})
    public void testChangePaymentMethodMissingMethodNameBundle() throws Exception {
        installAndInvokePaymentApp();
        startPaymentDetailsUpdateService();
        Bundle bundle = new Bundle();
        bundle.putString(PaymentDetailsUpdateServiceHelper.KEY_STRINGIFIED_DETAILS, "details");
        mIPaymentDetailsUpdateService.changePaymentMethod(
                bundle, new PaymentDetailsUpdateServiceCallback());
        Assert.assertFalse(
                PaymentDetailsUpdateServiceHelper.getInstance().isWaitingForPaymentDetailsUpdate());
        Assert.assertFalse(mMethodChangeListenerNotified);
    }

    @Test
    @MediumTest
    @Feature({"Payments"})
    public void testChangeShippingOption() throws Exception {
        installAndInvokePaymentApp();
        startPaymentDetailsUpdateService();
        mIPaymentDetailsUpdateService.changeShippingOption(
                "shipping option id", new PaymentDetailsUpdateServiceCallback());
        Assert.assertTrue(
                PaymentDetailsUpdateServiceHelper.getInstance().isWaitingForPaymentDetailsUpdate());
        Assert.assertTrue(mShippingOptionChangeListenerNotified);
    }

    @Test
    @MediumTest
    @Feature({"Payments"})
    public void testChangeShippingOptionWithMissingOptionId() throws Exception {
        installAndInvokePaymentApp();
        startPaymentDetailsUpdateService();
        mIPaymentDetailsUpdateService.changeShippingOption(
                /*shippingOptionId=*/"", new PaymentDetailsUpdateServiceCallback());
        Assert.assertFalse(
                PaymentDetailsUpdateServiceHelper.getInstance().isWaitingForPaymentDetailsUpdate());
        Assert.assertFalse(mShippingOptionChangeListenerNotified);
    }

    @Test
    @MediumTest
    @Feature({"Payments"})
    public void testChangeShippingAddress() throws Exception {
        installAndInvokePaymentApp();
        startPaymentDetailsUpdateService();
        Bundle bundle = new Bundle();
        bundle.putString(Address.EXTRA_ADDRESS_COUNTRY, "Canada");
        String[] addressLine = {"111 Richmond Street West"};
        bundle.putStringArray(Address.EXTRA_ADDRESS_LINES, addressLine);
        bundle.putString(Address.EXTRA_ADDRESS_REGION, "Ontario");
        bundle.putString(Address.EXTRA_ADDRESS_CITY, "Toronto");
        bundle.putString(Address.EXTRA_ADDRESS_POSTAL_CODE, "M5H2G4");
        bundle.putString(Address.EXTRA_ADDRESS_RECIPIENT, "John Smith");
        bundle.putString(Address.EXTRA_ADDRESS_PHONE, "4169158200");
        mIPaymentDetailsUpdateService.changeShippingAddress(
                bundle, new PaymentDetailsUpdateServiceCallback());
        Assert.assertTrue(
                PaymentDetailsUpdateServiceHelper.getInstance().isWaitingForPaymentDetailsUpdate());
        Assert.assertTrue(mShippingAddressChangeListenerNotified);
    }

    @Test
    @MediumTest
    @Feature({"Payments"})
    public void testChangeShippingAddressWithMissingBundle() throws Exception {
        installAndInvokePaymentApp();
        startPaymentDetailsUpdateService();
        mIPaymentDetailsUpdateService.changeShippingAddress(
                null, new PaymentDetailsUpdateServiceCallback());
        Assert.assertFalse(
                PaymentDetailsUpdateServiceHelper.getInstance().isWaitingForPaymentDetailsUpdate());
        Assert.assertFalse(mShippingAddressChangeListenerNotified);
    }

    @Test
    @MediumTest
    @Feature({"Payments"})
    public void testChangeWhileWaitingForPaymentDetailsUpdate() throws Exception {
        installAndInvokePaymentApp();
        startPaymentDetailsUpdateService();
        Bundle bundle = new Bundle();
        bundle.putString(PaymentDetailsUpdateServiceHelper.KEY_METHOD_NAME, "method name");
        bundle.putString(PaymentDetailsUpdateServiceHelper.KEY_STRINGIFIED_DETAILS, "details");
        mIPaymentDetailsUpdateService.changePaymentMethod(
                bundle, new PaymentDetailsUpdateServiceCallback());
        Assert.assertTrue(
                PaymentDetailsUpdateServiceHelper.getInstance().isWaitingForPaymentDetailsUpdate());
        Assert.assertTrue(mMethodChangeListenerNotified);

        // Call changeShippingOption while waiting for updated payment details.
        mIPaymentDetailsUpdateService.changeShippingOption(
                "shipping option id", new PaymentDetailsUpdateServiceCallback());
        Assert.assertTrue(
                PaymentDetailsUpdateServiceHelper.getInstance().isWaitingForPaymentDetailsUpdate());
        Assert.assertFalse(mShippingOptionChangeListenerNotified);
    }
}
