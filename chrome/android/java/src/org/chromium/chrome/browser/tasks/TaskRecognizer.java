// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tasks;

import android.net.Uri;
import android.text.TextUtils;

import org.chromium.base.Log;
import org.chromium.base.task.PostTask;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeFeatureList;
import org.chromium.chrome.browser.compositor.bottombar.ephemeraltab.EphemeralTabPanel;
import org.chromium.chrome.browser.contextualsearch.ContextualSearchContext;
import org.chromium.chrome.browser.contextualsearch.ResolvedSearchTerm;
import org.chromium.chrome.browser.contextualsearch.ResolvedSearchTerm.CardTag;
import org.chromium.chrome.browser.contextualsearch.SimpleSearchTermResolver;
import org.chromium.chrome.browser.contextualsearch.SimpleSearchTermResolver.ResolveResponse;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.TabLaunchType;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.tabmodel.TabModelSelectorTabObserver;
import org.chromium.chrome.browser.toolbar.bottom.SearchAccelerator;
import org.chromium.content_public.browser.LoadUrlParams;
import org.chromium.content_public.browser.NavigationHandle;
import org.chromium.content_public.browser.UiThreadTaskTraits;
import org.chromium.content_public.browser.WebContents;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.AbstractMap;
import java.util.List;

/**
 * Recognizes that a Tab is associated with a product.
 * Uses requests through the SimpleSearchTermResolver to make a Contextual Search request to
 * get a card for a section of text.  If that card is a Product, then we may take action.
 */
public class TaskRecognizer extends TabModelSelectorTabObserver
        implements ResolveResponse, ShoppingProductsResolver.ResolveResponse {
    private static final String TAG = "ctxs";

    // TODO(yusufo): remove by end of April 2019 when the server should be returning CardTags.
    private static final String UNICODE_STAR = "\u2605";

    /** Our singleton instance. */
    private static TaskRecognizer sInstance;

    /** The Tab that is in use. */
    private Tab mTabInUse;

    /** A Context that is in use, or null. */
    private ContextualSearchContext mContext;
    private SearchAccelerator mSearchAccelerator;

    /** Gets the singleton instance for this class. */
    public static TaskRecognizer getInstance(TabModelSelector tabModelSelector) {
        if (sInstance == null) sInstance = new TaskRecognizer(tabModelSelector);
        return sInstance;
    }

    /**
     * Creates a {@link TaskRecognizer} for the given tab.
     */
    public static void createForTabModelSelector(TabModelSelector tabModelSelector) {
        getInstance(tabModelSelector);
    }

    /**
     * Constructs a Task Recognizer for the given Tab.
     */
    private TaskRecognizer(TabModelSelector selector) {
        super(selector);
    }

    /**
     * Try to recognize that the given {@link Tab} is about a product and show some product info.
     * @param tab The tab that might be about a product.  Must be the current front tab.
     */
    private void tryToShowProduct(Tab tab) {

        // TODO(yusufo): filter based on other criteria, e.g. Incognito.
        boolean inProgress = resolveTitleAndShowOverlay(tab, this);
        if (inProgress) {
            Log.i(TAG, "Trying to show product info for tab: " + tab);
            mTabInUse = tab;
        }
    }

    /**
     * Tries to identify a product in the given tab, and calls the given callback with the results.
     * @param tab The tab that we're trying to identify a product in.  We currently use the tab's
     *        title as a hack for what to use for identification purposes.
     * @param responseCallback The callback to call.
     * @return Whether we were able to issue a request.
     */
    private boolean resolveTitleAndShowOverlay(Tab tab, ResolveResponse responseCallback) {
        String pageTitle = tab.getTitle();
        String pageUrl = tab.getUrl();
        WebContents webContents = tab.getWebContents();
        if (TextUtils.isEmpty(pageTitle) || TextUtils.isEmpty(pageUrl) || webContents == null) {
            Log.i(TAG, "ctxs not a good page. :-(");
            return false;
        }

        String provider = ChromeFeatureList.getFieldTrialParamByFeature(
                ChromeFeatureList.SHOPPING_ASSIST_PROVIDER, "shopping_assist_provider");

        if (provider.equals("BuyableCorpus")) {
            ShoppingProductsResolver.getInstance().startShoppingProductResolutionRequest(
                    webContents, this, getUriWithoutPii(tab.getUrl()));

        } else {
            if (mContext != null) mContext.destroy();
            int insertionPointLocation = pageTitle.length() / 2;
            mContext = ContextualSearchContext.getContextForInsertionPoint(
                    pageTitle, insertionPointLocation);
            if (mContext == null) {
                Log.i(TAG, "ctxs not a context. :-(");
                return false;
            }

            Log.i(TAG, "ctxs startSearchTermResolutionRequest!");
            SimpleSearchTermResolver.getInstance().startSearchTermResolutionRequest(
                    webContents, mContext, responseCallback);
        }

        mSearchAccelerator = tab.getActivity().findViewById(R.id.search_accelerator);
        if (mSearchAccelerator != null) {
            android.util.Log.e("ctxs", "Found the button !!");
        }

        return true;
    }

    private String getQuery(List<AbstractMap.SimpleEntry<String, String>> params)
            throws UnsupportedEncodingException {
        StringBuilder result = new StringBuilder();
        boolean first = true;

        for (AbstractMap.SimpleEntry<String, String> pair : params) {
            if (first)
                first = false;
            else
                result.append("&");

            result.append(URLEncoder.encode(pair.getKey(), "UTF-8"));
            result.append("=");
            result.append(URLEncoder.encode(pair.getValue(), "UTF-8"));
        }

        return result.toString();
    }

    private String getUriWithoutPii(String url) {
        Uri uri = Uri.parse(url);
        try {
            URL u = new URL(uri.getScheme(), uri.getAuthority(), uri.getPort(), uri.getPath());
            return u.toString();
        } catch (MalformedURLException e) {
            Log.e(TAG, "getUriWithoutPii failed: %s", e);
        }

        return null;
    }

    /** @return Whether the given {@link ResolvedSearchTerm} identified a product. */
    private boolean looksLikeAProduct(ResolvedSearchTerm resolvedSearchTerm) {
        if (resolvedSearchTerm.cardTagEnum() == CardTag.CT_PRODUCT) return true;

        // Fallback onto our "has-a-star" hack.
        return (resolvedSearchTerm.cardTagEnum() == CardTag.CT_NONE
                && resolvedSearchTerm.caption().contains(UNICODE_STAR));
    }

    /**
     * Creates an child tab for the given searchUrl using details from the given
     */
    private void createChildTabFor(Uri searchUrl) {
        mTabInUse.getActivity().getTabCreator(false).createNewTab(
                new LoadUrlParams(searchUrl.toString()), TabLaunchType.FROM_CHROME_UI, mTabInUse);
    }

    /** Shopping Assist's onResolveResponse  **/
    @Override
    public void onResolveResponse(ShoppingAssistServiceResponse.Product product) {
        if (product != null && mSearchAccelerator != null) {
            PostTask.runOrPostTask(UiThreadTaskTraits.USER_VISIBLE, () -> {
                mSearchAccelerator.reset(product.getName(),
                        view -> createChildTabFor(Uri.parse(product.getSearchUrl())));
            });
        }
    }

    private void createEphemeralTabFor(
            Tab activeTab, ResolvedSearchTerm resolvedSearchTerm, Uri searchUrl) {
        EphemeralTabPanel displayPanel = activeTab.getActivity().getEphemeralTabPanel();
        if (displayPanel != null) {
            displayPanel.requestOpenPanel(searchUrl.toString(), resolvedSearchTerm.displayText(),
                    activeTab.isIncognito());
        }
    }

    /** ResolveResponse overrides. */
    @Override
    public void onResolveResponse(ResolvedSearchTerm resolvedSearchTerm, Uri searchUri) {
        if (looksLikeAProduct(resolvedSearchTerm)) {
            if (mSearchAccelerator != null)
                mSearchAccelerator.reset(
                        resolvedSearchTerm.displayText(), view -> createChildTabFor(searchUri));

            mTabInUse.setProductUrl(resolvedSearchTerm.displayText());
            mTabInUse.setProductThumbnailUrl(resolvedSearchTerm.thumbnailUrl());
        }
    }

    @Override
    public void didFirstVisuallyNonEmptyPaint(Tab tab) {
        tryToShowProduct(tab);
    }

    @Override
    public void onDidStartNavigation(Tab tab, NavigationHandle navigationHandle) {
        mSearchAccelerator = tab.getActivity().findViewById(R.id.search_accelerator);

        if (navigationHandle.isInMainFrame() && !navigationHandle.isSameDocument()) {
            if (mSearchAccelerator != null) mSearchAccelerator.reset(null, null);
            tab.setProductUrl("");
            tab.setProductThumbnailUrl("");
        }
    }

    @Override
    public void onShown(Tab tab, int type) {
        mSearchAccelerator = tab.getActivity().findViewById(R.id.search_accelerator);
        if (mSearchAccelerator == null) return;

        mSearchAccelerator.reset(null, null);
    }
}
