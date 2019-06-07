// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tasks;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents shopping assist service response.
 */
public final class ShoppingAssistServiceResponse {
    private static final String TAG = "SHP_RESP";

    /**
     * Represents a shopping product
     * */
    public static final class Product {
        private final String mName;
        private final String mMid;
        private final String mSearchUrl;

        /**
         * constructor
         * */
        public Product(String name, String mid, String jackpotUrl) {
            mName = name;
            mMid = mid;
            mSearchUrl = jackpotUrl;
        }

        /**
         * Gets the product name
         * */
        public String getName() {
            return mName;
        }

        /**
         * Gets the product mid
         * */
        public String getMid() {
            return mMid;
        }

        /**
         * Gets a search url to the product.
         * */
        public String getSearchUrl() {
            return mSearchUrl;
        }
    }

    private final List<Product> mProducts;

    /**
     * Creates an instance of {@link ShoppingAssistServiceResponse} from the service's JSON response
     * */
    public static ShoppingAssistServiceResponse createFromJsonString(String json) {
        try {
            JSONObject jsonObject = new JSONObject(json);
            List<ShoppingAssistServiceResponse.Product> products = new ArrayList<>();

            if (jsonObject.has("products")) {
                JSONArray jsonProducts = jsonObject.getJSONArray("products");
                for (int i = 0; i < jsonProducts.length(); i++) {
                    JSONObject jsonProduct = jsonProducts.getJSONObject(i);

                    products.add(new ShoppingAssistServiceResponse.Product(
                            jsonProduct.optString("product_name", ""),
                            jsonProduct.optString("mid", ""),
                            jsonProduct.optString("immersive_jackpot", "")));
                }
            }

            return new ShoppingAssistServiceResponse(products);

        } catch (JSONException e) {
            Log.e(TAG, "%s", e);
        }

        return null;
    }

    /**
     * Gets the list of products
     * */
    public List<Product> getProducts() {
        return mProducts;
    }

    /**
     * Gets the first product in the list or null.
     * */
    public Product getFirstProductOrNull() {
        return hasProducts() ? mProducts.get(0) : null;
    }

    /**
     * Returns true if the response contains products
     * */
    public boolean hasProducts() {
        return mProducts != null && !mProducts.isEmpty();
    }

    private ShoppingAssistServiceResponse(List<Product> products) {
        mProducts = products;
    }
}
