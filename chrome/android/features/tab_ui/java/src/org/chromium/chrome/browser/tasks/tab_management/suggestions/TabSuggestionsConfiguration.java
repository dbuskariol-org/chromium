// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tasks.tab_management.suggestions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import org.chromium.base.Log;
import org.chromium.chrome.browser.ChromeVersionInfo;
import org.chromium.chrome.browser.lifecycle.Destroyable;
import org.chromium.chrome.browser.tasks.utils.RestEndpointFetcher;
import org.chromium.chrome.browser.tasks.utils.RestEndpointResponse;

import java.util.HashMap;
import java.util.Map;

class TabSuggestionsConfiguration implements Destroyable {
    private static final String TAG = "TabSuggestionsConfig";

    private static final int DEFAULT_CLIENT_PROVIDER_SCORE = 100;
    private static final int DEFAULT_SERVER_PROVIDER_SCORE = 200;

    private static final String OATH_CONSUMER_NAME = "Tabmari";
    private static final String ENDPOINT =
            "https://task-management-chrome.sandbox.google.com/tabs/suggestions/config";
    private static final String METHOD = "GET";
    private static final String CONTENT_TYPE = "application/json; charset=UTF-8";
    private static final String[] SCOPES = {"https://www.googleapis.com/auth/userinfo.email",
            "https://www.googleapis.com/auth/userinfo.profile"};
    private static final String PROVIDER_RANKINGS_KEY = "providerRankings";
    private static final String PROVIDER_NAME_KEY = "name";
    private static final String PROVIDER_SCORE_KEY = "score";
    private static final String PROVIDER_ENABLED_KEY = "enabled";
    private static final long TIMEOUT_MS = 5000;

    private Map<String, TabSuggestionProviderConfiguration> mProviderConfigurations = new HashMap<
            String, TabSuggestionProviderConfiguration>() {
        {
            put(TabSuggestionsRanker.SuggestionProviders.DUPLICATE_PAGE_SUGGESTION_PROVIDER,
                    new TabSuggestionProviderConfiguration(DEFAULT_CLIENT_PROVIDER_SCORE, true));
            put(TabSuggestionsRanker.SuggestionProviders.STALE_TABS_SUGGESTION_PROVIDER,
                    new TabSuggestionProviderConfiguration(DEFAULT_CLIENT_PROVIDER_SCORE, true));
            put(TabSuggestionsRanker.SuggestionProviders.SESSION_TAB_SWITCHES_SUGGESTION_PROVIDER,
                    new TabSuggestionProviderConfiguration(DEFAULT_CLIENT_PROVIDER_SCORE, true));
            put(TabSuggestionsRanker.SuggestionProviders.META_TAG_SUGGESTION_PROVIDER,
                    new TabSuggestionProviderConfiguration(DEFAULT_SERVER_PROVIDER_SCORE, true));
            put(TabSuggestionsRanker.SuggestionProviders.SHOPPING_PRODUCT_PROVIDER,
                    new TabSuggestionProviderConfiguration(DEFAULT_SERVER_PROVIDER_SCORE, true));
            put(TabSuggestionsRanker.SuggestionProviders.NEWS_SUGGESTION_PROVIDER,
                    new TabSuggestionProviderConfiguration(DEFAULT_SERVER_PROVIDER_SCORE, true));
            put(TabSuggestionsRanker.SuggestionProviders.TASKS_SUGGESTION_PROVIDER,
                    new TabSuggestionProviderConfiguration(DEFAULT_SERVER_PROVIDER_SCORE, true));
            put(TabSuggestionsRanker.SuggestionProviders.ARTICLES_SUGGESTION_PROVIDER,
                    new TabSuggestionProviderConfiguration(DEFAULT_SERVER_PROVIDER_SCORE, true));
        }
    };

    private RestEndpointFetcher mRestEndpointFetcher;

    @Override
    public void destroy() {
        mRestEndpointFetcher.destroy();
    }

    void fetchConfiguration() {
        if (!ChromeVersionInfo.isOfficialBuild()) return;

        if (mRestEndpointFetcher != null) mRestEndpointFetcher.destroy();

        // TODO(mattsimmons): Send Finch field trial info along as a query string (append to
        //  ENDPOINT).
        mRestEndpointFetcher = new RestEndpointFetcher(
                OATH_CONSUMER_NAME, ENDPOINT, METHOD, CONTENT_TYPE, SCOPES, null, TIMEOUT_MS);
        mRestEndpointFetcher.fetchResponse(this::handleResponse);
    }

    Map<String, TabSuggestionProviderConfiguration> getProviderConfigurations() {
        return mProviderConfigurations;
    }

    private void handleResponse(RestEndpointResponse response) {
        mRestEndpointFetcher.destroy();
        try {
            JSONObject jsonResponse = new JSONObject(response.getResponseString());
            JSONArray jsonRankings = jsonResponse.getJSONArray(PROVIDER_RANKINGS_KEY);

            for (int i = 0; i < jsonRankings.length(); i++) {
                JSONObject jsonRanking = jsonRankings.getJSONObject(i);
                final String name = jsonRanking.getString(PROVIDER_NAME_KEY);
                final int score = jsonRanking.getInt(PROVIDER_SCORE_KEY);
                final boolean enabled = jsonRanking.optBoolean(PROVIDER_ENABLED_KEY, true);
                Log.i(TAG, "Server Config: Provider %s overridden with score %d - enabled: %s",
                        name, score, enabled);
                mProviderConfigurations.put(
                        name, new TabSuggestionProviderConfiguration(score, enabled));
            }
        } catch (JSONException e) {
            Log.e(TAG, "JSON error fetching suggestions config: %s", e.getMessage());
        }
    }
}
