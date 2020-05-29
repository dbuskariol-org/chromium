// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.components.payments;

import android.content.pm.PackageInfo;
import android.content.pm.Signature;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import org.chromium.base.Log;

import java.util.Arrays;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.annotation.concurrent.GuardedBy;

/**
 * Helper class used by android payment app to notify the browser that the user has selected a
 * different payment instrument, shipping option, or shipping address inside native app.
 */
public class PaymentDetailsUpdateServiceHelper {
    private static final String TAG = "PaymentDetailsUpdate";

    // Bundle keys used to send data to this service.
    public static final String KEY_METHOD_NAME = "methodName";
    public static final String KEY_STRINGIFIED_DETAILS = "details";

    private final Object mStateLock = new Object();
    @Nullable
    @GuardedBy("mStateLock")
    private IPaymentDetailsUpdateServiceCallback mCallback;
    @Nullable
    @GuardedBy("mStateLock")
    private PaymentRequestUpdateEventListener mListener;

    private final ReadWriteLock mPackageInfoRwLock = new ReentrantReadWriteLock();
    @Nullable
    @GuardedBy("mPackageInfoRwLock")
    private PackageInfo mInvokedAppPackageInfo;
    @GuardedBy("mPackageInfoRwLock")
    private PackageManagerDelegate mPackageManagerDelegate;

    // Singleton instance.
    private static final Object sInstanceLock = new Object();
    private static volatile PaymentDetailsUpdateServiceHelper sInstance;
    private PaymentDetailsUpdateServiceHelper(){};

    /**
     * Returns the thread safe singleton instance, lazily creating one if needed.
     * The instance is only useful after its listener is set which happens when a native android app
     * gets invoked.
     * @return The singleton instance.
     */
    public static PaymentDetailsUpdateServiceHelper getInstance() {
        if (sInstance == null) {
            synchronized (sInstanceLock) {
                if (sInstance == null) sInstance = new PaymentDetailsUpdateServiceHelper();
            }
        }
        return sInstance;
    }

    /**
     * Initializes the service helper, called when an AndroidPaymentApp is invoked.
     * @param packageManagerDelegate The package manager used used to authorize the connecting app.
     * @param invokedAppPackageName The package name of the invoked payment app, used to authorize
     *         the connecting app.
     * @param listener The listener for payment method, shipping address, and shipping option
     *         changes.
     */
    public void initialize(PackageManagerDelegate packageManagerDelegate,
            String invokedAppPackageName, PaymentRequestUpdateEventListener listener) {
        synchronized (mStateLock) {
            assert mListener == null;
            mListener = listener;
        }

        mPackageInfoRwLock.writeLock().lock();
        try {
            mPackageManagerDelegate = packageManagerDelegate;
            mInvokedAppPackageInfo =
                    mPackageManagerDelegate.getPackageInfoWithSignatures(invokedAppPackageName);
        } finally {
            mPackageInfoRwLock.writeLock().unlock();
        }
    }

    /**
     * Called to notify the merchant that the user has selected a different payment method.
     * @param paymentHandlerMethodData The data containing the selected payment method's name and
     *         optional stringified details.
     * @param callback The callback used to notify the invoked app about updated payment details.
     */
    public void changePaymentMethod(
            Bundle paymentHandlerMethodData, IPaymentDetailsUpdateServiceCallback callback) {
        if (paymentHandlerMethodData == null) {
            runCallbackWithError(ErrorStrings.METHOD_DATA_REQUIRED, callback);
            return;
        }
        String methodName = paymentHandlerMethodData.getString(KEY_METHOD_NAME);
        if (TextUtils.isEmpty(methodName)) {
            runCallbackWithError(ErrorStrings.METHOD_NAME_REQUIRED, callback);
            return;
        }

        @Nullable
        String stringifiedDetails = paymentHandlerMethodData.getString(KEY_STRINGIFIED_DETAILS);

        synchronized (mStateLock) {
            if (isWaitingForPaymentDetailsUpdate() || mListener == null
                    || !mListener.changePaymentMethodFromInvokedApp(
                            methodName, stringifiedDetails)) {
                runCallbackWithError(ErrorStrings.INVALID_STATE, callback);
                return;
            }
            mCallback = callback;
        }
    }

    /**
     * Called to notify the merchant that the user has selected a different shipping option.
     * @param shippingOptionId The identifier of the selected shipping option.
     * @param callback The callback used to notify the invoked app about updated payment details.
     */
    public void changeShippingOption(
            String shippingOptionId, IPaymentDetailsUpdateServiceCallback callback) {
        if (TextUtils.isEmpty(shippingOptionId)) {
            runCallbackWithError(ErrorStrings.SHIPPING_OPTION_ID_REQUIRED, callback);
            return;
        }

        synchronized (mStateLock) {
            if (isWaitingForPaymentDetailsUpdate() || mListener == null
                    || !mListener.changeShippingOptionFromInvokedApp(shippingOptionId)) {
                runCallbackWithError(ErrorStrings.INVALID_STATE, callback);
                return;
            }
            mCallback = callback;
        }
    }

    /**
     * Called to notify the merchant that the user has selected a different shipping address.
     * @param shippingAddress The selected shipping address
     * @param callback The callback used to notify the invoked app about updated payment details.
     */
    public void changeShippingAddress(
            Bundle shippingAddress, IPaymentDetailsUpdateServiceCallback callback) {
        if (shippingAddress == null || shippingAddress.isEmpty()) {
            runCallbackWithError(ErrorStrings.SHIPPING_ADDRESS_INVALID, callback);
            return;
        }

        synchronized (mStateLock) {
            if (isWaitingForPaymentDetailsUpdate() || mListener == null
                    || !mListener.changeShippingAddressFromInvokedApp(
                            PaymentAddressTypeConverter.convertAddressToMojoPaymentAddress(
                                    Address.createFromBundle(shippingAddress)))) {
                runCallbackWithError(ErrorStrings.INVALID_STATE, callback);
                return;
            }
            mCallback = callback;
        }
    }

    /**
     * Resets the singleton instance's state.
     */
    public void reset() {
        synchronized (mStateLock) {
            mCallback = null;
            mListener = null;
        }

        mPackageInfoRwLock.writeLock().lock();
        try {
            mPackageManagerDelegate = null;
            mInvokedAppPackageInfo = null;
        } finally {
            mPackageInfoRwLock.writeLock().unlock();
        }
    }

    /**
     * Checks whether any payment method, shipping address or shipping option change event is
     * ongoing.
     * @return True after invoked payment app has bound PaymentDetaialsUpdateService and called
     *         changePaymentMethod, changeShippingAddress, or changeShippingOption and before the
     *         merchant replies with either updateWith() or onPaymentDetailsNotUpdated().
     */
    public boolean isWaitingForPaymentDetailsUpdate() {
        synchronized (mStateLock) {
            return mCallback != null;
        }
    }

    /**
     * @param callerUid The Uid of the service requester.
     * @return True when the service requester's package name and signature are the same as the
     *         invoked payment app's.
     */
    public boolean isCallerAuthorized(int callerUid) {
        mPackageInfoRwLock.readLock().lock();
        try {
            if (mPackageManagerDelegate == null) {
                Log.e(TAG, ErrorStrings.UNATHORIZED_SERVICE_REQUEST);
                return false;
            }
            PackageInfo callerPackageInfo =
                    mPackageManagerDelegate.getPackageInfoWithSignatures(callerUid);
            if (mInvokedAppPackageInfo == null || callerPackageInfo == null
                    || !mInvokedAppPackageInfo.packageName.equals(callerPackageInfo.packageName)) {
                Log.e(TAG, ErrorStrings.UNATHORIZED_SERVICE_REQUEST);
                return false;
            }

            // TODO(https://crbug.com/1086485): signatures field is deprecated in API level 28.
            Signature[] callerSignatures = callerPackageInfo.signatures;
            Signature[] invokedAppSignatures = mInvokedAppPackageInfo.signatures;

            boolean result = Arrays.equals(callerSignatures, invokedAppSignatures);
            if (!result) Log.e(TAG, ErrorStrings.UNATHORIZED_SERVICE_REQUEST);
            return result;
        } finally {
            mPackageInfoRwLock.readLock().unlock();
        }
    }

    private void runCallbackWithError(String error, IPaymentDetailsUpdateServiceCallback callback) {
        // TODO(https://crbug.com/1026667): Call Callback.updateWith with a
        // updatedPaymentDetails bundle including the error message only.
    }
}
