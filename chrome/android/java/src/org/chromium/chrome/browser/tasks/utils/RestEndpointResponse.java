// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tasks.utils;

import org.chromium.base.annotations.CalledByNative;

/**
 * Encapsulates the response from the {@Link RestEndpointFetcher}
 */
public class RestEndpointResponse {
    private String mResponseString;

    /**
     * Create the RestEndpointResponse
     * @param responseString the response string acquired from the endpoint
     */
    public RestEndpointResponse(String responseString) {
        mResponseString = responseString;
    }

    public String getResponseString() {
        return mResponseString;
    }

    @CalledByNative
    private static RestEndpointResponse createRestEndpointResponse(String response) {
        return new RestEndpointResponse(response);
    }
}
