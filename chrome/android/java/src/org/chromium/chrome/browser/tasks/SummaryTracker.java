// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tasks;

import android.net.Uri;
import android.text.TextUtils;

import com.google.protobuf.InvalidProtocolBufferException;

import org.chromium.chrome.browser.snackbar.Snackbar;
import org.chromium.chrome.browser.snackbar.SnackbarManager;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tab.TabExtension;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.tabmodel.TabModelSelectorTabObserver;
import org.chromium.chrome.browser.tasks.TaskSummary.Summary;
import org.chromium.chrome.browser.tasks.TaskSummary.SummaryInfo;
import org.chromium.content_public.browser.RenderFrameHost;
import org.chromium.net.GURLUtils;

import java.nio.ByteBuffer;
import java.util.Set;

/**
 * A class to keep track of various entry level information for the current tab and facilitate
 * reading the previous information and building a summary of the tab's history.
 */
public class SummaryTracker extends TabModelSelectorTabObserver {
    private static final String SEARCHABLE_FORM_URL_SEARCH_TERMS = "{searchTerms}";
    static final int SUMMARY_INFO_TYPE_QUERY = 1;
    static final int SUMMARY_INFO_TYPE_PRODUCT = 2;
    static final int SUMMARY_INFO_TYPE_DOMAIN = 3;
    static final int SUMMARY_INFO_TYPE_ANDROID_APP = 4;
    static final int SUMMARY_INFO_TYPE_HUB = 5;
    static final int SUMMARY_INFO_TYPE_SPOKE = 6;
    private final SnackbarManager.SnackbarController mSnackbarController;

    private TabSummary mCurrentTabSummary;

    /**
     * A {@link TabExtension} that keeps track of navigation summaries for in stack and pruned
     * entries.
     */
    public static class TabSummary implements TabExtension {
        private Summary.Builder mSummaryBuilder;

        public TabSummary() {
            mSummaryBuilder = Summary.newBuilder();
        }

        public void setSummary(Summary summary) {
            mSummaryBuilder = Summary.newBuilder(summary);
        }

        public Summary getSummary() {
            return mSummaryBuilder.build();
        }

        public void addNewInfo(SummaryInfo info) {
            mSummaryBuilder.addInfo(info);
        }

        @TabExtensionType
        @Override
        public String getType() {
            return TabExtensionType.TAB_SUMMARY;
        }

        @Override
        public void restoreFromByteBuffer(ByteBuffer buffer) {
            try {
                mSummaryBuilder = Summary.newBuilder(Summary.parseFrom(buffer));
            } catch (InvalidProtocolBufferException e) {
                e.printStackTrace();
            }
        }

        @Override
        public ByteBuffer saveToByteBuffer() {
            return ByteBuffer.wrap(mSummaryBuilder.build().toByteArray());
        }

        @Override
        public void onTabDestroy() {}
    }

    /**
     * Constructs an observer that should be notified of tabs changes for all tabs owned
     * by a specified {@link TabModelSelector}.  Any Tabs created after this call will be
     * observed as well, and Tabs removed will no longer have their information broadcast.
     * <p>
     * <p>
     * {@link #destroy()} must be called to unregister this observer.
     *
     * @param selector The selector that owns the Tabs that should notify this observer.
     */
    public SummaryTracker(TabModelSelector selector) {
        super(selector);
        mCurrentTabSummary = new TabSummary();
        mSnackbarController = new SnackbarManager.SnackbarController() {
            @Override
            public void onAction(Object actionData) {}

            @Override
            public void onDismissNoAction(Object actionData) {}
        };
    }

    public TabSummary getCurrentTabSummary() {
        return mCurrentTabSummary;
    }

    @Override
    public void onShown(Tab tab, int type) {
        android.util.Log.e("Yusuf", "OnTabShown : ");
        TabExtension extension = tab.getExtension(TabExtension.TabExtensionType.TAB_SUMMARY);
        mCurrentTabSummary = extension == null ? new TabSummary() : (TabSummary) extension;
        android.util.Log.e("Yusuf",
                "Saved summary info is :"
                        + mCurrentTabSummary.getSummary().getInfoList().toString());
    }

    @Override
    public void onHidden(Tab tab, int reason) {
        android.util.Log.e("Yusuf", "OnTabHidden : ");
        tab.addExtension(mCurrentTabSummary.getType(), mCurrentTabSummary);
        mCurrentTabSummary = null;
    }

    @Override
    public void onTabRestored(Tab tab) {
        android.util.Log.e("Yusuf", "OnTabRestored : ");
        TabExtension extension = tab.getExtension("summary");
        mCurrentTabSummary = extension == null ? new TabSummary() : (TabSummary) extension;
        android.util.Log.e("Yusuf",
                "Saved summary info is :"
                        + mCurrentTabSummary.getSummary().getInfoList().toString());
    }

    @Override
    public void hasPerformedSearch(Tab tab, String searchableFormUrl) {
        android.util.Log.e("Yusuf", "Searchable form url is here");
        if (TextUtils.isEmpty(searchableFormUrl)) return;

        String url = tab.getUrl();

        String query = getQueryFromUrl(searchableFormUrl, tab.getUrl());

        mCurrentTabSummary.addNewInfo(SummaryInfo.newBuilder()
                                              .setText(query)
                                              .setUrl(url)
                                              .setImageUrl(url)
                                              .setSummaryInfoType(SUMMARY_INFO_TYPE_QUERY)
                                              .build());
        Snackbar bar = Snackbar.make("Recorded query for " + query, mSnackbarController,
                Snackbar.TYPE_NOTIFICATION, Snackbar.UMA_UNKNOWN);
        bar.setDuration(500);
        tab.getSnackbarManager().showSnackbar(bar);
        android.util.Log.e(
                "Yusuf", "Recorded query info for " + query + " from " + GURLUtils.getOrigin(url));
    }

    private String getQueryFromUrl(String searchableFormUrl, String url) {
        Uri searchableFormUri = Uri.parse(searchableFormUrl);
        Uri uri = Uri.parse(url);
        String keyForQuery = null;
        String query = null;

        // The key for query should be the one that has the predefined query value in searchable
        // form url.
        Set<String> keys = searchableFormUri.getQueryParameterNames();
        for (String key : keys) {
            if (searchableFormUri.getQueryParameter(key).equals(SEARCHABLE_FORM_URL_SEARCH_TERMS)) {
                keyForQuery = key;
            }
        }
        if (TextUtils.isEmpty(keyForQuery)) return null;

        // If there is no query in the url, we have redirected to a designated entity page. As a
        // good approximation take the last path segment which will be the closest category.
        if (TextUtils.isEmpty(uri.getQuery())) {
            query = uri.getLastPathSegment();
        } else {
            query = uri.getQueryParameter(keyForQuery);
            if (TextUtils.isEmpty(query)) {
                // If this is a form submit, but the query is not keyed with the expected value,
                // look at the first key value pair as a last resort.
                Set<String> urlKeys = uri.getQueryParameterNames();
                query = uri.getQueryParameter(urlKeys.iterator().next());
            }
        }
        android.util.Log.e("Yusuf", "KEY AND QUERY" + keyForQuery + " = " + query);

        return query;
    }

    @Override
    public void onDidFinishNavigation(final Tab tab, final String url, boolean isInMainFrame,
            boolean isErrorPage, boolean hasCommitted, boolean isSameDocument,
            boolean isFragmentNavigation, Integer pageTransition, int errorCode,
            int httpStatusCode) {
        if (!isInMainFrame) return;

        if (tab.getWebContents() != null && tab.getWebContents().getMainFrame() != null) {
            final RenderFrameHost host = tab.getWebContents().getMainFrame();

            host.getOpenGraphType(type -> {
                android.util.Log.e("Yusuf", "OGType is " + type);
                if (type.contains("product") || type.isEmpty() || type.contains("ebay-objects:item")
                        || type.contains("video.movie") || type.contains("books.book")) {
                    host.getOpenGraphImageUrl(imageUrl -> {
                        if (imageUrl.isEmpty()) return;
                        android.util.Log.e("Yusuf", "Image url is  " + imageUrl);
                        host.getOpenGraphTitle(title -> {
                            mCurrentTabSummary.addNewInfo(
                                    SummaryInfo.newBuilder()
                                            .setSummaryInfoType(SUMMARY_INFO_TYPE_PRODUCT)
                                            .setImageUrl(imageUrl)
                                            .setUrl(url)
                                            .setText(title)
                                            .build());
                            Snackbar bar = Snackbar.make("Recorded product info for " + title,
                                    mSnackbarController, Snackbar.TYPE_NOTIFICATION,
                                    Snackbar.UMA_UNKNOWN);
                            bar.setDuration(500);
                            tab.getSnackbarManager().showSnackbar(bar);
                            android.util.Log.e("Yusuf", "Recording new product info for  " + title);
                        });

                    });
                }
            });
        }
    }
}
