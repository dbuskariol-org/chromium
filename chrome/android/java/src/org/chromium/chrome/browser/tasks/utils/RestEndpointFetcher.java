// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tasks.utils;

import org.chromium.base.Callback;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.chrome.browser.profiles.Profile;

/**
 * Calls a REST endpoint using rest_endpoint_fetcher.cc and returns the response
 */
public class RestEndpointFetcher {
    private long mNativeRestEndpointFetcher;

    /**
     * @param oathConsumerName consumer name for oath
     * @param url endpoint to be fetched
     * @param method "GET" or "POST"
     * @param contentType content type being fetched e.g. "application/json"
     * @param scopes oath scopes
     * @param postData data to be posted to endpoint if method is POST
     */
    public RestEndpointFetcher(String oathConsumerName, String url, String method,
            String contentType, String[] scopes, String postData) {
        nativeInit(this, Profile.getLastUsedProfile(), url, oathConsumerName, method, contentType,
                scopes, postData);
    }

    public void fetchResponse(Callback<String> callback) {
        nativeFetch(mNativeRestEndpointFetcher, callback);
    }

    @CalledByNative
    public void setNativePtr(long nativePtr) {
        assert mNativeRestEndpointFetcher == 0;
        mNativeRestEndpointFetcher = nativePtr;
    }

    public void destroy() {
        assert mNativeRestEndpointFetcher != 0;
        nativeDestroy(mNativeRestEndpointFetcher);
        mNativeRestEndpointFetcher = 0;
    }

    private native void nativeInit(RestEndpointFetcher restEndpointFetcher, Profile profile,
            String url, String oathConstumerName, String method, String contentType,
            String[] scopes, String postData);
    private native void nativeDestroy(long nativeRestEndpointFetcher);
    private native void nativeFetch(long nativeRestEndpointFetcher, Callback callback);
}
