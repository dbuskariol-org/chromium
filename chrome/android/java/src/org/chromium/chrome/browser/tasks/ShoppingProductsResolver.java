// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tasks;

import android.util.Log;

import org.chromium.chrome.browser.tasks.utils.RestEndpointFetcher;
import org.chromium.content_public.browser.WebContents;

/**
 * Resolves shopping products for a given url
 * */
public final class ShoppingProductsResolver {
    private static final String[] SHOPPING_ASSIST_OAUTH_SCOPES =
            new String[] {"https://www.googleapis.com/auth/userinfo.email"};
    private static final String SHOPPING_ASSIST_URL =
            "http://task-management-chrome.sandbox.google.com/shopping?offersonly=true&url=%s";
    private static final String SHOPPING_ASSIST_OAUTH_CONSUMER = "ShoppingAssist";
    private static final String TAG = "SHP_AST";

    private static ShoppingProductsResolver sInstance;

    /**
     * Product resolution callback interface
     * */
    public interface ResolveResponse {
        void onResolveResponse(ShoppingAssistServiceResponse.Product product);
    }

    /**
     * Gets the singleton instance for this class.
     */
    public static ShoppingProductsResolver getInstance() {
        if (sInstance == null) sInstance = new ShoppingProductsResolver();
        return sInstance;
    }
    /**
     * Issues an https request to the shopping assist backend. The result is communicated through
     * the provided ResolveResponse callback
     */
    public void startShoppingProductResolutionRequest(
            WebContents webContents, ResolveResponse responseCallback, String url) {
        if (webContents != null) {
            // start https request to fetch data from the service.
            RestEndpointFetcher shoppingEndpointFetcher = new RestEndpointFetcher(
                    SHOPPING_ASSIST_OAUTH_CONSUMER, String.format(SHOPPING_ASSIST_URL, url), "GET",
                    "", SHOPPING_ASSIST_OAUTH_SCOPES, null);

            shoppingEndpointFetcher.fetchResponse((response) -> {
                Log.e(TAG, response);
                ShoppingAssistServiceResponse shoppingAssistResponse =
                        ShoppingAssistServiceResponse.createFromJsonString(response);
                responseCallback.onResolveResponse(shoppingAssistResponse == null
                                ? null
                                : shoppingAssistResponse.getFirstProductOrNull());
            });
        }
    }
}
