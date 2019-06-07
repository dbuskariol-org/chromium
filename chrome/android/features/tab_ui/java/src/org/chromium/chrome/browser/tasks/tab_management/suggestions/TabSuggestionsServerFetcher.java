// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tasks.tab_management.suggestions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import org.chromium.base.Callback;
import org.chromium.chrome.browser.tasks.utils.RestEndpointFetcher;

import java.util.LinkedList;
import java.util.List;

/**
 * Implements {@link TabSuggestionsFetcher}. Abstracts the details of
 * communicating with all known server-side {@link TabSuggestionProvider}
 */
public final class TabSuggestionsServerFetcher implements TabSuggestionsFetcher {
    private static final String OATH_CONSUMER_NAME = "Tabmari";
    private static final String ENDPOINT =
            "https://task-management-chrome.sandbox.google.com/tabs/suggestions";
    private static final String METHOD = "POST";
    private static final String CONTENT_TYPE = "application/json; charset=UTF-8";
    private static final String[] SCOPES = {"https://www.googleapis.com/auth/userinfo.email",
            "https://www.googleapis.com/auth/userinfo.profile"};
    private static final String URL_KEY = "url";
    private static final String ORIGINAL_URL_KEY = "originalUrl";
    private static final String ID_KEY = "id";
    private static final String TITLE_KEY = "title";
    private static final String TIMESTAMP_KEY = "timestamp";
    private static final String REFERRER_KEY = "referrer";
    private static final String TABS_KEY = "tabs";
    private static final String TAB_CONTEXT_KEY = "tabContext";
    private static final String SUGGESTIONS_KEY = "suggestions";
    private static final String ACTION_KEY = "action";
    private static final String CLOSE_KEY = "close";
    private static final String GROUP_KEY = "group";
    private static final String PROVIDER_NAME_KEY = "providerName";

    private RestEndpointFetcher mRestEndpointFetcher;

    public TabSuggestionsServerFetcher() {}

    @Override
    public void fetch(TabContext tabContext, Callback<List<TabSuggestion>> callback) {
        JSONArray jsonTabs = new JSONArray();
        JSONObject jsonObject = new JSONObject();
        try {
            for (TabContext.TabInfo tab : tabContext.getTabsInfo()) {
                JSONObject jsonTab = new JSONObject();
                jsonTab.put(ID_KEY, tab.getId());
                jsonTab.put(ORIGINAL_URL_KEY, tab.getOriginalUrl());
                jsonTab.put(REFERRER_KEY, tab.getReferrerUrl());
                jsonTab.put(TIMESTAMP_KEY, tab.getTimestampMillis());
                jsonTab.put(TITLE_KEY, tab.getTitle());
                jsonTab.put(URL_KEY, tab.getUrl());
                jsonTabs.put(jsonTab);
            }

            jsonObject.put(TABS_KEY, jsonTabs);
            JSONObject jsonRes = new JSONObject();
            jsonRes.put(TAB_CONTEXT_KEY, jsonObject);

            // destroy existing instance to avoid leaks on native side.
            if (mRestEndpointFetcher != null) {
                mRestEndpointFetcher.destroy();
            }

            mRestEndpointFetcher = new RestEndpointFetcher(
                    OATH_CONSUMER_NAME, ENDPOINT, METHOD, CONTENT_TYPE, SCOPES, jsonRes.toString());
            mRestEndpointFetcher.fetchResponse(res -> fetchCallback(res, callback));
        } catch (JSONException e) {
            // Soft failure for now so we don't crash the app and fall back on client side
            // providers.
            android.util.Log.e("Tabmari ", "There was a problem parsing the JSON" + e.getMessage());
        }
    }

    private void fetchCallback(String str, Callback<List<TabSuggestion>> callback) {
        mRestEndpointFetcher.destroy();
        List<TabSuggestion> suggestions = new LinkedList<>();
        JSONObject jsonResponse;
        try {
            jsonResponse = new JSONObject(str);
            JSONArray jsonSuggestions = jsonResponse.getJSONArray(SUGGESTIONS_KEY);

            for (int i = 0; i < jsonSuggestions.length(); i++) {
                JSONObject jsonSuggestion = jsonSuggestions.getJSONObject(i);
                JSONArray jsonTabs = jsonSuggestion.getJSONArray(TABS_KEY);
                List<TabContext.TabInfo> tabs = new LinkedList<>();
                for (int j = 0; j < jsonTabs.length(); j++) {
                    JSONObject jsonTab = jsonTabs.getJSONObject(j);
                    tabs.add(new TabContext.TabInfo(jsonTab.getInt(ID_KEY),
                            jsonTab.getString(TITLE_KEY), jsonTab.getString(URL_KEY),
                            jsonTab.getString(ORIGINAL_URL_KEY), jsonTab.getString(REFERRER_KEY),
                            jsonTab.getLong(TIMESTAMP_KEY)));
                }

                String action = (String) jsonSuggestion.get(ACTION_KEY);
                suggestions.add(new TabSuggestion(tabs, getTabSuggestionAction(action),
                        (String) jsonSuggestion.get(PROVIDER_NAME_KEY)));
            }
        } catch (JSONException e) {
            // Soft failure for now so we don't crash the app and fall back on client side
            // providers.
            android.util.Log.e("Tabmari ",
                    String.format(
                            "There was a problem parsing the JSON\n Details: %s", e.getMessage()));
        }
        callback.onResult(suggestions);
    }

    private static int getTabSuggestionAction(String action) {
        switch (action) {
            case GROUP_KEY:
                return TabSuggestion.TabSuggestionAction.GROUP;
            case CLOSE_KEY:
                return TabSuggestion.TabSuggestionAction.CLOSE;
            default:
                android.util.Log.e("Tabmari ", String.format("Unknown action: %s\n", action));
                return -1;
        }
    }
}
